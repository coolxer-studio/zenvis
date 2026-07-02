package com.coolxer.controller.policy;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.service.config.ConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * MCP 工具：策略配置校验与轻量模拟。
 */
@Service
public class PolicyConfigValidationMcpTool {

    private static final Set<String> CHECKER_FILES = Set.of("host.json", "android.json", "ios.json", "h5.json", "wechat.json");

    private final ConfigService configService;

    public PolicyConfigValidationMcpTool(ConfigService configService) {
        this.configService = configService;
    }

    @Tool(name = "policy_config_validate", description = "校验策略 JSON 语法、根结构、必填字段和 schema 中声明的基础类型")
    public PolicyValidationResult validate(@ToolParam(description = "配置类型：checker、rating 或 punish") String type,
                                           @ToolParam(description = "配置文件名，例如 host.json、rating_rule.json、risk-block.json") String fileName,
                                           @ToolParam(description = "策略配置 JSON 文本") String text) {
        return validateInternal(type, fileName, text);
    }

    @Tool(name = "policy_config_simulate", description = "按策略类型做轻量模拟，返回是否通过、命中规则、风险提示和建议")
    public PolicySimulationResult simulate(@ToolParam(description = "配置类型：checker、rating 或 punish") String type,
                                           @ToolParam(description = "配置文件名，例如 host.json、rating_rule.json、risk-block.json") String fileName,
                                           @ToolParam(description = "策略配置 JSON 文本") String text,
                                           @ToolParam(description = "用于模拟命中的样例数据对象") Map<String, Object> sampleData) {
        PolicyValidationResult validation = validateInternal(type, fileName, text);
        List<String> matchedRules = new ArrayList<>();
        List<String> warnings = new ArrayList<>(validation.warnings());
        List<String> suggestions = new ArrayList<>();
        if (!validation.passed()) {
            suggestions.add("请先修复校验错误，再重新执行模拟测试。");
            return new PolicySimulationResult(false, normalize(type), fileName, matchedRules, warnings, suggestions, validation);
        }

        JsonNode policyNode = readJson(text, warnings);
        JsonNode sampleNode = sampleData == null
                ? JacksonConfig.OBJECT_MAPPER.createObjectNode()
                : JacksonConfig.OBJECT_MAPPER.valueToTree(sampleData);
        if (sampleNode == null || sampleNode.isNull() || sampleNode.isEmpty()) {
            warnings.add("未提供样例数据，仅做配置级静态模拟。");
        }

        switch (normalize(type)) {
            case "checker" -> simulateChecker(policyNode, matchedRules, warnings);
            case "rating" -> simulateRating(policyNode, sampleNode, matchedRules, warnings);
            case "punish" -> simulatePunish(policyNode, sampleNode, matchedRules, warnings);
            default -> warnings.add("未知策略类型，仅返回 schema 校验结果。");
        }

        boolean passed = validation.passed() && !matchedRules.isEmpty();
        if (!passed) {
            suggestions.add("未命中任何规则，请补充样例数据或调整策略命中条件。");
        }
        return new PolicySimulationResult(passed, normalize(type), fileName, matchedRules, warnings, suggestions, validation);
    }

    private PolicyValidationResult validateInternal(String type, String fileName, String text) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String normalizedType = normalize(type);

        if (!StringUtils.hasText(text)) {
            errors.add("配置文本不能为空。");
            return new PolicyValidationResult(false, normalizedType, fileName, errors, warnings);
        }

        JsonNode policyNode = null;
        try {
            policyNode = JacksonConfig.OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            errors.add("JSON 解析失败：" + e.getMessage());
        }
        if (policyNode == null) {
            return new PolicyValidationResult(false, normalizedType, fileName, errors, warnings);
        }

