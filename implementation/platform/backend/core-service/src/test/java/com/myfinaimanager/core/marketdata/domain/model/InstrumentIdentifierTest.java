package com.myfinaimanager.core.marketdata.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InstrumentIdentifierTest {

    @Test
    void uppercases_and_trims_ticker_and_market() {
        InstrumentIdentifier id = new InstrumentIdentifier("  aapl ", " xnas ", SupportedCurrency.USD);
        assertThat(id.ticker()).isEqualTo("AAPL");
        assertThat(id.market()).isEqualTo("XNAS");
        assertThat(id.currency()).isEqualTo(SupportedCurrency.USD);
    }

    @Test
    void rejects_a_blank_ticker() {
        assertThatThrownBy(() -> new InstrumentIdentifier("  ", "XNAS", SupportedCurrency.USD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_null_market_or_currency() {
        assertThatThrownBy(() -> new InstrumentIdentifier("AAPL", null, SupportedCurrency.USD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstrumentIdentifier("AAPL", "XNAS", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equal_by_normalized_value() {
        assertThat(new InstrumentIdentifier("aapl", "xnas", SupportedCurrency.USD))
                .isEqualTo(new InstrumentIdentifier("AAPL", "XNAS", SupportedCurrency.USD));
    }
}
