package com.myfinaimanager.core.ai.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.myfinaimanager.core.ai.domain.model.AiInvocationSettings.TokenLimits;
import com.myfinaimanager.core.ai.domain.model.OutputSchema.FieldSpec;
import com.myfinaimanager.core.ai.domain.model.OutputSchema.FieldType;

/**
 * Construction invariants of the EN006 provider-neutral domain models (data-model.md §1). Every
 * guard clause in every compact constructor is exercised individually — these are pure value
 * objects with no framework wiring, so full branch coverage is achievable and expected
 * (testing-strategy.md §15 — coverage exclusions are reserved for trivial/generated code, not
 * validation logic).
 */
class AiDomainModelTest {

    private static final PromptReference PROMPT = new PromptReference("global-system", "v1", "Be careful.");
    private static final AiUsage USAGE = new AiUsage(1, 1, 2, "local", "m", BigDecimal.ZERO);

    // ---- AiRequest --------------------------------------------------------------------------

    @Test
    void aiRequest_of_starts_with_an_unresolved_system_prompt() {
        AiRequest request = AiRequest.of("diagnostic", "hello", "", Optional.empty(), 64, "corr-1");

        assertThat(request.isSystemPromptResolved()).isFalse();
        assertThat(request.maxOutputTokens()).isEqualTo(64);
    }

