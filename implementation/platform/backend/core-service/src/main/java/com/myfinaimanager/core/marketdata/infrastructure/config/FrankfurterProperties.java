package com.myfinaimanager.core.marketdata.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the Frankfurter FX-rate integration (EN005 Revision 2; DR-032).
 * Bound from the {@code frankfurter.*} block in {@code application.yml}.
 *
 * <p>Frankfurter (ECB reference rates) is a <strong>keyless</strong> public API — there is no
 * secret here and none is fabricated (VC-011). {@code baseUrl} is overridable via
 * {@code FRANKFURTER_BASE_URL} so the containerized E2E can point the real adapter at a stub.
 *
 * @param baseUrl        Frankfurter API base URL (default {@code https://api.frankfurter.dev})
 * @param connectTimeout outbound connect timeout (explicit — FR-017)
 * @param readTimeout    outbound read timeout (explicit — FR-017)
 * @param cache          short-lived in-process FX cache TTL (Q3 — kept; Frankfurter updates daily)
 */
@ConfigurationProperties("frankfurter")
public record FrankfurterProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Cache cache) {

    /** FX cache TTL. */
    public record Cache(Duration fxTtl) {
    }
}
