package com.myfinaimanager.core.platform.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PlatformVersionTest {

    @Test
    void preservesAValidVersionIdentifier() {
        PlatformVersion version = PlatformVersion.of("0.1.0");

        assertThat(version.value()).isEqualTo("0.1.0");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(PlatformVersion.of("  0.1.0  ").value()).isEqualTo("0.1.0");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void rejectsNullOrBlankIdentifiers(String candidate) {
        assertThatThrownBy(() -> PlatformVersion.of(candidate))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valueObjectsWithTheSameIdentifierAreEqual() {
        assertThat(PlatformVersion.of("0.1.0")).isEqualTo(PlatformVersion.of("0.1.0"));
    }
}
