package com.coolxer.service.dih.dashboard;

import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.controller.dih.DashboardMcpTool;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.DashboardRepository;
import com.coolxer.model.dih.dashboard.DashboardApplyResult;
import com.coolxer.model.dih.dashboard.DashboardDiagnostic;
import com.coolxer.model.dih.dashboard.DashboardManifest;
import com.coolxer.model.dih.dashboard.DashboardPackage;
import com.coolxer.model.dih.dashboard.DashboardSpec;
import com.coolxer.model.dih.dashboard.DashboardValidationResult;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.service.dih.agent.VisualizationAgentTypes;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpInvocationContextHolder;
import com.coolxer.service.dih.workflow.AgentWorkflowState;
import com.coolxer.service.dih.workflow.AgentWorkflowStep;
import com.coolxer.service.dih.workflow.WorkflowStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardMcpToolTest {

    private DashboardPackageService packageService;
    private DashboardPublishService publishService;
    private DashboardPreviewRegistry previewRegistry;
    private ChatSessionService chatSessionService;
    private WorkflowStateStore workflowStateStore;
    private DashboardMcpTool tool;
    private ChatSession chatSession;
    private AgentWorkflowState workflow;
    private McpInvocationContext context;

    @BeforeEach
    void setUp() {
        packageService = mock(DashboardPackageService.class);
        publishService = mock(DashboardPublishService.class);
        previewRegistry = mock(DashboardPreviewRegistry.class);
        chatSessionService = mock(ChatSessionService.class);
        workflowStateStore = mock(WorkflowStateStore.class);
        tool = new DashboardMcpTool(packageService, publishService, previewRegistry,
                chatSessionService, workflowStateStore, mock(DashboardRepository.class));

        chatSession = new ChatSession();
        chatSession.setId(4352);
        chatSession.setSessionId("chat-1");
        chatSession.setMessages("[]");
        chatSession.setExtraData("{}");
        workflow = new AgentWorkflowState()
                .setWorkflowId("workflow-1")
                .setWorkflowType("visualization_dashboard")
                .setAgentType(VisualizationAgentTypes.DASHBOARD)
                .setStep(AgentWorkflowStep.BLOCKED)
                .setStatus("blocked")
                .setContext(new LinkedHashMap<>(Map.of(
                        "candidateDigest", "old-candidate",
                        "validationDigest", "old-validation",
                        "preview", Map.of("htmlUrl", "/old-preview"))));
        context = new McpInvocationContext(
                McpInvocationChannel.CHAT_AGENT,
                42,
                "chat-1",
                "turn-1",
                VisualizationAgentTypes.DASHBOARD,
                null,
                null,
                null);

        when(chatSessionService.getChatSessionBySessionId(eq("chat-1"), any(User.class)))
                .thenReturn(chatSession);
        when(workflowStateStore.loadActive(chatSession, VisualizationAgentTypes.DASHBOARD))
                .thenReturn(Optional.of(workflow));
        when(previewRegistry.newPreviewToken()).thenReturn("preview-token");
    }

    @Test
    void invalidValidationClearsOldDigestsAndCanBeRetried() {
        when(packageService.build(any(), any(), eq("workflow-1"), eq("chat-1"), anyString()))
                .thenReturn(invalidResult());
        String specJson = "{\"dashboard_id\":\"user-event-dashboard\",\"name\":\"用户事件大屏\"}";

        DashboardValidationResult result = McpInvocationContextHolder.callWith(
                context, () -> tool.validate(specJson, null));

        assertThat(result.isPassed()).isFalse();
        assertThat(workflow.getStep()).isEqualTo(AgentWorkflowStep.DASHBOARD_VALIDATING);
        assertThat(workflow.getContext())
                .containsEntry("validationStatus", "invalid")
                .containsEntry("candidateDigest", "")
                .containsEntry("validationDigest", "")
                .containsEntry("preview", Map.of())
                .containsEntry("files", List.of());
        verify(previewRegistry).remove("workflow-1");
    }

    @Test
    void rejectsModelSuppliedHtmlAndMenuPayloads() {
        String specJson = "{\"dashboard_id\":\"user-event-dashboard\",\"name\":\"用户事件大屏\","
                + "\"html\":\"<script>alert(1)</script>\"}";

        assertThatThrownBy(() -> McpInvocationContextHolder.callWith(
                context, () -> tool.validate(specJson, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许包含字段");
    }

    @Test
    void verifiedApplyIsReturnedFromTheWorkflowCacheWithoutASecondWrite() {
        DashboardPreviewRegistry realRegistry = new DashboardPreviewRegistry();
        DashboardMcpTool idempotentTool = new DashboardMcpTool(packageService, publishService,
                realRegistry, chatSessionService, workflowStateStore, mock(DashboardRepository.class));
        workflow.setStep(AgentWorkflowStep.DASHBOARD_APPLYING).setStatus("active");
        DashboardValidationResult validation = validResult();
        DashboardPackage packageValue = packageValue();
        Map<String, String> previewFiles = new LinkedHashMap<>();
        previewFiles.put("user-event-dashboard.html", "<html>preview</html>");
        realRegistry.put("workflow-1", "chat-1", 42, "token-1",
                validation, packageValue, previewFiles);
        DashboardApplyResult verified = new DashboardApplyResult();
        verified.setStatus("verified");
        verified.setApplied(true);
        verified.setDashboardId("user-event-dashboard");
        verified.setDashboardVersion(1);
        verified.setMode("HTML_PAGE");
        verified.setFileDigest("file-digest");
        verified.setDashboardRecordId(21);
        verified.setDashboardType("HTML_PAGE");
        verified.setDashboardCode("user-event-dashboard");
        when(publishService.apply(any(), eq("candidate"), eq("validation")))
                .thenReturn(verified);

        DashboardApplyResult first = McpInvocationContextHolder.callWith(
                context, () -> idempotentTool.apply("workflow-1", "candidate", "validation"));
        DashboardApplyResult second = McpInvocationContextHolder.callWith(
                context, () -> idempotentTool.apply("workflow-1", "candidate", "validation"));

        assertThat(first.getStatus()).isEqualTo("verified");
        assertThat(second.getDashboardRecordId()).isEqualTo(21);
        assertThat(workflow.getStep()).isEqualTo(AgentWorkflowStep.VERIFIED);
        verify(publishService, times(1)).apply(any(), eq("candidate"), eq("validation"));
    }

    private DashboardValidationResult invalidResult() {
        DashboardValidationResult result = new DashboardValidationResult();
        result.setPassed(false);
        result.setStatus("invalid");
        result.setWorkflowId("workflow-1");
        result.setDashboardId("user-event-dashboard");
        result.setDiagnostics(new ArrayList<>(List.of(new DashboardDiagnostic(
                "ENTITY_UNKNOWN", "blocks[0].query.entity", "实体不存在", "error"))));
        return result;
    }

    private DashboardValidationResult validResult() {
        DashboardValidationResult result = new DashboardValidationResult();
        result.setPassed(true);
        result.setStatus("validated");
        result.setWorkflowId("workflow-1");
        result.setDashboardId("user-event-dashboard");
        result.setDashboardVersion(1);
        result.setMode("HTML_PAGE");
        result.setCandidateDigest("candidate");
        result.setValidationDigest("validation");
        result.setSpecDigest("spec-digest");
        result.setFileDigest("file-digest");
        result.setSpec(spec());
        return result;
    }

    private DashboardPackage packageValue() {
        DashboardSpec spec = spec();
        DashboardManifest manifest = new DashboardManifest();
        manifest.setDashboardId(spec.getDashboardId());
        manifest.setDashboardVersion(1);
        manifest.setMode(spec.getMode());
        manifest.setSpec(spec);
        return new DashboardPackage(spec, manifest,
                Map.of("user-event-dashboard.html", "<html>preview</html>"),
                "user-event-dashboard.manifest.json", null);
    }

    private DashboardSpec spec() {
        DashboardSpec spec = new DashboardSpec();
        spec.setDashboardId("user-event-dashboard");
        spec.setName("用户事件大屏");
        return spec;
    }
}
