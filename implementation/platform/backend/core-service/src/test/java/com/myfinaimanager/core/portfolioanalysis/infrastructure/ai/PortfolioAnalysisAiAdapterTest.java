package com.myfinaimanager.core.portfolioanalysis.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.myfinaimanager.core.ai.business.GenerateAiUseCase;
import com.myfinaimanager.core.ai.domain.exceptions.AiCostBudgetExceededException;
import com.myfinaimanager.core.ai.domain.exceptions.AiGuardrailRejectedException;
import com.myfinaimanager.core.ai.domain.exceptions.AiProviderNotConfiguredException;
import com.myfinaimanager.core.ai.domain.exceptions.AiProviderUnavailableException;
import com.myfinaimanager.core.ai.domain.exceptions.AiStructuredOutputInvalidException;
import com.myfinaimanager.core.ai.domain.exceptions.AiTimeoutException;
import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;
import com.myfinaimanager.core.ai.domain.model.AiUsage;
import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.AnalysisFailedException;
import com.myfinaimanager.core.portfolioanalysis.domain.model.DiversificationLevel;
import com.myfinaimanager.core.portfolioanalysis.domain.model.FailureReason;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysisContext;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysisResult;
import com.myfinaimanager.core.portfolioanalysis.domain.model.RiskSeverity;
import com.myfinaimanager.core.portfolioanalysis.domain.model.RiskType;

/**
 * Builds the right {@link AiRequest} (taskType {@code "portfolio-analysis"}); maps a conforming
 * response to {@link PortfolioAnalysisResult}; maps every {@code AiException} subtype to its
 * {@link FailureReason} (contract C2).
 */
class PortfolioAnalysisAiAdapterTest {

    private static final PortfolioAnalysisContext CONTEXT = new PortfolioAnalysisContext("Portfolio text.", true);

    private static Map<String, Object> conformingContent() {
        return Map.of(
                "overallDiversification", Map.of("level", "MODERATE", "explanation", "Reasonably diversified."),
                "keyInsights", List.of(Map.of("type", "CONCENTRATION", "message", "AAPL is 76%.")),
                "risks", List.of(Map.of(
                        "type", "POSITION_CONCENTRATION", "severity", "MEDIUM", "title", "Concentrated",
                        "explanation", "AAPL is a large share.")));
    }

    private static AiResponse response(Map<String, Object> content) {
        AiUsage usage = new AiUsage(100, 50, 150, "openai", "gpt-4o-mini", new BigDecimal("0.01"));
        return new AiResponse(
                "{...}", Optional.of(content), "openai", "gpt-4o-mini", usage, 42, Optional.of("stop"),
                "req-1", Instant.parse("2026-09-05T10:00:00Z"));
    }

    @Test
    void builds_a_request_with_the_portfolio_analysis_task_type_and_the_context_text() {
        GenerateAiUseCase generateAi = mock(GenerateAiUseCase.class);
        when(generateAi.generate(any())).thenReturn(response(conformingContent()));
        PortfolioAnalysisAiAdapter adapter = new PortfolioAnalysisAiAdapter(generateAi);

        adapter.analyze(CONTEXT, "corr-1");

        org.mockito.ArgumentCaptor<AiRequest> captor = org.mockito.ArgumentCaptor.forClass(AiRequest.class);
        verify(generateAi).generate(captor.capture());
        AiRequest sent = captor.getValue();
        assertThat(sent.taskType()).isEqualTo("portfolio-analysis");
        assertThat(sent.context()).isEqualTo("Portfolio text.");
        assertThat(sent.correlationId()).isEqualTo("corr-1");
        assertThat(sent.outputSchema()).isPresent();
    }

