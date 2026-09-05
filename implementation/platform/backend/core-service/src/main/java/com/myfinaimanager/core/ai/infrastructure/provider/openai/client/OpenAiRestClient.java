package com.myfinaimanager.core.ai.infrastructure.provider.openai.client;

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

import com.myfinaimanager.core.ai.domain.exceptions.AiInvalidResponseException;
import com.myfinaimanager.core.ai.domain.exceptions.AiProviderAuthenticationFailedException;
import com.myfinaimanager.core.ai.domain.exceptions.AiProviderNotConfiguredException;
import com.myfinaimanager.core.ai.domain.exceptions.AiProviderRateLimitedException;
import com.myfinaimanager.core.ai.domain.exceptions.AiProviderUnavailableException;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.config.OpenAiProperties;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.dto.OpenAiChatRequest;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.dto.OpenAiChatResponse;

/**
 * The <strong>only</strong> class that touches Spring's {@link RestClient} for the OpenAI provider
 * (ArchUnit-enforced — research D3). Attaches the API key as the {@code Authorization: Bearer}
 * request header (never a query parameter — mirrors EN005's Finnhub {@code X-Finnhub-Token}
 * handling), calls {@code POST /chat/completions}, and translates the outcome into EN006's
 * provider-neutral exceptions (contract {@code openai-provider-contract.md}). One structured log
 * per call ({@code event=ProviderCall provider=openai capability=chat-completion}) — no key, no
 * prompt/completion body.
 *
 * <p>Also the only class that builds a {@link RestClient} (with explicit connect/read timeouts).
 * The package-private {@code (RestClient, OpenAiProperties)} constructor lets a test bind
 * {@code MockRestServiceServer}.
 */
@Component
public class OpenAiRestClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiRestClient.class);

    private final RestClient restClient;
    private final OpenAiProperties properties;

    @Autowired
    public OpenAiRestClient(OpenAiProperties properties) {
        this(buildRestClient(properties), properties);
    }

    /** For tests: bind a {@code RestClient} built from a {@code MockRestServiceServer}-bound builder. */
    public OpenAiRestClient(RestClient restClient, OpenAiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    private static RestClient buildRestClient(OpenAiProperties p) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(p.connectTimeout())
                .withReadTimeout(p.readTimeout());
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder()
                .baseUrl(p.baseUrl().toString())
                .requestFactory(factory)
                .build();
    }

    /** Whether a non-blank API key is configured — no outbound call is ever made otherwise. */
    public boolean isConfigured() {
        return properties.apiKey() != null && !properties.apiKey().isBlank();
    }

    /**
     * @throws AiProviderNotConfiguredException  blank API key — no outbound call
     * @throws AiProviderAuthenticationFailedException HTTP 401/403
     * @throws AiProviderRateLimitedException          HTTP 429
     * @throws AiProviderUnavailableException          any other 4xx/5xx, timeout, or network error
     * @throws AiInvalidResponseException              an empty/unreadable 2xx response
     */
    public OpenAiChatResponse chatCompletion(OpenAiChatRequest request) {
        if (!isConfigured()) {
            throw new AiProviderNotConfiguredException("OpenAI provider is not configured (no API key)");
        }

        long startNanos = System.nanoTime();
        try {
            OpenAiChatResponse body = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatResponse.class);
            if (body == null || body.choices() == null || body.choices().isEmpty()) {
                logCall("INVALID", "2xx-empty", startNanos, null);
                throw new AiInvalidResponseException("OpenAI returned no completion choices");
            }
            logCall("SUCCESS", "2xx", startNanos, null);
            return body;

        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 401 || code == 403) {
                logCall("AUTH_FAILED", "4xx", startNanos, e);
                throw new AiProviderAuthenticationFailedException("OpenAI rejected the credentials");
            }
            if (code == 429) {
                logCall("RATE_LIMITED", "429", startNanos, e);
                throw new AiProviderRateLimitedException("OpenAI rate limit reached");
            }
            logCall("UNAVAILABLE", code >= 500 ? "5xx" : "4xx", startNanos, e);
            throw new AiProviderUnavailableException("OpenAI returned HTTP " + code);

        } catch (ResourceAccessException e) {
            logCall("UNAVAILABLE", "NETWORK", startNanos, e);
            throw new AiProviderUnavailableException("network error contacting OpenAI");

        } catch (RestClientException e) {
            logCall("UNAVAILABLE", "BAD_RESPONSE", startNanos, e);
            throw new AiProviderUnavailableException("could not read the OpenAI response");
        }
    }

    private void logCall(String outcome, String statusCategory, long startNanos, Throwable cause) {
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
        var entry = ("SUCCESS".equals(outcome) ? log.atInfo() : log.atWarn())
                .addKeyValue("event", "ProviderCall")
                .addKeyValue("provider", "openai")
                .addKeyValue("capability", "chat-completion")
                .addKeyValue("outcome", outcome)
                .addKeyValue("httpStatusCategory", statusCategory)
                .addKeyValue("latencyMs", latencyMs);
        if (cause != null) {
            entry = entry.addKeyValue("errorType", cause.getClass().getSimpleName()); // type only — never the message
        }
        entry.log("OpenAI call chat-completion {}", outcome);
    }
}
