package com.coolxer.service.dih;

import com.coolxer.commons.enums.MessageType;
import com.coolxer.model.dih.ChatMessagePart;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static com.coolxer.service.dih.ReportDemoResponseService.REPORT_INCIDENT_REVIEW_EXAMPLE_PROMPT;
import static com.coolxer.service.dih.ReportDemoResponseService.REPORT_OPERATION_WEEKLY_EXAMPLE_PROMPT;
import static com.coolxer.service.dih.ReportDemoResponseService.REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT;
import static com.coolxer.service.dih.ReportDemoResponseService.REPORT_VISUALIZATION_ARCHIVE_EXAMPLE_PROMPT;
import static org.assertj.core.api.Assertions.assertThat;

class ReportDemoResponseServiceTest {

    private final ReportDemoResponseService service = new ReportDemoResponseService();
    private final ChatMessagePartParser parser = new ChatMessagePartParser();

    @Test
    void fixedExamplePromptsReturnReportDocumentResponse() {
        assertThat(List.of(
                REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT,
                REPORT_OPERATION_WEEKLY_EXAMPLE_PROMPT,
                REPORT_INCIDENT_REVIEW_EXAMPLE_PROMPT,
                REPORT_VISUALIZATION_ARCHIVE_EXAMPLE_PROMPT
        )).allSatisfy(prompt -> assertThat(service.findResponse(null, "chat-1", prompt, null)).isPresent());

        String response = responseOf(service.findResponse(null, "chat-1", REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT, null));

        assertThat(response)
                .contains("zenvis:report-document-config")
                .contains("# 用户事件数据分析报告")
                .contains("## 三、图表与素材占位")
                .contains("## 四、结论与建议");
    }

    @Test
    void nonReportDemoPromptDoesNotMatch() {
        assertThat(service.findResponse(null, "chat-1", "请帮我生成一份普通巡检报告。", null)).isEmpty();
        assertThat(service.findResponse(null, "chat-1", REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT + "请再详细一些。", null)).isEmpty();
    }

    @Test
    void demoResponseCanBeParsedAsReportDocument() {
        String response = responseOf(service.findResponse(null, "chat-1", REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT, null));

        List<ChatMessagePart> parts = parser.parse(response, MessageType.TEXT);

        assertThat(parts).anySatisfy(part -> {
            assertThat(part.getType()).isEqualTo("report-document");
            assertThat(part.getTitle()).isEqualTo("用户事件数据分析报告");
            assertThat(part.getLanguage()).isEqualTo("markdown");
            assertThat(part.getMetadata())
                    .containsEntry("configKind", "report-document")
                    .containsEntry("defaultFileName", "report.md");
            assertThat((List<?>) part.getMetadata().get("outline")).isNotEmpty();
        });
    }

    private String responseOf(Optional<Flux<String>> response) {
        assertThat(response).isPresent();
        return String.join("", response.get().collectList().block());
    }
}
