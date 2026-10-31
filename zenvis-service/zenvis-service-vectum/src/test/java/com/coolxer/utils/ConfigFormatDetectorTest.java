package com.coolxer.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigFormatDetectorTest {

    @Test
    void yamlCommentsAndVrlAssignmentsRemainYaml() {
        String config = """
                # 学生信息 Demo 数据推送配置
                # Meta: student_info.json
                sources:
                  demo:
                    type: demo_logs
                transforms:
                  normalize:
                    type: remap
                    inputs: [demo]
                    source: |
                      . = parse_json!(.message)
                sinks:
                  out:
                    type: console
                    inputs: [normalize]
                """;

        assertThat(ConfigFormatDetector.detect(null, config)).isEqualTo("yaml");
    }

    @Test
    void detectsTomlJsonAndExplicitFormats() {
        assertThat(ConfigFormatDetector.detect(null,
                "# comment\n[sources.demo]\ntype = \"demo_logs\""))
                .isEqualTo("toml");
        assertThat(ConfigFormatDetector.detect(null, "{\"sources\":{}}"))
                .isEqualTo("json");
        assertThat(ConfigFormatDetector.detect("yml", "sources: {}"))
                .isEqualTo("yaml");
    }

    @Test
    void returnsUnknownForAmbiguousContent() {
        assertThat(ConfigFormatDetector.detect(null, "not a vector config"))
                .isEqualTo("unknown");
    }

    @Test
    void resolveUsesExplicitFormatAndRejectsInvalidOrAmbiguousValues() {
        assertThat(ConfigFormatDetector.resolve("YML", "sources: {}")).isEqualTo("yaml");
        assertThat(ConfigFormatDetector.resolve("toml", "{\"sources\":{}}")).isEqualTo("toml");
        assertThat(ConfigFormatDetector.resolve("", "{\"sources\":{}}")).isEqualTo("json");
        assertThatThrownBy(() -> ConfigFormatDetector.resolve("ini", "sources: {}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的配置格式");
        assertThatThrownBy(() -> ConfigFormatDetector.resolve(null, "not a vector config"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("显式传入 format");
    }
}
