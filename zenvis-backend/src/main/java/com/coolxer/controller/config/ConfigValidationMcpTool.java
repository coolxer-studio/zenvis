package com.coolxer.controller.config;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.dih.mcp.McpToolApproval;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static com.coolxer.commons.enums.McpApprovalPolicy.ALLOW;
import static com.coolxer.commons.enums.McpToolRiskLevel.LOW;

/**
 * MCP 工具：对平台支持编辑的配置文件执行通用静态校验。
 */
@Service
public class ConfigValidationMcpTool {

    private static final List<String> SUPPORTED_FORMATS =
            List.of("json", "properties", "xml", "csv", "txt", "conf");

    private final ConfigService configService;

    public ConfigValidationMcpTool(ConfigService configService) {
        this.configService = configService;
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "config_validate", description = "校验系统配置的格式、结构以及可用 JSON Schema；不替代运行环境专项验证")
    public ConfigValidationResult validate(
            @ToolParam(description = "配置类型，例如 web、agent、meta 或低代码配置索引") String type,
            @ToolParam(description = "配置文件全名，必须包含扩展名") String fileName,
            @ToolParam(description = "待验证的完整配置文本") String text) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String normalizedType = normalize(type);
        String format = fileFormat(fileName);

        if (!StringUtils.hasText(text)) {
            errors.add("配置文本不能为空。");
            return result(false, false, normalizedType, fileName, format, errors, warnings);
        }
        if (!SUPPORTED_FORMATS.contains(format)) {
            errors.add("当前通用校验器不支持 " + displayFormat(format) + " 格式。");
            warnings.add("请连接能够验证该配置格式及运行效果的专项 MCP 服务。");
            return result(false, true, normalizedType, fileName, format, errors, warnings);
        }

        switch (format) {
            case "json" -> validateJson(normalizedType, fileName, text, errors, warnings);
            case "xml" -> validateXml(text, errors);
            case "properties" -> validateProperties(text, errors);
            case "csv" -> validateCsv(text, errors);
            case "txt", "conf" -> warnings.add("已完成非空检查；运行效果仍需对应系统或专项 MCP 验证。");
            default -> throw new IllegalStateException("未处理的配置格式: " + format);
        }

        return result(errors.isEmpty(), false, normalizedType, fileName, format, errors, warnings);
    }

    private void validateJson(String type,
                              String fileName,
                              String text,
                              List<String> errors,
                              List<String> warnings) {
        JsonNode value;
        try {
            value = JacksonConfig.OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            errors.add("JSON 解析失败：" + e.getMessage());
            return;
        }
        if (value == null) {
            errors.add("JSON 配置不能为空。");
            return;
        }

        String schemaText;
        try {
            schemaText = configService.readFileSchema(type, fileName);
        } catch (Exception e) {
            warnings.add("读取 JSON Schema 失败：" + e.getMessage());
            return;
        }
        if (!StringUtils.hasText(schemaText)) {
            warnings.add("未找到 JSON Schema，仅完成 JSON 语法校验。");
            return;
        }

        try {
            JsonNode schema = JacksonConfig.OBJECT_MAPPER.readTree(schemaText);
            validateJsonNode(value, schema, "$", errors);
        } catch (Exception e) {
            warnings.add("JSON Schema 解析失败，已跳过字段级校验：" + e.getMessage());
        }
    }

    private void validateJsonNode(JsonNode node, JsonNode schema, String path, List<String> errors) {
        if (schema == null || schema.isNull()) {
            return;
        }

        JsonNode oneOf = schema.get("oneOf");
        if (oneOf != null && oneOf.isArray()) {
            for (JsonNode option : oneOf) {
                List<String> optionErrors = new ArrayList<>();
                validateJsonNode(node, option, path, optionErrors);
                if (optionErrors.isEmpty()) {
                    return;
                }
            }
            errors.add(path + " 不符合 oneOf 中任一 schema 类型。");
            return;
        }

        String expectedType = schema.path("type").asText("");
        if (StringUtils.hasText(expectedType) && !matchesJsonType(node, expectedType)) {
            errors.add(path + " 期望类型为 " + expectedType + "，实际为 " + node.getNodeType().name().toLowerCase(Locale.ROOT) + "。");
            return;
        }

        if (node.isObject()) {
            JsonNode required = schema.get("required");
            if (required != null && required.isArray()) {
                required.forEach(field -> {
                    if (!node.has(field.asText())) {
                        errors.add(path + " 缺少必填字段：" + field.asText() + "。");
                    }
                });
            }
            JsonNode properties = schema.get("properties");
            if (properties != null && properties.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    if (node.has(field.getKey())) {
                        validateJsonNode(node.get(field.getKey()), field.getValue(), path + "." + field.getKey(), errors);
                    }
                }
            }
        }

        if (node.isArray() && schema.has("items")) {
            for (int i = 0; i < node.size(); i++) {
                validateJsonNode(node.get(i), schema.get("items"), path + "[" + i + "]", errors);
            }
        }
    }

    private boolean matchesJsonType(JsonNode node, String expectedType) {
        return switch (expectedType) {
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

    private void validateXml(String text, List<String> errors) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.newDocumentBuilder().parse(new InputSource(new StringReader(text)));
        } catch (Exception e) {
            errors.add("XML 解析失败：" + e.getMessage());
        }
    }

    private void validateProperties(String text, List<String> errors) {
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(text));
            if (properties.isEmpty()) {
                errors.add("properties 配置至少需要一个键值项。");
            }
        } catch (Exception e) {
            errors.add("properties 解析失败：" + e.getMessage());
        }
    }

    private void validateCsv(String text, List<String> errors) {
        String[] lines = text.split("\\R");
        int expectedColumns = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            int columns = csvColumnCount(lines[i]);
            if (columns < 1) {
                errors.add("CSV 第 " + (i + 1) + " 行格式错误。");
                continue;
            }
            if (expectedColumns < 0) {
                expectedColumns = columns;
            } else if (columns != expectedColumns) {
                errors.add("CSV 第 " + (i + 1) + " 行列数为 " + columns + "，期望 " + expectedColumns + "。");
            }
        }
        if (expectedColumns < 0) {
            errors.add("CSV 配置不能为空。");
        }
    }

    private int csvColumnCount(String line) {
        boolean quoted = false;
        int columns = 1;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                columns++;
            }
        }
        return quoted ? -1 : columns;
    }

    private String fileFormat(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).trim().toLowerCase(Locale.ROOT);
    }

    private String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    private String displayFormat(String format) {
        return "unknown".equals(format) ? "未知" : format.toUpperCase(Locale.ROOT);
    }

    private ConfigValidationResult result(boolean passed,
                                          boolean blocked,
                                          String type,
                                          String fileName,
                                          String format,
                                          List<String> errors,
                                          List<String> warnings) {
        return new ConfigValidationResult(
                passed,
                blocked,
                type,
                fileName,
                format,
                List.copyOf(errors),
                List.copyOf(warnings)
        );
    }

    public record ConfigValidationResult(
            boolean passed,
            boolean blocked,
            String configType,
            String fileName,
            String format,
            List<String> errors,
            List<String> warnings
    ) {
    }
}
