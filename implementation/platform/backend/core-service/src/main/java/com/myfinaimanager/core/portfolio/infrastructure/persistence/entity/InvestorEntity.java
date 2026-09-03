package com.myfinaimanager.core.portfolio.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA mapping of the {@code investor} table. FD001 has no Investor aggregate — this entity only
 * exists so the persistence adapter can read the single seeded default investor (ADR-002).
 */
@Entity
@Table(name = "investor")
public class InvestorEntity {

    @Id
    private UUID id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "preferred_currency")
    private String preferredCurrency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InvestorEntity() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPreferredCurrency() {
        return preferredCurrency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvestorEntity other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
