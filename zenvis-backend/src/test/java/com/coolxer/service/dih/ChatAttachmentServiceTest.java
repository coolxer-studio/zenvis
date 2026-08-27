package com.coolxer.service.dih;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.ChatAttachment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatAttachmentServiceTest {

    @TempDir
    Path workspace;

    @Test
    void resolveAttachmentRejectsNonUuidFileId() throws Exception {
        ChatAttachmentService service = newService();
        User user = newUser();
        ChatAttachment attachment = service.upload(new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        ), user);

        assertThat(service.resolveAttachmentFile(attachment.getFileId(), user)).isPresent();
        assertThat(service.resolveAttachmentFile("*", user)).isEmpty();
        assertThat(service.resolveAttachmentFile("../" + attachment.getFileId(), user)).isEmpty();
    }

    @Test
    void imageUploadRequiresSupportedMagicBytes() throws Exception {
        ChatAttachmentService service = newService();
        User user = newUser();

        ChatAttachment fakeSvg = service.upload(new MockMultipartFile(
                "file",
                "vector.svg",
                "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8)
        ), user);
        ChatAttachment png = service.upload(new MockMultipartFile(
                "file",
                "pixel.png",
                "image/png",
                new byte[]{
                        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                        0x00, 0x00, 0x00, 0x0D
                }
        ), user);

        assertThat(fakeSvg.getKind()).isNotEqualTo("image");
        assertThat(png.getKind()).isEqualTo("image");
        assertThat(png.getContentType()).isEqualTo("image/png");
    }

    @Test
    void attachmentBodyIsNotRetainedInCompactMemoryPrompt() {
        ChatAttachmentService service = newService();
        ChatAttachment attachment = ChatAttachment.builder()
                .fileId("3ab0c6d7-2907-45a3-bd8f-8f9754b386dd")
                .fileName("evidence.log")
                .fileSize(2048L)
                .build();
        String expanded = "请分析日志\n\n---\n以下是用户本轮消息上传的附件内容，请结合这些附件回答。"
                + "\n```log\nSECRET_RAW_ATTACHMENT_BODY\n```";

        String compact = service.compactPromptForMemory(expanded, List.of(attachment));

        assertThat(compact)
                .contains("请分析日志", "evidence.log", attachment.getFileId())
                .doesNotContain("SECRET_RAW_ATTACHMENT_BODY");
    }

    private ChatAttachmentService newService() {
        CustomWebConfig customWebConfig = new CustomWebConfig();
        ReflectionTestUtils.setField(customWebConfig, "sessionWorkspacePath", workspace.toString());
        return new ChatAttachmentService(customWebConfig);
    }

    private User newUser() {
        User user = new User();
        user.setId(123);
        return user;
    }
}
