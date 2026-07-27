package com.coolxer.service.dih;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.ChatAttachment;
import com.fasterxml.jackson.databind.JsonNode;
import com.coolxer.service.dih.advisor.ReasoningContentAdvisor;
import com.coolxer.service.dih.logging.LlmLogHelper;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.mcp.ToolRuntimeContext;
import com.coolxer.service.dih.rag.RagContextService;
import com.coolxer.service.dih.rag.RagContextService.RagContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 聊天服务
 */

@Service
public class AIChatService {

    private static final Logger log = LoggerFactory.getLogger(AIChatService.class);

    /**
     * Tool sessions can otherwise spend an unbounded amount of time producing a
     * verbose final report after all evidence has already been collected.
     */
    private static final int TOOL_CALL_MAX_TOKENS = 4096;

    private static final String QWEN_NATIVE_DEEP_THINK_SYSTEM_PROMPT = """
            You are a thoughtful AI assistant. Use the enabled reasoning mode to think before answering.
            Do not include <think> tags or reasoning text in the final answer, because the platform displays
            reasoning_content separately. Answer the user's question directly after reasoning, in the same
            language as the user when possible.
            """;

    private final ChatClient systemPromptChatClient;

    private final ChatMemory chatMemory;

    private final PromptTemplate deepThinkPromptTemplate;

    private final ReasoningContentAdvisor reasoningContentAdvisor;

    private final PromptTemplate askSystemPromptTemplate;

    private final RagContextService ragContextService;

    private final ChatAttachmentService chatAttachmentService;

    private final HttpClient openAiHttpClient;

    private final String openAiBaseUrl;

    private final String openAiApiKey;

    private final String defaultChatModel;

    public AIChatService(
            @Qualifier("springAiChatMemoryRepository") ChatMemoryRepository chatMemoryRepository,
            ChatModel chatModel,
            @Qualifier("askSystemPromptTemplate") PromptTemplate systemPromptTemplate,
            @Qualifier("deepThinkPromptTemplate") PromptTemplate deepThinkPromptTemplate,
            RagContextService ragContextService,
            ChatAttachmentService chatAttachmentService,
            @Value("${spring.ai.openai.base-url:}") String openAiBaseUrl,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            @Value("${spring.ai.openai.chat.options.model:}") String defaultChatModel
    ) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .build();
        this.chatMemory = chatMemory;

