package com.myfinaimanager.core.ai.infrastructure.api;

/**
 * The only fields {@code /actuator/ai-diagnostic} ever returns (contract
 * {@code ai-diagnostic-endpoint.md}) — never the prompt text or the response content.
 *
 * @param requestId  the adapter-assigned request id, useful to correlate with a Jaeger trace's
 *                    {@code ai.provider.request.id} attribute
 * @param latencyMs  the response's own {@code latencyMs}
 * @param totalTokens the response's own {@code usage.totalTokens}
 */
public record AiDiagnosticResult(String requestId, long latencyMs, int totalTokens) {
}
