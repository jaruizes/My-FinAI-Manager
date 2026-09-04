package com.myfinaimanager.core.marketdata.infrastructure.frankfurter.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
 * The Frankfurter structured log carries {@code provider=frankfurter} and {@code capability=fx-rate}
 * (FR-033). Frankfurter is keyless — there is no secret to leak — but the log still must not echo
 * the request URL query or a provider error body.
 */
@ExtendWith(OutputCaptureExtension.class)
class FrankfurterRestClientLoggingTest {

    private record Fx(FrankfurterRestClient client, MockRestServiceServer server) {
    }

    private static Fx fx() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.frankfurter.dev");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fx(new FrankfurterRestClient(builder.build()), server);
    }

    @Test
    void a_successful_call_logs_provider_and_capability(CapturedOutput output) {
        Fx fx = fx();
        fx.server().expect(requestTo(Matchers.startsWith("https://api.frankfurter.dev/v1/latest")))
                .andRespond(withSuccess(
                        "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-09-03\",\"rates\":{\"EUR\":0.85}}",
                        MediaType.APPLICATION_JSON));

        fx.client().latest("USD", "EUR");

        assertThat(output).contains("ProviderCall").contains("frankfurter").contains("fx-rate")
                .contains("LATEST").contains("SUCCESS").contains("latencyMs");
    }

    @Test
    void a_failed_call_logs_a_warning(CapturedOutput output) {
        Fx fx = fx();
        fx.server().expect(requestTo(Matchers.startsWith("https://api.frankfurter.dev/v1/latest")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        Throwable t = catchThrowable(() -> fx.client().latest("USD", "EUR"));

        assertThat(t).isNotNull();
        assertThat(output).contains("frankfurter").contains("UNAVAILABLE");
    }
}
