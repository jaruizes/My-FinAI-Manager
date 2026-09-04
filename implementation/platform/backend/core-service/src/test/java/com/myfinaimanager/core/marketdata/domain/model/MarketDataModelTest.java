package com.myfinaimanager.core.marketdata.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MarketDataModelTest {

    private static final InstrumentIdentifier AAPL =
            new InstrumentIdentifier("AAPL", "XNAS", SupportedCurrency.USD);
    private static final Instant T = Instant.parse("2026-09-03T10:00:00Z");

    @Test
    void market_price_holds_a_positive_decimal_and_freshness_metadata() {
        MarketPrice p = new MarketPrice(AAPL, new BigDecimal("187.32"), SupportedCurrency.USD,
                T, ObservedAtSource.PROVIDER_TIMESTAMP, DataSource.FINNHUB);
        assertThat(p.price()).isEqualByComparingTo("187.32");
        assertThat(p.observedAtSource()).isEqualTo(ObservedAtSource.PROVIDER_TIMESTAMP);
        assertThat(p.source()).isEqualTo(DataSource.FINNHUB);
    }

    @Test
    void market_price_rejects_a_null_or_non_positive_price() {
        assertThatThrownBy(() -> new MarketPrice(AAPL, null, SupportedCurrency.USD, T,
                ObservedAtSource.RETRIEVAL_TIME, DataSource.FINNHUB))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarketPrice(AAPL, BigDecimal.ZERO, SupportedCurrency.USD, T,
                ObservedAtSource.RETRIEVAL_TIME, DataSource.FINNHUB))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarketPrice(AAPL, new BigDecimal("-1"), SupportedCurrency.USD, T,
                ObservedAtSource.RETRIEVAL_TIME, DataSource.FINNHUB))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fx_rate_rejects_non_positive_rate_and_same_from_to() {
        assertThatThrownBy(() -> new FxRate(SupportedCurrency.USD, SupportedCurrency.EUR, BigDecimal.ZERO,
                T, ObservedAtSource.RETRIEVAL_TIME, DataSource.FINNHUB))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FxRate(SupportedCurrency.USD, SupportedCurrency.USD, new BigDecimal("1.0"),
                T, ObservedAtSource.RETRIEVAL_TIME, DataSource.FINNHUB))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fx_rate_holds_a_positive_decimal() {
        FxRate r = new FxRate(SupportedCurrency.USD, SupportedCurrency.EUR, new BigDecimal("0.9231"),
                T, ObservedAtSource.RETRIEVAL_TIME, DataSource.FINNHUB);
        assertThat(r.rate()).isEqualByComparingTo("0.9231");
        assertThat(r.from()).isEqualTo(SupportedCurrency.USD);
        assertThat(r.to()).isEqualTo(SupportedCurrency.EUR);
    }

    @Test
    void instrument_profile_always_has_a_non_null_sector() {
        InstrumentProfile withSector = new InstrumentProfile("AAPL", "Apple Inc", Sector.of("Technology"),
                "Consumer Electronics", SupportedCurrency.USD, "NASDAQ NMS - GLOBAL SELECT MARKET",
                DataSource.FINNHUB, T);
        assertThat(withSector.sector().isClassified()).isTrue();

        InstrumentProfile noSector = new InstrumentProfile("AAPL", "Apple Inc", Sector.UNCLASSIFIED,
                null, null, null, DataSource.FINNHUB, null);
        assertThat(noSector.sector()).isEqualTo(Sector.UNCLASSIFIED);

        assertThatThrownBy(() -> new InstrumentProfile("AAPL", "Apple Inc", null, null, null, null,
                DataSource.FINNHUB, null))
                .isInstanceOf(NullPointerException.class);
    }
}
