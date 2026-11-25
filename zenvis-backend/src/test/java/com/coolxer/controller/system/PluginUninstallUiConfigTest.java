package com.coolxer.controller.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PluginUninstallUiConfigTest {

    private static final Path CONFIG_PATH =
            Path.of("../deploy/open_config/plugin_config/index.json");

    @Test
    void uninstallDialogSendsKeepDataAndShowsBothPolicies() throws IOException {
        JsonNode root = new ObjectMapper().readTree(CONFIG_PATH.toFile());
        JsonNode dialog = findUninstallDialog(root);
        assertThat(dialog).isNotNull();

        assertThat(dialog.path("data").path("keep_data").asText())
                .isEqualTo("${data_retained}");

        JsonNode checkbox = findObject(dialog, "name", "keep_data");
        assertThat(checkbox).isNotNull();
        assertThat(checkbox.path("type").asText()).isEqualTo("checkbox");

        JsonNode ajax = findUninstallAjax(dialog);
        assertThat(ajax).isNotNull();
        assertThat(ajax.path("api").path("data").path("keep_data").asText())
                .isEqualTo("${keep_data}");

        assertThat(findAlert(dialog, "卸载将删除插件数据且不可恢复").path("visibleOn").asText())
                .contains("!data.keep_data");
        assertThat(findAlert(dialog, "已选择保留插件数据").path("visibleOn").asText())
                .contains("data.keep_data");
    }

    private JsonNode findUninstallDialog(JsonNode node) {
        if (node.isObject() && "dialog_uninstall".equals(node.path("dialog").path("id").asText(null))) {
            return node.path("dialog");
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findUninstallDialog(child);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                JsonNode found = findUninstallDialog(entry.getValue());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private JsonNode findUninstallAjax(JsonNode node) {
        if (node.isObject()
                && "ajax".equals(node.path("actionType").asText(null))
                && node.path("api").path("url").asText("").endsWith("/uninstall")) {
            return node;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findUninstallAjax(child);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                JsonNode found = findUninstallAjax(entry.getValue());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private JsonNode findAlert(JsonNode node, String title) {
        JsonNode alert = findObject(node, "title", title);
        assertThat(alert).isNotNull();
        return alert;
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
