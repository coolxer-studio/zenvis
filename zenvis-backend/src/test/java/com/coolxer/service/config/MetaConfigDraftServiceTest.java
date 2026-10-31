package com.coolxer.service.config;

import com.coolxer.service.retrieval.impl.MetaDataServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetaConfigDraftServiceTest {

    @TempDir
    Path configRoot;

    private MetaConfigDraftService draftService;
    private MetaConfigValidationService validationService;

    @BeforeEach
    void setUp() throws Exception {
        Path metaDir = configRoot.resolve("meta_config");
        Files.createDirectories(metaDir);
        ConfigService configService = mock(ConfigService.class);
        when(configService.configPath("meta")).thenReturn(metaDir.toString());
        MetaConfigContractService contractService = new MetaConfigContractService(configService);
        draftService = new MetaConfigDraftService(contractService);
        validationService = new MetaConfigValidationService(
                configService, new MetaDataServiceImpl());
    }

    @Test
    void conciseDraftExpandsToValidMetaAndRoundTripsThroughValidator() {
        String draft = """
                {
                  "entityName": "userEvent",
                  "entityLabel": "用户事件",
                  "description": "用户行为事件",
                  "fields": [
                    {"name": "eventId", "label": "事件ID", "type": "string"},
                    {"name": "createdAt", "label": "发生时间", "type": "datetime"},
                    {"name": "payload", "label": "详情", "type": "object"}
                  ]
                }
                """;

        MetaConfigDraftService.DraftResult expanded =
                draftService.draft("user_event.json", draft);

        assertThat(expanded.passed()).isTrue();
        assertThat(expanded.entityName()).isEqualTo("user_event");
        assertThat(expanded.tableName()).isEqualTo("zenvis.user_event");
        assertThat(expanded.text())
                .contains("\"column_name\" : \"event_id\"")
                .contains("\"data_source\" : \"clickhouse\"")
                .contains("\"column_type\" : \"DateTime64(3)\"")
                .contains("\"column_type\" : \"JSON\"")
                .contains("\"display_type\" : \"json\"")
                .contains("\"label\" : \"等于\"")
                .doesNotContain("zenvis_id");

        MetaConfigValidationService.ValidationResult validation =
                validationService.validate(expanded.fileName(), expanded.text());

        assertThat(validation.passed()).isTrue();
        assertThat(validation.diagnostics()).noneMatch(item -> "ERROR".equals(item.severity()));
        assertThat(validation.candidateDigest()).hasSize(64);
    }

    @Test
    void reservedFieldsAreDroppedAndReportedAsWarnings() {
        MetaConfigDraftService.DraftResult expanded = draftService.draft(
                "probe_event.json",
                "{\"entityName\":\"probe_event\",\"fields\":[\"zenvis_id\",\"event_id\"]}");

        assertThat(expanded.passed()).isTrue();
        assertThat(expanded.text()).doesNotContain("\"zenvis_id\"");
        assertThat(expanded.diagnostics())
                .singleElement()
                .satisfies(item -> assertThat(item.code()).isEqualTo("META_DRAFT_RESERVED_FIELD"));
    }

    @Test
    void contractFingerprintIsStableForPublishedSchema() {
        MetaConfigContractService contractService = new MetaConfigContractService(
                mock(ConfigService.class));

        MetaConfigContractService.Snapshot first = contractService.snapshot();
        MetaConfigContractService.Snapshot second = contractService.snapshot();

        assertThat(first.schemaHash()).isEqualTo(second.schemaHash()).hasSize(64);
        assertThat(first.schemaVersion()).isEqualTo(second.schemaVersion());
        assertThat(first.topLevelFields())
                .containsExactlyInAnyOrder("entity", "attribute", "operator");
    }
}
