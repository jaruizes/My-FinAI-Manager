package com.myfinaimanager.core.marketdata.infrastructure.frankfurter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.myfinaimanager.core.marketdata.domain.exceptions.FxRateUnavailableException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataUnavailableException;
import com.myfinaimanager.core.marketdata.domain.model.DataSource;
import com.myfinaimanager.core.marketdata.domain.model.FxRate;
import com.myfinaimanager.core.marketdata.domain.model.ObservedAtSource;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.infrastructure.frankfurter.client.FrankfurterRestClient;
import com.myfinaimanager.core.marketdata.infrastructure.frankfurter.mapper.FrankfurterFxRateMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * {@code FrankfurterFxRateAdapter} against a stubbed HTTP boundary — the request shape (no auth),
 * the happy USD↔EUR mappings, and every failure → a provider-neutral outcome
 * ([contracts/frankfurter-provider-contract.md]).
 */
class FrankfurterFxRateAdapterTest {

    private static final String BASE = "https://api.frankfurter.dev";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC);

    private record Fx(FrankfurterFxRateAdapter adapter, MockRestServiceServer server) {
    }

    private static Fx fx() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FrankfurterRestClient client = new FrankfurterRestClient(builder.build());
        return new Fx(new FrankfurterFxRateAdapter(client, new FrankfurterFxRateMapper(), CLOCK), server);
    }

    @Test
    void usd_to_eur_calls_the_keyless_endpoint_and_maps_the_rate_and_date() {
        Fx fx = fx();
        fx.server().expect(requestTo(Matchers.startsWith(BASE + "/v1/latest")))
                .andExpect(queryParam("base", "USD"))
                .andExpect(queryParam("symbols", "EUR"))
                .andExpect(req -> assertThat(req.getHeaders().get("X-Finnhub-Token")).isNull())
                .andExpect(req -> assertThat(req.getHeaders().get("Authorization")).isNull())
                .andRespond(withSuccess(
                        "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-09-03\",\"rates\":{\"EUR\":0.85477}}",
                        MediaType.APPLICATION_JSON));

        FxRate rate = fx.adapter().getRate(SupportedCurrency.USD, SupportedCurrency.EUR);

        assertThat(rate.from()).isEqualTo(SupportedCurrency.USD);
        assertThat(rate.to()).isEqualTo(SupportedCurrency.EUR);
        assertThat(rate.rate()).isEqualByComparingTo("0.85477");
        assertThat(rate.source()).isEqualTo(DataSource.FRANKFURTER);
        assertThat(rate.observedAt()).isEqualTo(Instant.parse("2026-09-03T00:00:00Z"));
        assertThat(rate.observedAtSource()).isEqualTo(ObservedAtSource.PROVIDER_TIMESTAMP);
        fx.server().verify();
    }

    @Test
    void eur_to_usd_maps_the_reverse_direction() {
        Fx fx = fx();
        fx.server().expect(requestTo(Matchers.startsWith(BASE + "/v1/latest")))
                .andExpect(queryParam("base", "EUR"))
                .andExpect(queryParam("symbols", "USD"))
                .andRespond(withSuccess(
                        "{\"amount\":1.0,\"base\":\"EUR\",\"date\":\"2026-09-03\",\"rates\":{\"USD\":1.1699}}",
                        MediaType.APPLICATION_JSON));

        assertThat(fx.adapter().getRate(SupportedCurrency.EUR, SupportedCurrency.USD).rate())
                .isEqualByComparingTo("1.1699");
    }

    @Test
    void a_missing_target_rate_is_fx_rate_unavailable() {
        Fx fx = fx();
        fx.server().expect(requestTo(Matchers.startsWith(BASE + "/v1/latest")))
                .andRespond(withSuccess(
                        "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-09-03\",\"rates\":{\"GBP\":0.79}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fx.adapter().getRate(SupportedCurrency.USD, SupportedCurrency.EUR))
                .isInstanceOf(FxRateUnavailableException.class);
    }

    @Test
    void a_malformed_body_is_fx_rate_unavailable() {
        Fx fx = fx();
        fx.server().expect(requestTo(Matchers.startsWith(BASE + "/v1/latest")))
                .andRespond(withSuccess("<<<not json>>>", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fx.adapter().getRate(SupportedCurrency.USD, SupportedCurrency.EUR))
                .isInstanceOf(FxRateUnavailableException.class);
    }

    @Test
    void a_5xx_is_provider_unavailable() {
        Fx fx = fx();
        fx.server().expect(requestTo(Matchers.startsWith(BASE + "/v1/latest")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> fx.adapter().getRate(SupportedCurrency.USD, SupportedCurrency.EUR))
                .isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void a_network_error_is_provider_unavailable() {
        Fx fx = fx();
        fx.server().expect(requestTo(Matchers.startsWith(BASE + "/v1/latest")))
                .andRespond(request -> {
                    throw new IOException("connection reset");
                });

        assertThatThrownBy(() -> fx.adapter().getRate(SupportedCurrency.USD, SupportedCurrency.EUR))
                .isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void same_currency_is_rejected_without_a_call() {
        Fx fx = fx();
        assertThatThrownBy(() -> fx.adapter().getRate(SupportedCurrency.USD, SupportedCurrency.USD))
                .isInstanceOf(IllegalArgumentException.class);
        fx.server().verify(); // no request expected
    }
}
