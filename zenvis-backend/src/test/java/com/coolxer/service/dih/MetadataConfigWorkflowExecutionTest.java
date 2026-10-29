package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.dih.agent.DataPushServiceAgent;
import com.coolxer.service.dih.agent.VisualizationAppAgent;
import com.coolxer.service.dih.agent.VisualizationChartAgent;
import com.coolxer.service.dih.agent.VisualizationDashboardAgent;
import com.coolxer.service.dih.agent.MetadataConfigAgent;
import com.coolxer.service.dih.agent.ReportAgent;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpApprovalPendingException;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.workflow.AgentWorkflowState;
import com.coolxer.service.dih.workflow.AgentWorkflowStep;
import com.coolxer.service.dih.workflow.WorkflowStateStore;
import com.coolxer.service.system.DashboardService;
import com.coolxer.service.system.MenuService;
import com.coolxer.service.system.PushTaskService;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataConfigWorkflowExecutionTest {

    @Test
    void approvedMetaCandidateUsesFixedWriteAndReadBackSequence() {
        List<String> calls = new ArrayList<>();
        List<String> inputs = new ArrayList<>();
        String candidate = """
                {"entity":[{"name":"probe_message"}],"attribute":[],"operator":[]}
                """;
        McpToolContext tools = toolContext(
                callback("config_tree", calls, inputs, ignored -> "[]"),
                callback("config_add", calls, inputs, ignored -> "true"),
                callback("config_apply", calls, inputs, ignored -> "true"),
                callback("config_read", calls, inputs,
                        ignored -> JacksonUtil.toJson(candidate)));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        session.setCreateBy(7);
        store.upsert(session, state(
                AgentWorkflowStep.META_PREWRITE_CHECK,
                Map.of(
                        "candidate", Map.of(
                                "kind", "meta",
                                "content", candidate,
                                "digest", "digest-1"),
                        "fileName", "probe_message.json")));
        DihChatApplicationService service = service(store);

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session);

        assertThat(prompt)
                .contains("平台已完成锁定 Meta 配置写入与读回")
                .contains("probe_message.json");
        assertThat(calls).containsExactly(
                "config_tree", "config_add", "config_apply", "config_read");
        Map<String, Object> addArguments = JacksonUtil.toObject(inputs.get(1), Map.class);
        assertThat((Map<String, Object>) addArguments.get("configDto"))
                .containsOnlyKeys("fileName")
                .containsEntry("fileName", "probe_message.json");
        assertThat(inputs.get(2)).doesNotContain("\"force\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void forcedMetaCandidatePassesForceToConfigApplyAuditContract() {
        List<String> calls = new ArrayList<>();
        List<String> inputs = new ArrayList<>();
        String candidate = """
                {"entity":[{"name":"probe_message"}],"attribute":[],"operator":[]}
                """;
        McpToolContext tools = toolContext(
                callback("config_tree", calls, inputs, ignored -> "[]"),
                callback("config_add", calls, inputs, ignored -> "true"),
                callback("config_apply", calls, inputs, ignored -> "true"),
                callback("config_read", calls, inputs,
                        ignored -> JacksonUtil.toJson(candidate)));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(
                AgentWorkflowStep.META_PREWRITE_CHECK,
                Map.of(
                        "candidate", Map.of(
                                "kind", "meta",
                                "content", candidate,
                                "digest", "digest-1"),
                        "fileName", "probe_message.json",
                        "forced", true)));

        String prompt = ReflectionTestUtils.invokeMethod(
                service(store),
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session);

        assertThat(prompt)
                .contains("用户已显式忽略静态校验并强制写入")
                .contains("validationStatus=forced");
        assertThat(calls).containsExactly(
                "config_tree", "config_add", "config_apply", "config_read");
        Map<String, Object> addArguments = JacksonUtil.toObject(
                inputs.get(1), Map.class);
        assertThat(addArguments).doesNotContainKey("force");
        Map<String, Object> applyArguments = JacksonUtil.toObject(
                inputs.get(2), Map.class);
        assertThat(applyArguments).containsEntry("force", true);
    }

    @Test
    void configApplyFailureKeepsWrittenCandidateRecordableAndRetriesApply() {
        List<String> calls = new ArrayList<>();
        String candidate = """
                {"entity":[{"name":"probe_message"}],"attribute":[],"operator":[]}
                """;
        McpToolContext tools = toolContext(
                callback("config_tree", calls, ignored -> "[]"),
                callback("config_add", calls, ignored -> "true"),
                callback("config_apply", calls, ignored -> "false"),
                callback("config_read", calls,
                        ignored -> JacksonUtil.toJson(candidate)));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(
                AgentWorkflowStep.META_PREWRITE_CHECK,
                Map.of(
                        "candidate", Map.of(
                                "kind", "meta",
                                "content", candidate,
                                "digest", "digest-1"),
                        "fileName", "probe_message.json")));

        String prompt = ReflectionTestUtils.invokeMethod(
                service(store),
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session);

        assertThat(prompt)
                .contains("平台 Meta 已写入，但应用或读回未完成")
                .contains("status=written")
                .contains("validationStatus 必须为 failed 或 blocked")
                .doesNotContain("平台已完成锁定 Meta 配置写入与读回");
        assertThat(calls).containsExactly(
                "config_tree", "config_add", "config_apply", "config_read");
        AgentWorkflowState workflow = store.loadActive(
                session, MetadataConfigAgent.AGENT_TYPE).orElseThrow();
        assertThat(workflow.getStep()).isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(workflow.getFailures()).last()
                .extracting(failure -> failure.get("retryStep"))
                .isEqualTo(AgentWorkflowStep.META_APPLY.name());
    }

    @Test
    void pendingWriteApprovalKeepsWorkflowAtWriteStep() {
        List<String> calls = new ArrayList<>();
        List<String> inputs = new ArrayList<>();
        String candidate = """
                {"entity":[{"name":"probe_message"}],"attribute":[],"operator":[]}
                """;
        McpToolContext tools = toolContext(
                callback("config_tree", calls, inputs, ignored -> "[]"),
                callback("config_add", calls, inputs, ignored -> {
                    throw new McpApprovalPendingException("approval-1");
                }),
                callback("config_apply", calls, inputs, ignored -> "true"),
                callback("config_read", calls, inputs, ignored -> ""));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        session.setCreateBy(7);
        store.upsert(session, state(
                AgentWorkflowStep.META_PREWRITE_CHECK,
                Map.of(
                        "candidate", Map.of(
                                "kind", "meta",
                                "content", candidate,
                                "digest", "digest-1"),
                        "fileName", "probe_message.json")));

        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        when(chatSessionService.updateWorkflowState(
                anyLong(), isNull(), anyString(), any())).thenReturn(true);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service(store, chatSessionService),
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session))
                .isInstanceOf(McpApprovalPendingException.class)
                .hasMessageContaining("approval-1");

        assertThat(calls).containsExactly("config_tree", "config_add");
        Map<String, Object> addArguments = JacksonUtil.toObject(
                inputs.get(1), Map.class);
        assertThat((Map<String, Object>) addArguments.get("configDto"))
                .containsOnlyKeys("fileName")
                .containsEntry("fileName", "probe_message.json");
        assertThat(addArguments).doesNotContainKey("force");
        AgentWorkflowState workflow = store.loadActive(
                session, MetadataConfigAgent.AGENT_TYPE).orElseThrow();
        assertThat(workflow.getStep()).isEqualTo(AgentWorkflowStep.META_PREWRITE_CHECK);
        assertThat(workflow.getStatus()).isEqualTo("active");
        assertThat(workflow.getContext()).containsEntry("metaAddRequested", true);
        org.mockito.ArgumentCaptor<String> extraData =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(chatSessionService, atLeastOnce()).updateWorkflowState(
                anyLong(), isNull(), extraData.capture(), any());
        assertThat(extraData.getValue()).contains("\"metaAddRequested\":true");
    }

    @Test
    @SuppressWarnings("unchecked")
    void configAddImmediatelyPersistsCreatedRecordBeforeApplyApproval() {
        List<String> calls = new ArrayList<>();
        String candidate = """
                {"entity":[{"name":"probe_message"}],"attribute":[],"operator":[]}
                """;
        McpToolContext tools = toolContext(
                callback("config_tree", calls, ignored -> "[]"),
                callback("config_add", calls, ignored -> "true"),
                callback("config_apply", calls, ignored -> {
                    throw new McpApprovalPendingException("approval-apply");
                }),
                callback("config_read", calls, ignored -> ""));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        session.setCreateBy(7);
        store.upsert(session, state(
                AgentWorkflowStep.META_PREWRITE_CHECK,
                Map.of(
                        "candidate", Map.of(
                                "kind", "meta",
                                "content", candidate,
                                "digest", "digest-1"),
                        "fileName", "probe_message.json",
                        "validationStatus", "success",
                        "validationDigest", "digest-1")));
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        when(chatSessionService.updateWorkflowState(
                anyLong(), isNull(), anyString(), any())).thenReturn(true);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service(store, chatSessionService),
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session))
                .isInstanceOf(McpApprovalPendingException.class)
                .hasMessageContaining("approval-apply");

        assertThat(calls).containsExactly("config_tree", "config_add", "config_apply");
        org.mockito.ArgumentCaptor<String> extraData =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(chatSessionService, atLeastOnce()).updateWorkflowState(
                anyLong(), isNull(), extraData.capture(), any());
        boolean foundCreated = extraData.getAllValues().stream()
                .map(value -> JacksonUtil.toObject(value, Map.class))
                .map(saved -> (Map<String, Object>) saved.get("metadataConfig"))
                .filter(section -> section != null)
                .map(section -> (List<Map<String, Object>>) section.get("records"))
                .filter(records -> records != null)
                .flatMap(List::stream)
                .anyMatch(record -> "probe_message.json".equals(record.get("fileName"))
                        && "created".equals(record.get("status"))
                        && "".equals(record.get("content")));
        assertThat(foundCreated).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void approvedConfigAddPersistsWrittenRecordBeforeApplyFailure() {
        List<String> calls = new ArrayList<>();
        int[] reads = {0};
        String candidate = """
                {"entity":[{"name":"probe_message"}],"attribute":[],"operator":[]}
                """;
        McpToolContext tools = toolContext(
                callback("config_tree", calls,
                        ignored -> "[{\"fileName\":\"probe_message.json\"}]"),
                callback("config_read", calls,
                        ignored -> reads[0]++ == 0 ? "" : JacksonUtil.toJson(candidate)),
                callback("config_add", calls, ignored -> "true"),
                callback("config_apply", calls, ignored -> "false"));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        session.setCreateBy(7);
        store.upsert(session, state(
                AgentWorkflowStep.META_PREWRITE_CHECK,
                Map.of(
                        "candidate", Map.of(
                                "kind", "meta",
                                "content", candidate,
                                "digest", "digest-1"),
                        "fileName", "probe_message.json",
                        "metaAddRequested", true,
                        "validationStatus", "success",
                        "validationDigest", "digest-1")));
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        when(chatSessionService.updateWorkflowState(
                anyLong(), isNull(), anyString(), any())).thenReturn(true);

        String prompt = ReflectionTestUtils.invokeMethod(
                service(store, chatSessionService),
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session);

        assertThat(prompt)
                .contains("平台 Meta 已写入，但应用或读回未完成")
                .contains("status=written");
        assertThat(calls).containsExactly(
                "config_tree", "config_read", "config_apply", "config_read");
        org.mockito.ArgumentCaptor<String> extraData =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(chatSessionService, atLeastOnce()).updateWorkflowState(
                anyLong(), isNull(), extraData.capture(), any());
        Map<String, Object> saved = JacksonUtil.toObject(
                extraData.getValue(), Map.class);
        Map<String, Object> metadataConfig = (Map<String, Object>)
                saved.get("metadataConfig");
        List<Map<String, Object>> records = (List<Map<String, Object>>)
                metadataConfig.get("records");
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record).containsEntry("fileName", "probe_message.json")
                    .containsEntry("status", "written")
                    .containsEntry("source", "workflow")
                    .containsEntry("validationStatus", "failed");
            assertThat((Map<String, Object>) record.get("config"))
                    .containsKey("entity");
        });
    }

    @Test
    void emptyFileFromApprovedConfigAddContinuesWithApply() {
        List<String> calls = new ArrayList<>();
        String candidate = """
                {"entity":[{"name":"probe_message"}],"attribute":[],"operator":[]}
                """;
        int[] reads = {0};
        McpToolContext tools = toolContext(
                callback("config_tree", calls,
                        ignored -> "[{\"fileName\":\"probe_message.json\"}]"),
                callback("config_read", calls,
                        ignored -> reads[0]++ == 0 ? "" : JacksonUtil.toJson(candidate)),
                callback("config_add", calls, ignored -> "true"),
                callback("config_apply", calls, ignored -> "true"));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(
                AgentWorkflowStep.META_PREWRITE_CHECK,
                Map.of(
                        "candidate", Map.of(
                                "kind", "meta",
                                "content", candidate,
                                "digest", "digest-1"),
                        "fileName", "probe_message.json")));

        String prompt = ReflectionTestUtils.invokeMethod(
                service(store),
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session);

        assertThat(prompt)
                .contains("平台已完成锁定 Meta 配置写入与读回")
                .doesNotContain("同名不同内容配置");
        assertThat(calls).containsExactly(
                "config_tree", "config_read", "config_apply", "config_read");
    }

    @Test
    void firstOrdinaryMetaTurnQueriesConfigTreeBeforeGeneratingDecision() {
        List<String> calls = new ArrayList<>();
        McpToolContext tools = toolContext(
                callback("config_tree", calls,
                        ignored -> "[{\"fileName\":\"existing.json\"}]"));
        DihChatApplicationService service = service(new WorkflowStateStore());

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapMetadataConfigWorkflow",
                tools,
                "请根据以下字段生成并添加元数据配置：event_id、event_type、server_time",
                session());

        assertThat(prompt)
                .contains("平台已执行 Meta 配置树 MCP")
                .contains("config_tree")
                .contains("existing.json")
                .doesNotContain("push_task_");
        assertThat(calls).containsExactly("config_tree");
    }

    @Test
    void schemaFailureDoesNotMisreportConfigTreeFailure() {
        List<String> calls = new ArrayList<>();
        McpToolContext tools = toolContext(
                callback("config_tree", calls,
                        ignored -> "[{\"fileName\":\"existing.json\"}]"),
                callback("config_schema", calls, ignored -> {
                    throw new IllegalStateException("schema unavailable");
                }));
        DihChatApplicationService service = service(new WorkflowStateStore());

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapMetadataConfigWorkflow",
                tools,
                "生成 probe_event 元数据",
                session());

        assertThat(prompt)
                .contains("平台已执行 Meta 配置树 MCP")
                .contains("existing.json")
                .contains("平台读取 Meta Schema 失败")
                .contains("不得臆造 Schema 字段")
                .contains("【Meta 精简契约】")
                .doesNotContain("平台 Meta 配置树 MCP 失败");
        assertThat(calls).containsExactly("config_tree", "config_schema");
    }

    @Test
    void metadataWorkflowUsesOnlyMetadataSteps() {
        List<String> calls = new ArrayList<>();
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(
                AgentWorkflowStep.META_PLAN_CONFIRMATION,
                Map.of(
                        "candidate", Map.of("content", "{}", "digest", "digest-1"),
                        "fileName", "probe_message.json")));

        String prompt = ReflectionTestUtils.invokeMethod(
                service(store),
                "bootstrapMetadataConfigWorkflow",
                toolContext(callback("config_tree", calls, ignored -> "[]")),
                "继续",
                session);

        assertThat(prompt).contains("不得调用任何 push_task_* 工具");
        assertThat(calls).isEmpty();
    }

    @Test
    void approvedRenameUsesLockedPathsAndVerifiesTreeAndContent() {
        List<String> calls = new ArrayList<>();
        List<String> inputs = new ArrayList<>();
        String content = "{\"entity\":[],\"attribute\":[],\"operator\":[]}";
        McpToolContext tools = toolContext(
                callback("config_tree", calls, inputs, ignored -> treeJson("new.json")),
                callback("config_rename", calls, inputs,
                        ignored -> JacksonUtil.toJson(Map.of(
                                "success", true,
                                "operation", "rename",
                                "kind", "file",
                                "sourcePath", "old.json",
                                "targetPath", "new.json",
                                "changed", true,
                                "applied", true,
                                "affectedPaths", List.of("old.json"),
                                "message", "配置已重命名"))),
                callback("config_read", calls, inputs,
                        ignored -> JacksonUtil.toJson(content)));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(
                AgentWorkflowStep.META_OPERATION_APPLY,
                Map.of("operation", operation(
                        "rename", "old.json", "new.json", false, content))));
        DihChatApplicationService service = service(store);

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session);

        assertThat(prompt)
                .contains("平台已完成元数据目录操作与读回核验")
                .contains("old.json", "new.json");
        assertThat(calls).containsExactly(
                "config_rename", "config_tree", "config_read");
        assertThat(inputs.get(0))
                .contains("\"fileName\":\"old.json\"")
                .contains("\"newFileName\":\"new.json\"");
        AgentWorkflowState updated = store.load(session, "workflow-1").orElseThrow();
        assertThat(updated.getStep()).isEqualTo(AgentWorkflowStep.META_VERIFIED);
        assertThat(updated.getContext()).containsEntry("metaOperationVerified", true);
    }

    @Test
    void recursiveDeleteUsesLockedRecursiveFlagAndRequiresSourceToDisappear() {
        List<String> calls = new ArrayList<>();
        List<String> inputs = new ArrayList<>();
        McpToolContext tools = toolContext(
                callback("config_tree", calls, inputs, ignored -> "[]"),
                callback("config_delete", calls, inputs,
                        ignored -> JacksonUtil.toJson(Map.of(
                                "success", true,
                                "operation", "delete",
                                "kind", "directory",
                                "sourcePath", "archive",
                                "changed", true,
                                "applied", true,
                                "affectedPaths", List.of("archive", "archive/a.json"),
                                "message", "配置目录已删除"))),
                callback("config_read", calls, inputs, ignored -> "\"\""));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        Map<String, Object> operation = operation(
                "delete", "archive", "", true, "");
        store.upsert(session, state(
                AgentWorkflowStep.META_OPERATION_APPLY,
                Map.of("operation", operation)));
        DihChatApplicationService service = service(store);

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session);

        assertThat(prompt).contains("平台已完成元数据目录操作与读回核验");
        assertThat(calls).containsExactly("config_delete", "config_tree");
        assertThat(inputs.get(0)).contains("\"recursive\":true");
    }

    @Test
    void strictReloadFailureKeepsChangedStateAndDoesNotClaimSuccess() {
        List<String> calls = new ArrayList<>();
        McpToolContext tools = toolContext(
                callback("config_tree", calls, ignored -> treeJson("old.json")),
                callback("config_rename", calls, ignored -> JacksonUtil.toJson(Map.of(
                        "success", false,
                        "operation", "rename",
                        "kind", "file",
                        "sourcePath", "old.json",
                        "targetPath", "new.json",
                        "changed", true,
                        "applied", false,
                        "affectedPaths", List.of("old.json"),
                        "message", "文件已重命名，但 Meta 严格加载失败"))),
                callback("config_read", calls, ignored -> "\"{}\""));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(
                AgentWorkflowStep.META_OPERATION_APPLY,
                Map.of("operation", operation(
                        "rename", "old.json", "new.json", false, "{}"))));
        DihChatApplicationService service = service(store);

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session);

        assertThat(prompt)
                .contains("平台元数据目录操作未完成")
                .contains("文件系统已发生变更")
                .doesNotContain("平台已完成元数据目录操作");
        assertThat(calls).containsExactly("config_rename");
        AgentWorkflowState updated = store.load(session, "workflow-1").orElseThrow();
        assertThat(updated.getStep()).isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(updated.getContext())
                .containsEntry("metaOperationChanged", true)
                .containsEntry("metaOperationApplied", false);
    }

    @Test
    void retryAfterReloadFailureOnlyReloadsMetaAndVerifiesExistingPaths() {
        List<String> calls = new ArrayList<>();
        String content = "{\"entity\":[],\"attribute\":[],\"operator\":[]}";
        McpToolContext tools = toolContext(
                callback("config_tree", calls, ignored -> treeJson("new.json")),
                callback("config_rename", calls, ignored -> {
                    throw new AssertionError("retry must not repeat filesystem rename");
                }),
                callback("config_read", calls, ignored -> JacksonUtil.toJson(content)));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        when(chatSessionService.updateWorkflowState(anyLong(), any(), anyString(), any()))
                .thenReturn(true);
        ConfigService configService = mock(ConfigService.class);
        DihChatApplicationService service = service(store, chatSessionService);
        ReflectionTestUtils.setField(service, "configService", configService);
        store.upsert(session, state(
                AgentWorkflowStep.META_OPERATION_APPLY,
                Map.of(
                        "operation", operation(
                                "rename", "old.json", "new.json", false, content),
                        "metaOperationChanged", true,
                        "metaOperationApplied", false)));

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session);

        assertThat(prompt).contains("平台已完成元数据目录操作与读回核验");
        assertThat(calls).containsExactly("config_tree", "config_read");
        verify(configService).applyConfig(eq("meta"), any());
        AgentWorkflowState updated = store.load(session, "workflow-1").orElseThrow();
        assertThat(updated.getStep()).isEqualTo(AgentWorkflowStep.META_VERIFIED);
    }

    @Test
    void retryAfterReloadFailureUpdatesFailedRenameRecordInPlace() {
        List<String> calls = new ArrayList<>();
        String content = "{\"entity\":[],\"attribute\":[],\"operator\":[]}";
        McpToolContext tools = toolContext(
                callback("config_tree", calls, ignored -> treeJson("new.json")),
                callback("config_rename", calls, ignored -> {
                    throw new AssertionError("retry must not repeat filesystem rename");
                }),
                callback("config_read", calls, ignored -> JacksonUtil.toJson(content)));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        session.setCreateBy(7);
        session.setExtraData(JacksonUtil.toJson(Map.of(
                "metadataConfig", Map.of("records", List.of(Map.of(
                        "id", "new.json",
                        "fileName", "new.json",
                        "previousFileName", "old.json",
                        "name", "new",
                        "status", "failed",
                        "validationStatus", "failed",
                        "operation", "rename",
                        "applied", false))))));
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        when(chatSessionService.updateWorkflowState(anyLong(), any(), anyString(), any()))
                .thenReturn(true);
        ConfigService configService = mock(ConfigService.class);
        DihChatApplicationService service = service(store, chatSessionService);
        ReflectionTestUtils.setField(service, "configService", configService);
        store.upsert(session, state(
                AgentWorkflowStep.META_OPERATION_APPLY,
                Map.of(
                        "operation", operation(
                                "rename", "old.json", "new.json", false, content),
                        "metaOperationChanged", true,
                        "metaOperationApplied", false)));

        ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session);

        List<Map<String, Object>> records = metadataRecords(session);
        assertThat(records).singleElement().satisfies(record -> assertThat(record)
                .containsEntry("id", "new.json")
                .containsEntry("fileName", "new.json")
                .containsEntry("previousFileName", "old.json")
                .containsEntry("operation", "rename")
                .containsEntry("status", "renamed")
                .containsEntry("applied", true));
    }

    @Test
    void renameRecordUpdatesExistingRowInPlace() {
        List<String> calls = new ArrayList<>();
        String content = "{\"entity\":[],\"attribute\":[],\"operator\":[]}";
        McpToolContext tools = toolContext(
                callback("config_tree", calls, ignored -> treeJson("new.json")),
                callback("config_rename", calls, ignored -> JacksonUtil.toJson(Map.of(
                        "success", true,
                        "operation", "rename",
                        "kind", "file",
                        "sourcePath", "old.json",
                        "targetPath", "new.json",
                        "changed", true,
                        "applied", true,
                        "affectedPaths", List.of("old.json"),
                        "message", "配置已重命名"))),
                callback("config_read", calls, ignored -> JacksonUtil.toJson(content)));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        session.setCreateBy(7);
        session.setExtraData(JacksonUtil.toJson(Map.of(
                "metadataConfig", Map.of("records", List.of(Map.of(
                        "id", "old.json",
                        "fileName", "old.json",
                        "name", "old",
                        "status", "applied",
                        "validationStatus", "success",
                        "entityName", "old",
                        "fieldCount", 0))))));
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        when(chatSessionService.updateWorkflowState(anyLong(), any(), anyString(), any()))
                .thenReturn(true);
        store.upsert(session, state(
                AgentWorkflowStep.META_OPERATION_APPLY,
                Map.of("operation", operation(
                        "rename", "old.json", "new.json", false, content))));
        DihChatApplicationService service = service(store, chatSessionService);

        ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session);

        List<Map<String, Object>> records = metadataRecords(session);
        assertThat(records).hasSize(1);
        assertThat(records.get(0))
                .containsEntry("id", "old.json")
                .containsEntry("fileName", "new.json")
                .containsEntry("previousFileName", "old.json")
                .containsEntry("operation", "rename")
                .containsEntry("status", "renamed")
                .containsEntry("applied", true);
    }

    @Test
    void deleteRecordKeepsTombstoneAndDisablesSuccessOpenState() {
        List<String> calls = new ArrayList<>();
        McpToolContext tools = toolContext(
                callback("config_tree", calls, ignored -> "[]"),
                callback("config_delete", calls, ignored -> JacksonUtil.toJson(Map.of(
                        "success", true,
                        "operation", "delete",
                        "kind", "file",
                        "sourcePath", "old.json",
                        "changed", true,
                        "applied", true,
                        "affectedPaths", List.of("old.json"),
                        "message", "配置文件已删除"))),
                callback("config_read", calls, ignored -> "\"\""));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        session.setCreateBy(7);
        session.setExtraData(JacksonUtil.toJson(Map.of(
                "metadataConfig", Map.of("records", List.of(Map.of(
                        "id", "old.json",
                        "fileName", "old.json",
                        "name", "old",
                        "status", "applied",
                        "validationStatus", "success",
                        "entityName", "old",
                        "fieldCount", 0))))));
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        when(chatSessionService.updateWorkflowState(anyLong(), any(), anyString(), any()))
                .thenReturn(true);
        store.upsert(session, state(
                AgentWorkflowStep.META_OPERATION_APPLY,
                Map.of("operation", operation(
                        "delete", "old.json", "", false, "{}"))));
        DihChatApplicationService service = service(store, chatSessionService);

        ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session);

        List<Map<String, Object>> records = metadataRecords(session);
        assertThat(records).hasSize(1);
        assertThat(records.get(0))
                .containsEntry("id", "old.json")
                .containsEntry("fileName", "old.json")
                .containsEntry("operation", "delete")
                .containsEntry("status", "deleted")
                .containsEntry("applied", true);
    }

    private String treeJson(String path) {
        return JacksonUtil.toJson(List.of(Map.of(
                "fileName", path,
                "relativePath", path,
                "isDir", false,
                "nodes", List.of())));
    }

    private Map<String, Object> operation(String operation,
                                          String source,
                                          String target,
                                          boolean recursive,
                                          String content) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("operation", operation);
        value.put("kind", "archive".equals(source) ? "directory" : "file");
        value.put("sourcePath", source);
        value.put("targetPath", target);
        value.put("recursive", recursive);
        value.put("affectedPaths", "archive".equals(source)
                ? List.of("archive", "archive/a.json")
                : List.of(source));
        Map<String, String> fileDigests = new LinkedHashMap<>();
        if (!"archive".equals(source)) {
            fileDigests.put(source, sha256(content));
        }
        value.put("fileDigests", fileDigests);
        value.put("nodeKinds", "archive".equals(source)
                ? Map.of("archive", "directory", "archive/a.json", "file")
                : Map.of(source, "file"));
        value.put("digest", "operation-digest");
        return value;
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> metadataRecords(ChatSession session) {
        Map<String, Object> extra = JacksonUtil.toObject(session.getExtraData(), Map.class);
        Map<String, Object> section = (Map<String, Object>) extra.get("metadataConfig");
        return (List<Map<String, Object>>) section.get("records");
    }

    private AgentWorkflowState state(
            AgentWorkflowStep step,
            Map<String, Object> context) {
        return new AgentWorkflowState()
                .setWorkflowId("workflow-1")
                .setWorkflowType("metadata_config")
                .setAgentType(MetadataConfigAgent.AGENT_TYPE)
                .setObjectType("meta_config")
                .setStep(step)
                .setStatus("active")
                .setContext(new LinkedHashMap<>(context));
    }

    private ChatSession session() {
        ChatSession session = new ChatSession()
                .setSessionId("chat-1")
                .setType(MetadataConfigAgent.AGENT_TYPE);
        session.setId(1);
        return session;
    }

    private DihChatApplicationService service(WorkflowStateStore store) {
        return service(store, null);
    }

    private DihChatApplicationService service(WorkflowStateStore store,
                                              ChatSessionService chatSessionService) {
        DihChatApplicationService service = new DihChatApplicationService(
                null, null, chatSessionService, null, null, null, null, null,
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
                (ConfigService) null,
                (PushTaskService) null,
                (DashboardService) null,
                (MenuService) null);
        ReflectionTestUtils.setField(service, "workflowStateStore", store);
        return service;
    }

    private McpToolContext toolContext(ToolCallback... callbacks) {
        return new McpToolContext(
                ToolCallbackProvider.from(callbacks),
                "metadata config workflow tools");
    }

    private ToolCallback callback(
            String name,
            List<String> calls,
            Function<String, String> result) {
        return callback(name, calls, null, result);
    }

    private ToolCallback callback(
            String name,
            List<String> calls,
            List<String> inputs,
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
                if (inputs != null) {
                    inputs.add(toolInput);
                }
                return result.apply(toolInput);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return call(toolInput);
            }
        };
    }
}
