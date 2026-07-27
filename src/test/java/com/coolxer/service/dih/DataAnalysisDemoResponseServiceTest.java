package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataAnalysisDemoResponseServiceTest {

    private final DataAnalysisDemoResponseService service = new DataAnalysisDemoResponseService();

    @Test
    void examplePromptReturnsBuiltinDatasetAndConfirmation() {
        String response = responseOf(service.findResponse(
                null,
                "chat-1",
                DataAnalysisDemoResponseService.DATA_ANALYSIS_EXAMPLE_PROMPT,
                null
        ));

        assertThat(response)
                .contains("zenvis:data-analysis-record")
                .contains("\"stage\": \"dataset_preparation\"")
                .contains("\"action\":\"analysis.confirm_dataset\"")
                .contains("\"toolNames\": [\"builtin_demo_dataset\"]");
    }

    @Test
    void demoConfirmationsReturnBuiltinServiceResultAndReport() {
        ChatSession session = new ChatSession()
                .setTitle(DataAnalysisDemoResponseService.DATA_ANALYSIS_DEMO_TITLE);

        String serviceResult = responseOf(service.findResponse(
                session,
                "chat-1",
                "我已确认当前数据集。请继续分析。",
                null
        ));
        String report = responseOf(service.findResponse(
                session,
                "chat-1",
                "我已确认分析服务结果。请生成报告。",
                null
        ));

        assertThat(serviceResult)
                .contains("\"stage\": \"service_analysis\"")
                .contains("\"serviceTaskId\": \"builtin-demo-analysis-001\"")
                .contains("\"source\": \"builtin_demo\"")
                .contains("\"action\":\"analysis.confirm_service_result\"");
        assertThat(report)
                .contains("\"stage\": \"report_output\"")
                .contains("zenvis:report-document-config")
                .contains("分析目标")
                .contains("分析过程")
                .contains("分析结论");
    }

    @Test
    void nonExamplePromptIsNotIntercepted() {
        assertThat(service.findResponse(null, "chat-1", "分析我的订单数据", null)).isEmpty();
    }

    private String responseOf(java.util.Optional<reactor.core.publisher.Flux<String>> response) {
        assertThat(response).isPresent();
        return String.join("", response.orElseThrow().collectList().block());
    }
}
