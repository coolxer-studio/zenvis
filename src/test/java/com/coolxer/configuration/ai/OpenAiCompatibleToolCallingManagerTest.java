package com.coolxer.configuration.ai;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiCompatibleToolCallingManagerTest {

    @Test
    void replacesAutoConfiguredToolCallingManagerInApplicationContext() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
                .withUserConfiguration(OpenAiToolCallingConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ToolCallingManager.class);
                    assertThat(context.getBean(ToolCallingManager.class))
                            .isInstanceOf(OpenAiCompatibleToolCallingManager.class);
                });
    }

    @Test
    void normalizesMissingAndBlankArgumentsBeforeToolExecution() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolExecutionResult expectedResult = mock(ToolExecutionResult.class);
        Prompt prompt = new Prompt("test");
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall("call-1", "function", "no_arguments", null),
                new AssistantMessage.ToolCall("call-2", "function", "blank_arguments", "  "),
                new AssistantMessage.ToolCall("call-3", "function", "with_arguments", "{\"ruleId\":1}")
        );
        when(delegate.executeToolCalls(same(prompt), org.mockito.ArgumentMatchers.any(ChatResponse.class)))
                .thenReturn(expectedResult);

        ToolExecutionResult actualResult =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(prompt, response);

        ArgumentCaptor<ChatResponse> responseCaptor = ArgumentCaptor.forClass(ChatResponse.class);
        verify(delegate).executeToolCalls(same(prompt), responseCaptor.capture());
        assertThat(actualResult).isSameAs(expectedResult);
        assertThat(responseCaptor.getValue()).isNotSameAs(response);
        assertThat(responseCaptor.getValue().getResult().getOutput().getToolCalls())
                .extracting(AssistantMessage.ToolCall::arguments)
                .containsExactly("{}", "{}", "{\"ruleId\":1}");
        assertThat(responseCaptor.getValue().getResult().getOutput().getText()).isEqualTo("calling tools");
        assertThat(responseCaptor.getValue().getResult().getOutput().getMetadata())
                .containsEntry("reasoning_content", "reason");
    }

    @Test
    void keepsResponseInstanceWhenAllArgumentsArePresent() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolExecutionResult expectedResult = mock(ToolExecutionResult.class);
        Prompt prompt = new Prompt("test");
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall("call-1", "function", "with_arguments", "{}")
        );
        when(delegate.executeToolCalls(prompt, response)).thenReturn(expectedResult);

        ToolExecutionResult actualResult =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(prompt, response);

        assertThat(actualResult).isSameAs(expectedResult);
        verify(delegate).executeToolCalls(same(prompt), same(response));
    }

    private ChatResponse responseWithToolCalls(AssistantMessage.ToolCall... toolCalls) {
        AssistantMessage message = new AssistantMessage(
                "calling tools",
                Map.of("reasoning_content", "reason"),
                List.of(toolCalls)
        );
        return new ChatResponse(List.of(new Generation(message)));
    }
}
