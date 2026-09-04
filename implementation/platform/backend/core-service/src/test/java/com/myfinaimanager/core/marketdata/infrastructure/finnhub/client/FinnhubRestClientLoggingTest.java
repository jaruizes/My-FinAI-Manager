package com.myfinaimanager.core.marketdata.infrastructure.finnhub.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.myfinaimanager.core.marketdata.infrastructure.config.FinnhubProperties;
import java.net.URI;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The structured {@code event=ProviderCall} log record (FR-033) carries {@code provider} +
 * {@code capability} + the required fields and <strong>never</strong> the API key, a key-bearing
 * URL, or the provider error message (VC-011).
 */
@ExtendWith(OutputCaptureExtension.class)
class FinnhubRestClientLoggingTest {

    private static final String KEY = "super-secret-finnhub-key-9f8e7d";

    private record Fx(FinnhubRestClient client, MockRestServiceServer server) {
    }

    private static Fx fx() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://finnhub.io/api/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FinnhubProperties props = new FinnhubProperties(KEY, URI.create("https://finnhub.io/api/v1"),
                Duration.ofSeconds(2), Duration.ofSeconds(5),
                new FinnhubProperties.Cache(Duration.ofSeconds(45), Duration.ofHours(24)));
        return new Fx(new FinnhubRestClient(builder.build(), props), server);
    }

    @Test
    void a_successful_call_logs_the_required_fields_and_no_secret(CapturedOutput output) {
        Fx fx = fx();
        fx.server().expect(requestTo(Matchers.startsWith("https://finnhub.io/api/v1/quote")))
                .andRespond(withSuccess("{\"c\":1,\"t\":1}", MediaType.APPLICATION_JSON));

        fx.client().quote("AAPL");

        // format-agnostic (plain key=value or ECS JSON, depending on what ran first in the fork)
        assertThat(output).contains("ProviderCall").contains("finnhub").contains("market-price")
                .contains("QUOTE").contains("SUCCESS").contains("latencyMs");
        assertThat(output).doesNotContain(KEY);
        assertThat(output).doesNotContain("token=");
    }

    @Test
    void a_failed_call_logs_a_warning_without_the_key_or_the_provider_message(CapturedOutput output) {
        Fx fx = fx();
        fx.server().expect(requestTo(Matchers.startsWith("https://finnhub.io/api/v1/quote")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":\"" + KEY + " is not a valid key\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        Throwable t = catchThrowable(() -> fx.client().quote("AAPL"));

        assertThat(t).isNotNull();
        assertThat(output).contains("AUTH_FAILED");
        assertThat(output).doesNotContain(KEY); // the key echoed in the provider error body must not reach the log
        assertThat(t.getMessage()).doesNotContain(KEY);
    }
}
