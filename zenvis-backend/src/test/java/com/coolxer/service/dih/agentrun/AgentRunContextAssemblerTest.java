package com.coolxer.service.dih.agentrun;

import com.coolxer.dao.mysql.entity.AgentRun;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.commons.enums.McpInvocationStatus;
import com.coolxer.dao.mysql.repository.AgentRunInputRepository;
import com.coolxer.dao.mysql.repository.McpToolInvocationRepository;
import com.coolxer.model.dih.ContextCompactionEventData;
import com.coolxer.model.dih.Message;
import com.coolxer.service.dih.ChatAttachmentService;
import com.coolxer.service.dih.DihTokenEstimator;
import com.coolxer.service.dih.mcp.McpToolDescriptor;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunContextAssemblerTest {

    private final AgentRunInputRepository inputRepository = mock(AgentRunInputRepository.class);
    private final McpToolInvocationRepository invocationRepository =
            mock(McpToolInvocationRepository.class);
    private final ChatAttachmentService attachmentService = mock(ChatAttachmentService.class);
    private AgentRunContextAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new AgentRunContextAssembler(
                inputRepository,
                invocationRepository,
                attachmentService,
                new DihTokenEstimator()
        );
        when(inputRepository.findByRunIdOrderByCreateTimeAsc(anyString())).thenReturn(List.of());
        when(invocationRepository.findByAgentRunIdAndTurnIdOrderByCreateTimeDesc(
                anyString(), anyString())).thenReturn(List.of());
        when(attachmentService.appendAttachmentContext(
                anyString(), anyList(), any(User.class), nullable(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void reportsRecentHistoryWindowAndLongMessageTruncation() {
        List<Message> messages = new ArrayList<>();
        for (int index = 1; index <= 8; index++) {
            String marker = index <= 2 ? "FIRST_OMITTED" : "LAST_KEPT";
            messages.add(new Message(index % 2 == 0 ? "ai" : "user",
                    marker + index + ":" + "数".repeat(3_010)));
        }
        ChatSession session = new ChatSession().setSessionId("chat-1")
                .setMessages(JacksonUtil.toJson(messages));
        List<ContextCompactionEventData> events = new ArrayList<>();

        AgentRunContextAssembler.PromptContext context = assembler.assemble(
                run("短目标", null), session, user(), "1", events::add);

        assertThat(context.text()).doesNotContain("FIRST_OMITTED")
                .contains("LAST_KEPT8");
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getStage()).isEqualTo(ContextCompactionEventData.STAGE_RUN_CONTEXT);
            assertThat(event.getReason()).isEqualTo("recent_history_truncated");
            assertThat(event.getOmittedMessages()).isEqualTo(2);
            assertThat(event.getRetainedMessages()).isEqualTo(6);
            assertThat(event.getTruncatedMessages()).isEqualTo(6);
            assertThat(event.getSavedTokens()).isPositive();
        });
    }

    @Test
    void reportsCheckpointTruncationBeforeTheRunPromptLimit() {
        String checkpoint = "C".repeat(11_000);
        List<ContextCompactionEventData> events = new ArrayList<>();

        AgentRunContextAssembler.PromptContext context = assembler.assemble(
                run("短目标", checkpoint), null, user(), "1", events::add);

        assertThat(context.text()).contains("【上一步检查点】")
                .doesNotContain("C".repeat(10_001));
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getReason()).isEqualTo("checkpoint_truncated");
            assertThat(event.getBeforeChars()).isEqualTo(11_000);
            assertThat(event.getAfterChars()).isEqualTo(10_000);
            assertThat(event.getBudgetChars()).isEqualTo(10_000);
        });
    }

    @Test
    void reportsRunPromptCharacterLimitAsACompactionEvent() {
        List<ContextCompactionEventData> events = new ArrayList<>();

        AgentRunContextAssembler.PromptContext context = assembler.assemble(
                run("G".repeat(50_000), null), null, user(), "1", events::add);

        assertThat(context.text()).hasSize(48_000);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getReason()).isEqualTo("prompt_char_limit");
            assertThat(event.getBeforeChars()).isGreaterThan(50_000);
            assertThat(event.getAfterChars()).isEqualTo(48_000);
            assertThat(event.getBudgetChars()).isEqualTo(48_000);
            assertThat(event.getSavedTokens()).isPositive();
        });
    }

    @Test
    void unknownPushCreatePointsModelToPlatformReapprovalInsteadOfUserInput() {
        when(invocationRepository.findByAgentRunIdAndTurnIdOrderByCreateTimeDesc(
                anyString(), anyString())).thenReturn(List.of(
                new McpToolInvocation()
                        .setToolKey(McpToolDescriptor.localKey("push_task_create_and_start"))
                        .setToolName("push_task_create_and_start")
                        .setStatus(McpInvocationStatus.UNKNOWN)));

        AgentRunContextAssembler.PromptContext context = assembler.assemble(
                run("推送数据", null), null, user(), "1", events -> {});

        assertThat(context.text())
                .contains("平台已按 sourceMark 为该工具启用幂等恢复")
                .contains("不要调用 run_request_user");
    }

    private AgentRun run(String goal, String checkpoint) {
        AgentRun run = new AgentRun()
                .setRunId("run-1")
                .setGoal(goal)
                .setPlanRevision(1)
                .setPlanJson("""
                        {"steps":[{"id":"1","title":"执行","status":"RUNNING"}]}
                        """)
                .setContextCheckpoint(checkpoint)
                .setLastEventSequence(0L);
        run.setCreateBy(7);
        return run;
    }

    private User user() {
        User user = new User();
        user.setId(7);
        return user;
    }
}
