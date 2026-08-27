package com.coolxer.service.dih.impl;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.ChatSessionRepository;
import com.coolxer.service.dih.mcp.McpChatToolGrantService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

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
}
