package com.myfinaimanager.core.marketdata.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SupportedCurrencyTest {

    @Test
    void is_supported_is_case_and_whitespace_insensitive_and_null_safe() {
        assertThat(SupportedCurrency.isSupported(" eur ")).isTrue();
        assertThat(SupportedCurrency.isSupported("USD")).isTrue();
        assertThat(SupportedCurrency.isSupported("gbp")).isFalse();
        assertThat(SupportedCurrency.isSupported(null)).isFalse();
    }

    @Test
    void parse_or_null_returns_the_enum_or_null() {
        assertThat(SupportedCurrency.parseOrNull("usd")).isEqualTo(SupportedCurrency.USD);
        assertThat(SupportedCurrency.parseOrNull(" EUR ")).isEqualTo(SupportedCurrency.EUR);
        assertThat(SupportedCurrency.parseOrNull("JPY")).isNull();
        assertThat(SupportedCurrency.parseOrNull(null)).isNull();
        assertThat(SupportedCurrency.parseOrNull("  ")).isNull();
    }
}
