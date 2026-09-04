package com.myfinaimanager.core.portfolio.business;

import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException;
import com.myfinaimanager.core.portfolio.domain.model.FxContext;
import com.myfinaimanager.core.portfolio.domain.model.FxConversion;
import com.myfinaimanager.core.portfolio.domain.model.InvestorId;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuation;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuationCalculator;
import com.myfinaimanager.core.portfolio.domain.model.Position;
import com.myfinaimanager.core.portfolio.domain.model.PositionInput;
import com.myfinaimanager.core.portfolio.domain.model.PositionPricing;
import com.myfinaimanager.core.portfolio.domain.ports.DefaultInvestorProvider;
import com.myfinaimanager.core.portfolio.domain.ports.MarketDataGateway;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioRepository;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioValuationRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * FD004 valuation orchestration (ADR-003 business layer). Depends only on {@code domain.model} /
 * {@code domain.ports}; the market-data lookups go through {@link MarketDataGateway} (the ACL port —
 * no {@code marketdata} or Finnhub type is visible here — FR-026, FR-035).
 *
 * <p>Flow: scope to the current investor → load the Portfolio (FD001/FD003 data, read-only) →
 * gather latest price + sector per Position and only the FX directions the Portfolio needs
 * (research D8) → run the deterministic {@link PortfolioValuationCalculator} → replace the latest
 * snapshot. This service performs <strong>no</strong> write to {@code portfolio} / {@code position}
 * (SC-008).
 */
@Service
public class PortfolioValuationService implements ValuePortfolioUseCase, PortfolioValuationQueryUseCase {

    private static final String EUR = "EUR";
    private static final String USD = "USD";

    private final PortfolioRepository portfolios;
    private final PortfolioValuationRepository valuations;
    private final MarketDataGateway marketData;
    private final DefaultInvestorProvider defaultInvestorProvider;
    private final Clock clock;
    private final PortfolioValuationCalculator calculator = new PortfolioValuationCalculator();

    public PortfolioValuationService(PortfolioRepository portfolios,
                                     PortfolioValuationRepository valuations,
                                     MarketDataGateway marketData,
                                     DefaultInvestorProvider defaultInvestorProvider,
                                     Clock clock) {
        this.portfolios = Objects.requireNonNull(portfolios);
        this.valuations = Objects.requireNonNull(valuations);
        this.marketData = Objects.requireNonNull(marketData);
        this.defaultInvestorProvider = Objects.requireNonNull(defaultInvestorProvider);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void value(PortfolioId portfolioId) {
        Objects.requireNonNull(portfolioId, "portfolioId");
        Portfolio portfolio = requireCurrentInvestorsPortfolio(portfolioId);

        List<PositionInput> inputs = new ArrayList<>();
        boolean anyUsd = false;
        boolean anyEur = false;
        for (Position position : portfolio.positions()) {
            String ticker = position.instrument().ticker().value();
            String market = position.instrument().market().value();
            String currency = position.currency().code();
            anyUsd |= USD.equals(currency);
            anyEur |= EUR.equals(currency);

            Optional<PositionPricing> price = marketData.latestPrice(ticker, market, currency);
            Optional<String> sector = marketData.sector(ticker, market, currency);
            inputs.add(new PositionInput(ticker, market, position.quantity().value(), currency,
                    price.map(PositionPricing::price), price.map(PositionPricing::observedAt), sector));
        }

        FxContext fx = fxContext(anyUsd, anyEur);
        PortfolioValuation result = calculator.calculate(portfolioId, inputs, fx, clock.instant());
        valuations.upsertLatest(result);
    }

    @Override
    public Optional<PortfolioValuation> findLatest(PortfolioId portfolioId) {
        Objects.requireNonNull(portfolioId, "portfolioId");
        requireCurrentInvestorsPortfolio(portfolioId);
        return valuations.findByPortfolioId(portfolioId);
    }

    private Portfolio requireCurrentInvestorsPortfolio(PortfolioId portfolioId) {
        InvestorId investor = defaultInvestorProvider.get();
        return portfolios.findByIdForInvestor(portfolioId, investor)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
    }

    private FxContext fxContext(boolean anyUsd, boolean anyEur) {
        Optional<FxConversion> usdToEur = anyUsd ? marketData.fxRate(USD, EUR) : Optional.empty();
        Optional<FxConversion> eurToUsd = anyEur ? marketData.fxRate(EUR, USD) : Optional.empty();
        return new FxContext(
                usdToEur.map(FxConversion::rate), usdToEur.map(FxConversion::observedAt),
                eurToUsd.map(FxConversion::rate), eurToUsd.map(FxConversion::observedAt));
    }
}
