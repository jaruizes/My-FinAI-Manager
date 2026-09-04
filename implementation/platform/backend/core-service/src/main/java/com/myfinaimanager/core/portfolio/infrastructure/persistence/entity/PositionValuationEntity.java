package com.myfinaimanager.core.portfolio.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA mapping of one Position's valuation onto the {@code position_valuation} table (Flyway
 * {@code V4}). Infrastructure only. Nullable monetary columns are {@code null} — never {@code 0} —
 * for an unvalued Position ({@code valued = false}); the {@code V4} CHECK constraint enforces this.
 */
@Entity
@Table(name = "position_valuation")
public class PositionValuationEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_valuation_id", nullable = false)
    private PortfolioValuationEntity portfolioValuation;

    @Column(nullable = false)
    private String ticker;

    @Column(nullable = false)
    private String market;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private boolean valued;

    @Column(name = "native_currency", nullable = false)
    private String nativeCurrency;

    @Column(name = "market_price")
    private BigDecimal marketPrice;

    @Column(name = "native_market_value")
    private BigDecimal nativeMarketValue;

    @Column(name = "value_eur")
    private BigDecimal valueEur;

    @Column(name = "value_usd")
    private BigDecimal valueUsd;

    @Column(name = "portfolio_weight")
    private BigDecimal portfolioWeight;

    @Column(nullable = false)
    private String sector;

    @Column(name = "price_observed_at")
    private Instant priceObservedAt;

    protected PositionValuationEntity() {
        // for JPA
    }

    public PositionValuationEntity(UUID id, String ticker, String market, BigDecimal quantity,
                                   boolean valued, String nativeCurrency, BigDecimal marketPrice,
                                   BigDecimal nativeMarketValue, BigDecimal valueEur,
                                   BigDecimal valueUsd, BigDecimal portfolioWeight, String sector,
                                   Instant priceObservedAt) {
        this.id = id;
        this.ticker = ticker;
        this.market = market;
        this.quantity = quantity;
        this.valued = valued;
        this.nativeCurrency = nativeCurrency;
        this.marketPrice = marketPrice;
        this.nativeMarketValue = nativeMarketValue;
        this.valueEur = valueEur;
        this.valueUsd = valueUsd;
        this.portfolioWeight = portfolioWeight;
        this.sector = sector;
        this.priceObservedAt = priceObservedAt;
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

    public String getTicker() {
        return ticker;
    }

    public String getMarket() {
        return market;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public boolean isValued() {
        return valued;
    }

    public String getNativeCurrency() {
        return nativeCurrency;
    }

    public BigDecimal getMarketPrice() {
        return marketPrice;
    }

    public BigDecimal getNativeMarketValue() {
        return nativeMarketValue;
    }

    public BigDecimal getValueEur() {
        return valueEur;
    }

    public BigDecimal getValueUsd() {
        return valueUsd;
    }

    public BigDecimal getPortfolioWeight() {
        return portfolioWeight;
    }

    public String getSector() {
        return sector;
    }

    public Instant getPriceObservedAt() {
        return priceObservedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PositionValuationEntity other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
