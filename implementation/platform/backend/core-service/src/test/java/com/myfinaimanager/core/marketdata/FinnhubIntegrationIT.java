package com.myfinaimanager.core.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataNotConfiguredException;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.domain.ports.FxRatePort;
import com.myfinaimanager.core.marketdata.domain.ports.InstrumentProfilePort;
import com.myfinaimanager.core.marketdata.domain.ports.MarketDataPort;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubMarketDataAdapter;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.cache.CachingFxRatePort;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.cache.CachingInstrumentProfilePort;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.cache.CachingMarketDataPort;
import com.myfinaimanager.core.marketdata.infrastructure.frankfurter.FrankfurterFxRateAdapter;
import com.myfinaimanager.core.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Boots the full application context with <strong>no</strong> {@code FINNHUB_API_KEY} and proves the
 * EN005 Revision 2 wiring:
 * <ul>
 *   <li>the three {@code marketdata} ports are the {@code @Primary} caching decorators;</li>
 *   <li>with no Finnhub key, the price and profile ports fail fast with
 *       {@link MarketDataNotConfiguredException} — and the application still started;</li>
 *   <li>the FX port is <strong>independent</strong> of the Finnhub key (Frankfurter is keyless — VC-004):
 *       it does <em>not</em> report "not configured"; with the base URL pointed at an unreachable
 *       host it surfaces a provider-neutral unavailable outcome, not an auth/config error;</li>
 *   <li>no outbound provider call reaches a real host.</li>
 * </ul>
 */
@SpringBootTest
@TestPropertySource(properties = "frankfurter.base-url=http://localhost:1")
class FinnhubIntegrationIT extends PostgresContainerSupport {

    @Autowired private MarketDataPort marketDataPort;
    @Autowired private InstrumentProfilePort instrumentProfilePort;
    @Autowired private FxRatePort fxRatePort;
    @Autowired private FinnhubMarketDataAdapter finnhubPriceAdapter;
    @Autowired private FrankfurterFxRateAdapter frankfurterFxAdapter;

    private static final InstrumentIdentifier AAPL =
            new InstrumentIdentifier("AAPL", "XNAS", SupportedCurrency.USD);

    @Test
    void the_primary_port_beans_are_the_caching_decorators() {
        assertThat(marketDataPort).isInstanceOf(CachingMarketDataPort.class);
        assertThat(instrumentProfilePort).isInstanceOf(CachingInstrumentProfilePort.class);
        assertThat(fxRatePort).isInstanceOf(CachingFxRatePort.class);
    }

    @Test
    void the_wired_provider_per_capability_matches_the_default_configuration() { // VC-004
        // market-data.price.provider=finnhub, market-data.fx.provider=frankfurter (application.yml)
        assertThat(finnhubPriceAdapter).isNotNull();       // the price provider is Finnhub
        assertThat(frankfurterFxAdapter).isNotNull();      // the FX provider is Frankfurter (keyless)
    }

    @Test
    void with_no_finnhub_key_price_and_profile_report_not_configured() {
        assertThatThrownBy(() -> marketDataPort.getLatestPrice(AAPL))
                .isInstanceOf(MarketDataNotConfiguredException.class);
        assertThatThrownBy(() -> instrumentProfilePort.getProfile(AAPL))
                .isInstanceOf(MarketDataNotConfiguredException.class);
    }

    @Test
    void the_fx_port_is_independent_of_the_finnhub_key() {
        assertThatThrownBy(() -> fxRatePort.getRate(SupportedCurrency.USD, SupportedCurrency.EUR))
                .isInstanceOf(MarketDataException.class)
                .isNotInstanceOf(MarketDataNotConfiguredException.class);
    }
}
