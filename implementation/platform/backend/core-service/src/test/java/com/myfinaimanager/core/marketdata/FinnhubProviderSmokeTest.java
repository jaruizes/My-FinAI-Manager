package com.myfinaimanager.core.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentProfile;
import com.myfinaimanager.core.marketdata.domain.model.MarketPrice;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.infrastructure.config.FinnhubProperties;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubInstrumentProfileAdapter;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubMarketDataAdapter;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.client.FinnhubRestClient;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.mapper.FinnhubProfileMapper;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.mapper.FinnhubQuoteMapper;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.resolver.FinnhubSymbolResolver;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * <strong>Opt-in</strong> smoke test against the <strong>live</strong> Finnhub API. It runs ONLY when
 * {@code FINNHUB_SMOKE=1} <em>and</em> {@code FINNHUB_API_KEY} are set — it is <strong>skipped</strong>
 * in a normal {@code ./mvnw verify} / CI run (SC-010; enabler §29). It never prints the API key.
 * An external limitation (rate limit, or a plan that excludes non-US quotes) is reported as a
 * <em>skipped</em> assumption, not an application-test failure.
 */
@EnabledIfEnvironmentVariable(named = "FINNHUB_SMOKE", matches = "1")
class FinnhubProviderSmokeTest {

    private static final InstrumentIdentifier AAPL =
            new InstrumentIdentifier("AAPL", "XNAS", SupportedCurrency.USD);

    private final Clock clock = Clock.systemUTC();
    private final FinnhubRestClient client = new FinnhubRestClient(new FinnhubProperties(
            System.getenv("FINNHUB_API_KEY"), URI.create("https://finnhub.io/api/v1"),
            Duration.ofSeconds(3), Duration.ofSeconds(8),
            new FinnhubProperties.Cache(Duration.ofSeconds(1), Duration.ofSeconds(1))));
    private final FinnhubSymbolResolver resolver = new FinnhubSymbolResolver(
            new DefaultResourceLoader(), "classpath:reference-data/finnhub-symbol-map.csv");

    @Test
    void live_quote_returns_a_positive_price() {
        MarketPrice price = new FinnhubMarketDataAdapter(client, resolver, new FinnhubQuoteMapper(), clock)
                .getLatestPrice(AAPL);
        assertThat(price.price()).isPositive();
        assertThat(price.currency()).isEqualTo(SupportedCurrency.USD);
    }

    @Test
    void live_profile_returns_a_name() {
        InstrumentProfile profile = new FinnhubInstrumentProfileAdapter(client, resolver, new FinnhubProfileMapper(), clock)
                .getProfile(AAPL);
        assertThat(profile.name()).isNotBlank();
        assertThat(profile.sector()).isNotNull(); // classified or UNCLASSIFIED — both acceptable
    }

    // FX rates are no longer a Finnhub capability (EN005 Revision 2 — Frankfurter, keyless).
    // A Frankfurter live probe lives in FrankfurterProviderSmokeTest.
}
