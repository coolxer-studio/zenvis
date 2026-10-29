package com.coolxer.service.config;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.service.retrieval.impl.MetaDataServiceImpl;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetaConfigValidationServiceTest {

    @TempDir
    Path configRoot;

    private Path metaDir;
    private MetaConfigValidationService service;

    @BeforeEach
    void setUp() throws Exception {
        metaDir = configRoot.resolve("meta_config");
        Files.createDirectories(metaDir);
        ConfigService configService = mock(ConfigService.class);
        when(configService.configPath("meta")).thenReturn(metaDir.toString());
        service = new MetaConfigValidationService(configService, new MetaDataServiceImpl());
    }

    @Test
    void validNewMetaPassesAndReturnsStableDigest() {
        String candidate = json(validRoot("probe_event"));

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", candidate);

        assertThat(result.passed()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.candidateDigest()).hasSize(64);
        assertThat(result.normalizedText()).contains("\"column_name\" : \"event_id\"");
    }

    @Test
    void overwriteValidationExcludesOldTargetFile() throws Exception {
        Files.writeString(metaDir.resolve("probe_event.json"), "{\"broken\":true}");

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(validRoot("probe_event")));

        assertThat(result.passed()).isTrue();
    }

    @Test
    void jsonSyntaxErrorIsReturnedAsDiagnostic() {
        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", "{\"entity\":[");

        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostics())
                .extracting(MetaConfigValidationService.Diagnostic::code)
                .contains("META_JSON_SYNTAX");
    }

    @Test
    void missingTopLevelArrayFailsEvenWhenNormalized() throws Exception {
        ObjectNode root = validRoot("probe_event");
        root.remove("operator");

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(root));

        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostics())
                .extracting(MetaConfigValidationService.Diagnostic::code)
                .contains("META_TOP_LEVEL_FIELD_MISSING");
    }

    @Test
    void reservedBuiltInAttributesAreRejected() throws Exception {
        ObjectNode root = validRoot("probe_event");
        ArrayNode attributes = (ArrayNode) root.get("attribute");
        attributes.add(attribute("probe_event", "record_key", "zenvis_id", "String", 10));
        attributes.add(attribute("probe_event", "zenvis_insert_time", "zenvis_insert_time",
                "DateTime64(3)", 11));

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(root));

        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostics())
                .extracting(MetaConfigValidationService.Diagnostic::code)
                .contains("META_RESERVED_ATTRIBUTE");
    }

    @Test
    void missingRequiredValuesAndUnknownEntityAreReportedTogether() throws Exception {
        ObjectNode root = validRoot("probe_event");
        ObjectNode attribute = (ObjectNode) root.get("attribute").get(0);
        attribute.remove("description");
        attribute.put("entity", "missing_entity");
        attribute.putArray("operators");

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(root));

        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostics())
                .extracting(MetaConfigValidationService.Diagnostic::code)
                .contains(
                        "META_REQUIRED_FIELD",
                        "META_UNKNOWN_ENTITY");
    }

    @Test
    void illegalFieldJsonTypesAreReportedWithStablePaths() throws Exception {
        ObjectNode root = validRoot("probe_event");
        ObjectNode attribute = (ObjectNode) root.get("attribute").get(0);
        attribute.put("id", "not-an-integer");
        attribute.put("display_selected", "yes");

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(root));

        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostics())
                .filteredOn(item -> "META_FIELD_TYPE_INVALID".equals(item.code()))
                .extracting(MetaConfigValidationService.Diagnostic::path)
                .contains("$.attribute[0].id", "$.attribute[0].display_selected");
    }

    @Test
    void wrongDatabasePrefixIsDeterministicallyNormalizedToZenvis() throws Exception {
        ObjectNode root = validRoot("probe_event");
        ((ObjectNode) root.get("entity").get(0))
                .put("table_name", "legacy.probe_event");

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(root));

        assertThat(result.passed()).isTrue();
        assertThat(result.normalizedText())
                .contains("\"table_name\" : \"zenvis.probe_event\"");
        assertThat(result.repairs())
                .anySatisfy(repair -> assertThat(repair.path())
                        .isEqualTo("$.entity[0].table_name"));
    }

    @Test
    void nullableTemporalTypeInfersSearchTypeAndDateOperators() throws Exception {
        ObjectNode root = validRoot("probe_event");
        ObjectNode attribute = (ObjectNode) root.get("attribute").get(0);
        attribute.put("column_type", "Nullable(DateTime64(3))");
        attribute.remove("operators");

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(root));

        assertThat(result.passed()).isTrue();
        assertThat(result.normalizedText())
                .contains("\"search_type\" : \"datetime\"")
                .contains("\"between\"");
    }

    @Test
    void missingReferencedOperatorIsDeterministicallyAdded() throws Exception {
        ObjectNode root = validRoot("probe_event");
        root.putArray("operator");

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(root));

        assertThat(result.passed()).isTrue();
        assertThat(result.repairs())
                .anySatisfy(repair -> assertThat(repair.reason()).contains("默认操作符"));
        assertThat(result.normalizedText()).contains("\"name\" : \"equal\"");
    }

    @Test
    void illegalColumnTypeAndUnknownOperatorFail() throws Exception {
        ObjectNode root = validRoot("probe_event");
        ObjectNode attribute = (ObjectNode) root.get("attribute").get(0);
        attribute.put("column_type", "NotARealType");
        attribute.putArray("operators").add("custom_unknown");

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(root));

        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostics())
                .extracting(MetaConfigValidationService.Diagnostic::code)
                .contains("META_COLUMN_TYPE_INVALID", "META_UNKNOWN_OPERATOR");
    }

    @Test
    void duplicateNonZeroIdsFail() throws Exception {
        ObjectNode root = validRoot("probe_event");
        ArrayNode attributes = (ArrayNode) root.get("attribute");
        ((ObjectNode) attributes.get(0)).put("id", 7);
        attributes.add(attribute("probe_event", "event_name", "event_name", "String", 7));

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(root));

        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostics())
                .extracting(MetaConfigValidationService.Diagnostic::code)
                .contains("META_DUPLICATE_ATTRIBUTE_ID");
    }

    @Test
    void idsMustFitNonNegativeIntAndSearchTypeMustBeSupported() throws Exception {
        ObjectNode root = validRoot("probe_event");
        ((ObjectNode) root.get("entity").get(0)).put("id", 2147483648L);
        ObjectNode attribute = (ObjectNode) root.get("attribute").get(0);
        attribute.put("id", -1);
        attribute.put("search_type", "timestamp");

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(root));

        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostics())
                .extracting(MetaConfigValidationService.Diagnostic::code)
                .contains("META_ID_INVALID", "META_SEARCH_TYPE_INVALID");
    }

    @Test
    void crossFileEntityAndTableConflictsAreReported() throws Exception {
        ObjectNode existing = validRoot("legacy_event");
        ((ObjectNode) existing.get("entity").get(0))
                .put("table_name", "zenvis.shared_table");
        Files.writeString(metaDir.resolve("legacy.json"), json(existing));
        ObjectNode candidate = validRoot("probe_event");
        ((ObjectNode) candidate.get("entity").get(0))
                .put("table_name", "zenvis.shared_table");

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(candidate));
        assertThat(result.diagnostics())
                .extracting(MetaConfigValidationService.Diagnostic::code)
                .contains("META_CROSS_FILE_TABLE_CONFLICT");

        ((ObjectNode) candidate.get("entity").get(0))
                .put("name", "legacy_event")
                .put("table_name", "zenvis.probe_event");
        ((ObjectNode) candidate.get("attribute").get(0)).put("entity", "legacy_event");
        result = service.validate("probe_event.json", json(candidate));
        assertThat(result.diagnostics())
                .extracting(MetaConfigValidationService.Diagnostic::code)
                .contains("META_CROSS_FILE_ENTITY_CONFLICT");
    }

    @Test
    void invalidTtlAndLinkTemplateAreReported() throws Exception {
        ObjectNode root = validRoot("probe_event");
        ObjectNode entity = (ObjectNode) root.get("entity").get(0);
        ObjectNode autoCreate = (ObjectNode) entity.get("auto_create");
        ObjectNode ttl = autoCreate.putObject("ttl");
        ttl.put("column", "event_id");
        ttl.put("expire_after", 30);
        ttl.put("unit", "DAY");
        ObjectNode attribute = (ObjectNode) root.get("attribute").get(0);
        attribute.put("link_template", "ftp://example.test/{missing}");
        ObjectNode condition = attribute.putObject("link_condition");
        condition.put("field", "missing_field");
        condition.put("operator", "equal");
        condition.put("value", "probe");

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(root));

        assertThat(result.diagnostics())
                .extracting(MetaConfigValidationService.Diagnostic::code)
                .contains(
                        "META_TTL_COLUMN_TYPE_INVALID",
                        "META_LINK_TEMPLATE_SCHEME_INVALID",
                        "META_LINK_TEMPLATE_REFERENCE_INVALID",
                        "META_LINK_CONDITION_FIELD_INVALID");
    }

    @Test
    void scopedAliasesDoNotRewriteAttributeOperators() throws Exception {
        ObjectNode root = validRoot("probe_event");
        ObjectNode attribute = (ObjectNode) root.get("attribute").get(0);
        attribute.put("columnType", "UInt32");
        attribute.remove("column_type");

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(root));

        assertThat(result.passed()).isTrue();
        assertThat(result.normalizedText())
                .contains("\"column_type\" : \"UInt32\"")
                .contains("\"operators\"");
        assertThat(result.repairs())
                .anySatisfy(repair -> assertThat(repair.path()).contains("columnType"));
    }

    @Test
    void duplicateAliasIsRemovedWithoutLeakingUnknownField() throws Exception {
        ObjectNode root = validRoot("probe_event");
        ObjectNode attribute = (ObjectNode) root.get("attribute").get(0);
        attribute.put("columnType", "UInt32");

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(root));

        assertThat(result.passed()).isTrue();
        assertThat(result.normalizedText()).doesNotContain("columnType");
        assertThat(result.diagnostics())
                .extracting(MetaConfigValidationService.Diagnostic::code)
                .doesNotContain("META_UNKNOWN_FIELD");
    }

    @Test
    void trulyUnknownTypoStillFailsStrictCheck() throws Exception {
        ObjectNode root = validRoot("probe_event");
        ObjectNode attribute = (ObjectNode) root.get("attribute").get(0);
        attribute.set("colum_type", attribute.remove("column_type"));

        MetaConfigValidationService.ValidationResult result =
                service.validate("probe_event.json", json(root));

        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostics())
                .extracting(MetaConfigValidationService.Diagnostic::code)
                .contains("META_UNKNOWN_FIELD");
    }

    @Test
    void userEventExamplePassesAfterOperatorLabelRepair() throws Exception {
        Path example = Paths.get("..", "deploy", "open_config", "meta_config",
                "user_event.json").normalize();
        assumeTrue(Files.isRegularFile(example), "local user_event example is unavailable");

        MetaConfigValidationService.ValidationResult result =
                service.validate("user_event.json", Files.readString(example));

        assertThat(result.passed()).isTrue();
        assertThat(result.normalizedText()).contains("\"label\" : \"等于\"");
    }

    @Test
    void studentInfoExamplePassesDeepValidation() throws Exception {
        Path example = Paths.get("..", "deploy", "open_config", "meta_config",
                "student_info.json").normalize();
        assumeTrue(Files.isRegularFile(example), "local student_info example is unavailable");

        MetaConfigValidationService.ValidationResult result =
                service.validate("student_info.json", Files.readString(example));

        assertThat(result.errors()).isEmpty();
        assertThat(result.passed()).isTrue();
    }

    private ObjectNode validRoot(String entityName) {
        ObjectNode root = JacksonConfig.OBJECT_MAPPER.createObjectNode();
        ObjectNode entity = root.putArray("entity").addObject();
        entity.put("name", entityName);
        entity.put("label", "探针事件");
        entity.put("description", "用于校验的探针事件");
        entity.put("table_name", "zenvis." + entityName);
        entity.put("data_source", "clickhouse");
        ObjectNode autoCreate = entity.putObject("auto_create");
        autoCreate.put("engine", "MergeTree");
        autoCreate.putArray("order_by").add("event_id");

        root.putArray("attribute").add(attribute(
                entityName, "event_id", "event_id", "String", 1));
        ObjectNode operator = root.putArray("operator").addObject();
        operator.put("name", "equal");
        operator.put("label", "等于");
        return root;
    }

    private ObjectNode attribute(String entity,
                                 String name,
                                 String columnName,
                                 String columnType,
                                 int id) {
        ObjectNode attribute = JacksonConfig.OBJECT_MAPPER.createObjectNode();
        attribute.put("id", id);
        attribute.put("entity", entity);
        attribute.put("name", name);
        attribute.put("label", name);
        attribute.put("description", name + " 描述");
        attribute.put("column_name", columnName);
        attribute.put("column_type", columnType);
        attribute.putArray("operators").add("equal");
        return attribute;
    }

    private String json(ObjectNode root) {
        return root.toPrettyString();
    }
}
