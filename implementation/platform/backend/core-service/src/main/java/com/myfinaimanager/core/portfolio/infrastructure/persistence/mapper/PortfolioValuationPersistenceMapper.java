package com.myfinaimanager.core.portfolio.infrastructure.persistence.mapper;

import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuation;
import com.myfinaimanager.core.portfolio.domain.model.PositionValuation;
import com.myfinaimanager.core.portfolio.domain.model.SectorAllocation;
import com.myfinaimanager.core.portfolio.domain.model.ValuationStatus;
import com.myfinaimanager.core.portfolio.infrastructure.persistence.entity.PortfolioValuationEntity;
import com.myfinaimanager.core.portfolio.infrastructure.persistence.entity.PositionValuationEntity;
import com.myfinaimanager.core.portfolio.infrastructure.persistence.entity.SectorAllocationEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Explicit domain &lt;-&gt; JPA-entity mapping for the FD004 valuation snapshot. The domain types
 * stay persistence-agnostic (ADR-003, constitution VI); this is the only place that knows both
 * sides. A domain {@link Optional#empty()} maps to a {@code null} column and back — never {@code 0}.
 */
@Component
public class PortfolioValuationPersistenceMapper {

    /** Domain snapshot → a new entity graph (fresh row ids) ready to persist. */
    public PortfolioValuationEntity toEntity(PortfolioValuation v) {
        PortfolioValuationEntity entity = new PortfolioValuationEntity(
                UUID.randomUUID(),
                v.portfolioId().value(),
                v.status().name(),
                v.calculatedAt(),
                v.totalValueEUR().orElse(null),
                v.totalValueUSD().orElse(null),
                v.marketDataAsOf().orElse(null),
                v.fxDataAsOf().orElse(null));

        for (PositionValuation p : v.positions()) {
            entity.addPosition(new PositionValuationEntity(
                    UUID.randomUUID(),
                    p.ticker(),
                    p.market(),
                    p.quantity(),
                    p.valued(),
                    p.nativeCurrency(),
                    p.marketPrice().orElse(null),
                    p.nativeMarketValue().orElse(null),
                    p.valueInEUR().orElse(null),
                    p.valueInUSD().orElse(null),
                    p.portfolioWeight().orElse(null),
                    p.sector(),
                    p.priceObservedAt().orElse(null)));
        }
        for (SectorAllocation s : v.sectors()) {
            entity.addSector(new SectorAllocationEntity(
                    UUID.randomUUID(), s.sector(), s.sectorValueEUR(), s.sectorWeight()));
        }
        return entity;
    }

    /** Entity graph loaded from PostgreSQL → the domain snapshot. */
    public PortfolioValuation toDomain(PortfolioValuationEntity e) {
        List<PositionValuation> positions = new ArrayList<>();
        for (PositionValuationEntity p : e.getPositions()) {
            positions.add(new PositionValuation(
                    p.getTicker(),
                    p.getMarket(),
                    p.getQuantity(),
                    p.getNativeCurrency().trim(),
                    p.isValued(),
                    opt(p.getMarketPrice()),
                    opt(p.getNativeMarketValue()),
                    opt(p.getValueEur()),
                    opt(p.getValueUsd()),
                    opt(p.getPortfolioWeight()),
                    p.getSector(),
                    Optional.ofNullable(p.getPriceObservedAt())));
        }
        List<SectorAllocation> sectors = new ArrayList<>();
        for (SectorAllocationEntity s : e.getSectors()) {
            sectors.add(new SectorAllocation(s.getSector(), s.getSectorValueEur(), s.getSectorWeight()));
        }
        return new PortfolioValuation(
                PortfolioId.of(e.getPortfolioId()),
                ValuationStatus.valueOf(e.getStatus()),
                e.getCalculatedAt(),
                opt(e.getTotalValueEur()),
                opt(e.getTotalValueUsd()),
                Optional.ofNullable(e.getMarketDataAsOf()),
                Optional.ofNullable(e.getFxDataAsOf()),
                positions,
                sectors);
    }

    private static Optional<BigDecimal> opt(BigDecimal value) {
        return Optional.ofNullable(value);
    }
}
