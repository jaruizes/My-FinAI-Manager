package com.myfinaimanager.core.portfolioanalysis.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The Portfolio + valuation facts {@code PortfolioAnalysisContextBuilder} renders into a prompt
 * (data-model.md §2). Produced by {@code PortfolioContextGateway.fetch(PortfolioId)} — the ACL
 * boundary translating FD004's {@code Portfolio}/{@code PortfolioValuation}/{@code
 * PositionValuation}/{@code SectorAllocation} into this module's own vocabulary. No FD004 type
 * appears here (contract {@code portfolio-analysis-ports.md} P2).
 *
 * @param portfolioName    the Portfolio's name, for a readable prompt
 * @param valuationStatus  this module's own valuation-state vocabulary
 * @param totalValueEur    present only when a EUR total could be produced
 * @param totalValueUsd    present only when a USD total could be produced
 * @param positions        the valuation's positions, valued or not
 * @param sectors          the valuation's sector allocation (empty when no EUR total exists)
 * @param valuedAt         when the valuation was calculated, when one exists
 */
public record PortfolioContextSnapshot(
        String portfolioName,
        PortfolioValuationStatus valuationStatus,
        Optional<BigDecimal> totalValueEur,
        Optional<BigDecimal> totalValueUsd,
        List<PositionSnapshot> positions,
        List<SectorSnapshot> sectors,
        Optional<Instant> valuedAt) {

    public PortfolioContextSnapshot {
        Objects.requireNonNull(portfolioName, "portfolioName");
        Objects.requireNonNull(valuationStatus, "valuationStatus");
        totalValueEur = orEmpty(totalValueEur);
        totalValueUsd = orEmpty(totalValueUsd);
        positions = positions == null ? List.of() : List.copyOf(positions);
        sectors = sectors == null ? List.of() : List.copyOf(sectors);
        valuedAt = orEmpty(valuedAt);
    }

    private static <T> Optional<T> orEmpty(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    /**
     * @param ticker   the instrument ticker
     * @param weight   the position's fraction of the Portfolio's EUR total, when valued
     * @param valueEur the position's EUR value, when valued
     * @param sector   the provider sector classification, or {@code "Unclassified"}, when known
     * @param currency the position's native currency
     */
    public record PositionSnapshot(
            String ticker,
            Optional<BigDecimal> weight,
            Optional<BigDecimal> valueEur,
            Optional<String> sector,
            String currency) {

        public PositionSnapshot {
            Objects.requireNonNull(ticker, "ticker");
            weight = weight == null ? Optional.empty() : weight;
            valueEur = valueEur == null ? Optional.empty() : valueEur;
            sector = sector == null ? Optional.empty() : sector;
            Objects.requireNonNull(currency, "currency");
        }
    }

    /**
     * @param sector the sector name
     * @param weight the sector's fraction of the Portfolio's EUR total
     */
    public record SectorSnapshot(String sector, BigDecimal weight) {

        public SectorSnapshot {
            Objects.requireNonNull(sector, "sector");
            Objects.requireNonNull(weight, "weight");
        }
    }
}
