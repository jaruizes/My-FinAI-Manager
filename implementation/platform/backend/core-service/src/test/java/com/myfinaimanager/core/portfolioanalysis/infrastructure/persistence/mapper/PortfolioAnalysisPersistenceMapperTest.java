package com.myfinaimanager.core.portfolioanalysis.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.myfinaimanager.core.portfolioanalysis.domain.model.CreationTrigger;
import com.myfinaimanager.core.portfolioanalysis.domain.model.DiversificationLevel;
import com.myfinaimanager.core.portfolioanalysis.domain.model.FailureReason;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysisResult;
import com.myfinaimanager.core.portfolioanalysis.domain.model.RiskSeverity;
import com.myfinaimanager.core.portfolioanalysis.domain.model.RiskType;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.persistence.entity.PortfolioAnalysisEntity;

/** Domain &lt;-&gt; entity round-trip for every optional-field combination (empty vs. populated). */
class PortfolioAnalysisPersistenceMapperTest {

    private final PortfolioAnalysisPersistenceMapper mapper = new PortfolioAnalysisPersistenceMapper();
    private static final UUID PORTFOLIO_ID = UUID.randomUUID();
    private static final Instant REQUESTED_AT = Instant.parse("2026-09-05T10:00:00Z");

    @Test
    void a_pending_analysis_round_trips_with_every_optional_field_absent() {
        PortfolioAnalysis pending = PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.MANUAL);

        PortfolioAnalysisEntity entity = mapper.toEntity(pending);

        assertThat(entity.getId()).isEqualTo(pending.id().value());
        assertThat(entity.getPortfolioId()).isEqualTo(PORTFOLIO_ID);
        assertThat(entity.getStatus()).isEqualTo("PENDING");
        assertThat(entity.getStartedAt()).isNull();
        assertThat(entity.getCompletedAt()).isNull();
        assertThat(entity.getSummary()).isNull();
        assertThat(entity.getOverallDiversification()).isNull();
        assertThat(entity.getProvider()).isNull();
        assertThat(entity.getModel()).isNull();
        assertThat(entity.getPromptId()).isNull();
        assertThat(entity.getPromptVersion()).isNull();
        assertThat(entity.getInputTokens()).isNull();
        assertThat(entity.getOutputTokens()).isNull();
        assertThat(entity.getTotalTokens()).isNull();
        assertThat(entity.getEstimatedCost()).isNull();
        assertThat(entity.getFailureReasonCode()).isNull();
        assertThat(entity.getInsights()).isEmpty();
        assertThat(entity.getRisks()).isEmpty();

        PortfolioAnalysis roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped).isEqualTo(pending);
        assertThat(roundTripped.startedAt()).isEmpty();
        assertThat(roundTripped.completedAt()).isEmpty();
        assertThat(roundTripped.summary()).isEmpty();
        assertThat(roundTripped.overallDiversification()).isEmpty();
        assertThat(roundTripped.provider()).isEmpty();
        assertThat(roundTripped.failureReasonCode()).isEmpty();
    }

    @Test
    void a_completed_analysis_round_trips_with_every_optional_field_populated() {
        PortfolioAnalysisResult result = new PortfolioAnalysisResult(
                DiversificationLevel.HIGH, "Well diversified.",
                List.of(new PortfolioAnalysis.Insight("CONCENTRATION", "msg", 0)),
                List.of(new PortfolioAnalysis.Risk(
                        RiskType.CURRENCY_CONCENTRATION, RiskSeverity.LOW, "title", "explanation", 0)),
                "openai", "gpt-4o-mini", "portfolio-analysis", "v1", 10, 5, 15, new BigDecimal("0.001"));
        PortfolioAnalysis completed = PortfolioAnalysis
                .requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC)
                .withRunning(REQUESTED_AT.plusSeconds(1))
                .withCompleted(result, REQUESTED_AT.plusSeconds(2));

        PortfolioAnalysisEntity entity = mapper.toEntity(completed);

        assertThat(entity.getStartedAt()).isNotNull();
        assertThat(entity.getCompletedAt()).isNotNull();
        assertThat(entity.getSummary()).isEqualTo("Well diversified.");
        assertThat(entity.getOverallDiversification()).isEqualTo("HIGH");
        assertThat(entity.getProvider()).isEqualTo("openai");
        assertThat(entity.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(entity.getPromptId()).isEqualTo("portfolio-analysis");
        assertThat(entity.getPromptVersion()).isEqualTo("v1");
        assertThat(entity.getInputTokens()).isEqualTo(10);
        assertThat(entity.getOutputTokens()).isEqualTo(5);
        assertThat(entity.getTotalTokens()).isEqualTo(15);
        assertThat(entity.getEstimatedCost()).isEqualByComparingTo("0.001");
        assertThat(entity.getFailureReasonCode()).isNull();
        assertThat(entity.getInsights()).hasSize(1);
        assertThat(entity.getRisks()).hasSize(1);

        PortfolioAnalysis roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped).isEqualTo(completed);
        assertThat(roundTripped.overallDiversification()).contains(DiversificationLevel.HIGH);
        assertThat(roundTripped.insights()).hasSize(1);
        assertThat(roundTripped.risks()).hasSize(1);
        assertThat(roundTripped.risks().get(0).type()).isEqualTo(RiskType.CURRENCY_CONCENTRATION);
        assertThat(roundTripped.risks().get(0).severity()).isEqualTo(RiskSeverity.LOW);
    }

    @Test
    void a_failed_analysis_round_trips_the_failure_reason_with_no_content_fields() {
        PortfolioAnalysis failed = PortfolioAnalysis
                .requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC)
                .withRunning(REQUESTED_AT.plusSeconds(1))
                .withFailed(FailureReason.INSUFFICIENT_DATA, REQUESTED_AT.plusSeconds(2));

        PortfolioAnalysisEntity entity = mapper.toEntity(failed);

        assertThat(entity.getFailureReasonCode()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(entity.getOverallDiversification()).isNull();
        assertThat(entity.getSummary()).isNull();

        PortfolioAnalysis roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped).isEqualTo(failed);
        assertThat(roundTripped.failureReasonCode()).contains(FailureReason.INSUFFICIENT_DATA);
    }
}
