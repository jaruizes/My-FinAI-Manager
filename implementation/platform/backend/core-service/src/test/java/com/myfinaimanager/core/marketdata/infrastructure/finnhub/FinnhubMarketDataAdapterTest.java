package com.myfinaimanager.core.marketdata.infrastructure.finnhub;

import static com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubAdapterTestSupport.KEY;
import static com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubAdapterTestSupport.boundClient;
import static com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubAdapterTestSupport.fixture;
import static com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubAdapterTestSupport.realResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.myfinaimanager.core.marketdata.domain.exceptions.InstrumentNotResolvedException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataUnavailableException;
import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderRateLimitedException;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.MarketPrice;
import com.myfinaimanager.core.marketdata.domain.model.ObservedAtSource;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.mapper.FinnhubQuoteMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

class FinnhubMarketDataAdapterTest {

    private static final InstrumentIdentifier AAPL =
            new InstrumentIdentifier("AAPL", "XNAS", SupportedCurrency.USD);
    private static final InstrumentIdentifier UNRESOLVABLE =
            new InstrumentIdentifier("FOO", "XZZZ", SupportedCurrency.USD);

    private FinnhubMarketDataAdapter adapter(FinnhubAdapterTestSupport.Bound bound) {
        return new FinnhubMarketDataAdapter(bound.client(), realResolver(), new FinnhubQuoteMapper(),
                FinnhubAdapterTestSupport.FIXED_CLOCK);
    }

    @Test
    void returns_a_decimal_safe_dated_market_price() {
        var bound = boundClient(KEY);
        bound.server().expect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .requestTo(Matchers.startsWith("https://finnhub.io/api/v1/quote")))
                .andExpect(queryParam("symbol", "AAPL"))
                .andRespond(withSuccess(fixture("quote-aapl.json"), MediaType.APPLICATION_JSON));

        MarketPrice price = adapter(bound).getLatestPrice(AAPL);

        assertThat(price.price()).isEqualByComparingTo("187.32");
        assertThat(price.currency()).isEqualTo(SupportedCurrency.USD);
        assertThat(price.observedAtSource()).isEqualTo(ObservedAtSource.PROVIDER_TIMESTAMP);
        bound.server().verify();
    }

    @Test
    void a_zero_price_is_unavailable() {
        var bound = boundClient(KEY);
        bound.server().expect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .requestTo(Matchers.startsWith("https://finnhub.io/api/v1/quote")))
                .andRespond(withSuccess(fixture("quote-zero.json"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> adapter(bound).getLatestPrice(AAPL))
                .isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void a_429_is_rate_limited() {
        var bound = boundClient(KEY);
        bound.server().expect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .requestTo(Matchers.startsWith("https://finnhub.io/api/v1/quote")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> adapter(bound).getLatestPrice(AAPL))
                .isInstanceOf(ProviderRateLimitedException.class);
    }

    @Test
    void an_unresolvable_instrument_makes_no_http_call() {
        var bound = boundClient(KEY);
        MockRestServiceServer server = bound.server(); // no expectations registered
        assertThatThrownBy(() -> adapter(bound).getLatestPrice(UNRESOLVABLE))
                .isInstanceOf(InstrumentNotResolvedException.class);
        server.verify();
    }
}
