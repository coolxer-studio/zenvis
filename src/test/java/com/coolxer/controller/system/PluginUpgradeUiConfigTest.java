package com.coolxer.controller.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PluginUpgradeUiConfigTest {

    private static final Path CONFIG_PATH =
            Path.of("deploy/open_config/plugin_config/index.json");

    @Test
    void uploadedUpgradePackageEnablesUpgradeAction() throws IOException {
        JsonNode root = new ObjectMapper().readTree(CONFIG_PATH.toFile());
        JsonNode upgradeButton = findObject(root, "label", "升级");
        assertNotNull(upgradeButton);

        JsonNode dialog = upgradeButton.path("dialog");
        JsonNode startButton = findObject(dialog, "label", "开始升级");
        JsonNode fileInput = findObject(dialog, "type", "input-file");
        assertNotNull(startButton);
        assertNotNull(fileInput);

        assertEquals("!plugin_path", startButton.path("disabledOn").asText());
        JsonNode setValue = fileInput.path("onEvent").path("success").path("actions").path(0);
        assertEquals("setValue", setValue.path("actionType").asText());
        assertEquals("dialog_upgrade", setValue.path("componentId").asText());
        assertEquals("${result.plugin_path}",
                setValue.path("args").path("value").path("plugin_path").asText());
    }

    private JsonNode findObject(JsonNode node, String field, String value) {
        if (node.isObject() && value.equals(node.path(field).asText(null))) {
            return node;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findObject(child, field, value);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                JsonNode found = findObject(entry.getValue(), field, value);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
