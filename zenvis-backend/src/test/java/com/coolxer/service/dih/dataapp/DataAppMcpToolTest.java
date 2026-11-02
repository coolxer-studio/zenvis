package com.coolxer.service.dih.dataapp;

import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.controller.dih.DataAppMcpTool;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.dataapp.DataAppDiagnostic;
import com.coolxer.model.dih.dataapp.DataAppValidationResult;
import com.coolxer.service.config.ConfigService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataAppMcpToolTest {

    private DataAppPackageService packageService;
    private DataAppPreviewRegistry previewRegistry;
    private ChatSessionService chatSessionService;
    private WorkflowStateStore workflowStateStore;
    private DataAppMcpTool tool;
    private ChatSession chatSession;
    private AgentWorkflowState workflow;

    @BeforeEach
    void setUp() {
        packageService = mock(DataAppPackageService.class);
        previewRegistry = mock(DataAppPreviewRegistry.class);
        chatSessionService = mock(ChatSessionService.class);
        workflowStateStore = mock(WorkflowStateStore.class);
        tool = new DataAppMcpTool(
                packageService,
                mock(DataAppPublishService.class),
                previewRegistry,
                chatSessionService,
                workflowStateStore,
                mock(ConfigService.class));

        chatSession = new ChatSession();
        chatSession.setId(4352);
        chatSession.setSessionId("chat-1");
        chatSession.setMessages("[]");
        chatSession.setExtraData("{}");
        workflow = new AgentWorkflowState()
                .setWorkflowId("workflow-1")
                .setWorkflowType("visualization_app")
                .setAgentType(VisualizationAgentTypes.APP)
                .setStep(AgentWorkflowStep.BLOCKED)
                .setStatus("blocked")
                .setContext(new LinkedHashMap<>(Map.of(
                        "validationStatus", "blocked",
                        "validation", invalidResult())));

        when(chatSessionService.getChatSessionBySessionId(eq("chat-1"), any(User.class)))
                .thenReturn(chatSession);
        when(workflowStateStore.loadActive(chatSession, VisualizationAgentTypes.APP))
                .thenReturn(Optional.of(workflow));
        when(previewRegistry.newPreviewToken()).thenReturn("preview-token");
        when(packageService.build(any(), any(), eq("workflow-1"), eq("chat-1"), anyString()))
                .thenReturn(invalidResult());
    }

    @Test
    void invalidSpecificationKeepsWorkflowRetryableAndCanBeValidatedAgain() {
        McpInvocationContext context = new McpInvocationContext(
                McpInvocationChannel.CHAT_AGENT,
                42,
                "chat-1",
                "turn-1",
                VisualizationAgentTypes.APP,
                null,
                null,
                null);
        String specJson = "{\"schema_version\":\"1.0\",\"app_id\":\"school-info-app\","
                + "\"name\":\"学校信息\",\"home_page_id\":\"overview\",\"pages\":[]}";

        McpInvocationContextHolder.callWith(context, () -> tool.validate(specJson, null));
        assertThat(workflow.getStep()).isEqualTo(AgentWorkflowStep.APP_VALIDATING);
        assertThat(workflow.getStatus()).isEqualTo("active");
        assertThat(workflow.getContext())
                .containsEntry("validationStatus", "invalid");

        DataAppValidationResult second = McpInvocationContextHolder.callWith(
                context, () -> tool.validate(specJson, null));

        assertThat(second.isPassed()).isFalse();
        assertThat(second.getStatus()).isEqualTo("invalid");
        verify(packageService, times(2))
                .build(any(), any(), eq("workflow-1"), eq("chat-1"), anyString());
        verify(workflowStateStore, times(2)).upsert(chatSession, workflow);
    }

    private DataAppValidationResult invalidResult() {
        DataAppValidationResult result = new DataAppValidationResult();
        result.setPassed(false);
        result.setStatus("invalid");
        result.setWorkflowId("workflow-1");
        result.setAppId("school-info-app");
        result.setDiagnostics(new ArrayList<>(List.of(new DataAppDiagnostic(
                "METRIC_CARD_COUNT",
                "pages[0].blocks[0].metric_cards",
                "指标卡数量必须为 1-8",
                "error"))));
        return result;
    }
}
