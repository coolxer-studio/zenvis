package com.coolxer.controller.dih;

import com.coolxer.controller.BaseController;
import com.coolxer.service.dih.ReportExportService;
import com.coolxer.service.dih.ReportExportService.ExportedReport;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/dih/chat-session/{sessionId}/report")
public class ReportExportController extends BaseController {

    private final ReportExportService reportExportService;

    public ReportExportController(ReportExportService reportExportService) {
        this.reportExportService = reportExportService;
    }

    @GetMapping("/export/{format}")
    public ResponseEntity<byte[]> export(
            @PathVariable Long sessionId,
            @PathVariable String format) {
        ExportedReport report = reportExportService.export(
                sessionId, format, getSessionUser());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(report.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(report.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(report.content());
    }
}
