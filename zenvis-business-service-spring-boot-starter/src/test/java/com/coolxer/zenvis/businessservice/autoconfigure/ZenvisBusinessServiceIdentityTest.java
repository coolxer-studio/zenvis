package com.coolxer.zenvis.businessservice.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ZenvisBusinessServiceIdentityTest {

    @Test
    void derivesStableAndValidInstanceId() {
        ZenvisBusinessServiceProperties properties = new ZenvisBusinessServiceProperties();
        properties.setServiceCode("synap server");
        properties.setServiceName("Synap Server");
        properties.setHost("pod name");
        properties.setPort(11099);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "fallback-service");

        BusinessServiceIdentity identity =
                new ZenvisBusinessServiceIdentity(properties, environment, null).current();

        assertThat(identity.serviceCode()).isEqualTo("synap-server");
        assertThat(identity.instanceId()).isEqualTo("synap-server-pod-name-11099");
        assertThat(identity.instanceId()).matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    }

    @Test
    void usesApplicationNameProfileAndRuntimePortAsFallbacks() {
        ZenvisBusinessServiceProperties properties = new ZenvisBusinessServiceProperties();
        properties.setHost("pod-1");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "order-api")
                .withProperty("local.server.port", "18080");
        environment.setActiveProfiles("test");

        BusinessServiceIdentity identity =
                new ZenvisBusinessServiceIdentity(properties, environment, null).current();

        assertThat(identity.serviceCode()).isEqualTo("order-api");
        assertThat(identity.serviceName()).isEqualTo("order-api");
        assertThat(identity.environment()).isEqualTo("test");
        assertThat(identity.port()).isEqualTo(18080);
        assertThat(identity.instanceId()).isEqualTo("order-api-pod-1-18080");
    }

    @Test
    void normalizesAndLimitsConfiguredInstanceId() {
        ZenvisBusinessServiceProperties properties = new ZenvisBusinessServiceProperties();
        properties.setInstanceId("@" + "instance id/".repeat(20));

        BusinessServiceIdentity identity =
                new ZenvisBusinessServiceIdentity(properties, new MockEnvironment(), null).current();

        assertThat(identity.instanceId()).hasSize(128);
        assertThat(identity.instanceId()).matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    }
}
