package com.myfinaimanager.core.marketdata.infrastructure.finnhub.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myfinaimanager.core.marketdata.domain.exceptions.InstrumentNotResolvedException;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class FinnhubSymbolResolverTest {

    private FinnhubSymbolResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new FinnhubSymbolResolver(new DefaultResourceLoader(),
                "classpath:reference-data/finnhub-symbol-map.csv");
    }

    @Test
    void us_listings_pass_the_ticker_through_unchanged() {
        assertThat(resolver.resolve(new InstrumentIdentifier("AAPL", "XNAS", SupportedCurrency.USD))).isEqualTo("AAPL");
        assertThat(resolver.resolve(new InstrumentIdentifier("spy", "arcx", SupportedCurrency.USD))).isEqualTo("SPY");
    }

    @Test
    void mapped_non_us_listings_get_the_configured_suffix() {
        assertThat(resolver.resolve(new InstrumentIdentifier("SAN", "XMAD", SupportedCurrency.EUR))).isEqualTo("SAN.MC");
        assertThat(resolver.resolve(new InstrumentIdentifier("ADS", "XETR", SupportedCurrency.EUR))).isEqualTo("ADS.DE");
        assertThat(resolver.resolve(new InstrumentIdentifier("ASML", "XAMS", SupportedCurrency.EUR))).isEqualTo("ASML.AS");
    }

    @Test
    void an_unmapped_mic_is_not_guessed() {
        assertThatThrownBy(() -> resolver.resolve(new InstrumentIdentifier("FOO", "XZZZ", SupportedCurrency.USD)))
                .isInstanceOf(InstrumentNotResolvedException.class);
    }

    @Test
    void a_missing_map_file_fails_fast() {
        assertThatThrownBy(() -> new FinnhubSymbolResolver(new DefaultResourceLoader(),
                "classpath:reference-data/does-not-exist.csv"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void a_malformed_map_row_fails_fast() {
        var loader = new org.springframework.core.io.ResourceLoader() {
            @Override public org.springframework.core.io.Resource getResource(String location) {
                return new org.springframework.core.io.ByteArrayResource(
                        "mic,strategy,suffix\nXNAS,NOT_A_STRATEGY,\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            @Override public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };
        assertThatThrownBy(() -> new FinnhubSymbolResolver(loader, "x"))
                .isInstanceOf(IllegalStateException.class);
    }
}
