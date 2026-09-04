package com.myfinaimanager.core.ai.infrastructure.observability;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.myfinaimanager.core.ai.domain.model.InvocationTelemetry;
import com.myfinaimanager.core.ai.domain.ports.TelemetryPort;
import com.myfinaimanager.core.ai.domain.ports.TelemetryScope;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * Records the {@code ai.usecase} → {@code ai.invocation} span pair plus the seven {@code ai_*}
 * meters (contract {@code ai-telemetry.md}; FR-043, FR-044, FR-045), via Micrometer Observation +
 * MeterRegistry, exported as OTLP (research D1). Never carries a raw prompt/response body,
 * credential, or user/Portfolio identifier — the attribute set below is exhaustive and closed.
 *
 * <p>A telemetry failure never breaks the business result (interface contract) — every method here
 * catches and logs rather than propagates.
 */
@Component
public class AiTelemetryRecorder implements TelemetryPort {

    private static final Logger LOG = LoggerFactory.getLogger(AiTelemetryRecorder.class);

    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;

    public AiTelemetryRecorder(ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public TelemetryScope startUsecase(String taskType) {
        return openSpan("ai.usecase", Observation.createNotStarted("ai.usecase", observationRegistry)
                .lowCardinalityKeyValue("ai.task", taskType)
                .lowCardinalityKeyValue("gen_ai.operation.name", "chat"));
    }

    @Override
    public TelemetryScope startInvocation() {
        return openSpan("ai.invocation", Observation.createNotStarted("ai.invocation", observationRegistry));
    }

    private TelemetryScope openSpan(String name, Observation observation) {
        try {
            observation.start();
            Observation.Scope scope = observation.openScope();
            return () -> {
                try {
                    scope.close();
                } finally {
                    observation.stop();
                }
            };
        } catch (RuntimeException e) {
            LOG.warn("event=AiTelemetryFailure span={} outcome=FAILURE", name, e);
            return () -> { };
        }
    }

    @Override
    public void recordOutcome(InvocationTelemetry telemetry) {
        try {
            annotateCurrentSpan(telemetry);
            recordMetrics(telemetry);
        } catch (RuntimeException e) {
            LOG.warn("event=AiTelemetryFailure outcome=FAILURE", e);
        }
    }

    private void annotateCurrentSpan(InvocationTelemetry t) {
        Observation current = observationRegistry.getCurrentObservation();
        if (current == null) {
            return;
        }
        current.lowCardinalityKeyValue("gen_ai.system", t.provider())
                .lowCardinalityKeyValue("gen_ai.request.model", t.model())
                .lowCardinalityKeyValue("ai.prompt.id", t.promptId())
                .lowCardinalityKeyValue("ai.prompt.version", t.promptVersion())
                .lowCardinalityKeyValue("ai.guardrail.result", t.guardrailResult())
                .lowCardinalityKeyValue("ai.success", String.valueOf(t.success()))
                .lowCardinalityKeyValue("ai.correlation.id", t.correlationId())
                .lowCardinalityKeyValue("ai.invocation.id", t.invocationId())
                .highCardinalityKeyValue("gen_ai.usage.input_tokens", String.valueOf(t.inputTokens()))
                .highCardinalityKeyValue("gen_ai.usage.output_tokens", String.valueOf(t.outputTokens()))
                .highCardinalityKeyValue("ai.estimated.cost", t.estimatedCost().toPlainString());
        t.finishReason().ifPresent(v -> current.highCardinalityKeyValue("ai.finish.reason", v));
        t.providerRequestId().ifPresent(v -> current.highCardinalityKeyValue("ai.provider.request.id", v));
    }

    private void recordMetrics(InvocationTelemetry t) {
        String outcome = !t.success() ? "error" : t.guardrailResult().startsWith("rejected") ? "rejected" : "success";

        meterRegistry.counter("ai_requests_total", "task", t.taskType(), "provider", t.provider(), "model",
                        t.model(), "outcome", outcome)
                .increment();

        // Percentile histogram enabled so the Grafana dashboard's p95 panel (histogram_quantile
        // over ai_request_duration_bucket) has buckets to query, not just _sum/_count.
        Timer.builder("ai_request_duration")
                .tag("task", t.taskType())
                .tag("provider", t.provider())
                .tag("model", t.model())
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Duration.ofMillis(t.latencyMs()));

        meterRegistry.counter("ai_input_tokens_total", "task", t.taskType(), "provider", t.provider(), "model",
                        t.model())
                .increment(t.inputTokens());
        meterRegistry.counter("ai_output_tokens_total", "task", t.taskType(), "provider", t.provider(), "model",
                        t.model())
                .increment(t.outputTokens());
        meterRegistry.counter("ai_estimated_cost_total", "task", t.taskType(), "provider", t.provider(), "model",
                        t.model())
                .increment(t.estimatedCost().doubleValue());

        if (!t.success()) {
            meterRegistry.counter("ai_errors_total", "task", t.taskType(), "provider", t.provider(), "model",
                            t.model(), "error", t.errorType().orElse("unknown"))
                    .increment();
        }
        if (t.guardrailResult().startsWith("rejected:")) {
            String guardrail = t.guardrailResult().substring("rejected:".length());
            meterRegistry.counter("ai_guardrail_rejections_total", "direction",
                            direction(guardrail), "guardrail", guardrail)
                    .increment();
        }
    }

    private String direction(String guardrailName) {
        return guardrailName.startsWith("input") ? "input" : "output";
    }
}