    @Test
    void maps_a_conforming_response_to_a_portfolio_analysis_result() {
        GenerateAiUseCase generateAi = mock(GenerateAiUseCase.class);
        when(generateAi.generate(any())).thenReturn(response(conformingContent()));
        PortfolioAnalysisAiAdapter adapter = new PortfolioAnalysisAiAdapter(generateAi);

        PortfolioAnalysisResult result = adapter.analyze(CONTEXT, "corr-1");

        assertThat(result.overallDiversification()).isEqualTo(DiversificationLevel.MODERATE);
        assertThat(result.explanation()).isEqualTo("Reasonably diversified.");
        assertThat(result.insights()).hasSize(1);
        assertThat(result.insights().get(0).type()).isEqualTo("CONCENTRATION");
        assertThat(result.risks()).hasSize(1);
        assertThat(result.risks().get(0).type()).isEqualTo(RiskType.POSITION_CONCENTRATION);
        assertThat(result.risks().get(0).severity()).isEqualTo(RiskSeverity.MEDIUM);
        assertThat(result.provider()).isEqualTo("openai");
        assertThat(result.model()).isEqualTo("gpt-4o-mini");
        assertThat(result.promptId()).isEqualTo("portfolio-analysis");
        assertThat(result.promptVersion()).isEqualTo("v1");
        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.outputTokens()).isEqualTo(50);
        assertThat(result.totalTokens()).isEqualTo(150);
        assertThat(result.estimatedCost()).isEqualByComparingTo("0.01");
    }

    @Test
    void a_provider_not_configured_failure_maps_to_not_configured() {
        assertMapsTo(new AiProviderNotConfiguredException("no key"), FailureReason.NOT_CONFIGURED);
    }

    @Test
    void a_provider_unavailable_failure_maps_to_provider_unavailable() {
        assertMapsTo(new AiProviderUnavailableException("down"), FailureReason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void a_timeout_failure_maps_to_timeout() {
        assertMapsTo(new AiTimeoutException("slow"), FailureReason.TIMEOUT);
    }

    @Test
    void a_guardrail_rejection_maps_to_guardrail_rejected() {
        assertMapsTo(new AiGuardrailRejectedException("output-guardrail", "blocked"), FailureReason.GUARDRAIL_REJECTED);
    }

    @Test
    void a_structured_output_invalid_failure_maps_to_invalid_output() {
        assertMapsTo(new AiStructuredOutputInvalidException("bad shape"), FailureReason.INVALID_OUTPUT);
    }

    @Test
    void a_cost_budget_exceeded_failure_maps_to_unknown() {
        assertMapsTo(new AiCostBudgetExceededException("too pricey"), FailureReason.UNKNOWN);
    }

    @Test
    void a_malformed_nested_field_in_an_otherwise_conforming_response_maps_to_invalid_output() {
        GenerateAiUseCase generateAi = mock(GenerateAiUseCase.class);
        Map<String, Object> malformed = Map.of(
                "overallDiversification", Map.of("level", "NOT_A_REAL_LEVEL", "explanation", "x"),
                "keyInsights", List.of(), "risks", List.of());
        when(generateAi.generate(any())).thenReturn(response(malformed));
        PortfolioAnalysisAiAdapter adapter = new PortfolioAnalysisAiAdapter(generateAi);

        assertThatThrownBy(() -> adapter.analyze(CONTEXT, "corr-1"))
                .isInstanceOf(AnalysisFailedException.class)
                .satisfies(e -> assertThat(((AnalysisFailedException) e).reason())
                        .isEqualTo(FailureReason.INVALID_OUTPUT));
    }

    private void assertMapsTo(RuntimeException thrown, FailureReason expected) {
        GenerateAiUseCase generateAi = mock(GenerateAiUseCase.class);
        when(generateAi.generate(any())).thenThrow(thrown);
        PortfolioAnalysisAiAdapter adapter = new PortfolioAnalysisAiAdapter(generateAi);

        assertThatThrownBy(() -> adapter.analyze(CONTEXT, "corr-1"))
                .isInstanceOf(AnalysisFailedException.class)
                .satisfies(e -> assertThat(((AnalysisFailedException) e).reason()).isEqualTo(expected));
    }
}
