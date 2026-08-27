package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.dao.mysql.entity.McpChatToolGrantId;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.coolxer.dao.mysql.repository.McpChatToolGrantRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpChatToolGrantServiceTest {

    private final McpChatToolGrantRepository repository = mock(McpChatToolGrantRepository.class);
    private final McpChatToolGrantService service = new McpChatToolGrantService(repository);

    @Test
    void grantLookupUsesExactChatUserAndTool() {
        McpChatToolGrantId id = new McpChatToolGrantId("chat-1", 42, "local::write_demo");
        when(repository.existsById(id)).thenReturn(true);

        assertThat(service.isGranted(context("chat-1", 42), "local::write_demo")).isTrue();
        assertThat(service.isGranted(context("chat-2", 42), "local::write_demo")).isFalse();
        assertThat(service.isGranted(context("chat-1", 43), "local::write_demo")).isFalse();
        assertThat(service.isGranted(context("chat-1", 42), "local::other")).isFalse();
    }

    @Test
    void nonChatInvocationCannotUseSessionGrant() {
        McpInvocationContext background = McpInvocationContext.background("analysis");

        assertThat(service.isGranted(background, "local::write_demo")).isFalse();
        verify(repository, never()).existsById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void grantAndChatDeletionUsePersistentRepository() {
        McpToolInvocation invocation = new McpToolInvocation()
                .setChatId("chat-1")
                .setRequesterUserId(42)
                .setToolKey("local::write_demo")
                .setRequestId("request-1");

        service.grant(invocation, 1);
        service.revokeChat("chat-1", 42);

        verify(repository).upsert("chat-1", 42, "local::write_demo", 1, "request-1");
        verify(repository).deleteByChat("chat-1", 42);
    }

    private McpInvocationContext context(String chatId, Integer userId) {
        return new McpInvocationContext(
                McpInvocationChannel.CHAT_AGENT,
                userId,
                chatId,
                "turn-1",
                "analysis",
                null,
                null,
                null
        );
    }
}
