package com.coolxer.service.dih;

import com.coolxer.commons.enums.MessageType;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.dto.ChatDto;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.model.dih.dto.ChatSessionSearchDto;
import com.coolxer.model.dih.vo.ChatSessionVo;
import com.coolxer.service.dih.agent.DataAnalysisAgent;
import com.coolxer.service.dih.agent.DataAccessAgent;
import com.coolxer.service.dih.agent.DataVisualizationAgent;
import com.coolxer.service.dih.agent.ConfigManagementAgent;
import com.coolxer.service.dih.agent.ReportAgent;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.mcp.McpToolCallLoggingProvider;
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
    void buildStructuredExtraDataPatchIncludesDataVisualizationChartLibrary() {
        DihChatApplicationService service = new DihChatApplicationService(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                (DataAnalysisAgent) null,
                (ConfigManagementAgent) null,
                (ReportAgent) null,
                (DataAccessAgent) null,
                (DataVisualizationAgent) null,
                null,
                null,
                null,
                (AgentMcpToolService) null,
                (SkillService) null,
                null,
                (PushTaskService) null,
                (DashboardService) null,
                (MenuService) null
        );

        ChatMessagePart part = ChatMessagePart.builder()
                .type("visualization-chart-record")
                .content("登录趋势图")
                .metadata(Map.of(
                        "id", "login-trend",
                        "name", "登录趋势图",
                        "chartType", "line",
                        "entity", "user_event",
                        "api", "/api/v1/entity/user_event/list"
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
                .containsEntry("id", "login-trend")
                .containsEntry("name", "登录趋势图")
                .containsEntry("chartType", "line")
                .containsEntry("entity", "user_event");
    }

    private DihChatApplicationService emptyService() {
        return new DihChatApplicationService(
                null, null, null, null, null, null, null, null,
                (DataAnalysisAgent) null, (ConfigManagementAgent) null, (ReportAgent) null,
                (DataAccessAgent) null, (DataVisualizationAgent) null,
                null, null, null, (AgentMcpToolService) null, (SkillService) null,
                null, (PushTaskService) null, (DashboardService) null, (MenuService) null
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildStructuredExtraDataPatchIncludesConfigurationRecords() {
        DihChatApplicationService service = new DihChatApplicationService(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                (DataAnalysisAgent) null,
                (ConfigManagementAgent) null,
                (ReportAgent) null,
                (DataAccessAgent) null,
                (DataVisualizationAgent) null,
                null,
                null,
                null,
                (AgentMcpToolService) null,
                (SkillService) null,
                null,
                (PushTaskService) null,
                (DashboardService) null,
                (MenuService) null
        );

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
        chatDto.setMessage("""
                # 用户事件数据接入
                表：msg_user_event
                数据源：demo_logs
                字段：event_type、server_time、reliability
                """);

        String response = String.join("", service.chat(chatDto, null).collectList().block());

        assertThat(response).contains("zenvis:info-steps", "用户事件数据接入元数据确认");
        assertThat(baseService.isModelSupportedCalls.get()).isZero();
        assertThat(baseService.resolveChatModelCalls.get()).isZero();
        assertThat(titleModel.calls.get()).isZero();
        assertThat(sessionService.session.getTitle())
                .isEqualTo(DataAccessDemoResponseService.USER_EVENT_DEMO_TITLE);
    }

    @Test
    void dataAnalysisExampleUsesBuiltinThreeStageResultsWithoutModelOrAgent() {
        FakeChatSessionService sessionService = new FakeChatSessionService();
        ThrowingAIBaseService baseService = new ThrowingAIBaseService();
        CountingDataAnalysisAgent dataAnalysisAgent = new CountingDataAnalysisAgent();
        ThrowingChatModel titleModel = new ThrowingChatModel();
        DihChatApplicationService service = new DihChatApplicationService(
                null,
                baseService,
                sessionService,
                null,
                null,
                new DataAnalysisDemoResponseService(),
                null,
                null,
                dataAnalysisAgent,
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
        chatDto.setType(DataAnalysisAgent.AGENT_TYPE);
        chatDto.setChatId("data-analysis-demo-chat");
        chatDto.setModel("unsupported-model-should-not-be-checked");
        chatDto.setResponseFormat(DihChatApplicationService.RESPONSE_FORMAT_EVENTS);

        chatDto.setMessage(DataAnalysisDemoResponseService.DATA_ANALYSIS_EXAMPLE_PROMPT);
        String dataset = String.join("", service.chat(chatDto, null).collectList().block());
        chatDto.setMessage("我已确认当前数据集。请继续分析。");
        String analysis = String.join("", service.chat(chatDto, null).collectList().block());
        chatDto.setMessage("我已确认分析服务结果。请生成报告。");
        String report = String.join("", service.chat(chatDto, null).collectList().block());

        assertThat(dataset).contains("dataset_preparation", "analysis.confirm_dataset");
        assertThat(analysis).contains("service_analysis", "builtin-demo-analysis-001");
        assertThat(report).contains("report_output", "zenvis:report-document-config");
        assertThat(dataAnalysisAgent.calls.get()).isZero();
        assertThat(baseService.isModelSupportedCalls.get()).isZero();
        assertThat(baseService.resolveChatModelCalls.get()).isZero();
        assertThat(titleModel.calls.get()).isZero();
        assertThat(sessionService.session.getTitle())
                .isEqualTo(DataAnalysisDemoResponseService.DATA_ANALYSIS_DEMO_TITLE);
        assertThat(sessionService.session.getExtraData())
                .contains("\"dataAnalysis\"")
                .contains("demo-analysis-dataset-001")
                .contains("demo-analysis-service-001")
                .contains("demo-analysis-report-001");
    }

    @Test
    void configManagementExampleUsesBuiltinThreeStageResultsWithoutModelOrAgent() {
        FakeChatSessionService sessionService = new FakeChatSessionService();
        ThrowingAIBaseService baseService = new ThrowingAIBaseService();
        CountingConfigManagementAgent configManagementAgent = new CountingConfigManagementAgent();
        ThrowingChatModel titleModel = new ThrowingChatModel();
        DihChatApplicationService service = new DihChatApplicationService(
                null,
                baseService,
                sessionService,
                null,
                null,
                null,
                new ConfigManagementDemoResponseService(),
                null,
                null,
                configManagementAgent,
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
        chatDto.setType(ConfigManagementAgent.AGENT_TYPE);
        chatDto.setChatId("config-management-demo-chat");
        chatDto.setModel("unsupported-model-should-not-be-checked");
        chatDto.setResponseFormat(DihChatApplicationService.RESPONSE_FORMAT_EVENTS);

        chatDto.setMessage(ConfigManagementDemoResponseService.CONFIG_MANAGEMENT_EXAMPLE_PROMPT);
        String generated = String.join("", service.chat(chatDto, null).collectList().block());
        chatDto.setMessage("我已确认进入试验场验证。");
        String validated = String.join("", service.chat(chatDto, null).collectList().block());
        chatDto.setMessage("我已确认将验证成功的配置正式下发。");
        String applied = String.join("", service.chat(chatDto, null).collectList().block());

        assertThat(generated).contains("demo-config-system-info-001", "config.confirm_trial");
        assertThat(validated).contains("validationStatus", "success", "config.confirm_apply");
        assertThat(applied).contains("effectiveStatus", "yes", "actualSystemChanged");
        assertThat(configManagementAgent.calls.get()).isZero();
        assertThat(baseService.isModelSupportedCalls.get()).isZero();
        assertThat(baseService.resolveChatModelCalls.get()).isZero();
        assertThat(titleModel.calls.get()).isZero();
        assertThat(sessionService.session.getTitle())
                .isEqualTo(ConfigManagementDemoResponseService.CONFIG_MANAGEMENT_DEMO_TITLE);
        assertThat(sessionService.session.getExtraData())
                .contains("\"configuration\"")
                .contains("demo-config-system-info-001")
                .contains("\"effectiveStatus\":\"yes\"");
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
                new ReportDemoResponseService(),
                (DataAnalysisAgent) null,
                (ConfigManagementAgent) null,
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

    private static class CountingDataAnalysisAgent extends DataAnalysisAgent {
        private final AtomicInteger calls = new AtomicInteger();

        private CountingDataAnalysisAgent() {
            super(null, null);
        }

        @Override
        public Flux<String> chat(String chatId,
                                 String model,
                                 String prompt,
                                 List<ChatAttachment> attachments,
                                 User user,
                                 List<String> skillIds,
                                 McpToolContext mcpToolContext) {
            calls.incrementAndGet();
            throw new AssertionError("数据分析示例不应调用 DataAnalysisAgent");
        }
    }

    private static class CountingConfigManagementAgent extends ConfigManagementAgent {
        private final AtomicInteger calls = new AtomicInteger();

        private CountingConfigManagementAgent() {
            super(null, null);
        }

        @Override
        public Flux<String> chat(String chatId,
                                 String model,
                                 String prompt,
                                 List<ChatAttachment> attachments,
                                 User user,
                                 List<String> skillIds,
                                 McpToolContext mcpToolContext) {
            calls.incrementAndGet();
            throw new AssertionError("配置管理示例不应调用 ConfigManagementAgent");
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
