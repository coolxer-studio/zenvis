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
