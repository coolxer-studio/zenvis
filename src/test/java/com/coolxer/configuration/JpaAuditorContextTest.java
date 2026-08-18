package com.coolxer.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JpaAuditorContextTest {

    @Test
    void scopesAndRestoresBackgroundAuditor() {
        assertThat(JpaAuditorContext.current()).isEmpty();

        Integer nested = JpaAuditorContext.callWith(23, () -> {
            assertThat(JpaAuditorContext.current()).contains(23);
            return JpaAuditorContext.callWith(42, () -> JpaAuditorContext.current().orElseThrow());
        });

        assertThat(nested).isEqualTo(42);
        assertThat(JpaAuditorContext.current()).isEmpty();
    }

    @Test
    void jpaAuditingPrefersBackgroundOwnerWithoutRequestContext() {
        JpaAuditingConfiguration configuration = new JpaAuditingConfiguration();

        assertThat(JpaAuditorContext.callWith(23, configuration::getCurrentAuditor))
                .contains(23);
    }
}
