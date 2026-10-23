package com.coolxer.service.dih;

import com.coolxer.configuration.minio.MinioProperties;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.core.vo.MinioObjectInfo;
import com.coolxer.model.dih.vo.ReportWorkspaceVo;
import com.coolxer.service.core.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportExportServiceTest {

    private final ReportDocumentService reportDocumentService = mock(ReportDocumentService.class);
    private final MinioService minioService = mock(MinioService.class);
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final User user = new User();

    private ReportExportService service;

    @BeforeEach
    void setUp() {
        service = new ReportExportService(reportDocumentService, minioService, new MinioProperties());
        user.setId(7);
        ReportWorkspaceVo workspace = new ReportWorkspaceVo(
                Map.of(
                        "documentId", "document-1",
                        "revision", 3L,
                        "title", "研发/周报",
                        "format", "markdown",
                        "contentHash", "content-hash",
                        "content", """
                                # 研发周报

                                [项目主页](https://example.com)

                                | 指标 | 数值 |
                                | --- | ---: |
                                | 完成率 | 92% |

                                - 一级
                                  - 二级

                                ```java
                                System.out.println("ok");
                                ```
                                """),
                List.of(),
                List.of(),
                "{}");
        when(reportDocumentService.workspace(11L, user)).thenReturn(workspace);
        when(minioService.isFile(anyString(), eq("zenvis-reports")))
                .thenAnswer(invocation -> objects.containsKey(invocation.getArgument(0)));
        when(minioService.uploadStream(anyString(), any(InputStream.class), anyLong(), anyString(), eq("zenvis-reports")))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    InputStream input = invocation.getArgument(1);
                    objects.put(key, input.readAllBytes());
                    return key;
                });
        when(minioService.getObjectInfo(anyString(), eq("zenvis-reports")))
                .thenAnswer(invocation -> {
                    byte[] bytes = objects.get(invocation.getArgument(0));
                    return new MinioObjectInfo(bytes.length, "application/octet-stream", "etag", Instant.now());
                });
        when(minioService.downloadFile(anyString(), eq("zenvis-reports")))
                .thenAnswer(invocation -> new ByteArrayInputStream(objects.get(invocation.getArgument(0))));
    }

    @Test
    void exportsMarkdownAsValidDocxPackage() throws Exception {
        ReportExportService.ExportedReport exported = service.export(11L, "docx", user);

        assertThat(exported.fileName()).isEqualTo("研发-周报.docx");
        assertThat(exported.contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(exported.size()).isGreaterThan(1_000);
        byte[] content = exported.content().readAllBytes();
        assertThat(content).hasSize((int) exported.size());
        assertThat(content[0]).isEqualTo((byte) 'P');
        assertThat(content[1]).isEqualTo((byte) 'K');
        assertThat(objects).hasSize(1);
        assertThat(objects.keySet().iterator().next())
                .startsWith("documents/document-1/revisions/3/exports/v1/docx/")
                .endsWith(".docx");
    }

    @Test
    void exportsMarkdownAsPdfAndReusesExistingObject() throws Exception {
        ReportExportService.ExportedReport first = service.export(11L, "pdf", user);
        String objectKey = objects.keySet().iterator().next();
        byte[] firstBytes = first.content().readAllBytes();
        assertThat(first.fileName()).isEqualTo("研发-周报.pdf");
        assertThat(first.contentType()).isEqualTo("application/pdf");
        assertThat(first.size()).isGreaterThan(1_000);
        assertThat(new String(firstBytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        byte[] sentinel = "%PDF-cached".getBytes(StandardCharsets.US_ASCII);
        objects.put(objectKey, sentinel);
        ReportExportService.ExportedReport second = service.export(11L, "pdf", user);

        assertThat(second.content().readAllBytes()).isEqualTo(sentinel);
        assertThat(second.size()).isEqualTo(sentinel.length);
        assertThat(objects).hasSize(1);
    }
}
