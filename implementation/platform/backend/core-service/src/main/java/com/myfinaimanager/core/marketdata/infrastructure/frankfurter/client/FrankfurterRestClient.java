package com.myfinaimanager.core.marketdata.infrastructure.frankfurter.client;

import com.myfinaimanager.core.marketdata.domain.exceptions.FxRateUnavailableException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataUnavailableException;
import com.myfinaimanager.core.marketdata.infrastructure.config.FrankfurterProperties;
import com.myfinaimanager.core.marketdata.infrastructure.frankfurter.dto.FrankfurterRatesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The only {@code frankfurter} class that touches Spring's {@link RestClient} (ArchUnit-enforced).
 * Frankfurter is a <strong>keyless</strong> public API (ECB reference rates) — no auth header, no
 * secret. Calls {@code GET {base-url}/v1/latest?base={base}&symbols={symbols}} with explicit
 * connect/read timeouts (FR-017) and translates the outcome into the module's provider-neutral
 * exceptions. One structured log per call ({@code event=ProviderCall provider=frankfurter
 * capability=fx-rate}) — no body, no URL query echoed.
 */
@Component
public class FrankfurterRestClient {

    private static final Logger log = LoggerFactory.getLogger(FrankfurterRestClient.class);

    private final RestClient restClient;

    @Autowired
    public FrankfurterRestClient(FrankfurterProperties properties) {
        this(buildRestClient(properties));
    }

    /** For tests: bind a {@code RestClient} built from a {@code MockRestServiceServer}-bound builder. */
    public FrankfurterRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(FrankfurterProperties p) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(p.connectTimeout())
                .withReadTimeout(p.readTimeout());
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder()
                .baseUrl(p.baseUrl().toString())
                .requestFactory(factory)
                .build();
    }

    /**
     * @return the raw provider response for {@code base} → {@code symbols} (single symbol)
     * @throws FxRateUnavailableException      the response could not be read as a rates payload
     * @throws MarketDataUnavailableException  transport failure or a non-2xx status
     */
    public FrankfurterRatesResponse latest(String base, String symbols) {
        long startNanos = System.nanoTime();
        try {
            FrankfurterRatesResponse body = restClient.get()
                    .uri(b -> b.path("/v1/latest")
                            .queryParam("base", base)
                            .queryParam("symbols", symbols)
                            .build())
                    .retrieve()
                    .body(FrankfurterRatesResponse.class);
            if (body == null || body.rates() == null) {
                logCall("MALFORMED", "2xx", startNanos, null);
                throw new FxRateUnavailableException("provider returned an unreadable rates payload");
            }
            logCall("SUCCESS", "2xx", startNanos, null);
            return body;

        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            logCall("UNAVAILABLE", code >= 500 ? "5xx" : "4xx", startNanos, e);
            throw new MarketDataUnavailableException("FX rate provider returned HTTP " + code);

        } catch (ResourceAccessException e) {
            logCall("UNAVAILABLE", "NETWORK", startNanos, e);
            throw new MarketDataUnavailableException("network error contacting the FX rate provider");

        } catch (RestClientException e) {
            logCall("MALFORMED", "BAD_RESPONSE", startNanos, e);
            throw new FxRateUnavailableException("could not read the FX rate provider response");
        }
    }

    private void logCall(String outcome, String statusCategory, long startNanos, Throwable cause) {
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
        var entry = ("SUCCESS".equals(outcome) ? log.atInfo() : log.atWarn())
                .addKeyValue("event", "ProviderCall")
                .addKeyValue("provider", "frankfurter")
                .addKeyValue("capability", "fx-rate")
                .addKeyValue("operation", "LATEST")
                .addKeyValue("outcome", outcome)
                .addKeyValue("httpStatusCategory", statusCategory)
                .addKeyValue("latencyMs", latencyMs);
        if (cause != null) {
            entry = entry.addKeyValue("errorType", cause.getClass().getSimpleName());
        }
        entry.log("Frankfurter call LATEST {}", outcome);
    }
}
