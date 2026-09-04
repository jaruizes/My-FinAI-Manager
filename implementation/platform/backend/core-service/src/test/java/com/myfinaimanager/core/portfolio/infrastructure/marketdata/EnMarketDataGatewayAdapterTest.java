package com.myfinaimanager.core.portfolio.infrastructure.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.myfinaimanager.core.marketdata.domain.exceptions.FxRateUnavailableException;
import com.myfinaimanager.core.marketdata.domain.exceptions.InstrumentNotResolvedException;
import com.myfinaimanager.core.marketdata.domain.exceptions.InstrumentProfileUnavailableException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataNotConfiguredException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataUnavailableException;
import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderAuthenticationFailedException;
import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderRateLimitedException;
import com.myfinaimanager.core.marketdata.domain.model.DataSource;
import com.myfinaimanager.core.marketdata.domain.model.FxRate;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentProfile;
import com.myfinaimanager.core.marketdata.domain.model.MarketPrice;
import com.myfinaimanager.core.marketdata.domain.model.ObservedAtSource;
import com.myfinaimanager.core.marketdata.domain.model.Sector;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.domain.ports.FxRatePort;
import com.myfinaimanager.core.marketdata.domain.ports.InstrumentProfilePort;
import com.myfinaimanager.core.marketdata.domain.ports.MarketDataPort;
import com.myfinaimanager.core.portfolio.domain.model.FxConversion;
import com.myfinaimanager.core.portfolio.domain.model.PositionPricing;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

/**
 * The ACL boundary (AR-062): every {@code marketdata} failure (and any malformed argument) becomes
 * {@link java.util.Optional#empty()}; a happy path maps to the {@code portfolio} module's own read
 * models. No {@code marketdata} type escapes.
 */
class EnMarketDataGatewayAdapterTest {

    private static final Instant TS = Instant.parse("2026-09-04T10:00:00Z");

    private final MarketDataPort marketDataPort = Mockito.mock(MarketDataPort.class);
    private final InstrumentProfilePort instrumentProfilePort = Mockito.mock(InstrumentProfilePort.class);
    private final FxRatePort fxRatePort = Mockito.mock(FxRatePort.class);
    private final EnMarketDataGatewayAdapter adapter =
            new EnMarketDataGatewayAdapter(marketDataPort, instrumentProfilePort, fxRatePort);

    @Test
    void latest_price_maps_a_market_price() {
        when(marketDataPort.getLatestPrice(any())).thenReturn(new MarketPrice(
                new InstrumentIdentifier("AAPL", "XNAS", SupportedCurrency.USD),
                new BigDecimal("200.10"), SupportedCurrency.USD, TS,
                ObservedAtSource.PROVIDER_TIMESTAMP, DataSource.FINNHUB));

        assertThat(adapter.latestPrice("AAPL", "XNAS", "USD"))
                .hasValue(new PositionPricing(new BigDecimal("200.10"), TS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unavailable", "rate-limited", "auth", "not-resolved", "not-configured"})
    void latest_price_is_empty_on_any_market_data_exception(String kind) {
        when(marketDataPort.getLatestPrice(any())).thenThrow(priceException(kind));
        assertThat(adapter.latestPrice("AAPL", "XNAS", "USD")).isEmpty();
    }

    @Test
    void latest_price_is_empty_for_an_unsupported_currency() {
        assertThat(adapter.latestPrice("AAPL", "XNAS", "GBP")).isEmpty();
        Mockito.verifyNoInteractions(marketDataPort);
    }

    @Test
    void latest_price_and_sector_are_empty_for_a_blank_ticker() {
        assertThat(adapter.latestPrice("  ", "XNAS", "USD")).isEmpty();
        assertThat(adapter.sector("  ", "XNAS", "USD")).isEmpty();
        Mockito.verifyNoInteractions(marketDataPort, instrumentProfilePort);
    }

    @Test
    void sector_maps_a_classified_profile() {
        when(instrumentProfilePort.getProfile(any())).thenReturn(profile(Sector.of("Technology")));
        assertThat(adapter.sector("AAPL", "XNAS", "USD")).hasValue("Technology");
    }

    @Test
    void sector_is_empty_for_an_unclassified_profile() {
        when(instrumentProfilePort.getProfile(any())).thenReturn(profile(Sector.UNCLASSIFIED));
        assertThat(adapter.sector("AAPL", "XNAS", "USD")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"profile-unavailable", "rate-limited", "auth", "not-resolved", "not-configured"})
    void sector_is_empty_on_any_market_data_exception(String kind) {
        when(instrumentProfilePort.getProfile(any())).thenThrow(profileException(kind));
        assertThat(adapter.sector("AAPL", "XNAS", "USD")).isEmpty();
    }

    @Test
    void fx_rate_maps_a_rate() {
        when(fxRatePort.getRate(SupportedCurrency.USD, SupportedCurrency.EUR)).thenReturn(new FxRate(
                SupportedCurrency.USD, SupportedCurrency.EUR, new BigDecimal("0.80"), TS,
                ObservedAtSource.RETRIEVAL_TIME, DataSource.FINNHUB));

        assertThat(adapter.fxRate("USD", "EUR"))
                .hasValue(new FxConversion(new BigDecimal("0.80"), TS));
    }

    @Test
    void fx_rate_is_empty_on_an_fx_exception() {
        when(fxRatePort.getRate(any(), any())).thenThrow(new FxRateUnavailableException("no rate"));
        assertThat(adapter.fxRate("USD", "EUR")).isEmpty();
    }

    @Test
    void fx_rate_is_empty_for_same_or_unsupported_currencies() {
        assertThat(adapter.fxRate("USD", "USD")).isEmpty();
        assertThat(adapter.fxRate("USD", "GBP")).isEmpty();
        Mockito.verifyNoInteractions(fxRatePort);
    }

    private static InstrumentProfile profile(Sector sector) {
        return new InstrumentProfile("AAPL", "Apple Inc.", sector, null, SupportedCurrency.USD,
                null, DataSource.FINNHUB, TS);
    }

    private static RuntimeException priceException(String kind) {
        return switch (kind) {
            case "rate-limited" -> new ProviderRateLimitedException("429");
            case "auth" -> new ProviderAuthenticationFailedException("401");
            case "not-resolved" -> new InstrumentNotResolvedException("no symbol");
            case "not-configured" -> new MarketDataNotConfiguredException("no key");
            default -> new MarketDataUnavailableException("down");
        };
    }

    private static RuntimeException profileException(String kind) {
        return switch (kind) {
            case "rate-limited" -> new ProviderRateLimitedException("429");
            case "auth" -> new ProviderAuthenticationFailedException("401");
            case "not-resolved" -> new InstrumentNotResolvedException("no symbol");
            case "not-configured" -> new MarketDataNotConfiguredException("no key");
            default -> new InstrumentProfileUnavailableException("down");
        };
    }
}
