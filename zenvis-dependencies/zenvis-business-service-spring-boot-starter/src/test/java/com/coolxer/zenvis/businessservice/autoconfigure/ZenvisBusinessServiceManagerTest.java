package com.coolxer.zenvis.businessservice.autoconfigure;

import com.coolxer.zenvis.businessservice.BusinessServiceEventSeverity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ZenvisBusinessServiceManagerTest {

    private ZenvisBusinessServiceProperties properties;
    private RecordingTransport transport;
    private RecordingTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new ZenvisBusinessServiceProperties();
        properties.setServiceCode("synap-server");
        properties.setServiceName("Synap Server");
        properties.setHost("pod-1");
        properties.setPort(11099);
        properties.setEnvironment("test");
        transport = new RecordingTransport();
        scheduler = new RecordingTaskScheduler();
    }

    @Test
    void startsImmediatelySchedulesHeartbeatsAndReportsStartedOnlyOnce() {
        ZenvisBusinessServiceManager manager = manager(new SyncTaskExecutor());

        manager.startReporting();
        manager.sendScheduledHeartbeat();

        assertThat(transport.heartbeats).hasSize(2);
        assertThat(scheduler.fixedDelaySchedules).isEqualTo(1);
        assertThat(transport.events)
                .extracting(BusinessServiceEventRequest::eventType)
                .containsExactly(ZenvisBusinessServiceManager.SERVICE_STARTED);
    }

    @Test
    void retriesRegistrationOnNextHeartbeat() {
        transport.heartbeatResults.add(false);
        transport.heartbeatResults.add(true);
        ZenvisBusinessServiceManager manager = manager(new SyncTaskExecutor());

        manager.sendScheduledHeartbeat();
        assertThat(transport.events).isEmpty();

        manager.sendScheduledHeartbeat();
        assertThat(transport.events)
                .extracting(BusinessServiceEventRequest::eventType)
                .containsExactly(ZenvisBusinessServiceManager.SERVICE_STARTED);
    }

    @Test
    void registersBeforeSendingRuntimeEventAndUsesUuid() {
        ZenvisBusinessServiceManager manager = manager(new SyncTaskExecutor());

        manager.reportEvent(
                "RULE_EXECUTION_FAILED",
                BusinessServiceEventSeverity.ERROR,
                "规则失败",
                "missing script",
                "trace-1",
                Map.of("script_name", "MissingRule.groovy"));

        assertThat(transport.operations).containsExactly("heartbeat", "event");
        BusinessServiceEventRequest event = transport.events.get(0);
        assertThat(event.eventId()).matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
        assertThat(event.traceId()).isEqualTo("trace-1");
    }

    @Test
    void reportsStoppingBeforeDownHeartbeat() {
        ZenvisBusinessServiceManager manager = manager(new SyncTaskExecutor());
        manager.sendScheduledHeartbeat();
        transport.operations.clear();
        transport.events.clear();
        transport.heartbeats.clear();

        manager.stopReporting();

        assertThat(transport.operations).containsExactly("event", "heartbeat");
        assertThat(transport.events.get(0).eventType())
                .isEqualTo(ZenvisBusinessServiceManager.SERVICE_STOPPING);
        assertThat(transport.heartbeats.get(0).status()).isEqualTo(BusinessServiceStatus.DOWN);
        assertThat(transport.heartbeats.get(0).statusMessage()).isEqualTo("stopping");
    }

    @Test
    void rejectedQueueDoesNotReachBusinessCaller() {
        TaskExecutor rejectingExecutor = task -> {
            throw new TaskRejectedException("queue full");
        };
        ZenvisBusinessServiceManager manager = manager(rejectingExecutor);

        assertThatCode(() -> manager.reportEvent(
                "CROSS_SERVICE_CALL_FAILED",
                BusinessServiceEventSeverity.ERROR,
                "调用失败",
                "timeout",
                null,
                Map.of("api_config_id", "asset")))
                .doesNotThrowAnyException();
        assertThat(transport.operations).isEmpty();
    }

    private ZenvisBusinessServiceManager manager(TaskExecutor executor) {
        ZenvisBusinessServiceIdentity identity = new ZenvisBusinessServiceIdentity(
                properties,
                new MockEnvironment(),
                null);
        return new ZenvisBusinessServiceManager(
                properties,
                identity,
                transport,
                executor,
                scheduler,
                new ObjectMapper());
    }

    private static final class RecordingTransport implements ZenvisBusinessServiceTransport {

        private final List<Boolean> heartbeatResults = new ArrayList<>();
        private final List<String> operations = new ArrayList<>();
        private final List<BusinessServiceHeartbeatRequest> heartbeats = new ArrayList<>();
        private final List<BusinessServiceEventRequest> events = new ArrayList<>();

        @Override
        public boolean reportHeartbeat(BusinessServiceHeartbeatRequest request) {
            operations.add("heartbeat");
            heartbeats.add(request);
            return heartbeatResults.isEmpty() || heartbeatResults.remove(0);
        }

        @Override
        public boolean reportEvent(BusinessServiceEventRequest request) {
            operations.add("event");
            events.add(request);
            return true;
        }
    }

    private static final class RecordingTaskScheduler implements TaskScheduler {

        private int fixedDelaySchedules;

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            return null;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
                Runnable task, Instant startTime, Duration period) {
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable task, Instant startTime, Duration delay) {
            fixedDelaySchedules++;
            task.run();
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            fixedDelaySchedules++;
            return null;
        }
    }
}
