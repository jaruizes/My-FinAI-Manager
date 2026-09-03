package com.myfinaimanager.core.portfolio.business;

import com.myfinaimanager.core.portfolio.domain.model.InvestorId;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.model.Position;
import com.myfinaimanager.core.portfolio.domain.ports.DefaultInvestorProvider;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Creates a portfolio (FD001, ADR-003 business layer). Depends only on {@code domain.model} and
 * {@code domain.ports}; component-scanned (its dependencies — the persistence adapter, the
 * default-investor provider, and the {@link Clock} bean — are supplied by {@code infrastructure}).
 *
 * <p>Flow: honour the idempotency key → resolve the default investor → build and validate the
 * aggregate ({@link Portfolio#create}) → persist atomically → record {@code PortfolioCreated} and
 * {@code PositionAdded} as structured log entries (FR-032 — no messaging).
 */
@Service
public final class CreatePortfolioService implements CreatePortfolioUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreatePortfolioService.class);

    private final PortfolioRepository repository;
    private final DefaultInvestorProvider defaultInvestorProvider;
    private final Clock clock;

    public CreatePortfolioService(PortfolioRepository repository,
                                  DefaultInvestorProvider defaultInvestorProvider,
                                  Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.defaultInvestorProvider = Objects.requireNonNull(defaultInvestorProvider);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public CreatePortfolioResult create(CreatePortfolioCommand command) {
        Objects.requireNonNull(command, "command");

        Optional<Portfolio> alreadyCreated = repository.findByIdempotencyKey(command.idempotencyKey());
        if (alreadyCreated.isPresent()) {
            return new CreatePortfolioResult(alreadyCreated.get(), true);
        }

        InvestorId investorId = defaultInvestorProvider.get();
        Portfolio candidate = Portfolio.create(
                investorId, command.name(), command.positions(), clock); // may throw PortfolioValidationException

        Portfolio saved = repository.save(candidate, command.idempotencyKey());
        boolean replayed = !saved.id().equals(candidate.id());

        if (!replayed) {
            recordBusinessOutcomes(saved);
        }
        return new CreatePortfolioResult(saved, replayed);
    }

    private void recordBusinessOutcomes(Portfolio portfolio) {
        log.atInfo()
                .addKeyValue("event", "PortfolioCreated")
                .addKeyValue("portfolioId", portfolio.id().toString())
                .addKeyValue("investorId", portfolio.investorId().toString())
                .addKeyValue("positionCount", portfolio.positions().size())
                .log("Portfolio created");
        for (Position position : portfolio.positions()) {
            log.atInfo()
                    .addKeyValue("event", "PositionAdded")
                    .addKeyValue("portfolioId", portfolio.id().toString())
                    .addKeyValue("positionId", position.id().toString())
                    .addKeyValue("ticker", position.instrument().ticker().value())
                    .addKeyValue("market", position.instrument().market().value())
                    .log("Position added");
        }
    }
}
