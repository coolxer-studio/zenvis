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
    void restoresWorkflowStateFromPendingCardWhenSessionStateWasLost() {
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("entity", "user_event");
        request.put("dimensions", List.of(new LinkedHashMap<>(Map.of(
                "field", "event_type",
                "kind", "CATEGORY"))));
        Map<String, Object> query = new LinkedHashMap<>(Map.of(
                "tool", "entity_aggregate",
                "request", request));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workflowId", "workflow-restored");
        metadata.put("workflowVersion", "1");
        metadata.put("step", "QUERY_PLAN_CONFIRMATION");
        metadata.put("stateRevision", 1);
        metadata.put("objectType", "visualization");
        metadata.put("source", "workflow");
        metadata.put("validationStatus", "success");
        metadata.put("allowedActions", List.of("approve", "reject", "revise"));
        metadata.put("planId", "plan-1");
        metadata.put("entity", "user_event");
        metadata.put("fields", List.of(Map.of("field", "event_type", "role", "dimension")));
        metadata.put("query", query);
        ChatMessagePart part = ChatMessagePart.builder()
                .id("part-restored")
                .type("confirm")
                .title("确认实体与字段查询方案")
                .status("pending")
                .metadata(metadata)
                .build();
        session.setMessages(JacksonUtil.toJson(List.of(message("message-restored", part))));
        assertThat(session.getExtraData()).isNull();
        WorkflowActionService service = new WorkflowActionService(chatService(session), store);

        var result = service.handle(request(
                "chat-1", "message-restored", "part-restored",
                "workflow-restored", "approve"), user());

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getState()).isEqualTo("DATA_QUERY");
        List<Message> persisted = JacksonUtil.toList(
                session.getMessages(), new com.fasterxml.jackson.core.type.TypeReference<List<Message>>() {});
        assertThat(persisted.get(0).getParts().get(0).getStatus()).isEqualTo("approved");
        AgentWorkflowState restored = store.load(session, "workflow-restored").orElseThrow();
        assertThat(restored.getStep()).isEqualTo(AgentWorkflowStep.DATA_QUERY);
        assertThat(restored.getContext())
                .containsEntry("planId", "plan-1");
        Map<String, Object> restoredQuery = (Map<String, Object>) restored.getContext().get("query");
        assertThat(dimensions(restoredQuery)).singleElement()
                .satisfies(dimension -> assertThat(dimension).containsEntry("kind", "FIELD"));
        Map<String, Object> persistedQuery = (Map<String, Object>) persisted.get(0)
                .getParts().get(0).getMetadata().get("query");
        assertThat(dimensions(persistedQuery)).singleElement()
                .satisfies(dimension -> assertThat(dimension).containsEntry("kind", "FIELD"));
    }

    @Test
    void retryBlockedQueryApprovesPlanAndNormalizesDimensionKind() {
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("entity", "user_event");
        request.put("dimensions", List.of(new LinkedHashMap<>(Map.of(
                "field", "event_type",
                "kind", "CATEGORY"))));
        Map<String, Object> query = new LinkedHashMap<>(Map.of(
                "tool", "entity_aggregate",
                "request", request));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workflowId", "workflow-retry");
        metadata.put("action", "visualization_chart.confirm_query_plan");
        metadata.put("step", "BLOCKED");
        metadata.put("stateRevision", 1);
        metadata.put("allowedActions", List.of("retry", "revise"));
        metadata.put("planId", "plan-1");
        metadata.put("query", query);
        ChatMessagePart part = ChatMessagePart.builder()
                .id("part-retry")
                .type("confirm")
                .status("blocked")
                .metadata(metadata)
                .build();
        session.setMessages(JacksonUtil.toJson(List.of(message("message-retry", part))));
        store.upsert(session, new AgentWorkflowState()
                .setWorkflowId("workflow-retry")
                .setAgentType("agent_visualization_chart")
                .setStep(AgentWorkflowStep.BLOCKED)
                .setStatus("blocked")
                .setMessageId("message-retry")
                .setPartId("part-retry")
                .setStateRevision(1)
                .setFailures(List.of(
                        Map.of("stage", "DATA_QUERY", "retryStep", "DATA_QUERY"),
                        Map.of("stage", "BLOCKED", "retryStep", "BLOCKED")))
                .setContext(new LinkedHashMap<>(Map.of("query", query, "planId", "plan-1"))));
        WorkflowActionService service = new WorkflowActionService(chatService(session), store);

        var result = service.handle(request(
                "chat-1", "message-retry", "part-retry",
                "workflow-retry", "retry"), user());

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getState()).isEqualTo("DATA_QUERY");
        assertThat(result.getPartStatus()).isEqualTo("approved");
        AgentWorkflowState restored = store.load(session, "workflow-retry").orElseThrow();
        assertThat(restored.getContext())
                .containsEntry("approvedPlanId", "plan-1");
        Map<String, Object> restoredQuery = (Map<String, Object>) restored.getContext().get("query");
        assertThat(dimensions(restoredQuery)).singleElement()
                .satisfies(dimension -> assertThat(dimension).containsEntry("kind", "FIELD"));
        List<Message> persisted = JacksonUtil.toList(
                session.getMessages(), new com.fasterxml.jackson.core.type.TypeReference<List<Message>>() {});
        assertThat(persisted.get(0).getParts().get(0).getStatus()).isEqualTo("approved");
        assertThat(persisted.get(0).getParts().get(0).getMetadata())
                .containsEntry("step", "DATA_QUERY")
                .containsEntry("action", "visualization_chart.confirm_query_plan")
                .doesNotContainKey("blockedAction");
        assertThat((List<?>) persisted.get(0).getParts().get(0)
                .getMetadata().get("allowedActions")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void retryBlockedArtifactReusesItsApprovedQueryWithoutMarkingArtifactSuccessful() {
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("entity", "user_event");
        request.put("dimensions", List.of(new LinkedHashMap<>(Map.of(
                "field", "event_type",
                "kind", "CATEGORY"))));
        Map<String, Object> query = new LinkedHashMap<>(Map.of(
                "tool", "entity_aggregate",
                "request", request));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workflowId", "workflow-artifact-retry");
        metadata.put("action", "visualization_chart.add_chart_library");
        metadata.put("blockedAction", "visualization_chart.add_chart_library");
        metadata.put("step", "BLOCKED");
        metadata.put("stateRevision", 3);
        metadata.put("allowedActions", List.of("retry", "revise"));
        metadata.put("validationStatus", "blocked");
        metadata.put("validationMessage", "查询方案未批准或本轮数据 MCP 未成功");
        metadata.put("artifactId", "artifact-1");
        metadata.put("planId", "plan-2");
        metadata.put("query", query);
        ChatMessagePart part = ChatMessagePart.builder()
                .id("part-artifact")
                .type("visualization-chart-preview")
                .status("blocked")
                .metadata(metadata)
                .build();
        session.setMessages(JacksonUtil.toJson(List.of(message("message-artifact", part))));
        store.upsert(session, new AgentWorkflowState()
                .setWorkflowId("workflow-artifact-retry")
                .setAgentType("agent_visualization_chart")
                .setStep(AgentWorkflowStep.BLOCKED)
                .setStatus("blocked")
                .setMessageId("message-artifact")
                .setPartId("part-artifact")
                .setStateRevision(3)
                .setFailures(List.of(
                        Map.of("stage", "DATA_QUERY", "retryStep", "DATA_QUERY"),
                        Map.of("stage", "BLOCKED", "retryStep", "BLOCKED")))
                .setContext(new LinkedHashMap<>(Map.of("query", query, "planId", "plan-2"))));
        WorkflowActionService service = new WorkflowActionService(chatService(session), store);

        var result = service.handle(request(
                "chat-1", "message-artifact", "part-artifact",
                "workflow-artifact-retry", "retry"), user());

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getState()).isEqualTo("DATA_QUERY");
        assertThat(result.getPartStatus()).isEqualTo("pending");
        AgentWorkflowState restored = store.load(session, "workflow-artifact-retry").orElseThrow();
        assertThat(restored.getContext())
                .containsEntry("approvedPlanId", "plan-2")
                .containsEntry("planId", "plan-2");
        Map<String, Object> approvedQuery = (Map<String, Object>) restored.getContext()
                .get("approvedQuery");
        assertThat(dimensions(approvedQuery)).singleElement()
                .satisfies(dimension -> assertThat(dimension).containsEntry("kind", "FIELD"));
        List<Message> persisted = JacksonUtil.toList(
                session.getMessages(), new com.fasterxml.jackson.core.type.TypeReference<List<Message>>() {});
        ChatMessagePart persistedPart = persisted.get(0).getParts().get(0);
        assertThat(persistedPart.getStatus()).isEqualTo("pending");
        assertThat(persistedPart.getMetadata())
                .containsEntry("step", "DATA_QUERY")
                .containsEntry("validationStatus", "pending")
                .doesNotContainKeys("blockedAction", "validationMessage");
        assertThat((List<?>) persistedPart.getMetadata().get("allowedActions")).isEmpty();
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
                .setAgentType("agent_visualization_chart")
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
                store.map(root.get("visualizationChart")).get("chartLibrary"));
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
                .setAgentType("agent_visualization_chart")
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
                .setType("agent_visualization_chart");
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> dimensions(Map<String, Object> query) {
        Map<String, Object> request = (Map<String, Object>) query.get("request");
        return (List<Map<String, Object>>) request.get("dimensions");
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
