package com.myfinaimanager.core.financialinstrument.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA mapping of {@code Market} onto the existing {@code market} table (Flyway
 * {@code V3__financial_instrument.sql} owns the schema — Hibernate never alters it). Infrastructure
 * only: the domain {@code Market} carries no persistence annotations (ADR-003, constitution VI).
 */
@Entity
@Table(name = "market")
public class MarketEntity {

    @Id
    private String mic;

    @Column(nullable = false)
    private String name;

    @Column(name = "country_iso2")
    private String countryIso2;

    @Column(name = "operating_mic")
    private String operatingMic;

    @Column(nullable = false)
    private boolean active;

    @Column
    private String source;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "last_imported_at")
    private Instant lastImportedAt;

    protected MarketEntity() {
        // for JPA
    }

    public MarketEntity(String mic, String name, String countryIso2, String operatingMic, boolean active,
                        String source, String sourceReference, Instant lastImportedAt) {
        this.mic = mic;
        this.name = name;
        this.countryIso2 = countryIso2;
        this.operatingMic = operatingMic;
        this.active = active;
        this.source = source;
        this.sourceReference = sourceReference;
        this.lastImportedAt = lastImportedAt;
    }

    /** Overwrite the mutable columns from a newer import (identity {@code mic} is unchanged). */
    public void refreshFrom(MarketEntity other) {
        this.name = other.name;
        this.countryIso2 = other.countryIso2;
        this.operatingMic = other.operatingMic;
        this.active = other.active;
        this.source = other.source;
        this.sourceReference = other.sourceReference;
        this.lastImportedAt = other.lastImportedAt;
    }

    public String getMic() {
        return mic;
    }

    public String getName() {
        return name;
    }

    public String getCountryIso2() {
        return countryIso2;
    }

    public String getOperatingMic() {
        return operatingMic;
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
        if (!(o instanceof MarketEntity other)) {
            return false;
        }
        return mic != null && mic.equals(other.mic);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mic);
    }
}
