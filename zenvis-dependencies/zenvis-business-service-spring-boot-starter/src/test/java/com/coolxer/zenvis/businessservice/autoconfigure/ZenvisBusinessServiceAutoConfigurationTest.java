package com.coolxer.zenvis.businessservice.autoconfigure;

import com.coolxer.zenvis.businessservice.BusinessServiceReporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ZenvisBusinessServiceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ZenvisBusinessServiceAutoConfiguration.class));

    @Test
    void enablesReporterAndDedicatedInfrastructureByDefault() {
        contextRunner
                .withPropertyValues("spring.application.name=test-service")
                .run(context -> {
                    assertThat(context).hasSingleBean(BusinessServiceReporter.class);
                    assertThat(context).hasBean("zenvisBusinessServiceRestTemplate");
                    assertThat(context).hasBean("zenvisBusinessServiceEventExecutor");
                    assertThat(context).hasBean("zenvisBusinessServiceHeartbeatScheduler");
                });
    }

    @Test
    void disabledModeProvidesNoOpReporterWithoutInfrastructure() {
        contextRunner
                .withPropertyValues("zenvis.business-service.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(BusinessServiceReporter.class);
                    assertThat(context).doesNotHaveBean("zenvisBusinessServiceRestTemplate");
                    assertThat(context).doesNotHaveBean("zenvisBusinessServiceEventExecutor");
                    assertThat(context).doesNotHaveBean("zenvisBusinessServiceHeartbeatScheduler");
                });
    }

    @Test
    void applicationReadyEventTriggersImmediateRegistration() {
        RecordingTransport transport = new RecordingTransport();
        contextRunner
                .withPropertyValues(
                        "spring.application.name=test-service",
                        "zenvis.business-service.host=pod-1",
                        "zenvis.business-service.port=18080")
                .withBean(ZenvisBusinessServiceTransport.class, () -> transport)
                .withBean(
                        "zenvisBusinessServiceHeartbeatScheduler",
                        TaskScheduler.class,
                        ImmediateTaskScheduler::new)
                .run(context -> {
                    context.publishEvent(new ApplicationReadyEvent(
                            new SpringApplication(Object.class),
                            new String[0],
                            context,
                            Duration.ZERO));

                    assertThat(transport.heartbeats).hasSize(1);
                    assertThat(transport.heartbeats.get(0).status()).isEqualTo(BusinessServiceStatus.UP);
                    assertThat(transport.events)
                            .extracting(BusinessServiceEventRequest::eventType)
                            .containsExactly(ZenvisBusinessServiceManager.SERVICE_STARTED);
                });
    }

    private static final class RecordingTransport implements ZenvisBusinessServiceTransport {

        private final List<BusinessServiceHeartbeatRequest> heartbeats = new ArrayList<>();
        private final List<BusinessServiceEventRequest> events = new ArrayList<>();

        @Override
        public boolean reportHeartbeat(BusinessServiceHeartbeatRequest request) {
            heartbeats.add(request);
            return true;
        }

        @Override
        public boolean reportEvent(BusinessServiceEventRequest request) {
            events.add(request);
            return true;
        }
    }

    private static final class ImmediateTaskScheduler implements TaskScheduler {

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            return null;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            task.run();
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
                Runnable task, Instant startTime, Duration period) {
            task.run();
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            task.run();
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable task, Instant startTime, Duration delay) {
            task.run();
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            task.run();
            return null;
        }
    }
}
