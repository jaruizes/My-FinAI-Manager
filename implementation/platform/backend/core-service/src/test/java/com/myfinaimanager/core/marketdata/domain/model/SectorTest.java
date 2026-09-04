package com.myfinaimanager.core.marketdata.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SectorTest {

    @Test
    void of_a_non_blank_value_is_classified_and_trimmed() {
        Sector s = Sector.of("  Technology ");
        assertThat(s.isClassified()).isTrue();
        assertThat(s.classification()).isEqualTo("Technology");
        assertThat(s).isNotEqualTo(Sector.UNCLASSIFIED);
    }

    @Test
    void of_null_or_blank_is_unclassified() {
        assertThat(Sector.of(null)).isEqualTo(Sector.UNCLASSIFIED);
        assertThat(Sector.of("   ")).isEqualTo(Sector.UNCLASSIFIED);
        assertThat(Sector.UNCLASSIFIED.isClassified()).isFalse();
        assertThat(Sector.UNCLASSIFIED.classification()).isNull();
    }

    @Test
    void equals_and_hashCode_behave_by_value() {
        assertThat(Sector.of("Tech")).isEqualTo(Sector.of("Tech"))
                .hasSameHashCodeAs(Sector.of("Tech"));
        assertThat(Sector.of("Tech")).isNotEqualTo("Tech");           // not a Sector
        assertThat(Sector.of("Tech")).isNotEqualTo(Sector.of("Health"));
        assertThat(Sector.of("Tech").toString()).isEqualTo("Tech");
        assertThat(Sector.UNCLASSIFIED.toString()).isEqualTo("UNCLASSIFIED");
    }
}
