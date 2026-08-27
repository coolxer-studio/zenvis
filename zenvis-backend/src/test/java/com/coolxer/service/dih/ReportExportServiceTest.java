package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.vo.ReportWorkspaceVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportExportServiceTest {

    private final ReportDocumentService reportDocumentService = mock(ReportDocumentService.class);
    private final User user = new User();

    private ReportExportService service;

    @BeforeEach
    void setUp() {
        service = new ReportExportService(reportDocumentService);
        user.setId(7);
        ReportWorkspaceVo workspace = new ReportWorkspaceVo(
                Map.of(
                        "title", "研发/周报",
                        "format", "markdown",
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
    }

    @Test
    void exportsMarkdownAsValidDocxPackage() {
        ReportExportService.ExportedReport exported = service.export(11L, "docx", user);

        assertThat(exported.fileName()).isEqualTo("研发-周报.docx");
        assertThat(exported.contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(exported.content()).hasSizeGreaterThan(1_000);
        assertThat(exported.content()[0]).isEqualTo((byte) 'P');
        assertThat(exported.content()[1]).isEqualTo((byte) 'K');
    }

    @Test
    void exportsMarkdownAsPdf() {
        ReportExportService.ExportedReport exported = service.export(11L, "pdf", user);

        assertThat(exported.fileName()).isEqualTo("研发-周报.pdf");
        assertThat(exported.contentType()).isEqualTo("application/pdf");
        assertThat(exported.content()).hasSizeGreaterThan(1_000);
        assertThat(new String(exported.content(), 0, 4, StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");
    }
}
