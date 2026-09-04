package com.myfinaimanager.core.ai.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * A provider-neutral AI generation result (FR-008). Only normalized, provider-neutral metadata
 * ever appears here — a provider payload type never crosses {@code AiModelPort} (FR-009).
 *
 * @param content          the generated text
 * @param structuredContent present only once a requested {@link OutputSchema} has been validated
 *                          (FR-017–FR-019); a flat field-name → value map, never a partial/invalid one
 * @param provider         the provider identifier
 * @param model            the model identifier
 * @param usage            token/cost usage
 * @param latencyMs        latency of the provider call itself (&ge;0)
 * @param finishReason     provider-reported completion reason, when available
 * @param requestId        a provider- or adapter-assigned request id, when available
 * @param generatedAt      when this response was produced
 */
public record AiResponse(
        String content,
        Optional<Map<String, Object>> structuredContent,
        String provider,
        String model,
        AiUsage usage,
        long latencyMs,
        Optional<String> finishReason,
        String requestId,
        Instant generatedAt) {

    public AiResponse {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (structuredContent == null) {
            throw new IllegalArgumentException("structuredContent must not be null (use Optional.empty())");
        }
        structuredContent = structuredContent.map(Map::copyOf);
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (usage == null) {
            throw new IllegalArgumentException("usage must not be null");
        }
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
        if (finishReason == null) {
            throw new IllegalArgumentException("finishReason must not be null (use Optional.empty())");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt must not be null");
        }
    }
}
