package com.coolxer.service.system;

import com.coolxer.model.system.validation.PushTaskDiagnosticVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PushTaskConfigRulesTest {

    private final PushTaskConfigRules rules = new PushTaskConfigRules();

    @Test
    void acceptsCompleteYamlWithMetaFieldsAndRequiredTests() {
        PushTaskConfigRules.StaticValidation result = rules.validate(
                validConfig(), "yaml", metaContent());

        assertThat(result.passed())
                .as(result.diagnostics().toString())
                .isTrue();
        assertThat(result.testsRequired()).isTrue();
        assertThat(result.format()).isEqualTo("yaml");
        assertThat(codes(result)).doesNotContain(
                "PUSH_META_TABLE_MISMATCH",
                "PUSH_META_FIELD_UNKNOWN",
                "PUSH_TESTS_REQUIRED",
                "PUSH_TEST_VALID_CASE_MISSING",
                "PUSH_TEST_ERROR_CASE_MISSING");
    }

    @Test
    void rejectsArrayComponentsAndUnknownBatchSize() {
        PushTaskConfigRules.StaticValidation arrayResult = rules.validate("""
                sources:
                  src:
                    type: demo_logs
                sinks:
                  - type: console
                    inputs: [src]
                """, "yaml", "");

        assertThat(arrayResult.passed()).isFalse();
        assertThat(codes(arrayResult)).contains("PUSH_COMPONENT_MAP_REQUIRED");

        PushTaskConfigRules.StaticValidation batchResult = rules.validate(
                validConfig().replace("    database: zenvis",
                        "    batch_size: 1000\n    database: zenvis"),
                "yaml", metaContent());
        assertThat(batchResult.passed()).isFalse();
        assertThat(codes(batchResult)).contains("PUSH_CLICKHOUSE_UNKNOWN_FIELD");
    }

    @Test
    void yamlCommentsAndVrlAssignmentsDoNotTriggerTomlDetection() {
        String config = "# 学生信息 Demo 数据推送配置\n"
                + "# Meta: student_info.json\n"
                + validConfig();

        PushTaskConfigRules.StaticValidation result = rules.validate(config, "", metaContent());

        assertThat(result.passed())
                .as(result.diagnostics().toString())
                .isTrue();
        assertThat(result.format()).isEqualTo("yaml");
        assertThat(codes(result)).doesNotContain(
                "PUSH_CONFIG_SYNTAX", "PUSH_CONFIG_FORMAT_AMBIGUOUS");
    }

    @Test
    void unsupportedOrAmbiguousFormatFailsClosed() {
        PushTaskConfigRules.StaticValidation unsupported =
                rules.validate(validConfig(), "xml", metaContent());
        assertThat(unsupported.passed()).isFalse();
        assertThat(codes(unsupported)).contains("PUSH_CONFIG_FORMAT_UNSUPPORTED");

        PushTaskConfigRules.StaticValidation ambiguous = rules.validate(
                "not a vector config\nvalue", "", metaContent());
        assertThat(ambiguous.passed()).isFalse();
        assertThat(ambiguous.format()).isEmpty();
        assertThat(codes(ambiguous)).contains("PUSH_CONFIG_FORMAT_AMBIGUOUS");
    }

    @Test
    void validatesInputExistenceSelfReferenceAndCycles() {
        PushTaskConfigRules.StaticValidation missing = rules.validate("""
                sources:
                  src:
                    type: demo_logs
                transforms:
                  shape:
                    type: remap
                    source: '.event_id = .id'
                sinks:
                  out:
                    type: console
                    inputs: [shape]
                """, "yaml", "");
        assertThat(codes(missing)).contains("PUSH_INPUT_REQUIRED");

        PushTaskConfigRules.StaticValidation self = rules.validate("""
                sources:
                  src:
                    type: demo_logs
                transforms:
                  shape:
                    type: remap
                    inputs: [shape]
                    source: '.event_id = .id'
                sinks:
                  out:
                    type: console
                    inputs: [shape]
                """, "yaml", "");
        assertThat(codes(self)).contains("PUSH_INPUT_SELF_REFERENCE");

        PushTaskConfigRules.StaticValidation cycle = rules.validate("""
                sources:
                  src:
                    type: demo_logs
                transforms:
                  first:
                    type: remap
                    inputs: [second]
                    source: '.event_id = .id'
                  second:
                    type: remap
                    inputs: [first]
                    source: '.user = .name'
                sinks:
                  out:
                    type: console
                    inputs: [first]
                """, "yaml", "");
        assertThat(codes(cycle)).contains("PUSH_INPUT_CYCLE");
    }

    @Test
    void enforcesMetaTableDatabaseAndAttributeContract() {
        PushTaskConfigRules.StaticValidation table = rules.validate(
                validConfig().replace("table: msg_user_event", "table: missing_table"),
                "yaml", metaContent());
        assertThat(codes(table)).contains("PUSH_META_TABLE_MISMATCH");

        PushTaskConfigRules.StaticValidation database = rules.validate(
                validConfig().replace("database: zenvis", "database: other"),
                "yaml", metaContent());
        assertThat(codes(database)).contains("PUSH_CLICKHOUSE_DATABASE_INVALID");

        PushTaskConfigRules.StaticValidation field = rules.validate(
                validConfig().replace(".user = .username", ".unknown_field = .username"),
                "yaml", metaContent());
        assertThat(codes(field)).contains("PUSH_META_FIELD_UNKNOWN");
    }

    @Test
    void rejectsReservedFieldsAndHardCodedSecretsIncludingSecretDefaults() {
        PushTaskConfigRules.StaticValidation reserved = rules.validate(
                validConfig().replace(".user = .username", ".zenvis_id = .id"),
                "yaml", metaContent());
        assertThat(codes(reserved)).contains("PUSH_RESERVED_FIELD_MAPPING");

        PushTaskConfigRules.StaticValidation literal = rules.validate(
                validConfig().replace("${CLICKHOUSE_PASSWORD}", "plain-password"),
                "yaml", metaContent());
        assertThat(codes(literal)).contains("PUSH_HARD_CODED_SECRET");

        PushTaskConfigRules.StaticValidation fallback = rules.validate(
                validConfig().replace("${CLICKHOUSE_PASSWORD}",
                        "${CLICKHOUSE_PASSWORD:-plain-password}"),
                "yaml", metaContent());
        assertThat(codes(fallback)).contains("PUSH_HARD_CODED_SECRET");

        PushTaskConfigRules.StaticValidation shellStyle = rules.validate(
                validConfig().replace("${CLICKHOUSE_PASSWORD}",
                        "$CLICKHOUSE_PASSWORD"),
                "yaml", metaContent());
        assertThat(codes(shellStyle)).contains("PUSH_HARD_CODED_SECRET");

        PushTaskConfigRules.StaticValidation safeEnvironment = rules.validate(
                validConfig().replace("${CLICKHOUSE_PASSWORD}",
                        "${CLICKHOUSE_PASSWORD:-}"),
                "yaml", metaContent());
        assertThat(codes(safeEnvironment)).doesNotContain("PUSH_HARD_CODED_SECRET");
    }

    @Test
    void requiresValidAndErrorTestsAndTargetFieldCoverage() {
        PushTaskConfigRules.StaticValidation absent = rules.validate(
                withoutTests(validConfig()), "yaml", metaContent());
        assertThat(codes(absent)).contains("PUSH_TESTS_REQUIRED");

        PushTaskConfigRules.StaticValidation onlyNormal = rules.validate(
                validConfig().replace("  - name: 异常输入", "  - name: 第二个合法输入"),
                "yaml", metaContent());
        assertThat(codes(onlyNormal)).contains("PUSH_TEST_ERROR_CASE_MISSING");

        PushTaskConfigRules.StaticValidation onlyError = rules.validate(
                withoutTests(validConfig()) + """
                        tests:
                          - name: 异常输入
                            inputs: []
                            outputs: []
                        """, "yaml", metaContent());
        assertThat(codes(onlyError)).contains("PUSH_TEST_VALID_CASE_MISSING");

        PushTaskConfigRules.StaticValidation uncovered = rules.validate(
                validConfig()
                        .replace(".event_id", ".other_id")
                        .replace(".user", ".other_user")
                        .replace("fields: [event_id, user]",
                                "fields: [other_id, other_user]"),
                "yaml", metaContent());
        assertThat(codes(uncovered)).contains("PUSH_TEST_TARGET_FIELDS_UNCOVERED");
    }

    @Test
    void pureSourceToConsoleDoesNotRequireTests() {
        PushTaskConfigRules.StaticValidation result = rules.validate("""
                sources:
                  src:
                    type: demo_logs
                sinks:
                  out:
                    type: console
                    inputs: [src]
                """, "yaml", "");

        assertThat(result.passed()).isTrue();
        assertThat(result.testsRequired()).isFalse();
    }

    @Test
    void parsesTomlWithoutChangingApplicationAntlrRuntime() {
        PushTaskConfigRules.StaticValidation result = rules.validate("""
                [sources.src]
                type = "demo_logs"

                [sinks.out]
                type = "console"
                inputs = ["src"]
                """, "toml", "");

        assertThat(result.passed())
                .as(result.diagnostics().toString())
                .isTrue();
        assertThat(result.format()).isEqualTo("toml");
    }

    private List<String> codes(PushTaskConfigRules.StaticValidation result) {
        return result.diagnostics().stream().map(PushTaskDiagnosticVo::getCode).toList();
    }

    private String withoutTests(String config) {
        int tests = config.indexOf("\ntests:");
        return tests < 0 ? config : config.substring(0, tests) + "\n";
    }

    private String metaContent() {
        return """
                {"entity":[{"name":"user_event","table_name":"zenvis.msg_user_event"}],
                 "attribute":[
                   {"entity":"user_event","name":"event_id","column_name":"event_id"},
                   {"entity":"user_event","name":"user","column_name":"user"}]}
                """;
    }

    private String validConfig() {
        return """
                sources:
                  src:
                    type: demo_logs
                transforms:
                  shape:
                    type: remap
                    inputs: [src]
                    source: |
                      .event_id = .id
                      .user = .username
                sinks:
                  ch:
                    type: clickhouse
                    inputs: [shape]
                    endpoint: ${CLICKHOUSE_ENDPOINT:-http://clickhouse:8123}
                    database: zenvis
                    table: msg_user_event
                    auth:
                      strategy: basic
                      user: ${CLICKHOUSE_USER:-default}
                      password: ${CLICKHOUSE_PASSWORD}
                tests:
                  - name: 合法输入
                    inputs: []
                    outputs: []
                    fields: [event_id, user]
                  - name: 异常输入
                    inputs: []
                    outputs: []
                    fields: [event_id, user]
                """;
    }
}
