package com.myfinaimanager.core.marketdata.infrastructure.finnhub.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderRateLimitedException;
import com.myfinaimanager.core.marketdata.domain.model.DataSource;
import com.myfinaimanager.core.marketdata.domain.model.FxRate;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentProfile;
import com.myfinaimanager.core.marketdata.domain.model.MarketPrice;
import com.myfinaimanager.core.marketdata.domain.model.ObservedAtSource;
import com.myfinaimanager.core.marketdata.domain.model.Sector;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.infrastructure.config.FinnhubProperties;
import com.myfinaimanager.core.marketdata.infrastructure.config.FrankfurterProperties;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubInstrumentProfileAdapter;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubMarketDataAdapter;
import com.myfinaimanager.core.marketdata.infrastructure.frankfurter.FrankfurterFxRateAdapter;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The three caching decorators collapse repeated identical calls within the TTL to <strong>one</strong>
 * delegate call (SC-009), preserve the delegate result's {@code observedAt} (FR-023), and never cache
 * a failure.
 */
@ExtendWith(MockitoExtension.class)
class CachingPortsTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-03T10:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private static final InstrumentIdentifier AAPL =
            new InstrumentIdentifier("AAPL", "XNAS", SupportedCurrency.USD);
    private static final Instant DELEGATE_OBSERVED_AT = Instant.parse("2026-09-03T09:59:00Z");

    private static FinnhubProperties props() {
        return new FinnhubProperties("k", URI.create("https://finnhub.io/api/v1"),
                Duration.ofSeconds(2), Duration.ofSeconds(5),
                new FinnhubProperties.Cache(Duration.ofSeconds(45), Duration.ofHours(24)));
    }

    private static FrankfurterProperties fxProps() {
        return new FrankfurterProperties(URI.create("https://api.frankfurter.dev"),
                Duration.ofSeconds(2), Duration.ofSeconds(5),
                new FrankfurterProperties.Cache(Duration.ofMinutes(10)));
    }

    @Mock private FinnhubMarketDataAdapter marketDelegate;
    @Mock private FinnhubInstrumentProfileAdapter profileDelegate;
    @Mock private FrankfurterFxRateAdapter fxDelegate;

    @Test
    void market_data_cache_serves_one_delegate_call_within_the_ttl_and_keeps_observed_at() {
        MutableClock clock = new MutableClock();
        when(marketDelegate.getLatestPrice(AAPL)).thenReturn(new MarketPrice(AAPL, new BigDecimal("10"),
                SupportedCurrency.USD, DELEGATE_OBSERVED_AT, ObservedAtSource.PROVIDER_TIMESTAMP, DataSource.FINNHUB));
        var caching = new CachingMarketDataPort(marketDelegate, props(), clock);

        for (int i = 0; i < 4; i++) {
            assertThat(caching.getLatestPrice(AAPL).observedAt()).isEqualTo(DELEGATE_OBSERVED_AT);
        }
        verify(marketDelegate, times(1)).getLatestPrice(AAPL);

        clock.advance(Duration.ofSeconds(46));
        caching.getLatestPrice(AAPL);
        verify(marketDelegate, times(2)).getLatestPrice(AAPL);
    }

    @Test
    void profile_cache_serves_one_delegate_call_within_the_ttl() {
        when(profileDelegate.getProfile(AAPL)).thenReturn(new InstrumentProfile("AAPL", "Apple Inc",
                Sector.of("Technology"), null, SupportedCurrency.USD, "NASDAQ", DataSource.FINNHUB, DELEGATE_OBSERVED_AT));
        var caching = new CachingInstrumentProfilePort(profileDelegate, props(), new MutableClock());

        caching.getProfile(AAPL);
        caching.getProfile(AAPL);
        verify(profileDelegate, times(1)).getProfile(AAPL);
    }

    @Test
    void fx_cache_is_directional_and_never_caches_a_failure() {
        when(fxDelegate.getRate(SupportedCurrency.USD, SupportedCurrency.EUR))
                .thenThrow(new ProviderRateLimitedException("throttled"))
                .thenReturn(new FxRate(SupportedCurrency.USD, SupportedCurrency.EUR, new BigDecimal("0.9"),
                        DELEGATE_OBSERVED_AT, ObservedAtSource.PROVIDER_TIMESTAMP, DataSource.FRANKFURTER));
        when(fxDelegate.getRate(SupportedCurrency.EUR, SupportedCurrency.USD))
                .thenReturn(new FxRate(SupportedCurrency.EUR, SupportedCurrency.USD, new BigDecimal("1.08"),
                        DELEGATE_OBSERVED_AT, ObservedAtSource.PROVIDER_TIMESTAMP, DataSource.FRANKFURTER));
        var caching = new CachingFxRatePort(fxDelegate, fxProps(), new MutableClock());

        assertThatThrownBy(() -> caching.getRate(SupportedCurrency.USD, SupportedCurrency.EUR))
                .isInstanceOf(ProviderRateLimitedException.class);
        // the failure was not cached — a retry reaches the delegate again and now succeeds
        assertThat(caching.getRate(SupportedCurrency.USD, SupportedCurrency.EUR).rate()).isEqualByComparingTo("0.9");
        // reverse direction is a distinct cache key
        caching.getRate(SupportedCurrency.EUR, SupportedCurrency.USD);

        verify(fxDelegate, times(2)).getRate(SupportedCurrency.USD, SupportedCurrency.EUR);
        verify(fxDelegate, times(1)).getRate(SupportedCurrency.EUR, SupportedCurrency.USD);
    }

    @Test
    void same_currency_is_rejected_before_the_cache() {
        var caching = new CachingFxRatePort(fxDelegate, fxProps(), new MutableClock());
        assertThatThrownBy(() -> caching.getRate(SupportedCurrency.EUR, SupportedCurrency.EUR))
                .isInstanceOf(IllegalArgumentException.class);
        verify(fxDelegate, times(0)).getRate(any(), any());
    }
}
