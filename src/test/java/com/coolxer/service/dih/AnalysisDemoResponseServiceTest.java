package com.coolxer.service.dih;

import com.coolxer.commons.enums.MessageType;
import com.coolxer.model.dih.ChatMessagePart;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.coolxer.service.dih.AnalysisDemoResponseService.ANALYSIS_WEB_SHELL_EXAMPLE_PROMPT;
import static org.assertj.core.api.Assertions.assertThat;

class AnalysisDemoResponseServiceTest {

    private final AnalysisDemoResponseService service = new AnalysisDemoResponseService();
    private final ChatMessagePartParser parser = new ChatMessagePartParser();

    @Test
    void demoPromptReturnsOnlyLogAggregationStage() {
        assertThat(ANALYSIS_WEB_SHELL_EXAMPLE_PROMPT)
                .contains("演示告警日志信息")
                .contains("\"alarmId\": \"ALM-20260713-0007\"")
                .contains("\"rawLog\"");

        String response = responseOf(service.findResponse(null, "chat-1", ANALYSIS_WEB_SHELL_EXAMPLE_PROMPT, null));

        assertThat(response)
                .contains("zenvis:analysis-record")
                .contains("\"stage\": \"log_aggregation\"")
                .contains("\"logs\"")
                .contains("analysis_demo.confirm_log_aggregation")
                .doesNotContain("\"stage\": \"sandbox_analysis\"")
                .doesNotContain("\"stage\": \"report_output\"")
                .doesNotContain("zenvis:report-document-config");

        List<ChatMessagePart> parts = parser.parse(response, MessageType.TEXT);
        assertThat(parts).filteredOn(part -> "analysis-record".equals(part.getType())).hasSize(1);
        assertThat(parts).anySatisfy(part -> {
            assertThat(part.getType()).isEqualTo("confirm");
            assertThat(part.getMetadata()).containsEntry("action", "analysis_demo.confirm_log_aggregation");
        });
    }

    @Test
    void confirmLogAggregationReturnsSandboxStageOnly() {
        String response = responseOf(service.findResponse(null, "chat-1", "我已确认日志聚合结果，请进入沙箱研判阶段。", null));

        assertThat(response)
                .contains("\"stage\": \"sandbox_analysis\"")
                .contains("\"sandboxResult\"")
                .contains("analysis_demo.confirm_sandbox_result")
                .doesNotContain("\"stage\": \"report_output\"")
                .doesNotContain("zenvis:report-document-config");
    }

    @Test
    void reviseLogAggregationReturnsUpdatedLogAggregationStage() {
        String response = responseOf(service.findResponse(null, "chat-1", "我需要补充更多日志聚合数据。补充内容如下：补查文件变更。", null));

        assertThat(response)
                .contains("\"stage\": \"log_aggregation\"")
                .contains("log-007")
                .contains("文件变更")
                .contains("analysis_demo.confirm_log_aggregation")
                .doesNotContain("\"stage\": \"sandbox_analysis\"");
    }

    @Test
    void confirmSandboxReturnsConclusionReportAndDecision() {
        String response = responseOf(service.findResponse(null, "chat-1", "我已确认沙箱研判结果，结果满意，请进入分析结论阶段。", null));

        assertThat(response)
                .contains("\"stage\": \"report_output\"")
                .contains("\"timeline\"")
                .contains("zenvis:report-document-config")
                .contains("zenvis:analysis-decision");
    }

    @Test
    void reviseSandboxReturnsUpdatedSandboxStage() {
        String response = responseOf(service.findResponse(null, "chat-1", "我需要补充信息继续沙箱研判。补充研判重点如下：复核文件落地。", null));

        assertThat(response)
                .contains("\"stage\": \"sandbox_analysis\"")
                .contains("sandbox-demo-20260713-0007-rerun")
                .contains("\"confidence\": 0.94")
                .contains("analysis_demo.confirm_sandbox_result")
                .doesNotContain("\"stage\": \"report_output\"");
    }

    @Test
    void ordinaryPromptDoesNotMatchDemo() {
        assertThat(service.findResponse(null, "chat-1", "请分析一条普通告警", null)).isEmpty();
        assertThat(AnalysisDemoResponseService.isAnalysisDemoPrompt(ANALYSIS_WEB_SHELL_EXAMPLE_PROMPT + "请详细一些")).isFalse();
    }

    private String responseOf(Optional<reactor.core.publisher.Flux<String>> response) {
        assertThat(response).isPresent();
        return String.join("", response.get().collectList().block());
    }
}
