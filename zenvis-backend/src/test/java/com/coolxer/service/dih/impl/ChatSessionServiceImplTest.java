package com.coolxer.service.dih.impl;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.ChatSessionRepository;
import com.coolxer.model.dih.Message;
import com.coolxer.service.dih.demo.AgentDemoStateStore;
import com.coolxer.service.dih.mcp.McpChatToolGrantService;
import com.coolxer.service.dih.workflow.WorkflowStateStore;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatSessionServiceImplTest {

    @Test
    void deletingChatRevokesPersistentMcpGrantsFirst() {
        ChatSessionRepository repository = mock(ChatSessionRepository.class);
        McpChatToolGrantService grantService = mock(McpChatToolGrantService.class);
        ChatSessionServiceImpl service = new ChatSessionServiceImpl();
        ReflectionTestUtils.setField(service, "chatSessionRepository", repository);
        ReflectionTestUtils.setField(service, "mcpChatToolGrantService", grantService);
        ChatSession session = new ChatSession()
                .setSessionId("chat-1");
        session.setCreateBy(42);
        when(repository.findById(7L)).thenReturn(Optional.of(session));
        User requester = new User();
        requester.setId(42);

        service.delete(7L, requester);

        var ordered = inOrder(grantService, repository);
        ordered.verify(grantService).revokeChat("chat-1", 42);
        ordered.verify(repository).deleteById(7L);
    }

    @Test
    void appendingMessageKeepsWorkflowStateComputedForCurrentTurn() {
        ChatSessionRepository repository = mock(ChatSessionRepository.class);
        ChatSessionServiceImpl service = new ChatSessionServiceImpl();
        ReflectionTestUtils.setField(service, "chatSessionRepository", repository);
        ReflectionTestUtils.setField(service, "mcpChatToolGrantService", mock(McpChatToolGrantService.class));
        ReflectionTestUtils.setField(service, "workflowStateStore", new WorkflowStateStore());
        ReflectionTestUtils.setField(service, "agentDemoStateStore", new AgentDemoStateStore());
        User requester = new User();
        requester.setId(42);
        ChatSession locked = new ChatSession()
                .setSessionId("chat-1")
                .setMessages("[]")
                .setExtraData("{\"report\":{\"currentDocument\":{\"documentId\":\"doc-1\"}}}");
        locked.setId(7);
        locked.setCreateBy(42);
        ChatSession current = new ChatSession()
                .setSessionId("chat-1")
                .setExtraData("{\"agentWorkflows\":{\"workflows\":{\"workflow-1\":{\"step\":\"QUERY_PLAN_CONFIRMATION\"}}},"
                        + "\"agentDemos\":{\"activeByAgent\":{\"agent_visualization_chart\":\"demo-1\"}}}");
        current.setId(7);
        current.setCreateBy(42);
        when(repository.findOwnedByIdForUpdate(7, 42)).thenReturn(Optional.of(locked));
        when(repository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatSession saved = service.appendMessage(current, new Message("ai", "answer"), requester);

        Map<String, Object> extraData = JacksonUtil.toMap(
                saved.getExtraData(), new TypeReference<Map<String, Object>>() {});
        assertThat(extraData)
                .containsKeys("report", "agentWorkflows", "agentDemos");
    }
}
