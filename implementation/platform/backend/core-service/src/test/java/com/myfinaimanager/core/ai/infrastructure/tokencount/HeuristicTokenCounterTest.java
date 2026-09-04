package com.myfinaimanager.core.ai.infrastructure.tokencount;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.PromptReference;
import com.myfinaimanager.core.ai.domain.model.TokenUsageEstimate;

/** FR-027; research D4 — {@code ceil(characters / 4)}. */
class HeuristicTokenCounterTest {

    private final HeuristicTokenCounter counter = new HeuristicTokenCounter();

    @Test
    void estimates_input_tokens_from_prompt_plus_context_length() {
        PromptReference systemPrompt = new PromptReference("global-system", "v1", "1234"); // 4 chars
        AiRequest request = new AiRequest(
                "diagnostic", systemPrompt, "12345678", "12", Optional.empty(), 64, Optional.empty(),
                java.util.Map.of(), "c1"); // 8 + 2 = 10 chars context+prompt; total 14 chars

        TokenUsageEstimate estimate = counter.estimate(request);

        assertThat(estimate.inputTokens()).isEqualTo(Math.ceilDiv(14, 4));
        assertThat(estimate.maxOutputTokens()).isEqualTo(64);
    }

    @Test
    void never_estimates_zero_tokens_for_non_empty_text() {
        PromptReference systemPrompt = new PromptReference("global-system", "v1", "a");
        AiRequest request = new AiRequest(
                "diagnostic", systemPrompt, "", "", Optional.empty(), 1, Optional.empty(),
                java.util.Map.of(), "c1");

        assertThat(counter.estimate(request).inputTokens()).isGreaterThanOrEqualTo(1);
    }
}
