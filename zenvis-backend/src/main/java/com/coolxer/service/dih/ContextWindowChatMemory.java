package com.coolxer.service.dih;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-time token window over the persistent Spring AI chat memory.
 *
 * <p>The repository remains the source of truth for the model memory. Only the
 * copy returned to a model call is compacted into a bounded summary plus recent
 * complete turns.</p>
 */
final class ContextWindowChatMemory implements ChatMemory {

    private static final int MESSAGE_OVERHEAD_TOKENS = 6;

    private final ChatMemory delegate;
    private final ChatMemoryRepository repository;
    private final DihTokenEstimator tokenEstimator;
    private final int maxHistoryTokens;
    private final int maxSummaryTokens;
    private final int recentTurns;
    private final Map<String, Integer> perConversationBudgets = new ConcurrentHashMap<>();
    private final Map<String, PromptReplacement> promptReplacements = new ConcurrentHashMap<>();

    ContextWindowChatMemory(ChatMemory delegate,
                            ChatMemoryRepository repository,
                            DihTokenEstimator tokenEstimator,
                            int maxHistoryTokens,
                            int maxSummaryTokens,
                            int recentTurns) {
        this.delegate = delegate;
        this.repository = repository;
        this.tokenEstimator = tokenEstimator;
        this.maxHistoryTokens = Math.max(maxHistoryTokens, 0);
        this.maxSummaryTokens = Math.max(maxSummaryTokens, 0);
        this.recentTurns = Math.max(recentTurns, 1);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        PromptReplacement replacement = promptReplacements.get(conversationId);
        if (replacement == null || messages == null || messages.isEmpty()) {
            delegate.add(conversationId, messages);
            return;
        }
        boolean replaced = false;
        List<Message> compacted = new ArrayList<>(messages.size());
        for (Message message : messages) {
            if (!replaced
                    && message instanceof UserMessage
                    && replacement.expandedPrompt().equals(message.getText())) {
                compacted.add(new UserMessage(replacement.compactPrompt()));
                replaced = true;
            } else {
                compacted.add(message);
            }
        }
        delegate.add(conversationId, compacted);
        if (replaced) {
            promptReplacements.remove(conversationId, replacement);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> messages = delegate.get(conversationId);
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        int budget = Math.min(
                maxHistoryTokens,
                Math.max(perConversationBudgets.getOrDefault(conversationId, maxHistoryTokens), 0)
        );
        if (budget <= 0) {
            return List.of();
        }

        List<Message> systemMessages = new ArrayList<>();
        List<List<Message>> turns = splitIntoTurns(messages, systemMessages);
        int firstRecentTurn = Math.max(turns.size() - recentTurns, 0);
        List<List<Message>> selectedReversed = new ArrayList<>();
        int selectedTokens = 0;

        for (int index = turns.size() - 1; index >= firstRecentTurn; index--) {
            List<Message> turn = turns.get(index);
            int turnTokens = estimateMessages(turn);
            if (turnTokens > budget - selectedTokens) {
                break;
            }
            selectedReversed.add(turn);
            selectedTokens += turnTokens;
        }
        Collections.reverse(selectedReversed);
        Set<List<Message>> selectedTurns =
                Collections.newSetFromMap(new IdentityHashMap<>());
        selectedTurns.addAll(selectedReversed);

        List<Message> dropped = new ArrayList<>();
        for (int index = 0; index < turns.size(); index++) {
            List<Message> turn = turns.get(index);
            if (index < firstRecentTurn || !selectedTurns.contains(turn)) {
                dropped.addAll(turn);
            }
        }

        List<Message> result = new ArrayList<>();
        int remaining = Math.max(budget - selectedTokens, 0);
        SystemMessage compactSummary = buildSummary(systemMessages, dropped, remaining);
        if (compactSummary != null) {
            result.add(compactSummary);
        } else {
            for (Message systemMessage : systemMessages) {
                int messageTokens = estimateMessage(systemMessage);
                if (messageTokens <= remaining) {
                    result.add(systemMessage);
                    remaining -= messageTokens;
                }
            }
        }
        selectedReversed.forEach(result::addAll);
        return result;
    }

    @Override
    public void clear(String conversationId) {
        perConversationBudgets.remove(conversationId);
        promptReplacements.remove(conversationId);
        delegate.clear(conversationId);
    }

    void setHistoryTokenBudget(String conversationId, int maxTokens) {
        if (StringUtils.hasText(conversationId)) {
            perConversationBudgets.put(conversationId, Math.max(maxTokens, 0));
        }
    }

    void clearHistoryTokenBudget(String conversationId) {
        if (StringUtils.hasText(conversationId)) {
            perConversationBudgets.remove(conversationId);
        }
    }

    void registerPromptReplacement(String conversationId,
                                   String expandedPrompt,
                                   String compactPrompt) {
        if (StringUtils.hasText(conversationId)
                && StringUtils.hasText(expandedPrompt)
                && !expandedPrompt.equals(compactPrompt)) {
            promptReplacements.put(
                    conversationId,
                    new PromptReplacement(expandedPrompt, compactPrompt)
            );
        }
    }

    void clearPromptReplacement(String conversationId) {
        if (StringUtils.hasText(conversationId)) {
            promptReplacements.remove(conversationId);
        }
    }

    /**
     * Replace the latest expanded attachment prompt after a model call so future
     * turns retain only the user text and stable attachment references.
     */
    void replaceLatestUserPrompt(String conversationId, String expandedPrompt, String compactPrompt) {
        if (!StringUtils.hasText(conversationId)
                || !StringUtils.hasText(expandedPrompt)
                || expandedPrompt.equals(compactPrompt)) {
            return;
        }
        synchronized (repository) {
            List<Message> persisted = repository.findByConversationId(conversationId);
            if (persisted == null || persisted.isEmpty()) {
                return;
            }
            List<Message> stored = new ArrayList<>(persisted);
            for (int index = stored.size() - 1; index >= 0; index--) {
                Message message = stored.get(index);
                if (message instanceof UserMessage && expandedPrompt.equals(message.getText())) {
                    stored.set(index, new UserMessage(compactPrompt));
                    repository.saveAll(conversationId, stored);
                    return;
                }
            }
        }
    }

    int estimateHistoryTokens(String conversationId) {
        return estimateMessages(get(conversationId));
    }

    private List<List<Message>> splitIntoTurns(List<Message> messages, List<Message> systemMessages) {
        List<List<Message>> turns = new ArrayList<>();
        List<Message> current = null;
        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            if (message instanceof SystemMessage) {
                systemMessages.add(message);
                continue;
            }
            if (message instanceof UserMessage || current == null) {
                current = new ArrayList<>();
                turns.add(current);
            }
            current.add(message);
        }
        return turns;
    }

