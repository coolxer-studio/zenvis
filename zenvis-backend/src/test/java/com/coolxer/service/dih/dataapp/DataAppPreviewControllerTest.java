package com.coolxer.service.dih.dataapp;

import com.coolxer.controller.dih.DataAppPreviewController;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.dataapp.DataAppManifest;
import com.coolxer.model.dih.dataapp.DataAppPackage;
import com.coolxer.model.dih.dataapp.DataAppSpec;
import com.coolxer.model.dih.dataapp.DataAppValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataAppPreviewControllerTest {

    private TestPreviewController controller;

    @BeforeEach
    void setUp() {
        DataAppPreviewRegistry registry = new DataAppPreviewRegistry();
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
        DataAppPackage packageValue = new DataAppPackage(
                spec, manifest, Map.of("index.json", "{}"), List.of(), null);
        DataAppValidationResult validation = new DataAppValidationResult();
        validation.setCandidateDigest("candidate");
        validation.setValidationDigest("validation");
        Map<String, String> previewFiles = new LinkedHashMap<>();
        previewFiles.put("site.json", "{\"data\":{\"pages\":[]}}");
        previewFiles.put("index.json", "{\"type\":\"page\"}");
        registry.put("wf-1", "chat-1", 7, "token-1", validation, packageValue, previewFiles);
        controller = new TestPreviewController(registry);
        controller.user = user(7);
    }

    @Test
    void returnsUnauthorizedWithoutAUser() {
        controller.user = null;
        ResponseEntity<String> response = controller.site("wf-1", "chat-1", "token-1");
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void rejectsCrossUserChatAndTokenAccess() {
        controller.user = user(8);
        assertThat(controller.site("wf-1", "chat-1", "token-1").getStatusCode().value())
                .isEqualTo(404);
        controller.user = user(7);
        assertThat(controller.site("wf-1", "chat-2", "token-1").getStatusCode().value())
                .isEqualTo(404);
        assertThat(controller.site("wf-1", "chat-1", "token-2").getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void rejectsTraversalAndUndeclaredPaths() {
        assertThat(controller.file("wf-1", "chat-1", "token-1", "../_app.json")
                .getStatusCode().value()).isEqualTo(404);
        assertThat(controller.file("wf-1", "chat-1", "token-1", "pages/overview.json")
                .getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void servesOnlyTheScopedPreviewWithNoStoreAndCsp() {
        ResponseEntity<String> site = controller.site("wf-1", "chat-1", "token-1");
        ResponseEntity<String> index = controller.file("wf-1", "chat-1", "token-1", "index.json");

        assertThat(site.getStatusCode().value()).isEqualTo(200);
        assertThat(site.getBody()).contains("\"pages\"");
        assertThat(site.getHeaders().getCacheControl()).contains("no-store");
        assertThat(site.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(site.getHeaders().getFirst("Content-Security-Policy"))
                .contains("frame-ancestors 'self'", "connect-src 'self'");
        assertThat(index.getStatusCode().value()).isEqualTo(200);
        assertThat(index.getBody()).contains("\"page\"");
    }

    private User user(int id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static final class TestPreviewController extends DataAppPreviewController {

        private User user;

        private TestPreviewController(DataAppPreviewRegistry registry) {
            super(registry);
        }

        @Override
        protected User getSessionUser() {
            return user;
        }
    }
}
