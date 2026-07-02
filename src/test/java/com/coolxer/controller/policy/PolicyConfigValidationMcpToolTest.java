package com.coolxer.controller.policy;

import com.coolxer.model.policy.dto.ConfigDto;
import com.coolxer.model.policy.vo.ConfigVo;
import com.coolxer.service.config.ConfigService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyConfigValidationMcpToolTest {

    private final PolicyConfigValidationMcpTool tool = new PolicyConfigValidationMcpTool(new FakeConfigService());

    @Test
    void invalidJsonFailsValidation() {
        PolicyConfigValidationMcpTool.PolicyValidationResult result =
                tool.validate("rating", "rating_rule.json", "{\"name\":");

        assertThat(result.passed()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("JSON 解析失败"));
    }

    @Test
    void missingRequiredRatingFieldFailsValidation() {
        PolicyConfigValidationMcpTool.PolicyValidationResult result =
                tool.validate("rating", "rating_rule.json", "[{\"name\":\"高危评分\"}]");

        assertThat(result.passed()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("app_id"));
    }

    @Test
    void checkerSimulationMatchesNonEmptyPolicyArray() {
        String text = """
                {
                  "runtimeConfig": {
                    "process": ["frpc"]
                  }
                }
                """;

        PolicyConfigValidationMcpTool.PolicySimulationResult result =
                tool.simulate("checker", "host.json", text, Map.of());

        assertThat(result.passed()).isTrue();
        assertThat(result.matchedRules()).anySatisfy(rule -> assertThat(rule).contains("runtimeConfig.process"));
    }

    @Test
    void ratingSimulationMatchesSampleTag() {
        String text = """
                [
                  {
                    "name": "WebShell 高危评分",
                    "app_id": 1,
                    "code": "webshell-risk",
                    "computation_period": 60,
                    "grade_rules": {
                      "低风险": {"from": 0, "to": 30},
                      "中风险": {"from": 31, "to": 70},
                      "高风险": {"from": 71, "to": null}
                    },
                    "status": 1,
                    "score_rules": [
                      {"tag": "webshell", "basic_score": 80, "superposition_score": 10, "top_score": 100}
                    ]
                  }
                ]
                """;

        PolicyConfigValidationMcpTool.PolicySimulationResult result =
                tool.simulate("rating", "rating_rule.json", text, Map.of("tags", List.of("webshell")));

        assertThat(result.passed()).isTrue();
        assertThat(result.matchedRules()).anySatisfy(rule -> assertThat(rule).contains("webshell"));
    }

    @Test
    void punishSimulationMatchesSampleTagAndSource() {
        String text = """
                [
                  {
                    "tag": "webshell",
                    "sourceRegex": ".*one\\\\.jsp.*",
                    "action": {
                      "type": 1,
                      "title": "阻断 WebShell 来源",
                      "message": "命中高危 WebShell 行为，建议阻断来源。"
                    }
                  }
                ]
                """;

        PolicyConfigValidationMcpTool.PolicySimulationResult result =
                tool.simulate("punish", "webshell-block.json", text,
                        Map.of("tag", "webshell", "source", "/shell/one.jsp"));

        assertThat(result.passed()).isTrue();
        assertThat(result.matchedRules()).anySatisfy(rule -> assertThat(rule).contains("阻断 WebShell 来源"));
    }

    private static class FakeConfigService implements ConfigService {

        @Override
        public List<ConfigVo> getConfigFileTree(String type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String readFileSchema(String type, String fileName) {
            return switch (type) {
                case "checker" -> """
                        {
                          "type": "object",
                          "properties": {
                            "runtimeConfig": {
                              "type": "object",
                              "properties": {
                                "process": {
                                  "type": "array",
                                  "items": { "type": "string" }
                                }
                              }
                            }
                          }
                        }
                        """;
                case "rating" -> """
                        {
                          "type": "array",
                          "items": {
                            "type": "object",
                            "properties": {
                              "name": { "type": "string" },
                              "app_id": { "type": "integer" },
                              "code": { "type": "string" },
                              "computation_period": { "type": "integer" },
                              "grade_rules": {
                                "type": "object",
                                "properties": {
                                  "低风险": {
                                    "type": "object",
                                    "properties": {
                                      "from": { "type": "integer" },
                                      "to": { "oneOf": [{ "type": "integer" }, { "type": "null" }] }
                                    },
                                    "required": ["from", "to"]
                                  },
                                  "中风险": {
                                    "type": "object",
                                    "properties": {
                                      "from": { "type": "integer" },
                                      "to": { "oneOf": [{ "type": "integer" }, { "type": "null" }] }
                                    },
                                    "required": ["from", "to"]
                                  },
                                  "高风险": {
                                    "type": "object",
                                    "properties": {
                                      "from": { "type": "integer" },
                                      "to": { "oneOf": [{ "type": "integer" }, { "type": "null" }] }
                                    },
                                    "required": ["from", "to"]
                                  }
                                },
                                "required": ["低风险", "中风险", "高风险"]
                              },
                              "status": { "type": "integer" },
                              "score_rules": {
                                "type": "array",
                                "items": {
                                  "type": "object",
                                  "properties": {
                                    "tag": { "type": "string" },
                                    "basic_score": { "type": "integer" },
                                    "superposition_score": { "type": "integer" },
                                    "top_score": { "type": "integer" }
                                  },
                                  "required": ["tag", "basic_score", "superposition_score", "top_score"]
                                }
                              }
                            },
                            "required": ["name", "app_id", "code", "computation_period", "grade_rules", "status", "score_rules"]
                          }
                        }
                        """;
                case "punish" -> """
                        {
                          "type": "array",
                          "items": {
                            "type": "object",
                            "properties": {
                              "tag": { "type": "string" },
                              "sourceRegex": { "type": "string" },
                              "action": {
                                "type": "object",
                                "properties": {
                                  "type": { "type": "integer" },
                                  "title": { "type": "string" },
                                  "message": { "type": "string" }
                                },
                                "required": ["type", "title", "message"]
                              }
                            },
                            "required": ["tag", "sourceRegex", "action"]
                          }
                        }
                        """;
                default -> null;
            };
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
        public void applyPolicy(String type, ConfigDto configDto) {
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
