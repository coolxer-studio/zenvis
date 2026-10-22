package com.coolxer.service.dih.agentrun;

import com.coolxer.commons.enums.AgentRunStatus;
import com.coolxer.configuration.JpaAuditorContext;
import com.coolxer.dao.mysql.entity.AgentRun;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.AgentRunEventRepository;
import com.coolxer.dao.mysql.repository.AgentRunInputRepository;
import com.coolxer.dao.mysql.repository.AgentRunRepository;
import com.coolxer.dao.mysql.repository.ChatSessionRepository;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.dto.AgentRunRequestDto;
import com.coolxer.model.dih.dto.AgentRunSteerDto;
import com.coolxer.model.dih.vo.AgentRunVo;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.ai.agent-run.dispatch-delay-ms=86400000",
        "app.ai.agent-run.max-concurrency=1"
})
class AgentRunPersistenceIntegrationTest {

    @Autowired
    private AgentRunService runService;

    @Autowired
    private AgentRunRepository runRepository;

    @Autowired
    private AgentRunEventRepository eventRepository;

    @Autowired
    private AgentRunInputRepository inputRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    @Qualifier("mysqlDataSource")
    private DataSource mysqlDataSource;

    @BeforeEach
    void removeStaleTestRuns() {
        JdbcTemplate jdbc = new JdbcTemplate(mysqlDataSource);
        for (String prefix : List.of("agent-run-create-%", "agent-run-lease-%", "agent-run-session-%")) {
            jdbc.update("DELETE e FROM t_ai_agent_run_event e JOIN t_ai_agent_run r " +
                    "ON e.run_id = r.run_id WHERE r.chat_id LIKE ?", prefix);
            jdbc.update("DELETE i FROM t_ai_agent_run_input i JOIN t_ai_agent_run r " +
                    "ON i.run_id = r.run_id WHERE r.chat_id LIKE ?", prefix);
            jdbc.update("DELETE FROM t_ai_agent_run WHERE chat_id LIKE ?", prefix);
            jdbc.update("DELETE FROM t_ai_chat_session WHERE session_id LIKE ?", prefix);
        }
    }

    @Test
    void concurrentCreateKeepsSingleActiveRunAndSerializesSteer() throws Exception {
        String chatId = "agent-run-create-" + UUID.randomUUID();
        User user = testUser();
        List<String> runIds = new ArrayList<>();
        try {
            List<AgentRunVo> created = runConcurrently(2,
                    () -> asUser(user, () -> runService.create(request(chatId), user)));

            assertThat(created).hasSize(2);
            runIds.addAll(created.stream().map(AgentRunVo::getRunId).distinct().toList());
            assertThat(created).extracting(AgentRunVo::getRunId).containsOnly(created.get(0).getRunId());
            assertThat(created).extracting(AgentRunVo::isSteered).containsExactlyInAnyOrder(false, true);
            String runId = created.get(0).getRunId();
            assertThat(runRepository.findFirstByChatIdAndCreateByAndStatusInOrderByCreateTimeDesc(
                    chatId, user.getId(), AgentRunService.ACTIVE_STATUSES)).isPresent();

            AgentRunSteerDto steer = new AgentRunSteerDto();
            steer.setMessage("补充要求");
            steer.setClientMessageId("same-client-message");
            List<AgentRunVo> steered = runConcurrently(2,
                    () -> asUser(user, () -> runService.steer(runId, steer, user)));

            assertThat(steered).extracting(AgentRunVo::getRunId).containsOnly(runId);
            assertThat(inputRepository.findByRunIdOrderByCreateTimeAsc(runId)
                    .stream()
                    .filter(input -> "same-client-message".equals(input.getClientMessageId()))
                    .count()).isEqualTo(1);
            ChatSession session = chatSessionRepository
                    .findBySessionIdAndCreateBy(chatId, user.getId()).orElseThrow();
            List<Message> messages = JacksonUtil.toList(
                    session.getMessages(), new TypeReference<List<Message>>() {});
            assertThat(messages.stream().filter(message -> "补充要求".equals(message.getContent())).count())
                    .isEqualTo(1);
        } finally {
            cleanup(chatId, user.getId(), runIds);
        }
    }

