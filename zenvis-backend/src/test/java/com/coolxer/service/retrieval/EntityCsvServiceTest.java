package com.coolxer.service.retrieval;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityCsvServiceTest {

    private MetaDataService metaDataService;
    private EntityCoreService entityCoreService;
    private EntityCsvService service;
    private DataEntity entity;
    private List<DataAttribute> attributes;

    @BeforeEach
    void setUp() {
        metaDataService = mock(MetaDataService.class);
        entityCoreService = mock(EntityCoreService.class);
        service = new EntityCsvService(metaDataService, entityCoreService, new ObjectMapper());

        entity = new DataEntity();
        entity.setName("asset_inventory");
        entity.setTableName("asset_inventory");

        DataAttribute id = attribute("asset_id", "资产编号", "String", true);
        DataAttribute name = attribute("asset_name", "资产名称", "String", true);
        DataAttribute category = attribute("category", "资产类别", "String", true);
        category.setMustCandidate(true);
        category.setMapping(Map.of("服务器", "SERVER", "网络设备", "NETWORK"));
        DataAttribute updatedAt = attribute("updated_at", "更新时间", "DateTime64(3)", true);
        DataAttribute remark = attribute("remark", "备注", "String", false);
        attributes = List.of(id, name, category, updatedAt, remark);

        when(metaDataService.getDataEntityByName("asset_inventory")).thenReturn(entity);
        when(metaDataService.getAllDataAttributeByEntity(entity)).thenReturn(attributes);
    }

    @Test
    void importsCanonicalOrChineseHeadersAndValidatesMetaMapping() throws Exception {
        String csv = "资产编号,资产名称,资产类别,更新时间,备注\r\n"
                + "AST-1,核心服务器,SERVER,2026-08-07 20:30:00.123,\"生产,核心\"\r\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "assets.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        EntityCsvService.ImportResult result = service.importCsv("asset_inventory", file);

        assertThat(result.imported()).isEqualTo(1);
        verify(entityCoreService).add("asset_inventory", Map.of(
                "asset_id", "AST-1",
                "asset_name", "核心服务器",
                "category", "SERVER",
                "updated_at", "2026-08-07 20:30:00.123",
                "remark", "生产,核心"
        ));
    }

    @Test
    void rejectsMissingRequiredHeaderInvalidTypeAndUnknownCandidate() {
        String missing = "asset_id,asset_name,category\nAST-1,服务器,SERVER\n";
        assertThatThrownBy(() -> service.parseAndValidate(
                missing.getBytes(StandardCharsets.UTF_8), attributes))
                .hasMessageContaining("updated_at");

        String badDate = "asset_id,asset_name,category,updated_at\nAST-1,服务器,SERVER,not-a-date\n";
        assertThatThrownBy(() -> service.parseAndValidate(
                badDate.getBytes(StandardCharsets.UTF_8), attributes))
                .hasMessageContaining("DateTime64(3)");

        String badCandidate = "asset_id,asset_name,category,updated_at\n"
                + "AST-1,服务器,UNKNOWN,2026-08-07 20:30:00\n";
        assertThatThrownBy(() -> service.parseAndValidate(
                badCandidate.getBytes(StandardCharsets.UTF_8), attributes))
                .hasMessageContaining("mapping");
    }

    @Test
    void preservesTheMetaValueTypeForNumericCandidates() {
        DataAttribute level = attribute("level", "等级", "UInt16", false);
        level.setMustCandidate(true);
        level.setMapping(Map.of("高", 3));

        List<Map<String, Object>> records = service.parseAndValidate(
                "level\n3\n".getBytes(StandardCharsets.UTF_8), List.of(level));

        assertThat(records).containsExactly(Map.of("level", 3));
    }

    @Test
    void generatesRoundTripTemplateAndEscapedExport() {
        String template = new String(service.importTemplate("asset_inventory"), StandardCharsets.UTF_8);
        assertThat(template).startsWith("\ufeffasset_id,asset_name,category,updated_at,remark\r\n");

        when(entityCoreService.getPageList(
                org.mockito.ArgumentMatchers.eq("asset_inventory"), anyMap()))
                .thenReturn(new PageRowsVo<>(List.of(Map.of(
                        "asset_id", "AST-1",
                        "asset_name", "核心服务器",
                        "category", "SERVER",
                        "updated_at", "2026-08-07 20:30:00.123",
                        "remark", "生产,核心"
                )), 1));

        EntityCsvService.ExportResult export = service.exportCsv("asset_inventory", Map.of());
        String csv = new String(export.content(), StandardCharsets.UTF_8);
        assertThat(export.exported()).isEqualTo(1);
        assertThat(export.truncated()).isFalse();
        assertThat(csv).contains("AST-1,核心服务器,SERVER,2026-08-07 20:30:00.123,\"生产,核心\"");
    }

    private DataAttribute attribute(
            String name,
            String label,
            String type,
            boolean required) {
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity("asset_inventory");
        attribute.setName(name);
        attribute.setLabel(label);
        attribute.setColumnName(name);
        attribute.setColumnType(type);
        attribute.setRequired(required);
        return attribute;
    }
}
