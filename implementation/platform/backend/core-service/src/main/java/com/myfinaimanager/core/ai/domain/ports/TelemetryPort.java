package com.myfinaimanager.core.ai.domain.ports;

import com.myfinaimanager.core.ai.domain.model.InvocationTelemetry;

/**
 * Records the nested span chain and metrics for one invocation (FR-043, FR-044; contract
 * {@code ai-telemetry.md}). The shipped implementation wraps Micrometer Observation + a
 * MeterRegistry, exported as OTLP (research D1) — none of that appears here or in
 * {@code ai.business}.
 *
 * <p>Usage (in {@code AiInvocationPolicy}):
 *
 * <pre>{@code
 * try (TelemetryScope usecase = telemetry.startUsecase(request.taskType())) {
 *     ...
 *     try (TelemetryScope invocation = telemetry.startInvocation()) {
 *         response = adapter.generate(request);
 *     }
 *     ...
 *     telemetry.recordOutcome(InvocationTelemetry...);
 * }
 * }</pre>
 */
public interface TelemetryPort {

    /** Opens the outer {@code ai.usecase} span for one {@code AiInvocationPolicy.generate(...)} call. */
    TelemetryScope startUsecase(String taskType);

    /** Opens the inner {@code ai.invocation} span around one {@code AiModelPort.generate(...)} attempt. */
    TelemetryScope startInvocation();

    /**
     * Records the final metrics + span attributes for the still-open usecase span. Must never throw
     * or otherwise affect the business result — a telemetry failure is swallowed by the
     * implementation.
     */
    void recordOutcome(InvocationTelemetry telemetry);
}
