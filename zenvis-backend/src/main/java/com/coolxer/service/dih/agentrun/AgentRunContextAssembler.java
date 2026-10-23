package com.coolxer.service.dih.agentrun;

import com.coolxer.commons.enums.AgentRunInputStatus;
import com.coolxer.dao.mysql.entity.AgentRun;
import com.coolxer.dao.mysql.entity.AgentRunInput;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.AgentRunInputRepository;
import com.coolxer.dao.mysql.repository.McpToolInvocationRepository;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.model.dih.ContextCompactionEventData;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.agentrun.AgentRunPlan;
import com.coolxer.model.dih.agentrun.AgentRunPlanStep;
import com.coolxer.service.dih.ContextCompactionObserver;
import com.coolxer.service.dih.ChatAttachmentService;
import com.coolxer.service.dih.DihTokenEstimator;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 将持久化计划、摘要、最近对话和 steer 装配成单步模型上下文。
 */
@Slf4j
@Service
public class AgentRunContextAssembler {

    private static final int MAX_CONTEXT_CHARS = 48_000;
    private static final int MAX_MESSAGE_CHARS = 3_000;

    private final AgentRunInputRepository inputRepository;
    private final McpToolInvocationRepository invocationRepository;
    private final ChatAttachmentService chatAttachmentService;
    private final DihTokenEstimator tokenEstimator;

    public AgentRunContextAssembler(AgentRunInputRepository inputRepository,
                                    McpToolInvocationRepository invocationRepository,
                                    ChatAttachmentService chatAttachmentService,
                                    DihTokenEstimator tokenEstimator) {
        this.inputRepository = inputRepository;
        this.invocationRepository = invocationRepository;
        this.chatAttachmentService = chatAttachmentService;
        this.tokenEstimator = tokenEstimator;
    }

    public PromptContext assemble(AgentRun run, ChatSession session, User user, String stepId) {
        return assemble(run, session, user, stepId, ContextCompactionObserver.NOOP);
    }

    public PromptContext assemble(AgentRun run,
                                  ChatSession session,
                                  User user,
                                  String stepId,
                                  ContextCompactionObserver compactionObserver) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("【用户目标】\n")
                .append(StringUtils.defaultString(run.getGoal())).append("\n\n");

        AgentRunPlan plan = parsePlan(run.getPlanJson());
        prompt.append("【当前计划 revision ").append(run.getPlanRevision()).append("】\n");
        for (AgentRunPlanStep step : plan.getSteps()) {
            prompt.append("- [").append(step.getStatus()).append("] ")
                    .append(step.getId()).append(" ").append(step.getTitle());
            if (StringUtils.isNotBlank(step.getAcceptance())) {
                prompt.append("；验收：").append(StringUtils.left(step.getAcceptance(), 600));
            }
            if (StringUtils.isNotBlank(step.getSummary())) {
                prompt.append("；摘要：").append(StringUtils.left(step.getSummary(), 1_200));
            }
            if (step.getEvidenceRefs() != null && !step.getEvidenceRefs().isEmpty()) {
                prompt.append("；证据数：").append(step.getEvidenceRefs().size());
            }
            prompt.append("\n");
        }

        List<AgentRunInput> inputs = inputRepository.findByRunIdOrderByCreateTimeAsc(run.getRunId());
        List<ChatAttachment> attachments = parseAttachments(inputs);
        List<AgentRunInput> pending = new ArrayList<>();
        List<Integer> pendingInputIds = new ArrayList<>();
        for (AgentRunInput input : inputs) {
            if (input.getStatus() == AgentRunInputStatus.PENDING) {
                pending.add(input);
            }
        }
        if (!pending.isEmpty()) {
            prompt.append("\n【用户运行中追加指令，必须优先吸收】\n");
            for (AgentRunInput input : pending) {
                prompt.append("- ").append(StringUtils.left(
                        StringUtils.defaultString(input.getContent()), 4_000)).append("\n");
                pendingInputIds.add(input.getId());
            }
        }

        HistoryWindowStats historyStats = appendRecentConversation(
                prompt, run, stepId, session, compactionObserver);
        appendApprovalState(prompt, run, stepId);
        String checkpoint = StringUtils.defaultString(run.getContextCheckpoint());
        if (StringUtils.isNotBlank(checkpoint)) {
            prompt.append("\n【上一步检查点】\n")
                    .append(StringUtils.left(checkpoint, 10_000)).append("\n");
            if (checkpoint.length() > 10_000) {
                emitCompaction(compactionObserver, run, stepId,
                        baseEvent(ContextCompactionEventData.STAGE_RUN_CONTEXT,
                                "checkpoint_truncated", checkpoint, checkpoint.substring(0, 10_000))
                                .omittedMessages(1)
                                .retainedMessages(historyStats.retainedMessages())
                                .truncatedMessages(1)
                                .budgetChars(10_000)
                                .build());
            }
        }
        prompt.append("\n请推进当前步骤 ").append(stepId)
                .append("。需要调整计划时调用 run_update_plan；完成步骤后调用 run_update_step。")
                .append("只有全部步骤完成或明确跳过时才调用 run_finish。");

