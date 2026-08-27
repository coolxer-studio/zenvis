package com.coolxer.service.dih;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Generates compact chat titles for long first-turn prompts.
 */
@Slf4j
@Service
public class ChatTitleService {

    public static final int MAX_TITLE_LENGTH = 60;
    private static final int MAX_PROMPT_LENGTH = 4000;

    private final ChatClient chatClient;

    public ChatTitleService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    public String generateTitle(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return "新建会话";
        }
        if (DataAccessDemoResponseService.isUserEventDemoRequirementPrompt(userMessage)) {
            return DataAccessDemoResponseService.USER_EVENT_DEMO_TITLE;
        }
        if (DataVisualizationDemoResponseService
                .isUserEventVisualizationDemoPrompt(userMessage)) {
            return DataVisualizationDemoResponseService
                    .USER_EVENT_VISUALIZATION_DEMO_TITLE;
        }
        if (ReportDemoResponseService.isReportDemoPrompt(userMessage)) {
            return ReportDemoResponseService.REPORT_DEMO_TITLE;
        }
        try {
            String title = chatClient.prompt()
                    .options(OpenAiChatOptions.builder().temperature(0.2).build())
                    .system("""
                            你是会话标题生成器。请根据用户首条消息生成一个简洁中文标题。
                            要求：
                            1. 只输出标题本身，不要解释、编号、引号或 Markdown。
                            2. 标题不超过 20 个中文字符或 60 个字符。
                            3. 保留核心业务对象和任务意图。
                            """)
                    .user("用户首条消息：\n" + limitInput(userMessage))
                    .call()
                    .content();
            return normalizeTitle(title, userMessage);
        } catch (RuntimeException e) {
            log.warn("AI 会话标题生成失败，使用本地兜底标题。", e);
            return fallbackTitle(userMessage);
        }
    }

    private String limitInput(String input) {
        if (input.length() <= MAX_PROMPT_LENGTH) {
            return input;
        }
        return input.substring(0, MAX_PROMPT_LENGTH);
    }

    private String normalizeTitle(String title, String fallbackSource) {
        String normalized = StringUtils.trimWhitespace(title);
        if (normalized != null) {
            normalized = normalized
                    .replaceAll("^[#\\-\\d.、\\s]+", "")
                    .replaceAll("[\"'“”‘`]+", "")
                    .replaceAll("[\\r\\n]+", " ")
                    .trim();
        }
        if (!StringUtils.hasText(normalized)) {
            normalized = fallbackTitle(fallbackSource);
        }
        return truncate(normalized);
    }

    private String fallbackTitle(String userMessage) {
        String normalized = userMessage
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("[#>*_`|\\[\\]{}()]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!StringUtils.hasText(normalized)) {
            normalized = "新建会话";
        }
        return truncate(normalized);
    }

    private String truncate(String value) {
        if (value == null) {
            return "新建会话";
        }
        int length = value.codePointCount(0, value.length());
        if (length <= MAX_TITLE_LENGTH) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, MAX_TITLE_LENGTH));
    }
}
