package com.coolxer.service.dih.mcp;

import com.coolxer.configuration.JacksonConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Normalizes Spring AI tool schemas for OpenAI-compatible providers.
 *
 * <p>Spring AI 1.1.0-M4 can emit nested {@code $defs} while still producing
 * document-root {@code $ref} pointers. Strict providers reject that combination,
 * so definitions are resolved inline before the schema is sent to a model.</p>
 */
public final class ToolSchemaNormalizer {

    private static final String DEFINITIONS = "$defs";
    private static final String LEGACY_DEFINITIONS = "definitions";
    private static final String REF = "$ref";
    private static final String DEFINITIONS_REF_PREFIX = "#/$defs/";
    private static final String LEGACY_DEFINITIONS_REF_PREFIX = "#/definitions/";
    private static final String RECURSIVE_SCHEMA_DESCRIPTION = "递归结构";
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private ToolSchemaNormalizer() {
    }

    public static String normalize(String schema) {
        if (!StringUtils.hasText(schema)) {
            return schema;
        }
        return CACHE.computeIfAbsent(schema, ToolSchemaNormalizer::normalizeUncached);
    }

    private static String normalizeUncached(String schema) {
        try {
            JsonNode root = JacksonConfig.OBJECT_MAPPER.readTree(schema);
            if (root == null || !root.isObject()) {
                return schema;
            }
            Map<String, JsonNode> definitions = new LinkedHashMap<>();
            collectDefinitions(root, definitions);
            if (definitions.isEmpty()) {
                return schema;
            }
            JsonNode normalized = inlineDefinitions(root, definitions, new LinkedHashSet<>());
            removeDefinitions(normalized);
            return JacksonConfig.OBJECT_MAPPER.writeValueAsString(normalized);
        } catch (Exception ignored) {
            return schema;
        }
    }

    private static void collectDefinitions(JsonNode node, Map<String, JsonNode> definitions) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            collectDefinitionContainer(object.get(DEFINITIONS), definitions);
            collectDefinitionContainer(object.get(LEGACY_DEFINITIONS), definitions);
            object.fields().forEachRemaining(entry -> {
                if (!DEFINITIONS.equals(entry.getKey())
                        && !LEGACY_DEFINITIONS.equals(entry.getKey())) {
                    collectDefinitions(entry.getValue(), definitions);
                }
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectDefinitions(child, definitions));
        }
    }

    private static void collectDefinitionContainer(JsonNode container,
                                                   Map<String, JsonNode> definitions) {
        if (container == null || !container.isObject()) {
            return;
        }
        container.fields().forEachRemaining(entry ->
                definitions.putIfAbsent(entry.getKey(), entry.getValue().deepCopy()));
    }

    private static JsonNode inlineDefinitions(JsonNode node,
                                              Map<String, JsonNode> definitions,
                                              Set<String> resolvingDefinitions) {
        if (node == null) {
            return JacksonConfig.OBJECT_MAPPER.nullNode();
        }
        if (node.isArray()) {
            ArrayNode array = JacksonConfig.OBJECT_MAPPER.createArrayNode();
            node.forEach(child ->
                    array.add(inlineDefinitions(child, definitions, resolvingDefinitions)));
            return array;
        }
        if (!node.isObject()) {
            return node.deepCopy();
        }

        ObjectNode object = (ObjectNode) node;
        String referenceName = localDefinitionName(object.path(REF).asText(""));
        JsonNode definition = referenceName == null ? null : definitions.get(referenceName);
        if (definition != null) {
            if (!resolvingDefinitions.add(referenceName)) {
                return JacksonConfig.OBJECT_MAPPER.createObjectNode()
                        .put("type", "object")
                        .put("description", RECURSIVE_SCHEMA_DESCRIPTION);
            }
            JsonNode resolved = inlineDefinitions(
                    definition, definitions, resolvingDefinitions);
            resolvingDefinitions.remove(referenceName);
            if (object.size() == 1 || !resolved.isObject()) {
                return resolved;
            }
            ObjectNode merged = (ObjectNode) resolved.deepCopy();
            object.fields().forEachRemaining(entry -> {
                if (!REF.equals(entry.getKey())
                        && !DEFINITIONS.equals(entry.getKey())
                        && !LEGACY_DEFINITIONS.equals(entry.getKey())) {
                    merged.set(entry.getKey(), inlineDefinitions(
                            entry.getValue(), definitions, resolvingDefinitions));
                }
            });
            return merged;
        }

        ObjectNode result = JacksonConfig.OBJECT_MAPPER.createObjectNode();
        object.fields().forEachRemaining(entry -> {
            if (!DEFINITIONS.equals(entry.getKey())
                    && !LEGACY_DEFINITIONS.equals(entry.getKey())) {
                result.set(entry.getKey(), inlineDefinitions(
                        entry.getValue(), definitions, resolvingDefinitions));
            }
        });
        return result;
    }

    private static String localDefinitionName(String reference) {
        if (!StringUtils.hasText(reference)) {
            return null;
        }
        String name;
        if (reference.startsWith(DEFINITIONS_REF_PREFIX)) {
            name = reference.substring(DEFINITIONS_REF_PREFIX.length());
        } else if (reference.startsWith(LEGACY_DEFINITIONS_REF_PREFIX)) {
            name = reference.substring(LEGACY_DEFINITIONS_REF_PREFIX.length());
        } else {
            return null;
        }
        if (!StringUtils.hasText(name) || name.contains("/")) {
            return null;
        }
        return name.replace("~1", "/").replace("~0", "~");
    }

    private static void removeDefinitions(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.remove(DEFINITIONS);
            object.remove(LEGACY_DEFINITIONS);
            object.fields().forEachRemaining(entry -> removeDefinitions(entry.getValue()));
            return;
        }
        if (node.isArray()) {
            node.forEach(ToolSchemaNormalizer::removeDefinitions);
        }
    }
}
