package com.myfinaimanager.core.financialinstrument.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA mapping of {@code FinancialInstrumentListing} onto the existing {@code financial_instrument}
 * table. Infrastructure only. {@code marketMic} is a plain column + DB FK to {@code market(mic)} —
 * <strong>not</strong> a {@code @ManyToOne} navigation (there is no aggregate to traverse from a
 * listing — research.md D3/D4, mirrors EN003's {@code investor_id}).
 */
@Entity
@Table(name = "financial_instrument")
public class FinancialInstrumentEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String ticker;

    @Column(name = "market_mic", nullable = false)
    private String marketMic;

    @Column(nullable = false)
    private String currency;

    @Column
    private String isin;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "instrument_type")
    private String instrumentType;

    @Column(name = "provider_symbol")
    private String providerSymbol;

    @Column(nullable = false)
    private boolean active;

    @Column
    private String source;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "last_imported_at")
    private Instant lastImportedAt;

    protected FinancialInstrumentEntity() {
        // for JPA
    }

    public FinancialInstrumentEntity(UUID id, String name, String ticker, String marketMic, String currency,
                                     String isin, String externalReference, String instrumentType,
                                     String providerSymbol, boolean active, String source,
                                     String sourceReference, Instant lastImportedAt) {
        this.id = id;
        this.name = name;
        this.ticker = ticker;
        this.marketMic = marketMic;
        this.currency = currency;
        this.isin = isin;
        this.externalReference = externalReference;
        this.instrumentType = instrumentType;
        this.providerSymbol = providerSymbol;
        this.active = active;
        this.source = source;
        this.sourceReference = sourceReference;
        this.lastImportedAt = lastImportedAt;
    }

    /** Overwrite the mutable columns from a newer import (identity {@code (ticker, market_mic)} unchanged). */
    public void refreshFrom(FinancialInstrumentEntity other) {
        this.name = other.name;
        this.currency = other.currency;
        this.isin = other.isin;
        this.externalReference = other.externalReference;
        this.instrumentType = other.instrumentType;
        this.providerSymbol = other.providerSymbol;
        this.active = other.active;
        this.source = other.source;
        this.sourceReference = other.sourceReference;
        this.lastImportedAt = other.lastImportedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTicker() {
        return ticker;
    }

    public String getMarketMic() {
        return marketMic;
    }

    public String getCurrency() {
        return currency;
    }

    public String getIsin() {
        return isin;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getInstrumentType() {
        return instrumentType;
    }

    public String getProviderSymbol() {
        return providerSymbol;
    }

    public boolean isActive() {
        return active;
    }

    public String getSource() {
        return source;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public Instant getLastImportedAt() {
        return lastImportedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FinancialInstrumentEntity other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
