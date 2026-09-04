package com.myfinaimanager.core.portfolio.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA mapping of the latest FD004 valuation snapshot onto the {@code portfolio_valuation} table
 * (Flyway {@code V4} owns the schema). Infrastructure only — the domain {@code PortfolioValuation}
 * carries no persistence annotations (ADR-003, constitution VI).
 *
 * <p>{@code status} is stored as {@code String}; the mapper converts to/from the domain enum.
 * Monetary columns declare no {@code precision}/{@code scale} so the exact computed value
 * round-trips. Children cascade fully (one snapshot is written/replaced as a unit).
 */
@Entity
@Table(name = "portfolio_valuation")
public class PortfolioValuationEntity {

    @Id
    private UUID id;

    @Column(name = "portfolio_id", nullable = false, unique = true)
    private UUID portfolioId;

    @Column(nullable = false)
    private String status;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "total_value_eur")
    private BigDecimal totalValueEur;

    @Column(name = "total_value_usd")
    private BigDecimal totalValueUsd;

    @Column(name = "market_data_as_of")
    private Instant marketDataAsOf;

    @Column(name = "fx_data_as_of")
    private Instant fxDataAsOf;

    @OneToMany(mappedBy = "portfolioValuation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PositionValuationEntity> positions = new ArrayList<>();

    @OneToMany(mappedBy = "portfolioValuation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SectorAllocationEntity> sectors = new ArrayList<>();

    protected PortfolioValuationEntity() {
        // for JPA
    }

    public PortfolioValuationEntity(UUID id, UUID portfolioId, String status, Instant calculatedAt,
                                    BigDecimal totalValueEur, BigDecimal totalValueUsd,
                                    Instant marketDataAsOf, Instant fxDataAsOf) {
        this.id = id;
        this.portfolioId = portfolioId;
        this.status = status;
        this.calculatedAt = calculatedAt;
        this.totalValueEur = totalValueEur;
        this.totalValueUsd = totalValueUsd;
        this.marketDataAsOf = marketDataAsOf;
        this.fxDataAsOf = fxDataAsOf;
    }

    public void addPosition(PositionValuationEntity position) {
        position.setPortfolioValuation(this);
        this.positions.add(position);
    }

    public void addSector(SectorAllocationEntity sector) {
        sector.setPortfolioValuation(this);
        this.sectors.add(sector);
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

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public BigDecimal getTotalValueEur() {
        return totalValueEur;
    }

    public BigDecimal getTotalValueUsd() {
        return totalValueUsd;
    }

    public Instant getMarketDataAsOf() {
        return marketDataAsOf;
    }

    public Instant getFxDataAsOf() {
        return fxDataAsOf;
    }

    public List<PositionValuationEntity> getPositions() {
        return positions;
    }

    public List<SectorAllocationEntity> getSectors() {
        return sectors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PortfolioValuationEntity other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
