package com.coolxer.service.dih.dashboard;

import com.coolxer.model.dih.dashboard.DashboardApplyResult;
import com.coolxer.model.dih.dashboard.DashboardManifest;
import com.coolxer.model.dih.dashboard.DashboardPackage;
import com.coolxer.model.dih.dashboard.DashboardSpec;
import com.coolxer.model.dih.dashboard.DashboardValidationResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardPreviewRegistryTest {

    @Test
    void bindsPreviewToWorkflowUserChatAndToken() {
        DashboardPreviewRegistry registry = new DashboardPreviewRegistry();
        put(registry, "wf-1", "chat-1", 7, "token-1");

        assertThat(registry.get("wf-1", 7, "chat-1", "token-1")).isPresent();
        assertThat(registry.get("wf-1", 8, "chat-1", "token-1")).isEmpty();
        assertThat(registry.get("wf-1", 7, "chat-2", "token-1")).isEmpty();
        assertThat(registry.get("wf-1", 7, "chat-1", "token-2")).isEmpty();
        assertThat(registry.get("wf-2", 7, "chat-1", "token-1")).isEmpty();
        assertThat(registry.getForApply("wf-1", 7, "chat-1")).isPresent();
        assertThat(registry.getForApply("wf-1", 8, "chat-1")).isEmpty();
    }

    @Test
    void expiresPreviewAndAppliedResultAfterThirtyMinutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-01T00:00:00Z"));
        DashboardPreviewRegistry registry = new DashboardPreviewRegistry(clock);
        put(registry, "wf-1", "chat-1", 7, "token-1");
        DashboardApplyResult applied = new DashboardApplyResult();
        applied.setStatus("verified");
        applied.setDashboardId("user-event-dashboard");
        registry.markApplied("wf-1", applied);

        clock.advance(Duration.ofMinutes(29));
        assertThat(registry.get("wf-1", 7, "chat-1", "token-1")).isPresent();
        assertThat(registry.getApplied("wf-1", 7, "chat-1", "candidate", "validation"))
                .isPresent();

        clock.advance(Duration.ofMinutes(2));
        assertThat(registry.get("wf-1", 7, "chat-1", "token-1")).isEmpty();
        assertThat(registry.getForApply("wf-1", 7, "chat-1")).isEmpty();
        assertThat(registry.getApplied("wf-1", 7, "chat-1", "candidate", "validation"))
                .isEmpty();
    }

    @Test
    void appliedResultIsBoundToTheLockedDigests() {
        DashboardPreviewRegistry registry = new DashboardPreviewRegistry();
        put(registry, "wf-1", "chat-1", 7, "token-1");
        DashboardApplyResult applied = new DashboardApplyResult();
        applied.setStatus("verified");
        registry.markApplied("wf-1", applied);

        assertThat(registry.getApplied("wf-1", 7, "chat-1", "candidate", "validation"))
                .isPresent();
        assertThat(registry.getApplied("wf-1", 7, "chat-1", "candidate", "old-validation"))
                .isEmpty();
        assertThat(registry.getApplied("wf-1", 8, "chat-1", "candidate", "validation"))
                .isEmpty();
    }

    @Test
    void exposesOnlyFilesDeclaredInTheCompiledPreviewMap() {
        DashboardPreviewRegistry registry = new DashboardPreviewRegistry();
        put(registry, "wf-1", "chat-1", 7, "token-1");

        DashboardPreviewRegistry.PreviewSession session = registry.get(
                "wf-1", 7, "chat-1", "token-1").orElseThrow();

        assertThat(session.previewFiles()).containsOnlyKeys("user-event-dashboard.html");
        assertThat(session.previewFiles().get("../user-event-dashboard.html")).isNull();
        assertThat(session.previewFiles().get("_dashboard.json")).isNull();

        registry.remove("wf-1");
        assertThat(registry.get("wf-1", 7, "chat-1", "token-1")).isEmpty();
    }

    private void put(DashboardPreviewRegistry registry,
                     String workflowId,
                     String chatId,
                     int userId,
                     String token) {
        DashboardSpec spec = new DashboardSpec();
        spec.setDashboardId("user-event-dashboard");
        spec.setName("用户事件大屏");
        DashboardManifest manifest = new DashboardManifest();
        manifest.setDashboardId(spec.getDashboardId());
        manifest.setDashboardVersion(1);
        manifest.setMode(spec.getMode());
        manifest.setSpec(spec);
        manifest.setFiles(new LinkedHashMap<>(Map.of(
                "user-event-dashboard.html", "file-digest")));
        DashboardPackage packageValue = new DashboardPackage(
                spec, manifest, Map.of("user-event-dashboard.html", "<html></html>"),
                "user-event-dashboard.manifest.json", null);
        DashboardValidationResult validation = new DashboardValidationResult();
        validation.setCandidateDigest("candidate");
        validation.setValidationDigest("validation");
        Map<String, String> previewFiles = new LinkedHashMap<>();
        previewFiles.put("user-event-dashboard.html", "<html></html>");
        registry.put(workflowId, chatId, userId, token, validation, packageValue, previewFiles);
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> now;

        private MutableClock(Instant initial) {
            this.now = new AtomicReference<>(initial);
        }

        private void advance(Duration duration) {
            now.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
