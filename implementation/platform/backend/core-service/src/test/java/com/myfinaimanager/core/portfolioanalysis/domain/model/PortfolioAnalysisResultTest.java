package com.myfinaimanager.core.portfolioanalysis.domain.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Every validation guard on the AI-produced result (data-model.md §2). */
class PortfolioAnalysisResultTest {

    private static PortfolioAnalysisResult build(String provider, String model, String promptId,
                                                 String promptVersion, String explanation, int inputTokens,
                                                 int outputTokens, int totalTokens, BigDecimal cost) {
        return new PortfolioAnalysisResult(
                DiversificationLevel.LOW, explanation, List.of(), List.of(), provider, model, promptId,
                promptVersion, inputTokens, outputTokens, totalTokens, cost);
    }

    @Test
    void rejects_a_null_overall_diversification() {
        assertThatThrownBy(() -> new PortfolioAnalysisResult(
                        null, "x", List.of(), List.of(), "p", "m", "id", "v", 1, 1, 2, BigDecimal.ZERO))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_a_blank_explanation() {
        assertThatThrownBy(() -> build("p", "m", "id", "v", " ", 1, 1, 2, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_blank_provider() {
        assertThatThrownBy(() -> build(" ", "m", "id", "v", "x", 1, 1, 2, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_blank_model() {
        assertThatThrownBy(() -> build("p", " ", "id", "v", "x", 1, 1, 2, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_blank_promptId() {
        assertThatThrownBy(() -> build("p", "m", " ", "v", "x", 1, 1, 2, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_blank_promptVersion() {
        assertThatThrownBy(() -> build("p", "m", "id", " ", "x", 1, 1, 2, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_negative_input_tokens() {
        assertThatThrownBy(() -> build("p", "m", "id", "v", "x", -1, 1, 2, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_negative_output_tokens() {
        assertThatThrownBy(() -> build("p", "m", "id", "v", "x", 1, -1, 2, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_negative_total_tokens() {
        assertThatThrownBy(() -> build("p", "m", "id", "v", "x", 1, 1, -1, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_null_estimated_cost() {
        assertThatThrownBy(() -> build("p", "m", "id", "v", "x", 1, 1, 2, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_negative_estimated_cost() {
        assertThatThrownBy(() -> build("p", "m", "id", "v", "x", 1, 1, 2, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_null_insights() {
        assertThatThrownBy(() -> new PortfolioAnalysisResult(
                        DiversificationLevel.LOW, "x", null, List.of(), "p", "m", "id", "v", 1, 1, 2,
                        BigDecimal.ZERO))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_null_risks() {
        assertThatThrownBy(() -> new PortfolioAnalysisResult(
                        DiversificationLevel.LOW, "x", List.of(), null, "p", "m", "id", "v", 1, 1, 2,
                        BigDecimal.ZERO))
                .isInstanceOf(NullPointerException.class);
    }
}
