package com.myfinaimanager.core.marketdata.infrastructure.frankfurter.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myfinaimanager.core.marketdata.domain.exceptions.FxRateUnavailableException;
import com.myfinaimanager.core.marketdata.domain.model.DataSource;
import com.myfinaimanager.core.marketdata.domain.model.FxRate;
import com.myfinaimanager.core.marketdata.domain.model.ObservedAtSource;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.infrastructure.frankfurter.dto.FrankfurterRatesResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FrankfurterFxRateMapperTest {

    private static final Instant RETRIEVED = Instant.parse("2026-09-04T12:00:00Z");
    private final FrankfurterFxRateMapper mapper = new FrankfurterFxRateMapper();

    @Test
    void maps_rate_date_and_source() {
        FrankfurterRatesResponse dto = new FrankfurterRatesResponse(
                BigDecimal.ONE, "USD", "2026-09-03", Map.of("EUR", new BigDecimal("0.85")));

        FxRate rate = mapper.map(dto, SupportedCurrency.USD, SupportedCurrency.EUR, RETRIEVED);

        assertThat(rate.rate()).isEqualByComparingTo("0.85");
        assertThat(rate.observedAt()).isEqualTo(Instant.parse("2026-09-03T00:00:00Z"));
        assertThat(rate.observedAtSource()).isEqualTo(ObservedAtSource.PROVIDER_TIMESTAMP);
        assertThat(rate.source()).isEqualTo(DataSource.FRANKFURTER);
    }

    @Test
    void falls_back_to_retrieval_time_when_the_date_is_absent_or_unparseable() {
        FxRate blank = mapper.map(new FrankfurterRatesResponse(BigDecimal.ONE, "USD", null,
                Map.of("EUR", new BigDecimal("0.85"))), SupportedCurrency.USD, SupportedCurrency.EUR, RETRIEVED);
        assertThat(blank.observedAt()).isEqualTo(RETRIEVED);
        assertThat(blank.observedAtSource()).isEqualTo(ObservedAtSource.RETRIEVAL_TIME);

        FxRate bad = mapper.map(new FrankfurterRatesResponse(BigDecimal.ONE, "USD", "not-a-date",
                Map.of("EUR", new BigDecimal("0.85"))), SupportedCurrency.USD, SupportedCurrency.EUR, RETRIEVED);
        assertThat(bad.observedAt()).isEqualTo(RETRIEVED);
    }

    @Test
    void a_missing_or_non_positive_target_rate_raises_unavailable_never_a_default() {
        assertThatThrownBy(() -> mapper.map(new FrankfurterRatesResponse(BigDecimal.ONE, "USD", "2026-09-03",
                Map.of("GBP", new BigDecimal("0.79"))), SupportedCurrency.USD, SupportedCurrency.EUR, RETRIEVED))
                .isInstanceOf(FxRateUnavailableException.class);

        assertThatThrownBy(() -> mapper.map(new FrankfurterRatesResponse(BigDecimal.ONE, "USD", "2026-09-03",
                Map.of("EUR", BigDecimal.ZERO)), SupportedCurrency.USD, SupportedCurrency.EUR, RETRIEVED))
                .isInstanceOf(FxRateUnavailableException.class);

        assertThatThrownBy(() -> mapper.map(new FrankfurterRatesResponse(BigDecimal.ONE, "USD", "2026-09-03", null),
                SupportedCurrency.USD, SupportedCurrency.EUR, RETRIEVED))
                .isInstanceOf(FxRateUnavailableException.class);
    }
}
