package com.myfinaimanager.core.portfolioanalysis.infrastructure.persistence.entity;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** JPA mapping of one {@code PortfolioAnalysis.Insight} onto {@code portfolio_analysis_insight}. */
@Entity
@Table(name = "portfolio_analysis_insight")
public class PortfolioAnalysisInsightEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_analysis_id", nullable = false)
    private PortfolioAnalysisEntity portfolioAnalysis;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected PortfolioAnalysisInsightEntity() {
        // for JPA
    }

    public PortfolioAnalysisInsightEntity(UUID id, String type, String message, int displayOrder) {
        this.id = id;
        this.type = type;
        this.message = message;
        this.displayOrder = displayOrder;
    }

    void setPortfolioAnalysis(PortfolioAnalysisEntity portfolioAnalysis) {
        this.portfolioAnalysis = portfolioAnalysis;
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PortfolioAnalysisInsightEntity other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
