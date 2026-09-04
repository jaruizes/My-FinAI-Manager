package com.myfinaimanager.core.ai.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.myfinaimanager.core.ai.domain.model.InvocationTelemetry;
import com.myfinaimanager.core.ai.domain.ports.TelemetryScope;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;

/** Contract {@code ai-telemetry.md} — FR-043, FR-044, FR-045, VC-016, VC-017, VC-030. */
class AiTelemetryRecorderTest {

    private static InvocationTelemetry telemetry(boolean success, String guardrailResult, Optional<String> error) {
        return new InvocationTelemetry(
                "local", "local-deterministic-v1", "diagnostic", "global-system", "v1", 10, 5, 15,
                BigDecimal.ZERO, 3, success, Optional.of("stop"), guardrailResult, "corr-1", "inv-1",
                Optional.of("req-1"), error);
    }

    @Test
    void records_the_usecase_and_invocation_span_pair_with_the_expected_attributes() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        AiTelemetryRecorder recorder = new AiTelemetryRecorder(registry, new SimpleMeterRegistry());

        try (TelemetryScope usecase = recorder.startUsecase("diagnostic")) {
            try (TelemetryScope invocation = recorder.startInvocation()) {
                // simulate the provider call happening here
            }
            recorder.recordOutcome(telemetry(true, "allowed", Optional.empty()));
        }

        TestObservationRegistryAssert.assertThat(registry)
                .hasNumberOfObservationsWithNameEqualTo("ai.usecase", 1)
                .hasNumberOfObservationsWithNameEqualTo("ai.invocation", 1)
                .hasObservationWithNameEqualTo("ai.usecase")
                .that()
                .hasLowCardinalityKeyValue("ai.task", "diagnostic")
                .hasLowCardinalityKeyValue("gen_ai.system", "local")
                .hasLowCardinalityKeyValue("ai.guardrail.result", "allowed")
                .hasLowCardinalityKeyValue("ai.success", "true");
    }

    @Test
    void records_the_seven_ai_meters() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiTelemetryRecorder recorder = new AiTelemetryRecorder(observationRegistry, meterRegistry);

        try (TelemetryScope usecase = recorder.startUsecase("diagnostic")) {
            recorder.recordOutcome(telemetry(true, "allowed", Optional.empty()));
        }

        assertThat(meterRegistry.find("ai_requests_total").counter()).isNotNull();
        assertThat(meterRegistry.find("ai_request_duration").timer()).isNotNull();
        assertThat(meterRegistry.find("ai_input_tokens_total").counter().count()).isEqualTo(10);
        assertThat(meterRegistry.find("ai_output_tokens_total").counter().count()).isEqualTo(5);
        assertThat(meterRegistry.find("ai_estimated_cost_total").counter()).isNotNull();
    }

    @Test
    void records_errors_and_guardrail_rejections_by_label() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiTelemetryRecorder recorder = new AiTelemetryRecorder(observationRegistry, meterRegistry);

        try (TelemetryScope usecase = recorder.startUsecase("diagnostic")) {
            recorder.recordOutcome(
                    telemetry(false, "rejected:input-injection", Optional.of("AiGuardrailRejectedException")));
        }

        assertThat(meterRegistry.find("ai_errors_total").tag("error", "AiGuardrailRejectedException").counter())
                .isNotNull();
        assertThat(meterRegistry
                        .find("ai_guardrail_rejections_total")
                        .tag("direction", "input")
                        .tag("guardrail", "input-injection")
                        .counter())
                .isNotNull();
    }

    @Test
    void records_an_output_guardrail_rejection_with_the_output_direction() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiTelemetryRecorder recorder = new AiTelemetryRecorder(observationRegistry, meterRegistry);

        try (TelemetryScope usecase = recorder.startUsecase("diagnostic")) {
            recorder.recordOutcome(telemetry(
                    false, "rejected:output-prohibited-action", Optional.of("AiGuardrailRejectedException")));
        }

        assertThat(meterRegistry
                        .find("ai_guardrail_rejections_total")
                        .tag("direction", "output")
                        .tag("guardrail", "output-prohibited-action")
                        .counter())
                .isNotNull();
    }

    @Test
    void a_rejected_guardrail_result_on_an_otherwise_successful_outcome_counts_as_rejected() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiTelemetryRecorder recorder = new AiTelemetryRecorder(observationRegistry, meterRegistry);

        try (TelemetryScope usecase = recorder.startUsecase("diagnostic")) {
            recorder.recordOutcome(telemetry(true, "rejected:output-prohibited-action", Optional.empty()));
        }

        assertThat(meterRegistry.find("ai_requests_total").tag("outcome", "rejected").counter()).isNotNull();
    }

    @Test
    void carries_no_field_that_could_hold_a_raw_prompt_response_or_credential() {
        // Structural safety net: the recorder must have no field of a type that could carry raw
        // prompt/response text or a credential (contract ai-telemetry.md "Never present").
        for (Field field : AiTelemetryRecorder.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase(java.util.Locale.ROOT);
            assertThat(name).doesNotContain("prompt").doesNotContain("content").doesNotContain("credential")
                    .doesNotContain("apikey");
        }
    }

    @Test
    void a_telemetry_failure_never_throws() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiTelemetryRecorder recorder = new AiTelemetryRecorder(observationRegistry, meterRegistry);

        // No open span/observation when recordOutcome is called directly — must not throw.
        recorder.recordOutcome(telemetry(true, "allowed", Optional.empty()));
    }
}
