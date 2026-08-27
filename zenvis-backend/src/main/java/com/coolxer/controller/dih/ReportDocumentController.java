package com.coolxer.controller.dih;

import com.coolxer.controller.BaseController;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.dih.dto.ReportArchiveDto;
import com.coolxer.model.dih.dto.ReportArtifactRenameDto;
import com.coolxer.model.dih.dto.ReportDocumentSaveDto;
import com.coolxer.model.dih.vo.ReportWorkspaceVo;
import com.coolxer.service.dih.ReportDocumentService;
import com.coolxer.service.dih.ReportRevisionConflictException;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Tag(name = "报表文档")
@RestController
@RequestMapping("/api/v1/dih/chat-session/{sessionId}/report")
public class ReportDocumentController extends BaseController {

    private final ReportDocumentService reportDocumentService;

    public ReportDocumentController(ReportDocumentService reportDocumentService) {
        this.reportDocumentService = reportDocumentService;
    }

    @GetMapping
    public ResponseEntity<ResponseWrap<ReportWorkspaceVo>> workspace(
            @PathVariable Long sessionId) {
        return execute(() -> reportDocumentService.workspace(sessionId, getSessionUser()));
    }

    @GetMapping("/materials")
    public ResponseEntity<ResponseWrap<List<Map<String, Object>>>> materials(
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ResponseWrap.success(
                reportDocumentService.materials(sessionId, getSessionUser())));
    }

    @PostMapping("/save")
    public ResponseEntity<ResponseWrap<ReportWorkspaceVo>> save(
            @PathVariable Long sessionId,
            @RequestBody ReportDocumentSaveDto request) {
        return execute(() -> reportDocumentService.save(
                sessionId, request, getSessionUser()));
    }

    @PostMapping("/archive")
    public ResponseEntity<ResponseWrap<ReportWorkspaceVo>> archive(
            @PathVariable Long sessionId,
            @RequestBody ReportArchiveDto request) {
        return execute(() -> reportDocumentService.archive(
                sessionId, request, getSessionUser()));
    }

    @PostMapping("/artifacts/{artifactId}/restore")
    public ResponseEntity<ResponseWrap<ReportWorkspaceVo>> restore(
            @PathVariable Long sessionId,
            @PathVariable String artifactId,
            @RequestBody ReportArchiveDto request) {
        return execute(() -> reportDocumentService.restore(
                sessionId, artifactId, request, getSessionUser()));
    }

    @PostMapping("/artifacts/{artifactId}/rename")
    public ResponseEntity<ResponseWrap<ReportWorkspaceVo>> rename(
            @PathVariable Long sessionId,
            @PathVariable String artifactId,
            @RequestBody ReportArtifactRenameDto request) {
        return execute(() -> reportDocumentService.renameArtifact(
                sessionId, artifactId, request, getSessionUser()));
    }

    @DeleteMapping("/artifacts/{artifactId}")
    public ResponseEntity<ResponseWrap<ReportWorkspaceVo>> delete(
            @PathVariable Long sessionId,
            @PathVariable String artifactId,
            @RequestParam("base_revision") Long baseRevision) {
        return execute(() -> reportDocumentService.deleteArtifact(
                sessionId, artifactId, baseRevision, getSessionUser()));
    }

    private ResponseEntity<ResponseWrap<ReportWorkspaceVo>> execute(
            Supplier<ReportWorkspaceVo> action) {
        try {
            return ResponseEntity.ok(ResponseWrap.success(action.get()));
        } catch (ReportRevisionConflictException e) {
            ReportWorkspaceVo conflict = new ReportWorkspaceVo(
                    e.getCurrentDocument(), null, null, null);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ResponseWrap<>(
                            HttpStatus.CONFLICT.value(), e.getMessage(), conflict));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ResponseWrap<>(
                            HttpStatus.BAD_REQUEST.value(), e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseWrap.fail(e));
        }
    }
}
