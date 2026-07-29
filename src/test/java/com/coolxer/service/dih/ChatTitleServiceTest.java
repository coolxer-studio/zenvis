package com.coolxer.service.dih;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.concurrent.atomic.AtomicInteger;

import static com.coolxer.service.dih.ReportDemoResponseService.REPORT_DEMO_TITLE;
import static com.coolxer.service.dih.ReportDemoResponseService.REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT;
import static org.assertj.core.api.Assertions.assertThat;

class ChatTitleServiceTest {

    @Test
    void reportDemoPromptUsesLocalTitleWithoutCallingModel() {
        ThrowingChatModel chatModel = new ThrowingChatModel();
        ChatTitleService service = new ChatTitleService(chatModel);

        String title = service.generateTitle(REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT);

        assertThat(title).isEqualTo(REPORT_DEMO_TITLE);
        assertThat(chatModel.calls.get()).isZero();
    }

    private static class ThrowingChatModel implements ChatModel {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            throw new AssertionError("报表示例标题不应调用模型");
        }
    }
}
