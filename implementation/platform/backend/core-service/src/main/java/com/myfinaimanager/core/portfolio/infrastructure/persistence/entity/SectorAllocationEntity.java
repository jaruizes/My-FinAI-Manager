package com.myfinaimanager.core.portfolio.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA mapping of one sector's allocation onto the {@code sector_allocation} table (Flyway
 * {@code V4}). Infrastructure only.
 */
@Entity
@Table(name = "sector_allocation")
public class SectorAllocationEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_valuation_id", nullable = false)
    private PortfolioValuationEntity portfolioValuation;

    @Column(nullable = false)
    private String sector;

    @Column(name = "sector_value_eur", nullable = false)
    private BigDecimal sectorValueEur;

    @Column(name = "sector_weight", nullable = false)
    private BigDecimal sectorWeight;

    protected SectorAllocationEntity() {
        // for JPA
    }

    public SectorAllocationEntity(UUID id, String sector, BigDecimal sectorValueEur,
                                  BigDecimal sectorWeight) {
        this.id = id;
        this.sector = sector;
        this.sectorValueEur = sectorValueEur;
        this.sectorWeight = sectorWeight;
    }

    void setPortfolioValuation(PortfolioValuationEntity portfolioValuation) {
        this.portfolioValuation = portfolioValuation;
    }

    public UUID getId() {
        return id;
    }

    public PortfolioValuationEntity getPortfolioValuation() {
        return portfolioValuation;
    }

    public String getSector() {
        return sector;
    }

    public BigDecimal getSectorValueEur() {
        return sectorValueEur;
    }

    public BigDecimal getSectorWeight() {
        return sectorWeight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SectorAllocationEntity other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
