package com.coolxer.controller.config;

import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.config.vo.ConfigPathOperationResult;
import com.coolxer.model.config.vo.ConfigVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.config.MetaConfigValidationService;
import com.coolxer.service.retrieval.impl.MetaDataServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigValidationMcpToolTest {

    private final ConfigValidationMcpTool tool = new ConfigValidationMcpTool(new FakeConfigService());

    @TempDir
    Path configRoot;

    @Test
    void invalidJsonFailsValidation() {
        ConfigValidationMcpTool.ConfigValidationResult result =
                tool.validate("system", "system-info.json", "{\"displayName\":");

        assertThat(result.passed()).isFalse();
        assertThat(result.blocked()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("JSON 解析失败"));
    }

    @Test
    void availableJsonSchemaIsApplied() {
        ConfigValidationMcpTool.ConfigValidationResult result =
                tool.validate("system", "system-info.json", "{\"enabled\":true}");

        assertThat(result.passed()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("displayName"));
    }

    @Test
    void supportedFormatsReceiveStructuralValidation() {
        assertThat(tool.validate("system", "settings.properties", "name=ZenVis").passed()).isTrue();
        assertThat(tool.validate("system", "items.csv", "name,value\none,1").passed()).isTrue();
        assertThat(tool.validate("system", "layout.xml", "<layout><title>ZenVis</title></layout>").passed()).isTrue();
        assertThat(tool.validate("system", "runtime.conf", "enabled=true").passed()).isTrue();
        assertThat(tool.validate("system", "notes.txt", "configuration note").passed()).isTrue();
    }

    @Test
    void xmlExternalEntityIsRejected() {
        ConfigValidationMcpTool.ConfigValidationResult result = tool.validate(
                "system",
                "layout.xml",
                "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><foo>&xxe;</foo>"
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("XML 解析失败"));
    }

    @Test
    void inconsistentCsvColumnsFailValidation() {
        ConfigValidationMcpTool.ConfigValidationResult result =
                tool.validate("system", "items.csv", "name,value\none,1,extra");

        assertThat(result.passed()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("列数"));
    }

    @Test
    void unsupportedFormatIsBlocked() {
        ConfigValidationMcpTool.ConfigValidationResult result =
                tool.validate("system", "settings.yaml", "displayName: ZenVis");

        assertThat(result.passed()).isFalse();
        assertThat(result.blocked()).isTrue();
        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning).contains("专项 MCP"));
    }

    @Test
    void metaValidationRunsDeepSemanticCheckWithoutWritingFiles() throws Exception {
        ConfigValidationMcpTool metaTool = metaTool();

        ConfigValidationMcpTool.ConfigValidationResult result =
                metaTool.validate("meta", "probe_event.json", validMeta());

        assertThat(result.passed()).isTrue();
        assertThat(result.candidateDigest()).hasSize(64);
        assertThat(result.validationId()).startsWith("meta-validation-");
        assertThat(result.schemaVersion()).startsWith("meta-schema@");
        assertThat(result.schemaHash()).hasSize(64);
        assertThat(result.normalizedText()).contains("\"name\" : \"probe_event\"");
        assertThat(Files.list(configRoot.resolve("meta_config")).toList()).isEmpty();
    }

    @Test
    void metaValidationReturnsStructuredDiagnosticsInsteadOfOnlyFirstException()
            throws Exception {
        ConfigValidationMcpTool metaTool = metaTool();
        String invalid = validMeta()
                .replace("\"column_type\"", "\"columnType\"")
                .replace("[ \"equal\" ]", "[ \"unknown\" ]");

        ConfigValidationMcpTool.ConfigValidationResult result =
                metaTool.validate("meta", "probe_event.json", invalid);

        assertThat(result.passed()).isFalse();
        assertThat(result.diagnostics())
                .extracting(MetaConfigValidationService.Diagnostic::code)
                .contains("META_UNKNOWN_OPERATOR");
        assertThat(result.repairs())
                .anySatisfy(repair -> assertThat(repair.path()).contains("columnType"));
        assertThat(result.normalizedText()).contains("\"column_type\"");
    }

    private ConfigValidationMcpTool metaTool() throws Exception {
        Path metaDir = configRoot.resolve("meta_config");
        Files.createDirectories(metaDir);
        ConfigService configService = mock(ConfigService.class);
        when(configService.configPath("meta")).thenReturn(metaDir.toString());
        ConfigValidationMcpTool metaTool = new ConfigValidationMcpTool(configService);
        ReflectionTestUtils.setField(metaTool, "metaConfigValidationService",
                new MetaConfigValidationService(configService, new MetaDataServiceImpl()));
        return metaTool;
    }

    private String validMeta() {
        return """
                {
                  "entity": [{
                    "name": "probe_event",
                    "label": "探针事件",
                    "description": "探针事件描述",
                    "table_name": "zenvis.probe_event",
                    "data_source": "clickhouse",
                    "auto_create": { "engine": "MergeTree", "order_by": ["event_id"] }
                  }],
                  "attribute": [{
                    "entity": "probe_event",
                    "name": "event_id",
                    "label": "事件ID",
                    "description": "事件唯一标识",
                    "column_name": "event_id",
                    "column_type": "String",
                    "operators": [ "equal" ]
                  }],
                  "operator": [{ "name": "equal", "label": "等于" }]
                }
                """;
    }

    private static class FakeConfigService implements ConfigService {

        @Override
        public List<ConfigVo> getConfigFileTree(String type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String readFileSchema(String type, String fileName) {
            if (!"system-info.json".equals(fileName)) {
                return null;
            }
            return """
                    {
                      "type": "object",
                      "properties": {
                        "displayName": { "type": "string" },
                        "enabled": { "type": "boolean" }
                      },
                      "required": ["displayName"]
                    }
                    """;
        }

        @Override
        public String readFile(String type, String fileName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void modifyConfig(String type, ConfigDto configDto) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addFile(String type, String fileName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean renameFile(String type, String originalFile, String newFile) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConfigPathOperationResult renameEntry(String type,
                                                     String sourcePath,
                                                     String targetName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteFile(String type, String fileName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConfigPathOperationResult deleteEntry(String type,
                                                     String sourcePath,
                                                     boolean recursive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String configPath(String type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void applyConfig(String type, ConfigDto configDto) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addRootPath(String type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean ensureRootPath(String type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean fileExistsInConfigPath(String type, String fileName) {
            throw new UnsupportedOperationException();
        }
    }
}
