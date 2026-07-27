package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.Message;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 数据分析中性示例的内置三阶段响应，不调用模型或 MCP。
 */
@Slf4j
@Service
public class DataAnalysisDemoResponseService {

    public static final String DATA_ANALYSIS_DEMO_TITLE = "用户事件异常波动分析演示";
    public static final String DATA_ANALYSIS_EXAMPLE_PROMPT =
            "请分析用户事件近七天的上报量和失败率，识别异常波动及可能的关联因素，并形成包含分析目标、分析过程和分析结论的报告。";

    private static final int DEMO_STREAM_CHUNK_SIZE = 64;
    private static final Duration DEMO_STREAM_DELAY = Duration.ofMillis(8);

    public Optional<Flux<String>> findResponse(ChatSession chatSession, String chatId, String prompt, User user) {
        if (!StringUtils.hasText(prompt)) {
            return Optional.empty();
        }
        String normalizedPrompt = prompt.trim();
        if (isDataAnalysisDemoPrompt(normalizedPrompt)) {
            return Optional.of(streamResponse(buildDatasetResponse()));
        }
        if (!isDemoSession(chatSession)) {
            return Optional.empty();
        }
        if (isCancelPrompt(normalizedPrompt)) {
            return Optional.of(streamResponse(buildCancelledResponse()));
        }
        if (isRevisePrompt(normalizedPrompt)) {
            return Optional.of(streamResponse(buildFixedDemoNotice()));
        }
        if (isServiceResultConfirmation(normalizedPrompt)) {
            return Optional.of(streamResponse(buildReportResponse()));
        }
        if (isDatasetConfirmation(normalizedPrompt)) {
            return Optional.of(streamResponse(buildServiceAnalysisResponse()));
        }
        return Optional.of(streamResponse(buildFixedDemoNotice()));
    }

    public static boolean isDataAnalysisDemoPrompt(String prompt) {
        return StringUtils.hasText(prompt) && DATA_ANALYSIS_EXAMPLE_PROMPT.equals(prompt.trim());
    }

    private boolean isDatasetConfirmation(String prompt) {
        return prompt.contains("我已确认当前数据集")
                || prompt.contains("analysis.confirm_dataset");
    }

    private boolean isServiceResultConfirmation(String prompt) {
        return prompt.contains("我已确认分析服务结果")
                || prompt.contains("analysis.confirm_service_result");
    }

    private boolean isCancelPrompt(String prompt) {
        return prompt.contains("我已取消当前数据分析流程")
                || prompt.contains("不要进入下一阶段");
    }

    private boolean isRevisePrompt(String prompt) {
        return prompt.contains("我需要调整分析数据集")
                || prompt.contains("我需要补充或调整分析服务任务")
                || prompt.contains("已补充数据集调整要求")
                || prompt.contains("已补充分析服务调整要求");
    }

    private boolean isDemoSession(ChatSession chatSession) {
        if (chatSession == null) {
            return false;
        }
        if (DATA_ANALYSIS_DEMO_TITLE.equals(chatSession.getTitle())) {
            return true;
        }
        if (StringUtils.hasText(chatSession.getExtraData())
                && chatSession.getExtraData().contains("demo-analysis-")) {
            return true;
        }
        if (!StringUtils.hasText(chatSession.getMessages())) {
            return false;
        }
        try {
            List<Message> messages = JacksonUtil.toList(
                    chatSession.getMessages(),
                    new TypeReference<List<Message>>() {
                    }
            );
            return messages.stream()
                    .map(Message::getContent)
                    .filter(StringUtils::hasText)
                    .anyMatch(content -> isDataAnalysisDemoPrompt(content)
                            || content.contains("demo-analysis-"));
        } catch (Exception e) {
            log.warn("判断数据分析演示会话失败: {}", e.getMessage());
            return false;
        }
    }

