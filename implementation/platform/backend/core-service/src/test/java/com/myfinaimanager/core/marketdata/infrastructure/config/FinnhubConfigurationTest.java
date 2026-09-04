package com.myfinaimanager.core.marketdata.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataNotConfiguredException;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubInstrumentProfileAdapter;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubMarketDataAdapter;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.cache.CachingInstrumentProfilePort;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.cache.CachingMarketDataPort;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.client.FinnhubRestClient;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.mapper.FinnhubProfileMapper;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.mapper.FinnhubQuoteMapper;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.resolver.FinnhubSymbolResolver;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.DefaultResourceLoader;

@ExtendWith(OutputCaptureExtension.class)
class FinnhubConfigurationTest {

    private static final Clock CLOCK = Clock.fixed(java.time.Instant.parse("2026-09-03T10:00:00Z"), ZoneOffset.UTC);

    private static FinnhubProperties props(String apiKey) {
        return new FinnhubProperties(apiKey, URI.create("https://finnhub.io/api/v1"),
                Duration.ofSeconds(2), Duration.ofSeconds(5),
                new FinnhubProperties.Cache(Duration.ofSeconds(45), Duration.ofHours(24)));
    }

    @Test
    void logs_that_the_integration_is_disabled_when_no_api_key_is_configured(CapturedOutput output) {
        new MarketDataModuleConfiguration(props("   "));
        assertThat(output).contains("FinnhubIntegrationDisabled");
    }

    @Test
    void does_not_log_disabled_when_a_key_is_present(CapturedOutput output) {
        new MarketDataModuleConfiguration(props("real-key"));
        assertThat(output).doesNotContain("FinnhubIntegrationDisabled");
    }

    @Test
    void every_caching_port_propagates_not_configured_when_the_key_is_blank() {
        FinnhubProperties blank = props("");
        FinnhubRestClient client = new FinnhubRestClient(blank);
        FinnhubSymbolResolver resolver = new FinnhubSymbolResolver(new DefaultResourceLoader(),
                "classpath:reference-data/finnhub-symbol-map.csv");

        var price = new CachingMarketDataPort(
                new FinnhubMarketDataAdapter(client, resolver, new FinnhubQuoteMapper(), CLOCK), blank, CLOCK);
        var profile = new CachingInstrumentProfilePort(
                new FinnhubInstrumentProfileAdapter(client, resolver, new FinnhubProfileMapper(), CLOCK), blank, CLOCK);

        var id = new com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier(
                "AAPL", "XNAS", SupportedCurrency.USD);
        assertThatThrownBy(() -> price.getLatestPrice(id)).isInstanceOf(MarketDataNotConfiguredException.class);
        assertThatThrownBy(() -> profile.getProfile(id)).isInstanceOf(MarketDataNotConfiguredException.class);
        // FX is now Frankfurter (keyless) — a blank Finnhub key does not affect it (VC-004).
    }
}
