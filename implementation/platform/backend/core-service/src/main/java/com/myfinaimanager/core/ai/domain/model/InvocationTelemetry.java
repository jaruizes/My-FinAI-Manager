package com.myfinaimanager.core.ai.domain.model;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The exact, safe-to-export telemetry record for one invocation (contract {@code ai-telemetry.md};
 * FR-043). Deliberately carries no raw prompt/response text, no credential, no user/Portfolio
 * identifier — {@link com.myfinaimanager.core.ai.domain.ports.TelemetryPort} implementations must
 * never be handed anything beyond this shape.
 */
public record InvocationTelemetry(
        String provider,
        String model,
        String taskType,
        String promptId,
        String promptVersion,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        BigDecimal estimatedCost,
        long latencyMs,
        boolean success,
        Optional<String> finishReason,
        String guardrailResult,
        String correlationId,
        String invocationId,
        Optional<String> providerRequestId,
        Optional<String> errorType) {

    public InvocationTelemetry {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (taskType == null || taskType.isBlank()) {
            throw new IllegalArgumentException("taskType must not be blank");
        }
        if (promptId == null || promptVersion == null) {
            throw new IllegalArgumentException("promptId/promptVersion must not be null");
        }
        if (guardrailResult == null || guardrailResult.isBlank()) {
            throw new IllegalArgumentException("guardrailResult must not be blank");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (invocationId == null || invocationId.isBlank()) {
            throw new IllegalArgumentException("invocationId must not be blank");
        }
    }
}
