package com.myfinaimanager.core.marketdata.infrastructure.finnhub.client;

import com.myfinaimanager.core.marketdata.domain.exceptions.InstrumentProfileUnavailableException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataNotConfiguredException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataUnavailableException;
import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderAuthenticationFailedException;
import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderRateLimitedException;
import com.myfinaimanager.core.marketdata.infrastructure.config.FinnhubProperties;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.dto.FinnhubCompanyProfileResponse;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.dto.FinnhubQuoteResponse;
import java.util.function.Function;
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
 * The <strong>only</strong> class in the {@code marketdata} module that touches Spring's
 * {@link RestClient} (ArchUnit-enforced — plan.md D11). It attaches the Finnhub API key as the
 * {@code X-Finnhub-Token} request <strong>header</strong> (never a query parameter — OD-EN005-2),
 * calls the endpoint, and translates the outcome into the module's provider-neutral exceptions
 * (research D5). Every call emits one structured log record ({@code event=ProviderCall
 * provider=finnhub capability=…}) with no key, no URL, and no body.
 *
 * <p>Also the only class that builds a {@link RestClient} (with explicit connect/read timeouts —
 * FR-017), so all HTTP-client knowledge stays here. The package-private
 * {@code (RestClient, FinnhubProperties)} constructor lets a test bind {@code MockRestServiceServer}.
 */
@Component
public class FinnhubRestClient {

    private static final Logger log = LoggerFactory.getLogger(FinnhubRestClient.class);
    private static final String TOKEN_HEADER = "X-Finnhub-Token";

    private final RestClient restClient;
    private final FinnhubProperties properties;

    @Autowired
    public FinnhubRestClient(FinnhubProperties properties) {
        this(buildRestClient(properties), properties);
    }

    /** For tests: bind a {@code RestClient} built from a {@code MockRestServiceServer}-bound builder. */
    public FinnhubRestClient(RestClient restClient, FinnhubProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    private static RestClient buildRestClient(FinnhubProperties p) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(p.connectTimeout())
                .withReadTimeout(p.readTimeout());
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder()
                .baseUrl(p.baseUrl().toString())
                .requestFactory(factory)
                .build();
    }

    public FinnhubQuoteResponse quote(String symbol) {
        return call(FinnhubOperation.QUOTE, FinnhubQuoteResponse.class,
                MarketDataUnavailableException::new, "/quote", "symbol", symbol);
    }

    public FinnhubCompanyProfileResponse profile(String symbol) {
        return call(FinnhubOperation.PROFILE, FinnhubCompanyProfileResponse.class,
                InstrumentProfileUnavailableException::new, "/stock/profile2", "symbol", symbol);
    }

    private <T> T call(FinnhubOperation op,
                       Class<T> type,
                       Function<String, ? extends MarketDataException> unavailable,
                       String path,
                       String queryName,
                       String queryValue) {

        if (!properties.isConfigured()) {
            throw new MarketDataNotConfiguredException(
                    "market data provider is not configured (no API key)");
        }

        long startNanos = System.nanoTime();
        try {
            T body = restClient.get()
                    .uri(b -> b.path(path).queryParam(queryName, queryValue).build())
                    .header(TOKEN_HEADER, properties.apiKey())
                    .retrieve()
                    .body(type);
            if (body == null) {
                logCall(op, "UNAVAILABLE", "2xx-empty", startNanos, null);
                throw unavailable.apply("empty response from the provider");
            }
            logCall(op, "SUCCESS", "2xx", startNanos, null);
            return body;

        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 401 || code == 403) {
                logCall(op, "AUTH_FAILED", "4xx", startNanos, e);
                throw new ProviderAuthenticationFailedException(
                        "market data provider rejected the credentials");
            }
            if (code == 429) {
                logCall(op, "RATE_LIMITED", "429", startNanos, e);
                throw new ProviderRateLimitedException(
                        "market data provider rate limit reached");
            }
            logCall(op, "UNAVAILABLE", code >= 500 ? "5xx" : "4xx", startNanos, e);
            throw unavailable.apply("market data provider returned HTTP " + code);

        } catch (ResourceAccessException e) {
            logCall(op, "UNAVAILABLE", "NETWORK", startNanos, e);
            throw unavailable.apply("network error contacting the market data provider");

        } catch (RestClientException e) {
            logCall(op, "UNAVAILABLE", "BAD_RESPONSE", startNanos, e);
            throw unavailable.apply("could not read the market data provider response");
        }
    }

    private void logCall(FinnhubOperation op, String outcome, String statusCategory,
                         long startNanos, Throwable cause) {
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
        var entry = ("SUCCESS".equals(outcome) ? log.atInfo() : log.atWarn())
                .addKeyValue("event", "ProviderCall")
                .addKeyValue("provider", "finnhub")
                .addKeyValue("capability", capabilityOf(op))
                .addKeyValue("operation", op.name())
                .addKeyValue("outcome", outcome)
                .addKeyValue("httpStatusCategory", statusCategory)
                .addKeyValue("latencyMs", latencyMs);
        if (cause != null) {
            entry = entry.addKeyValue("errorType", cause.getClass().getSimpleName()); // type only — never the message
        }
        entry.log("Finnhub call {} {}", op, outcome);
    }

    private static String capabilityOf(FinnhubOperation op) {
        return op == FinnhubOperation.QUOTE ? "market-price" : "instrument-profile";
    }
}
