package com.coolxer.zenvis.businessservice.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZenvisBusinessServicePropertiesTest {

    @Test
    void reportingIsEnabledByDefault() {
        assertThat(new ZenvisBusinessServiceProperties().isEnabled()).isTrue();
    }
}
