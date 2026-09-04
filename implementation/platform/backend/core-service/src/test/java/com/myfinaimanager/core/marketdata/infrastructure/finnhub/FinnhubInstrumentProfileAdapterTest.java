package com.myfinaimanager.core.marketdata.infrastructure.finnhub;

import static com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubAdapterTestSupport.KEY;
import static com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubAdapterTestSupport.boundClient;
import static com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubAdapterTestSupport.fixture;
import static com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubAdapterTestSupport.realResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.myfinaimanager.core.marketdata.domain.exceptions.InstrumentProfileUnavailableException;
import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderAuthenticationFailedException;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentProfile;
import com.myfinaimanager.core.marketdata.domain.model.Sector;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.mapper.FinnhubProfileMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class FinnhubInstrumentProfileAdapterTest {

    private static final InstrumentIdentifier AAPL =
            new InstrumentIdentifier("AAPL", "XNAS", SupportedCurrency.USD);

    private FinnhubInstrumentProfileAdapter adapter(FinnhubAdapterTestSupport.Bound bound) {
        return new FinnhubInstrumentProfileAdapter(bound.client(), realResolver(),
                new FinnhubProfileMapper(), FinnhubAdapterTestSupport.FIXED_CLOCK);
    }

    @Test
    void returns_a_profile_with_a_classified_sector() {
        var bound = boundClient(KEY);
        bound.server().expect(requestTo(Matchers.startsWith("https://finnhub.io/api/v1/stock/profile2")))
                .andExpect(queryParam("symbol", "AAPL"))
                .andRespond(withSuccess(fixture("profile-aapl.json"),
                        MediaType.APPLICATION_JSON));

        InstrumentProfile p = adapter(bound).getProfile(AAPL);

        assertThat(p.name()).isEqualTo("Apple Inc");
        assertThat(p.sector().classification()).isEqualTo("Technology");
        assertThat(p.providerExchange()).isEqualTo("NASDAQ NMS - GLOBAL SELECT MARKET");
        bound.server().verify();
    }

    @Test
    void a_missing_industry_yields_an_unclassified_sector() {
        var bound = boundClient(KEY);
        bound.server().expect(requestTo(Matchers.startsWith("https://finnhub.io/api/v1/stock/profile2")))
                .andRespond(withSuccess(fixture("profile-no-industry.json"), MediaType.APPLICATION_JSON));
        assertThat(adapter(bound).getProfile(AAPL).sector()).isEqualTo(Sector.UNCLASSIFIED);
    }

    @Test
    void an_empty_payload_is_unavailable() {
        var bound = boundClient(KEY);
        bound.server().expect(requestTo(Matchers.startsWith("https://finnhub.io/api/v1/stock/profile2")))
                .andRespond(withSuccess(fixture("profile-empty.json"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> adapter(bound).getProfile(AAPL))
                .isInstanceOf(InstrumentProfileUnavailableException.class);
    }

    @Test
    void a_401_is_authentication_failed() {
        var bound = boundClient(KEY);
        bound.server().expect(requestTo(Matchers.startsWith("https://finnhub.io/api/v1/stock/profile2")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> adapter(bound).getProfile(AAPL))
                .isInstanceOf(ProviderAuthenticationFailedException.class);
    }
}
