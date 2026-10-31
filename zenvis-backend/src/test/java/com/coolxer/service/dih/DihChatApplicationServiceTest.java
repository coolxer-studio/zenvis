package com.coolxer.service.dih;

import com.coolxer.commons.enums.MessageType;
import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.commons.enums.McpInvocationStatus;
import com.coolxer.commons.enums.McpToolRiskLevel;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.RagSearchEventData;
import com.coolxer.model.dih.dto.ChatDto;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.model.dih.dto.ChatSessionSearchDto;
import com.coolxer.model.dih.dto.ReportActionDto;
import com.coolxer.model.dih.vo.ChatSessionVo;
import com.coolxer.model.dih.vo.McpApprovalVo;
import com.coolxer.service.dih.agent.DataPushServiceAgent;
import com.coolxer.service.dih.agent.MetadataConfigAgent;
import com.coolxer.service.dih.agent.ReportAgent;
import com.coolxer.service.dih.agent.VisualizationAgentTypes;
import com.coolxer.service.dih.agent.VisualizationAppAgent;
import com.coolxer.service.dih.agent.VisualizationChartAgent;
import com.coolxer.service.dih.agent.VisualizationDashboardAgent;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.agentrun.AgentRunService;
import com.coolxer.service.dih.demo.AgentDemoStateStore;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.mcp.McpToolCallLoggingProvider;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpApprovalEvent;
import com.coolxer.service.dih.workflow.AgentWorkflowState;
import com.coolxer.service.dih.workflow.AgentWorkflowStep;
import com.coolxer.service.dih.workflow.WorkflowStateStore;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.system.DashboardService;
import com.coolxer.service.system.MenuService;
import com.coolxer.service.system.PushTaskService;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.coolxer.service.dih.ReportDemoResponseService.REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DihChatApplicationServiceTest {

    private static final String CONTEXT_LENGTH_EXCEEDED_MESSAGE =
            "当前对话内容过长，已超过模型可处理的上下文长度。请新建对话，或减少历史消息、附件及输入内容后重试。";

    @Test
    void interactiveWorkflowCardsPauseAPersistentRunForUserInput() {
        DihChatApplicationService service = streamingService();
        List<String> interactiveTypes = new ArrayList<>(List.of(
                "confirm",
                "info-steps",
                "metadata-config-decision",
                "data-push-service-decision"));
        VisualizationAgentTypes.ALL.stream()
                .map(VisualizationAgentTypes::decisionPartType)
                .forEach(interactiveTypes::add);
        for (String type : interactiveTypes) {
            String card = "```zenvis:" + type + "\n"
                    + "{\"title\":\"需要确认\",\"content\":\"请处理当前卡片\"}\n"
                    + "```\n";
            assertThat(service.requiresUserDecision(card))
                    .as("interactive card type %s", type)
                    .isTrue();
        }

        String notice = """
                ```zenvis:notice
                {"title":"说明","content":"只是提示","level":"info"}
                ```
                """;
        String preview = """
                ```zenvis:visualization-chart-preview
                {"title":"趋势图","chartType":"line","echartsOption":{"series":[]}}
                ```
                """;

        assertThat(service.requiresUserDecision(notice)).isFalse();
        assertThat(service.requiresUserDecision("普通文本回答")).isFalse();
        assertThat(service.requiresUserDecision(preview)).isFalse();
        for (String type : List.of(
                "visualization-chart-record",
                "visualization-app-record",
                "visualization-dashboard-record")) {
            String record = "```zenvis:" + type + "\n"
                    + "{\"title\":\"记录\",\"name\":\"记录\"}\n"
                    + "```\n";
            assertThat(service.requiresUserDecision(record))
                    .as("non-interactive record type %s", type)
                    .isFalse();
        }
    }

    @Test
    void waitingQuestionUsesSpecificWorkflowCardRequirements() {
        DihChatApplicationService service = streamingService();
        String content = """
                ```zenvis:info-steps
                {"title":"缺少接入信息","content":"请补充后继续。","steps":[{"title":"Kafka 地址","description":"填写 Bootstrap Servers。","required":true,"placeholder":"kafka:9092"}]}
                ```
                """;

        String question = service.resolveUserDecisionQuestion(content);

        assertThat(question)
                .contains("缺少接入信息")
                .contains("Kafka 地址：填写 Bootstrap Servers。")
                .contains("填写提示：kafka:9092")
                .doesNotContain("请处理当前智能体工作流卡片后继续");
    }

    @Test
    void persistentRunResponseKeepsRunIdAndStructuredParts() {
        DihChatApplicationService service = streamingService();
        String content = """
                ```zenvis:metadata-config-decision
                {"title":"元数据配置已生成","content":"请选择后续处理","actions":["apply_config","abandon"]}
                ```
                """;

        Message message = service.saveAgentRunResponse(
                null, null, content, MessageType.TEXT, null, "run-1");

        assertThat(message.getRunId()).isEqualTo("run-1");
        assertThat(message.getParts()).singleElement()
                .satisfies(part -> assertThat(part.getType())
                        .isEqualTo("metadata-config-decision"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectionRewriteIsReturnedAsFragmentAndNeverAsFullDocument() {
        DihChatApplicationService service = emptyService();
        ReportActionDto action = new ReportActionDto();
        action.setType(ReportActionDto.SELECTION_REWRITE);
        action.setDocumentId("doc-1");
        action.setBaseRevision(3L);
        action.setSelectionId("selection-1");
        action.setSelectionHash("before-hash");
        ChatMessagePart generated = ChatMessagePart.builder()
                .type("report-document")
                .content("改写后的片段")
                .metadata(Map.of("format", "markdown"))
                .build();

        List<ChatMessagePart> parts = ReflectionTestUtils.invokeMethod(
                service,
                "applyReportProtocol",
                List.of(generated),
                "改写后的片段",
                action,
                List.of(Map.of("type", "attachment", "id", "file-1")),
                "message-1",
                null
        );

        assertThat(parts).isNotNull();
        assertThat(parts).extracting(ChatMessagePart::getType)
                .containsExactly("report-fragment");
        assertThat(parts.get(0).getMetadata())
                .containsEntry("documentId", "doc-1")
                .containsEntry("baseRevision", 3L)
                .containsEntry("selectionHash", "before-hash");
    }

    @Test
    @SuppressWarnings("unchecked")
    void duplicateFullReportsAreRejectedInsteadOfSilentlyOverwriting() {
        DihChatApplicationService service = emptyService();
        ReportActionDto action = new ReportActionDto();
        action.setType(ReportActionDto.FULL_REWRITE);
        List<ChatMessagePart> source = List.of(
                ChatMessagePart.builder().type("report-document").content("# A").build(),
                ChatMessagePart.builder().type("report-document").content("# B").build());

        List<ChatMessagePart> parts = ReflectionTestUtils.invokeMethod(
                service,
                "applyReportProtocol",
                source,
                "",
                action,
                List.of(),
                "message-1",
                null
        );

        assertThat(parts).isNotNull();
        assertThat(parts.stream().filter(part -> "report-document".equals(part.getType())))
                .allMatch(part -> "failed".equals(part.getStatus()));
        assertThat(parts).anyMatch(part ->
                "notice".equals(part.getType()) && part.getContent().contains("重复"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void truncatedReportHidesRawHtmlAndAddsActionableNotice() {
        DihChatApplicationService service = emptyService();
        ReportActionDto action = new ReportActionDto();
        action.setType(ReportActionDto.FULL_REWRITE);
        String raw = """
                正在生成本期报表。

                ```zenvis:report-document-config
                <!DOCTYPE html>
                <html lang="zh-CN"><body><p>分布特征解读：
                """;
        ChatMessagePart leakedMarkdown = ChatMessagePart.builder()
                .type("markdown")
                .content(raw)
                .build();

        List<ChatMessagePart> parts = ReflectionTestUtils.invokeMethod(
                service,
                "applyReportProtocol",
                List.of(leakedMarkdown),
                raw,
                action,
                List.of(),
                "message-1",
                null,
                true
        );

        assertThat(parts).isNotNull();
        assertThat(parts).noneMatch(part -> part.getContent() != null
                && part.getContent().toLowerCase().contains("<!doctype html"));
        assertThat(parts).anyMatch(part -> "notice".equals(part.getType())
                && "报表输出被截断".equals(part.getTitle())
                && part.getContent().contains("未完整生成"));
        assertThat(parts).anyMatch(part -> "markdown".equals(part.getType())
                && "正在生成本期报表。".equals(part.getContent()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unclosedReportFenceWithoutLengthSignalIsReportedAsIncomplete() {
        DihChatApplicationService service = emptyService();
        String raw = """
                报表生成中。

                ```zenvis:report-document-config
                <html><body><p>未闭合
                """;
        ChatMessagePart leakedMarkdown = ChatMessagePart.builder()
                .type("markdown")
                .content(raw)
                .build();

        List<ChatMessagePart> parts = ReflectionTestUtils.invokeMethod(
                service,
                "applyReportProtocol",
                List.of(leakedMarkdown),
                raw,
                null,
                List.of(),
                "message-2",
                null,
                false
        );

        assertThat(parts).isNotNull();
        assertThat(parts).noneMatch(part -> part.getContent() != null
                && part.getContent().toLowerCase().contains("<html"));
        assertThat(parts).anyMatch(part -> "notice".equals(part.getType())
                && "报表格式不完整".equals(part.getTitle()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void truncatedReportMessageFallbackExcludesRawHtml() {
        DihChatApplicationService service = streamingService();
        ReportActionDto action = new ReportActionDto();
        action.setType(ReportActionDto.FULL_REWRITE);
        String raw = """
                即将生成报表。

                ```zenvis:report-document-config
                <!DOCTYPE html>
                <html><body><p>未完成
                """;

        Message message = ReflectionTestUtils.invokeMethod(
                service,
                "saveAiResponse",
                null,
                null,
                raw,
                MessageType.TEXT,
                true,
                false,
                List.of(),
                null,
                null,
                action,
                true
        );

        assertThat(message).isNotNull();
        assertThat(message.getContent())
                .isEqualTo("即将生成报表。")
                .doesNotContain("<!DOCTYPE html", "<html");
        assertThat(message.getParts()).anyMatch(part -> "notice".equals(part.getType())
                && "报表输出被截断".equals(part.getTitle()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void eventStreamEmitsOneRagSearchAndPersistsItBeforeAnswer() {
        DihChatApplicationService service = streamingService();
        RagSearchEventData event = RagSearchEventData.builder()
                .searchId("rag-1")
                .query("接入链路")
                .mode("ask")
                .scopeType("all")
                .knowledgeBaseIds(List.of(1, 2))
                .topK(6)
                .status("succeeded")
                .reason("ok")
                .hitCount(1)
                .durationMillis(43L)
                .startedAt("2026-10-22T10:00:00Z")
                .completedAt("2026-10-22T10:00:00.043Z")
                .results(List.of(RagSearchEventData.RagSearchResult.builder()
                        .documentId("doc-1")
                        .knowledgeBaseId(1)
                        .knowledgeBaseName("产品文档")
                        .fileName("quick-start.md")
                        .source("kb_1")
                        .text("接入链路说明")
                        .build()))
                .build();
        String marker = RagSearchEventData.STREAM_MARKER_PREFIX + JacksonUtil.toJson(event);

        Flux<String> response = ReflectionTestUtils.invokeMethod(
                service,
                "emitAndSaveTextResponse",
                Flux.just(marker, "最终回答"),
                null,
                null,
                true,
                new AtomicReference<>(MessageType.TEXT),
                false
        );

        assertThat(response).isNotNull();
        List<String> events = response.collectList().block();
        assertThat(events).hasSize(3);
        assertThat(events.get(0)).contains("\"event\":\"rag_search\"");
        assertThat(events.get(0)).doesNotContain(RagSearchEventData.STREAM_MARKER_PREFIX);
        Map<String, Object> ragEvent = JacksonUtil.toMap(
                events.get(0), new TypeReference<Map<String, Object>>() {
                });
        Map<String, Object> ragData = (Map<String, Object>) ragEvent.get("data");
        assertThat(ragData)
                .containsEntry("searchId", "rag-1")
                .containsEntry("query", "接入链路")
                .containsEntry("scopeType", "all")
                .containsEntry("status", "succeeded")
                .containsEntry("hitCount", 1);
        assertThat(events.get(1)).contains("\"delta\"").contains("最终回答");

        Map<String, Object> doneEvent = JacksonUtil.toMap(
                events.get(2), new TypeReference<Map<String, Object>>() {
                });
        Map<String, Object> message = (Map<String, Object>) doneEvent.get("message");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) message.get("parts");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0))
                .containsEntry("id", "rag-1")
                .containsEntry("type", "rag-search")
                .containsEntry("status", "succeeded")
                .containsEntry("content", "知识库检索 · 1 个来源");
        assertThat((Map<String, Object>) parts.get(0).get("metadata"))
                .containsEntry("hitCount", 1)
                .containsEntry("reason", "ok");
        assertThat(parts.get(1)).containsEntry("type", "markdown");
        assertThat(String.valueOf(parts.get(1).get("content"))).contains("最终回答");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonEventStreamDropsInternalRagMarker() {
        DihChatApplicationService service = streamingService();
        RagSearchEventData event = RagSearchEventData.builder()
                .searchId("rag-hidden")
                .query("问题")
                .mode("ask")
                .scopeType("all")
                .status("empty")
                .reason("no_match")
                .hitCount(0)
                .results(List.of())
                .build();
        String marker = RagSearchEventData.STREAM_MARKER_PREFIX + JacksonUtil.toJson(event);

        Flux<String> response = ReflectionTestUtils.invokeMethod(
                service,
                "emitAndSaveTextResponse",
                Flux.just(marker, "最终回答"),
                null,
                null,
                false,
                new AtomicReference<>(MessageType.TEXT),
                false
        );

        assertThat(response).isNotNull();
        List<String> chunks = response.collectList().block();
        assertThat(chunks).containsExactly("最终回答");
    }

    @Test
    @SuppressWarnings("unchecked")
    void eventStreamConsumesTruncationMarkerWithoutExposingOrPersistingIt() {
        DihChatApplicationService service = streamingService();

        Flux<String> response = ReflectionTestUtils.invokeMethod(
                service,
                "emitAndSaveTextResponse",
                Flux.just("半截回答", AIChatService.OUTPUT_TRUNCATED_MARKER),
                null,
                null,
                true,
                new AtomicReference<>(MessageType.TEXT),
                false
        );

        assertThat(response).isNotNull();
        List<String> events = response.collectList().block();
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).contains("\"delta\"").contains("半截回答");
        assertThat(events.get(1))
                .contains("\"event\":\"done\"")
                .doesNotContain(AIChatService.OUTPUT_TRUNCATED_MARKER_PREFIX);
        assertThat(events).noneMatch(event -> event.contains(AIChatService.OUTPUT_TRUNCATED_MARKER_PREFIX));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonEventStreamDropsTruncationMarker() {
        DihChatApplicationService service = streamingService();

        Flux<String> response = ReflectionTestUtils.invokeMethod(
                service,
                "emitAndSaveTextResponse",
                Flux.just("半截回答", AIChatService.OUTPUT_TRUNCATED_MARKER),
                null,
                null,
                false,
                new AtomicReference<>(MessageType.TEXT),
                false
        );

        assertThat(response).isNotNull();
        assertThat(response.collectList().block()).containsExactly("半截回答");
    }

    @Test
    @SuppressWarnings("unchecked")
    void eventStreamFailureEmitsExactlyOneTerminalErrorEvent() {
        DihChatApplicationService service = emptyService();

        Flux<String> response = ReflectionTestUtils.invokeMethod(
                service,
                "emitAndSaveTextResponse",
                Flux.error(new IllegalStateException("upstream failed")),
                null,
                null,
                true,
                new AtomicReference<>(MessageType.TEXT),
                false
        );

        assertThat(response).isNotNull();
        List<String> events = response.collectList().block();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).contains("\"event\":\"error\"");
        assertThat(events.get(0)).doesNotContain("\"event\":\"done\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void contextLengthExceededFailureEmitsActionableEventErrorWithoutProviderDetails() {
        DihChatApplicationService service = emptyService();
        WebClientResponseException providerError = providerBadRequest("""
                {
                  "error": {
                    "message": "This model's maximum context length is 102400 tokens. However, you requested 4096 output tokens and your prompt contains at least 98305 input tokens.",
                    "type": "BadRequestError",
                    "param": "input_tokens",
                    "code": 400
                  }
                }
                """);

        Flux<String> response = ReflectionTestUtils.invokeMethod(
                service,
                "emitAndSaveTextResponse",
                Flux.error(new IllegalStateException(
                        "400 Bad Request from POST http://model-service/v1/chat/completions",
                        providerError)),
                null,
                null,
                true,
                new AtomicReference<>(MessageType.TEXT),
                false
        );

        assertThat(response).isNotNull();
        List<String> events = response.collectList().block();
        assertThat(events).hasSize(1);
        assertThat(events.get(0))
                .contains("\"event\":\"error\"")
                .contains(CONTEXT_LENGTH_EXCEEDED_MESSAGE)
                .doesNotContain("\"event\":\"done\"")
                .doesNotContain("maximum context length")
                .doesNotContain("102400")
                .doesNotContain("model-service");
    }

    @Test
    void contextLengthExceededErrorCodeIsRecognizedThroughNestedCause() {
        DihChatApplicationService service = emptyService();
        WebClientResponseException providerError = providerBadRequest("""
                {"error":{"message":"request rejected","code":"context_length_exceeded"}}
                """);

        String message = ReflectionTestUtils.invokeMethod(
                service,
                "resolveChatErrorMessage",
                new IllegalStateException("wrapped provider failure", providerError)
        );

        assertThat(message).isEqualTo(CONTEXT_LENGTH_EXCEEDED_MESSAGE);
    }

    @Test
    void unrelatedBadRequestKeepsGenericChatErrorMessage() {
        DihChatApplicationService service = emptyService();
        WebClientResponseException providerError = providerBadRequest("""
                {"error":{"message":"unsupported parameter: stream_options","code":400}}
                """);

        String message = ReflectionTestUtils.invokeMethod(
                service,
                "resolveChatErrorMessage",
                providerError
        );

        assertThat(message).isEqualTo("抱歉，回复失败，请稍后重试~");
    }

    @Test
    void invalidToolSchemaUsesActionableErrorMessage() {
        DihChatApplicationService service = emptyService();
        WebClientResponseException providerError = providerBadRequest("""
                {"error":{"message":"Invalid schema for function 'entity_trend': Pointer '/$defs/RequestCriteriaDto' does not exist"}}
                """);

        String message = ReflectionTestUtils.invokeMethod(
                service,
                "resolveChatErrorMessage",
                new IllegalStateException("wrapped provider failure", providerError)
        );

        assertThat(message)
                .isEqualTo("模型服务无法加载当前智能体的工具定义，请联系管理员检查工具参数后重试。");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonEventStreamReturnsSameContextLengthExceededMessage() {
        DihChatApplicationService service = emptyService();
        WebClientResponseException providerError = providerBadRequest("""
                {"error":{"message":"input_tokens exceed the context window limit"}}
                """);

        Flux<String> response = ReflectionTestUtils.invokeMethod(
                service,
                "emitAndSaveTextResponse",
                Flux.error(providerError),
                null,
                null,
                false,
                new AtomicReference<>(MessageType.TEXT),
                false
        );

        assertThat(response).isNotNull();
        assertThat(response.collectList().block())
                .containsExactly(CONTEXT_LENGTH_EXCEEDED_MESSAGE);
    }

    @Test
    void agentCapabilityErrorTakesPriorityOverNestedContextLengthError() {
        DihChatApplicationService service = emptyService();
        WebClientResponseException providerError = providerBadRequest("""
                {"error":{"code":"context_length_exceeded"}}
                """);

        String message = ReflectionTestUtils.invokeMethod(
                service,
                "resolveChatErrorMessage",
                new AgentCapabilityUnavailableException("智能体能力不可用。", providerError)
        );

        assertThat(message).isEqualTo("智能体能力不可用。");
    }

    private WebClientResponseException providerBadRequest(String responseBody) {
        return WebClientResponseException.create(
                400,
                "Bad Request",
                HttpHeaders.EMPTY,
                responseBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
    }

    @Test
    void mcpToolLogPayloadsAreSavedAsPrettyCodeParts() throws Exception {
        Class<?> streamType = java.util.Arrays.stream(DihChatApplicationService.class.getDeclaredClasses())
                .filter(type -> "McpToolLogStream".equals(type.getSimpleName()))
                .findFirst()
                .orElseThrow();
        var formatLog = streamType.getDeclaredMethod(
                "formatLog",
                McpToolCallLoggingProvider.McpToolCallLog.class
        );
        formatLog.setAccessible(true);

        String started = (String) formatLog.invoke(null,
                McpToolCallLoggingProvider.McpToolCallLog.started(
                        "dashboard_create",
                        "{\"request\":{\"name\":\"审批验证\",\"type\":\"LINK\"}}"
                ));
        String succeeded = (String) formatLog.invoke(null,
                McpToolCallLoggingProvider.McpToolCallLog.succeeded(
                        "dashboard_create",
                        120L,
                        "{\"id\":502,\"name\":\"审批验证\"}"
                ));

        List<ChatMessagePart> parts = new ChatMessagePartParser().parse(started + succeeded, MessageType.TEXT);

        assertThat(parts).extracting(ChatMessagePart::getType)
                .containsExactly("markdown", "code", "markdown", "code");
        assertThat(parts.get(1).getLanguage()).isEqualTo("json");
        assertThat(parts.get(1).getContent()).contains("\n  \"request\"");
        assertThat(parts.get(3).getLanguage()).isEqualTo("json");
        assertThat(parts.get(3).getContent()).contains("\n  \"id\" : 502");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mcpToolLogStreamKeepsConcurrentCallsSeparateAndMergesByCallId() throws Exception {
        Class<?> streamType = java.util.Arrays.stream(DihChatApplicationService.class.getDeclaredClasses())
                .filter(type -> "McpToolLogStream".equals(type.getSimpleName()))
                .findFirst()
                .orElseThrow();
        var create = streamType.getDeclaredMethod("create");
        var emit = streamType.getDeclaredMethod(
                "emit", McpToolCallLoggingProvider.McpToolCallLog.class);
        var toolParts = streamType.getDeclaredMethod("toolParts");
        var evidenceRefs = streamType.getDeclaredMethod("evidenceRefs");
        create.setAccessible(true);
        emit.setAccessible(true);
        toolParts.setAccessible(true);
        evidenceRefs.setAccessible(true);

        Object stream = create.invoke(null);
        emit.invoke(stream, McpToolCallLoggingProvider.McpToolCallLog.started(
                "call-a", "search", "{\"q\":\"a\",\"limit\":20}", "{\"q\":\"a\",\"limit\":20}"));
        emit.invoke(stream, McpToolCallLoggingProvider.McpToolCallLog.started(
                "call-b", "search", "{\"q\":\"b\",\"limit\":20}", "{\"q\":\"b\",\"limit\":20}"));
        emit.invoke(stream, McpToolCallLoggingProvider.McpToolCallLog.succeeded(
                "call-a", "search", 41L, "{\"total\":1}", "{\"total\":1}"));
        emit.invoke(stream, McpToolCallLoggingProvider.McpToolCallLog.failed(
                "call-b", "search", 17L, "timeout"));

        List<ChatMessagePart> parts = (List<ChatMessagePart>) toolParts.invoke(stream);

        assertThat(parts).extracting(ChatMessagePart::getId)
                .containsExactly("call-a", "call-b");
        assertThat(parts).extracting(ChatMessagePart::getStatus)
                .containsExactly("succeeded", "failed");
        assertThat(parts.get(0).getMetadata())
                .containsEntry("arguments", "{\"q\":\"a\",\"limit\":20}")
                .containsEntry("durationMillis", 41L)
                .containsEntry("error", null);
        assertThat(JacksonUtil.toMap(
                String.valueOf(parts.get(0).getMetadata().get("result")),
                new TypeReference<Map<String, Object>>() {
                })).containsEntry("total", 1);
        assertThat(parts.get(1).getMetadata())
                .containsEntry("arguments", "{\"q\":\"b\",\"limit\":20}")
                .containsEntry("error", "timeout")
                .containsEntry("durationMillis", 17L);

        List<Map<String, Object>> evidence =
                (List<Map<String, Object>>) evidenceRefs.invoke(stream);
        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0))
                .containsEntry("tool", "search")
                .containsEntry("argumentsSummary", "{\"q\":\"a\",\"limit\":20}");
    }

    @Test
    void mcpToolLogStreamSelectsLatestStartedSuccessfulCallBySequence() throws Exception {
        Class<?> streamType = java.util.Arrays.stream(DihChatApplicationService.class.getDeclaredClasses())
                .filter(type -> "McpToolLogStream".equals(type.getSimpleName()))
                .findFirst()
                .orElseThrow();
        var create = streamType.getDeclaredMethod("create");
        var emit = streamType.getDeclaredMethod(
                "emit", McpToolCallLoggingProvider.McpToolCallLog.class);
        var successfulCall = streamType.getDeclaredMethod("successfulCall", String.class);
        create.setAccessible(true);
        emit.setAccessible(true);
        successfulCall.setAccessible(true);

        Object stream = create.invoke(null);
        emit.invoke(stream, McpToolCallLoggingProvider.McpToolCallLog.started(
                "call-b", "search", "{\"q\":\"b\"}", "{\"q\":\"b\"}"));
        emit.invoke(stream, McpToolCallLoggingProvider.McpToolCallLog.started(
                "call-a", "search", "{\"q\":\"a\"}", "{\"q\":\"a\"}"));
        emit.invoke(stream, McpToolCallLoggingProvider.McpToolCallLog.succeeded(
                "call-a", "search", 11L, "{\"total\":1}", "{\"total\":1}"));
        emit.invoke(stream, McpToolCallLoggingProvider.McpToolCallLog.succeeded(
                "call-b", "search", 22L, "{\"total\":2}", "{\"total\":2}"));

        Object latest = successfulCall.invoke(stream, "search");

        assertThat((String) ReflectionTestUtils.invokeMethod(latest, "callId")).isEqualTo("call-a");
        assertThat((String) ReflectionTestUtils.invokeMethod(latest, "arguments"))
                .isEqualTo("{\"q\":\"a\"}");
        assertThat((String) ReflectionTestUtils.invokeMethod(latest, "result"))
                .isEqualTo("{\"total\":1}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mcpToolLogStreamUnwrapsNestedTextResultForDisplay() throws Exception {
        Class<?> streamType = java.util.Arrays.stream(DihChatApplicationService.class.getDeclaredClasses())
                .filter(type -> "McpToolLogStream".equals(type.getSimpleName()))
                .findFirst()
                .orElseThrow();
        var create = streamType.getDeclaredMethod("create");
        var emit = streamType.getDeclaredMethod(
                "emit", McpToolCallLoggingProvider.McpToolCallLog.class);
        var toolParts = streamType.getDeclaredMethod("toolParts");
        create.setAccessible(true);
        emit.setAccessible(true);
        toolParts.setAccessible(true);

        Object stream = create.invoke(null);
        emit.invoke(stream, McpToolCallLoggingProvider.McpToolCallLog.started(
                "call-wrapped", "lab_note_search", "{\"keyword\":\"接入\"}", "{\"keyword\":\"接入\"}"));
        String nested = JacksonUtil.toJson(Map.of(
                "rows", List.of(Map.of("id", 3, "title", "最后验证 Agent 能力")),
                "total", 1));
        String wrapped = JacksonUtil.toJson(List.of(Map.of("text", nested)));
        emit.invoke(stream, McpToolCallLoggingProvider.McpToolCallLog.succeeded(
                "call-wrapped", "lab_note_search", 41L, wrapped, wrapped));

        List<ChatMessagePart> parts = (List<ChatMessagePart>) toolParts.invoke(stream);
        String result = String.valueOf(parts.get(0).getMetadata().get("result"));
        Map<String, Object> parsed = JacksonUtil.toMap(
                result,
                new TypeReference<Map<String, Object>>() {
                });

        assertThat(result).doesNotContain("\\\"rows\\\"", "\"text\"");
        assertThat(parsed).containsEntry("total", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) parsed.get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("id", 3)
                .containsEntry("title", "最后验证 Agent 能力");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergeSupplementalPartsKeepsApprovalAtItsStreamPosition() {
        DihChatApplicationService service = emptyService();
        ChatMessagePart approval = ChatMessagePart.builder()
                .id("approval-1")
                .type("mcp-approval")
                .status("succeeded")
                .metadata(Map.of("contentOffset", 6))
                .build();
        ChatMessagePart markdown = ChatMessagePart.builder()
                .type("markdown")
                .content("beforeafter")
                .build();

        List<ChatMessagePart> parts = ReflectionTestUtils.invokeMethod(
                service, "mergeSupplementalParts", "beforeafter", List.of(markdown), List.of(approval));

        assertThat(parts).extracting(ChatMessagePart::getType)
                .containsExactly("markdown", "mcp-approval", "markdown");
        assertThat(parts.get(0).getContent()).isEqualTo("before");
        assertThat(parts.get(2).getContent()).isEqualTo("after");
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistedBuiltinDemoApprovalDisablesSessionApproval() throws Exception {
        Class<?> streamType = java.util.Arrays.stream(
                        DihChatApplicationService.class.getDeclaredClasses())
                .filter(type -> "McpToolLogStream".equals(type.getSimpleName()))
                .findFirst()
                .orElseThrow();
        var toApprovalPart = streamType.getDeclaredMethod(
                "toApprovalPart", McpApprovalVo.class, Integer.class);
        toApprovalPart.setAccessible(true);
        McpToolInvocation invocation = new McpToolInvocation()
                .setRequestId("approval-demo-1")
                .setToolKey("local::menu_create")
                .setToolName("menu_create")
                .setDescription("创建演示菜单")
                .setRiskLevel(McpToolRiskLevel.HIGH)
                .setChannel(McpInvocationChannel.CHAT_AGENT)
                .setPolicySnapshot(McpApprovalPolicy.ASK)
                .setStatus(McpInvocationStatus.PENDING)
                .setRequesterUserId(42)
                .setChatId("chat-demo")
                .setTurnId("turn-demo")
                .setMcpClientInfo(
                        McpInvocationContext
                                .BUILTIN_VISUALIZATION_CHART_DEMO)
                .setArguments("{}");

        ChatMessagePart part = (ChatMessagePart) toApprovalPart.invoke(
                null, new McpApprovalVo(invocation), 12);

        assertThat(part.getType()).isEqualTo("mcp-approval");
        assertThat(part.getStatus()).isEqualTo("pending");
        assertThat(part.getMetadata())
                .containsEntry("sessionApprovalAllowed", false)
                .containsEntry("contentOffset", 12);
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildStructuredExtraDataPatchIncludesDataVisualizationChartLibrary() {
        DihChatApplicationService service = emptyService();

        ChatMessagePart part = ChatMessagePart.builder()
                .type("visualization-chart-record")
                .content("登录趋势图")
                .metadata(Map.ofEntries(
                        Map.entry("id", "traffic-trend"),
                        Map.entry("name", "流量趋势图"),
                        Map.entry("planId", "plan-1"),
                        Map.entry("entities", List.of("traffic")),
                        Map.entry("fields", List.of(Map.of(
                                "field", "zenvis_insert_time", "role", "time"))),
                        Map.entry("query", Map.of(
                                "tool", "entity_trend",
                                "request", Map.of(
                                        "entities", List.of("traffic"),
                                        "granularity", "DAY"))),
                        Map.entry("queryMeta", Map.of(
                                "query_type", "trend", "result_count", 2)),
                        Map.entry("echartsOption", Map.of(
                                "xAxis", Map.of("type", "category"),
                                "yAxis", Map.of("type", "value"),
                                "series", List.of())),
                        Map.entry("amisConfig", Map.of("type", "chart")),
                        Map.entry("queriedAt", "2026-07-30T10:00:00+08:00"),
                        Map.entry("validationStatus", "success"),
                        Map.entry("chartType", "line")
                ))
                .build();

        Map<String, Object> patch = ReflectionTestUtils.invokeMethod(
                service,
                "buildStructuredExtraDataPatch",
                List.of(part)
        );

        assertThat(patch).isNotNull();
        Map<String, Object> visualizationChart = (Map<String, Object>) patch.get("visualizationChart");
        assertThat(visualizationChart).isNotNull();
        List<Map<String, Object>> chartLibrary = (List<Map<String, Object>>) visualizationChart.get("chartLibrary");
        assertThat(chartLibrary).hasSize(1);
        assertThat(chartLibrary.get(0))
                .containsEntry("id", "traffic-trend")
                .containsEntry("name", "流量趋势图")
                .containsEntry("chartType", "line")
                .containsEntry("entity", "traffic")
                .containsEntry("planId", "plan-1")
                .containsEntry("validationStatus", "success");
    }

    @Test
    void unverifiedVisualizationChartRecordIsNotPersisted() {
        DihChatApplicationService service = emptyService();
        ChatMessagePart part = ChatMessagePart.builder()
                .type("visualization-chart-record")
                .metadata(Map.of(
                        "id", "demo",
                        "name", "演示图",
                        "config", Map.of("series", List.of())))
                .build();

        Map<String, Object> patch = ReflectionTestUtils.invokeMethod(
                service, "buildStructuredExtraDataPatch", List.of(part));

        assertThat(patch).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deterministicDemoChartRecordIsPersistedWithoutWorkflowQueryEvidence() {
        DihChatApplicationService service = emptyService();
        ChatMessagePart part = ChatMessagePart.builder()
                .type("visualization-chart-record")
                .metadata(Map.of(
                        "id", "demo-user-event-report-trend",
                        "name", "用户事件上报趋势图",
                        "chartType", "line",
                        "source", "demo",
                        "demoId", "visualization-chart:chart",
                        "config", Map.of(
                                "type", "chart",
                                "config", Map.of("series", List.of()))))
                .build();

        Map<String, Object> patch = ReflectionTestUtils.invokeMethod(
                service, "buildStructuredExtraDataPatch", List.of(part));

        assertThat(patch).isNotNull();
        Map<String, Object> visualizationChart =
                (Map<String, Object>) patch.get("visualizationChart");
        List<Map<String, Object>> chartLibrary =
                (List<Map<String, Object>>) visualizationChart.get("chartLibrary");
        assertThat(chartLibrary).hasSize(1);
        assertThat(chartLibrary.get(0))
                .containsEntry("id", "demo-user-event-report-trend")
                .containsEntry("source", "demo")
                .containsEntry("validationStatus", "unverified");
    }

    @Test
    @SuppressWarnings("unchecked")
    void visualizationWorkflowRequiresMetaApprovalAndSuccessfulDataTool() throws Exception {
        DihChatApplicationService service = emptyService();
        Class<?> streamType = java.util.Arrays.stream(
                        DihChatApplicationService.class.getDeclaredClasses())
                .filter(type -> "McpToolLogStream".equals(type.getSimpleName()))
                .findFirst()
                .orElseThrow();
        var create = streamType.getDeclaredMethod("create");
        var emit = streamType.getDeclaredMethod(
                "emit", McpToolCallLoggingProvider.McpToolCallLog.class);
        create.setAccessible(true);
        emit.setAccessible(true);

        Object emptyEvidence = create.invoke(null);
        ChatMessagePart unqueriedEntitySelection = ChatMessagePart.builder()
                .type("info-steps")
                .title("实体选择")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "visualization_chart.select_entity_from_meta",
                        "steps", List.of(Map.of(
                                "id", "analysis_entity",
                                "title", "选择实体",
                                "required", true,
                                "suggestions", List.of(
                                        "probe_message",
                                        "network_traffic",
                                        "security_event"))))))
                .build();
        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                null, List.of(unqueriedEntitySelection), emptyEvidence);
        assertThat(unqueriedEntitySelection.getStatus()).isEqualTo("blocked");
        assertThat(unqueriedEntitySelection.getMetadata())
                .containsEntry("validationStatus", "blocked")
                .containsEntry("steps", List.of())
                .doesNotContainKey("action");

        ChatMessagePart prematurePlan = ChatMessagePart.builder()
                .type("confirm")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "visualization_chart.confirm_query_plan",
                        "planId", "plan-1",
                        "entity", "traffic",
                        "fields", List.of(),
                        "query", Map.of(
                                "tool", "entity_trend",
                                "request", Map.of(
                                        "entities", List.of("traffic"),
                                        "granularity", "DAY")))))
                .build();
        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                null, List.of(prematurePlan), emptyEvidence);
        assertThat(prematurePlan.getStatus()).isEqualTo("blocked");
        assertThat(prematurePlan.getMetadata())
                .containsEntry("validationStatus", "blocked")
                .containsEntry("blockedAction", "visualization_chart.confirm_query_plan")
                .doesNotContainKey("action");

        Object evidence = create.invoke(null);
        emitSuccessfulCall(emit, evidence,
                "retrieval_list_display_entity", "{}",
                "{\"entityList\":["
                        + "{\"name\":\"traffic\",\"label\":\"网络流量\"},"
                        + "{\"name\":\"probe_agent_message\",\"label\":\"探针消息\"}],"
                        + "\"selectedEntity\":[\"traffic\",\"probe_agent_message\"]}");
        emitSuccessfulCall(emit, evidence,
                "retrieval_list_display_attribute",
                "{\"entity\":\"traffic\"}",
                "{\"entity\":\"traffic\",\"attributeList\":["
                        + "{\"name\":\"zenvis_insert_time\",\"label\":\"创建时间\"},"
                        + "{\"name\":\"fact_type\",\"label\":\"事实类型\"}]}");
        ChatMessagePart verifiedEntitySelection = ChatMessagePart.builder()
                .type("info-steps")
                .title("实体选择")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "steps", List.of(Map.of(
                                "id", "analysis_entity",
                                "title", "选择实体",
                                "required", true,
                                "suggestions", List.of(
                                        "probe_message",
                                        "network_traffic",
                                        "security_event"))))))
                .build();
        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                null, List.of(verifiedEntitySelection), evidence);
        assertThat(verifiedEntitySelection.getStatus()).isEqualTo("pending");
        assertThat(verifiedEntitySelection.getMetadata())
                .containsEntry("action", "visualization_chart.select_entity_from_meta")
                .containsEntry("validationStatus", "success")
                .containsEntry("metaVerified", true);
        List<Map<String, Object>> entitySteps =
                (List<Map<String, Object>>) verifiedEntitySelection.getMetadata().get("steps");
        assertThat(entitySteps).hasSize(1);
        assertThat(entitySteps.get(0)).containsEntry("strictOptions", true);
        List<Map<String, Object>> entitySuggestions =
                (List<Map<String, Object>>) entitySteps.get(0).get("suggestions");
        assertThat(entitySuggestions)
                .extracting(option -> option.get("value"))
                .containsExactly("traffic", "probe_agent_message");
        assertThat(entitySuggestions)
                .extracting(option -> option.get("label"))
                .containsExactly("网络流量（traffic）", "探针消息（probe_agent_message）");
        assertThat(entitySuggestions)
                .allSatisfy(option -> assertThat(option.get("value"))
                        .isNotIn("probe_message", "network_traffic", "security_event"));

        ChatMessagePart inferredPlan = ChatMessagePart.builder()
                .type("confirm")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "visualization_chart.confirm_query_plan",
                        "planId", "plan-inferred",
                        "query", Map.of(
                                "tool", "entity_distribution",
                                "request", Map.of(
                                        "entity", "traffic",
                                        "time_field", "zenvis_insert_time",
                                        "dimension", "fact_type",
                                        "limit", 20)))))
                .build();
        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                null, List.of(inferredPlan), evidence);
        assertThat(inferredPlan.getStatus()).isEqualTo("pending");
        assertThat(inferredPlan.getMetadata())
                .containsEntry("validationStatus", "success")
                .containsEntry("entity", "traffic");
        assertThat(inferredPlan.getMetadata().get("fields")).isEqualTo(List.of(
                Map.of("field", "zenvis_insert_time", "label", "创建时间", "role", "time"),
                Map.of("field", "fact_type", "label", "事实类型", "role", "dimension")));
        assertThat(inferredPlan.getContent())
                .contains("网络流量（traffic）", "创建时间（zenvis_insert_time，time）",
                        "事实类型（fact_type，dimension）");

        ChatMessagePart allTimeDistributionPlan = ChatMessagePart.builder()
                .type("confirm")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "visualization_chart.confirm_query_plan",
                        "planId", "plan-all-time-distribution",
                        "query", Map.of(
                                "tool", "entity_distribution",
                                "request", Map.of(
                                        "entity", "traffic",
                                        "time_field", "zenvis_insert_time",
                                        "dimension", "fact_type",
                                        "time_range", Map.of("preset", "ALL"),
                                        "limit", 20)))))
                .build();
        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                null, List.of(allTimeDistributionPlan), evidence);
        assertThat(allTimeDistributionPlan.getStatus()).isEqualTo("pending");
        assertThat(allTimeDistributionPlan.getMetadata())
                .containsEntry("validationStatus", "success")
                .containsEntry("metaVerified", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> allTimeQuery =
                (Map<String, Object>) allTimeDistributionPlan.getMetadata().get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> allTimeRequest =
                (Map<String, Object>) allTimeQuery.get("request");
        assertThat(allTimeRequest.get("time_range"))
                .isEqualTo(Map.of("preset", "ALL_TIME"));

        ChatMessagePart unsupportedAllTimeTrendPlan = ChatMessagePart.builder()
                .type("confirm")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "visualization_chart.confirm_query_plan",
                        "planId", "plan-unsupported-all-time-trend",
                        "query", Map.of(
                                "tool", "entity_trend",
                                "request", Map.of(
                                        "entities", List.of("traffic"),
                                        "time_field", "zenvis_insert_time",
                                        "granularity", "DAY",
                                        "time_range", Map.of("preset", "ALL"))))))
                .build();
        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                null, List.of(unsupportedAllTimeTrendPlan), evidence);
        assertThat(unsupportedAllTimeTrendPlan.getStatus()).isEqualTo("blocked");
        assertThat(unsupportedAllTimeTrendPlan.getMetadata())
                .containsEntry("validationStatus", "blocked")
                .containsEntry("validationCode", "validation_failed")
                .containsEntry("validationMessage",
                        "时间范围无效：当前接口不支持ALL_TIME")
                .doesNotContainKey("action");

        ChatMessagePart categorizedPlan = ChatMessagePart.builder()
                .type("confirm")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "visualization_chart.confirm_query_plan",
                        "planId", "plan-category-kind",
                        "query", Map.of(
                                "tool", "entity_aggregate",
                                "request", Map.of(
                                        "entity", "traffic",
                                        "dimensions", List.of(Map.of(
                                                "name", "fact_type",
                                                "field", "fact_type",
                                                "kind", "CATEGORY")))))))
                .build();
        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                null, List.of(categorizedPlan), evidence);
        assertThat(categorizedPlan.getStatus()).isEqualTo("pending");
        Map<String, Object> categorizedQuery =
                (Map<String, Object>) categorizedPlan.getMetadata().get("query");
        Map<String, Object> categorizedRequest =
                (Map<String, Object>) categorizedQuery.get("request");
        assertThat((List<Map<String, Object>>) categorizedRequest.get("dimensions"))
                .singleElement()
                .satisfies(dimension -> assertThat(dimension)
                        .containsEntry("kind", "FIELD"));

        ChatMessagePart hallucinatedFieldPlan = ChatMessagePart.builder()
                .type("confirm")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "visualization_chart.confirm_query_plan",
                        "planId", "plan-hallucinated-field",
                        "query", Map.of(
                                "tool", "entity_distribution",
                                "request", Map.of(
                                        "entity", "traffic",
                                        "dimension", "message_type",
                                        "limit", 20)))))
                .build();
        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                null, List.of(hallucinatedFieldPlan), evidence);
        assertThat(hallucinatedFieldPlan.getStatus()).isEqualTo("blocked");
        assertThat(hallucinatedFieldPlan.getMetadata())
                .containsEntry("validationMessage",
                        "字段 Meta MCP 返回结果中不存在实体traffic的逻辑字段：message_type");

        ChatMessagePart confirmedPlan = ChatMessagePart.builder()
                .type("confirm")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "visualization_chart.confirm_query_plan",
                        "planId", "plan-2",
                        "entity", "traffic",
                        "fields", List.of(Map.of(
                                "field", "zenvis_insert_time", "role", "time")),
                        "query", Map.of(
                                "tool", "entity_trend",
                                "request", Map.of(
                                        "entities", List.of("traffic"),
                                        "granularity", "DAY")))))
                .build();
        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                null, List.of(confirmedPlan), evidence);
        assertThat(confirmedPlan.getStatus()).isEqualTo("pending");
        assertThat(confirmedPlan.getMetadata())
                .containsEntry("validationStatus", "success")
                .containsEntry("metaVerified", true);

        confirmedPlan.setStatus("approved");
        Message approvedMessage = new Message("ai", "已确认", MessageType.TEXT);
        approvedMessage.setParts(List.of(confirmedPlan));
        ChatSession session = new ChatSession();
        session.setMessages(JacksonUtil.toJson(List.of(approvedMessage)));
        emitSuccessfulCall(emit, evidence,
                "entity_trend",
                "{\"request\":{\"entities\":[\"traffic\"],\"granularity\":\"DAY\"}}",
                "{\"meta\":{\"query_type\":\"trend\",\"result_count\":1},"
                        + "\"result\":{\"columns\":[],\"rows\":[]},"
                        + "\"echarts\":{\"chart_type\":\"line\",\"option\":"
                        + "{\"xAxis\":{\"type\":\"category\"},\"yAxis\":{\"type\":\"value\"},"
                        + "\"series\":[]}}}");
        ChatMessagePart preview = ChatMessagePart.builder()
                .type("visualization-chart-preview")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "visualization_chart.add_chart_library",
                        "planId", "plan-2",
                        "entities", List.of("traffic"),
                        "fields", List.of(),
                        "query", Map.of(
                                "tool", "entity_trend",
                                "request", Map.of("entities", List.of("wrong"))))))
                .build();

        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                session, List.of(preview), evidence);

        assertThat(preview.getMetadata())
                .containsEntry("validationStatus", "success")
                .containsKey("echartsOption")
                .doesNotContainKeys("api", "url", "option");
        @SuppressWarnings("unchecked")
        Map<String, Object> verifiedQuery =
                (Map<String, Object>) preview.getMetadata().get("query");
        assertThat(verifiedQuery.get("request"))
                .isEqualTo(Map.of("entities", List.of("traffic"), "granularity", "DAY"));

        Object mismatchedEvidence = create.invoke(null);
        emitSuccessfulCall(emit, mismatchedEvidence,
                "entity_trend",
                "{\"request\":{\"entities\":[\"traffic\"],\"granularity\":\"HOUR\"}}",
                "{\"meta\":{},\"result\":{\"columns\":[],\"rows\":[]},"
                        + "\"echarts\":{\"chart_type\":\"line\",\"option\":{\"series\":[]}}}");
        ChatMessagePart mismatchedPreview = ChatMessagePart.builder()
                .type("visualization-chart-preview")
                .metadata(new LinkedHashMap<>(Map.of(
                        "planId", "plan-2",
                        "query", Map.of(
                                "tool", "entity_trend",
                                "request", Map.of(
                                        "entities", List.of("traffic"),
                                        "granularity", "DAY")))))
                .build();
        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                session, List.of(mismatchedPreview), mismatchedEvidence);
        assertThat(mismatchedPreview.getStatus()).isEqualTo("blocked");
        assertThat(mismatchedPreview.getMetadata())
                .containsEntry("validationStatus", "blocked")
                .containsEntry("validationCode", "query_parameter_mismatch")
                .containsEntry("validationMessage",
                        "数据查询参数与已确认方案不一致，本次结果未验证；请重新确认查询方案后重试");
    }

    @Test
    @SuppressWarnings("unchecked")
    void skillChartPreviewUsesSuccessfulCurrentTurnMcpAsItsProvenance() throws Exception {
        DihChatApplicationService service = emptyService();
        Class<?> streamType = java.util.Arrays.stream(
                        DihChatApplicationService.class.getDeclaredClasses())
                .filter(type -> "McpToolLogStream".equals(type.getSimpleName()))
                .findFirst()
                .orElseThrow();
        var create = streamType.getDeclaredMethod("create");
        var emit = streamType.getDeclaredMethod(
                "emit", McpToolCallLoggingProvider.McpToolCallLog.class);
        create.setAccessible(true);
        emit.setAccessible(true);

        Object evidence = create.invoke(null);
        emitSuccessfulCall(emit, evidence,
                "btw_security_snapshot",
                "{\"range_mode\":\"LATEST_DATA\",\"window_days\":14,\"top_n\":10}",
                "{\"text\":\"{\\\"status\\\":\\\"completed\\\"}\"}");
        Map<String, Object> option = Map.of(
                "xAxis", Map.of("type", "value"),
                "yAxis", Map.of("type", "value"),
                "series", List.of(Map.of("type", "scatter", "data", List.of())));
        ChatMessagePart preview = ChatMessagePart.builder()
                .type("visualization-chart-preview")
                .status("temporary")
                .metadata(new LinkedHashMap<>(Map.of(
                        "source", "btw_security_snapshot.device_analysis",
                        "chartType", "scatter",
                        "echartsOption", option)))
                .build();
        ChatSession session = new ChatSession();
        session.setType("skill:btw-security-posture-analysis");

        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                session, List.of(preview), evidence);

        assertThat(preview.getStatus()).isEqualTo("temporary");
        assertThat(preview.getMetadata())
                .containsEntry("validationStatus", "success")
                .containsEntry("validated", true)
                .containsEntry("echartsOption", option)
                .containsKey("queriedAt");
        Map<String, Object> amisConfig =
                (Map<String, Object>) preview.getMetadata().get("amisConfig");
        assertThat(amisConfig)
                .containsEntry("type", "chart")
                .containsEntry("config", option);

        Map<String, Object> legacyAmisConfig = Map.of(
                "type", "chart",
                "config", Map.of(
                        "animation", false,
                        "dataset", Map.of("source", List.of())),
                "xAxis", Map.of("type", "value"),
                "yAxis", Map.of("type", "value"),
                "series", List.of(Map.of("type", "scatter")));
        ChatMessagePart legacyPreview = ChatMessagePart.builder()
                .type("visualization-chart-preview")
                .status("temporary")
                .metadata(new LinkedHashMap<>(Map.of(
                        "source", "btw_security_snapshot.device_analysis",
                        "amisConfig", legacyAmisConfig)))
                .build();
        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                session, List.of(legacyPreview), evidence);
        assertThat(legacyPreview.getMetadata())
                .containsEntry("validationStatus", "success");
        Map<String, Object> normalizedLegacyOption =
                (Map<String, Object>) legacyPreview.getMetadata().get("echartsOption");
        assertThat(normalizedLegacyOption)
                .containsKeys("dataset", "xAxis", "yAxis", "series");

        ChatMessagePart unverified = ChatMessagePart.builder()
                .type("visualization-chart-preview")
                .metadata(new LinkedHashMap<>(Map.of(
                        "source", "missing_tool.device_analysis",
                        "echartsOption", option)))
                .build();
        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                session, List.of(unverified), evidence);
        assertThat(unverified.getStatus()).isEqualTo("blocked");
        assertThat(unverified.getMetadata())
                .containsEntry("validationStatus", "blocked")
                .containsEntry("validationMessage",
                        "图表 source 未指向本轮成功的 MCP 工具调用，图表不能验证");
    }

    @Test
    void unambiguousEntityPromptPrefetchesAttributeMetaForDirectPlanCard() {
        DihChatApplicationService service = emptyService();
        ToolCallback attributeTool = mock(ToolCallback.class);
        ToolDefinition attributeDefinition = mock(ToolDefinition.class);
        when(attributeDefinition.name()).thenReturn("retrieval_list_display_attribute");
        when(attributeTool.getToolDefinition()).thenReturn(attributeDefinition);
        when(attributeTool.call(any(String.class))).thenReturn(
                "{\"entity\":\"traffic\",\"attributeList\":["
                        + "{\"name\":\"fact_type\",\"label\":\"事实类型\",\"type\":\"string\"}]}");
        ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
        when(provider.getToolCallbacks()).thenReturn(new ToolCallback[]{attributeTool});
        McpToolContext context = new McpToolContext(provider, "system-prompt");
        List<Map<String, Object>> entityRows = List.of(
                Map.of("name", "traffic", "label", "网络流量"),
                Map.of("name", "probe_agent_message", "label", "探针消息"));

        String fastPath = ReflectionTestUtils.invokeMethod(
                service, "tryBootstrapUnambiguousAttributeMeta",
                context, "分析下网络流量最近的趋势", entityRows, null, null, true);

        assertThat(fastPath).contains("retrieval_list_display_attribute")
                .contains("visualization_chart.confirm_query_plan")
                .contains("fact_type");

        String ambiguous = ReflectionTestUtils.invokeMethod(
                service, "tryBootstrapUnambiguousAttributeMeta",
                context, "对比网络流量和探针消息的差异", entityRows, null, null, true);
        assertThat(ambiguous).isEmpty();

        String unrelated = ReflectionTestUtils.invokeMethod(
                service, "tryBootstrapUnambiguousAttributeMeta",
                context, "你好", entityRows, null, null, true);
        assertThat(unrelated).isEmpty();
    }

    @Test
    void attributeMetaPrefetchUsesSingleEntityAndWorkflowContextEntity() {
        DihChatApplicationService service = emptyService();
        ToolCallback attributeTool = mock(ToolCallback.class);
        ToolDefinition attributeDefinition = mock(ToolDefinition.class);
        when(attributeDefinition.name()).thenReturn("retrieval_list_display_attribute");
        when(attributeTool.getToolDefinition()).thenReturn(attributeDefinition);
        when(attributeTool.call(any(String.class))).thenReturn(
                "{\"entity\":\"user_event\",\"attributeList\":["
                        + "{\"name\":\"event_type\",\"label\":\"事件类型\",\"type\":\"string\"}]}");
        ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
        when(provider.getToolCallbacks()).thenReturn(new ToolCallback[]{attributeTool});
        McpToolContext context = new McpToolContext(provider, "system-prompt");
        List<Map<String, Object>> entityRows = List.of(
                Map.of("name", "user_event", "label", "用户事件数据"));

        String single = ReflectionTestUtils.invokeMethod(
                service, "tryBootstrapUnambiguousAttributeMeta",
                context, "风险事件都有哪些类型，按照饼图展示", entityRows, null, null, true);
        assertThat(single).contains("user_event").contains("event_type");

        AgentWorkflowState workflow = new AgentWorkflowState()
                .setWorkflowId("wf-1")
                .setAgentType(VisualizationChartAgent.AGENT_TYPE)
                .setStep(AgentWorkflowStep.ENTITY_META)
                .setContext(new LinkedHashMap<>(Map.of(
                        "query", Map.of("tool", "entity_distribution",
                                "request", Map.of("entity", "user_event")))));
        List<Map<String, Object>> manyEntities = List.of(
                Map.of("name", "user_event", "label", "用户事件数据"),
                Map.of("name", "probe_agent_message", "label", "探针消息"));
        String fromContext = ReflectionTestUtils.invokeMethod(
                service, "tryBootstrapUnambiguousAttributeMeta",
                context, "继续", manyEntities, null, workflow, true);
        assertThat(fromContext).contains("user_event");

        String disabled = ReflectionTestUtils.invokeMethod(
                service, "tryBootstrapUnambiguousAttributeMeta",
                context, "继续", entityRows, null, workflow, false);
        assertThat(disabled).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void steerConfirmationAfterVerifiedPlanCardCountsAsApproval() {
        DihChatApplicationService service = emptyService();
        Map<String, Object> planMetadata = new LinkedHashMap<>();
        planMetadata.put("action", "visualization_chart.confirm_query_plan");
        planMetadata.put("planId", "plan-1");
        planMetadata.put("validationStatus", "success");
        planMetadata.put("query", Map.of(
                "tool", "entity_distribution",
                "request", Map.of("entity", "user_event", "dimension", "event_type")));

        ChatSession session = new ChatSession();
        session.setMessages(com.coolxer.utils.JacksonUtil.toJson(List.of(
                Map.of("sender", "user", "content", "风险事件都有哪些类型"),
                Map.of("sender", "ai", "content", "请确认方案",
                        "parts", List.of(Map.of(
                                "type", "confirm",
                                "status", "pending",
                                "metadata", planMetadata))),
                Map.of("sender", "user", "content", "已确认"))));

        Map<String, Object> approved = ReflectionTestUtils.invokeMethod(
                service, "approvedVisualizationPlan", session, "plan-1");
        assertThat(approved).containsEntry("planId", "plan-1");

        session.setMessages(com.coolxer.utils.JacksonUtil.toJson(List.of(
                Map.of("sender", "user", "content", "风险事件都有哪些类型"),
                Map.of("sender", "ai", "content", "请确认方案",
                        "parts", List.of(Map.of(
                                "type", "confirm",
                                "status", "pending",
                                "metadata", planMetadata))),
                Map.of("sender", "user", "content", "改成柱状图"))));
        Map<String, Object> revised = ReflectionTestUtils.invokeMethod(
                service, "approvedVisualizationPlan", session, "plan-1");
        assertThat(revised).isEmpty();

        ChatSession confirmed = new ChatSession();
        confirmed.setMessages(com.coolxer.utils.JacksonUtil.toJson(List.of(
                Map.of("sender", "user", "content", "风险事件都有哪些类型"),
                Map.of("sender", "ai", "content", "请确认方案",
                        "parts", List.of(Map.of(
                                "type", "confirm",
                                "status", "pending",
                                "metadata", planMetadata))),
                Map.of("sender", "user", "content", "就按这个来"))));
        Map<String, Object> exactConfirmed = ReflectionTestUtils.invokeMethod(
                service, "approvedVisualizationPlan", confirmed, "plan-1");
        assertThat(exactConfirmed).isEmpty();

        session.setMessages(com.coolxer.utils.JacksonUtil.toJson(List.of(
                Map.of("sender", "ai", "content", "请确认方案",
                        "parts", List.of(Map.of(
                                "type", "confirm",
                                "status", "pending",
                                "metadata", planMetadata))))));
        Map<String, Object> unanswered = ReflectionTestUtils.invokeMethod(
                service, "approvedVisualizationPlan", session, "plan-1");
        assertThat(unanswered).isEmpty();
    }

    @Test
    void platformChartPreviewReplacesModelPreviewThatCannotPassValidation() throws Exception {
        DihChatApplicationService service = emptyService();
        WorkflowStateStore store = mock(WorkflowStateStore.class);
        AgentWorkflowState workflow = new AgentWorkflowState()
                .setWorkflowId("wf-1")
                .setAgentType(VisualizationChartAgent.AGENT_TYPE)
                .setStep(AgentWorkflowStep.DATA_QUERY)
                .setContext(new LinkedHashMap<>(Map.of(
                        "planId", "plan-1",
                        "query", Map.of(
                                "tool", "entity_trend",
                                "request", Map.of(
                                        "entities", List.of("traffic"),
                                        "granularity", "DAY")))));
        when(store.loadActive(any(), any())).thenReturn(Optional.of(workflow));
        ReflectionTestUtils.setField(service, "workflowStateStore", store);
        ChatSession session = new ChatSession();
        session.setType(VisualizationChartAgent.AGENT_TYPE);

        Object evidence = newEvidenceStream();
        emitEvidenceSuccess(evidence, "entity_trend",
                "{\"request\":{\"entities\":[\"traffic\"],\"granularity\":\"DAY\"}}",
                "{\"data\":{\"meta\":{\"entity\":\"traffic\"},"
                        + "\"result\":{\"columns\":[],\"rows\":[]},"
                        + "\"echarts\":{\"chart_type\":\"line\","
                        + "\"option\":{\"xAxis\":{\"type\":\"time\"}}}}}");

        ChatMessagePart mismatched = ChatMessagePart.builder()
                .id("model-preview-bad")
                .type("visualization-chart-preview")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "planId", "plan-1",
                        "query", Map.of(
                                "tool", "entity_trend",
                                "request", Map.of(
                                        "entities", List.of("security_event"),
                                        "granularity", "DAY")))))
                .build();
        List<ChatMessagePart> parts = new ArrayList<>(List.of(mismatched));
        ReflectionTestUtils.invokeMethod(service, "ensurePlatformVisualizationChartPreview",
                session, parts, evidence);

        assertThat(parts).hasSize(1);
        ChatMessagePart platformPreview = parts.get(0);
        assertThat(platformPreview.getId()).isEqualTo("artifact:wf-1:plan-1");
        assertThat(platformPreview.getMetadata())
                .containsEntry("planId", "plan-1")
                .containsEntry("action", "visualization_chart.add_chart_library");

        ChatMessagePart matching = ChatMessagePart.builder()
                .id("model-preview-ok")
                .type("visualization-chart-preview")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "planId", "plan-1",
                        "query", Map.of(
                                "tool", "entity_trend",
                                "request", Map.of(
                                        "entities", List.of("traffic"),
                                        "granularity", "DAY")))))
                .build();
        List<ChatMessagePart> keptParts = new ArrayList<>(List.of(matching));
        ReflectionTestUtils.invokeMethod(service, "ensurePlatformVisualizationChartPreview",
                session, keptParts, evidence);

        assertThat(keptParts).hasSize(1);
        assertThat(keptParts.get(0).getId()).isEqualTo("model-preview-ok");
    }

    @Test
    @SuppressWarnings("unchecked")
    void platformEntitySelectionCardIsSynthesizedWhenModelOnlyOutputsText() throws Exception {
        DihChatApplicationService service = emptyService();
        Object evidence = newEvidenceStream();
        emitEvidenceSuccess(evidence, "retrieval_list_display_entity", "{}",
                "{\"entityList\":["
                        + "{\"name\":\"traffic\",\"label\":\"网络流量\"},"
                        + "{\"name\":\"probe_agent_message\",\"label\":\"探针消息\"}]}");
        ChatSession session = new ChatSession();
        session.setType(VisualizationChartAgent.AGENT_TYPE);

        ChatMessagePart markdown = ChatMessagePart.builder()
                .type("markdown")
                .content("我来帮你分析网络流量的趋势，请先确认要分析的实体。")
                .build();
        List<ChatMessagePart> parts = new ArrayList<>(List.of(markdown));
        ReflectionTestUtils.invokeMethod(service, "ensurePlatformVisualizationEntitySelection",
                session, parts, evidence);

        assertThat(parts).hasSize(2);
        ChatMessagePart selection = parts.get(1);
        assertThat(selection.getType()).isEqualTo("info-steps");
        assertThat(selection.getMetadata())
                .containsEntry("action", "visualization_chart.select_entity_from_meta");

        ReflectionTestUtils.invokeMethod(service, "validateDataVisualizationParts",
                session, parts, evidence);
        assertThat(selection.getMetadata())
                .containsEntry("validationStatus", "success")
                .containsEntry("metaVerified", true);
        List<Map<String, Object>> steps =
                (List<Map<String, Object>>) selection.getMetadata().get("steps");
        assertThat(steps).hasSize(1);
        List<Map<String, Object>> suggestions =
                (List<Map<String, Object>>) steps.get(0).get("suggestions");
        assertThat(suggestions).extracting(option -> option.get("value"))
                .containsExactly("traffic", "probe_agent_message");

        List<ChatMessagePart> chitchat = new ArrayList<>(List.of(ChatMessagePart.builder()
                .type("markdown")
                .content("你好，介绍一下你自己。")
                .build()));
        ReflectionTestUtils.invokeMethod(service, "ensurePlatformVisualizationEntitySelection",
                session, chitchat, evidence);
        assertThat(chitchat).hasSize(1);
    }

    @Test
    void platformEntitySelectionCardRepairsMalformedInfoStepsFence() throws Exception {
        DihChatApplicationService service = emptyService();
        Object evidence = newEvidenceStream();
        emitEvidenceSuccess(evidence, "retrieval_list_display_entity", "{}",
                "{\"entityList\":[{\"name\":\"traffic\",\"label\":\"网络流量\"}]}");
        ChatSession session = new ChatSession();
        session.setType(VisualizationChartAgent.AGENT_TYPE);

        ChatMessagePart malformed = ChatMessagePart.builder()
                .type("markdown")
                .content("请先选择实体。\n```zenvis:info-steps\n{not valid json\n")
                .build();
        List<ChatMessagePart> parts = new ArrayList<>(List.of(malformed));
        ReflectionTestUtils.invokeMethod(service, "ensurePlatformVisualizationEntitySelection",
                session, parts, evidence);

        assertThat(malformed.getContent()).doesNotContain("zenvis:info-steps");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(1).getType()).isEqualTo("info-steps");
    }

    @Test
    void agentRunResponseValidatesPlanCardWithStepToolEvidence() throws Exception {
        DihChatApplicationService service = streamingService();
        Object evidence = newEvidenceOnlyStream();
        emitEvidenceSuccess(evidence, "retrieval_list_display_entity", "{}",
                "{\"entityList\":[{\"name\":\"user_event\",\"label\":\"用户事件数据\"}]}");
        emitEvidenceSuccess(evidence, "retrieval_list_display_attribute",
                "{\"entity\":\"user_event\"}",
                "{\"entity\":\"user_event\",\"attributeList\":["
                        + "{\"name\":\"event_type\",\"label\":\"事件类型\",\"type\":\"string\"},"
                        + "{\"name\":\"zenvis_insert_time\",\"label\":\"创建时间\",\"type\":\"datetime\"}]}");
        String content = """
                ```zenvis:confirm
                {"title":"确认实体与字段查询方案","action":"visualization_chart.confirm_query_plan",
                "planId":"plan-run-1","entity":"user_event",
                "query":{"tool":"entity_distribution","request":{"entity":"user_event",
                "dimension":"event_type","time_field":"zenvis_insert_time",
                "time_range":{"preset":"ALL_TIME"}}}}
                ```
                """;

        Message message = ReflectionTestUtils.invokeMethod(
                service, "saveAgentRunResponse",
                null, null, content, MessageType.TEXT, null, "run-1", evidence);

        assertThat(message).isNotNull();
        assertThat(message.getParts()).extracting(ChatMessagePart::getType)
                .doesNotContain("tool-call");
        ChatMessagePart confirm = message.getParts().stream()
                .filter(part -> "confirm".equals(part.getType()))
                .findFirst()
                .orElseThrow();
        assertThat(confirm.getMetadata())
                .containsEntry("validationStatus", "success")
                .containsEntry("metaVerified", true);
        assertThat(confirm.getStatus()).isNotEqualTo("blocked");
    }

    @Test
    @SuppressWarnings("unchecked")
    void agentRunResponsePersistsPendingMcpApprovalPart() throws Exception {
        DihChatApplicationService service = streamingService();
        Object evidence = newEvidenceOnlyStream();
        McpToolInvocation invocation = new McpToolInvocation()
                .setRequestId("approval-run-1")
                .setToolKey("local::push_task_update_and_restart")
                .setToolName("push_task_update_and_restart")
                .setDescription("更新推送任务并重启")
                .setRiskLevel(McpToolRiskLevel.HIGH)
                .setChannel(McpInvocationChannel.CHAT_AGENT)
                .setPolicySnapshot(McpApprovalPolicy.ASK)
                .setStatus(McpInvocationStatus.PENDING)
                .setRequesterUserId(42)
                .setChatId("chat-run-1")
                .setAgentRunId("run-1")
                .setTurnId("run:run-1:7")
                .setArguments("{}");
        var recordApproval = evidence.getClass().getDeclaredMethod(
                "recordApproval", McpApprovalEvent.class);
        recordApproval.setAccessible(true);
        recordApproval.invoke(evidence, McpApprovalEvent.required(new McpApprovalVo(invocation)));

        Message message = ReflectionTestUtils.invokeMethod(
                service, "saveAgentRunResponse",
                null, null, "正在等待工具审批。", MessageType.TEXT, null, "run-1", evidence);

        ChatMessagePart approval = message.getParts().stream()
                .filter(part -> "mcp-approval".equals(part.getType()))
                .findFirst()
                .orElseThrow();
        assertThat(approval.getId()).isEqualTo("approval-run-1");
        assertThat(approval.getStatus()).isEqualTo("pending");
        assertThat(approval.getContent()).isEqualTo("更新推送任务并重启");
    }

    private Object newEvidenceOnlyStream() throws Exception {
        Class<?> streamType = java.util.Arrays.stream(
                        DihChatApplicationService.class.getDeclaredClasses())
                .filter(type -> "McpToolLogStream".equals(type.getSimpleName()))
                .findFirst()
                .orElseThrow();
        var create = streamType.getDeclaredMethod("createEvidenceOnly", String.class);
        create.setAccessible(true);
        return create.invoke(null, "turn-evidence");
    }

    private Object newEvidenceStream() throws Exception {
        Class<?> streamType = java.util.Arrays.stream(
                        DihChatApplicationService.class.getDeclaredClasses())
                .filter(type -> "McpToolLogStream".equals(type.getSimpleName()))
                .findFirst()
                .orElseThrow();
        var create = streamType.getDeclaredMethod("create");
        create.setAccessible(true);
        return create.invoke(null);
    }

    private void emitEvidenceSuccess(Object evidence,
                                     String tool,
                                     String arguments,
                                     String result) throws Exception {
        var emit = evidence.getClass().getDeclaredMethod(
                "emit", McpToolCallLoggingProvider.McpToolCallLog.class);
        emit.setAccessible(true);
        String callId = java.util.UUID.randomUUID().toString();
        emit.invoke(evidence,
                McpToolCallLoggingProvider.McpToolCallLog.started(
                        callId, tool, arguments, arguments));
        emit.invoke(evidence,
                McpToolCallLoggingProvider.McpToolCallLog.succeeded(
                        callId, tool, 1L, result, result));
    }

    private void emitSuccessfulCall(java.lang.reflect.Method emit,
                                    Object evidence,
                                    String tool,
                                    String arguments,
                                    String result) throws Exception {
        String callId = java.util.UUID.randomUUID().toString();
        emit.invoke(evidence,
                McpToolCallLoggingProvider.McpToolCallLog.started(
                        callId, tool, arguments, arguments));
        emit.invoke(evidence,
                McpToolCallLoggingProvider.McpToolCallLog.succeeded(
                        callId, tool, 1L, result, result));
    }

    private DihChatApplicationService streamingService() {
        return new DihChatApplicationService(
                null, null, null, null, null, null, null, null,
                null,
                (ReportAgent) null, (MetadataConfigAgent) null,
                (DataPushServiceAgent) null, (VisualizationChartAgent) null,
                (VisualizationAppAgent) null, (VisualizationDashboardAgent) null,
                new ChatMessagePartParser(), null, null,
                (AgentMcpToolService) null, (SkillService) null,
                null, (PushTaskService) null, (DashboardService) null, (MenuService) null
        );
    }

    private DihChatApplicationService emptyService() {
        return new DihChatApplicationService(
                null, null, null, null, null, null, null, null,
                null,
                (ReportAgent) null, (MetadataConfigAgent) null,
                (DataPushServiceAgent) null, (VisualizationChartAgent) null,
                (VisualizationAppAgent) null, (VisualizationDashboardAgent) null,
                null, null, null, (AgentMcpToolService) null, (SkillService) null,
                null, (PushTaskService) null, (DashboardService) null, (MenuService) null
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildStructuredExtraDataPatchIncludesConfigurationRecords() {
        DihChatApplicationService service = emptyService();

        ChatMessagePart part = ChatMessagePart.builder()
                .type("config-record")
                .content("调整系统信息展示配置")
                .metadata(Map.ofEntries(
                        Map.entry("recordId", "config-001"),
                        Map.entry("changeDescription", "调整系统信息展示配置"),
                        Map.entry("changeMode", "modify"),
                        Map.entry("configType", "system"),
                        Map.entry("fileName", "system-info.json"),
                        Map.entry("format", "json"),
                        Map.entry("oldConfig", Map.of("displayName", "Old")),
                        Map.entry("newConfig", Map.of("displayName", "ZenVis")),
                        Map.entry("validationStatus", "unverified"),
                        Map.entry("effectiveStatus", "no"),
                        Map.entry("validationResult", Map.of()),
                        Map.entry("applyResult", Map.of()),
                        Map.entry("updatedAt", "2026-07-27T12:00:00+08:00")
                ))
                .build();

        Map<String, Object> patch = ReflectionTestUtils.invokeMethod(
                service,
                "buildStructuredExtraDataPatch",
                List.of(part)
        );

        assertThat(patch).isNotNull();
        Map<String, Object> configuration = (Map<String, Object>) patch.get("configuration");
        assertThat(configuration).isNotNull();
        List<Map<String, Object>> records = (List<Map<String, Object>>) configuration.get("records");
        assertThat(records).hasSize(1);
        assertThat(records.get(0))
                .containsEntry("recordId", "config-001")
                .containsEntry("configType", "system")
                .containsEntry("format", "json")
                .containsEntry("validationStatus", "unverified")
                .containsEntry("effectiveStatus", "no");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildStructuredExtraDataPatchUsesDataAnalysisStagesAndCanonicalFields() {
        DihChatApplicationService service = emptyService();
        ChatMessagePart dataset = ChatMessagePart.builder()
                .type("data-analysis-record")
                .metadata(Map.ofEntries(
                        Map.entry("recordId", "dataset-001"),
                        Map.entry("stage", "dataset_preparation"),
                        Map.entry("status", "completed"),
                        Map.entry("title", "数据集准备完成"),
                        Map.entry("analysisTarget", "分析近七天上报量与失败率异常波动"),
                        Map.entry("datasetSummary", "按应用和日期聚合"),
                        Map.entry("datasetRecords", List.of(Map.of("date", "2026-07-27", "count", 120)))
                ))
                .build();
        ChatMessagePart serviceResult = ChatMessagePart.builder()
                .type("data-analysis-record")
                .metadata(Map.of(
                        "recordId", "service-001",
                        "stage", "service_analysis",
                        "serviceTaskId", "task-001",
                        "analysisResult", Map.of("method", "change-point", "anomaly", true)
                ))
                .build();
        ChatMessagePart report = ChatMessagePart.builder()
                .type("data-analysis-record")
                .metadata(Map.of(
                        "recordId", "report-001",
                        "stage", "report_output",
                        "timeline", List.of(
                                Map.of("title", "分析目标", "content", "识别异常波动"),
                                Map.of("title", "分析过程", "content", "使用变点检测"),
                                Map.of("title", "分析结论", "content", "发现一处异常波动")
                        )
                ))
                .build();

        Map<String, Object> patch = ReflectionTestUtils.invokeMethod(
                service, "buildStructuredExtraDataPatch", List.of(dataset, serviceResult, report));

        Map<String, Object> dataAnalysis = (Map<String, Object>) patch.get("dataAnalysis");
        assertThat(dataAnalysis).containsKeys("records", "datasetRecords", "serviceResults", "reportTimeline");
        assertThat((List<Map<String, Object>>) dataAnalysis.get("records")).hasSize(3);
        assertThat((List<Map<String, Object>>) dataAnalysis.get("datasetRecords"))
                .containsExactly(Map.of("date", "2026-07-27", "count", 120));
        assertThat((List<Map<String, Object>>) dataAnalysis.get("serviceResults"))
                .singleElement()
                .satisfies(item -> assertThat(item)
                        .containsEntry("serviceTaskId", "task-001")
                        .containsKey("analysisResult"));
        assertThat((List<Map<String, Object>>) dataAnalysis.get("reportTimeline"))
                .extracting(item -> item.get("title"))
                .containsExactly("分析目标", "分析过程", "分析结论");
    }

    @Test
    void incompleteDatasetOrAnalysisServiceResultIsNotPersisted() {
        DihChatApplicationService service = emptyService();
        ChatMessagePart emptyDataset = ChatMessagePart.builder()
                .type("data-analysis-record")
                .metadata(Map.of(
                        "recordId", "dataset-empty",
                        "stage", "dataset_preparation",
                        "analysisTarget", "识别异常波动",
                        "datasetSummary", "用户事件近七天聚合数据",
                        "datasetRecords", List.of()
                ))
                .build();
        ChatMessagePart emptyServiceResult = ChatMessagePart.builder()
                .type("data-analysis-record")
                .metadata(Map.of(
                        "recordId", "service-empty",
                        "stage", "service_analysis",
                        "serviceTaskId", "task-empty",
                        "analysisResult", Map.of()
                ))
                .build();

        Map<String, Object> patch = ReflectionTestUtils.invokeMethod(
                service, "buildStructuredExtraDataPatch", List.of(emptyDataset, emptyServiceResult));

        assertThat(patch).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void configurationRecordCannotBecomeEffectiveWithoutSuccessfulValidationApprovalWriteAndReadBack() {
        DihChatApplicationService service = emptyService();

        for (Map<String, Object> scenario : List.<Map<String, Object>>of(
                Map.of("validationStatus", "failed", "approvalStatus", "approved", "writeSucceeded", true, "readBackMatched", true),
                Map.of("validationStatus", "blocked", "approvalStatus", "approved", "writeSucceeded", true, "readBackMatched", true),
                Map.of("validationStatus", "success", "approvalStatus", "rejected", "writeSucceeded", true, "readBackMatched", true),
                Map.of("validationStatus", "success", "approvalStatus", "approved", "writeSucceeded", false, "readBackMatched", true),
                Map.of("validationStatus", "success", "approvalStatus", "approved", "writeSucceeded", true, "readBackMatched", false)
        )) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("recordId", "config-" + scenario.hashCode());
            metadata.put("changeDescription", "调整系统信息展示配置");
            metadata.put("changeMode", "modify");
            metadata.put("configType", "system");
            metadata.put("fileName", "system-info.json");
            metadata.put("format", "json");
            metadata.put("oldConfig", Map.of("displayName", "Old"));
            metadata.put("newConfig", Map.of("displayName", "ZenVis"));
            metadata.put("validationStatus", scenario.get("validationStatus"));
            metadata.put("effectiveStatus", "yes");
            metadata.put("validationResult", Map.of());
            metadata.put("applyResult", Map.of(
                    "approvalStatus", scenario.get("approvalStatus"),
                    "writeSucceeded", scenario.get("writeSucceeded"),
                    "readBackMatched", scenario.get("readBackMatched")
            ));
            metadata.put("updatedAt", "2026-07-27T12:00:00+08:00");

            ChatMessagePart part = ChatMessagePart.builder().type("config-record").metadata(metadata).build();
            Map<String, Object> patch = ReflectionTestUtils.invokeMethod(
                    service, "buildStructuredExtraDataPatch", List.of(part));
            Map<String, Object> configuration = (Map<String, Object>) patch.get("configuration");
            List<Map<String, Object>> records = (List<Map<String, Object>>) configuration.get("records");
            assertThat(records).singleElement()
                    .satisfies(record -> assertThat(record).containsEntry("effectiveStatus", "no"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void configurationRecordBecomesEffectiveOnlyAfterApprovedWriteAndMatchingReadBack() {
        DihChatApplicationService service = emptyService();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("recordId", "config-effective");
        metadata.put("changeDescription", "调整系统信息展示配置");
        metadata.put("changeMode", "modify");
        metadata.put("configType", "system");
        metadata.put("fileName", "system-info.json");
        metadata.put("format", "json");
        metadata.put("oldConfig", Map.of("displayName", "Old"));
        metadata.put("newConfig", Map.of("displayName", "ZenVis"));
        metadata.put("validationStatus", "success");
        metadata.put("effectiveStatus", "yes");
        metadata.put("validationResult", Map.of("status", "success"));
        metadata.put("applyResult", Map.of(
                "approvalStatus", "approved",
                "writeSucceeded", true,
                "readBackMatched", true
        ));
        metadata.put("updatedAt", "2026-07-27T12:00:00+08:00");

        ChatMessagePart part = ChatMessagePart.builder().type("config-record").metadata(metadata).build();
        Map<String, Object> patch = ReflectionTestUtils.invokeMethod(
                service, "buildStructuredExtraDataPatch", List.of(part));
        Map<String, Object> configuration = (Map<String, Object>) patch.get("configuration");
        List<Map<String, Object>> records = (List<Map<String, Object>>) configuration.get("records");

        assertThat(records).singleElement()
                .satisfies(record -> assertThat(record).containsEntry("effectiveStatus", "yes"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void writtenMetadataRecordPersistsEvenWhenApplyFailed() {
        String content = """
                {"entity":[{"name":"probe_event"}],"attribute":[],"operator":[]}
                """;
        ConfigService configService = mock(ConfigService.class);
        when(configService.fileExistsInConfigPath("meta", "probe_event.json"))
                .thenReturn(true);
        when(configService.readFile("meta", "probe_event.json"))
                .thenReturn(content);
        DihChatApplicationService service = emptyService();
        ReflectionTestUtils.setField(service, "configService", configService);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fileName", "probe_event.json");
        metadata.put("status", "written");
        metadata.put("source", "workflow");
        metadata.put("validationStatus", "failed");
        metadata.put("candidateDigest", "candidate-digest");
        metadata.put("config", JacksonUtil.toObject(content, Map.class));
        ChatMessagePart part = ChatMessagePart.builder()
                .type("metadata-config-record")
                .content(content)
                .metadata(metadata)
                .build();

        Map<String, Object> patch = ReflectionTestUtils.invokeMethod(
                service, "buildStructuredExtraDataPatch", List.of(part));

        Map<String, Object> metadataConfig = (Map<String, Object>) patch.get("metadataConfig");
        List<Map<String, Object>> records =
                (List<Map<String, Object>>) metadataConfig.get("records");
        assertThat(records).singleElement().satisfies(record -> assertThat(record)
                .containsEntry("fileName", "probe_event.json")
                .containsEntry("status", "written")
                .containsEntry("validationStatus", "failed"));

        Map<String, Object> mislabeled = new LinkedHashMap<>(metadata);
        mislabeled.put("status", "applied");
        ChatMessagePart mislabeledPart = ChatMessagePart.builder()
                .type("metadata-config-record")
                .content(content)
                .metadata(mislabeled)
                .build();
        Map<String, Object> normalizedPatch = ReflectionTestUtils.invokeMethod(
                service, "buildStructuredExtraDataPatch", List.of(mislabeledPart));
        Map<String, Object> normalizedConfig =
                (Map<String, Object>) normalizedPatch.get("metadataConfig");
        assertThat((List<Map<String, Object>>) normalizedConfig.get("records"))
                .singleElement()
                .satisfies(record -> assertThat(record)
                        .containsEntry("status", "written"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void forcedMetadataRecordPersistsAuditFieldsAndCannotClaimValidationSuccess() {
        String content = """
                {"entity":[],"attribute":[],"operator":[]}
                """;
        ConfigService configService = mock(ConfigService.class);
        when(configService.fileExistsInConfigPath("meta", "probe_event.json"))
                .thenReturn(true);
        when(configService.readFile("meta", "probe_event.json"))
                .thenReturn(content);
        DihChatApplicationService service = emptyService();
        ReflectionTestUtils.setField(service, "configService", configService);

        Map<String, Object> forcedMetadata = new LinkedHashMap<>();
        forcedMetadata.put("fileName", "probe_event.json");
        forcedMetadata.put("status", "applied");
        forcedMetadata.put("source", "workflow");
        forcedMetadata.put("forced", true);
        forcedMetadata.put("validationStatus", "forced");
        forcedMetadata.put("candidateDigest", "candidate-digest");
        forcedMetadata.put("validationDigest", "validation-digest");
        forcedMetadata.put("validationErrors", List.of("META_COLUMN_TYPE_INVALID"));
        forcedMetadata.put("config", JacksonUtil.toObject(content, Map.class));
        ChatMessagePart forcedPart = ChatMessagePart.builder()
                .type("metadata-config-record")
                .content(content)
                .metadata(forcedMetadata)
                .build();

        Map<String, Object> patch = ReflectionTestUtils.invokeMethod(
                service, "buildStructuredExtraDataPatch", List.of(forcedPart));
        Map<String, Object> metadataConfig = (Map<String, Object>) patch.get("metadataConfig");
        List<Map<String, Object>> records =
                (List<Map<String, Object>>) metadataConfig.get("records");
        assertThat(records).singleElement().satisfies(record -> assertThat(record)
                .containsEntry("forced", true)
                .containsEntry("validationStatus", "forced")
                .containsEntry("candidateDigest", "candidate-digest")
                .containsEntry("validationDigest", "validation-digest")
                .containsEntry("validationErrors", List.of("META_COLUMN_TYPE_INVALID")));

        Map<String, Object> masqueraded = new LinkedHashMap<>(forcedMetadata);
        masqueraded.put("validationStatus", "success");
        ChatMessagePart invalidPart = ChatMessagePart.builder()
                .type("metadata-config-record")
                .content(content)
                .metadata(masqueraded)
                .build();
        Object masqueradedPatch = ReflectionTestUtils.invokeMethod(
                service, "buildStructuredExtraDataPatch", List.of(invalidPart));
        assertThat(masqueradedPatch).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void modelGeneratedOperationRecordsAreIgnoredWhileServerVerifiedRecordIsPersisted() {
        DihChatApplicationService service = emptyService();
        ChatMessagePart forgedRename = ChatMessagePart.builder()
                .type("metadata-config-record")
                .content("")
                .metadata(Map.of(
                        "operation", "rename",
                        "status", "renamed",
                        "fileName", "new.json",
                        "previousFileName", "old.json"))
                .build();
        ChatMessagePart forgedDelete = ChatMessagePart.builder()
                .type("metadata-config-record")
                .content("")
                .metadata(Map.of(
                        "operation", "delete",
                        "status", "deleted",
                        "fileName", "old.json"))
                .build();

        Object forgedPatch = ReflectionTestUtils.invokeMethod(
                service,
                "buildMetadataConfigExtraDataPatch",
                List.of(forgedRename, forgedDelete));
        assertThat(forgedPatch).isNull();

        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        when(chatSessionService.updateWorkflowState(any(), any(), any(), any()))
                .thenReturn(true);
        ReflectionTestUtils.setField(service, "chatSessionService", chatSessionService);
        ChatSession session = new ChatSession()
                .setSessionId("chat-operation")
                .setExtraData("{}");
        session.setId(1);
        session.setCreateBy(7);
        AgentWorkflowState workflow = new AgentWorkflowState()
                .setWorkflowId("workflow-operation")
                .setContext(new LinkedHashMap<>(Map.of(
                        "operationDigest", "server-operation-digest")));
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operation", "delete");
        operation.put("sourcePath", "old.json");
        operation.put("affectedPaths", List.of("old.json"));
        operation.put("digest", "server-operation-digest");

        ReflectionTestUtils.invokeMethod(
                service,
                "persistMetaOperationRecord",
                session,
                workflow,
                null,
                operation,
                "deleted",
                "success",
                true,
                true,
                "配置文件已删除");

        Map<String, Object> extraData = JacksonUtil.toObject(session.getExtraData(), Map.class);
        Map<String, Object> metadataConfig =
                (Map<String, Object>) extraData.get("metadataConfig");
        List<Map<String, Object>> records =
                (List<Map<String, Object>>) metadataConfig.get("records");
        assertThat(records).singleElement().satisfies(record -> assertThat(record)
                .containsEntry("fileName", "old.json")
                .containsEntry("operation", "delete")
                .containsEntry("status", "deleted")
                .containsEntry("operationDigest", "server-operation-digest")
                .containsEntry("source", "workflow"));
    }

    @Test
    void metadataConfigExampleStartsWithoutConfiguredModelOrAgent() {
        FakeChatSessionService sessionService = new FakeChatSessionService();
        ThrowingAIBaseService baseService = new ThrowingAIBaseService();
        ThrowingChatModel titleModel = new ThrowingChatModel();
        DihChatApplicationService service = new DihChatApplicationService(
                null,
                baseService,
                sessionService,
                new MetadataConfigDemoResponseService(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ChatMessagePartParser(),
                null,
                new ChatTitleService(titleModel),
                null,
                new EnabledSkillService(),
                null,
                null,
                null,
                null
        );
        ChatDto chatDto = new ChatDto();
        chatDto.setType(MetadataConfigAgent.AGENT_TYPE);
        chatDto.setChatId("metadata-config-demo-chat");
        chatDto.setModel("unsupported-model-should-not-be-checked");
        chatDto.setResponseFormat(DihChatApplicationService.RESPONSE_FORMAT_EVENTS);
        chatDto.setMessage(
                MetadataConfigDemoResponseService.USER_EVENT_EXAMPLE_PROMPT);

        String response = String.join("", service.chat(chatDto, null).collectList().block());

        assertThat(response).contains("zenvis:info-steps", "用户事件数据接入元数据确认");
        assertThat(baseService.isModelSupportedCalls.get()).isZero();
        assertThat(baseService.resolveChatModelCalls.get()).isZero();
        assertThat(titleModel.calls.get()).isZero();
        assertThat(sessionService.session.getTitle())
                .isEqualTo(MetadataConfigDemoResponseService.USER_EVENT_DEMO_TITLE);
    }

    @Test
    void builtinDemoStillWinsWhenPersistentRunsAreEnabledForAllAgentTypes() {
        FakeChatSessionService sessionService = new FakeChatSessionService();
        ThrowingAIBaseService baseService = new ThrowingAIBaseService();
        ThrowingChatModel titleModel = new ThrowingChatModel();
        AgentRunService agentRunService = mock(AgentRunService.class);
        when(agentRunService.isEnabledFor(MetadataConfigAgent.AGENT_TYPE)).thenReturn(true);
        DihChatApplicationService service = new DihChatApplicationService(
                null,
                baseService,
                sessionService,
                new MetadataConfigDemoResponseService(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ChatMessagePartParser(),
                null,
                new ChatTitleService(titleModel),
                null,
                new EnabledSkillService(),
                null,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(service, "agentRunService", agentRunService);
        ChatDto chatDto = new ChatDto();
        chatDto.setType(MetadataConfigAgent.AGENT_TYPE);
        chatDto.setChatId("metadata-config-demo-chat");
        chatDto.setModel("unsupported-model-should-not-be-checked");
        chatDto.setResponseFormat(DihChatApplicationService.RESPONSE_FORMAT_EVENTS);
        chatDto.setMessage(MetadataConfigDemoResponseService.USER_EVENT_EXAMPLE_PROMPT);

        String response = String.join("", service.chat(chatDto, null).collectList().block());

        assertThat(response).contains("zenvis:info-steps", "用户事件数据接入元数据确认");
        assertThat(baseService.isModelSupportedCalls.get()).isZero();
        verify(agentRunService, never()).create(any(), any());
    }

    @Test
    void allBuiltinExamplesResolveToDeterministicDemoRoutes() {
        DihChatApplicationService service = emptyService();
        List<Map<String, String>> examples = List.of(
                Map.of(
                        "type", MetadataConfigAgent.AGENT_TYPE,
                        "prompt", MetadataConfigDemoResponseService
                                .USER_EVENT_EXAMPLE_PROMPT,
                        "demoId", "metadata-config:user-event"),
                Map.of(
                        "type", DataPushServiceAgent.AGENT_TYPE,
                        "prompt", DataPushDemoResponseService
                                .USER_EVENT_EXAMPLE_PROMPT,
                        "demoId", "data-push:user-event"),
                Map.of(
                        "type", VisualizationChartAgent.AGENT_TYPE,
                        "prompt", VisualizationChartDemoResponseService
                                .CHART_EXAMPLE_PROMPT,
                        "demoId", "visualization-chart:chart"),
                Map.of(
                        "type", VisualizationAppAgent.AGENT_TYPE,
                        "prompt", VisualizationAppDemoResponseService
                                .PAGE_EXAMPLE_PROMPT,
                        "demoId", "visualization-app:single-page"),
                Map.of(
                        "type", VisualizationAppAgent.AGENT_TYPE,
                        "prompt", VisualizationAppDemoResponseService
                                .SIDEBAR_APP_EXAMPLE_PROMPT,
                        "demoId", "visualization-app:sidebar"),
                Map.of(
                        "type", VisualizationDashboardAgent.AGENT_TYPE,
                        "prompt", VisualizationDashboardDemoResponseService
                                .DASHBOARD_EXAMPLE_PROMPT,
                        "demoId", "visualization-dashboard:dashboard"),
                Map.of(
                        "type", VisualizationAppAgent.AGENT_TYPE,
                        "prompt", VisualizationAppDemoResponseService
                                .MENU_EXAMPLE_PROMPT,
                        "demoId", "visualization-app:menu"),
                Map.of(
                        "type", ReportAgent.AGENT_TYPE,
                        "prompt", ReportDemoResponseService
                                .REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT,
                        "demoId", "report:user-event-analysis"),
                Map.of(
                        "type", ReportAgent.AGENT_TYPE,
                        "prompt", ReportDemoResponseService
                                .REPORT_OPERATION_WEEKLY_EXAMPLE_PROMPT,
                        "demoId", "report:operation-weekly"),
                Map.of(
                        "type", ReportAgent.AGENT_TYPE,
                        "prompt", ReportDemoResponseService
                                .REPORT_INCIDENT_REVIEW_EXAMPLE_PROMPT,
                        "demoId", "report:incident-review"),
                Map.of(
                        "type", ReportAgent.AGENT_TYPE,
                        "prompt", ReportDemoResponseService
                                .REPORT_VISUALIZATION_ARCHIVE_EXAMPLE_PROMPT,
                        "demoId", "report:visualization-archive"));

        examples.forEach(example -> assertThat(
                (String) ReflectionTestUtils.invokeMethod(
                        service,
                        "resolveBuiltinDemoId",
                        example.get("type"),
                        example.get("prompt"),
                        null))
                .isEqualTo(example.get("demoId")));
        assertThat((String) ReflectionTestUtils.invokeMethod(
                service,
                "resolveBuiltinDemoId",
                VisualizationChartAgent.AGENT_TYPE,
                "请分析真实探针消息，生成近 24 小时趋势图",
                null))
                .isEmpty();
        assertThat((String) ReflectionTestUtils.invokeMethod(
                service,
                "resolveBuiltinDemoId",
                MetadataConfigAgent.AGENT_TYPE,
                MetadataConfigDemoResponseService.USER_EVENT_EXAMPLE_PROMPT
                        + "\n请把实体名称修改为真实探针实体",
                null))
                .isEmpty();
        assertThat((String) ReflectionTestUtils.invokeMethod(
                service,
                "resolveBuiltinDemoId",
                "agent_data_access",
                MetadataConfigDemoResponseService.USER_EVENT_EXAMPLE_PROMPT,
                null))
                .isEmpty();
    }

    @Test
    void reportDemoChatUsesTemplateWithoutCallingModelAgent() {
        FakeChatSessionService sessionService = new FakeChatSessionService();
        ThrowingAIBaseService baseService = new ThrowingAIBaseService();
        CountingReportAgent reportAgent = new CountingReportAgent();
        ThrowingChatModel titleModel = new ThrowingChatModel();

        DihChatApplicationService service = new DihChatApplicationService(
                null,
                baseService,
                sessionService,
                null,
                null,
                null,
                null,
                null,
                new ReportDemoResponseService(),
                reportAgent,
                (MetadataConfigAgent) null,
                (DataPushServiceAgent) null,
                (VisualizationChartAgent) null,
                null,
                null,
                new ChatMessagePartParser(),
                null,
                new ChatTitleService(titleModel),
                null,
                new EnabledSkillService(),
                null,
                (PushTaskService) null,
                (DashboardService) null,
                (MenuService) null
        );

        ChatDto chatDto = new ChatDto();
        chatDto.setType(ReportAgent.AGENT_TYPE);
        chatDto.setChatId("report-demo-chat");
        chatDto.setModel("unsupported-model-should-not-be-checked");
        chatDto.setMessage(REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT);
        chatDto.setResponseFormat(DihChatApplicationService.RESPONSE_FORMAT_EVENTS);

        String response = String.join("", service.chat(chatDto, null).collectList().block());

        assertThat(response).contains("zenvis:report-document-config");
        assertThat(reportAgent.calls.get()).isZero();
        assertThat(baseService.isModelSupportedCalls.get()).isZero();
        assertThat(baseService.resolveChatModelCalls.get()).isZero();
        assertThat(titleModel.calls.get()).isZero();
        assertThat(sessionService.session.getTitle()).isEqualTo(ReportDemoResponseService.REPORT_DEMO_TITLE);
        assertThat(sessionService.session.getExtraData())
                .contains("\"report\"")
                .contains("\"currentDocument\"")
                .contains("用户事件数据分析报告");
    }

    @Test
    void visualizationDemoStartsWithoutConfiguredModelAndCarriesDemoMetadataOnly() {
        FakeChatSessionService sessionService = new FakeChatSessionService();
        ThrowingAIBaseService baseService = new ThrowingAIBaseService();
        ThrowingChatModel titleModel = new ThrowingChatModel();
        ConfigService configService = mock(ConfigService.class);
        when(configService.fileExistsInConfigPath("meta", "user_event.json"))
                .thenReturn(true);
        DihChatApplicationService service = new DihChatApplicationService(
                null,
                baseService,
                sessionService,
                null,
                null,
                new VisualizationChartDemoResponseService(
                        configService),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ChatMessagePartParser(),
                null,
                new ChatTitleService(titleModel),
                null,
                new EnabledSkillService(),
                configService,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(
                service, "agentDemoStateStore", new AgentDemoStateStore());
        ChatDto chatDto = new ChatDto();
        chatDto.setType(VisualizationChartAgent.AGENT_TYPE);
        chatDto.setChatId("visualization-demo-chat");
        chatDto.setModel("unsupported-model-should-not-be-checked");
        chatDto.setResponseFormat(DihChatApplicationService.RESPONSE_FORMAT_EVENTS);
        chatDto.setMessage(
                VisualizationChartDemoResponseService.CHART_EXAMPLE_PROMPT);

        String response = String.join(
                "", service.chat(chatDto, null).collectList().block());

        assertThat(response)
                .contains("zenvis:info-steps")
                .contains("用户事件临时图表信息确认");
        assertThat(baseService.isModelSupportedCalls.get()).isZero();
        assertThat(baseService.resolveChatModelCalls.get()).isZero();
        assertThat(titleModel.calls.get()).isZero();
        assertThat(sessionService.session.getTitle()).isEqualTo(
                VisualizationChartDemoResponseService.USER_EVENT_DEMO_TITLE);
        assertThat(sessionService.session.getExtraData())
                .contains("\"agentDemos\"")
                .contains("\"visualization-chart:chart\"")
                .contains("\"stage\":\"initial\"");
        Message aiMessage = sessionService.messages.stream()
                .filter(message -> "ai".equals(message.getSender()))
                .findFirst()
                .orElseThrow();
        assertThat(aiMessage.getParts()).isNotEmpty();
        assertThat(aiMessage.getParts())
                .allSatisfy(part -> assertThat(part.getMetadata())
                        .containsEntry(
                                "demoId",
                                "visualization-chart:chart")
                        .doesNotContainKey("workflowId"));
    }

    private static class ThrowingAIBaseService extends AIBaseService {
        private final AtomicInteger isModelSupportedCalls = new AtomicInteger();
        private final AtomicInteger resolveChatModelCalls = new AtomicInteger();

        private ThrowingAIBaseService() {
            super("", "", "");
        }

        @Override
        public boolean isModelSupported(String model) {
            isModelSupportedCalls.incrementAndGet();
            throw new AssertionError("内置演示示例不应校验后台模型");
        }

        @Override
        public String resolveChatModel(String requestedModel, boolean deepThinking, boolean hasImageAttachment) {
            resolveChatModelCalls.incrementAndGet();
            throw new AssertionError("内置演示示例不应选择后台模型");
        }
    }

    private static class CountingReportAgent extends ReportAgent {
        private final AtomicInteger calls = new AtomicInteger();

        private CountingReportAgent() {
            super(null, null);
        }

        @Override
        public Flux<String> chat(String chatId, String model, String prompt, List<ChatAttachment> attachments, User user,
                                 McpToolContext mcpToolContext) {
            calls.incrementAndGet();
            throw new AssertionError("报表示例不应调用 ReportAgent");
        }
    }

    private static class EnabledSkillService extends SkillService {
        private EnabledSkillService() {
            super(null, new ObjectMapper());
        }

        @Override
        public boolean isBuiltinAgentType(String agentType) {
            return true;
        }

        @Override
        public boolean isBuiltinAgentEnabled(String agentType) {
            return true;
        }
    }

    private static class ThrowingChatModel implements ChatModel {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            throw new AssertionError("报表示例标题不应调用模型");
        }
    }

    private static class FakeChatSessionService implements ChatSessionService {
        private final List<Message> messages = new ArrayList<>();
        private ChatSession session;

        @Override
        public List<ChatSessionVo> findAll() {
            return List.of();
        }

        @Override
        public ChatSession create(ChatSessionDto chatSessionDto, User currentUser) {
            return null;
        }

        @Override
        public Boolean update(Long id, ChatSessionDto chatSessionDto, User currentUser) {
            if (session != null && chatSessionDto.getExtraData() != null) {
                session.setExtraData(chatSessionDto.getExtraData());
            }
            return true;
        }

        @Override
        public void delete(Long id, User currentUser) {
        }

        @Override
        public void deleteByIds(List<Long> ids, User currentUser) {
        }

        @Override
        public ChatSessionVo info(Long id, User currentUser) {
            return null;
        }

        @Override
        public List<ChatSessionVo> getPinList(User currentUser) {
            return List.of();
        }

        @Override
        public PageRowsVo<ChatSessionVo> getPageList(ChatSessionSearchDto chatSessionSearchDto, User currentUser) {
            return null;
        }

        @Override
        public ChatSession getChatSessionBySessionId(String chatId, User currentUser) {
            return session != null && chatId.equals(session.getSessionId()) ? session : null;
        }

        @Override
        public ChatSession appendMessage(String chatId, ChatSessionDto createDefaults, Message message, User currentUser) {
            if (session == null) {
                session = new ChatSession()
                        .setSessionId(chatId)
                        .setTitle(createDefaults.getTitle())
                        .setType(createDefaults.getType())
                        .setDeepThink(createDefaults.getDeepThink())
                        .setOnlineSearch(createDefaults.getOnlineSearch());
                session.setId(1);
            }
            messages.add(message);
            session.setMessages(JacksonUtil.toJson(messages));
            return session;
        }

        @Override
        public ChatSession appendMessage(ChatSession chatSession, Message message, User currentUser) {
            messages.add(message);
            chatSession.setMessages(JacksonUtil.toJson(messages));
            return chatSession;
        }
    }
}