    private Flux<String> streamResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return Flux.just("");
        }
        List<String> chunks = new ArrayList<>();
        int index = 0;
        while (index < response.length()) {
            int end = Math.min(response.length(), index + DEMO_STREAM_CHUNK_SIZE);
            chunks.add(response.substring(index, end));
            index = end;
        }
        return Flux.fromIterable(chunks).delayElements(DEMO_STREAM_DELAY);
    }

    private String buildDatasetResponse() {
        return """
                已加载内置用户事件演示数据，并完成近七天上报量与失败率数据集准备。本结果仅用于产品演示，不查询实际系统数据。

                ```zenvis:data-analysis-record
                {
                  "recordId": "demo-analysis-dataset-001",
                  "stage": "dataset_preparation",
                  "status": "completed",
                  "title": "演示数据集准备完成",
                  "analysisTarget": "分析用户事件近七天上报量和失败率，识别异常波动及关联因素",
                  "datasetSummary": "内置演示数据；按日期聚合上报量、失败量和失败率，共 7 条记录",
                  "datasetRecords": [
                    {"date":"2026-07-21","totalCount":1200,"failedCount":24,"failureRate":2.00},
                    {"date":"2026-07-22","totalCount":1280,"failedCount":26,"failureRate":2.03},
                    {"date":"2026-07-23","totalCount":1310,"failedCount":27,"failureRate":2.06},
                    {"date":"2026-07-24","totalCount":1295,"failedCount":25,"failureRate":1.93},
                    {"date":"2026-07-25","totalCount":1370,"failedCount":30,"failureRate":2.19},
                    {"date":"2026-07-26","totalCount":1510,"failedCount":95,"failureRate":6.29},
                    {"date":"2026-07-27","totalCount":1450,"failedCount":72,"failureRate":4.97}
                  ],
                  "toolNames": ["builtin_demo_dataset"]
                }
                ```

                ```zenvis:confirm
                {"title":"演示数据集已准备，是否继续分析","content":"确认后将加载演示统计分析结果。","action":"analysis.confirm_dataset","actions":["approved","revise","rejected"],"reviseLabel":"调整数据集"}
                ```
                """;
    }

    private String buildServiceAnalysisResponse() {
        return """
                已加载演示统计分析结果。

                ```zenvis:data-analysis-record
                {
                  "recordId": "demo-analysis-service-001",
                  "stage": "service_analysis",
                  "status": "completed",
                  "title": "内置分析结果已加载",
                  "serviceTaskId": "builtin-demo-analysis-001",
                  "analysisResult": {
                    "source": "builtin_demo",
                    "method": "移动基线与稳健 Z-Score 演示",
                    "baselineFailureRate": 2.04,
                    "anomalies": [
                      {"date":"2026-07-26","failureRate":6.29,"level":"high","deviationMultiple":3.08},
                      {"date":"2026-07-27","failureRate":4.97,"level":"medium","deviationMultiple":2.44}
                    ],
                    "relatedFactors": [
                      "上报量在 2026-07-26 同时升至七日峰值",
                      "失败率连续两日高于前五日基线"
                    ]
                  },
                  "toolNames": ["builtin_demo_statistics"]
                }
                ```

                ```zenvis:confirm
                {"title":"内置分析结果已加载，是否生成演示报告","content":"确认后将直接生成内置的分析目标、分析过程和分析结论。","action":"analysis.confirm_service_result","actions":["approved","revise","rejected"],"reviseLabel":"调整分析任务"}
                ```
                """;
    }

    private String buildReportResponse() {
        return """
                已根据内置数据集和统计结果生成演示报告。

                ```zenvis:data-analysis-record
                {
                  "recordId": "demo-analysis-report-001",
                  "stage": "report_output",
                  "status": "completed",
                  "title": "演示分析报告已生成",
                  "timeline": [
                    {"id":"analysis-target","title":"分析目标","content":"识别用户事件近七天上报量与失败率的异常波动，并观察相关变化。","type":"primary"},
                    {"id":"analysis-process","title":"分析过程","content":"使用内置七日聚合数据，以前五日失败率均值 2.04% 作为演示基线，对后续日期进行移动基线与稳健 Z-Score 比较。","type":"primary"},
                    {"id":"analysis-conclusion","title":"分析结论","content":"2026-07-26 失败率升至 6.29%，2026-07-27 仍为 4.97%，连续高于基线；同时上报量达到阶段峰值，演示结果将其标记为需要关注的异常波动。","type":"success"}
                  ]
                }
                ```

                ```zenvis:report-document-config
                # 用户事件近七天异常波动分析演示报告

                > 数据来源：ZenVis 内置演示数据

                ## 分析目标

                分析用户事件近七天上报量和失败率，识别异常波动及可能的关联因素。

                ## 分析过程

                演示数据按日期聚合上报量、失败量和失败率。以内置统计结果中的前五日平均失败率 2.04% 为基线，对后两日进行移动基线与稳健 Z-Score 比较。

                ## 分析结论

                2026-07-26 失败率升至 6.29%，2026-07-27 仍为 4.97%，形成连续两日异常。同时 2026-07-26 上报量达到七日峰值，演示结果认为失败率抬升与业务量增长存在时间上的同步关系，但不据此推断因果关系。
                ```
                """;
    }

    private String buildCancelledResponse() {
        return """
                已结束本次数据分析演示，不会进入下一阶段。

                ```zenvis:notice
                {"title":"数据分析演示已取消","content":"当前会话保留已生成的内置演示记录。","level":"info"}
                ```
                """;
    }

    private String buildFixedDemoNotice() {
        return """
                当前会话展示预设的数据分析演示流程。

                ```zenvis:notice
                {"title":"演示流程不支持自定义调整","content":"请使用确认卡继续演示流程；如需分析自定义数据，请新建数据分析会话。","level":"info"}
                ```
                """;
    }
}
