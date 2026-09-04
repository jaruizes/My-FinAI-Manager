package com.myfinaimanager.core.portfolio.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One sector's share of a Portfolio valuation, in the canonical EUR basis (FD004 §11; FR-012).
 * Present only for a valuation that has a positive {@code totalValueEUR}. A valued Position with no
 * EUR value (a USD Position when USD&rarr;EUR FX was missing) is excluded here (research D4).
 *
 * @param sector          provider classification string, or the literal {@code "Unclassified"}
 * @param sectorValueEUR  Σ of {@code valueInEUR} for that sector's valued Positions; exact, &gt; 0
 * @param sectorWeight    {@code sectorValueEUR / totalValueEUR}, an exact fraction (scale 12)
 */
public record SectorAllocation(String sector, BigDecimal sectorValueEUR, BigDecimal sectorWeight) {

    public static final String UNCLASSIFIED = "Unclassified";

    public SectorAllocation {
        Objects.requireNonNull(sector, "sector");
        if (sector.isBlank()) {
            throw new IllegalArgumentException("sector must not be blank");
        }
        if (sectorValueEUR == null || sectorValueEUR.signum() <= 0) {
            throw new IllegalArgumentException("sectorValueEUR must be a positive amount");
        }
        if (sectorWeight == null || sectorWeight.signum() < 0) {
            throw new IllegalArgumentException("sectorWeight must not be negative");
        }
    }
}
