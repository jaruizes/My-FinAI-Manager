package com.myfinaimanager.core.ai.infrastructure.config;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized {@code ai.*} configuration (EN006; data-model.md §4). Bound from {@code application.yml};
 * mapped to the domain-safe {@link com.myfinaimanager.core.ai.domain.model.AiInvocationSettings} by
 * {@link AiModuleConfiguration} so {@code ai.business} never depends on this Spring-annotated type
 * (ADR-003).
 *
 * @param defaultProvider the default {@code AiModelPort} provider identifier
 * @param defaultModel    the default model identifier
 * @param limits          token/character budget ceilings
 * @param timeout         connect/read timeouts around one provider call
 * @param retry           bounded-retry policy for transient failures
 */
@ConfigurationProperties("ai")
public record AiProperties(
        String defaultProvider,
        String defaultModel,
        Limits limits,
        Timeout timeout,
        Retry retry) {

    public record Limits(
            int maxInputTokens,
            int maxOutputTokens,
            int maxTotalTokens,
            int maxInputCharacters,
            BigDecimal maxEstimatedCost) {
    }

    public record Timeout(Duration connect, Duration read) {
    }

    public record Retry(int maxAttempts, Duration backoff) {
    }
}
