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
import com.coolxer.model.dih.dto.ChatDto;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.model.dih.dto.ChatSessionSearchDto;
import com.coolxer.model.dih.vo.ChatSessionVo;
import com.coolxer.model.dih.vo.McpApprovalVo;
import com.coolxer.service.dih.agent.DataAccessAgent;
import com.coolxer.service.dih.agent.DataVisualizationAgent;
import com.coolxer.service.dih.agent.ReportAgent;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.demo.AgentDemoStateStore;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.mcp.McpToolCallLoggingProvider;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.system.DashboardService;
import com.coolxer.service.system.MenuService;
import com.coolxer.service.system.PushTaskService;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.coolxer.service.dih.ReportDemoResponseService.REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DihChatApplicationServiceTest {

    private static final String CONTEXT_LENGTH_EXCEEDED_MESSAGE =
            "当前对话内容过长，已超过模型可处理的上下文长度。请新建对话，或减少历史消息、附件及输入内容后重试。";

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
                                .BUILTIN_DATA_VISUALIZATION_DEMO)
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
        Map<String, Object> dataVisualization = (Map<String, Object>) patch.get("dataVisualization");
        assertThat(dataVisualization).isNotNull();
        List<Map<String, Object>> chartLibrary = (List<Map<String, Object>>) dataVisualization.get("chartLibrary");
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
                        "demoId", "data-visualization:chart",
                        "config", Map.of(
                                "type", "chart",
                                "config", Map.of("series", List.of()))))
                .build();

        Map<String, Object> patch = ReflectionTestUtils.invokeMethod(
                service, "buildStructuredExtraDataPatch", List.of(part));

        assertThat(patch).isNotNull();
        Map<String, Object> dataVisualization =
                (Map<String, Object>) patch.get("dataVisualization");
        List<Map<String, Object>> chartLibrary =
                (List<Map<String, Object>>) dataVisualization.get("chartLibrary");
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
                        "action", "data_visualization.select_entity_from_meta",
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
                        "action", "data_visualization.confirm_query_plan",
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
                .containsEntry("blockedAction", "data_visualization.confirm_query_plan")
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
                .containsEntry("action", "data_visualization.select_entity_from_meta")
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
                        "action", "data_visualization.confirm_query_plan",
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

        ChatMessagePart hallucinatedFieldPlan = ChatMessagePart.builder()
                .type("confirm")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "data_visualization.confirm_query_plan",
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
                        "action", "data_visualization.confirm_query_plan",
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
                        "action", "data_visualization.add_chart_library",
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
                .containsEntry("validationMessage",
                        "实际数据 MCP 查询参数与已批准方案不一致，图表不能验证或入库");
    }

    private void emitSuccessfulCall(java.lang.reflect.Method emit,
                                    Object evidence,
                                    String tool,
                                    String arguments,
                                    String result) throws Exception {
        emit.invoke(evidence,
                McpToolCallLoggingProvider.McpToolCallLog.started(
                        tool, arguments, arguments));
        emit.invoke(evidence,
                McpToolCallLoggingProvider.McpToolCallLog.succeeded(
                        tool, 1L, result, result));
    }

    private DihChatApplicationService emptyService() {
        return new DihChatApplicationService(
                null, null, null, null, null, null,
                (ReportAgent) null, (DataAccessAgent) null, (DataVisualizationAgent) null,
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
    void dataAccessExampleStartsWithoutConfiguredModelOrAgent() {
        FakeChatSessionService sessionService = new FakeChatSessionService();
        ThrowingAIBaseService baseService = new ThrowingAIBaseService();
        ThrowingChatModel titleModel = new ThrowingChatModel();
        DihChatApplicationService service = new DihChatApplicationService(
                null,
                baseService,
                sessionService,
                new DataAccessDemoResponseService(),
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
        chatDto.setType(DataAccessAgent.AGENT_TYPE);
        chatDto.setChatId("data-access-demo-chat");
        chatDto.setModel("unsupported-model-should-not-be-checked");
        chatDto.setResponseFormat(DihChatApplicationService.RESPONSE_FORMAT_EVENTS);
        chatDto.setMessage(
                DataAccessDemoResponseService.USER_EVENT_EXAMPLE_PROMPT);

        String response = String.join("", service.chat(chatDto, null).collectList().block());

        assertThat(response).contains("zenvis:info-steps", "用户事件数据接入元数据确认");
        assertThat(baseService.isModelSupportedCalls.get()).isZero();
        assertThat(baseService.resolveChatModelCalls.get()).isZero();
        assertThat(titleModel.calls.get()).isZero();
        assertThat(sessionService.session.getTitle())
                .isEqualTo(DataAccessDemoResponseService.USER_EVENT_DEMO_TITLE);
    }

    @Test
    void allTenBuiltinExamplesResolveToDeterministicDemoRoutes() {
        DihChatApplicationService service = emptyService();
        List<Map<String, String>> examples = List.of(
                Map.of(
                        "type", DataAccessAgent.AGENT_TYPE,
                        "prompt", DataAccessDemoResponseService
                                .USER_EVENT_EXAMPLE_PROMPT,
                        "demoId", "data-access:user-event"),
                Map.of(
                        "type", DataVisualizationAgent.AGENT_TYPE,
                        "prompt", DataVisualizationDemoResponseService
                                .CHART_EXAMPLE_PROMPT,
                        "demoId", "data-visualization:chart"),
                Map.of(
                        "type", DataVisualizationAgent.AGENT_TYPE,
                        "prompt", DataVisualizationDemoResponseService
                                .PAGE_EXAMPLE_PROMPT,
                        "demoId", "data-visualization:single-page"),
                Map.of(
                        "type", DataVisualizationAgent.AGENT_TYPE,
                        "prompt", DataVisualizationDemoResponseService
                                .SIDEBAR_APP_EXAMPLE_PROMPT,
                        "demoId", "data-visualization:sidebar-app"),
                Map.of(
                        "type", DataVisualizationAgent.AGENT_TYPE,
                        "prompt", DataVisualizationDemoResponseService
                                .DASHBOARD_EXAMPLE_PROMPT,
                        "demoId", "data-visualization:dashboard"),
                Map.of(
                        "type", DataVisualizationAgent.AGENT_TYPE,
                        "prompt", DataVisualizationDemoResponseService
                                .MENU_EXAMPLE_PROMPT,
                        "demoId", "data-visualization:menu"),
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
                DataVisualizationAgent.AGENT_TYPE,
                "请分析真实探针消息，生成近 24 小时趋势图",
                null))
                .isEmpty();
        assertThat((String) ReflectionTestUtils.invokeMethod(
                service,
                "resolveBuiltinDemoId",
                DataAccessAgent.AGENT_TYPE,
                DataAccessDemoResponseService.USER_EVENT_EXAMPLE_PROMPT
                        + "\n请把实体名称修改为真实探针实体",
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
                new ReportDemoResponseService(),
                reportAgent,
                (DataAccessAgent) null,
                (DataVisualizationAgent) null,
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
                new DataVisualizationDemoResponseService(
                        configService,
                        mock(MenuService.class),
                        mock(DashboardService.class)),
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
        chatDto.setType(DataVisualizationAgent.AGENT_TYPE);
        chatDto.setChatId("visualization-demo-chat");
        chatDto.setModel("unsupported-model-should-not-be-checked");
        chatDto.setResponseFormat(DihChatApplicationService.RESPONSE_FORMAT_EVENTS);
        chatDto.setMessage(
                DataVisualizationDemoResponseService.CHART_EXAMPLE_PROMPT);

        String response = String.join(
                "", service.chat(chatDto, null).collectList().block());

        assertThat(response)
                .contains("zenvis:info-steps")
                .contains("用户事件临时图表信息确认");
        assertThat(baseService.isModelSupportedCalls.get()).isZero();
        assertThat(baseService.resolveChatModelCalls.get()).isZero();
        assertThat(titleModel.calls.get()).isZero();
        assertThat(sessionService.session.getTitle()).isEqualTo(
                DataVisualizationDemoResponseService
                        .USER_EVENT_VISUALIZATION_DEMO_TITLE);
        assertThat(sessionService.session.getExtraData())
                .contains("\"agentDemos\"")
                .contains("\"data-visualization:chart\"")
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
                                "data-visualization:chart")
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