        String originalPrompt = prompt.toString();
        String bounded = StringUtils.left(originalPrompt, MAX_CONTEXT_CHARS);
        if (bounded.length() < originalPrompt.length()) {
            emitCompaction(compactionObserver, run, stepId,
                    baseEvent(ContextCompactionEventData.STAGE_RUN_CONTEXT,
                            "prompt_char_limit", originalPrompt, bounded)
                            .omittedMessages(Math.max(historyStats.omittedMessages(), 1))
                            .retainedMessages(historyStats.retainedMessages())
                            .truncatedMessages(historyStats.truncatedMessages())
                            .budgetChars(MAX_CONTEXT_CHARS)
                            .build());
        }
        return new PromptContext(
                chatAttachmentService.appendAttachmentContext(bounded, attachments, user),
                attachments, pendingInputIds);
    }

    public void applyInputs(List<Integer> inputIds) {
        if (inputIds == null || inputIds.isEmpty()) {
            return;
        }
        List<java.io.Serializable> ids = inputIds.stream()
                .map(id -> (java.io.Serializable) id)
                .toList();
        List<AgentRunInput> inputs = inputRepository.findAllById(ids).stream()
                .filter(input -> input.getStatus() == AgentRunInputStatus.PENDING)
                .toList();
        if (inputs.isEmpty()) {
            return;
        }
        Date now = new Date();
        inputs.forEach(input -> input.setStatus(AgentRunInputStatus.APPLIED).setAppliedAt(now));
        inputRepository.saveAll(inputs);
    }

    private HistoryWindowStats appendRecentConversation(StringBuilder prompt,
                                                        AgentRun run,
                                                        String stepId,
                                                        ChatSession session,
                                                        ContextCompactionObserver observer) {
        if (session == null || StringUtils.isBlank(session.getMessages())) {
            return HistoryWindowStats.empty();
        }
        List<Message> messages;
        try {
            messages = JacksonUtil.toList(session.getMessages(), new TypeReference<List<Message>>() {});
        } catch (RuntimeException e) {
            log.warn("解析 Run 会话历史失败: sessionId={}", session.getSessionId());
            return HistoryWindowStats.empty();
        }
        if (messages == null || messages.isEmpty()) {
            return HistoryWindowStats.empty();
        }
        List<Message> candidates = messages.stream()
                .filter(message -> !Boolean.TRUE.equals(message.getInternal()))
                .filter(message -> !StringUtils.equals(run.getRunId(), message.getRunId()))
                .filter(message -> StringUtils.isNotBlank(message.getContent()))
                .toList();
        List<Message> recent = candidates.stream()
                .skip(Math.max(candidates.size() - 6L, 0))
                .toList();
        if (recent.isEmpty()) {
            return HistoryWindowStats.empty();
        }
        prompt.append("\n【最近对话，仅作背景】\n");
        int beforeChars = 0;
        int afterChars = 0;
        int truncatedMessages = 0;
        for (Message message : candidates) {
            beforeChars += StringUtils.defaultString(message.getContent()).length();
        }
        for (Message message : recent) {
            String content = StringUtils.defaultString(message.getContent());
            String included = StringUtils.left(content, MAX_MESSAGE_CHARS);
            afterChars += included.length();
            if (included.length() < content.length()) {
                truncatedMessages++;
            }
            prompt.append("- ").append(StringUtils.defaultIfBlank(message.getSender(), "unknown"))
                    .append("：").append(included)
                    .append("\n");
        }
        int omittedMessages = Math.max(candidates.size() - recent.size(), 0);
        if (omittedMessages > 0 || truncatedMessages > 0) {
            ContextCompactionEventData data = ContextCompactionEventData.builder()
                    .compactionId(UUID.randomUUID().toString())
                    .stage(ContextCompactionEventData.STAGE_RUN_CONTEXT)
                    .reason(truncatedMessages > 0
                            ? "recent_history_truncated" : "recent_history_window")
                    .beforeTokens(tokenEstimator.estimate(joinContents(candidates)))
                    .afterTokens(tokenEstimator.estimate(joinContents(recent, MAX_MESSAGE_CHARS)))
                    .beforeChars(beforeChars)
                    .afterChars(afterChars)
                    .omittedMessages(omittedMessages)
                    .retainedMessages(recent.size())
                    .truncatedMessages(truncatedMessages)
                    .summaryPreview("仅保留最近 " + recent.size() + " 条历史消息作为背景")
                    .build();
            data.setSavedTokens(Math.max(data.getBeforeTokens() - data.getAfterTokens(), 0));
            emitCompaction(observer, run, stepId, data);
        }
        return new HistoryWindowStats(omittedMessages, recent.size(), truncatedMessages);
    }

    private String joinContents(List<Message> messages) {
        return joinContents(messages, Integer.MAX_VALUE);
    }

    private String joinContents(List<Message> messages, int maxCharsPerMessage) {
        StringBuilder value = new StringBuilder();
        for (Message message : messages) {
            value.append(StringUtils.left(
                    StringUtils.defaultString(message.getContent()), maxCharsPerMessage))
                    .append('\n');
        }
        return value.toString();
    }

    private ContextCompactionEventData.ContextCompactionEventDataBuilder baseEvent(
            String stage,
            String reason,
            String before,
            String after) {
        int beforeTokens = tokenEstimator.estimate(before);
        int afterTokens = tokenEstimator.estimate(after);
        return ContextCompactionEventData.builder()
                .compactionId(UUID.randomUUID().toString())
                .stage(stage)
                .reason(reason)
                .beforeTokens(beforeTokens)
                .afterTokens(afterTokens)
                .savedTokens(Math.max(beforeTokens - afterTokens, 0))
                .beforeChars(before.length())
                .afterChars(after.length());
    }

    private void emitCompaction(ContextCompactionObserver observer,
                                AgentRun run,
                                String stepId,
                                ContextCompactionEventData data) {
        if (observer == null || !observer.enabled()) {
            return;
        }
        try {
            observer.onCompacted(data);
        } catch (RuntimeException e) {
            log.warn("记录 Run 上下文压缩事件失败: runId={}, stepId={}, reason={}",
                    run.getRunId(), stepId, data == null ? null : data.getReason());
        }
    }

    private void appendApprovalState(StringBuilder prompt, AgentRun run, String stepId) {
        List<McpToolInvocation> invocations = invocationRepository
                .findByAgentRunIdAndTurnIdOrderByCreateTimeDesc(
                        run.getRunId(), "run:" + run.getRunId() + ":" + stepId);
        if (invocations.isEmpty()) {
            return;
        }
        McpToolInvocation latest = invocations.get(0);
        if (latest.getStatus() == com.coolxer.commons.enums.McpInvocationStatus.REJECTED
                || latest.getStatus() == com.coolxer.commons.enums.McpInvocationStatus.EXPIRED
                || latest.getStatus() == com.coolxer.commons.enums.McpInvocationStatus.UNKNOWN
                || latest.getStatus() == com.coolxer.commons.enums.McpInvocationStatus.FAILED) {
            prompt.append("\n【最近工具审批/执行结果】\n工具 ")
                    .append(latest.getToolName()).append(" 状态为 ")
                    .append(latest.getStatus()).append("。")
                    .append("不要重复执行已失败或被拒绝的外部写操作，请调整计划或说明阻塞。\n");
        }
    }

    private List<ChatAttachment> parseAttachments(List<AgentRunInput> inputs) {
        List<ChatAttachment> attachments = new ArrayList<>();
        for (AgentRunInput input : inputs) {
            if (StringUtils.isBlank(input.getAttachments())) {
                continue;
            }
            try {
                List<ChatAttachment> parsed = JacksonUtil.toList(
                        input.getAttachments(), new TypeReference<List<ChatAttachment>>() {});
                if (parsed != null) {
                    attachments.addAll(parsed);
                }
            } catch (RuntimeException e) {
                log.warn("解析 Agent Run 附件失败: runId={}, inputId={}",
                        input.getRunId(), input.getId());
            }
        }
        return attachments;
    }

    private AgentRunPlan parsePlan(String json) {
        if (StringUtils.isBlank(json)) {
            return AgentRunPlan.builder().steps(List.of()).build();
        }
        try {
            AgentRunPlan plan = JacksonUtil.toObject(json, AgentRunPlan.class);
            return plan == null ? AgentRunPlan.builder().steps(List.of()).build() : plan;
        } catch (RuntimeException e) {
            return AgentRunPlan.builder().steps(List.of()).build();
        }
    }

    public record PromptContext(String text,
                                List<ChatAttachment> attachments,
                                List<Integer> pendingInputIds) {
    }

    private record HistoryWindowStats(int omittedMessages,
                                      int retainedMessages,
                                      int truncatedMessages) {
        private static HistoryWindowStats empty() {
            return new HistoryWindowStats(0, 0, 0);
        }
    }
}
