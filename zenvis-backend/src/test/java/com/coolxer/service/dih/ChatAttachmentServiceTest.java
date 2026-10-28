package com.coolxer.service.dih;

import com.coolxer.configuration.minio.MinioProperties;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.core.vo.MinioObjectInfo;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.service.core.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatAttachmentServiceTest {

    private final MinioService minioService = mock(MinioService.class);
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private ChatAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new ChatAttachmentService(minioService, new MinioProperties());
        when(minioService.uploadStream(anyString(), any(InputStream.class), anyLong(), anyString(), eq("zenvis-chat")))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    objects.put(key, invocation.<InputStream>getArgument(1).readAllBytes());
                    return key;
                });
        when(minioService.listObjects(anyString(), eq("zenvis-chat")))
                .thenAnswer(invocation -> {
                    String prefix = invocation.getArgument(0);
                    return objects.keySet().stream()
                            .filter(key -> key.startsWith(prefix + "/"))
                            .toList();
                });
        when(minioService.getObjectInfo(anyString(), eq("zenvis-chat")))
                .thenAnswer(invocation -> {
                    byte[] bytes = objects.get(invocation.getArgument(0));
                    return new MinioObjectInfo(bytes.length, "text/plain", "etag", Instant.now());
                });
        when(minioService.downloadFile(anyString(), eq("zenvis-chat")))
                .thenAnswer(invocation -> new ByteArrayInputStream(objects.get(invocation.getArgument(0))));
    }

    @Test
    void uploadsToUserScopedObjectKeyAndResolvesByOwnership() throws Exception {
        User user = newUser();
        ChatAttachment attachment = service.upload(new MockMultipartFile(
                "file",
                "../note.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        ), user);

        assertThat(attachment.getFileName()).isEqualTo("note.txt");
        assertThat(objects).containsKey(
                "chat/123/" + attachment.getFileId() + "/note.txt");
        assertThat(service.resolveAttachmentFile(attachment.getFileId(), user)).isPresent();
        assertThat(service.resolveAttachmentFile("*", user)).isEmpty();
        assertThat(service.resolveAttachmentFile("../" + attachment.getFileId(), user)).isEmpty();
        User other = new User();
        other.setId(456);
        assertThat(service.resolveAttachmentFile(attachment.getFileId(), other)).isEmpty();
    }

    @Test
    void imageUploadRequiresSupportedMagicBytes() throws Exception {
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
        assertThat(fakeSvg.getContentType()).isEqualTo("application/octet-stream");
        assertThat(png.getKind()).isEqualTo("image");
        assertThat(png.getContentType()).isEqualTo("image/png");
        assertThat(png.getParseStatus()).isEqualTo("ready");
    }

    @Test
    void textUploadStartsPendingAndUnsupportedBinaryIsMarkedUnsupported() throws Exception {
        ChatAttachment text = service.upload(new MockMultipartFile(
                "file", "notes.md", "text/markdown", "# notes".getBytes(StandardCharsets.UTF_8)
        ), newUser());
        ChatAttachment binary = service.upload(new MockMultipartFile(
                "file", "archive.zip", "application/zip", new byte[]{'P', 'K', 3, 4}
        ), newUser());

        assertThat(text.getParseStatus()).isEqualTo("pending");
        assertThat(binary.getParseStatus()).isEqualTo("unsupported");
    }

    @Test
    void sendValidationRejectsTooManyAttachmentsAndNonTerminalStates() {
        ChatAttachment ready = ChatAttachment.builder()
                .fileId("3ab0c6d7-2907-45a3-bd8f-8f9754b386dd")
                .fileName("ready.txt")
                .parseStatus("ready")
                .build();
        ChatAttachment partial = ChatAttachment.builder()
                .fileId("4ab0c6d7-2907-45a3-bd8f-8f9754b386dd")
                .fileName("partial.pdf")
                .parseStatus("partial")
                .build();
        ChatAttachment pending = ChatAttachment.builder()
                .fileId("5ab0c6d7-2907-45a3-bd8f-8f9754b386dd")
                .fileName("pending.csv")
                .parseStatus("pending")
                .build();
        ChatAttachment failed = ChatAttachment.builder()
                .fileId("6ab0c6d7-2907-45a3-bd8f-8f9754b386dd")
                .fileName("failed.xlsx")
                .parseStatus("failed")
                .build();

        assertThatCode(() -> service.ensureSendable(List.of(ready, partial), newUser()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.ensureSendable(List.of(pending), newUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("尚未就绪");
        assertThatThrownBy(() -> service.ensureSendable(List.of(failed), newUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("尚未就绪");
        assertThatThrownBy(() -> service.ensureSendable(
                java.util.Collections.nCopies(11, ready), newUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最多支持 10 个附件");
    }

    @Test
    void fakeRasterImageIsStoredWithNonPreviewableContentType() throws Exception {
        ChatAttachment attachment = service.upload(new MockMultipartFile(
                "file",
                "not-really.png",
                "image/png",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)
        ), newUser());

        assertThat(attachment.getKind()).isEqualTo("file");
        assertThat(attachment.getContentType()).isEqualTo("application/octet-stream");
        assertThat(ChatAttachmentService.isPreviewableImageContentType(attachment.getContentType()))
                .isFalse();
        assertThat(ChatAttachmentService.isPreviewableImageContentType("image/svg+xml")).isFalse();
        assertThat(ChatAttachmentService.isPreviewableImageContentType("image/png; charset=binary")).isTrue();
    }

    @Test
    void attachmentBodyIsNotRetainedInCompactMemoryPrompt() {
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

    private User newUser() {
        User user = new User();
        user.setId(123);
        return user;
    }
}
