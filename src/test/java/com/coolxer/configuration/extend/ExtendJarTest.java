package com.coolxer.configuration.extend;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtendJarTest {

    @Test
    void buildsNamespacedPluginPath() {
        ExtendJar extendJar = new ExtendJar(
                "com.coolxer.plugin.asset",
                null,
                "com.coolxer.plugin"
        );

        assertThat(extendJar.fullPathBuild("/rule/list"))
                .isEqualTo("/api/v1/plugin/com.coolxer.plugin.asset/rule/list");
        assertThat(extendJar.getBeanNamePrefix())
                .isEqualTo("com.coolxer.plugin.asset.");
    }
}