    @Test
    void aiRequest_rejects_blank_taskType() {
        assertThatThrownBy(() -> validRequest(" ", PROMPT, "hi", "", 64, " ", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiRequest_rejects_null_systemPrompt() {
        assertThatThrownBy(() -> new AiRequest(
                        "t", null, "hi", "", Optional.empty(), 64, Optional.empty(), Map.of(), "c"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiRequest_rejects_null_userPrompt() {
        assertThatThrownBy(() -> new AiRequest(
                        "t", PROMPT, null, "", Optional.empty(), 64, Optional.empty(), Map.of(), "c"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiRequest_rejects_null_context() {
        assertThatThrownBy(() -> new AiRequest(
                        "t", PROMPT, "hi", null, Optional.empty(), 64, Optional.empty(), Map.of(), "c"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiRequest_rejects_null_outputSchema() {
        assertThatThrownBy(() -> new AiRequest("t", PROMPT, "hi", "", null, 64, Optional.empty(), Map.of(), "c"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiRequest_rejects_non_positive_maxOutputTokens() {
        assertThatThrownBy(() -> AiRequest.of("diagnostic", "hi", "", Optional.empty(), 0, "c"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiRequest_rejects_null_temperature() {
        assertThatThrownBy(() -> new AiRequest("t", PROMPT, "hi", "", Optional.empty(), 64, null, Map.of(), "c"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiRequest_rejects_blank_correlationId() {
        assertThatThrownBy(() -> AiRequest.of("t", "hi", "", Optional.empty(), 64, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiRequest_treats_a_null_metadata_map_as_empty() {
        AiRequest request = new AiRequest("t", PROMPT, "hi", "", Optional.empty(), 64, Optional.empty(), null, "c");

        assertThat(request.metadata()).isEmpty();
    }

    @Test
    void aiRequest_withSystemPrompt_marks_it_resolved() {
        AiRequest request = AiRequest.of("diagnostic", "hi", "", Optional.empty(), 64, "c1");

        AiRequest withPrompt = request.withSystemPrompt(PROMPT);

        assertThat(withPrompt.isSystemPromptResolved()).isTrue();
        assertThat(withPrompt.systemPrompt()).isEqualTo(PROMPT);
    }

    @Test
    void aiRequest_withContext_replaces_only_context() {
        AiRequest request = AiRequest.of("diagnostic", "hi", "raw", Optional.empty(), 64, "c1");

        AiRequest budgeted = request.withContext("budgeted");

        assertThat(budgeted.context()).isEqualTo("budgeted");
        assertThat(budgeted.userPrompt()).isEqualTo("hi");
    }

    @Test
    void aiRequest_diagnostic_is_self_consistent() {
        AiRequest diagnostic = AiRequest.diagnostic();

        assertThat(diagnostic.taskType()).isEqualTo("diagnostic");
        assertThat(diagnostic.correlationId()).startsWith("diagnostic-");
    }

    private static AiRequest validRequest(
            String taskType, PromptReference prompt, String userPrompt, String context, int maxOutputTokens,
            String temperatureIgnored, String correlationId) {
        return new AiRequest(
                taskType, prompt, userPrompt, context, Optional.empty(), maxOutputTokens, Optional.empty(),
                Map.of(), correlationId);
    }

    // ---- AiResponse -------------------------------------------------------------------------

    @Test
    void aiResponse_rejects_null_content() {
        assertThatThrownBy(() -> new AiResponse(
                        null, Optional.empty(), "local", "m", USAGE, 1, Optional.empty(), "r", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiResponse_rejects_null_structuredContent_optional() {
        assertThatThrownBy(() -> new AiResponse(
                        "x", null, "local", "m", USAGE, 1, Optional.empty(), "r", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiResponse_rejects_blank_provider() {
        assertThatThrownBy(() -> new AiResponse(
                        "x", Optional.empty(), " ", "m", USAGE, 1, Optional.empty(), "r", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiResponse_rejects_blank_model() {
        assertThatThrownBy(() -> new AiResponse(
                        "x", Optional.empty(), "local", " ", USAGE, 1, Optional.empty(), "r", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiResponse_rejects_null_usage() {
        assertThatThrownBy(() -> new AiResponse(
                        "x", Optional.empty(), "local", "m", null, 1, Optional.empty(), "r", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiResponse_rejects_negative_latency() {
        assertThatThrownBy(() -> new AiResponse(
                        "x", Optional.empty(), "local", "m", USAGE, -1, Optional.empty(), "r", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiResponse_rejects_null_finishReason_optional() {
        assertThatThrownBy(() -> new AiResponse("x", Optional.empty(), "local", "m", USAGE, 1, null, "r", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiResponse_requires_non_blank_requestId() {
        assertThatThrownBy(() -> new AiResponse(
                        "content", Optional.empty(), "local", "m", USAGE, 1, Optional.empty(), " ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiResponse_rejects_null_generatedAt() {
        assertThatThrownBy(() -> new AiResponse(
                        "x", Optional.empty(), "local", "m", USAGE, 1, Optional.empty(), "r", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiResponse_copies_structuredContent_defensively() {
        Map<String, Object> mutable = new java.util.HashMap<>(Map.of("a", 1));
        AiResponse response = new AiResponse(
                "x", Optional.of(mutable), "local", "m", USAGE, 1, Optional.empty(), "r", Instant.now());

        assertThat(response.structuredContent()).contains(Map.of("a", 1));
    }

    // ---- AiUsage ----------------------------------------------------------------------------

    @Test
    void aiUsage_rejects_negative_inputTokens() {
        assertThatThrownBy(() -> new AiUsage(-1, 0, 0, "local", "m", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiUsage_rejects_negative_outputTokens() {
        assertThatThrownBy(() -> new AiUsage(0, -1, 0, "local", "m", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiUsage_rejects_negative_totalTokens() {
        assertThatThrownBy(() -> new AiUsage(0, 0, -1, "local", "m", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiUsage_rejects_blank_provider() {
        assertThatThrownBy(() -> new AiUsage(0, 0, 0, " ", "m", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiUsage_rejects_blank_model() {
        assertThatThrownBy(() -> new AiUsage(0, 0, 0, "local", " ", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiUsage_rejects_null_estimatedCost() {
        assertThatThrownBy(() -> new AiUsage(0, 0, 0, "local", "m", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiUsage_rejects_negative_estimatedCost() {
        assertThatThrownBy(() -> new AiUsage(0, 0, 0, "local", "m", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- PromptReference ----------------------------------------------------------------------

    @Test
    void promptReference_rejects_blank_promptId() {
        assertThatThrownBy(() -> new PromptReference(" ", "v1", "body"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void promptReference_rejects_blank_promptVersion() {
        assertThatThrownBy(() -> new PromptReference("id", " ", "body"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void promptReference_rejects_null_body() {
        assertThatThrownBy(() -> new PromptReference("id", "v1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- OutputSchema / FieldSpec -------------------------------------------------------------

    @Test
    void outputSchema_rejects_null_fields() {
        assertThatThrownBy(() -> new OutputSchema(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outputSchema_rejects_empty_fields() {
        assertThatThrownBy(() -> new OutputSchema(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fieldSpec_rejects_blank_name() {
        assertThatThrownBy(() -> new FieldSpec(" ", FieldType.STRING, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fieldSpec_rejects_null_type() {
        assertThatThrownBy(() -> new FieldSpec("name", null, true)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- GuardrailOutcome ---------------------------------------------------------------------

    @Test
    void guardrailOutcome_allowed_and_rejected() {
        assertThat(GuardrailOutcome.allowed().isAllowed()).isTrue();
        GuardrailOutcome rejected = GuardrailOutcome.rejected("g", "reason");
        assertThat(rejected.isAllowed()).isFalse();
        assertThat(((GuardrailOutcome.Rejected) rejected).guardrailName()).isEqualTo("g");
    }

    @Test
    void guardrailOutcome_rejected_requires_a_guardrail_name() {
        assertThatThrownBy(() -> new GuardrailOutcome.Rejected(" ", "reason"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void guardrailOutcome_rejected_requires_a_reason() {
        assertThatThrownBy(() -> new GuardrailOutcome.Rejected("g", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- TokenUsageEstimate -------------------------------------------------------------------

    @Test
    void tokenUsageEstimate_computes_total() {
        assertThat(new TokenUsageEstimate(10, 20).estimatedTotalTokens()).isEqualTo(30);
    }

    @Test
    void tokenUsageEstimate_rejects_negative_inputTokens() {
        assertThatThrownBy(() -> new TokenUsageEstimate(-1, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenUsageEstimate_rejects_non_positive_maxOutputTokens() {
        assertThatThrownBy(() -> new TokenUsageEstimate(1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- AiInvocationSettings / TokenLimits ----------------------------------------------------

    private static AiInvocationSettings.TokenLimits validLimits() {
        return new TokenLimits(1, 1, 1, 1);
    }

    @Test
    void aiInvocationSettings_rejects_blank_defaultProvider() {
        assertThatThrownBy(() -> new AiInvocationSettings(
                        " ", "m", validLimits(), BigDecimal.ZERO, Duration.ofSeconds(1), 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiInvocationSettings_rejects_blank_defaultModel() {
        assertThatThrownBy(() -> new AiInvocationSettings(
                        "local", " ", validLimits(), BigDecimal.ZERO, Duration.ofSeconds(1), 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiInvocationSettings_rejects_null_limits() {
        assertThatThrownBy(() -> new AiInvocationSettings(
                        "local", "m", null, BigDecimal.ZERO, Duration.ofSeconds(1), 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiInvocationSettings_rejects_null_maxEstimatedCost() {
        assertThatThrownBy(() -> new AiInvocationSettings(
                        "local", "m", validLimits(), null, Duration.ofSeconds(1), 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiInvocationSettings_rejects_negative_maxEstimatedCost() {
        assertThatThrownBy(() -> new AiInvocationSettings(
                        "local", "m", validLimits(), new BigDecimal("-1"), Duration.ofSeconds(1), 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiInvocationSettings_rejects_null_timeout() {
        assertThatThrownBy(() -> new AiInvocationSettings(
                        "local", "m", validLimits(), BigDecimal.ZERO, null, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiInvocationSettings_rejects_negative_timeout() {
        assertThatThrownBy(() -> new AiInvocationSettings(
                        "local", "m", validLimits(), BigDecimal.ZERO, Duration.ofSeconds(-1), 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiInvocationSettings_rejects_non_positive_timeout() {
        assertThatThrownBy(() -> new AiInvocationSettings(
                        "local", "m", validLimits(), BigDecimal.ZERO, Duration.ZERO, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiInvocationSettings_rejects_non_positive_maxRetryAttempts() {
        assertThatThrownBy(() -> new AiInvocationSettings(
                        "local", "m", validLimits(), BigDecimal.ZERO, Duration.ofSeconds(1), 0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiInvocationSettings_rejects_null_retryBackoff() {
        assertThatThrownBy(() -> new AiInvocationSettings(
                        "local", "m", validLimits(), BigDecimal.ZERO, Duration.ofSeconds(1), 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiInvocationSettings_rejects_negative_retryBackoff() {
        assertThatThrownBy(() -> new AiInvocationSettings(
                        "local", "m", validLimits(), BigDecimal.ZERO, Duration.ofSeconds(1), 1, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenLimits_rejects_non_positive_maxInputTokens() {
        assertThatThrownBy(() -> new TokenLimits(0, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenLimits_rejects_non_positive_maxOutputTokens() {
        assertThatThrownBy(() -> new TokenLimits(1, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenLimits_rejects_non_positive_maxTotalTokens() {
        assertThatThrownBy(() -> new TokenLimits(1, 1, 0, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenLimits_rejects_non_positive_maxInputCharacters() {
        assertThatThrownBy(() -> new TokenLimits(1, 1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- InvocationTelemetry -------------------------------------------------------------------

    @Test
    void invocationTelemetry_rejects_blank_provider() {
        assertThatThrownBy(() -> new InvocationTelemetry(
                        " ", "m", "task", "p", "v", 1, 1, 2, BigDecimal.ZERO, 1, true, Optional.empty(), "allowed",
                        "c", "i", Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invocationTelemetry_rejects_blank_model() {
        assertThatThrownBy(() -> new InvocationTelemetry(
                        "local", " ", "task", "p", "v", 1, 1, 2, BigDecimal.ZERO, 1, true, Optional.empty(),
                        "allowed", "c", "i", Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invocationTelemetry_rejects_blank_taskType() {
        assertThatThrownBy(() -> new InvocationTelemetry(
                        "local", "m", " ", "p", "v", 1, 1, 2, BigDecimal.ZERO, 1, true, Optional.empty(), "allowed",
                        "c", "i", Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invocationTelemetry_rejects_null_promptId() {
        assertThatThrownBy(() -> new InvocationTelemetry(
                        "local", "m", "task", null, "v", 1, 1, 2, BigDecimal.ZERO, 1, true, Optional.empty(),
                        "allowed", "c", "i", Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invocationTelemetry_rejects_null_promptVersion() {
        assertThatThrownBy(() -> new InvocationTelemetry(
                        "local", "m", "task", "p", null, 1, 1, 2, BigDecimal.ZERO, 1, true, Optional.empty(),
                        "allowed", "c", "i", Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invocationTelemetry_rejects_blank_guardrailResult() {
        assertThatThrownBy(() -> new InvocationTelemetry(
                        "local", "m", "task", "p", "v", 1, 1, 2, BigDecimal.ZERO, 1, true, Optional.empty(), " ",
                        "c", "i", Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invocationTelemetry_rejects_blank_correlationId() {
        assertThatThrownBy(() -> new InvocationTelemetry(
                        "local", "m", "task", "p", "v", 1, 1, 2, BigDecimal.ZERO, 1, true, Optional.empty(),
                        "allowed", " ", "inv-1", Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invocationTelemetry_rejects_blank_invocationId() {
        assertThatThrownBy(() -> new InvocationTelemetry(
                        "local", "m", "task", "p", "v", 1, 1, 2, BigDecimal.ZERO, 1, true, Optional.empty(),
                        "allowed", "c", " ", Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
