package com.myfinaimanager.core.marketdata.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the Finnhub integration (EN005; DR-032). Bound from the
 * {@code finnhub.*} block in {@code application.yml}.
 *
 * <p>{@code apiKey} comes from {@code ${FINNHUB_API_KEY:}} — an <strong>empty string</strong> when
 * the environment variable is unset. A blank key disables the integration (see
 * {@code MarketDataModuleConfiguration} / {@code FinnhubRestClient}); it is never logged, traced,
 * returned, or placed in a URL (the adapter sends it as the {@code X-Finnhub-Token} header).
 *
 * @param apiKey         Finnhub API key; blank ⇒ integration disabled
 * @param baseUrl        Finnhub API base URL (e.g. {@code https://finnhub.io/api/v1})
 * @param connectTimeout outbound connect timeout (explicit — FR-017)
 * @param readTimeout    outbound read timeout (explicit — FR-017)
 * @param cache          short-lived in-process cache TTL for quotes (FR-023). FX caching moved to
 *                       {@code FrankfurterProperties}; instrument profile is database-first (no cache).
 */
@ConfigurationProperties("finnhub")
public record FinnhubProperties(
        String apiKey,
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Cache cache) {

    /** Quote cache TTL (EN005 Rev 2 — profile is DB-first, FX cache is on Frankfurter). */
    public record Cache(Duration quoteTtl, Duration profileTtl) {
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
