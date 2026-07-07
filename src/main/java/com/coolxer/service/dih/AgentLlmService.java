package com.coolxer.service.dih;

import com.coolxer.service.dih.logging.LlmLogHelper;
import com.coolxer.service.dih.mcp.McpToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用 LLM 调用封装。
 */
public class AgentLlmService {

    private static final Logger log = LoggerFactory.getLogger(AgentLlmService.class);

    private static final ThreadLocal<String> CURRENT_MODEL = new ThreadLocal<>();

    private static final ThreadLocal<ToolCallbackProvider> CURRENT_TOOL_CALLBACK_PROVIDER = new ThreadLocal<>();

    private static final ThreadLocal<String> CURRENT_TOOL_SYSTEM_PROMPT = new ThreadLocal<>();

    private final ChatClient chatClient;

    public AgentLlmService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public void setModel(String model) {
        if (StringUtils.hasText(model)) {
            CURRENT_MODEL.set(model);
        }
        else {
            CURRENT_MODEL.remove();
        }
    }

    public void clearModel() {
        CURRENT_MODEL.remove();
    }

    public void setMcpToolContext(McpToolContext context) {
        if (context != null && context.hasTools()) {
            CURRENT_TOOL_CALLBACK_PROVIDER.set(context.toolCallbackProvider());
            CURRENT_TOOL_SYSTEM_PROMPT.set(context.systemPrompt());
        }
        else {
            clearMcpToolContext();
        }
    }

    public void clearMcpToolContext() {
        CURRENT_TOOL_CALLBACK_PROVIDER.remove();
        CURRENT_TOOL_SYSTEM_PROMPT.remove();
    }

    public String call(String prompt) {
        String scene = "AgentLlmService.call";
        String requestId = LlmLogHelper.newRequestId();
        long startedAtNanos = System.nanoTime();
        OpenAiChatOptions options = buildModelOptions();
        var spec = chatClient.prompt().user(prompt);
        if (StringUtils.hasText(currentToolSystemPrompt())) {
            spec = spec.system(currentToolSystemPrompt());
        }
        if (options != null) {
            spec = spec.options(options);
        }
        if (currentToolCallbackProvider() != null) {
            spec = spec.toolCallbacks(currentToolCallbackProvider());
        }
        LlmLogHelper.logRequest(log, requestId, scene, buildLogRequest(null, prompt, false));
        try {
            String response = spec.call().content();
            LlmLogHelper.logResponse(log, requestId, scene, response, startedAtNanos);
            return response;
        }
        catch (RuntimeException e) {
            LlmLogHelper.logError(log, requestId, scene, null, startedAtNanos, e);
            throw e;
        }
    }

    public String callWithSystemPrompt(String systemPrompt, String userPrompt) {
        String scene = "AgentLlmService.callWithSystemPrompt";
        String requestId = LlmLogHelper.newRequestId();
        long startedAtNanos = System.nanoTime();
        OpenAiChatOptions options = buildModelOptions();
        var spec = chatClient.prompt().system(appendToolSystemPrompt(systemPrompt)).user(userPrompt);
        if (options != null) {
            spec = spec.options(options);
        }
        if (currentToolCallbackProvider() != null) {
            spec = spec.toolCallbacks(currentToolCallbackProvider());
        }
        LlmLogHelper.logRequest(log, requestId, scene, buildLogRequest(systemPrompt, userPrompt, false));
        try {
            String response = spec.call().content();
            LlmLogHelper.logResponse(log, requestId, scene, response, startedAtNanos);
            return response;
        }
        catch (RuntimeException e) {
            LlmLogHelper.logError(log, requestId, scene, null, startedAtNanos, e);
            throw e;
        }
    }

    public Flux<ChatResponse> streamCall(String prompt) {
        String scene = "AgentLlmService.streamCall";
        String requestId = LlmLogHelper.newRequestId();
        long startedAtNanos = System.nanoTime();
        OpenAiChatOptions options = buildModelOptions();
        var spec = chatClient.prompt().user(prompt);
        if (StringUtils.hasText(currentToolSystemPrompt())) {
            spec = spec.system(currentToolSystemPrompt());
        }
        if (options != null) {
            spec = spec.options(options);
        }
        if (currentToolCallbackProvider() != null) {
            spec = spec.toolCallbacks(currentToolCallbackProvider());
        }
        LlmLogHelper.logRequest(log, requestId, scene, buildLogRequest(null, prompt, true));
        return LlmLogHelper.logChatResponseStream(log, requestId, scene, spec.stream().chatResponse(), startedAtNanos);
    }

    public Flux<ChatResponse> streamCallWithSystemPrompt(String systemPrompt, String userPrompt) {
        String scene = "AgentLlmService.streamCallWithSystemPrompt";
        String requestId = LlmLogHelper.newRequestId();
        long startedAtNanos = System.nanoTime();
        OpenAiChatOptions options = buildModelOptions();
        var spec = chatClient.prompt().system(appendToolSystemPrompt(systemPrompt)).user(userPrompt);
        if (options != null) {
            spec = spec.options(options);
        }
        if (currentToolCallbackProvider() != null) {
            spec = spec.toolCallbacks(currentToolCallbackProvider());
        }
        LlmLogHelper.logRequest(log, requestId, scene, buildLogRequest(systemPrompt, userPrompt, true));
        return LlmLogHelper.logChatResponseStream(log, requestId, scene, spec.stream().chatResponse(), startedAtNanos);
    }

    private OpenAiChatOptions buildModelOptions() {
        String model = CURRENT_MODEL.get();
        if (model == null) {
            return null;
        }
        return OpenAiChatOptions.builder()
                .model(model)
                .build();
    }

    private ToolCallbackProvider currentToolCallbackProvider() {
        return CURRENT_TOOL_CALLBACK_PROVIDER.get();
    }

    private String currentToolSystemPrompt() {
        return CURRENT_TOOL_SYSTEM_PROMPT.get();
    }

    private String appendToolSystemPrompt(String systemPrompt) {
        String toolSystemPrompt = currentToolSystemPrompt();
        if (!StringUtils.hasText(toolSystemPrompt)) {
            return systemPrompt;
        }
        if (!StringUtils.hasText(systemPrompt)) {
            return toolSystemPrompt;
        }
        return systemPrompt + "\n\n" + toolSystemPrompt;
    }

    private Map<String, Object> buildLogRequest(String systemPrompt, String userPrompt, boolean stream) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", CURRENT_MODEL.get());
        request.put("stream", stream);
        request.put("system_prompt", systemPrompt);
        request.put("user_prompt", userPrompt);
        return request;
    }
}
