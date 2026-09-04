package com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper;

import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuation;
import com.myfinaimanager.core.portfolio.domain.model.PositionValuation;
import com.myfinaimanager.core.portfolio.domain.model.SectorAllocation;
import com.myfinaimanager.core.portfolio.domain.model.ValuationStatus;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto.PortfolioValuationResponse;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto.PortfolioValuationResponse.PositionValuationResponse;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto.PortfolioValuationResponse.SectorAllocationResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps the domain {@link PortfolioValuation} to the REST {@link PortfolioValuationResponse}.
 * Decimals become plain strings, full precision; a domain {@code Optional.empty()} becomes
 * {@code null} — never {@code "0"} (FR-019). This formatting is the OpenAPI contract; the contract
 * test guards it.
 */
@Component
public class PortfolioValuationResponseMapper {

    public PortfolioValuationResponse toResponse(PortfolioValuation v) {
        List<PositionValuationResponse> positions = v.positions().stream()
                .map(PortfolioValuationResponseMapper::toPosition)
                .toList();
        List<SectorAllocationResponse> sectors = v.sectors().stream()
                .map(PortfolioValuationResponseMapper::toSector)
                .toList();
        return new PortfolioValuationResponse(
                v.portfolioId().toString(),
                v.status().name(),
                v.calculatedAt().toString(),
                plain(v.totalValueEUR().orElse(null)),
                plain(v.totalValueUSD().orElse(null)),
                instant(v.marketDataAsOf().orElse(null)),
                instant(v.fxDataAsOf().orElse(null)),
                positions,
                sectors);
    }

    /** The explicit {@code PENDING} body for a Portfolio with no valuation snapshot yet (FR-025). */
    public PortfolioValuationResponse pending(PortfolioId portfolioId) {
        return new PortfolioValuationResponse(
                portfolioId.toString(),
                ValuationStatus.PENDING.name(),
                null, null, null, null, null,
                List.of(), List.of());
    }

    private static PositionValuationResponse toPosition(PositionValuation p) {
        return new PositionValuationResponse(
                p.ticker(),
                p.market(),
                p.quantity().toPlainString(),
                p.nativeCurrency(),
                p.valued(),
                plain(p.marketPrice().orElse(null)),
                plain(p.nativeMarketValue().orElse(null)),
                plain(p.valueInEUR().orElse(null)),
                plain(p.valueInUSD().orElse(null)),
                plain(p.portfolioWeight().orElse(null)),
                p.sector(),
                instant(p.priceObservedAt().orElse(null)));
    }

    private static SectorAllocationResponse toSector(SectorAllocation s) {
        return new SectorAllocationResponse(
                s.sector(),
                s.sectorValueEUR().toPlainString(),
                s.sectorWeight().toPlainString());
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String instant(Instant value) {
        return value == null ? null : value.toString();
    }
}