    private SystemMessage buildSummary(List<Message> systemMessages,
                                       List<Message> droppedMessages,
                                       int availableTokens) {
        if (droppedMessages.isEmpty() && systemMessages.isEmpty()) {
            return null;
        }
        int summaryBudget = Math.min(
                maxSummaryTokens,
                Math.max(availableTokens - MESSAGE_OVERHEAD_TOKENS, 0)
        );
        if (summaryBudget <= 32) {
            return null;
        }

        StringBuilder summary = new StringBuilder(
                "【较早对话的压缩摘录】\n仅用于延续上下文；原始工具结果和附件正文已省略。\n"
        );
        appendSummaryMessages(summary, systemMessages, "系统");
        appendSummaryMessages(summary, droppedMessages, null);
        String compact = tokenEstimator.truncate(summary.toString(), summaryBudget);
        return StringUtils.hasText(compact) ? new SystemMessage(compact) : null;
    }

    private void appendSummaryMessages(StringBuilder summary,
                                       List<Message> messages,
                                       String fixedRole) {
        int start = Math.max(messages.size() - 12, 0);
        for (int index = start; index < messages.size(); index++) {
            Message message = messages.get(index);
            if (!StringUtils.hasText(message.getText())) {
                continue;
            }
            String role = fixedRole;
            if (role == null) {
                role = message instanceof UserMessage ? "用户" : "助手";
            }
            summary.append(role)
                    .append("：")
                    .append(tokenEstimator.truncate(message.getText(), 240))
                    .append("\n");
        }
    }

    private int estimateMessages(List<Message> messages) {
        if (messages == null) {
            return 0;
        }
        return messages.stream().mapToInt(this::estimateMessage).sum();
    }

    private int estimateMessage(Message message) {
        return message == null
                ? 0
                : MESSAGE_OVERHEAD_TOKENS + tokenEstimator.estimate(message.getText());
    }

    private record PromptReplacement(String expandedPrompt, String compactPrompt) {
    }
}
