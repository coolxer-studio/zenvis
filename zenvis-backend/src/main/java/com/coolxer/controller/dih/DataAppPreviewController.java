package com.coolxer.controller.dih;

import com.coolxer.controller.BaseController;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.service.dih.dataapp.DataAppPreviewRegistry;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Authenticated, workflow-scoped read-only preview for a validated data app.
 * The registry is the only source of files; the configuration directory is
 * never exposed directly.
 */
@RestController
@RequestMapping("/api/v1/dih/chat/workflows")
public class DataAppPreviewController extends BaseController {

    private final DataAppPreviewRegistry previewRegistry;

    public DataAppPreviewController(DataAppPreviewRegistry previewRegistry) {
        this.previewRegistry = previewRegistry;
    }

    @GetMapping("/{workflowId}/app-preview/site")
    public ResponseEntity<String> site(@PathVariable("workflowId") String workflowId,
                                       @RequestParam(value = "chat_id", required = false) String chatId,
                                       @RequestParam(value = "token", required = false) String token) {
        return previewFile(workflowId, chatId, token, "site.json");
    }

    @GetMapping("/{workflowId}/app-preview/files")
    public ResponseEntity<String> file(@PathVariable("workflowId") String workflowId,
                                       @RequestParam(value = "chat_id", required = false) String chatId,
                                       @RequestParam(value = "token", required = false) String token,
                                       @RequestParam("path") String path) {
        return previewFile(workflowId, chatId, token, path);
    }

    private ResponseEntity<String> previewFile(String workflowId,
                                               String chatId,
                                               String token,
                                               String path) {
        User user = getSessionUser();
        if (user == null) {
            return response(401, "当前用户未登录");
        }
        Optional<DataAppPreviewRegistry.PreviewSession> session = previewRegistry.get(
                workflowId, user.getId(), chatId, token);
        if (session.isEmpty()) {
            return response(404, "预览不存在、已过期或无权访问");
        }
        String content = session.get().previewFiles().get(path);
        if (content == null) {
            return response(404, "预览文件不存在或未列入编译清单");
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy",
                        "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
                                + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                                + "connect-src 'self'; frame-ancestors 'self'")
                .body(content);
    }

    private ResponseEntity<String> response(int status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .body("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
    }
}
