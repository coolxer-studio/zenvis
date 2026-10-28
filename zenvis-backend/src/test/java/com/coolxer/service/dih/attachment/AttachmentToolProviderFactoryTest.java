package com.coolxer.service.dih.attachment;

import com.coolxer.configuration.ai.AttachmentProperties;
import com.coolxer.dao.mysql.entity.ChatAttachmentRecord;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.service.dih.ChatAttachmentService;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentToolProviderFactoryTest {

    @Test
    void exposesBoundChatAttachmentsEvenWithoutNewMessageAttachments() {
        ChatAttachmentService attachmentService = mock(ChatAttachmentService.class);
        AttachmentContentService contentService = mock(AttachmentContentService.class);
        AttachmentProperties properties = new AttachmentProperties();
        ChatAttachmentRecord record = record(1);
        when(attachmentService.accessibleRecords(eq(user(1)), eq("chat-1"), any()))
                .thenReturn(List.of(record));
        AttachmentToolProviderFactory factory = new AttachmentToolProviderFactory(
                attachmentService, contentService, properties);

        AttachmentToolProviderFactory.AttachmentToolBundle bundle =
                factory.create(user(1), "chat-1", null, List.of());

        assertThat(bundle.hasTools()).isTrue();
        assertThat(bundle.fileIds()).containsExactly("file-1");
        ToolCallback list = callback(bundle, "attachment_list");
        Map<String, Object> response = JacksonUtil.toMap(
                list.call("{}"), new TypeReference<Map<String, Object>>() { });
        assertThat(response).containsEntry("status", "ok");
        assertThat(JacksonUtil.toJson(response)).contains("sales.csv", "ready");
    }

    @Test
    void rejectsAttachmentsOwnedByAnotherUser() {
        ChatAttachmentService attachmentService = mock(ChatAttachmentService.class);
        when(attachmentService.accessibleRecords(any(), any(), any()))
                .thenReturn(List.of(record(2)));
        AttachmentToolProviderFactory factory = new AttachmentToolProviderFactory(
                attachmentService, mock(AttachmentContentService.class), new AttachmentProperties());

        assertThat(factory.create(user(1), "chat-1", null, List.of()).hasTools()).isFalse();
    }

    @Test
    void enforcesIndependentAttachmentToolCallBudget() {
        ChatAttachmentService attachmentService = mock(ChatAttachmentService.class);
        when(attachmentService.accessibleRecords(any(), any(), any()))
                .thenReturn(List.of(record(1)));
        AttachmentProperties properties = new AttachmentProperties();
        properties.getTool().setMaxCalls(6);
        AttachmentToolProviderFactory factory = new AttachmentToolProviderFactory(
                attachmentService, mock(AttachmentContentService.class), properties);
        ToolCallback list = callback(factory.create(user(1), "chat-1", null, List.of()),
                "attachment_list");

        for (int call = 0; call < 6; call++) {
            assertThat(list.call("{}")).contains("\"status\":\"ok\"");
        }
        assertThatThrownBy(() -> list.call("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("调用次数预算已用尽");
    }

    private ToolCallback callback(AttachmentToolProviderFactory.AttachmentToolBundle bundle,
                                  String name) {
        return java.util.Arrays.stream(bundle.provider().getToolCallbacks())
                .filter(callback -> name.equals(callback.getToolDefinition().name()))
                .findFirst().orElseThrow();
    }

    private ChatAttachmentRecord record(int userId) {
        return new ChatAttachmentRecord()
                .setFileId("file-1")
                .setUserId(userId)
                .setChatId("chat-1")
                .setFileName("sales.csv")
                .setFileSize(100L)
                .setKind("file")
                .setFormat("csv")
                .setContentType("text/csv")
                .setParseStatus(AttachmentStatus.READY);
    }

    private User user(int id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
