package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class ReportDemoResponseService {

    public static final String REPORT_DEMO_TITLE = "报表生成演示";
    public static final String REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT =
            "请基于用户事件数据生成一份分析报告，包含摘要、数据概览、关键发现、图表占位、结论与建议。";
    public static final String REPORT_OPERATION_WEEKLY_EXAMPLE_PROMPT =
            "请基于用户事件数据生成一份运营周报，覆盖本周概览、趋势变化、异常事件、下周建议。";
    public static final String REPORT_INCIDENT_REVIEW_EXAMPLE_PROMPT =
            "请基于用户事件数据生成一份风险事件复盘报告，包含时间线、影响范围、原因分析、整改建议。";
    public static final String REPORT_VISUALIZATION_ARCHIVE_EXAMPLE_PROMPT =
            "请把用户事件数据可视化结论整理成一份可归档报告，包含图表说明、洞察摘要和后续行动。";

    private static final int DEMO_STREAM_CHUNK_SIZE = 20;
    private static final Duration DEMO_STREAM_DELAY = Duration.ofMillis(45);

    private static final String USER_EVENT_ANALYSIS_REPORT = """
            # 用户事件数据分析报告

            > 报告周期：近 24 小时
            > 数据来源：user-event / msg_user_event 演示数据

            ## 摘要

            本报告基于用户事件演示数据，对登录、点击、浏览、删除、修改等行为进行汇总分析。整体上报链路稳定，浏览与点击事件占主要比例，删除与修改事件占比较低但需要持续关注。

            ## 目录

            - 一、数据概览
            - 二、关键发现
            - 三、图表与素材占位
            - 四、结论与建议

            ## 一、数据概览

            - 覆盖实体：user-event / 调试信息。
            - 关键字段：event_type、server_time、reliability、tags、detail。
            - 主要事件：login、click、view、delete、modify、other。
            - 关注维度：事件趋势、事件类型分布、可信度评分、风险标签。

            ## 二、关键发现

            1. 浏览与点击事件构成主要访问行为，可作为活跃度判断的核心指标。
            2. 删除、修改事件虽然数量较少，但与“重要”“有风险”等标签同时出现时，需要进入复核队列。
            3. 可信度评分可用于过滤低质量事件，建议对低可信度数据建立单独的数据质量观察项。

            ## 三、图表与素材占位

            - 图表占位：用户事件上报趋势折线图。
            - 图表占位：事件类型分布柱状图。
            - 表格占位：低可信度事件明细 Top 10。
            - 附件占位：用户事件数据接入需求模板与当前会话分析结论。

            ## 四、结论与建议

            用户事件数据已具备支撑日常分析与可视化展示的基础条件。建议继续完善事件标签体系，针对删除、修改等高敏行为建立专项监控，并将趋势图与异常明细纳入固定周报。
            """;

    private static final String OPERATION_WEEKLY_REPORT = """
            # 用户事件运营周报

            > 报告周期：本周
            > 数据来源：user-event / msg_user_event 演示数据

            ## 摘要

            本周用户事件上报保持连续，核心行为以浏览、点击和登录为主。系统运行状态平稳，未发现大面积上报中断迹象；少量删除和修改事件建议纳入例行复核。

            ## 目录

            - 一、本周概览
            - 二、趋势变化
            - 三、异常事件
            - 四、下周建议

            ## 一、本周概览

            - 上报实体：user-event。
            - 运营关注点：活跃行为、变更行为、低可信度事件。
            - 主要输出物：周趋势图、事件分布图、异常事件清单。

            ## 二、趋势变化

            浏览和点击事件在工作时段更集中，登录事件相对平稳。删除、修改事件峰值需要结合 detail.path 与 tags 进一步确认业务背景。

            ## 三、异常事件

            - 删除事件：建议按用户、路径、可信度进行交叉复核。
            - 修改事件：建议关注携带“重要”“有风险”标签的记录。
            - 低可信度事件：建议进入数据质量观察列表。

            ## 四、下周建议

            1. 将用户事件趋势图固定到数据看板。
            2. 对删除、修改事件设置阈值提醒。
            3. 将低可信度事件明细作为周报固定附表。
            """;

    private static final String INCIDENT_REVIEW_REPORT = """
            # 用户事件风险复盘报告

            > 事件主题：高敏用户行为复核
            > 数据来源：user-event / msg_user_event 演示数据

            ## 摘要

            本次复盘聚焦删除、修改等高敏用户行为。当前演示数据中尚未形成明确风险闭环，但存在需要持续观察的高敏行为组合：修改事件、风险标签、低可信度评分同时出现时，应触发人工复核。

            ## 目录

            - 一、事件背景
            - 二、时间线
            - 三、影响范围
            - 四、原因分析
            - 五、整改建议

            ## 一、事件背景

            用户事件数据记录了登录、点击、浏览、删除、修改等行为，可用于追踪关键业务动作与潜在异常操作。

            ## 二、时间线

            - T0：用户事件数据持续写入 msg_user_event。
            - T1：出现删除、修改等高敏行为。
            - T2：通过 tags 与 reliability 字段识别需要复核的事件。
            - T3：形成复盘结论并沉淀为后续监控规则。

            ## 三、影响范围

            当前影响范围限定在演示实体 user-event。若后续接入真实业务数据，需要同步评估关联用户、接口路径、请求参数与操作结果。

            ## 四、原因分析

            初步判断风险主要来自高敏行为缺少固定阈值、标签体系仍需细化、低可信度事件尚未进入自动分流流程。

            ## 五、整改建议

            1. 对 delete、modify 事件建立专项监控。
            2. 将“重要”“有风险”等标签纳入风险加权。
            3. 对低可信度事件生成复核工单或日报附表。
            4. 将复盘报告归档，作为后续策略配置依据。
            """;

    private static final String VISUALIZATION_ARCHIVE_REPORT = """
            # 用户事件可视化结论归档报告

            > 归档对象：用户事件可视化分析
            > 数据来源：user-event / msg_user_event 演示数据

            ## 摘要

            本报告用于归档用户事件可视化结论，便于将临时图表、页面应用或数据看板中的发现沉淀为可审阅文档。当前结论显示，浏览、点击、登录是主要行为，删除与修改事件适合作为风险观察入口。

            ## 目录

            - 一、图表说明
            - 二、洞察摘要
            - 三、图表与素材占位
            - 四、后续行动

            ## 一、图表说明

            - 用户事件上报趋势图：展示不同时段各类事件的变化。
            - 事件类型分布图：展示 login、click、view、delete、modify 的占比。
            - 低可信度事件表：展示需要进一步复核的数据明细。

            ## 二、洞察摘要

            1. 高频事件主要集中在浏览与点击，体现用户活跃行为。
            2. 高敏事件数量较低，但适合作为风险监控指标。
            3. 可信度字段可辅助识别数据质量问题。

            ## 三、图表与素材占位

            - 图表占位：用户事件上报趋势。
            - 图表占位：事件类型分布。
            - 看板占位：用户事件数据看板链接。
            - 附件占位：可视化配置与分析结论。

            ## 四、后续行动

            建议将临时图表加入图表库，将用户事件看板纳入运营巡检页面，并按周归档图表结论，形成可追溯的分析材料。
            """;

    public Optional<Flux<String>> findResponse(ChatSession chatSession, String chatId, String prompt, User user) {
        if (!StringUtils.hasText(prompt)) {
            return Optional.empty();
        }
        String normalizedPrompt = prompt.trim();
        if (isUserEventAnalysisReportPrompt(normalizedPrompt)) {
            return Optional.of(streamResponse(buildReportResponse(USER_EVENT_ANALYSIS_REPORT)));
        }
        if (isOperationWeeklyReportPrompt(normalizedPrompt)) {
            return Optional.of(streamResponse(buildReportResponse(OPERATION_WEEKLY_REPORT)));
        }
        if (isIncidentReviewReportPrompt(normalizedPrompt)) {
            return Optional.of(streamResponse(buildReportResponse(INCIDENT_REVIEW_REPORT)));
        }
        if (isVisualizationArchiveReportPrompt(normalizedPrompt)) {
            return Optional.of(streamResponse(buildReportResponse(VISUALIZATION_ARCHIVE_REPORT)));
        }
        return Optional.empty();
    }

    public static boolean isReportDemoPrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return false;
        }
        String normalizedPrompt = prompt.trim();
        return isUserEventAnalysisReportPrompt(normalizedPrompt)
                || isOperationWeeklyReportPrompt(normalizedPrompt)
                || isIncidentReviewReportPrompt(normalizedPrompt)
                || isVisualizationArchiveReportPrompt(normalizedPrompt);
    }

    private static boolean isUserEventAnalysisReportPrompt(String prompt) {
        return REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT.equals(prompt);
    }

    private static boolean isOperationWeeklyReportPrompt(String prompt) {
        return REPORT_OPERATION_WEEKLY_EXAMPLE_PROMPT.equals(prompt);
    }

    private static boolean isIncidentReviewReportPrompt(String prompt) {
        return REPORT_INCIDENT_REVIEW_EXAMPLE_PROMPT.equals(prompt);
    }

    private static boolean isVisualizationArchiveReportPrompt(String prompt) {
        return REPORT_VISUALIZATION_ARCHIVE_EXAMPLE_PROMPT.equals(prompt);
    }

    private Flux<String> streamResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return Flux.just("");
        }
        return Flux.fromIterable(splitResponseChunks(response))
                .delayElements(DEMO_STREAM_DELAY);
    }

    private List<String> splitResponseChunks(String response) {
        List<String> chunks = new java.util.ArrayList<>();
        int index = 0;
        while (index < response.length()) {
            int limit = Math.min(response.length(), index + DEMO_STREAM_CHUNK_SIZE);
            chunks.add(response.substring(index, limit));
            index = limit;
        }
        return chunks;
    }

    private String buildReportResponse(String reportContent) {
        return """
                已根据示例数据生成一份可编辑报表草稿，右侧报表编辑器会自动同步。

                ```zenvis:report-document-config
                %s
                ```
                """.formatted(reportContent.trim());
    }
}
