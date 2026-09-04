package com.myfinaimanager.core.marketdata.infrastructure.frankfurter.mapper;

import com.myfinaimanager.core.marketdata.domain.exceptions.FxRateUnavailableException;
import com.myfinaimanager.core.marketdata.domain.model.DataSource;
import com.myfinaimanager.core.marketdata.domain.model.FxRate;
import com.myfinaimanager.core.marketdata.domain.model.ObservedAtSource;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.infrastructure.frankfurter.dto.FrankfurterRatesResponse;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * Maps a Frankfurter {@code /v1/latest?base={from}} response to a provider-neutral {@link FxRate}
 * for the requested {@code from -> to} pair. A missing or non-positive target rate raises
 * {@link FxRateUnavailableException} — never a default of {@code 1} or {@code 0} (FR-010).
 * Frankfurter dates its rates (ECB publication date), so {@code observedAt} is that date at
 * start-of-day UTC and {@code observedAtSource} is {@link ObservedAtSource#PROVIDER_TIMESTAMP}.
 */
@Component
public class FrankfurterFxRateMapper {

    public FxRate map(FrankfurterRatesResponse response,
                      SupportedCurrency from,
                      SupportedCurrency to,
                      Instant retrievalInstant) {
        BigDecimal rate = response.rates() == null ? null : response.rates().get(to.name());
        if (rate == null || rate.signum() <= 0) {
            throw new FxRateUnavailableException(
                    "provider returned no usable rate for " + from + "->" + to);
        }
        Instant observedAt = parseDate(response.date(), retrievalInstant);
        ObservedAtSource observedAtSource = observedAt.equals(retrievalInstant)
                ? ObservedAtSource.RETRIEVAL_TIME
                : ObservedAtSource.PROVIDER_TIMESTAMP;
        return new FxRate(from, to, rate, observedAt, observedAtSource, DataSource.FRANKFURTER);
    }

    private static Instant parseDate(String date, Instant fallback) {
        if (date == null || date.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeException e) {
            return fallback;
        }
    }
}
