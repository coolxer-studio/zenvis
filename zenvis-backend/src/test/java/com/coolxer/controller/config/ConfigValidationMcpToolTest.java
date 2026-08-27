package com.coolxer.controller.config;

import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.config.vo.ConfigVo;
import com.coolxer.service.config.ConfigService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigValidationMcpToolTest {

    private final ConfigValidationMcpTool tool = new ConfigValidationMcpTool(new FakeConfigService());

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
        public boolean deleteFile(String type, String fileName) {
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
