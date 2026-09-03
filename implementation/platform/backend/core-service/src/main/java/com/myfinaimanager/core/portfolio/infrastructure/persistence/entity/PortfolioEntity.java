package com.myfinaimanager.core.portfolio.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA mapping of the {@code Portfolio} aggregate root onto the existing {@code portfolio} table
 * (Flyway {@code V2__portfolio.sql} owns the schema — Hibernate never alters it). Infrastructure
 * only: the domain {@code Portfolio} carries no persistence annotations (ADR-003, constitution VI).
 *
 * <p>{@code investorId} is a plain column, not a {@code @ManyToOne} — FD001 has no Investor
 * aggregate to navigate to (research.md D3). {@code status} is stored as {@code String} and the
 * mapper converts to/from the domain enum.
 */
@Entity
@Table(name = "portfolio")
public class PortfolioEntity {

    @Id
    private UUID id;

    @Column(name = "investor_id", nullable = false)
    private UUID investorId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PositionEntity> positions = new ArrayList<>();

    protected PortfolioEntity() {
        // for JPA
    }

    public PortfolioEntity(UUID id, UUID investorId, String name, String status,
                           String idempotencyKey, Instant createdAt) {
        this.id = id;
        this.investorId = investorId;
        this.name = name;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    /** Add a position and set both sides of the association. */
    public void addPosition(PositionEntity position) {
        position.setPortfolio(this);
        this.positions.add(position);
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvestorId() {
        return investorId;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<PositionEntity> getPositions() {
        return positions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PortfolioEntity other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
