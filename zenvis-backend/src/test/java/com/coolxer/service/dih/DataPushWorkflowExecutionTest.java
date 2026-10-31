package com.coolxer.service.dih;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.model.system.vo.PushTaskOperationVo;
import com.coolxer.model.system.vo.PushTaskPageVo;
import com.coolxer.model.system.vo.PushTaskSummaryVo;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.dih.agent.DataPushServiceAgent;
import com.coolxer.service.dih.agent.MetadataConfigAgent;
import com.coolxer.service.dih.agent.ReportAgent;
import com.coolxer.service.dih.agent.VisualizationAppAgent;
import com.coolxer.service.dih.agent.VisualizationChartAgent;
import com.coolxer.service.dih.agent.VisualizationDashboardAgent;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpApprovalPendingException;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.mcp.McpToolResultUnknownException;
import com.coolxer.service.dih.workflow.AgentWorkflowState;
import com.coolxer.service.dih.workflow.AgentWorkflowStep;
import com.coolxer.service.dih.workflow.WorkflowStateStore;
import com.coolxer.service.system.DashboardService;
import com.coolxer.service.system.MenuService;
import com.coolxer.service.system.PushTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataPushWorkflowExecutionTest {

    private static final String MARK = "data-push:chat-1:probe";
    private static final String META_FILE = "user_event.json";
    private static final String META_CONTENT = "{\"entity\":[{\"name\":\"user_event\"}]}";
    private static final String CONFIG = "[sources.demo]\ntype = \"demo_logs\"";

    @Test
    void approvedCandidateUsesValidationListCreateGetAndDiagnosisWithoutFormatDetection() {
        List<String> calls = new ArrayList<>();
        PushBackend backend = new PushBackend(false, null);
        McpToolContext tools = workflowTools(calls, backend, passValidation(), successDiagnosis("run-1"));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(executionContext(CONFIG, "toml", 0, List.of())));

        String prompt = invoke(store, tools, session);

        assertThat(prompt)
                .contains("平台已完成锁定 PushTask 校验、执行与诊断")
                .contains("\"taskId\":31")
                .contains("\"runId\":\"run-1\"")
                .contains("push_task_validate_config", "push_task_list", "push_task_get");
        assertThat(calls)
                .containsSubsequence("push_task_validate_config", "push_task_list",
                        "push_task_create_and_start", "push_task_get", "push_task_diagnose")
                .doesNotContain("push_task_detect_format", "push_task_get_log");
        assertThat(store.loadActive(session, DataPushServiceAgent.AGENT_TYPE))
                .get().extracting(AgentWorkflowState::getStep)
                .isEqualTo(AgentWorkflowStep.PUSH_VERIFIED);
    }

    @Test
    void blockedRetryRevalidatesLockedCandidateWithoutListCreateOrStart() {
        List<String> calls = new ArrayList<>();
        PushBackend backend = new PushBackend(false, null);
        McpToolContext tools = workflowTools(
                calls, backend, passValidation(), successDiagnosis("run-1"));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        Map<String, Object> context = executionContext(CONFIG, "toml", 0, List.of());
        context.put("revalidateOnly", true);
        AgentWorkflowState blocked = state(context)
                .setStep(AgentWorkflowStep.PUSH_CONFIG_VALIDATION)
                .setStatus("active");
        store.upsert(session, blocked);

        String prompt = invoke(store, tools, session);

        assertThat(prompt)
                .contains("平台已重新校验锁定 PushTask 候选",
                        "平台未创建、更新或启动任何任务")
                .contains("不得修改候选", "push_task_create_and_start");
        assertThat(calls).containsExactly(
                "config_tree", "config_read", "push_task_validate_config");
        assertThat(store.loadActive(session, DataPushServiceAgent.AGENT_TYPE))
                .get().satisfies(workflow -> {
                    assertThat(workflow.getStep())
                            .isEqualTo(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);
                    assertThat(workflow.getContext()).doesNotContainKey("revalidateOnly");
                    assertThat(workflow.getContext()).containsKey("validation");
                });
    }

    @Test
    void missingLockedFormatBlocksBeforeAnyToolCall() {
        List<String> calls = new ArrayList<>();
        PushBackend backend = new PushBackend(false, null);
        McpToolContext tools = workflowTools(calls, backend, passValidation(), successDiagnosis("run-1"));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(executionContext(CONFIG, "", 0, List.of())));

        String prompt = invoke(store, tools, session);

        assertThat(prompt).contains("缺少锁定配置、format", "未调用任何写工具");
        assertThat(calls).isEmpty();
    }

    @Test
    void changedExistingConfigurationUsesUpdateAndRestartThenDiagnosesNewRunId() {
        List<String> calls = new ArrayList<>();
        PushBackend backend = new PushBackend(true, "old-config");
        McpToolContext tools = workflowTools(
                calls, backend, passValidation(), successDiagnosis("run-new"));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(executionContext(CONFIG, "toml", 0, List.of())));

        String prompt = invoke(store, tools, session);

        assertThat(prompt).contains("run-new", "push_task_update_and_restart");
        assertThat(calls).contains("push_task_update_and_restart", "push_task_get",
                        "push_task_diagnose")
                .doesNotContain("push_task_create_and_start");
        assertThat(backend.config).isEqualTo(CONFIG);
        assertThat(backend.runId).isEqualTo("run-new");
    }

    @Test
    void failedValidationNeverCallsCreateOrUpdate() {
        List<String> calls = new ArrayList<>();
        PushBackend backend = new PushBackend(false, null);
        String failed = """
                {"passed":false,"blocked":false,"format":"toml",
                 "candidateDigest":"%s","metaDigest":"%s",
                 "validationDigest":"validation-failed","diagnostics":[
                   {"code":"VECTOR_SCHEMA_ARRAY_AS_MAP","severity":"ERROR","source":"VECTOR",
                    "path":"$sinks","message":"sinks 必须是映射","repairHint":"改为映射"}],
                 "tests":{"present":false,"total":0,"passed":0,"failed":0,"cases":[]}}
                """.formatted(digest(CONFIG), digest(META_CONTENT));
        McpToolContext tools = workflowTools(
                calls, backend, failed, successDiagnosis("run-1"));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(executionContext(CONFIG, "toml", 0, List.of())));

        String prompt = invoke(store, tools, session);

        assertThat(prompt).contains(
                "配置门禁未通过", "VECTOR_SCHEMA_ARRAY_AS_MAP", "改为映射");
        assertThat(calls).doesNotContain(
                "push_task_create_and_start", "push_task_update_and_restart");
        assertThat(store.loadActive(session, DataPushServiceAgent.AGENT_TYPE))
                .get().extracting(AgentWorkflowState::getStep)
                .isEqualTo(AgentWorkflowStep.BLOCKED);
    }

    @Test
    void runtimeErrorAfterCreateCannotProduceSuccessRecord() {
        List<String> calls = new ArrayList<>();
        PushBackend backend = new PushBackend(false, null);
        McpToolContext tools = workflowTools(calls, backend, passValidation(), """
                {"verdict":"FAILED","runId":"run-bad","taskStatus":"failed",
                 "failureStage":"STARTUP_CONFIG","repairable":true,
                 "diagnostics":[{"code":"VECTOR_SCHEMA_INVALID","severity":"ERROR",
                   "source":"VECTUM","path":"$sinks.ch","message":"invalid sink schema",
                   "repairHint":"修正 sink 字段"}],"logExcerpt":"Configuration error"}
                """);
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(executionContext(CONFIG, "toml", 0, List.of())));

        String prompt = invoke(store, tools, session);

        assertThat(prompt)
                .doesNotContain("zenvis:vectum-task-record")
                .contains("当前轮次诊断", "run-bad", "第 1/5 轮");
        assertThat(store.loadActive(session, DataPushServiceAgent.AGENT_TYPE))
                .get().extracting(AgentWorkflowState::getStep)
                .isEqualTo(AgentWorkflowStep.PUSH_REPAIR);
    }

    @Test
    void runningWithErrorStatusCannotBeTreatedAsSuccess() {
        List<String> calls = new ArrayList<>();
        PushBackend backend = new PushBackend(false, null);
        McpToolContext tools = workflowTools(calls, backend, passValidation(), """
                {"verdict":"FAILED","runId":"run-error","taskStatus":"running[error]",
                 "failureStage":"PROCESS_ERROR","repairable":true,
                 "diagnostics":[{"code":"RUNTIME_PROCESS_FAILURE","severity":"ERROR",
                   "source":"PROCESS","path":"$process","message":"本轮日志包含错误",
                   "repairHint":"依据本轮日志修复"}],"logExcerpt":"ERROR failed"}
                """);
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(executionContext(CONFIG, "toml", 0, List.of())));

        String prompt = invoke(store, tools, session);

        assertThat(prompt)
                .doesNotContain("平台已完成锁定 PushTask 校验、执行与诊断")
                .contains("当前轮次诊断", "running[error]");
        assertThat(store.loadActive(session, DataPushServiceAgent.AGENT_TYPE))
                .get().extracting(AgentWorkflowState::getStep)
                .isEqualTo(AgentWorkflowStep.PUSH_REPAIR);
    }

    @Test
    void externalDependencyBlockDoesNotEnterRepairOrModifyConfiguration() {
        List<String> calls = new ArrayList<>();
        PushBackend backend = new PushBackend(true, CONFIG);
        McpToolContext tools = workflowTools(calls, backend, passValidation(), """
                {"verdict":"FAILED","runId":"run-ext","taskStatus":"failed",
                 "failureStage":"EXTERNAL_DEPENDENCY","repairable":false,
                 "diagnostics":[{"code":"EXTERNAL_AUTH","severity":"ERROR","source":"VECTUM",
                   "path":"$sink.auth","message":"authentication failed",
                   "repairHint":"修正凭据后重试"}],"logExcerpt":"Authentication failed"}
                """);
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(executionContext(CONFIG, "toml", 0, List.of())));

        String prompt = invoke(store, tools, session);

        assertThat(prompt).contains("需外部处理", "平台不会继续修改配置");
        assertThat(calls).doesNotContain(
                "push_task_create_and_start", "push_task_update_and_restart");
        assertThat(store.loadActive(session, DataPushServiceAgent.AGENT_TYPE))
                .get().extracting(AgentWorkflowState::getStep)
                .isEqualTo(AgentWorkflowStep.BLOCKED);
    }

    @Test
    void repeatedFailureFingerprintStopsFurtherRepair() {
        List<String> calls = new ArrayList<>();
        PushBackend backend = new PushBackend(true, CONFIG);
        McpToolContext tools = workflowTools(calls, backend, passValidation(), """
                {"verdict":"FAILED","runId":"run-bad","taskStatus":"failed",
                 "failureStage":"STARTUP_CONFIG","repairable":true,
                 "diagnostics":[{"code":"VECTOR_SCHEMA_INVALID","severity":"ERROR",
                   "source":"VECTUM","path":"$sinks.ch","message":"invalid sink schema",
                   "repairHint":"修正字段"}],"logExcerpt":"Configuration error"}
                """);
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(executionContext(
                CONFIG, "toml", 1, List.of("STARTUP_CONFIG|VECTOR_SCHEMA_INVALID"))));

        String prompt = invoke(store, tools, session);

        assertThat(prompt).contains("本轮失败与上一轮指纹相同");
        assertThat(calls.stream()
                .filter("push_task_update_and_restart"::equals)
                .count()).isEqualTo(1);
        assertThat(store.loadActive(session, DataPushServiceAgent.AGENT_TYPE))
                .get().extracting(AgentWorkflowState::getStep)
                .isEqualTo(AgentWorkflowStep.BLOCKED);
    }

    @Test
    void fifthRepairRoundCanUpdateOnceButSixthRoundIsRejected() {
        List<String> calls = new ArrayList<>();
        PushBackend backend = new PushBackend(true, CONFIG);
        McpToolContext tools = workflowTools(calls, backend, passValidation(), """
                {"verdict":"FAILED","runId":"run-6","taskStatus":"failed",
                 "failureStage":"STARTUP_VRL","repairable":true,
                 "diagnostics":[{"code":"VRL_COMPILE_ERROR","severity":"ERROR",
                   "source":"VECTUM","path":"$transforms.t","message":"VRL compile failed",
                   "repairHint":"修正 VRL"}],"logExcerpt":"VRL error"}
                """);
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(executionContext(CONFIG, "toml", 5, List.of())));

        String prompt = invoke(store, tools, session);

        assertThat(prompt).contains("修复达到上限");
        assertThat(calls.stream()
                .filter("push_task_update_and_restart"::equals)
                .count()).isEqualTo(1);
        assertThat(store.loadActive(session, DataPushServiceAgent.AGENT_TYPE))
                .get().extracting(AgentWorkflowState::getStep)
                .isEqualTo(AgentWorkflowStep.BLOCKED);
    }

    @Test
    void approvalPendingAndUnknownResultKeepExecutionStage() {
        assertPending(new McpApprovalPendingException("approval-1"),
                McpApprovalPendingException.class);
        assertPending(new McpToolResultUnknownException("request-1", "unknown"),
                McpToolResultUnknownException.class);
    }

    @Test
    void missingMetaStopsBeforeValidationOrWrites() {
        List<String> calls = new ArrayList<>();
        PushBackend backend = new PushBackend(false, null);
        McpToolContext tools = workflowToolsWithMeta(
                calls, backend, "[]", passValidation(), successDiagnosis("run-1"));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(executionContext(CONFIG, "toml", 0, List.of())));

        String prompt = invoke(store, tools, session);

        assertThat(prompt).contains("Meta", "前往元数据配置");
        assertThat(calls).containsExactly("config_tree");
    }

    @Test
    void planningReusesLockedMetaWithoutReadingItAgain() {
        List<String> calls = new ArrayList<>();
        PushBackend backend = new PushBackend(false, null);
        McpToolContext tools = workflowTools(
                calls, backend, passValidation(), successDiagnosis("run-1"));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(executionContext(CONFIG, "toml", 0, List.of()))
                .setStep(AgentWorkflowStep.PUSH_INPUT_COLLECTION));

        String prompt = invoke(store, tools, session);

        assertThat(prompt).contains("平台复用已锁定 Meta", MARK, digest(META_CONTENT))
                .contains("本轮不再重复调用 config_tree/config_read");
        assertThat(calls).isEmpty();
    }

    @Test
    void changedMetaRequiresNewConfirmationWithoutWrites() {
        List<String> calls = new ArrayList<>();
        PushBackend backend = new PushBackend(false, null);
        McpToolContext tools = workflowTools(calls, backend, passValidation(), successDiagnosis("run-1"));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        Map<String, Object> context = executionContext(CONFIG, "toml", 0, List.of());
        context.put("metaRef", Map.of(
                "fileName", META_FILE, "contentDigest", "stale-digest"));
        store.upsert(session, state(context));

        String prompt = invoke(store, tools, session);

        assertThat(prompt).contains("Meta 已变化", "重新确认");
        assertThat(calls).containsExactly("config_tree", "config_read");
        assertThat(store.loadActive(session, DataPushServiceAgent.AGENT_TYPE))
                .get().extracting(AgentWorkflowState::getStep)
                .isEqualTo(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);
    }

    private void assertPending(RuntimeException failure,
                               Class<? extends RuntimeException> expectedType) {
        List<String> calls = new ArrayList<>();
        PushBackend backend = new PushBackend(false, null);
        backend.createFailure = failure;
        McpToolContext tools = workflowTools(
                calls, backend, passValidation(), successDiagnosis("run-1"));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(executionContext(CONFIG, "toml", 0, List.of())));

        assertThatThrownBy(() -> invoke(store, tools, session))
                .isInstanceOf(expectedType);
        assertThat(store.loadActive(session, DataPushServiceAgent.AGENT_TYPE))
                .get().satisfies(workflow -> {
                    assertThat(workflow.getStep())
                            .isEqualTo(AgentWorkflowStep.PUSH_CONFIG_VALIDATION);
                    assertThat(workflow.getStatus()).isEqualTo("active");
                });
    }

    private String invoke(WorkflowStateStore store,
                          McpToolContext tools,
                          ChatSession session) {
        DihChatApplicationService service = service(store);
        return ReflectionTestUtils.invokeMethod(
                service, "bootstrapDataPushWorkflow", tools, "继续", session);
    }

    private McpToolContext workflowTools(List<String> calls,
                                         PushBackend backend,
                                         String validation,
                                         String diagnosis) {
        return workflowToolsWithMeta(
                calls, backend,
                "[{\"fileName\":\"user_event.json\"}]", validation, diagnosis);
    }

    private McpToolContext workflowToolsWithMeta(List<String> calls,
                                                 PushBackend backend,
                                                 String tree,
                                                 String validation,
                                                 String diagnosis) {
        return toolContext(
                callback("config_tree", calls, ignored -> tree),
                callback("config_read", calls, ignored -> META_CONTENT),
                callback("push_task_validate_config", calls, ignored -> validation),
                callback("push_task_list", calls, ignored -> backend.listJson()),
                callback("push_task_create_and_start", calls, ignored -> {
                    if (backend.createFailure != null) {
                        throw backend.createFailure;
                    }
                    return backend.create(ignored);
                }),
                callback("push_task_get", calls, ignored -> backend.taskJson()),
                callback("push_task_update_and_restart", calls,
                        ignored -> backend.update(ignored)),
                callback("push_task_diagnose", calls, ignored -> diagnosis));
    }

    private Map<String, Object> executionContext(String candidate,
                                                 String format,
                                                 int repairRound,
                                                 List<String> fingerprints) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("candidate", Map.of(
                "content", candidate,
                "format", format,
                "digest", digest(candidate)));
        context.put("sourceMark", MARK);
        context.put("metaRef", Map.of(
                "fileName", META_FILE,
                "contentDigest", digest(META_CONTENT)));
        context.put("request", Map.of("name", "探针推送", "description", "probe"));
        context.put("repairRound", repairRound);
        context.put("failureFingerprints", new ArrayList<>(fingerprints));
        return context;
    }

    private AgentWorkflowState state(Map<String, Object> context) {
        return new AgentWorkflowState()
                .setWorkflowId("workflow-1")
                .setWorkflowType("data_push")
                .setAgentType(DataPushServiceAgent.AGENT_TYPE)
                .setObjectType("push_task")
                .setStep(AgentWorkflowStep.PUSH_CONFIG_VALIDATION)
                .setStatus("active")
                .setContext(new LinkedHashMap<>(context));
    }

    private ChatSession session() {
        ChatSession session = new ChatSession()
                .setSessionId("chat-1")
                .setType(DataPushServiceAgent.AGENT_TYPE);
        session.setId(1);
        return session;
    }

    private DihChatApplicationService service(WorkflowStateStore store) {
        DihChatApplicationService service = new DihChatApplicationService(
                null, null, null, null, null, null, null, null,
                null,
                (ReportAgent) null,
                (MetadataConfigAgent) null,
                (DataPushServiceAgent) null,
                (VisualizationChartAgent) null,
                (VisualizationAppAgent) null,
                (VisualizationDashboardAgent) null,
                null, null, null,
                (AgentMcpToolService) null,
                (SkillService) null,
                null,
                (PushTaskService) null,
                (DashboardService) null,
                (MenuService) null);
        ReflectionTestUtils.setField(service, "workflowStateStore", store);
        return service;
    }

    private McpToolContext toolContext(ToolCallback... callbacks) {
        return new McpToolContext(
                ToolCallbackProvider.from(callbacks),
                "data push workflow tools");
    }

    private ToolCallback callback(String name,
                                  List<String> calls,
                                  Function<String, String> result) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description(name)
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                calls.add(name);
                return result.apply(toolInput);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return call(toolInput);
            }
        };
    }

    private String passValidation() {
        return """
                {"passed":true,"blocked":false,"format":"toml",
                 "candidateDigest":"%s","metaDigest":"%s",
                 "validationDigest":"validation-1","vectorVersion":"vector 0.55.0",
                 "diagnostics":[],
                 "tests":{"present":true,"total":2,"passed":2,"failed":0,"cases":[]}}
                """.formatted(digest(CONFIG), digest(META_CONTENT));
    }

    private String successDiagnosis(String runId) {
        return """
                {"verdict":"RUNNING","runId":"%s","taskStatus":"running",
                 "failureStage":"NONE","repairable":false,"diagnostics":[],
                 "logExcerpt":"started"}
                """.formatted(runId);
    }

    private String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private final class PushBackend {
        private boolean exists;
        private String config;
        private String runId = "run-1";
        private RuntimeException createFailure;

        private PushBackend(boolean exists, String config) {
            this.exists = exists;
            this.config = config;
        }

        private String listJson() {
            List<PushTaskSummaryVo> items = new ArrayList<>();
            if (exists) {
                PushTaskSummaryVo summary = new PushTaskSummaryVo();
                summary.setId(31);
                summary.setName("探针推送");
                summary.setSource("SYSTEM");
                summary.setMark(MARK);
                summary.setFormat("toml");
                summary.setStatus("running");
                summary.setCurrentRunId(runId);
                items.add(summary);
            }
            return toolJson(PushTaskPageVo.builder()
                    .items(items)
                    .total(items.size())
                    .offset(0)
                    .limit(100)
                    .hasMore(false)
                    .build());
        }

        private String create(String input) {
            this.exists = true;
            this.config = nestedValue(input, "request", "config");
            return operationJson("create_and_start");
        }

        private String update(String input) {
            this.config = nestedValue(input, "request", "config");
            this.runId = "run-new";
            return operationJson("update_and_restart");
        }

        private String taskJson() {
            PushTaskVo task = new PushTaskVo();
            task.setId(31);
            task.setName("探针推送");
            task.setDescription("probe");
            task.setSource("SYSTEM");
            task.setMark(MARK);
            task.setConfig(config);
            task.setFormat("toml");
            task.setStatus("running");
            task.setCurrentRunId(runId);
            return toolJson(task);
        }

        private String operationJson(String action) {
            return toolJson(PushTaskOperationVo.builder()
                    .action(action)
                    .taskId(31)
                    .previousStatus("stopped")
                    .status("running")
                    .changed(true)
                    .currentRunId(runId)
                    .build());
        }

        private String toolJson(Object value) {
            try {
                return JacksonConfig.MCP_TOOL_RESULT_MAPPER.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private String nestedValue(String input, String parent, String field) {
            try {
                return JacksonConfig.OBJECT_MAPPER.readTree(input).path(parent).path(field).asText();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
