package com.coolxer.service.dih;

import com.coolxer.commons.enums.MessageType;
import com.coolxer.model.dih.ChatMessagePart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChatMessagePartParserTest {

    private final ChatMessagePartParser parser = new ChatMessagePartParser();

    @Test
    @DisplayName("纯文本应解析为 Markdown 片段")
    void parsePlainText() {
        List<ChatMessagePart> parts = parser.parse("你好\n这是普通回复", MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("markdown", parts.get(0).getType());
        assertEquals("你好\n这是普通回复", parts.get(0).getContent());
        assertNotNull(parts.get(0).getId());
    }

    @Test
    @DisplayName("标准代码围栏应解析为 code 片段")
    void parseCodeFence() {
        List<ChatMessagePart> parts = parser.parse("示例：\n```java\nSystem.out.println(\"hi\");\n```\n完成", MessageType.TEXT);

        assertEquals(3, parts.size());
        assertEquals("markdown", parts.get(0).getType());
        assertEquals("code", parts.get(1).getType());
        assertEquals("java", parts.get(1).getLanguage());
        assertEquals("System.out.println(\"hi\");", parts.get(1).getContent());
        assertEquals("markdown", parts.get(2).getType());
    }

    @Test
    @DisplayName("zenvis notice 围栏应解析为提示片段")
    void parseNoticeFence() {
        String content = """
                ```zenvis:notice
                {"title":"注意","content":"操作前请确认配置","level":"warning"}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("notice", parts.get(0).getType());
        assertEquals("注意", parts.get(0).getTitle());
        assertEquals("操作前请确认配置", parts.get(0).getContent());
        assertEquals("warning", parts.get(0).getLevel());
    }

    @Test
    @DisplayName("数据可视化图表预览围栏应解析为预览片段")
    void parseDataVisualizationChartPreviewFence() {
        String content = """
                ```zenvis:visualization-chart-preview
                {"title":"用户事件上报趋势图","content":"按小时聚合","chartType":"line","echartsOption":{"xAxis":{"type":"category"},"series":[]}}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("visualization-chart-preview", parts.get(0).getType());
        assertEquals("用户事件上报趋势图", parts.get(0).getTitle());
        assertEquals("按小时聚合", parts.get(0).getContent());
        assertEquals("line", parts.get(0).getMetadata().get("chartType"));
        assertNotNull(parts.get(0).getMetadata().get("echartsOption"));
    }

    @Test
    @DisplayName("数据可视化记录围栏应解析为对应记录片段")
    void parseDataVisualizationRecordFences() {
        String content = """
                ```zenvis:visualization-chart-record
                {"name":"登录趋势图","chartType":"line","status":"temporary"}
                ```
                ```zenvis:visualization-config-record
                {"name":"登录可视化页面","configType":"login-visualization","fileName":"index.json"}
                ```
                ```zenvis:dashboard-config-record
                {"name":"登录大屏","dashboardId":"12","code":"login-dashboard"}
                ```
                ```zenvis:menu-config-record
                {"name":"登录菜单","menuId":"34","params":"login-visualization"}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(4, parts.size());
        assertEquals("visualization-chart-record", parts.get(0).getType());
        assertEquals("登录趋势图", parts.get(0).getContent());
        assertEquals("line", parts.get(0).getMetadata().get("chartType"));
        assertEquals("visualization-config-record", parts.get(1).getType());
        assertEquals("login-visualization", parts.get(1).getMetadata().get("configType"));
        assertEquals("dashboard-config-record", parts.get(2).getType());
        assertEquals("12", parts.get(2).getMetadata().get("dashboardId"));
        assertEquals("menu-config-record", parts.get(3).getType());
        assertEquals("34", parts.get(3).getMetadata().get("menuId"));
    }

    @Test
    @DisplayName("低代码页面配置围栏应解析为配置片段")
    void parseLowCodePageConfigFence() {
        String content = """
                ```zenvis:low-code-page-config
                {"type":"page","title":"巡检总览","body":[]}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("config", parts.get(0).getType());
        assertEquals("低代码页面配置", parts.get(0).getTitle());
        assertEquals("json", parts.get(0).getLanguage());
        assertEquals("{\"type\":\"page\",\"title\":\"巡检总览\",\"body\":[]}", parts.get(0).getContent());
        assertEquals("low-code-page", parts.get(0).getMetadata().get("configKind"));
        assertEquals("<configIndex>_config/index.json", parts.get(0).getMetadata().get("defaultFileName"));
    }

    @Test
    @DisplayName("低代码应用配置围栏应解析为配置片段")
    void parseLowCodeAppConfigFence() {
        String content = """
                ```zenvis:low-code-app-config
                {"type":"app","brandName":"巡检应用","pages":[]}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("config", parts.get(0).getType());
        assertEquals("低代码应用配置", parts.get(0).getTitle());
        assertEquals("json", parts.get(0).getLanguage());
        assertEquals("low-code-app", parts.get(0).getMetadata().get("configKind"));
        assertEquals("<configIndex>_config/site.json", parts.get(0).getMetadata().get("defaultFileName"));
    }

    @Test
    @DisplayName("静态 HTML 配置围栏应解析为配置片段")
    void parseHtmlPageConfigFence() {
        String content = """
                ```zenvis:html-page-config
                <!DOCTYPE html>
                <html lang="zh-CN"><body>巡检看板</body></html>
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("config", parts.get(0).getType());
        assertEquals("静态 HTML 页面配置", parts.get(0).getTitle());
        assertEquals("html", parts.get(0).getLanguage());
        assertEquals("<!DOCTYPE html>\n<html lang=\"zh-CN\"><body>巡检看板</body></html>", parts.get(0).getContent());
        assertEquals("html-page", parts.get(0).getMetadata().get("configKind"));
        assertEquals("html-page_config/<slug>.html", parts.get(0).getMetadata().get("defaultFileName"));
    }

    @Test
    @DisplayName("持续分析任务配置围栏应解析为配置片段")
    void parseContinuousAnalysisTaskConfigFence() {
        String content = """
                ```zenvis:continuous-analysis-task-config
                {"matchRule":{},"pushTask":{},"analysisTask":{}}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("config", parts.get(0).getType());
        assertEquals("持续分析任务配置", parts.get(0).getTitle());
        assertEquals("json", parts.get(0).getLanguage());
        assertEquals("continuous-analysis-task", parts.get(0).getMetadata().get("configKind"));
        assertEquals("continuous-analysis-task.json", parts.get(0).getMetadata().get("defaultFileName"));
    }

    @Test
    @DisplayName("元数据配置围栏应解析为配置片段")
    void parseMetaConfigFence() {
        String content = """
                ```zenvis:meta-config
                {"entity":[],"attribute":[],"operator":[]}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("config", parts.get(0).getType());
        assertEquals("元数据配置", parts.get(0).getTitle());
        assertEquals("json", parts.get(0).getLanguage());
        assertEquals("meta-config", parts.get(0).getMetadata().get("configKind"));
        assertEquals("meta_config/<entity>.json", parts.get(0).getMetadata().get("defaultFileName"));
    }

    @Test
    @DisplayName("处置策略配置围栏应解析为配置片段")
    void parseDisposalStrategyConfigFence() {
        String content = """
                ```zenvis:disposal-strategy-config
                {"disposalObject":{},"disposalMethod":{}}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("config", parts.get(0).getType());
        assertEquals("处置策略配置", parts.get(0).getTitle());
        assertEquals("json", parts.get(0).getLanguage());
        assertEquals("disposal-strategy", parts.get(0).getMetadata().get("configKind"));
        assertEquals("analysis-disposal-strategy.json", parts.get(0).getMetadata().get("defaultFileName"));
    }

    @Test
    @DisplayName("采集策略配置围栏应解析为配置片段")
    void parseCollectionPolicyConfigFence() {
        String content = """
                ```zenvis:collection-policy-config
                {"runtimeConfig":{"process":["frpc"]}}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("config", parts.get(0).getType());
        assertEquals("采集策略配置", parts.get(0).getTitle());
        assertEquals("json", parts.get(0).getLanguage());
        assertEquals("collection-policy", parts.get(0).getMetadata().get("configKind"));
        assertEquals("checker_config/{host|android|ios|h5|wechat}.json", parts.get(0).getMetadata().get("defaultFileName"));
    }

    @Test
    @DisplayName("标记评分策略配置围栏应解析为配置片段")
    void parseTaggingPolicyConfigFence() {
        String content = """
                ```zenvis:tagging-policy-config
                [{"name":"高危评分","score_rules":[]}]
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("config", parts.get(0).getType());
        assertEquals("标记评分策略配置", parts.get(0).getTitle());
        assertEquals("json", parts.get(0).getLanguage());
        assertEquals("tagging-policy", parts.get(0).getMetadata().get("configKind"));
        assertEquals("rating_config/rating_rule.json", parts.get(0).getMetadata().get("defaultFileName"));
    }

    @Test
    @DisplayName("生产处置策略配置围栏应解析为配置片段")
    void parseDisposalPolicyConfigFence() {
        String content = """
                ```zenvis:disposal-policy-config
                [{"tag":"webshell","sourceRegex":".*","action":{"type":1,"title":"阻断","message":"阻断风险源"}}]
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("config", parts.get(0).getType());
        assertEquals("处置策略配置", parts.get(0).getTitle());
        assertEquals("json", parts.get(0).getLanguage());
        assertEquals("disposal-policy", parts.get(0).getMetadata().get("configKind"));
        assertEquals("punish_config/<stable-name>.json", parts.get(0).getMetadata().get("defaultFileName"));
    }

    @Test
    @DisplayName("报表文档围栏应解析为配置片段")
    void parseReportDocumentConfigFence() {
        String content = """
                ```zenvis:report-document-config
                # 巡检研判报告

                ## 摘要

                本次发现高危风险。
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("config", parts.get(0).getType());
        assertEquals("报表文档", parts.get(0).getTitle());
        assertEquals("markdown", parts.get(0).getLanguage());
        assertEquals("report-document", parts.get(0).getMetadata().get("configKind"));
        assertEquals("report.md", parts.get(0).getMetadata().get("defaultFileName"));
    }

    @Test
    @DisplayName("HTML 报表文档围栏应识别为 HTML")
    void parseHtmlReportDocumentConfigFence() {
        String content = """
                ```zenvis:report-document-config
                <!DOCTYPE html>
                <html lang="zh-CN"><body>报告</body></html>
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("config", parts.get(0).getType());
        assertEquals("报表文档", parts.get(0).getTitle());
        assertEquals("html", parts.get(0).getLanguage());
        assertEquals("report-document", parts.get(0).getMetadata().get("configKind"));
    }

    @Test
    @DisplayName("think 标签应解析为思考片段并从正文中剥离")
    void parseThinkingTag() {
        List<ChatMessagePart> parts = parser.parse("<think>先分析问题\n再给结论</think>\n最终回答", MessageType.TEXT);

        assertEquals(2, parts.size());
        assertEquals("thinking", parts.get(0).getType());
        assertEquals("思考过程", parts.get(0).getTitle());
        assertEquals("先分析问题\n再给结论", parts.get(0).getContent());
        assertEquals("completed", parts.get(0).getStatus());
        assertEquals("markdown", parts.get(1).getType());
        assertEquals("\n最终回答", parts.get(1).getContent());
    }

    @Test
    @DisplayName("zenvis confirm 围栏应解析为待确认片段")
    void parseConfirmFence() {
        String content = """
                ```zenvis:confirm
                {"title":"是否执行","content":"准备生成插件产物","action":"plugin.generate"}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("confirm", parts.get(0).getType());
        assertEquals("是否执行", parts.get(0).getTitle());
        assertEquals("准备生成插件产物", parts.get(0).getContent());
        assertEquals("pending", parts.get(0).getStatus());
        assertEquals("plugin.generate", parts.get(0).getMetadata().get("action"));
    }

    @Test
    @DisplayName("研判后续选择围栏应解析为待选择片段")
    void parseAnalysisDecisionFence() {
        String content = """
                ```zenvis:analysis-decision
                {"title":"研判完成，请选择后续处理","content":"可以执行处置、忽略告警，或补充研判重点继续分析。","actions":["dispose","ignore","continue"]}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("analysis-decision", parts.get(0).getType());
        assertEquals("研判完成，请选择后续处理", parts.get(0).getTitle());
        assertEquals("可以执行处置、忽略告警，或补充研判重点继续分析。", parts.get(0).getContent());
        assertEquals("pending", parts.get(0).getStatus());
        assertEquals(List.of("dispose", "ignore", "continue"), parts.get(0).getMetadata().get("actions"));
    }

    @Test
    @DisplayName("数据接入后续选择围栏应解析为待选择片段")
    void parseDataAccessDecisionFence() {
        String content = """
                ```zenvis:data-access-decision
                {"title":"元数据配置已生成，请选择后续处理","content":"可以添加配置到系统、放弃本次配置，或补充调整要求继续更新配置。","actions":["apply_config","abandon","revise"]}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("data-access-decision", parts.get(0).getType());
        assertEquals("元数据配置已生成，请选择后续处理", parts.get(0).getTitle());
        assertEquals("可以添加配置到系统、放弃本次配置，或补充调整要求继续更新配置。", parts.get(0).getContent());
        assertEquals("pending", parts.get(0).getStatus());
        assertEquals(List.of("apply_config", "abandon", "revise"), parts.get(0).getMetadata().get("actions"));
    }

    @Test
    @DisplayName("补充信息步骤围栏应解析为待提交片段")
    void parseInfoStepsFence() {
        String content = """
                ```zenvis:info-steps
                {"title":"需要补充信息","content":"请补充以下信息后继续。","submitLabel":"提交补充信息","steps":[{"id":"data_source","title":"数据源类型","description":"请选择或填写本次接入的数据来源。","required":true,"suggestions":[{"label":"Kafka","value":"Kafka"},{"label":"HTTP API","value":"HTTP API"},{"label":"文件日志","value":"file"}],"placeholder":"也可以输入其他数据源类型"}]}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("info-steps", parts.get(0).getType());
        assertEquals("需要补充信息", parts.get(0).getTitle());
        assertEquals("请补充以下信息后继续。", parts.get(0).getContent());
        assertEquals("pending", parts.get(0).getStatus());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) parts.get(0).getMetadata().get("steps");
        assertNotNull(steps);
        assertEquals(1, steps.size());
        assertEquals("data_source", steps.get(0).get("id"));
    }

    @Test
    @DisplayName("元数据配置记录围栏应解析为记录片段")
    void parseMetaConfigRecordFence() {
        String content = """
                ```zenvis:meta-config-record
                {"title":"元数据配置已记录","fileName":"ips.json","entityName":"ips","entityLabel":"IP 情报","tableName":"default.ips","status":"applied","config":{"entity":[{"name":"ips"}],"attribute":[{"name":"ip"}],"operator":[]}}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("metadata-config-record", parts.get(0).getType());
        assertEquals("元数据配置已记录", parts.get(0).getTitle());
        assertEquals("applied", parts.get(0).getMetadata().get("status"));
        assertEquals("ips.json", parts.get(0).getMetadata().get("fileName"));
        assertEquals("IP 情报", parts.get(0).getContent());
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) parts.get(0).getMetadata().get("config");
        assertNotNull(config);
    }

    @Test
    @DisplayName("Vectum 任务记录围栏应解析为记录片段")
    void parseVectumTaskRecordFence() {
        String content = """
                ```zenvis:vectum-task-record
                {"title":"数据推送服务已创建","taskId":"task-001","name":"IP 情报推送","description":"同步 IP 情报数据","status":"running","config":"sources:\\n  in:\\n    type: demo_logs"}
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("data-push-service-record", parts.get(0).getType());
        assertEquals("数据推送服务已创建", parts.get(0).getTitle());
        assertEquals("同步 IP 情报数据", parts.get(0).getContent());
        assertEquals("task-001", parts.get(0).getMetadata().get("taskId"));
        assertEquals("running", parts.get(0).getMetadata().get("status"));
    }

    @Test
    @DisplayName("非法 zenvis JSON 应回退为 Markdown")
    void invalidSpecialFenceFallsBackToMarkdown() {
        String content = """
                ```zenvis:confirm
                {"title":
                ```
                """;

        List<ChatMessagePart> parts = parser.parse(content, MessageType.TEXT);

        assertEquals(1, parts.size());
        assertEquals("markdown", parts.get(0).getType());
        assertEquals(content.stripTrailing(), parts.get(0).getContent());
    }
}
