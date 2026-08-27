package com.coolxer.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticVersionTest {

    @Test
    void comparesSemVerPrecedence() {
        assertThat(SemanticVersion.parse("1.0.1"))
                .isGreaterThan(SemanticVersion.parse("1.0.0"));
        assertThat(SemanticVersion.parse("1.0.0"))
                .isGreaterThan(SemanticVersion.parse("1.0.0-rc.1"));
        assertThat(SemanticVersion.parse("1.0.0-rc.10"))
                .isGreaterThan(SemanticVersion.parse("1.0.0-rc.2"));
        assertThat(SemanticVersion.parse("1.0.0+build.2"))
                .isEqualByComparingTo(SemanticVersion.parse("1.0.0+build.1"));
    }

    @Test
    void rejectsNonSemVerAndLeadingZeros() {
        assertThatThrownBy(() -> SemanticVersion.parse("1.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanticVersion.parse("01.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanticVersion.parse("1.0.0-01"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
