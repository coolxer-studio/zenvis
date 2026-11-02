package com.coolxer.controller.dih;

import com.coolxer.controller.BaseController;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.dashboard.DashboardSpec;
import com.coolxer.service.dih.dashboard.DashboardPackageService;
import com.coolxer.service.dih.dashboard.DashboardPreviewRegistry;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Authenticated, workflow-scoped read-only preview. The registry is the only
 * source of content; the configuration directory is never exposed directly.
 */
@RestController
@RequestMapping("/api/v1/dih/chat/workflows")
public class DashboardPreviewController extends BaseController {

    private static final String CSP = "default-src 'self'; script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; "
            + "connect-src 'self'; frame-src 'self'; frame-ancestors 'self'; "
            + "base-uri 'none'; form-action 'none'";

    private final DashboardPreviewRegistry previewRegistry;
    private final DashboardPackageService packageService;

    public DashboardPreviewController(DashboardPreviewRegistry previewRegistry,
                                      DashboardPackageService packageService) {
        this.previewRegistry = previewRegistry;
        this.packageService = packageService;
    }

    @GetMapping("/{workflowId}/dashboard-preview")
    public ResponseEntity<String> preview(@PathVariable("workflowId") String workflowId,
                                          @RequestParam(value = "chat_id", required = false) String chatId,
                                          @RequestParam(value = "token", required = false) String token) {
        User user = getSessionUser();
        if (user == null) {
            return error(401, "当前用户未登录");
        }
        Optional<DashboardPreviewRegistry.PreviewSession> session = previewRegistry.get(
                workflowId, user.getId(), chatId, token);
        if (session.isEmpty()) {
            return error(404, "预览不存在、已过期或无权访问");
        }
        DashboardSpec spec = session.get().validation().getSpec();
        String path = packageService.previewContentPath(spec);
        String content = session.get().previewFiles().get(path);
        if (content == null) {
            return error(404, "预览内容不存在或未列入编译清单");
        }
        return response(content, mediaType(path));
    }

    @GetMapping("/{workflowId}/dashboard-preview/files")
    public ResponseEntity<String> file(@PathVariable("workflowId") String workflowId,
                                       @RequestParam(value = "chat_id", required = false) String chatId,
                                       @RequestParam(value = "token", required = false) String token,
                                       @RequestParam("path") String path) {
        User user = getSessionUser();
        if (user == null) {
            return error(401, "当前用户未登录");
        }
        Optional<DashboardPreviewRegistry.PreviewSession> session = previewRegistry.get(
                workflowId, user.getId(), chatId, token);
        if (session.isEmpty()) {
            return error(404, "预览不存在、已过期或无权访问");
        }
        String content = session.get().previewFiles().get(path);
        if (content == null) {
            return error(404, "预览文件不存在或未列入编译清单");
        }
        return response(content, mediaType(path));
    }

    private MediaType mediaType(String path) {
        String normalized = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".json")) {
            return new MediaType("application", "json", StandardCharsets.UTF_8);
        }
        return new MediaType("text", "html", StandardCharsets.UTF_8);
    }

    private ResponseEntity<String> response(String content, MediaType mediaType) {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", CSP)
                .header("Referrer-Policy", "no-referrer")
                .body(content);
    }

    private ResponseEntity<String> error(int status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
    }
}