    @Test
    void casClaimAllowsOnlyOneWorkerAndExpiredLeaseIsRequeued() {
        String chatId = "agent-run-lease-" + UUID.randomUUID();
        String runId = UUID.randomUUID().toString();
        AgentRun run = new AgentRun()
                .setRunId(runId)
                .setChatId(chatId)
                .setChatType("agent_report")
                .setAgentType("agent_report")
                .setStatus(AgentRunStatus.QUEUED)
                .setGoal("租约测试")
                .setDecisionSteps(0)
                .setToolCalls(0)
                .setEffectiveMillis(0L)
                .setMaxSteps(30)
                .setMaxDurationSeconds(1800)
                .setMaxToolCalls(80)
                .setPlanRevision(0)
                .setLastEventSequence(0L);
        run.setCreateBy(-91001);
        run.setUpdateBy(-91001);
        run = runRepository.saveAndFlush(run);

        try {
            Date now = new Date();
            Date leaseUntil = new Date(now.getTime() + 60_000);
            Integer first = runRepository.claimRun(run.getId(), "lease-a", leaseUntil, now);
            Integer second = runRepository.claimRun(run.getId(), "lease-b", leaseUntil, now);

            assertThat(List.of(first, second)).containsExactlyInAnyOrder(1, 0);
            AgentRun claimed = runRepository.findByRunId(runId).orElseThrow();
            assertThat(claimed.getStatus()).isEqualTo(AgentRunStatus.RUNNING);
            assertThat(claimed.getAttemptCount()).isEqualTo(1);

            claimed.setHeartbeatTime(new Date(now.getTime() - 10 * 60_000));
            runRepository.saveAndFlush(claimed);
            assertThat(runRepository.requeueInterrupted(new Date(now.getTime() - 5 * 60_000)))
                    .isGreaterThanOrEqualTo(1);

            AgentRun recovered = runRepository.findByRunId(runId).orElseThrow();
            assertThat(recovered.getStatus()).isEqualTo(AgentRunStatus.QUEUED);
            assertThat(recovered.getLeaseId()).isNull();
            assertThat(recovered.getRecoveryCount()).isEqualTo(1);
        } finally {
            cleanup(chatId, -91001, List.of(runId));
        }
    }

    @Test
    void concurrentMessageAppendDoesNotOverwriteExistingMessages() throws Exception {
        String chatId = "agent-run-session-" + UUID.randomUUID();
        User user = testUser();
        ChatSession session = null;
        ChatSession createdSession;
        try {
            createdSession = asUser(user, () -> chatSessionService.appendMessage(chatId,
                    new com.coolxer.model.dih.dto.ChatSessionDto(),
                    new Message("user", "初始消息"), user));
            session = createdSession;

            runConcurrently(2, index -> asUser(user, () -> chatSessionService.appendMessage(
                    createdSession, new Message("user", "并发消息-" + index), user)));

            ChatSession persisted = chatSessionRepository
                    .findBySessionIdAndCreateBy(chatId, user.getId()).orElseThrow();
            List<Message> messages = JacksonUtil.toList(
                    persisted.getMessages(), new TypeReference<List<Message>>() {});
            assertThat(messages).extracting(Message::getContent)
                    .contains("并发消息-0", "并发消息-1");
        } finally {
            if (session != null && session.getId() != null) {
                chatSessionRepository.deleteById(session.getId());
            } else {
                chatSessionRepository.findBySessionIdAndCreateBy(chatId, user.getId())
                        .ifPresent(item -> chatSessionRepository.deleteById(item.getId()));
            }
        }
    }

    private List<AgentRunVo> runConcurrently(int count,
                                             java.util.function.Supplier<AgentRunVo> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<AgentRunVo>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return action.get();
                }));
            }
            start.countDown();
            List<AgentRunVo> values = new ArrayList<>();
            for (Future<AgentRunVo> future : futures) {
                values.add(future.get(20, TimeUnit.SECONDS));
            }
            return values;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<ChatSession> runConcurrently(int count,
                                              java.util.function.Function<Integer, ChatSession> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ChatSession>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int current = index;
                futures.add(pool.submit(() -> {
                    start.await();
                    return action.apply(current);
                }));
            }
            start.countDown();
            List<ChatSession> values = new ArrayList<>();
            for (Future<ChatSession> future : futures) {
                values.add(future.get(20, TimeUnit.SECONDS));
            }
            return values;
        } finally {
            pool.shutdownNow();
        }
    }

    private AgentRunRequestDto request(String chatId) {
        AgentRunRequestDto request = new AgentRunRequestDto();
        request.setChatId(chatId);
        request.setType("agent_report");
        request.setMessage("生成运营周报");
        request.setClientMessageId(UUID.randomUUID().toString());
        return request;
    }

    private User testUser() {
        User user = new User();
        user.setId(-91002);
        return user;
    }

    private <T> T asUser(User user, java.util.function.Supplier<T> action) {
        return JpaAuditorContext.callWith(user.getId(), action);
    }

    private void cleanup(String chatId, Integer userId, List<String> runIds) {
        for (String runId : runIds) {
            inputRepository.findByRunIdOrderByCreateTimeAsc(runId)
                    .forEach(inputRepository::delete);
            eventRepository.findTop500ByRunIdAndSequenceGreaterThanOrderBySequenceAsc(runId, 0)
                    .forEach(eventRepository::delete);
            runRepository.findByRunId(runId).ifPresent(runRepository::delete);
        }
        chatSessionRepository.findBySessionIdAndCreateBy(chatId, userId)
                .ifPresent(session -> chatSessionRepository.deleteById(session.getId()));
    }
}
