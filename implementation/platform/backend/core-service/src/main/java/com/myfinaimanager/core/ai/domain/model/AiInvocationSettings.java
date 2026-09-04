package com.myfinaimanager.core.ai.domain.model;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Domain-safe view of {@code ai.*} configuration (research D-plan OD-3). {@code ai.infrastructure
 * .config.AiProperties} is the Spring {@code @ConfigurationProperties} binding target; a
 * {@code @Configuration} class maps it to this plain record so {@code ai.business} never depends on
 * a Spring-annotated type (ADR-003; ArchUnit-enforced).
 *
 * @param defaultProvider   the configured default {@code AiModelPort} provider identifier
 * @param defaultModel      the configured default model identifier
 * @param limits            token/character budget ceilings
 * @param maxEstimatedCost  the maximum estimated cost allowed per request (&ge;0)
 * @param timeout           the bounded timeout around one {@code AiModelPort.generate(...)} call
 * @param maxRetryAttempts  maximum attempts (including the first) for a transient failure (&ge;1)
 * @param retryBackoff      delay between retry attempts
 */
public record AiInvocationSettings(
        String defaultProvider,
        String defaultModel,
        TokenLimits limits,
        BigDecimal maxEstimatedCost,
        Duration timeout,
        int maxRetryAttempts,
        Duration retryBackoff) {

    public AiInvocationSettings {
        if (defaultProvider == null || defaultProvider.isBlank()) {
            throw new IllegalArgumentException("defaultProvider must not be blank");
        }
        if (defaultModel == null || defaultModel.isBlank()) {
            throw new IllegalArgumentException("defaultModel must not be blank");
        }
        if (limits == null) {
            throw new IllegalArgumentException("limits must not be null");
        }
        if (maxEstimatedCost == null || maxEstimatedCost.signum() < 0) {
            throw new IllegalArgumentException("maxEstimatedCost must be non-negative");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxRetryAttempts < 1) {
            throw new IllegalArgumentException("maxRetryAttempts must be >= 1");
        }
        if (retryBackoff == null || retryBackoff.isNegative()) {
            throw new IllegalArgumentException("retryBackoff must not be negative");
        }
    }

    /**
     * @param maxInputTokens     ceiling on estimated input tokens (FR-026)
     * @param maxOutputTokens    ceiling on requested output tokens (FR-026, FR-032)
     * @param maxTotalTokens     ceiling on estimated input+output tokens (FR-026)
     * @param maxInputCharacters ceiling on the composed user prompt + context length (FR-022)
     */
    public record TokenLimits(int maxInputTokens, int maxOutputTokens, int maxTotalTokens, int maxInputCharacters) {

        public TokenLimits {
            if (maxInputTokens <= 0 || maxOutputTokens <= 0 || maxTotalTokens <= 0 || maxInputCharacters <= 0) {
                throw new IllegalArgumentException("token/character limits must be > 0");
            }
        }
    }
}
