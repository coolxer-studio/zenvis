package com.coolxer.configuration.ai;

import com.coolxer.model.dih.vo.SkillRuntimeLimitsVo;
import com.coolxer.service.dih.mcp.ToolRuntimeContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void returnsToolErrorForInvalidJsonSoModelCanRegenerateArgumentsOnce() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        Prompt prompt = new Prompt("test");
        ChatResponse response = responseWithFinishReason(
                "LENGTH",
                new AssistantMessage.ToolCall(
                        "call-1", "function", "truncated_arguments", "{\"ruleId\":1"
                )
        );

        ToolExecutionResult result =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(prompt, response);

        verify(delegate, never()).executeToolCalls(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(result.returnDirect()).isFalse();
        assertThat(result.conversationHistory()).hasSize(2);
        assertThat(result.conversationHistory().get(1)).isInstanceOf(UserMessage.class);
        assertThat(result.conversationHistory().get(1).getText())
                .contains("INVALID_TOOL_ARGUMENTS", "truncated_arguments")
                .doesNotContain("{\"ruleId\":1");
        assertThat(result.conversationHistory())
                .noneMatch(AssistantMessage.class::isInstance)
                .noneMatch(ToolResponseMessage.class::isInstance);
    }

    @Test
    void rejectsValidJsonThatIsNotAnObject() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall("call-1", "function", "array_arguments", "[1,2]")
        );

        ToolExecutionResult result =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(new Prompt("test"), response);

        verify(delegate, never()).executeToolCalls(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(correctionMessage(result)).contains("INVALID_TOOL_ARGUMENTS");
    }

    @Test
    void returnsToolErrorWhenNestedArgumentDoesNotMatchToolSchema() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().build();
        Prompt prompt = new Prompt("test", options);
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "retrieval_search",
                        "{\"request\":[{\"entity\":\"asset\"}]}"
                )
        );
        when(delegate.resolveToolDefinitions(same(options))).thenReturn(List.of(
                toolDefinition(
                        "retrieval_search",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "request": {
                                      "type": "object",
                                      "properties": {
                                        "entity": {"type": "string"}
                                      },
                                      "required": ["entity"]
                                    }
                                  },
                                  "required": ["request"],
                                  "additionalProperties": false
                                }
                                """
                )
        ));

        ToolExecutionResult result =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(prompt, response);

        verify(delegate, never()).executeToolCalls(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(correctionMessage(result))
                .contains("INVALID_TOOL_ARGUMENTS", "$.request", "expected object", "array");
    }

    @Test
    void delegatesWhenNestedArgumentsMatchToolSchema() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolExecutionResult expectedResult = mock(ToolExecutionResult.class);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().build();
        Prompt prompt = new Prompt("test", options);
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "retrieval_search",
                        "{\"request\":{\"entity\":\"asset\"}}"
                )
        );
        when(delegate.resolveToolDefinitions(same(options))).thenReturn(List.of(
                toolDefinition(
                        "retrieval_search",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "request": {
                                      "type": "object",
                                      "properties": {
                                        "entity": {"type": "string"}
                                      },
                                      "required": ["entity"]
                                    }
                                  },
                                  "required": ["request"],
                                  "additionalProperties": false
                                }
                                """
                )
        ));
        when(delegate.executeToolCalls(same(prompt), same(response))).thenReturn(expectedResult);

        ToolExecutionResult result =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(prompt, response);

        assertThat(result).isSameAs(expectedResult);
        verify(delegate).executeToolCalls(same(prompt), same(response));
    }

    @Test
    void returnsToolErrorWhenRequiredNestedArgumentIsMissing() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().build();
        Prompt prompt = new Prompt("test", options);
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "retrieval_search",
                        "{\"request\":{}}"
                )
        );
        when(delegate.resolveToolDefinitions(same(options))).thenReturn(List.of(
                toolDefinition(
                        "retrieval_search",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "request": {
                                      "type": "object",
                                      "properties": {
                                        "entity": {"type": "string"}
                                      },
                                      "required": ["entity"]
                                    }
                                  },
                                  "required": ["request"]
                                }
                                """
                )
        ));

        ToolExecutionResult result =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(prompt, response);

        verify(delegate, never()).executeToolCalls(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(correctionMessage(result))
                .contains(
                        "INVALID_TOOL_ARGUMENTS",
                        "$.request.entity",
                        "required",
                        "missing",
                        "topLevelProperties",
                        "request"
                );
    }

    @Test
    void stopsAfterModelReturnsInvalidArgumentsTwiceInARow() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolResponseMessage retryRequest = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "previous-call",
                        "truncated_arguments",
                        "{\"error\":{\"code\":\"INVALID_TOOL_ARGUMENTS\"}}"
                )))
                .build();
        Prompt prompt = new Prompt(List.of(retryRequest));
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall(
                        "call-2", "function", "truncated_arguments", "{\"ruleId\":\"two"
                )
        );

        ToolExecutionResult result =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(prompt, response);

        assertThat(result.returnDirect()).isTrue();
        assertThat(((ToolResponseMessage) result.conversationHistory().get(2))
                .getResponses().get(0).responseData())
                .contains("部分完成", "invalid_tool_arguments_repeated");
        verify(delegate, never()).executeToolCalls(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void normalizesJsonStringWhenSchemaRequiresNestedObject() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolExecutionResult expectedResult = mock(ToolExecutionResult.class);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().build();
        Prompt prompt = new Prompt("test", options);
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "retrieval_search",
                        "{\"request\":\"{\\\"entity\\\":\\\"asset\\\"}\"}"
                )
        );
        when(delegate.resolveToolDefinitions(same(options))).thenReturn(List.of(
                toolDefinition(
                        "retrieval_search",
                        """
                                {
                                  "type":"object",
                                  "properties":{
                                    "request":{
                                      "type":"object",
                                      "properties":{"entity":{"type":"string"}},
                                      "required":["entity"]
                                    }
                                  },
                                  "required":["request"]
                                }
                                """
                )
        ));
        when(delegate.executeToolCalls(same(prompt), org.mockito.ArgumentMatchers.any(ChatResponse.class)))
                .thenReturn(expectedResult);

        ToolExecutionResult result =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(prompt, response);

        ArgumentCaptor<ChatResponse> responseCaptor = ArgumentCaptor.forClass(ChatResponse.class);
        verify(delegate).executeToolCalls(same(prompt), responseCaptor.capture());
        assertThat(result).isSameAs(expectedResult);
        assertThat(responseCaptor.getValue().getResult().getOutput().getToolCalls().get(0).arguments())
                .isEqualTo("{\"request\":{\"entity\":\"asset\"}}");
    }

    @Test
    void completesOnlyMissingClosingDelimitersAndCanonicalizesArguments() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolExecutionResult expectedResult = mock(ToolExecutionResult.class);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().build();
        Prompt prompt = new Prompt("test", options);
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "retrieval_search",
                        "{\"request\":{\"entity\":\"asset\"}"
                )
        );
        when(delegate.resolveToolDefinitions(same(options))).thenReturn(List.of(
                requestObjectToolDefinition()
        ));
        when(delegate.executeToolCalls(same(prompt), org.mockito.ArgumentMatchers.any(ChatResponse.class)))
                .thenReturn(expectedResult);

        ToolExecutionResult result =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(prompt, response);

        ArgumentCaptor<ChatResponse> responseCaptor = ArgumentCaptor.forClass(ChatResponse.class);
        verify(delegate).executeToolCalls(same(prompt), responseCaptor.capture());
        assertThat(result).isSameAs(expectedResult);
        assertThat(responseCaptor.getValue().getResult().getOutput().getToolCalls().get(0).arguments())
                .isEqualTo("{\"request\":{\"entity\":\"asset\"}}");
    }

    @Test
    void canonicalizesBoundedPermissiveJsonWithoutGuessingValues() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolExecutionResult expectedResult = mock(ToolExecutionResult.class);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().build();
        Prompt prompt = new Prompt("test", options);
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "retrieval_search",
                        "{request: {entity: 'asset',},}"
                )
        );
        when(delegate.resolveToolDefinitions(same(options))).thenReturn(List.of(
                requestObjectToolDefinition()
        ));
        when(delegate.executeToolCalls(same(prompt), org.mockito.ArgumentMatchers.any(ChatResponse.class)))
                .thenReturn(expectedResult);

        ToolExecutionResult result =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(prompt, response);

        ArgumentCaptor<ChatResponse> responseCaptor = ArgumentCaptor.forClass(ChatResponse.class);
        verify(delegate).executeToolCalls(same(prompt), responseCaptor.capture());
        assertThat(result).isSameAs(expectedResult);
        assertThat(responseCaptor.getValue().getResult().getOutput().getToolCalls().get(0).arguments())
                .isEqualTo("{\"request\":{\"entity\":\"asset\"}}");
    }

    @Test
    void removesWholeJsonMarkdownFenceBeforeExecution() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolExecutionResult expectedResult = mock(ToolExecutionResult.class);
        Prompt prompt = new Prompt("test");
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "retrieval_search",
                        "```json\n{\"request\":{\"entity\":\"asset\"}}\n```"
                )
        );
        when(delegate.executeToolCalls(same(prompt), org.mockito.ArgumentMatchers.any(ChatResponse.class)))
                .thenReturn(expectedResult);

        ToolExecutionResult result =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(prompt, response);

        ArgumentCaptor<ChatResponse> responseCaptor = ArgumentCaptor.forClass(ChatResponse.class);
        verify(delegate).executeToolCalls(same(prompt), responseCaptor.capture());
        assertThat(result).isSameAs(expectedResult);
        assertThat(responseCaptor.getValue().getResult().getOutput().getToolCalls().get(0).arguments())
                .isEqualTo("{\"request\":{\"entity\":\"asset\"}}");
    }

    @Test
    void removesSingleTrailingFunctionCallSuffixBeforeExecution() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolExecutionResult expectedResult = mock(ToolExecutionResult.class);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().build();
        Prompt prompt = new Prompt("test", options);
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "retrieval_search",
                        "{\"request\":{\"entity\":\"asset\"}})"
                )
        );
        when(delegate.resolveToolDefinitions(same(options))).thenReturn(List.of(
                requestObjectToolDefinition()
        ));
        when(delegate.executeToolCalls(same(prompt), org.mockito.ArgumentMatchers.any(ChatResponse.class)))
                .thenReturn(expectedResult);

        ToolExecutionResult result =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(prompt, response);

        ArgumentCaptor<ChatResponse> responseCaptor = ArgumentCaptor.forClass(ChatResponse.class);
        verify(delegate).executeToolCalls(same(prompt), responseCaptor.capture());
        assertThat(result).isSameAs(expectedResult);
        assertThat(responseCaptor.getValue().getResult().getOutput().getToolCalls().get(0).arguments())
                .isEqualTo("{\"request\":{\"entity\":\"asset\"}}");
    }

    @Test
    void movesLeakedFieldsBackIntoSoleObjectParameter() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolExecutionResult expectedResult = mock(ToolExecutionResult.class);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().build();
        Prompt prompt = new Prompt("test", options);
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "retrieval_search",
                        """
                                {"request":{"entity":"asset"},"display_list":[],"page":1}
                                """
                )
        );
        when(delegate.resolveToolDefinitions(same(options))).thenReturn(List.of(
                requestObjectToolDefinition()
        ));
        when(delegate.executeToolCalls(same(prompt), org.mockito.ArgumentMatchers.any(ChatResponse.class)))
                .thenReturn(expectedResult);

        ToolExecutionResult result =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(prompt, response);

        ArgumentCaptor<ChatResponse> responseCaptor = ArgumentCaptor.forClass(ChatResponse.class);
        verify(delegate).executeToolCalls(same(prompt), responseCaptor.capture());
        assertThat(result).isSameAs(expectedResult);
        assertThat(responseCaptor.getValue().getResult().getOutput().getToolCalls().get(0).arguments())
                .isEqualTo("{\"request\":{\"entity\":\"asset\",\"display_list\":[],\"page\":1}}");
    }

    @Test
    void wrapsFlatFieldsWhenSchemaHasOneUnambiguousObjectParameter() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolExecutionResult expectedResult = mock(ToolExecutionResult.class);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().build();
        Prompt prompt = new Prompt("test", options);
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "retrieval_search",
                        "{\"entity\":\"asset\",\"page\":1}"
                )
        );
        when(delegate.resolveToolDefinitions(same(options))).thenReturn(List.of(
                requestObjectToolDefinition()
        ));
        when(delegate.executeToolCalls(same(prompt), org.mockito.ArgumentMatchers.any(ChatResponse.class)))
                .thenReturn(expectedResult);

        ToolExecutionResult result =
                new OpenAiCompatibleToolCallingManager(delegate).executeToolCalls(prompt, response);

        ArgumentCaptor<ChatResponse> responseCaptor = ArgumentCaptor.forClass(ChatResponse.class);
        verify(delegate).executeToolCalls(same(prompt), responseCaptor.capture());
        assertThat(result).isSameAs(expectedResult);
        assertThat(responseCaptor.getValue().getResult().getOutput().getToolCalls().get(0).arguments())
                .isEqualTo("{\"request\":{\"entity\":\"asset\",\"page\":1}}");
    }

    @Test
    void rejectsTrailingJsonAndNeverReturnsRawMalformedArgumentsToProviderHistory() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "retrieval_search",
                        "{\"request\":{}} {\"extra\":true}"
                )
        );

        ToolExecutionResult result =
                new OpenAiCompatibleToolCallingManager(delegate)
                        .executeToolCalls(new Prompt("test"), response);

        assertThat(result.conversationHistory())
                .noneMatch(AssistantMessage.class::isInstance)
                .noneMatch(ToolResponseMessage.class::isInstance);
        assertThat(correctionMessage(result))
                .contains("INVALID_TOOL_ARGUMENTS")
                .doesNotContain("{\"request\":{}} {\"extra\":true}");
    }

    @Test
    void truncatesToolResultsAndStopsAdditionalCallsAtRuntimeBudget() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().build();
        ToolRuntimeContext runtimeContext = new ToolRuntimeContext(
                new SkillRuntimeLimitsVo(1, 2, 12, 12));
        options.setToolContext(Map.of(ToolRuntimeContext.TOOL_CONTEXT_KEY, runtimeContext));
        Prompt prompt = new Prompt("test", options);
        ChatResponse response = responseWithToolCalls(
                new AssistantMessage.ToolCall("call-1", "function", "retrieval_search", "{}")
        );
        ToolExecutionResult delegateResult = ToolExecutionResult.builder()
                .conversationHistory(List.of(
                        response.getResult().getOutput(),
                        ToolResponseMessage.builder()
                                .responses(List.of(new ToolResponseMessage.ToolResponse(
                                        "call-1",
                                        "retrieval_search",
                                        "{\"rows\":[\"abcdefghijklmnopqrstuvwxyz\"]}"
                                )))
                                .build()
                ))
                .returnDirect(false)
                .build();
        when(delegate.executeToolCalls(same(prompt), same(response))).thenReturn(delegateResult);
        OpenAiCompatibleToolCallingManager manager =
                new OpenAiCompatibleToolCallingManager(delegate);

        ToolExecutionResult first = manager.executeToolCalls(prompt, response);
        ToolResponseMessage firstToolResponse =
                (ToolResponseMessage) first.conversationHistory().get(1);
        assertThat(firstToolResponse.getResponses().get(0).responseData())
                .contains("\"truncated\":true", "\"stop\":true", "tool_call_budget_exhausted");

        ToolExecutionResult second = manager.executeToolCalls(prompt, response);
        assertThat(second.returnDirect()).isTrue();
        assertThat(((ToolResponseMessage) second.conversationHistory().get(2))
                .getResponses().get(0).responseData())
                .contains("tool_call_budget_exhausted", "部分完成");
        verify(delegate).executeToolCalls(same(prompt), same(response));
    }

    @Test
    void accumulatedResultBudgetCountsOnlyTheLatestToolResponse() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().build();
        ToolRuntimeContext runtimeContext = new ToolRuntimeContext(
                new SkillRuntimeLimitsVo(4, 2, 20, 25));
        options.setToolContext(Map.of(ToolRuntimeContext.TOOL_CONTEXT_KEY, runtimeContext));
        Prompt prompt = new Prompt("test", options);
        ChatResponse firstCall = responseWithToolCalls(
                new AssistantMessage.ToolCall("call-1", "function", "retrieval_search", "{}"));
        ChatResponse secondCall = responseWithToolCalls(
                new AssistantMessage.ToolCall("call-2", "function", "retrieval_search", "{}"));
        ToolResponseMessage firstToolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call-1", "retrieval_search", "1234567890")))
                .build();
        ToolResponseMessage secondToolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call-2", "retrieval_search", "abcdefghij")))
                .build();
        when(delegate.executeToolCalls(same(prompt), same(firstCall))).thenReturn(
                ToolExecutionResult.builder()
                        .conversationHistory(List.of(
                                firstCall.getResult().getOutput(), firstToolResponse))
                        .returnDirect(false)
                        .build());
        when(delegate.executeToolCalls(same(prompt), same(secondCall))).thenReturn(
                ToolExecutionResult.builder()
                        .conversationHistory(List.of(
                                firstCall.getResult().getOutput(),
                                firstToolResponse,
                                secondCall.getResult().getOutput(),
                                secondToolResponse))
                        .returnDirect(false)
                        .build());
        OpenAiCompatibleToolCallingManager manager =
                new OpenAiCompatibleToolCallingManager(delegate);

        manager.executeToolCalls(prompt, firstCall);
        ToolExecutionResult second = manager.executeToolCalls(prompt, secondCall);

        assertThat(runtimeContext.accumulatedToolResultChars()).isEqualTo(20);
        assertThat(runtimeContext.stopRequested()).isFalse();
        assertThat(((ToolResponseMessage) second.conversationHistory().get(1))
                .getResponses().get(0).responseData()).isEqualTo("1234567890");
        assertThat(((ToolResponseMessage) second.conversationHistory().get(3))
                .getResponses().get(0).responseData()).isEqualTo("abcdefghij");
    }

    @Test
    void stopsAfterRepeatedFieldFailuresEvenWhenModelGuessesDifferentAttributeIds() {
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().build();
        ToolRuntimeContext runtimeContext = new ToolRuntimeContext(
                new SkillRuntimeLimitsVo(16, 2, 12_000, 48_000));
        options.setToolContext(Map.of(ToolRuntimeContext.TOOL_CONTEXT_KEY, runtimeContext));
        Prompt prompt = new Prompt("test", options);
        ChatResponse firstCall = responseWithToolCalls(
                new AssistantMessage.ToolCall(
                        "call-1", "function", "retrieval_search", "{\"attribute_id\":520101}")
        );
        ChatResponse secondCall = responseWithToolCalls(
                new AssistantMessage.ToolCall(
                        "call-2", "function", "retrieval_search", "{\"attribute_id\":520199}")
        );
        when(delegate.executeToolCalls(same(prompt), same(firstCall)))
                .thenReturn(toolFailure(firstCall, "attribute id 520101 not found"));
        when(delegate.executeToolCalls(same(prompt), same(secondCall)))
                .thenReturn(toolFailure(secondCall, "attribute id 520199 not found"));
        OpenAiCompatibleToolCallingManager manager =
                new OpenAiCompatibleToolCallingManager(delegate);

        ToolExecutionResult first = manager.executeToolCalls(prompt, firstCall);
        assertThat(runtimeContext.stopRequested()).isFalse();
        assertThat(((ToolResponseMessage) first.conversationHistory().get(1))
                .getResponses().get(0).responseData()).doesNotContain("\"stop\":true");

        ToolExecutionResult second = manager.executeToolCalls(prompt, secondCall);
        assertThat(runtimeContext.stopRequested()).isTrue();
        assertThat(runtimeContext.stopReason()).isEqualTo("repeated_tool_failure");
        assertThat(((ToolResponseMessage) second.conversationHistory().get(1))
                .getResponses().get(0).responseData())
                .contains("\"stop\":true", "repeated_tool_failure");

        ToolExecutionResult terminal = manager.executeToolCalls(prompt, secondCall);
        assertThat(terminal.returnDirect()).isTrue();
        assertThat(((ToolResponseMessage) terminal.conversationHistory().get(2))
                .getResponses().get(0).responseData())
                .contains("部分完成", "repeated_tool_failure");
    }

    private ChatResponse responseWithToolCalls(AssistantMessage.ToolCall... toolCalls) {
        return responseWithFinishReason(null, toolCalls);
    }

    private ChatResponse responseWithFinishReason(
            String finishReason,
            AssistantMessage.ToolCall... toolCalls
    ) {
        AssistantMessage message = new AssistantMessage(
                "calling tools",
                Map.of("reasoning_content", "reason"),
                List.of(toolCalls)
        );
        ChatGenerationMetadata metadata = finishReason == null
                ? ChatGenerationMetadata.NULL
                : ChatGenerationMetadata.builder().finishReason(finishReason).build();
        return new ChatResponse(List.of(new Generation(message, metadata)));
    }

    private ToolDefinition toolDefinition(String name, String inputSchema) {
        return ToolDefinition.builder()
                .name(name)
                .description(name)
                .inputSchema(inputSchema)
                .build();
    }

    private ToolDefinition requestObjectToolDefinition() {
        return toolDefinition(
                "retrieval_search",
                """
                        {
                          "type":"object",
                          "properties":{
                            "request":{
                              "type":"object",
                              "properties":{
                                "entity":{"type":"string"},
                                "display_list":{"type":"array"},
                                "page":{"type":"integer"}
                              },
                              "required":["entity"]
                            }
                          },
                          "required":["request"],
                          "additionalProperties":false
                        }
                        """
        );
    }

    private String correctionMessage(ToolExecutionResult result) {
        List<Message> history = result.conversationHistory();
        return history.get(history.size() - 1).getText();
    }

    private ToolExecutionResult toolFailure(ChatResponse response, String message) {
        return ToolExecutionResult.builder()
                .conversationHistory(List.of(
                        response.getResult().getOutput(),
                        ToolResponseMessage.builder()
                                .responses(List.of(new ToolResponseMessage.ToolResponse(
                                        response.getResult().getOutput().getToolCalls().get(0).id(),
                                        "retrieval_search",
                                        "{\"status\":\"failed\",\"message\":\"" + message + "\"}"
                                )))
                                .build()
                ))
                .returnDirect(false)
                .build();
    }
}
