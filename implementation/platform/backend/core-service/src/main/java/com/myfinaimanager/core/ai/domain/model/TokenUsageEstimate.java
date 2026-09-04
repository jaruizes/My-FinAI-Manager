package com.myfinaimanager.core.ai.domain.model;

/**
 * A pre-invocation token estimate (FR-027), checked against {@link AiInvocationSettings.TokenLimits}
 * before {@code AiModelPort.generate(...)} is ever called (FR-028).
 *
 * @param inputTokens     estimated input tokens (&ge;0)
 * @param maxOutputTokens the request's requested output-token ceiling (&gt;0)
 */
public record TokenUsageEstimate(int inputTokens, int maxOutputTokens) {

    public TokenUsageEstimate {
        if (inputTokens < 0) {
            throw new IllegalArgumentException("inputTokens must not be negative");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be > 0");
        }
    }

    public int estimatedTotalTokens() {
        return inputTokens + maxOutputTokens;
    }
}
