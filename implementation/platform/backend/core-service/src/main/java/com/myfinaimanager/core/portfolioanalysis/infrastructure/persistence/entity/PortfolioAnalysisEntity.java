package com.myfinaimanager.core.portfolioanalysis.infrastructure.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * JPA mapping of one {@code PortfolioAnalysis} onto the {@code portfolio_analysis} table (Flyway
 * {@code V5} owns the schema — data-model.md §1). Infrastructure only — the domain
 * {@code PortfolioAnalysis} carries no persistence annotations (ADR-003, constitution VI). No FK
 * into any FD004 table (deliberate — data-model.md §1).
 */
@Entity
@Table(name = "portfolio_analysis")
public class PortfolioAnalysisEntity {

    @Id
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(nullable = false)
    private String status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "overall_diversification")
    private String overallDiversification;

    @Column
    private String provider;

    @Column
    private String model;

    @Column(name = "prompt_id")
    private String promptId;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "estimated_cost")
    private BigDecimal estimatedCost;

    @Column(name = "failure_reason_code")
    private String failureReasonCode;

    @Column(name = "created_by_trigger", nullable = false)
    private String createdByTrigger;

    @OneToMany(mappedBy = "portfolioAnalysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PortfolioAnalysisInsightEntity> insights = new ArrayList<>();

    @OneToMany(mappedBy = "portfolioAnalysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PortfolioAnalysisRiskEntity> risks = new ArrayList<>();

    protected PortfolioAnalysisEntity() {
        // for JPA
    }

    public PortfolioAnalysisEntity(UUID id, UUID portfolioId, String status, Instant requestedAt,
                                   Instant startedAt, Instant completedAt, String summary,
                                   String overallDiversification, String provider, String model,
                                   String promptId, String promptVersion, Integer inputTokens,
                                   Integer outputTokens, Integer totalTokens, BigDecimal estimatedCost,
                                   String failureReasonCode, String createdByTrigger) {
        this.id = id;
        this.portfolioId = portfolioId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.summary = summary;
        this.overallDiversification = overallDiversification;
        this.provider = provider;
        this.model = model;
        this.promptId = promptId;
        this.promptVersion = promptVersion;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.estimatedCost = estimatedCost;
        this.failureReasonCode = failureReasonCode;
        this.createdByTrigger = createdByTrigger;
    }

    public void addInsight(PortfolioAnalysisInsightEntity insight) {
        insight.setPortfolioAnalysis(this);
        this.insights.add(insight);
    }

    public void addRisk(PortfolioAnalysisRiskEntity risk) {
        risk.setPortfolioAnalysis(this);
        this.risks.add(risk);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPortfolioId() {
        return portfolioId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getSummary() {
        return summary;
    }

    public String getOverallDiversification() {
        return overallDiversification;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getPromptId() {
        return promptId;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public String getFailureReasonCode() {
        return failureReasonCode;
    }

    public String getCreatedByTrigger() {
        return createdByTrigger;
    }

    public List<PortfolioAnalysisInsightEntity> getInsights() {
        return insights;
    }

    public List<PortfolioAnalysisRiskEntity> getRisks() {
        return risks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PortfolioAnalysisEntity other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
