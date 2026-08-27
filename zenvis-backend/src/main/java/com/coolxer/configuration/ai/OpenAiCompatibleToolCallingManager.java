package com.coolxer.configuration.ai;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.service.dih.mcp.ToolRuntimeContext;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ensures tool calls returned by OpenAI-compatible providers can be sent back in
 * the next chat-completions request.
 *
 * <p>Some providers omit {@code function.arguments} when a tool has no required
 * arguments. Spring AI preserves that null value in the assistant message, but
 * the OpenAI request schema requires {@code arguments} to be a JSON string. The
 * follow-up request is then rejected before the tool result can be processed.
 * Other providers occasionally return syntactically valid arguments whose nested
 * values do not match the declared tool schema. This manager normalizes missing
 * arguments and validates argument structure before execution so the model gets
 * one bounded opportunity to regenerate an invalid call.</p>
 */
final class OpenAiCompatibleToolCallingManager implements ToolCallingManager {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleToolCallingManager.class);

    private static final String EMPTY_ARGUMENTS = "{}";

    private static final String INVALID_ARGUMENTS_CODE = "INVALID_TOOL_ARGUMENTS";

    private static final int MAX_SCHEMA_DEPTH = 64;

    private static final int MAX_JSON_STRING_NORMALIZATION_DEPTH = 4;

    private static final int MAX_JSON_STRING_NORMALIZATION_CHARS = 64 * 1024;

    private static final int MAX_ARGUMENT_NORMALIZATION_CHARS = 64 * 1024;

    private static final int MAX_APPENDED_JSON_CLOSERS = 8;

    private static final int MAX_ARGUMENT_STRUCTURE_PREVIEW_CHARS = 240;

    /**
     * Compatibility parsing is deliberately narrower than Jackson's full set of
     * permissive features. Every accepted value is immediately serialized back
     * to canonical JSON and validated against the declared tool schema.
     */
    private static final ObjectMapper COMPAT_ARGUMENT_MAPPER = new ObjectMapper(
            JsonFactory.builder()
                    .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                    .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
                    .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                    .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                    .enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
                    .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
                    .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                    .build()
    );

    private final ToolCallingManager delegate;

    OpenAiCompatibleToolCallingManager(ToolCallingManager delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        Map<String, JsonNode> toolSchemas = resolveToolSchemas(prompt);
        ChatResponse normalizedResponse = normalizeToolArguments(chatResponse, toolSchemas);
        Generation toolGeneration = firstToolGeneration(normalizedResponse);
        if (toolGeneration == null) {
            return delegate.executeToolCalls(prompt, normalizedResponse);
        }

        AssistantMessage assistantMessage = toolGeneration.getOutput();
        ToolRuntimeContext runtimeContext = resolveRuntimeContext(prompt);
        if (runtimeContext != null
                && !runtimeContext.reserveToolCalls(assistantMessage.getToolCalls().size())) {
            return buildTerminalPartialResult(
                    prompt, assistantMessage, runtimeContext.stopReason());
        }

        List<InvalidToolCall> invalidToolCalls = assistantMessage.getToolCalls().stream()
                .map(toolCall -> validateToolCall(toolCall, toolSchemas))
                .flatMap(Optional::stream)
                .toList();
        if (invalidToolCalls.isEmpty()) {
            ToolExecutionResult result = delegate.executeToolCalls(prompt, normalizedResponse);
            return runtimeContext == null
                    ? result
                    : constrainToolExecutionResult(result, runtimeContext);
        }

        logInvalidToolCalls(invalidToolCalls, toolGeneration);
        int invalidAttempts = runtimeContext == null
                ? (previousTurnRequestedArgumentRetry(prompt) ? 2 : 1)
                : runtimeContext.registerInvalidArguments();
        if (invalidAttempts >= 2 || (runtimeContext != null && runtimeContext.stopRequested())) {
            return buildTerminalPartialResult(
                    prompt, assistantMessage, "invalid_tool_arguments_repeated");
        }
        return buildArgumentRetryResult(prompt, invalidToolCalls, toolSchemas);
    }

    private ToolExecutionResult constrainToolExecutionResult(
            ToolExecutionResult result,
            ToolRuntimeContext runtimeContext
    ) {
        if (result == null || result.conversationHistory() == null) {
            return result;
        }
        List<Message> history = new ArrayList<>(result.conversationHistory());
        int lastToolResponseIndex = -1;
        for (int index = history.size() - 1; index >= 0; index--) {
            if (history.get(index) instanceof ToolResponseMessage) {
                lastToolResponseIndex = index;
                break;
            }
        }
        if (lastToolResponseIndex >= 0) {
            ToolResponseMessage latest =
                    (ToolResponseMessage) history.get(lastToolResponseIndex);
            List<ToolResponseMessage.ToolResponse> responses = latest.getResponses().stream()
                    .map(response -> constrainToolResponse(response, runtimeContext))
                    .toList();
            history.set(lastToolResponseIndex,
                    ToolResponseMessage.builder().responses(responses).build());
        }

        if (runtimeContext.remainingToolCalls() == 0) {
            runtimeContext.requestStop("tool_call_budget_exhausted");
        }
        if (runtimeContext.stopRequested() && lastToolResponseIndex >= 0) {
            ToolResponseMessage last = (ToolResponseMessage) history.get(lastToolResponseIndex);
            List<ToolResponseMessage.ToolResponse> annotated = new ArrayList<>(last.getResponses());
            if (!annotated.isEmpty()) {
                int index = annotated.size() - 1;
                ToolResponseMessage.ToolResponse response = annotated.get(index);
                annotated.set(index, new ToolResponseMessage.ToolResponse(
                        response.id(),
                        response.name(),
                        appendStopInstruction(response.responseData(), runtimeContext.stopReason())
                ));
                history.set(lastToolResponseIndex,
                        ToolResponseMessage.builder().responses(annotated).build());
            }
        }
        return ToolExecutionResult.builder()
                .conversationHistory(history)
                .returnDirect(result.returnDirect())
                .build();
    }

    private ToolResponseMessage.ToolResponse constrainToolResponse(
            ToolResponseMessage.ToolResponse response,
            ToolRuntimeContext runtimeContext
    ) {
        String data = Objects.toString(response.responseData(), "");
        if (isFailureResult(data)) {
            runtimeContext.registerFailure(failureSignature(response.name(), data));
        }
        ToolRuntimeContext.ResultAllowance allowance =
                runtimeContext.reserveResult(data);
        String constrained = allowance.truncated()
                ? truncatedResult(data, allowance)
                : data;
        return new ToolResponseMessage.ToolResponse(
                response.id(), response.name(), constrained);
    }

    private boolean isFailureResult(String data) {
        String normalized = StringUtils.trimWhitespace(data).toLowerCase();
        return normalized.contains("\"error\"")
                || normalized.contains("\"status\":\"failed\"")
                || normalized.contains("\"status\":\"error\"")
                || normalized.contains("\"status\":\"invalid_request\"")
                || normalized.contains("字段不存在")
                || normalized.contains("entity_not_found")
                || normalized.contains("tool_error");
    }

    private String failureSignature(String toolName, String data) {
        String normalized = StringUtils.trimWhitespace(data)
                .replaceAll("\\s+", " ")
                .replaceAll("\\b\\d+\\b", "#");
        if (normalized.length() > 256) {
            normalized = normalized.substring(0, 256);
        }
        return defaultIfBlank(toolName, "unknown_tool") + ":" + normalized;
    }

    private String truncatedResult(
            String data,
            ToolRuntimeContext.ResultAllowance allowance
    ) {
        int previewLength = Math.min(allowance.allowedChars(), data.length());
        ObjectNode envelope = JacksonConfig.OBJECT_MAPPER.createObjectNode();
        envelope.put("truncated", true);
        envelope.put("originalChars", allowance.requestedChars());
        envelope.put("returnedChars", previewLength);
        envelope.put("originalTokens", allowance.requestedTokens());
        envelope.put("returnedTokens", allowance.allowedTokens());
        envelope.put("contentPreview", data.substring(0, previewLength));
        copyPaginationMetadata(data, envelope);
        envelope.put("instruction", "结果已按 Skill 预算截断；不得推断被截断部分。");
        return envelope.toString();
    }

    private void copyPaginationMetadata(String data, ObjectNode envelope) {
        try {
            JsonNode root = JacksonConfig.OBJECT_MAPPER.readTree(data);
            if (root == null || !root.isObject()) {
                return;
            }
            ObjectNode metadata = envelope.putObject("pagination");
            for (String key : List.of(
                    "total", "hasMore", "has_more", "nextCursor", "next_cursor",
                    "cursor", "token", "page", "size", "perPage", "per_page")) {
                JsonNode value = root.get(key);
                if (value != null && !value.isContainerNode()) {
                    metadata.set(key, value);
                }
            }
            if (metadata.isEmpty()) {
                envelope.remove("pagination");
            }
        } catch (JsonProcessingException ignored) {
            // The generic preview remains available for non-JSON tool results.
        }
    }

    private String appendStopInstruction(String data, String reason) {
        String normalizedReason = defaultIfBlank(reason, "tool_execution_stopped");
        return Objects.toString(data, "")
                + "\n\n{\"_zenvis_runtime\":{\"stop\":true,\"reason\":\""
                + normalizedReason
                + "\",\"instruction\":\"不要再调用工具；立即基于已有证据输出部分完成报告并列出覆盖缺口。\"}}";
    }

    private ToolRuntimeContext resolveRuntimeContext(Prompt prompt) {
        if (prompt == null || !(prompt.getOptions() instanceof ToolCallingChatOptions toolOptions)
                || toolOptions.getToolContext() == null) {
            return null;
        }
        Object context = toolOptions.getToolContext().get(ToolRuntimeContext.TOOL_CONTEXT_KEY);
        return context instanceof ToolRuntimeContext runtimeContext ? runtimeContext : null;
    }

    private Generation firstToolGeneration(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResults() == null) {
            return null;
        }
        return chatResponse.getResults().stream()
                .filter(Objects::nonNull)
                .filter(generation -> generation.getOutput() != null)
                .filter(generation -> !generation.getOutput().getToolCalls().isEmpty())
                .findFirst()
                .orElse(null);
    }

    private Optional<InvalidToolCall> validateToolCall(
            AssistantMessage.ToolCall toolCall,
            Map<String, JsonNode> toolSchemas
    ) {
        String arguments = toolCall.arguments();
        if (!StringUtils.hasText(arguments)) {
            return Optional.of(new InvalidToolCall(toolCall, "arguments were empty"));
        }

        JsonNode parsed;
        try {
            parsed = parseJsonStrict(arguments);
        } catch (JsonProcessingException exception) {
            return Optional.of(new InvalidToolCall(toolCall, jsonParseFailureReason(exception)));
        }
        if (parsed == null || !parsed.isObject()) {
            return Optional.of(new InvalidToolCall(
                    toolCall,
                    "$ expected object but was " + jsonType(parsed)
            ));
        }

        JsonNode schema = toolSchemas.get(toolCall.name());
        if (schema == null) {
            return Optional.empty();
        }
        String schemaError = validateAgainstSchema(parsed, schema, schema, "$", 0);
        return schemaError == null
                ? Optional.empty()
                : Optional.of(new InvalidToolCall(toolCall, schemaError));
    }

    private Map<String, JsonNode> resolveToolSchemas(Prompt prompt) {
        if (prompt == null || !(prompt.getOptions() instanceof ToolCallingChatOptions toolOptions)) {
            return Map.of();
        }

        Map<String, JsonNode> schemas = new HashMap<>();
        List<ToolDefinition> definitions = resolveToolDefinitions(toolOptions);
        if (definitions == null) {
            return Map.of();
        }
        for (ToolDefinition definition : definitions) {
            if (definition == null
                    || !StringUtils.hasText(definition.name())
                    || !StringUtils.hasText(definition.inputSchema())) {
                continue;
            }
            try {
                JsonNode schema = JacksonConfig.OBJECT_MAPPER.readTree(definition.inputSchema());
                if (schema != null && schema.isObject()) {
                    schemas.put(definition.name(), schema);
                }
            } catch (JsonProcessingException exception) {
                log.warn("工具 {} 的输入 Schema 不是合法 JSON，跳过参数结构校验", definition.name());
            }
        }
        return schemas;
    }

    private String validateAgainstSchema(
            JsonNode value,
            JsonNode schema,
            JsonNode rootSchema,
            String path,
            int depth
    ) {
        if (depth > MAX_SCHEMA_DEPTH || schema == null || !schema.isObject()) {
            return null;
        }

        JsonNode resolvedSchema = resolveSchemaReference(schema, rootSchema);
        if (resolvedSchema != schema) {
            return validateAgainstSchema(value, resolvedSchema, rootSchema, path, depth + 1);
        }

        String compositeError = validateCompositeSchemas(value, schema, rootSchema, path, depth);
        if (compositeError != null) {
            return compositeError;
        }

        JsonNode typeSchema = schema.get("type");
        if (typeSchema != null && !matchesType(value, typeSchema)) {
            return "%s expected %s but was %s".formatted(path, expectedTypes(typeSchema), jsonType(value));
        }

        JsonNode enumSchema = schema.get("enum");
        if (enumSchema != null && enumSchema.isArray() && !contains(enumSchema, value)) {
            return path + " was not one of the allowed values";
        }
        JsonNode constSchema = schema.get("const");
        if (constSchema != null && !constSchema.equals(value)) {
            return path + " did not match the required constant value";
        }

        if (value != null && value.isObject()) {
            String objectError = validateObject(value, schema, rootSchema, path, depth);
            if (objectError != null) {
                return objectError;
            }
        }
        if (value != null && value.isArray()) {
            String arrayError = validateArray(value, schema, rootSchema, path, depth);
            if (arrayError != null) {
                return arrayError;
            }
        }
        return null;
    }

    private String validateCompositeSchemas(
            JsonNode value,
            JsonNode schema,
            JsonNode rootSchema,
            String path,
            int depth
    ) {
        JsonNode allOf = schema.get("allOf");
        if (allOf != null && allOf.isArray()) {
            for (JsonNode candidate : allOf) {
                String error = validateAgainstSchema(value, candidate, rootSchema, path, depth + 1);
                if (error != null) {
                    return error;
                }
            }
        }

        for (String keyword : List.of("anyOf", "oneOf")) {
            JsonNode alternatives = schema.get(keyword);
            if (alternatives == null || !alternatives.isArray() || alternatives.isEmpty()) {
                continue;
            }
            boolean matches = false;
            for (JsonNode candidate : alternatives) {
                if (validateAgainstSchema(value, candidate, rootSchema, path, depth + 1) == null) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return path + " did not match any allowed schema";
            }
        }
        return null;
    }

    private String validateObject(
            JsonNode value,
            JsonNode schema,
            JsonNode rootSchema,
            String path,
            int depth
    ) {
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            for (JsonNode requiredName : required) {
                if (requiredName.isTextual() && !value.has(requiredName.textValue())) {
                    return path + "." + requiredName.textValue() + " was required but missing";
                }
            }
        }

        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!value.has(field.getKey())) {
                    continue;
                }
                String error = validateAgainstSchema(
                        value.get(field.getKey()),
                        field.getValue(),
                        rootSchema,
                        path + "." + field.getKey(),
                        depth + 1
                );
                if (error != null) {
                    return error;
                }
            }
        }

        JsonNode additionalProperties = schema.get("additionalProperties");
        if (additionalProperties != null && additionalProperties.isBoolean()
                && !additionalProperties.booleanValue() && properties != null && properties.isObject()) {
            Iterator<String> fieldNames = value.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (!properties.has(fieldName)) {
                    return path + "." + fieldName + " was not defined by the tool schema";
                }
            }
        }
        return null;
    }

    private String validateArray(
            JsonNode value,
            JsonNode schema,
            JsonNode rootSchema,
            String path,
            int depth
    ) {
        JsonNode itemSchema = schema.get("items");
        if (itemSchema == null || !itemSchema.isObject()) {
            return null;
        }
        for (int index = 0; index < value.size(); index++) {
            String error = validateAgainstSchema(
                    value.get(index),
                    itemSchema,
                    rootSchema,
                    path + "[" + index + "]",
                    depth + 1
            );
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    private JsonNode resolveSchemaReference(JsonNode schema, JsonNode rootSchema) {
        JsonNode reference = schema.get("$ref");
        if (reference == null || !reference.isTextual() || !reference.textValue().startsWith("#/")) {
            return schema;
        }
        JsonNode resolved = rootSchema.at(reference.textValue().substring(1));
        return resolved.isMissingNode() ? schema : resolved;
    }

    private boolean matchesType(JsonNode value, JsonNode typeSchema) {
        if (typeSchema.isTextual()) {
            return matchesType(value, typeSchema.textValue());
        }
        if (typeSchema.isArray()) {
            for (JsonNode allowedType : typeSchema) {
                if (allowedType.isTextual() && matchesType(value, allowedType.textValue())) {
                    return true;
                }
            }
        }
        return true;
    }

    private boolean matchesType(JsonNode value, String expectedType) {
        return switch (expectedType) {
            case "object" -> value != null && value.isObject();
            case "array" -> value != null && value.isArray();
            case "string" -> value != null && value.isTextual();
            case "integer" -> value != null && value.isIntegralNumber();
            case "number" -> value != null && value.isNumber();
            case "boolean" -> value != null && value.isBoolean();
            case "null" -> value == null || value.isNull();
            default -> true;
        };
    }

    private String expectedTypes(JsonNode typeSchema) {
        if (typeSchema.isTextual()) {
            return typeSchema.textValue();
        }
        if (typeSchema.isArray()) {
            List<String> types = new ArrayList<>();
            typeSchema.forEach(type -> {
                if (type.isTextual()) {
                    types.add(type.textValue());
                }
            });
            return String.join(" or ", types);
        }
        return "a value allowed by the tool schema";
    }

    private String jsonType(JsonNode value) {
        if (value == null || value.isNull()) {
            return "null";
        }
        if (value.isObject()) {
            return "object";
        }
        if (value.isArray()) {
            return "array";
        }
        if (value.isTextual()) {
            return "string";
        }
        if (value.isIntegralNumber()) {
            return "integer";
        }
        if (value.isNumber()) {
            return "number";
        }
        if (value.isBoolean()) {
            return "boolean";
        }
        return value.getNodeType().name().toLowerCase();
    }

    private boolean contains(JsonNode candidates, JsonNode value) {
        for (JsonNode candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private void logInvalidToolCalls(List<InvalidToolCall> invalidToolCalls,
                                     Generation generation) {
        String finishReason = generation.getMetadata() == null
                ? ""
                : StringUtils.trimWhitespace(generation.getMetadata().getFinishReason());
        String summaries = invalidToolCalls.stream()
                .map(invalidToolCall -> (
                        "%s(id=%s, argumentChars=%d, reason=%s, digest=%s, structure=%s)"
                ).formatted(
                        StringUtils.hasText(invalidToolCall.toolCall().name())
                                ? invalidToolCall.toolCall().name() : "unknown",
                        StringUtils.hasText(invalidToolCall.toolCall().id())
                                ? invalidToolCall.toolCall().id() : "unknown",
                        invalidToolCall.toolCall().arguments() == null
                                ? 0 : invalidToolCall.toolCall().arguments().length(),
                        invalidToolCall.reason(),
                        argumentDigest(invalidToolCall.toolCall().arguments()),
                        argumentStructurePreview(invalidToolCall.toolCall().arguments())
                ))
                .collect(Collectors.joining(", "));
        log.warn("模型返回非法工具参数，将请求模型重新生成: tools=[{}], finishReason={}",
                summaries, StringUtils.hasText(finishReason) ? finishReason : "unknown");
    }

    private boolean previousTurnRequestedArgumentRetry(Prompt prompt) {
        if (prompt == null || prompt.getInstructions() == null || prompt.getInstructions().isEmpty()) {
            return false;
        }
        Message lastMessage = prompt.getInstructions().get(prompt.getInstructions().size() - 1);
        if (StringUtils.hasText(lastMessage.getText())
                && lastMessage.getText().contains(INVALID_ARGUMENTS_CODE)) {
            return true;
        }
        return lastMessage instanceof ToolResponseMessage toolResponseMessage
                && toolResponseMessage.getResponses().stream()
                .map(ToolResponseMessage.ToolResponse::responseData)
                .filter(Objects::nonNull)
                .anyMatch(response -> response.contains(INVALID_ARGUMENTS_CODE));
    }

    private ToolExecutionResult buildArgumentRetryResult(
            Prompt prompt,
            List<InvalidToolCall> invalidToolCalls,
            Map<String, JsonNode> toolSchemas
    ) {
        List<Message> conversationHistory = new ArrayList<>(prompt.getInstructions());
        conversationHistory.add(new UserMessage(
                invalidArgumentsCorrection(invalidToolCalls, toolSchemas)));
        return ToolExecutionResult.builder()
                .conversationHistory(conversationHistory)
                .returnDirect(false)
                .build();
    }

    private String invalidArgumentsCorrection(
            List<InvalidToolCall> invalidToolCalls,
            Map<String, JsonNode> toolSchemas
    ) {
        ObjectNode correction = JacksonConfig.OBJECT_MAPPER.createObjectNode();
        correction.put("code", INVALID_ARGUMENTS_CODE);
        correction.put("message",
                "平台未执行上一轮工具调用，因为参数不符合工具 Schema。"
                        + "请只重试一次，并生成与 Schema 完全匹配的单个 JSON 对象；不要复用上一轮参数。"
                        + "不要添加 Markdown 代码围栏、注释或前后说明，所有字段必须位于 Schema 声明的父对象内。");
        var failures = correction.putArray("invalidCalls");
        invalidToolCalls.forEach(invalid -> {
            ObjectNode failure = failures.addObject();
            String toolName = defaultIfBlank(invalid.toolCall().name(), "unknown");
            failure.put("tool", toolName);
            failure.put("reason", invalid.reason());
            JsonNode schema = toolSchemas.get(toolName);
            if (schema != null) {
                failure.set("expectedShape", summarizeSchemaShape(schema));
            }
        });
        return correction.toString();
    }

    private ToolExecutionResult buildTerminalPartialResult(
            Prompt prompt,
            AssistantMessage assistantMessage,
            String reason
    ) {
        AssistantMessage.ToolCall firstCall = assistantMessage.getToolCalls().isEmpty()
                ? new AssistantMessage.ToolCall("zenvis-budget", "function", "zenvis_runtime", EMPTY_ARGUMENTS)
                : assistantMessage.getToolCalls().get(0);
        AssistantMessage sanitizedAssistant = new AssistantMessage(
                assistantMessage.getText(),
                assistantMessage.getMetadata(),
                List.of(new AssistantMessage.ToolCall(
                        firstCall.id(), firstCall.type(), firstCall.name(), EMPTY_ARGUMENTS)),
                assistantMessage.getMedia()
        );
        String normalizedReason = defaultIfBlank(reason, "tool_execution_stopped");
        String partialReport = """
                处理已部分完成：工具调用已被安全终止（%s）。系统保留此前已经取得的真实结果，
                但不会继续递归调用或猜测参数。请将本次结果视为覆盖不完整。

                ```zenvis:notice
                {"level":"warning","title":"工具执行覆盖不完整","content":"工具调用已被有界终止，未生成未经验证的结果。请重试一次；若仍失败，请检查兼容模型的工具参数格式。","details":{"truncated":true,"dataGaps":["%s"],"toolFailures":["%s"]}}
                ```
                """.formatted(normalizedReason, normalizedReason, normalizedReason);
        List<Message> conversationHistory = new ArrayList<>(prompt.getInstructions());
        conversationHistory.add(sanitizedAssistant);
        conversationHistory.add(ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        firstCall.id(), firstCall.name(), partialReport)))
                .build());
        return ToolExecutionResult.builder()
                .conversationHistory(conversationHistory)
                .returnDirect(true)
                .build();
    }

    private ChatResponse normalizeToolArguments(
            ChatResponse chatResponse,
            Map<String, JsonNode> toolSchemas
    ) {
        if (chatResponse == null || !chatResponse.hasToolCalls()) {
            return chatResponse;
        }

        boolean requiresNormalization = chatResponse.getResults().stream()
                .filter(Objects::nonNull)
                .anyMatch(generation -> generation.getOutput().getToolCalls().stream()
                        .anyMatch(toolCall -> requiresNormalization(
                                toolCall,
                                toolSchemas.get(toolCall.name()),
                                allowsDelimiterCompletion(generation))));
        if (!requiresNormalization) {
            return chatResponse;
        }

        List<Generation> generations = chatResponse.getResults().stream()
                .map(generation -> normalizeGeneration(generation, toolSchemas))
                .toList();
        return ChatResponse.builder()
                .from(chatResponse)
                .generations(generations)
                .build();
    }

    private boolean requiresNormalization(
            AssistantMessage.ToolCall toolCall,
            JsonNode schema,
            boolean allowDelimiterCompletion
    ) {
        if (!StringUtils.hasText(toolCall.arguments())) {
            return true;
        }
        try {
            return normalizeArgumentText(
                    toolCall.arguments(), schema, allowDelimiterCompletion).changed();
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }

    private Generation normalizeGeneration(
            Generation generation,
            Map<String, JsonNode> toolSchemas
    ) {
        AssistantMessage message = generation.getOutput();
        List<AssistantMessage.ToolCall> normalizedToolCalls = message.getToolCalls().stream()
                .map(toolCall -> normalizeToolCall(
                        toolCall,
                        toolSchemas.get(toolCall.name()),
                        allowsDelimiterCompletion(generation)))
                .toList();
        AssistantMessage normalizedMessage = new AssistantMessage(
                message.getText(),
                message.getMetadata(),
                normalizedToolCalls,
                message.getMedia()
        );
        return new Generation(normalizedMessage, generation.getMetadata());
    }

    private AssistantMessage.ToolCall normalizeToolCall(
            AssistantMessage.ToolCall toolCall,
            JsonNode schema,
            boolean allowDelimiterCompletion
    ) {
        if (!StringUtils.hasText(toolCall.arguments())) {
            return new AssistantMessage.ToolCall(
                    toolCall.id(),
                    toolCall.type(),
                    toolCall.name(),
                    EMPTY_ARGUMENTS
            );
        }
        try {
            NormalizedArguments normalized =
                    normalizeArgumentText(
                            toolCall.arguments(), schema, allowDelimiterCompletion);
            if (normalized.changed()) {
                log.info("已规范化兼容模型工具参数: tool={}, repairs={}, argumentChars={}",
                        defaultIfBlank(toolCall.name(), "unknown"),
                        normalized.repairs(),
                        toolCall.arguments().length());
                return new AssistantMessage.ToolCall(
                        toolCall.id(),
                        toolCall.type(),
                        toolCall.name(),
                        normalized.arguments()
                );
            }
        } catch (JsonProcessingException ignored) {
            return toolCall;
        }
        return toolCall;
    }

    private NormalizedArguments normalizeArgumentText(
            String arguments,
            JsonNode schema,
            boolean allowDelimiterCompletion
    ) throws JsonProcessingException {
        ParsedJson parsedJson =
                parseJsonCompatible(arguments, allowDelimiterCompletion);
        JsonNode parsed = parsedJson.value();
        JsonNode normalized = normalizeJsonStrings(parsed, schema, schema, 0);
        normalized = normalizeSoleObjectWrapper(normalized, schema, schema);
        normalized = normalizeJsonStrings(normalized, schema, schema, 0);

        Set<String> repairs = new LinkedHashSet<>(parsedJson.repairs());
        if (!normalized.equals(parsed)) {
            repairs.add("schema_shape");
        }
        if (repairs.isEmpty()) {
            return new NormalizedArguments(arguments, false, List.of());
        }
        return new NormalizedArguments(
                JacksonConfig.OBJECT_MAPPER.writeValueAsString(normalized),
                true,
                List.copyOf(repairs)
        );
    }

    /**
     * Repairs the common OpenAI-compatible shape drift where a provider either
     * flattens a method's sole object parameter or closes that wrapper too early.
     * Fields are moved only when the declared schema provides an unambiguous
     * destination, and conflicting values are never overwritten.
     */
    private JsonNode normalizeSoleObjectWrapper(
            JsonNode value,
            JsonNode schema,
            JsonNode rootSchema
    ) {
        if (value == null || !value.isObject() || schema == null) {
            return value;
        }
        JsonNode resolvedSchema = resolveSchemaReference(schema, rootSchema);
        JsonNode outerProperties = resolvedSchema.get("properties");
        if (outerProperties == null || !outerProperties.isObject()
                || outerProperties.size() != 1) {
            return value;
        }

        Map.Entry<String, JsonNode> wrapperProperty =
                outerProperties.fields().next();
        String wrapperName = wrapperProperty.getKey();
        JsonNode wrapperSchema =
                resolveSchemaReference(wrapperProperty.getValue(), rootSchema);
        if (!expectsObject(wrapperSchema)) {
            return value;
        }
        JsonNode innerProperties = wrapperSchema.get("properties");
        if (innerProperties == null || !innerProperties.isObject()) {
            return value;
        }

        ObjectNode source = (ObjectNode) value;
        if (!source.has(wrapperName)) {
            if (source.isEmpty() || !allFieldsDeclaredBy(source, innerProperties)) {
                return value;
            }
            ObjectNode wrapped = JacksonConfig.OBJECT_MAPPER.createObjectNode();
            wrapped.set(wrapperName, source.deepCopy());
            return wrapped;
        }
        if (!source.get(wrapperName).isObject()) {
            return value;
        }

        ObjectNode normalized = source.deepCopy();
        ObjectNode wrapper = (ObjectNode) normalized.get(wrapperName).deepCopy();
        List<String> movableFields = new ArrayList<>();
        normalized.fieldNames().forEachRemaining(field -> {
            if (!wrapperName.equals(field)
                    && innerProperties.has(field)
                    && !wrapper.has(field)) {
                movableFields.add(field);
            }
        });
        if (movableFields.isEmpty()) {
            return value;
        }
        movableFields.forEach(field -> wrapper.set(field, normalized.remove(field)));
        normalized.set(wrapperName, wrapper);
        return normalized;
    }

    private boolean allFieldsDeclaredBy(ObjectNode value, JsonNode properties) {
        Iterator<String> fields = value.fieldNames();
        while (fields.hasNext()) {
            if (!properties.has(fields.next())) {
                return false;
            }
        }
        return true;
    }

    private JsonNode normalizeJsonStrings(
            JsonNode value,
            JsonNode schema,
            JsonNode rootSchema,
            int depth
    ) {
        if (value == null || schema == null || depth > MAX_JSON_STRING_NORMALIZATION_DEPTH) {
            return value;
        }
        JsonNode resolvedSchema = resolveSchemaReference(schema, rootSchema);
        if (value.isTextual()
                && value.textValue().length() <= MAX_JSON_STRING_NORMALIZATION_CHARS
                && expectsObjectOrArray(resolvedSchema)) {
            try {
                JsonNode decoded = parseJsonStrict(value.textValue());
                if (decoded != null && (decoded.isObject() || decoded.isArray())) {
                    return normalizeJsonStrings(decoded, resolvedSchema, rootSchema, depth + 1);
                }
            } catch (JsonProcessingException ignored) {
                return value;
            }
        }
        if (value.isObject()) {
            ObjectNode normalized = (ObjectNode) value.deepCopy();
            JsonNode properties = resolvedSchema.get("properties");
            if (properties != null && properties.isObject()) {
                properties.fields().forEachRemaining(field -> {
                    if (normalized.has(field.getKey())) {
                        normalized.set(
                                field.getKey(),
                                normalizeJsonStrings(
                                        normalized.get(field.getKey()),
                                        field.getValue(),
                                        rootSchema,
                                        depth + 1)
                        );
                    }
                });
            }
            return normalized;
        }
        if (value.isArray()) {
            JsonNode itemSchema = resolvedSchema.get("items");
            if (itemSchema != null && itemSchema.isObject()) {
                com.fasterxml.jackson.databind.node.ArrayNode normalized =
                        JacksonConfig.OBJECT_MAPPER.createArrayNode();
                value.forEach(item -> normalized.add(
                        normalizeJsonStrings(item, itemSchema, rootSchema, depth + 1)));
                return normalized;
            }
        }
        return value;
    }

    private boolean expectsObjectOrArray(JsonNode schema) {
        if (schema == null || !schema.isObject()) {
            return false;
        }
        JsonNode type = schema.get("type");
        if (type == null) {
            return schema.has("properties") || schema.has("items");
        }
        if (type.isTextual()) {
            return "object".equals(type.textValue()) || "array".equals(type.textValue());
        }
        if (type.isArray()) {
            for (JsonNode candidate : type) {
                if (candidate.isTextual()
                        && ("object".equals(candidate.textValue())
                        || "array".equals(candidate.textValue()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean expectsObject(JsonNode schema) {
        if (schema == null || !schema.isObject()) {
            return false;
        }
        JsonNode type = schema.get("type");
        if (type == null) {
            return schema.has("properties");
        }
        if (type.isTextual()) {
            return "object".equals(type.textValue());
        }
        if (type.isArray()) {
            for (JsonNode candidate : type) {
                if (candidate.isTextual() && "object".equals(candidate.textValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean allowsDelimiterCompletion(Generation generation) {
        String finishReason = generation == null || generation.getMetadata() == null
                ? ""
                : generation.getMetadata().getFinishReason();
        return !"length".equalsIgnoreCase(StringUtils.trimWhitespace(finishReason));
    }

    private ParsedJson parseJsonCompatible(
            String json,
            boolean allowDelimiterCompletion
    ) throws JsonProcessingException {
        JsonProcessingException strictFailure;
        try {
            return new ParsedJson(parseJsonStrict(json), List.of());
        } catch (JsonProcessingException exception) {
            strictFailure = exception;
        }

        if (json == null || json.length() > MAX_ARGUMENT_NORMALIZATION_CHARS) {
            throw strictFailure;
        }

        String candidate = unwrapJsonCodeFence(json);
        List<String> repairs = new ArrayList<>();
        if (!candidate.equals(json)) {
            repairs.add("markdown_fence");
            try {
                return new ParsedJson(parseJsonStrict(candidate), List.copyOf(repairs));
            } catch (JsonProcessingException ignored) {
                // Continue with the bounded compatibility parser.
            }
        }

        String withoutCallSuffix = stripTrailingCallSuffix(candidate);
        if (withoutCallSuffix != null) {
            try {
                JsonNode parsed = parseJsonStrict(withoutCallSuffix);
                repairs.add("trailing_call_suffix");
                return new ParsedJson(parsed, List.copyOf(repairs));
            } catch (JsonProcessingException ignored) {
                try {
                    JsonNode parsed = parseJsonPermissive(withoutCallSuffix);
                    repairs.add("trailing_call_suffix");
                    repairs.add("permissive_json");
                    return new ParsedJson(parsed, List.copyOf(repairs));
                } catch (JsonProcessingException ignoredAgain) {
                    // Only accept the suffix removal when the remainder is one JSON value.
                }
            }
        }

        try {
            JsonNode parsed = parseJsonPermissive(candidate);
            repairs.add("permissive_json");
            return new ParsedJson(parsed, List.copyOf(repairs));
        } catch (JsonProcessingException ignored) {
            // A final, unambiguous delimiter completion is attempted below.
        }

        String completed = allowDelimiterCompletion
                ? appendMissingJsonClosers(candidate)
                : null;
        if (completed != null) {
            repairs.add("missing_closers");
            try {
                return new ParsedJson(parseJsonStrict(completed), List.copyOf(repairs));
            } catch (JsonProcessingException ignored) {
                try {
                    JsonNode parsed = parseJsonPermissive(completed);
                    repairs.add("permissive_json");
                    return new ParsedJson(parsed, List.copyOf(repairs));
                } catch (JsonProcessingException ignoredAgain) {
                    // Preserve the original strict parse error for diagnostics.
                }
            }
        }
        throw strictFailure;
    }

    /**
     * Some OpenAI-compatible models serialize the function call notation around
     * the JSON arguments and leave a single trailing {@code )} or {@code );}.
     * Removing that suffix is safe only if the remaining text parses as exactly
     * one complete JSON value; the caller enforces that condition.
     */
    private String stripTrailingCallSuffix(String json) {
        String trimmed = StringUtils.trimWhitespace(json);
        if (trimmed.endsWith(");")) {
            trimmed = StringUtils.trimTrailingWhitespace(
                    trimmed.substring(0, trimmed.length() - 2));
        } else if (trimmed.endsWith(")")) {
            trimmed = StringUtils.trimTrailingWhitespace(
                    trimmed.substring(0, trimmed.length() - 1));
        } else {
            return null;
        }
        return trimmed.isEmpty()
                || (trimmed.charAt(0) != '{' && trimmed.charAt(0) != '[')
                ? null
                : trimmed;
    }

    private String unwrapJsonCodeFence(String json) {
        String trimmed = StringUtils.trimWhitespace(json);
        if (!trimmed.startsWith("```") || !trimmed.endsWith("```")) {
            return json;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0) {
            return json;
        }
        String language = trimmed.substring(3, firstLineEnd).trim();
        if (StringUtils.hasText(language) && !"json".equalsIgnoreCase(language)) {
            return json;
        }
        String content = trimmed.substring(firstLineEnd + 1, trimmed.length() - 3);
        return StringUtils.trimWhitespace(content);
    }

    private String appendMissingJsonClosers(String json) {
        String trimmed = StringUtils.trimWhitespace(json);
        if (trimmed.isEmpty()
                || (trimmed.charAt(0) != '{' && trimmed.charAt(0) != '[')) {
            return null;
        }

        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        char quote = 0;
        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    inString = false;
                }
                continue;
            }
            if (current == '"' || current == '\'') {
                inString = true;
                quote = current;
                continue;
            }
            if (current == '{' || current == '[') {
                stack.push(current);
                continue;
            }
            if (current == '}' || current == ']') {
                if (stack.isEmpty() || !matchesCloser(stack.pop(), current)) {
                    return null;
                }
            }
        }
        if (inString || escaped || stack.isEmpty()
                || stack.size() > MAX_APPENDED_JSON_CLOSERS) {
            return null;
        }
        char last = trimmed.charAt(trimmed.length() - 1);
        if (last == ':' || last == ',') {
            return null;
        }

        StringBuilder completed = new StringBuilder(trimmed);
        while (!stack.isEmpty()) {
            completed.append(stack.pop() == '{' ? '}' : ']');
        }
        return completed.toString();
    }

    private boolean matchesCloser(char opener, char closer) {
        return (opener == '{' && closer == '}')
                || (opener == '[' && closer == ']');
    }

    private JsonNode parseJsonPermissive(String json) throws JsonProcessingException {
        try (JsonParser parser = COMPAT_ARGUMENT_MAPPER.createParser(json)) {
            JsonNode parsed = COMPAT_ARGUMENT_MAPPER.readTree(parser);
            if (parser.nextToken() != null) {
                throw new JsonProcessingException("Trailing JSON content") {
                };
            }
            return parsed;
        } catch (java.io.IOException exception) {
            if (exception instanceof JsonProcessingException jsonProcessingException) {
                throw jsonProcessingException;
            }
            throw new JsonProcessingException(exception.getMessage(), exception) {
            };
        }
    }

    private JsonNode parseJsonStrict(String json) throws JsonProcessingException {
        try (JsonParser parser = JacksonConfig.OBJECT_MAPPER.createParser(json)) {
            JsonNode parsed = JacksonConfig.OBJECT_MAPPER.readTree(parser);
            if (parser.nextToken() != null) {
                throw new JsonProcessingException("Trailing JSON content") {
                };
            }
            return parsed;
        } catch (java.io.IOException exception) {
            if (exception instanceof JsonProcessingException jsonProcessingException) {
                throw jsonProcessingException;
            }
            throw new JsonProcessingException(exception.getMessage(), exception) {
            };
        }
    }

    private String jsonParseFailureReason(JsonProcessingException exception) {
        JsonLocation location = exception == null ? null : exception.getLocation();
        long offset = location == null ? -1 : location.getCharOffset();
        if (offset >= 0) {
            return "arguments were not valid JSON at char " + offset;
        }
        return "arguments were not valid JSON";
    }

    private ObjectNode summarizeSchemaShape(JsonNode schema) {
        ObjectNode summary = JacksonConfig.OBJECT_MAPPER.createObjectNode();
        JsonNode properties = schema == null ? null : schema.get("properties");
        if (properties == null || !properties.isObject()) {
            summary.put("type", "object");
            return summary;
        }
        var topLevel = summary.putArray("topLevelProperties");
        properties.fieldNames().forEachRemaining(topLevel::add);
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            summary.set("requiredTopLevelProperties", required.deepCopy());
        }
        ObjectNode nested = summary.putObject("nestedObjectProperties");
        properties.fields().forEachRemaining(field -> {
            JsonNode resolved = resolveSchemaReference(field.getValue(), schema);
            JsonNode nestedProperties = resolved.get("properties");
            if (nestedProperties == null || !nestedProperties.isObject()) {
                return;
            }
            var names = nested.putArray(field.getKey());
            nestedProperties.fieldNames().forEachRemaining(names::add);
        });
        if (nested.isEmpty()) {
            summary.remove("nestedObjectProperties");
        }
        return summary;
    }

    private String argumentDigest(String arguments) {
        if (arguments == null) {
            return "none";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(arguments.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(arguments.hashCode());
        }
    }

    /**
     * Keeps only JSON punctuation and coarse token markers so malformed calls
     * can be diagnosed without writing payloads, indicators or user data to logs.
     */
    private String argumentStructurePreview(String arguments) {
        if (arguments == null) {
            return "";
        }
        StringBuilder structure = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        boolean token = false;
        char quote = 0;
        for (int index = 0; index < arguments.length(); index++) {
            char current = arguments.charAt(index);
            if (inString) {
                if (!token) {
                    structure.append('#');
                    token = true;
                }
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    structure.append(quote);
                    inString = false;
                    token = false;
                }
                continue;
            }
            if (current == '"' || current == '\'') {
                structure.append(current);
                inString = true;
                quote = current;
                token = false;
            } else if (Character.isLetterOrDigit(current)
                    || current == '_' || current == '-' || current == '.') {
                if (!token) {
                    structure.append('#');
                    token = true;
                }
            } else {
                token = false;
                if (Character.isWhitespace(current)) {
                    if (!structure.isEmpty()
                            && structure.charAt(structure.length() - 1) != ' ') {
                        structure.append(' ');
                    }
                } else {
                    structure.append(current);
                }
            }
        }
        if (structure.length() <= MAX_ARGUMENT_STRUCTURE_PREVIEW_CHARS) {
            return structure.toString();
        }
        int side = (MAX_ARGUMENT_STRUCTURE_PREVIEW_CHARS - 1) / 2;
        return structure.substring(0, side)
                + "…"
                + structure.substring(structure.length() - side);
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private record InvalidToolCall(AssistantMessage.ToolCall toolCall, String reason) {
    }

    private record ParsedJson(JsonNode value, List<String> repairs) {
    }

    private record NormalizedArguments(
            String arguments,
            boolean changed,
            List<String> repairs
    ) {
    }
}
