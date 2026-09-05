package com.myfinaimanager.core.portfolioanalysis.infrastructure.persistence.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.myfinaimanager.core.portfolioanalysis.domain.model.AnalysisId;
import com.myfinaimanager.core.portfolioanalysis.domain.model.AnalysisStatus;
import com.myfinaimanager.core.portfolioanalysis.domain.model.CreationTrigger;
import com.myfinaimanager.core.portfolioanalysis.domain.model.DiversificationLevel;
import com.myfinaimanager.core.portfolioanalysis.domain.model.FailureReason;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.domain.model.RiskSeverity;
import com.myfinaimanager.core.portfolioanalysis.domain.model.RiskType;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.persistence.entity.PortfolioAnalysisEntity;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.persistence.entity.PortfolioAnalysisInsightEntity;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.persistence.entity.PortfolioAnalysisRiskEntity;

/**
 * Explicit domain &lt;-&gt; JPA-entity mapping for {@link PortfolioAnalysis}. The domain type stays
 * persistence-agnostic (ADR-003, constitution VI); this is the only place that knows both sides. A
 * domain {@link Optional#empty()} maps to a {@code null} column and back — never a sentinel value.
 */
@Component
public class PortfolioAnalysisPersistenceMapper {

    /** Domain analysis → a fresh entity graph (its own id — insert or merge-update by that id). */
    public PortfolioAnalysisEntity toEntity(PortfolioAnalysis a) {
        PortfolioAnalysisEntity entity = new PortfolioAnalysisEntity(
                a.id().value(),
                a.portfolioId(),
                a.status().name(),
                a.requestedAt(),
                a.startedAt().orElse(null),
                a.completedAt().orElse(null),
                a.summary().orElse(null),
                a.overallDiversification().map(Enum::name).orElse(null),
                a.provider().orElse(null),
                a.model().orElse(null),
                a.promptId().orElse(null),
                a.promptVersion().orElse(null),
                a.inputTokens().orElse(null),
                a.outputTokens().orElse(null),
                a.totalTokens().orElse(null),
                a.estimatedCost().orElse(null),
                a.failureReasonCode().map(Enum::name).orElse(null),
                a.createdByTrigger().name());

        for (PortfolioAnalysis.Insight insight : a.insights()) {
            entity.addInsight(new PortfolioAnalysisInsightEntity(
                    UUID.randomUUID(), insight.type(), insight.message(), insight.order()));
        }
        for (PortfolioAnalysis.Risk risk : a.risks()) {
            entity.addRisk(new PortfolioAnalysisRiskEntity(
                    UUID.randomUUID(), risk.type().name(), risk.severity().name(), risk.title(),
                    risk.explanation(), risk.order()));
        }
        return entity;
    }

    /** Entity graph loaded from PostgreSQL → the domain analysis. */
    public PortfolioAnalysis toDomain(PortfolioAnalysisEntity e) {
        List<PortfolioAnalysis.Insight> insights = new ArrayList<>();
        for (PortfolioAnalysisInsightEntity i : e.getInsights()) {
            insights.add(new PortfolioAnalysis.Insight(i.getType(), i.getMessage(), i.getDisplayOrder()));
        }
        List<PortfolioAnalysis.Risk> risks = new ArrayList<>();
        for (PortfolioAnalysisRiskEntity r : e.getRisks()) {
            risks.add(new PortfolioAnalysis.Risk(
                    RiskType.valueOf(r.getType()), RiskSeverity.valueOf(r.getSeverity()), r.getTitle(),
                    r.getExplanation(), r.getDisplayOrder()));
        }
        return new PortfolioAnalysis(
                AnalysisId.of(e.getId()),
                e.getPortfolioId(),
                AnalysisStatus.valueOf(e.getStatus()),
                e.getRequestedAt(),
                Optional.ofNullable(e.getStartedAt()),
                Optional.ofNullable(e.getCompletedAt()),
                Optional.ofNullable(e.getSummary()),
                Optional.ofNullable(e.getOverallDiversification()).map(DiversificationLevel::valueOf),
                insights,
                risks,
                Optional.ofNullable(e.getProvider()),
                Optional.ofNullable(e.getModel()),
                Optional.ofNullable(e.getPromptId()),
                Optional.ofNullable(e.getPromptVersion()),
                Optional.ofNullable(e.getInputTokens()),
                Optional.ofNullable(e.getOutputTokens()),
                Optional.ofNullable(e.getTotalTokens()),
                Optional.ofNullable(e.getEstimatedCost()),
                Optional.ofNullable(e.getFailureReasonCode()).map(FailureReason::valueOf),
                CreationTrigger.valueOf(e.getCreatedByTrigger()));
    }
}
