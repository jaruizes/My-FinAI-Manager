package com.myfinaimanager.core.ai.infrastructure.provider.openai.config;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the OpenAI provider adapter (FD005 research D3; DR-032). Bound
 * from the {@code openai.*} block in {@code application.yml}.
 *
 * <p>{@code apiKey} is supplied per environment via {@code OPENAI_API_KEY} and is NEVER committed
 * (VC-011). When it is blank, {@code OpenAiRestClient} throws {@code AiProviderNotConfiguredException}
 * with no outbound call — mirrors EN005's Finnhub blank-key behavior exactly. The key is sent only
 * as the {@code Authorization: Bearer} request header, never a URL query parameter, so it cannot
 * appear in a URL, log line, or exception.
 *
 * @param apiKey         the OpenAI API key (blank = integration disabled)
 * @param baseUrl        OpenAI API base URL (default {@code https://api.openai.com/v1})
 * @param model          the chat completion model identifier (spec A1)
 * @param connectTimeout outbound connect timeout (explicit — mirrors EN005's FR-017 pattern)
 * @param readTimeout    outbound read timeout
 * @param pricing        placeholder per-1k-token pricing used to estimate cost (spec A1)
 */
@ConfigurationProperties("openai")
public record OpenAiProperties(
        String apiKey,
        URI baseUrl,
        String model,
        Duration connectTimeout,
        Duration readTimeout,
        Pricing pricing) {

    /** Placeholder per-1k-token pricing — {@code BigDecimal}, never binary floating point. */
    public record Pricing(BigDecimal inputPer1k, BigDecimal outputPer1k) {
    }
}
