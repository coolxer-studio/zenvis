package com.coolxer.configuration.ai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * Ensures tool calls returned by OpenAI-compatible providers can be sent back in
 * the next chat-completions request.
 *
 * <p>Some providers omit {@code function.arguments} when a tool has no required
 * arguments. Spring AI preserves that null value in the assistant message, but
 * the OpenAI request schema requires {@code arguments} to be a JSON string. The
 * follow-up request is then rejected before the tool result can be processed.</p>
 */
final class OpenAiCompatibleToolCallingManager implements ToolCallingManager {

    private static final String EMPTY_ARGUMENTS = "{}";

    private final ToolCallingManager delegate;

    OpenAiCompatibleToolCallingManager(ToolCallingManager delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        return delegate.executeToolCalls(prompt, normalizeToolArguments(chatResponse));
    }

    private ChatResponse normalizeToolArguments(ChatResponse chatResponse) {
        if (chatResponse == null || !chatResponse.hasToolCalls()) {
            return chatResponse;
        }

        boolean requiresNormalization = chatResponse.getResults().stream()
                .map(Generation::getOutput)
                .flatMap(message -> message.getToolCalls().stream())
                .anyMatch(toolCall -> !StringUtils.hasText(toolCall.arguments()));
        if (!requiresNormalization) {
            return chatResponse;
        }

        List<Generation> generations = chatResponse.getResults().stream()
                .map(this::normalizeGeneration)
                .toList();
        return ChatResponse.builder()
                .from(chatResponse)
                .generations(generations)
                .build();
    }

    private Generation normalizeGeneration(Generation generation) {
        AssistantMessage message = generation.getOutput();
        List<AssistantMessage.ToolCall> normalizedToolCalls = message.getToolCalls().stream()
                .map(this::normalizeToolCall)
                .toList();
        AssistantMessage normalizedMessage = new AssistantMessage(
                message.getText(),
                message.getMetadata(),
                normalizedToolCalls,
                message.getMedia()
        );
        return new Generation(normalizedMessage, generation.getMetadata());
    }

    private AssistantMessage.ToolCall normalizeToolCall(AssistantMessage.ToolCall toolCall) {
        if (StringUtils.hasText(toolCall.arguments())) {
            return toolCall;
        }
        return new AssistantMessage.ToolCall(
                toolCall.id(),
                toolCall.type(),
                toolCall.name(),
                EMPTY_ARGUMENTS
        );
    }
}