        this.systemPromptChatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        this.askSystemPromptTemplate = systemPromptTemplate;
        this.deepThinkPromptTemplate = deepThinkPromptTemplate;
        this.reasoningContentAdvisor = new ReasoningContentAdvisor(1);
        this.ragContextService = ragContextService;
        this.chatAttachmentService = chatAttachmentService;
        this.openAiHttpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.openAiBaseUrl = openAiBaseUrl;
        this.openAiApiKey = openAiApiKey;
        this.defaultChatModel = defaultChatModel;
    }

    public Flux<String> chat(String chatId, String model, String prompt) {
        return qaChat(chatId, model, prompt, List.of(), null, false);
    }

    public Flux<String> chat(String chatId, String model, String prompt, List<ChatAttachment> attachments, User user) {
        return qaChat(chatId, model, prompt, attachments, user, false);
    }

    /**
     * 普通问答入口：允许 RAG，禁止 Skill/MCP 工具。
     */
    public Flux<String> qaChat(String chatId,
                               String model,
                               String prompt,
                               List<ChatAttachment> attachments,
                               User user,
                               boolean deepThinking) {
        String mode = deepThinking ? "deep_think" : "ask";
        RagContext ragContext = ragContextService.retrieve(prompt, mode);
        String systemPrompt = buildQaSystemPrompt(model, deepThinking, ragContext.systemPrompt());

        if (canUseNativeQaStream(model, attachments, deepThinking)) {
            return nativeOpenAiChat(
                    chatId,
                    model,
                    prompt,
                    attachments,
                    user,
                    deepThinking,
                    systemPrompt,
                    deepThinking
                            ? "AIChatService.qaChat.deepThink.native"
                            : "AIChatService.qaChat.native"
            );
        }

        String scene = deepThinking ? "AIChatService.qaChat.deepThink" : "AIChatService.qaChat";
        String requestId = LlmLogHelper.newRequestId();
        long startedAtNanos = System.nanoTime();
        LlmLogHelper.logRequest(log, requestId, scene,
                buildChatLogRequest(
                        chatId,
                        model,
                        prompt,
                        attachments,
                        deepThinking,
                        ragContext.requested(),
                        ragContext.used(),
                        ragContext.documentCount(),
                        systemPrompt,
                        false
                ));

        var promptSpec = systemPromptChatClient.prompt()
                .options(buildRuntimeOptions(model, false))
                .system(systemPrompt)
                .user(prompt)
                .advisors(memoryAdvisor -> memoryAdvisor
                        .param(ChatMemory.CONVERSATION_ID, chatId)
                );

        if (deepThinking || supportsReasoningContent(model)) {
            promptSpec = promptSpec.advisors(reasoningContentAdvisor);
        }

        return LlmLogHelper.logStringStream(log, requestId, scene, promptSpec.stream().content(), startedAtNanos);
    }

    /**
     * 智能体入口：允许显式 Skill 和受控 MCP 工具，禁止 RAG。
     */
    public Flux<String> agentChat(String chatId,
                                  String model,
                                  String systemPrompt,
                                  String prompt,
                                  List<ChatAttachment> attachments,
                                  User user,
                                  McpToolContext mcpToolContext) {
        if (!StringUtils.hasText(systemPrompt)) {
            return Flux.error(new AgentCapabilityUnavailableException("智能体系统提示词不能为空。"));
        }

        log.debug("agent chat model is: {}", model);

        McpToolContext resolvedMcpToolContext = mcpToolContext == null
                ? McpToolContext.empty()
                : mcpToolContext;
        if (!resolvedMcpToolContext.hasTools()
                && chatAttachmentService.hasImageAttachment(attachments)
                && canUseNativeOpenAiStream()) {
            return nativeOpenAiChat(
                    chatId,
                    model,
                    prompt,
                    attachments,
                    user,
                    false,
                    systemPrompt,
                    "AIChatService.agentChat.native"
            );
        }

        String scene = "AIChatService.agentChat";
        String requestId = LlmLogHelper.newRequestId();
        long startedAtNanos = System.nanoTime();
        LlmLogHelper.logRequest(log, requestId, scene,
                buildChatLogRequest(chatId, model, prompt, attachments, false,
                        false, false, 0, systemPrompt,
                        resolvedMcpToolContext.hasTools()));

        var promptSpec = systemPromptChatClient.prompt()
                .options(buildRuntimeOptions(model, resolvedMcpToolContext.hasTools()))
                .system(systemPrompt)
                .user(prompt)
                .advisors(memoryAdvisor -> memoryAdvisor
                        .param(ChatMemory.CONVERSATION_ID, chatId)
                );

        if (resolvedMcpToolContext.hasTools()) {
            promptSpec = promptSpec.toolCallbacks(resolvedMcpToolContext.toolCallbackProvider());
            Map<String, Object> toolContext = new HashMap<>();
            if (resolvedMcpToolContext.invocationContext() != null) {
                toolContext.put(
                        McpInvocationContext.TOOL_CONTEXT_KEY,
                        resolvedMcpToolContext.invocationContext());
            }
            if (resolvedMcpToolContext.toolRuntimeContext() != null
                    && resolvedMcpToolContext.toolRuntimeContext().hasLimits()) {
                toolContext.put(
                        ToolRuntimeContext.TOOL_CONTEXT_KEY,
                        resolvedMcpToolContext.toolRuntimeContext());
            }
            if (!toolContext.isEmpty()) {
                promptSpec = promptSpec.toolContext(toolContext);
            }
        }

        if (supportsReasoningContent(model)) {
            promptSpec = promptSpec.advisors(reasoningContentAdvisor);
        }

        return LlmLogHelper.logStringStream(log, requestId, scene, promptSpec.stream().content(), startedAtNanos);
    }

    public Flux<String> deepThinkingChat(String chatId, String model, String prompt) {
        return qaChat(chatId, model, prompt, List.of(), null, true);
    }

    public Flux<String> deepThinkingChat(String chatId, String model, String prompt, List<ChatAttachment> attachments, User user) {
        return qaChat(chatId, model, prompt, attachments, user, true);
    }

    private boolean canUseNativeQaStream(String model,
                                         List<ChatAttachment> attachments,
                                         boolean deepThinking) {
        if (!canUseNativeOpenAiStream()) {
            return false;
        }
        if (deepThinking && supportsQwenReasoningStream(model)) {
            return true;
        }
        return chatAttachmentService.hasImageAttachment(attachments);
    }

    private String buildQaSystemPrompt(String model, boolean deepThinking, String ragSystemPrompt) {
        String basePrompt;
        if (deepThinking && supportsQwenReasoningStream(model)) {
            basePrompt = QWEN_NATIVE_DEEP_THINK_SYSTEM_PROMPT;
        } else if (deepThinking) {
            basePrompt = deepThinkPromptTemplate.getTemplate();
        } else {
            basePrompt = askSystemPromptTemplate.getTemplate();
        }
        return appendSystemPrompt(basePrompt, ragSystemPrompt);
    }

    private Flux<String> nativeOpenAiChat(
            String chatId,
            String model,
            String prompt,
            List<ChatAttachment> attachments,
            User user,
            boolean deepThinking,
            String systemPromptOverride,
            String scene
    ) {
        AtomicReference<String> finalAnswer = new AtomicReference<>("");
        AtomicReference<String> fullResponse = new AtomicReference<>("");
        String requestId = LlmLogHelper.newRequestId();
        long startedAtNanos = System.nanoTime();

        return Flux.<String>create(sink -> {
                    AtomicBoolean inThinking = new AtomicBoolean(false);
                    try {
                        String chatCompletionsUrl = openAiChatCompletionsUrl();
                        Map<String, Object> nativeRequest =
                                buildNativeChatRequest(chatId, model, prompt, attachments, user, deepThinking, systemPromptOverride);
                        LlmLogHelper.logRequest(log, requestId, scene,
                                buildNativeHttpLogRequest(chatCompletionsUrl, nativeRequest));
                        String requestBody = JacksonConfig.OBJECT_MAPPER.writeValueAsString(nativeRequest);
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(chatCompletionsUrl))
                                .timeout(Duration.ofMinutes(5))
                                .header("Content-Type", "application/json")
                                .header("Accept", "text/event-stream")
                                .header("Authorization", "Bearer " + openAiApiKey)
                                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                .build();

                        HttpResponse<Stream<String>> response = openAiHttpClient.send(request, HttpResponse.BodyHandlers.ofLines());
                        if (response.statusCode() >= 400) {
                            String errorBody;
                            try (Stream<String> lines = response.body()) {
                                errorBody = lines.collect(Collectors.joining("\n"));
                            }
                            LlmLogHelper.logResponse(log, requestId, scene,
                                    buildNativeHttpLogResponse(response.statusCode(), errorBody), startedAtNanos);
                            sink.error(new IllegalStateException("本地模型请求失败，HTTP " + response.statusCode()));
                            return;
                        }

                        try (Stream<String> lines = response.body()) {
                            var iterator = lines.iterator();
                            while (!sink.isCancelled() && iterator.hasNext()) {
                                String line = iterator.next();
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String data = line.substring("data:".length()).trim();
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                emitNativeChatChunk(data, inThinking, finalAnswer, fullResponse, sink);
                            }
                        }

                        if (inThinking.get() && !sink.isCancelled()) {
                            emitText("</think>", fullResponse, sink);
                        }
                        if (!sink.isCancelled()) {
                            sink.complete();
                        }
                    } catch (Exception e) {
                        if (!sink.isCancelled()) {
                            sink.error(e);
                        }
                    }
                })
                .doOnComplete(() -> LlmLogHelper.logResponse(log, requestId, scene,
                        buildNativeStreamLogResponse(finalAnswer.get(), fullResponse.get()), startedAtNanos))
                .doOnComplete(() -> saveNativeChatMemory(chatId, prompt, finalAnswer.get(), fullResponse.get()))
                .doOnError(error -> LlmLogHelper.logError(log, requestId, scene,
                        buildNativeStreamLogResponse(finalAnswer.get(), fullResponse.get()), startedAtNanos, error))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, Object> buildChatLogRequest(
            String chatId,
            String model,
            String prompt,
            List<ChatAttachment> attachments,
            boolean deepThinking,
            boolean ragRequested,
            boolean ragUsed,
            int ragDocumentCount,
            String systemPrompt,
            boolean toolCalling
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        String resolvedModel = StringUtils.hasText(model) ? model : defaultChatModel;
        List<Map<String, Object>> messages = buildChatLogMessages(chatId, systemPrompt, prompt);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", resolvedModel);
        requestBody.put("messages", messages);
        requestBody.put("temperature", toolCalling ? 0.1 : 0.8);
        requestBody.put("stream", true);
        if (toolCalling) {
            requestBody.put("parallel_tool_calls", false);
            requestBody.put("max_tokens", TOOL_CALL_MAX_TOKENS);
        }

        request.put("url", openAiChatCompletionsUrl());
        request.put("body", requestBody);
        request.put("chat_id", chatId);
        request.put("model", resolvedModel);
        request.put("message_count", messages.size());
        request.put("deep_thinking", deepThinking);
        request.put("rag_requested", ragRequested);
        request.put("rag_used", ragUsed);
        request.put("rag_document_count", ragDocumentCount);
        request.put("attachment_count", attachments == null ? 0 : attachments.size());
        return request;
    }

    private List<Map<String, Object>> buildChatLogMessages(String chatId, String systemPrompt, String prompt) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        if (StringUtils.hasText(chatId)) {
            for (Message message : chatMemory.get(chatId)) {
                String role = toOpenAiRole(message);
                if (StringUtils.hasText(role) && StringUtils.hasText(message.getText())) {
                    messages.add(Map.of("role", role, "content", message.getText()));
                }
            }
        }
        messages.add(Map.of("role", "user", "content", prompt));
        return messages;
    }

    private Map<String, Object> buildNativeHttpLogRequest(String url, Map<String, Object> body) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("url", url);
        request.put("body", body);
        return request;
    }

    private Map<String, Object> buildNativeHttpLogResponse(int statusCode, String body) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status_code", statusCode);
        response.put("body", body);
        return response;
    }

    private Map<String, Object> buildNativeStreamLogResponse(String finalAnswer, String fullResponse) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("final_answer", finalAnswer);
        response.put("full_response", fullResponse);
        return response;
    }

    private Map<String, Object> buildNativeChatRequest(
            String chatId,
            String model,
            String prompt,
            List<ChatAttachment> attachments,
            User user,
            boolean deepThinking,
            String systemPromptOverride
    ) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", StringUtils.hasText(model) ? model : defaultChatModel);
        request.put("stream", true);
        request.put("temperature", 0.8);
        if (deepThinking && supportsQwenReasoningStream(model)) {
            request.put("chat_template_kwargs", Map.of("enable_thinking", true));
        }
        request.put("messages", buildNativeMessages(chatId, model, prompt, attachments, user, deepThinking, systemPromptOverride));
        return request;
    }

    private List<Map<String, Object>> buildNativeMessages(
            String chatId,
            String model,
            String prompt,
            List<ChatAttachment> attachments,
            User user,
            boolean deepThinking,
            String systemPromptOverride
    ) {
        List<Map<String, Object>> messages = new ArrayList<>();
        String systemPrompt = StringUtils.hasText(systemPromptOverride)
                ? systemPromptOverride
                : nativeSystemPrompt(model, deepThinking);
        messages.add(Map.of("role", "system", "content", systemPrompt));

        if (StringUtils.hasText(chatId)) {
            for (Message message : chatMemory.get(chatId)) {
                String role = toOpenAiRole(message);
                if (!StringUtils.hasText(role) || !StringUtils.hasText(message.getText())) {
                    continue;
                }
                messages.add(Map.of("role", role, "content", message.getText()));
            }
        }

        List<Map<String, Object>> imageParts = chatAttachmentService.buildOpenAiImageContentParts(attachments, user);
        if (imageParts.isEmpty()) {
            messages.add(Map.of("role", "user", "content", prompt));
        } else {
            List<Map<String, Object>> contentParts = new ArrayList<>();
            contentParts.add(Map.of("type", "text", "text", prompt));
            contentParts.addAll(imageParts);
            messages.add(Map.of("role", "user", "content", contentParts));
        }
        return messages;
    }

    private void emitNativeChatChunk(
            String data,
            AtomicBoolean inThinking,
            AtomicReference<String> finalAnswer,
            AtomicReference<String> fullResponse,
            reactor.core.publisher.FluxSink<String> sink
    ) throws Exception {
        JsonNode root = JacksonConfig.OBJECT_MAPPER.readTree(data);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return;
        }

        JsonNode delta = choices.get(0).path("delta");
        String reasoningContent = firstText(delta, "reasoning_content", "reasoningContent");
        String content = firstText(delta, "content");

        if (hasContent(reasoningContent)) {
            if (inThinking.compareAndSet(false, true)) {
                emitText("<think>", fullResponse, sink);
            }
            emitText(reasoningContent, fullResponse, sink);
        }

        if (hasContent(content)) {
            if (inThinking.compareAndSet(true, false)) {
                emitText("</think>", fullResponse, sink);
            }
            finalAnswer.getAndAccumulate(content, String::concat);
            emitText(content, fullResponse, sink);
        }
    }

    private void emitText(String text, AtomicReference<String> fullResponse, reactor.core.publisher.FluxSink<String> sink) {
        if (!hasContent(text) || sink.isCancelled()) {
            return;
        }
        fullResponse.getAndAccumulate(text, String::concat);
        sink.next(text);
    }

    private boolean hasContent(String text) {
        return text != null && !text.isEmpty();
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isTextual()) {
                return value.asText();
            }
        }
        return "";
    }

    private void saveNativeChatMemory(String chatId, String prompt, String finalAnswer, String fullResponse) {
        if (!StringUtils.hasText(chatId)) {
            return;
        }
        String assistantText = StringUtils.hasText(finalAnswer) ? finalAnswer : fullResponse;
        chatMemory.add(chatId, List.of(new UserMessage(prompt), new AssistantMessage(assistantText)));
    }

    private String openAiChatCompletionsUrl() {
        String normalizedBaseUrl = openAiBaseUrl.trim();
        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        if (normalizedBaseUrl.endsWith("/v1")) {
            return normalizedBaseUrl + "/chat/completions";
        }
        return normalizedBaseUrl + "/v1/chat/completions";
    }

    private String toOpenAiRole(Message message) {
        return switch (message.getMessageType()) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
            default -> null;
        };
    }

    private String nativeSystemPrompt(String model, boolean deepThinking) {
        if (!deepThinking) {
            return "You are a helpful AI assistant.";
        }
        if (supportsQwenReasoningStream(model)) {
            return QWEN_NATIVE_DEEP_THINK_SYSTEM_PROMPT;
        }
        return """
                You are a thoughtful AI assistant. Think carefully before answering.
                If the runtime exposes reasoning metadata, use it. Otherwise answer directly and clearly
                without inventing hidden reasoning text.
                """;
    }

    private OpenAiChatOptions buildRuntimeOptions(String model, boolean toolCalling) {
        var builder = OpenAiChatOptions.builder()
                .temperature(toolCalling ? 0.1 : 0.8);
        if (toolCalling) {
            builder.parallelToolCalls(false)
                    .maxTokens(TOOL_CALL_MAX_TOKENS);
        }
        if (StringUtils.hasText(model)) {
            builder.model(model);
        }
        return builder.build();
    }

    private String appendSystemPrompt(String systemPrompt, String additionalSystemPrompt) {
        if (!StringUtils.hasText(additionalSystemPrompt)) {
            return systemPrompt;
        }
        if (!StringUtils.hasText(systemPrompt)) {
            return additionalSystemPrompt;
        }
        return systemPrompt + "\n\n" + additionalSystemPrompt;
    }

    private boolean supportsReasoningContent(String model) {
        if (!StringUtils.hasText(model)) {
            return false;
        }
        String normalizedModel = model.toLowerCase(Locale.ROOT);
        return normalizedModel.contains("deepseek-r1") || normalizedModel.contains("deepseek-reasoner");
    }

    private boolean supportsQwenReasoningStream(String model) {
        String normalizedModel = StringUtils.hasText(model) ? model : defaultChatModel;
        if (!StringUtils.hasText(normalizedModel)) {
            return false;
        }
        return normalizedModel.toLowerCase(Locale.ROOT).contains("qwen3");
    }

    private boolean canUseNativeOpenAiStream() {
        return StringUtils.hasText(openAiBaseUrl) && StringUtils.hasText(openAiApiKey);
    }
}
