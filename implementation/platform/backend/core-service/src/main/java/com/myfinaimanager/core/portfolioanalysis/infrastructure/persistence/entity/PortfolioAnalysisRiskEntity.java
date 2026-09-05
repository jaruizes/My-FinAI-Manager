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

/** JPA mapping of one {@code PortfolioAnalysis.Risk} onto {@code portfolio_analysis_risk}. */
@Entity
@Table(name = "portfolio_analysis_risk")
public class PortfolioAnalysisRiskEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_analysis_id", nullable = false)
    private PortfolioAnalysisEntity portfolioAnalysis;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected PortfolioAnalysisRiskEntity() {
        // for JPA
    }

    public PortfolioAnalysisRiskEntity(UUID id, String type, String severity, String title,
                                       String explanation, int displayOrder) {
        this.id = id;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.explanation = explanation;
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

    public String getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getExplanation() {
        return explanation;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PortfolioAnalysisRiskEntity other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
