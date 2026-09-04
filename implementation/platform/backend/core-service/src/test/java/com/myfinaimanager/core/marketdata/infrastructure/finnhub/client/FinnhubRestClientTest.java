package com.myfinaimanager.core.marketdata.infrastructure.finnhub.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.myfinaimanager.core.marketdata.domain.exceptions.InstrumentProfileUnavailableException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataNotConfiguredException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataUnavailableException;
import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderAuthenticationFailedException;
import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderRateLimitedException;
import com.myfinaimanager.core.marketdata.infrastructure.config.FinnhubProperties;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.dto.FinnhubQuoteResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FinnhubRestClientTest {

    private static final String KEY = "test-key-abc123";

    private record Fixture(FinnhubRestClient client, MockRestServiceServer server) {
    }

    private static FinnhubProperties props(String apiKey) {
        return new FinnhubProperties(apiKey, URI.create("https://finnhub.io/api/v1"),
                Duration.ofSeconds(2), Duration.ofSeconds(5),
                new FinnhubProperties.Cache(Duration.ofSeconds(45), Duration.ofHours(24)));
    }

    private static Fixture fixture(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://finnhub.io/api/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new FinnhubRestClient(builder.build(), props(apiKey)), server);
    }

    @Test
    void quote_calls_the_right_endpoint_with_the_token_header_and_maps_the_body() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(org.hamcrest.Matchers.startsWith("https://finnhub.io/api/v1/quote")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("symbol", "AAPL"))
                .andExpect(header("X-Finnhub-Token", KEY))
                .andRespond(withSuccess("{\"c\":187.32,\"t\":1725000000}", MediaType.APPLICATION_JSON));

        FinnhubQuoteResponse r = f.client().quote("AAPL");

        assertThat(r.c()).isEqualByComparingTo("187.32");
        assertThat(r.t()).isEqualTo(1725000000L);
        f.server().verify();
    }

    @Test
    void the_token_is_never_a_query_parameter() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("token"))))
                .andRespond(withSuccess("{\"c\":1,\"t\":1}", MediaType.APPLICATION_JSON));
        f.client().quote("AAPL");
        f.server().verify();
    }

    @Test
    void http_401_maps_to_authentication_failed_without_leaking_the_key() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(org.hamcrest.Matchers.startsWith("https://finnhub.io/api/v1/quote")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"Invalid API key\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        Throwable t = catchThrowable(() -> f.client().quote("AAPL"));

        assertThat(t).isInstanceOf(ProviderAuthenticationFailedException.class);
        assertThat(t.getMessage()).doesNotContain(KEY);
    }

    @Test
    void http_429_maps_to_rate_limited() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(org.hamcrest.Matchers.startsWith("https://finnhub.io/api/v1/quote")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> f.client().quote("AAPL")).isInstanceOf(ProviderRateLimitedException.class);
    }

    @Test
    void http_500_maps_to_the_operation_unavailable() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(org.hamcrest.Matchers.startsWith("https://finnhub.io/api/v1/quote")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> f.client().quote("AAPL")).isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void http_404_maps_to_the_operation_unavailable() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(org.hamcrest.Matchers.startsWith("https://finnhub.io/api/v1/quote")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> f.client().quote("NOPE")).isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void an_empty_200_body_maps_to_the_operation_unavailable() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(org.hamcrest.Matchers.startsWith("https://finnhub.io/api/v1/quote")))
                .andRespond(withStatus(HttpStatus.OK)); // no body
        assertThatThrownBy(() -> f.client().quote("AAPL")).isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void a_malformed_body_maps_to_the_operation_unavailable() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(org.hamcrest.Matchers.startsWith("https://finnhub.io/api/v1/stock/profile2")))
                .andRespond(withSuccess("<<<not json>>>", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> f.client().profile("AAPL"))
                .isInstanceOf(InstrumentProfileUnavailableException.class);
    }

    @Test
    void a_network_error_maps_to_the_operation_unavailable() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(org.hamcrest.Matchers.startsWith("https://finnhub.io/api/v1/stock/profile2")))
                .andRespond(request -> {
                    throw new IOException("connection reset");
                });
        assertThatThrownBy(() -> f.client().profile("AAPL"))
                .isInstanceOf(InstrumentProfileUnavailableException.class);
    }

    @Test
    void a_blank_api_key_short_circuits_with_not_configured_and_makes_no_call() {
        Fixture f = fixture("   ");
        // no server.expect(...) — any request would fail verification
        assertThatThrownBy(() -> f.client().quote("AAPL")).isInstanceOf(MarketDataNotConfiguredException.class);
        assertThatThrownBy(() -> f.client().profile("AAPL")).isInstanceOf(MarketDataNotConfiguredException.class);
        f.server().verify();
    }

    @Test
    void profile_hits_its_endpoint() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(org.hamcrest.Matchers.startsWith("https://finnhub.io/api/v1/stock/profile2")))
                .andExpect(queryParam("symbol", "AAPL"))
                .andExpect(header("X-Finnhub-Token", KEY))
                .andRespond(withSuccess("{\"ticker\":\"AAPL\",\"name\":\"Apple Inc\"}", MediaType.APPLICATION_JSON));
        assertThat(f.client().profile("AAPL").name()).isEqualTo("Apple Inc");
        f.server().verify();
    }
}
