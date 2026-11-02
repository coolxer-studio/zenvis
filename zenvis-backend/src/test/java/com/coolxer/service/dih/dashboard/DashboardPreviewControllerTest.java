package com.coolxer.service.dih.dashboard;

import com.coolxer.controller.dih.DashboardPreviewController;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.dashboard.DashboardManifest;
import com.coolxer.model.dih.dashboard.DashboardPackage;
import com.coolxer.model.dih.dashboard.DashboardSpec;
import com.coolxer.model.dih.dashboard.DashboardValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardPreviewControllerTest {

    private TestPreviewController controller;
    private DashboardPreviewRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DashboardPreviewRegistry();
        DashboardSpec spec = new DashboardSpec();
        spec.setDashboardId("user-event-dashboard");
        spec.setName("用户事件大屏");
        DashboardManifest manifest = new DashboardManifest();
        manifest.setDashboardId(spec.getDashboardId());
        manifest.setDashboardVersion(1);
        manifest.setMode(spec.getMode());
        manifest.setSpec(spec);
        DashboardPackage packageValue = new DashboardPackage(
                spec, manifest, Map.of("user-event-dashboard.html", "<html>preview</html>"),
                "user-event-dashboard.manifest.json", null);
        DashboardValidationResult validation = new DashboardValidationResult();
        validation.setCandidateDigest("candidate");
        validation.setValidationDigest("validation");
        validation.setSpec(spec);
        Map<String, String> files = new LinkedHashMap<>();
        files.put("user-event-dashboard.html", "<html>preview</html>");
        files.put("index.json", "{\"type\":\"page\"}");
        registry.put("wf-1", "chat-1", 7, "token-1", validation, packageValue, files);

        DashboardPackageService packageService = mock(DashboardPackageService.class);
        when(packageService.previewContentPath(any())).thenReturn("user-event-dashboard.html");
        controller = new TestPreviewController(registry, packageService);
        controller.user = user(7);
    }

    @Test
    void returnsUnauthorizedWithoutAUser() {
        controller.user = null;
        ResponseEntity<String> response = controller.preview("wf-1", "chat-1", "token-1");
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void rejectsCrossUserChatAndTokenAccess() {
        controller.user = user(8);
        assertThat(controller.preview("wf-1", "chat-1", "token-1").getStatusCode().value())
                .isEqualTo(404);
        controller.user = user(7);
        assertThat(controller.preview("wf-1", "chat-2", "token-1").getStatusCode().value())
                .isEqualTo(404);
        assertThat(controller.preview("wf-1", "chat-1", "token-2").getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void rejectsTraversalAndUndeclaredPaths() {
        assertThat(controller.file("wf-1", "chat-1", "token-1", "../user-event-dashboard.html")
                .getStatusCode().value()).isEqualTo(404);
        assertThat(controller.file("wf-1", "chat-1", "token-1", "_dashboard.json")
                .getStatusCode().value()).isEqualTo(404);
        assertThat(controller.file("wf-1", "chat-1", "token-1", "/etc/passwd")
                .getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void servesOnlyTheScopedPreviewWithNoStoreAndCsp() {
        ResponseEntity<String> preview = controller.preview("wf-1", "chat-1", "token-1");
        ResponseEntity<String> file = controller.file("wf-1", "chat-1", "token-1", "index.json");

        assertThat(preview.getStatusCode().value()).isEqualTo(200);
        assertThat(preview.getBody()).contains("preview");
        assertThat(preview.getHeaders().getContentType().toString()).contains("text/html");
        assertThat(preview.getHeaders().getCacheControl()).contains("no-store");
        assertThat(preview.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(preview.getHeaders().getFirst("Content-Security-Policy"))
                .contains("frame-ancestors 'self'", "connect-src 'self'", "script-src 'self'");
        assertThat(file.getStatusCode().value()).isEqualTo(200);
        assertThat(file.getHeaders().getContentType().toString()).contains("application/json");
        assertThat(file.getBody()).contains("\"page\"");
    }

    private User user(int id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static final class TestPreviewController extends DashboardPreviewController {

        private User user;

        private TestPreviewController(DashboardPreviewRegistry registry,
                                      DashboardPackageService packageService) {
            super(registry, packageService);
        }

        @Override
        protected User getSessionUser() {
            return user;
        }
    }
}
