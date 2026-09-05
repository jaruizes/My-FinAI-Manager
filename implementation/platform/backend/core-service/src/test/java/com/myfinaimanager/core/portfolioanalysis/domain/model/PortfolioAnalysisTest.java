package com.myfinaimanager.core.portfolioanalysis.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Every field/guard, each lifecycle transition (data-model.md §2, §5). */
class PortfolioAnalysisTest {

    private static final UUID PORTFOLIO_ID = UUID.randomUUID();
    private static final Instant REQUESTED_AT = Instant.parse("2026-09-05T10:00:00Z");

    private static PortfolioAnalysisResult result() {
        return new PortfolioAnalysisResult(
                DiversificationLevel.MODERATE, "Reasonably diversified.",
                List.of(new PortfolioAnalysis.Insight("CONCENTRATION", "AAPL is 76% of the portfolio.", 0)),
                List.of(new PortfolioAnalysis.Risk(
                        RiskType.POSITION_CONCENTRATION, RiskSeverity.MEDIUM, "Concentrated in AAPL",
                        "AAPL represents a large share.", 0)),
                "openai", "gpt-4o-mini", "portfolio-analysis", "v1", 100, 50, 150, new BigDecimal("0.01"));
    }

    // ---- requested() -------------------------------------------------------------------------

    @Test
    void requested_starts_pending_with_no_terminal_or_running_fields_set() {
        PortfolioAnalysis analysis = PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC);

        assertThat(analysis.id()).isNotNull();
        assertThat(analysis.portfolioId()).isEqualTo(PORTFOLIO_ID);
        assertThat(analysis.status()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(analysis.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(analysis.startedAt()).isEmpty();
        assertThat(analysis.completedAt()).isEmpty();
        assertThat(analysis.summary()).isEmpty();
        assertThat(analysis.overallDiversification()).isEmpty();
        assertThat(analysis.insights()).isEmpty();
        assertThat(analysis.risks()).isEmpty();
        assertThat(analysis.provider()).isEmpty();
        assertThat(analysis.failureReasonCode()).isEmpty();
        assertThat(analysis.createdByTrigger()).isEqualTo(CreationTrigger.AUTOMATIC);
    }

    @Test
    void two_requested_analyses_never_share_an_id() {
        PortfolioAnalysis a = PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC);
        PortfolioAnalysis b = PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.MANUAL);

        assertThat(a.id()).isNotEqualTo(b.id());
    }

    // ---- withRunning -------------------------------------------------------------------------

    @Test
    void withRunning_transitions_from_pending_and_returns_a_new_instance() {
        PortfolioAnalysis pending = PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC);
        Instant startedAt = REQUESTED_AT.plusSeconds(1);

        PortfolioAnalysis running = pending.withRunning(startedAt);

        assertThat(running).isNotSameAs(pending);
        assertThat(running.status()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(running.startedAt()).contains(startedAt);
        assertThat(pending.status()).isEqualTo(AnalysisStatus.PENDING); // original untouched
    }

    @Test
    void withRunning_rejects_a_non_pending_analysis() {
        PortfolioAnalysis running = PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC)
                .withRunning(REQUESTED_AT);

        assertThatThrownBy(() -> running.withRunning(REQUESTED_AT)).isInstanceOf(IllegalStateException.class);
    }

    // ---- withCompleted -----------------------------------------------------------------------

    @Test
    void withCompleted_transitions_from_running_and_populates_every_result_field() {
        PortfolioAnalysis running = PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.MANUAL)
                .withRunning(REQUESTED_AT);
        Instant completedAt = REQUESTED_AT.plusSeconds(5);

        PortfolioAnalysis completed = running.withCompleted(result(), completedAt);

        assertThat(completed.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(completed.completedAt()).contains(completedAt);
        assertThat(completed.summary()).contains("Reasonably diversified.");
        assertThat(completed.overallDiversification()).contains(DiversificationLevel.MODERATE);
        assertThat(completed.insights()).hasSize(1);
        assertThat(completed.risks()).hasSize(1);
        assertThat(completed.provider()).contains("openai");
        assertThat(completed.model()).contains("gpt-4o-mini");
        assertThat(completed.promptId()).contains("portfolio-analysis");
        assertThat(completed.promptVersion()).contains("v1");
        assertThat(completed.inputTokens()).contains(100);
        assertThat(completed.outputTokens()).contains(50);
        assertThat(completed.totalTokens()).contains(150);
        assertThat(completed.estimatedCost()).contains(new BigDecimal("0.01"));
        assertThat(completed.failureReasonCode()).isEmpty();
    }

    @Test
    void withCompleted_rejects_a_non_running_analysis() {
        PortfolioAnalysis pending = PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC);

        assertThatThrownBy(() -> pending.withCompleted(result(), REQUESTED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- withFailed --------------------------------------------------------------------------

    @Test
    void withFailed_transitions_from_running_and_carries_the_reason() {
        PortfolioAnalysis running = PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC)
                .withRunning(REQUESTED_AT);
        Instant completedAt = REQUESTED_AT.plusSeconds(2);

        PortfolioAnalysis failed = running.withFailed(FailureReason.PROVIDER_UNAVAILABLE, completedAt);

        assertThat(failed.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.completedAt()).contains(completedAt);
        assertThat(failed.failureReasonCode()).contains(FailureReason.PROVIDER_UNAVAILABLE);
        assertThat(failed.summary()).isEmpty();
        assertThat(failed.insights()).isEmpty();
    }

    @Test
    void withFailed_also_works_directly_from_pending_insufficient_data_path() {
        PortfolioAnalysis pending = PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC);

        PortfolioAnalysis failed = pending.withFailed(FailureReason.INSUFFICIENT_DATA, REQUESTED_AT);

        assertThat(failed.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.failureReasonCode()).contains(FailureReason.INSUFFICIENT_DATA);
    }

    @Test
    void withFailed_rejects_an_already_terminal_analysis() {
        PortfolioAnalysis failed = PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC)
                .withFailed(FailureReason.UNKNOWN, REQUESTED_AT);

        assertThatThrownBy(() -> failed.withFailed(FailureReason.TIMEOUT, REQUESTED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- Insight / Risk value object guards --------------------------------------------------

    @Test
    void insight_rejects_a_blank_type_or_message_or_a_negative_order() {
        assertThatThrownBy(() -> new PortfolioAnalysis.Insight("", "msg", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortfolioAnalysis.Insight("type", "", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortfolioAnalysis.Insight("type", "msg", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void risk_rejects_a_blank_title_or_explanation_or_a_negative_order() {
        assertThatThrownBy(() -> new PortfolioAnalysis.Risk(RiskType.OTHER, RiskSeverity.LOW, "", "expl", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortfolioAnalysis.Risk(RiskType.OTHER, RiskSeverity.LOW, "title", "", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortfolioAnalysis.Risk(RiskType.OTHER, RiskSeverity.LOW, "title", "expl", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
