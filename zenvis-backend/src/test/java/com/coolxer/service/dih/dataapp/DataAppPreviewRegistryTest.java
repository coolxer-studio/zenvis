package com.coolxer.service.dih.dataapp;

import com.coolxer.model.dih.dataapp.DataAppManifest;
import com.coolxer.model.dih.dataapp.DataAppPackage;
import com.coolxer.model.dih.dataapp.DataAppSpec;
import com.coolxer.model.dih.dataapp.DataAppValidationResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DataAppPreviewRegistryTest {

    @Test
    void bindsPreviewToWorkflowUserChatAndToken() {
        DataAppPreviewRegistry registry = new DataAppPreviewRegistry();
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
    void expiresPreviewAfterThirtyMinutesAndRemovesIt() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-01T00:00:00Z"));
        DataAppPreviewRegistry registry = new DataAppPreviewRegistry(clock);
        put(registry, "wf-1", "chat-1", 7, "token-1");

        clock.advance(Duration.ofMinutes(29));
        assertThat(registry.get("wf-1", 7, "chat-1", "token-1")).isPresent();

        clock.advance(Duration.ofMinutes(2));
        assertThat(registry.get("wf-1", 7, "chat-1", "token-1")).isEmpty();
        assertThat(registry.getForApply("wf-1", 7, "chat-1")).isEmpty();
    }

    @Test
    void exposesOnlyFilesDeclaredInTheCompiledPreviewMap() {
        DataAppPreviewRegistry registry = new DataAppPreviewRegistry();
        put(registry, "wf-1", "chat-1", 7, "token-1");

        DataAppPreviewRegistry.PreviewSession session = registry.get(
                "wf-1", 7, "chat-1", "token-1").orElseThrow();

        assertThat(session.previewFiles()).containsOnlyKeys("site.json", "index.json");
        assertThat(session.previewFiles().get("../_app.json")).isNull();
        assertThat(session.previewFiles().get("pages/overview.json")).isNull();

        registry.remove("wf-1");
        assertThat(registry.get("wf-1", 7, "chat-1", "token-1")).isEmpty();
    }

    private void put(DataAppPreviewRegistry registry,
                     String workflowId,
                     String chatId,
                     int userId,
                     String token) {
        DataAppSpec spec = new DataAppSpec();
        spec.setAppId("user-event-analysis");
        spec.setName("用户事件分析");
        spec.setHomePageId("overview");
        DataAppSpec.DataAppPage page = new DataAppSpec.DataAppPage();
        page.setPageId("overview");
        page.setTitle("总览");
        spec.setPages(new ArrayList<>(List.of(page)));
        DataAppManifest manifest = new DataAppManifest();
        manifest.setAppId(spec.getAppId());
        manifest.setAppVersion(1);
        manifest.setMenuType("LOW_CODE_PAGE");
        manifest.setMode("LOW_CODE_PAGE");
        manifest.setSpec(spec);
        manifest.setFiles(new LinkedHashMap<>(Map.of("index.json", "digest")));
        DataAppPackage packageValue = new DataAppPackage(
                spec, manifest, Map.of("index.json", "{}"), List.of(), null);
        DataAppValidationResult validation = new DataAppValidationResult();
        validation.setCandidateDigest("candidate");
        validation.setValidationDigest("validation");
        Map<String, String> previewFiles = new LinkedHashMap<>();
        previewFiles.put("site.json", "{\"data\":{}}");
        previewFiles.put("index.json", "{\"type\":\"page\"}");
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
