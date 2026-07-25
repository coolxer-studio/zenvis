package com.coolxer.service.dih;

import com.coolxer.service.dih.logging.LlmLogHelper;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.ToolRuntimeContext;
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

    private static final int TOOL_CALL_MAX_TOKENS = 4096;

    private static final ThreadLocal<String> CURRENT_MODEL = new ThreadLocal<>();

    private static final ThreadLocal<ToolCallbackProvider> CURRENT_TOOL_CALLBACK_PROVIDER = new ThreadLocal<>();

    private static final ThreadLocal<String> CURRENT_TOOL_SYSTEM_PROMPT = new ThreadLocal<>();

    private static final ThreadLocal<McpInvocationContext> CURRENT_TOOL_INVOCATION_CONTEXT = new ThreadLocal<>();

    private static final ThreadLocal<ToolRuntimeContext> CURRENT_TOOL_RUNTIME_CONTEXT = new ThreadLocal<>();

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
            CURRENT_TOOL_INVOCATION_CONTEXT.set(context.invocationContext() == null
                    ? McpInvocationContext.background(null) : context.invocationContext());
            if (context.toolRuntimeContext() != null && context.toolRuntimeContext().hasLimits()) {
                CURRENT_TOOL_RUNTIME_CONTEXT.set(context.toolRuntimeContext());
            } else {
                CURRENT_TOOL_RUNTIME_CONTEXT.remove();
            }
        }
        else {
            clearMcpToolContext();
        }
    }

    public void clearMcpToolContext() {
        CURRENT_TOOL_CALLBACK_PROVIDER.remove();
        CURRENT_TOOL_SYSTEM_PROMPT.remove();
        CURRENT_TOOL_INVOCATION_CONTEXT.remove();
        CURRENT_TOOL_RUNTIME_CONTEXT.remove();
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
            spec = applyToolContext(spec);
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
            spec = applyToolContext(spec);
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
            spec = applyToolContext(spec);
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
            spec = applyToolContext(spec);
        }
        LlmLogHelper.logRequest(log, requestId, scene, buildLogRequest(systemPrompt, userPrompt, true));
        return LlmLogHelper.logChatResponseStream(log, requestId, scene, spec.stream().chatResponse(), startedAtNanos);
    }

    private OpenAiChatOptions buildModelOptions() {
        String model = CURRENT_MODEL.get();
        boolean toolCalling = currentToolCallbackProvider() != null;
        if (model == null && !toolCalling) {
            return null;
        }
        var builder = OpenAiChatOptions.builder();
        if (model != null) {
            builder.model(model);
        }
        if (toolCalling) {
            builder.temperature(0.1)
                    .parallelToolCalls(false)
                    .maxTokens(TOOL_CALL_MAX_TOKENS);
        }
        return builder.build();
    }

    private ToolCallbackProvider currentToolCallbackProvider() {
        return CURRENT_TOOL_CALLBACK_PROVIDER.get();
    }

    private String currentToolSystemPrompt() {
        return CURRENT_TOOL_SYSTEM_PROMPT.get();
    }

    private ChatClient.ChatClientRequestSpec applyToolContext(ChatClient.ChatClientRequestSpec spec) {
        McpInvocationContext context = CURRENT_TOOL_INVOCATION_CONTEXT.get();
        ToolRuntimeContext runtimeContext = CURRENT_TOOL_RUNTIME_CONTEXT.get();
        Map<String, Object> toolContext = new LinkedHashMap<>();
        if (context != null) {
            toolContext.put(McpInvocationContext.TOOL_CONTEXT_KEY, context);
        }
        if (runtimeContext != null) {
            toolContext.put(ToolRuntimeContext.TOOL_CONTEXT_KEY, runtimeContext);
        }
        return toolContext.isEmpty() ? spec : spec.toolContext(toolContext);
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
