package com.myfinaimanager.core.ai.domain.model;

import java.math.BigDecimal;

/**
 * Provider-neutral token/cost usage for one invocation (FR-008, FR-030, FR-031). Populated by
 * every {@code AiModelPort} implementation — the shipped local/stub adapter uses deterministic,
 * clearly-synthetic figures since no live provider exists (FR-033).
 *
 * @param inputTokens   estimated/actual input tokens (&ge;0)
 * @param outputTokens  actual output tokens (&ge;0)
 * @param totalTokens   {@code inputTokens + outputTokens}
 * @param provider      the provider identifier, e.g. {@code "local"}
 * @param model         the model identifier, e.g. {@code "local-deterministic-v1"}
 * @param estimatedCost estimated cost (&ge;0; {@code ZERO} when no real provider is involved)
 */
public record AiUsage(
        int inputTokens,
        int outputTokens,
        int totalTokens,
        String provider,
        String model,
        BigDecimal estimatedCost) {

    public AiUsage {
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (estimatedCost == null || estimatedCost.signum() < 0) {
            throw new IllegalArgumentException("estimatedCost must be non-negative");
        }
    }
}
