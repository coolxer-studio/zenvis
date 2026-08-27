package com.coolxer.controller.retrieval;

import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.service.retrieval.EntityCsvService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityCoreControllerCsvTest {

    private EntityCoreController controller;
    private EntityCsvService csvService;

    @BeforeEach
    void setUp() {
        controller = new EntityCoreController();
        csvService = mock(EntityCsvService.class);
        ReflectionTestUtils.setField(controller, "entityCsvService", csvService);
    }

    @Test
    void exposesGenericImportResultForAnyEntity() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "assets.csv", "text/csv", "id\n1\n".getBytes(StandardCharsets.UTF_8));
        when(csvService.importCsv("asset_inventory", file))
                .thenReturn(new EntityCsvService.ImportResult(1, "assets.csv"));

        ResponseWrap<?> response = controller.importCsv("asset_inventory", file);

        assertThat(response.getStatus()).isZero();
        assertThat(response.getMsg()).contains("1");
        assertThat(response.getData()).isEqualTo(Map.of(
                "value", "assets.csv",
                "filename", "assets.csv",
                "imported", 1
        ));
    }

    @Test
    void templateAndExportUseDownloadHeadersAndExportMetadata() {
        byte[] template = "\ufeffid\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] export = "\ufeffid\r\n1\r\n".getBytes(StandardCharsets.UTF_8);
        when(csvService.importTemplate("asset_inventory")).thenReturn(template);
        when(csvService.exportCsv("asset_inventory", Map.of("status", "ACTIVE")))
                .thenReturn(new EntityCsvService.ExportResult(export, 1, false));

        ResponseEntity<?> templateResponse = controller.importTemplate("asset_inventory");
        ResponseEntity<?> exportResponse = controller.exportCsv(
                "asset_inventory", Map.of("status", "ACTIVE"));

        assertThat(templateResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(templateResponse.getHeaders().getFirst("Content-Disposition"))
                .contains("asset_inventory-template.csv");
        assertThat(templateResponse.getBody()).isEqualTo(template);
        assertThat(exportResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(exportResponse.getHeaders().getFirst("Content-Disposition"))
                .contains("asset_inventory.csv");
        assertThat(exportResponse.getHeaders().getFirst("X-Exported-Rows")).isEqualTo("1");
        assertThat(exportResponse.getHeaders().getFirst("X-Export-Truncated")).isEqualTo("false");
        assertThat(exportResponse.getBody()).isEqualTo(export);
    }
}