        validatePolicyTarget(normalizedType, fileName, policyNode, errors, warnings);
        validateBySchema(normalizedType, fileName, policyNode, errors, warnings);
        return new PolicyValidationResult(errors.isEmpty(), normalizedType, fileName, errors, warnings);
    }

    private void validatePolicyTarget(String type, String fileName, JsonNode policyNode, List<String> errors, List<String> warnings) {
        switch (type) {
            case "checker" -> {
                if (!policyNode.isObject()) {
                    errors.add("checker 策略根节点必须是 JSON 对象。");
                }
                if (StringUtils.hasText(fileName) && !CHECKER_FILES.contains(fileName)) {
                    warnings.add("checker 策略建议写入 host.json、android.json、ios.json、h5.json 或 wechat.json。");
                }
            }
            case "rating" -> {
                if (!policyNode.isArray()) {
                    errors.add("rating 策略根节点必须是 JSON 数组。");
                }
                if (StringUtils.hasText(fileName) && !"rating_rule.json".equals(fileName)) {
                    warnings.add("rating 策略默认目标文件为 rating_rule.json。");
                }
            }
            case "punish" -> {
                if (!policyNode.isArray()) {
                    errors.add("punish 策略根节点必须是 JSON 数组。");
                }
            }
            default -> warnings.add("未知策略类型：" + type + "，仅执行 JSON 和 schema 可用项校验。");
        }
    }

    private void validateBySchema(String type, String fileName, JsonNode policyNode, List<String> errors, List<String> warnings) {
        String schemaText = null;
        try {
            schemaText = configService.readFileSchema(type, fileName);
        } catch (Exception e) {
            warnings.add("读取 schema 失败：" + e.getMessage());
        }
        if (!StringUtils.hasText(schemaText)) {
            warnings.add("未找到 schema，已跳过 schema 字段级校验。");
            return;
        }

        try {
            JsonNode schemaNode = JacksonConfig.OBJECT_MAPPER.readTree(schemaText);
            validateNode(policyNode, schemaNode, "$", errors);
        } catch (Exception e) {
            warnings.add("schema 解析失败，已跳过字段级校验：" + e.getMessage());
        }
    }

    private void validateNode(JsonNode node, JsonNode schema, String path, List<String> errors) {
        if (schema == null || schema.isNull()) {
            return;
        }

        JsonNode oneOf = schema.get("oneOf");
        if (oneOf != null && oneOf.isArray()) {
            for (JsonNode option : oneOf) {
                List<String> optionErrors = new ArrayList<>();
                validateNode(node, option, path, optionErrors);
                if (optionErrors.isEmpty()) {
                    return;
                }
            }
            errors.add(path + " 不符合 oneOf 中任一 schema 类型。");
            return;
        }

        String type = schemaText(schema, "type");
        if (StringUtils.hasText(type) && !matchesType(node, type)) {
            errors.add(path + " 期望类型为 " + type + "，实际为 " + describeNodeType(node) + "。");
            return;
        }

        if ("object".equals(type) && node.isObject()) {
            validateRequired(node, schema, path, errors);
            JsonNode properties = schema.get("properties");
            if (properties != null && properties.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    JsonNode child = node.get(field.getKey());
                    if (child != null) {
                        validateNode(child, field.getValue(), path + "." + field.getKey(), errors);
                    }
                }
            }
        }

        if ("array".equals(type) && node.isArray()) {
            JsonNode itemSchema = schema.get("items");
            if (itemSchema != null) {
                for (int i = 0; i < node.size(); i++) {
                    validateNode(node.get(i), itemSchema, path + "[" + i + "]", errors);
                }
            }
        }
    }

    private void validateRequired(JsonNode node, JsonNode schema, String path, List<String> errors) {
        JsonNode required = schema.get("required");
        if (required == null || !required.isArray()) {
            return;
        }
        for (JsonNode requiredField : required) {
            String fieldName = requiredField.asText();
            if (!node.has(fieldName)) {
                errors.add(path + " 缺少必填字段：" + fieldName + "。");
            }
        }
    }

    private void simulateChecker(JsonNode policyNode, List<String> matchedRules, List<String> warnings) {
        collectNonEmptyArrays(policyNode, "$", matchedRules);
        if (matchedRules.isEmpty()) {
            warnings.add("checker 策略未发现非空采集/检测项。");
        }
    }

    private void simulateRating(JsonNode policyNode, JsonNode sampleNode, List<String> matchedRules, List<String> warnings) {
        Set<String> sampleTags = collectSampleTags(sampleNode);
        if (sampleTags.isEmpty()) {
            warnings.add("样例数据未提供 tag/tags/agenda_tags 字段，无法验证评分规则命中。");
            return;
        }
        if (!policyNode.isArray()) {
            return;
        }
        for (JsonNode rule : policyNode) {
            String ruleName = text(rule, "name", "未命名评分策略");
            JsonNode scoreRules = rule.get("score_rules");
            if (scoreRules == null || !scoreRules.isArray()) {
                continue;
            }
            for (JsonNode scoreRule : scoreRules) {
                String tag = text(scoreRule, "tag", "");
                if (sampleTags.contains(tag)) {
                    matchedRules.add(ruleName + " 命中评分标签：" + tag);
                }
            }
        }
    }

    private void simulatePunish(JsonNode policyNode, JsonNode sampleNode, List<String> matchedRules, List<String> warnings) {
        Set<String> sampleTags = collectSampleTags(sampleNode);
        String source = firstText(sampleNode, "source", "sourceRegex", "sourceMark", "source_mark", "uri", "url", "entity");
        if (sampleTags.isEmpty()) {
            warnings.add("样例数据未提供 tag/tags 字段，无法验证处置标签命中。");
        }
        if (!StringUtils.hasText(source)) {
            warnings.add("样例数据未提供 source/sourceMark/uri/url 字段，无法验证来源正则命中。");
        }
        if (!policyNode.isArray()) {
            return;
        }

        for (JsonNode rule : policyNode) {
            String tag = text(rule, "tag", "");
            String sourceRegex = text(rule, "sourceRegex", "");
            boolean tagMatched = sampleTags.contains(tag);
            boolean sourceMatched = regexMatches(sourceRegex, source, warnings);
            if (tagMatched && sourceMatched) {
                JsonNode action = rule.get("action");
                String actionTitle = action == null ? "未命名动作" : text(action, "title", "未命名动作");
                matchedRules.add(tag + " -> " + actionTitle);
            }
        }
    }

    private boolean regexMatches(String regex, String source, List<String> warnings) {
        if (!StringUtils.hasText(regex) || !StringUtils.hasText(source)) {
            return false;
        }
        try {
            return Pattern.compile(regex).matcher(source).find();
        } catch (PatternSyntaxException e) {
            warnings.add("来源正则不合法：" + regex);
            return false;
        }
    }

    private void collectNonEmptyArrays(JsonNode node, String path, List<String> matchedRules) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            if (!node.isEmpty()) {
                matchedRules.add(path + " 包含 " + node.size() + " 个检测项");
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            collectNonEmptyArrays(field.getValue(), path + "." + field.getKey(), matchedRules);
        }
    }

    private Set<String> collectSampleTags(JsonNode sampleNode) {
        if (sampleNode == null || !sampleNode.isObject()) {
            return Collections.emptySet();
        }
        Set<String> tags = new HashSet<>();
        collectTagField(sampleNode.get("tag"), tags);
        collectTagField(sampleNode.get("tags"), tags);
        collectTagField(sampleNode.get("agenda_tags"), tags);
        collectTagField(sampleNode.get("labels"), tags);
        collectTagField(sampleNode.get("risk_tags"), tags);
        return tags;
    }

    private void collectTagField(JsonNode node, Set<String> tags) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectTagField(item, tags);
            }
            return;
        }
        if (!node.isTextual()) {
            return;
        }
        String[] parts = node.asText().split("[,，;；\\s]+");
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                tags.add(part.trim());
            }
        }
    }

    private boolean matchesType(JsonNode node, String type) {
        return switch (type) {
            case "object" -> node.isObject();
            case "array" -> node.isArray();
            case "string" -> node.isTextual();
            case "integer" -> node.isIntegralNumber();
            case "number" -> node.isNumber();
            case "boolean" -> node.isBoolean();
            case "null" -> node.isNull();
            default -> true;
        };
    }

    private JsonNode readJson(String text, List<String> warnings) {
        try {
            return JacksonConfig.OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            warnings.add("模拟阶段重新解析 JSON 失败：" + e.getMessage());
            return JacksonConfig.OBJECT_MAPPER.createObjectNode();
        }
    }

    private String schemaText(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String text(JsonNode node, String fieldName, String defaultValue) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? defaultValue : value.asText(defaultValue);
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return "";
        }
        for (String field : fields) {
            String value = text(node, field, "");
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    private String describeNodeType(JsonNode node) {
        if (node == null) {
            return "missing";
        }
        if (node.isObject()) {
            return "object";
        }
        if (node.isArray()) {
            return "array";
        }
        if (node.isTextual()) {
            return "string";
        }
        if (node.isIntegralNumber()) {
            return "integer";
        }
        if (node.isNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        if (node.isNull()) {
            return "null";
        }
        return node.getNodeType().name().toLowerCase(Locale.ROOT);
    }

    public record PolicyValidationResult(
            boolean passed,
            String type,
            String fileName,
            List<String> errors,
            List<String> warnings
    ) {
    }

    public record PolicySimulationResult(
            boolean passed,
            String type,
            String fileName,
            List<String> matchedRules,
            List<String> warnings,
            List<String> suggestions,
            PolicyValidationResult validation
    ) {
    }
}
