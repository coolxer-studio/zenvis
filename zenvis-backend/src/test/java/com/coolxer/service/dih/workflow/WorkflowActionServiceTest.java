package com.coolxer.service.dih.workflow;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.dto.WorkflowActionDto;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowActionServiceTest {

    @Test
    void strictEntitySubmissionAcceptsOnlyCurrentMcpCandidate() {
        Fixture fixture = selectionFixture();
        WorkflowActionDto invalid = fixture.request("submit");
        invalid.setAnswers(List.of(Map.of(
                "id", "analysis_entity",
                "value", "invented_entity")));

        assertThatThrownBy(() -> fixture.service().handle(invalid, fixture.user()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("MCP 返回的候选项");

        WorkflowActionDto valid = fixture.request("submit");
        valid.setAnswers(List.of(Map.of(
                "id", "analysis_entity",
                "value", "probe_message")));
        var result = fixture.service().handle(valid, fixture.user());

        assertThat(result.getState()).isEqualTo("ATTRIBUTE_META");
        assertThat(result.getContinuation())
                .containsEntry("display", "已选择实体：probe_message");
    }

    @Test
    void stalePartCannotMutateLatestWorkflowCard() {
        Fixture fixture = selectionFixture();
        WorkflowActionDto stale = fixture.request("submit");
        stale.setPartId("old-part");
        stale.setAnswers(List.of(Map.of(
                "id", "analysis_entity",
                "value", "probe_message")));

        assertThatThrownBy(() -> fixture.service().handle(stale, fixture.user()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void addToLibraryCopiesVerifiedArtifactAndIsIdempotent() {
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workflowId", "workflow-chart");
        metadata.put("allowedActions", List.of("add_to_library", "retry"));
        metadata.put("validationStatus", "success");
        metadata.put("source", "workflow");
        metadata.put("artifactId", "artifact-1");
        metadata.put("planId", "plan-1");
        metadata.put("query", Map.of(
                "tool", "entity_aggregate",
                "request", Map.of("entity", "probe_message")));
        metadata.put("echartsOption", Map.of("series", List.of()));
        ChatMessagePart part = ChatMessagePart.builder()
                .id("part-chart")
                .type("visualization-chart-preview")
                .title("真实图表")
                .status("pending")
                .metadata(metadata)
                .build();
        session.setMessages(JacksonUtil.toJson(List.of(message("message-chart", part))));
        AgentWorkflowState state = new AgentWorkflowState()
                .setWorkflowId("workflow-chart")
                .setAgentType("agent_data_visualization")
                .setStep(AgentWorkflowStep.ARTIFACT_READY)
                .setStatus("active")
                .setMessageId("message-chart")
                .setPartId("part-chart")
                .setArtifactId("artifact-1");
        store.upsert(session, state);
        ChatSessionService chatSessionService = chatService(session);
        WorkflowActionService service = new WorkflowActionService(chatSessionService, store);
        WorkflowActionDto request = request(
                "chat-1", "message-chart", "part-chart",
                "workflow-chart", "add_to_library");

        service.handle(request, user());
        service.handle(request, user());

        Map<String, Object> root = store.parse(session.getExtraData());
        List<Map<String, Object>> records = listOfMaps(
                store.map(root.get("dataVisualization")).get("chartLibrary"));
        assertThat(records)
                .hasSize(1)
                .first()
                .satisfies(record -> {
                    assertThat(record)
                            .containsEntry("artifactId", "artifact-1")
                            .containsEntry("planId", "plan-1");
                });
    }

    private Fixture selectionFixture() {
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", "analysis_entity");
        step.put("title", "选择实体");
        step.put("required", true);
        step.put("strictOptions", true);
        step.put("suggestions", List.of(Map.of(
                "label", "探针消息（probe_message）",
                "value", "probe_message")));
        ChatMessagePart part = ChatMessagePart.builder()
                .id("part-1")
                .type("info-steps")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "workflowId", "workflow-1",
                        "allowedActions", List.of("submit"),
                        "steps", List.of(step))))
                .build();
        session.setMessages(JacksonUtil.toJson(List.of(message("message-1", part))));
        store.upsert(session, new AgentWorkflowState()
                .setWorkflowId("workflow-1")
                .setAgentType("agent_data_visualization")
                .setStep(AgentWorkflowStep.ENTITY_SELECTION)
                .setStatus("active")
                .setMessageId("message-1")
                .setPartId("part-1")
                .setContext(new LinkedHashMap<>(Map.of(
                        "strictOptions", Map.of(
                                "analysis_entity", List.of("probe_message"))))));
        return new Fixture(
                new WorkflowActionService(chatService(session), store),
                user());
    }

    private ChatSessionService chatService(ChatSession session) {
        ChatSessionService service = mock(ChatSessionService.class);
        when(service.getChatSessionBySessionId(anyString(), any())).thenReturn(session);
        when(service.updateWorkflowState(any(), anyString(), anyString(), any()))
                .thenReturn(true);
        return service;
    }

    private ChatSession session() {
        ChatSession session = new ChatSession()
                .setSessionId("chat-1")
                .setType("agent_data_visualization");
        session.setId(1);
        session.setCreateBy(9);
        return session;
    }

    private User user() {
        User user = new User();
        user.setId(9);
        return user;
    }

    private Message message(String id, ChatMessagePart part) {
        Message message = new Message();
        message.setId(id);
        message.setSender("ai");
        message.setContent("");
        message.setParts(List.of(part));
        return message;
    }

    private WorkflowActionDto request(
            String chatId,
            String messageId,
            String partId,
            String workflowId,
            String action) {
        WorkflowActionDto request = new WorkflowActionDto();
        request.setChatId(chatId);
        request.setMessageId(messageId);
        request.setPartId(partId);
        request.setWorkflowId(workflowId);
        request.setAction(action);
        return request;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        return value instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();
    }

    private record Fixture(WorkflowActionService service, User user) {
        private WorkflowActionDto request(String action) {
            WorkflowActionDto request = new WorkflowActionDto();
            request.setChatId("chat-1");
            request.setMessageId("message-1");
            request.setPartId("part-1");
            request.setWorkflowId("workflow-1");
            request.setAction(action);
            return request;
        }
    }
}
