package com.myfinaimanager.core.portfolioanalysis.infrastructure.portfolio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.myfinaimanager.core.portfolio.business.PortfolioQueryUseCase;
import com.myfinaimanager.core.portfolio.business.PortfolioValuationQueryUseCase;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuation;
import com.myfinaimanager.core.portfolio.domain.model.PositionValuation;
import com.myfinaimanager.core.portfolio.domain.model.SectorAllocation;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioContextSnapshot;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioContextSnapshot.PositionSnapshot;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioContextSnapshot.SectorSnapshot;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioValuationStatus;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioContextGateway;

/**
 * The <strong>sole</strong> class touching {@code com.myfinaimanager.core.portfolio.*}
 * (ArchUnit-enforced — AR-062; contract {@code portfolio-analysis-ports.md} C1, P1). Calls
 * {@link PortfolioQueryUseCase#view} then {@link PortfolioValuationQueryUseCase#findLatest} and
 * translates both into this module's own {@link PortfolioContextSnapshot} vocabulary — no FD004
 * type crosses this boundary (P2). A pure read; never writes (P4).
 */
@Component
public class PortfolioContextGatewayAdapter implements PortfolioContextGateway {

    private final PortfolioQueryUseCase portfolios;
    private final PortfolioValuationQueryUseCase valuations;

    public PortfolioContextGatewayAdapter(PortfolioQueryUseCase portfolios,
                                          PortfolioValuationQueryUseCase valuations) {
        this.portfolios = portfolios;
        this.valuations = valuations;
    }

    @Override
    public PortfolioContextSnapshot fetch(UUID portfolioId) {
        PortfolioId id = PortfolioId.of(portfolioId);
        Portfolio portfolio;
        try {
            portfolio = portfolios.view(id);
        } catch (com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException e) {
            // Translated here — AR-062: portfolio's exception type never reaches beyond this
            // adapter. The caller sees portfolioanalysis's own PortfolioNotFoundException instead,
            // mapped to the same 404 shape FD003/FD004 already use (contract C1, P3).
            throw new com.myfinaimanager.core.portfolioanalysis.domain.exceptions.PortfolioNotFoundException(
                    portfolioId);
        }
        Optional<PortfolioValuation> valuation = valuations.findLatest(id);

        if (valuation.isEmpty()) {
            return new PortfolioContextSnapshot(
                    portfolio.name().value(), PortfolioValuationStatus.ABSENT, Optional.empty(),
                    Optional.empty(), List.of(), List.of(), Optional.empty());
        }

        PortfolioValuation v = valuation.get();
        return new PortfolioContextSnapshot(
                portfolio.name().value(),
                PortfolioValuationStatus.valueOf(v.status().name()),
                v.totalValueEUR(),
                v.totalValueUSD(),
                v.positions().stream().map(this::toPositionSnapshot).toList(),
                v.sectors().stream().map(this::toSectorSnapshot).toList(),
                Optional.of(v.calculatedAt()));
    }

    private PositionSnapshot toPositionSnapshot(PositionValuation p) {
        return new PositionSnapshot(
                p.ticker(), p.portfolioWeight(), p.valueInEUR(), Optional.of(p.sector()), p.nativeCurrency());
    }

    private SectorSnapshot toSectorSnapshot(SectorAllocation s) {
        return new SectorSnapshot(s.sector(), s.sectorWeight());
    }
}
