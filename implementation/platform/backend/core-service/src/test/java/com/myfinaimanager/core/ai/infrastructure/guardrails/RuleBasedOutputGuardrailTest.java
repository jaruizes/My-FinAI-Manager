package com.myfinaimanager.core.ai.infrastructure.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;
import com.myfinaimanager.core.ai.domain.model.AiUsage;
import com.myfinaimanager.core.ai.domain.model.GuardrailOutcome;

/** FR-020, FR-023; research D5. */
class RuleBasedOutputGuardrailTest {

    private final RuleBasedOutputGuardrail guardrail = new RuleBasedOutputGuardrail();
    private final AiRequest request = AiRequest.of("diagnostic", "hi", "", Optional.empty(), 10, "c1");

    private static AiResponse responseWith(String content) {
        AiUsage usage = new AiUsage(1, 1, 2, "local", "m", BigDecimal.ZERO);
        return new AiResponse(content, Optional.empty(), "local", "m", usage, 1, Optional.of("stop"), "req-1",
                Instant.now());
    }

    @Test
    void allows_a_clean_response() {
        assertThat(guardrail.check(request, responseWith("Here is a summary of your portfolio.")).isAllowed())
                .isTrue();
    }

    @Test
    void rejects_a_response_claiming_an_executed_action() {
        GuardrailOutcome outcome = guardrail.check(request, responseWith("I have sold your AAPL position."));

        assertThat(outcome.isAllowed()).isFalse();
        assertThat(((GuardrailOutcome.Rejected) outcome).guardrailName()).isEqualTo("output-prohibited-action");
    }

    @Test
    void rejects_case_insensitively() {
        assertThat(guardrail.check(request, responseWith("ORDER PLACED for 10 shares.")).isAllowed()).isFalse();
    }
}
