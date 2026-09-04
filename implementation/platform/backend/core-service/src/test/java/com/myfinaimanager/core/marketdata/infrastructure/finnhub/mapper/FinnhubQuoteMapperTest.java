package com.myfinaimanager.core.marketdata.infrastructure.finnhub.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataUnavailableException;
import com.myfinaimanager.core.marketdata.domain.model.DataSource;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.MarketPrice;
import com.myfinaimanager.core.marketdata.domain.model.ObservedAtSource;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.dto.FinnhubQuoteResponse;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FinnhubQuoteMapperTest {

    private final FinnhubQuoteMapper mapper = new FinnhubQuoteMapper();
    private static final InstrumentIdentifier AAPL =
            new InstrumentIdentifier("AAPL", "XNAS", SupportedCurrency.USD);
    private static final Instant RETRIEVAL = Instant.parse("2026-09-03T10:00:00Z");

    private static FinnhubQuoteResponse quote(BigDecimal c, Long t) {
        return new FinnhubQuoteResponse(c, null, null, null, null, null, null, t);
    }

    @Test
    void maps_current_price_currency_and_provider_timestamp() {
        MarketPrice p = mapper.map(quote(new BigDecimal("187.32"), 1725000000L), AAPL, RETRIEVAL);

        assertThat(p.price()).isEqualByComparingTo("187.32");
        assertThat(p.currency()).isEqualTo(SupportedCurrency.USD);
        assertThat(p.instrument()).isEqualTo(AAPL);
        assertThat(p.observedAt()).isEqualTo(Instant.ofEpochSecond(1725000000L));
        assertThat(p.observedAtSource()).isEqualTo(ObservedAtSource.PROVIDER_TIMESTAMP);
        assertThat(p.source()).isEqualTo(DataSource.FINNHUB);
    }

    @Test
    void falls_back_to_retrieval_time_when_the_provider_timestamp_is_absent_or_zero() {
        assertThat(mapper.map(quote(new BigDecimal("10"), null), AAPL, RETRIEVAL).observedAtSource())
                .isEqualTo(ObservedAtSource.RETRIEVAL_TIME);
        assertThat(mapper.map(quote(new BigDecimal("10"), 0L), AAPL, RETRIEVAL).observedAt())
                .isEqualTo(RETRIEVAL);
    }

    @Test
    void a_zero_or_missing_price_is_unavailable_never_a_zero_market_price() {
        assertThatThrownBy(() -> mapper.map(quote(BigDecimal.ZERO, 1725000000L), AAPL, RETRIEVAL))
                .isInstanceOf(MarketDataUnavailableException.class);
        assertThatThrownBy(() -> mapper.map(quote(null, 1725000000L), AAPL, RETRIEVAL))
                .isInstanceOf(MarketDataUnavailableException.class);
        assertThatThrownBy(() -> mapper.map(quote(new BigDecimal("-1"), 1725000000L), AAPL, RETRIEVAL))
                .isInstanceOf(MarketDataUnavailableException.class);
    }
}
