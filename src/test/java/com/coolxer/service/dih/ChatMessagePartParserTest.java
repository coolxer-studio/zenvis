package com.coolxer.service.dih;

import com.coolxer.commons.enums.MessageType;
import com.coolxer.model.dih.ChatMessagePart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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
