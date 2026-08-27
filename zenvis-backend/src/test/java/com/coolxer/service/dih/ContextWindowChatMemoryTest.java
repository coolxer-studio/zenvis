package com.coolxer.service.dih;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextWindowChatMemoryTest {

    @Test
    void returnsCompressedSummaryAndRecentCompleteTurnsWithinBudget() {
        InMemoryChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        ChatMemory delegate = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(40)
                .build();
        DihTokenEstimator estimator = new DihTokenEstimator();
        ContextWindowChatMemory memory = new ContextWindowChatMemory(
                delegate, repository, estimator, 1_000, 220, 2);

        List<Message> messages = new ArrayList<>();
        for (int turn = 1; turn <= 6; turn++) {
            messages.add(new UserMessage("用户问题" + turn + "：" + "数".repeat(180)));
            messages.add(new AssistantMessage("助手回答" + turn + "：" + "据".repeat(180)));
        }
        memory.add("chat-1", messages);
        memory.setHistoryTokenBudget("chat-1", 900);

        List<Message> result = memory.get("chat-1");

        assertThat(result.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(result.get(0).getText()).contains("较早对话的压缩摘录");
        assertThat(result)
                .filteredOn(message -> !(message instanceof SystemMessage))
                .extracting(Message::getText)
                .contains(
                        "用户问题5：" + "数".repeat(180),
                        "助手回答5：" + "据".repeat(180),
                        "用户问题6：" + "数".repeat(180),
                        "助手回答6：" + "据".repeat(180)
                );
        assertThat(result.stream()
                .mapToInt(message -> estimator.estimate(message.getText()) + 6)
                .sum()).isLessThanOrEqualTo(900);
    }

    @Test
    void replacesExpandedAttachmentPromptInPersistentModelMemory() {
        InMemoryChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        ChatMemory delegate = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(40)
                .build();
        ContextWindowChatMemory memory = new ContextWindowChatMemory(
                delegate, repository, new DihTokenEstimator(), 2_000, 200, 4);
        String expanded = "分析附件\nRAW_ATTACHMENT_BODY";
        String compact = "分析附件\n[附件引用: evidence.log]";

        memory.add("chat-2", List.of(
                new UserMessage(expanded),
                new AssistantMessage("完成")
        ));
        memory.replaceLatestUserPrompt("chat-2", expanded, compact);

        assertThat(repository.findByConversationId("chat-2"))
                .extracting(Message::getText)
                .containsExactly(compact, "完成");
    }
}
