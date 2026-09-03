package com.myfinaimanager.core.portfolio.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA mapping of a {@code Position} onto the existing {@code position} table. Infrastructure only.
 *
 * <p>{@code quantity} and {@code averagePurchasePrice} declare <strong>no</strong>
 * {@code precision}/{@code scale} so the investor's exact input scale round-trips (SC-007).
 * {@code averagePurchasePriceCurrency} is set by the mapper to the position currency when a price
 * is present (else {@code null}), keeping the {@code V2} price/currency CHECK constraints satisfied.
 */
@Entity
@Table(name = "position")
public class PositionEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private PortfolioEntity portfolio;

    @Column(nullable = false)
    private String ticker;

    @Column(nullable = false)
    private String market;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private String currency;

    @Column(name = "initial_purchase_date")
    private LocalDate initialPurchaseDate;

    @Column(name = "average_purchase_price")
    private BigDecimal averagePurchasePrice;

    @Column(name = "average_purchase_price_currency")
    private String averagePurchasePriceCurrency;

    protected PositionEntity() {
        // for JPA
    }

    public PositionEntity(UUID id, String ticker, String market, BigDecimal quantity, String currency,
                          LocalDate initialPurchaseDate, BigDecimal averagePurchasePrice,
                          String averagePurchasePriceCurrency) {
        this.id = id;
        this.ticker = ticker;
        this.market = market;
        this.quantity = quantity;
        this.currency = currency;
        this.initialPurchaseDate = initialPurchaseDate;
        this.averagePurchasePrice = averagePurchasePrice;
        this.averagePurchasePriceCurrency = averagePurchasePriceCurrency;
    }

    void setPortfolio(PortfolioEntity portfolio) {
        this.portfolio = portfolio;
    }

    public UUID getId() {
        return id;
    }

    public PortfolioEntity getPortfolio() {
        return portfolio;
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

    public String getCurrency() {
        return currency;
    }

    public LocalDate getInitialPurchaseDate() {
        return initialPurchaseDate;
    }

    public BigDecimal getAveragePurchasePrice() {
        return averagePurchasePrice;
    }

    public String getAveragePurchasePriceCurrency() {
        return averagePurchasePriceCurrency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PositionEntity other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
