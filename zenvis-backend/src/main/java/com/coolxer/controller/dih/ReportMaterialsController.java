package com.coolxer.controller.dih;

import com.coolxer.controller.BaseController;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.service.dih.ReportDocumentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "报表图表库")
@RestController
@RequestMapping("/api/v1/dih/report")
public class ReportMaterialsController extends BaseController {

    private final ReportDocumentService reportDocumentService;

    public ReportMaterialsController(ReportDocumentService reportDocumentService) {
        this.reportDocumentService = reportDocumentService;
    }

    @GetMapping("/chart-library")
    public ResponseEntity<ResponseWrap<List<Map<String, Object>>>> chartLibrary() {
        return ResponseEntity.ok(ResponseWrap.success(
                reportDocumentService.chartLibrary(getSessionUser())));
    }

    @GetMapping("/materials")
    public ResponseEntity<ResponseWrap<List<Map<String, Object>>>> materials() {
        return ResponseEntity.ok(ResponseWrap.success(
                reportDocumentService.chartLibrary(getSessionUser())));
    }
}
