package com.myfinaimanager.core.portfolio.business;

import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.ports.DefaultInvestorProvider;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Reads portfolios for the current investor (FD003). The only business rules here are
 * <em>scope every read to the current investor</em> (resolved via {@link DefaultInvestorProvider} —
 * the same seam FD001 uses, so real multi-investor identity drops in later) and <em>a miss is a
 * {@link PortfolioNotFoundException}</em>. No sorting/shaping (the repository returns newest-first),
 * no idempotency, no clock, and — critically — never a write (FR-015, SC-005): this service never
 * calls {@code PortfolioRepository.save}.
 */
@Service
public class PortfolioQueryService implements PortfolioQueryUseCase {

    private final PortfolioRepository portfolios;
    private final DefaultInvestorProvider defaultInvestorProvider;

    public PortfolioQueryService(PortfolioRepository portfolios,
                                 DefaultInvestorProvider defaultInvestorProvider) {
        this.portfolios = portfolios;
        this.defaultInvestorProvider = defaultInvestorProvider;
    }

    @Override
    public List<Portfolio> list() {
        return portfolios.findAllByInvestor(defaultInvestorProvider.get());
    }

    @Override
    public Portfolio view(PortfolioId id) {
        return portfolios.findByIdForInvestor(id, defaultInvestorProvider.get())
                .orElseThrow(() -> new PortfolioNotFoundException(id));
    }
}
