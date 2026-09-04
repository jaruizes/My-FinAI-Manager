package com.myfinaimanager.core.portfolio.business;

import com.myfinaimanager.core.portfolio.domain.events.PortfolioCreatedEvent;
import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioValidationException;
import com.myfinaimanager.core.portfolio.domain.model.Currency;
import com.myfinaimanager.core.portfolio.domain.model.InvestorId;
import com.myfinaimanager.core.portfolio.domain.model.Market;
import com.myfinaimanager.core.portfolio.domain.model.NewPosition;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.model.Position;
import com.myfinaimanager.core.portfolio.domain.model.Ticker;
import com.myfinaimanager.core.portfolio.domain.model.ValidationCode;
import com.myfinaimanager.core.portfolio.domain.model.Violation;
import com.myfinaimanager.core.portfolio.domain.ports.DefaultInvestorProvider;
import com.myfinaimanager.core.portfolio.domain.ports.InstrumentCatalog;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Creates a portfolio (FD001, ADR-003 business layer). Depends only on {@code domain.model} and
 * {@code domain.ports}; component-scanned (its dependencies — the persistence adapter, the
 * default-investor provider, and the {@link Clock} bean — are supplied by {@code infrastructure}).
 *
 * <p>Flow: honour the idempotency key → resolve the default investor → build and validate the
 * aggregate ({@link Portfolio#create}) → validate each position against the Financial Instrument
 * catalog ({@link InstrumentCatalog}, FD002 FR-011) → persist atomically → record
 * {@code PortfolioCreated} and {@code PositionAdded} as structured log entries (FR-032 — no
 * messaging).
 *
 * <p>Structural violations (FD001) and catalog violations (FD002) are reported together in one
 * {@link PortfolioValidationException} / one {@code 400} (FD001 FR-024).
 */
@Service
public final class CreatePortfolioService implements CreatePortfolioUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreatePortfolioService.class);

    private final PortfolioRepository repository;
    private final DefaultInvestorProvider defaultInvestorProvider;
    private final InstrumentCatalog instrumentCatalog;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public CreatePortfolioService(PortfolioRepository repository,
                                  DefaultInvestorProvider defaultInvestorProvider,
                                  InstrumentCatalog instrumentCatalog,
                                  Clock clock,
                                  ApplicationEventPublisher events) {
        this.repository = Objects.requireNonNull(repository);
        this.defaultInvestorProvider = Objects.requireNonNull(defaultInvestorProvider);
        this.instrumentCatalog = Objects.requireNonNull(instrumentCatalog);
        this.clock = Objects.requireNonNull(clock);
        this.events = Objects.requireNonNull(events);
    }

    @Override
    public CreatePortfolioResult create(CreatePortfolioCommand command) {
        Objects.requireNonNull(command, "command");

        Optional<Portfolio> alreadyCreated = repository.findByIdempotencyKey(command.idempotencyKey());
        if (alreadyCreated.isPresent()) {
            return new CreatePortfolioResult(alreadyCreated.get(), true);
        }

        InvestorId investorId = defaultInvestorProvider.get();

        List<Violation> structural = new ArrayList<>();
        Portfolio candidate = null;
        try {
            candidate = Portfolio.create(investorId, command.name(), command.positions(), clock);
        } catch (PortfolioValidationException e) {
            structural.addAll(e.violations());
        }

        List<Violation> catalog = validateAgainstCatalog(command.positions());
        if (!structural.isEmpty() || !catalog.isEmpty()) {
            List<Violation> all = new ArrayList<>(structural);
            all.addAll(catalog);
            throw new PortfolioValidationException(all);
        }

        Portfolio saved = repository.save(candidate, command.idempotencyKey());
        boolean replayed = !saved.id().equals(candidate.id());

        if (!replayed) {
            recordBusinessOutcomes(saved);
            // FD004 FR-001/FR-004: the save above has already committed (its own transaction), so a
            // synchronous listener runs post-commit, on this request thread, to value the portfolio.
            // A valuation failure is contained by the listener and never reaches this caller (FR-002).
            events.publishEvent(new PortfolioCreatedEvent(saved.id()));
        }
        return new CreatePortfolioResult(saved, replayed);
    }

    /**
     * FD002 FR-011 — every position whose {@code ticker}, {@code market} and {@code currency} are
     * individually well-formed (a malformed one is already owned by the FD001
     * {@code REQUIRED} / {@code CURRENCY_FORMAT} check) must correspond to one active catalogued
     * listing. A catalog/infrastructure failure propagates (it is not a validation {@code false}).
     */
    private List<Violation> validateAgainstCatalog(List<NewPosition> positions) {
        List<Violation> violations = new ArrayList<>();
        List<NewPosition> list = positions == null ? List.of() : positions;
        for (int i = 0; i < list.size(); i++) {
            NewPosition p = list.get(i);
            if (isBlank(p.ticker()) || isBlank(p.market()) || !Currency.hasValidShape(p.currency())) {
                continue;
            }
            boolean selectable = instrumentCatalog.isSelectable(
                    new Ticker(p.ticker()), new Market(p.market()), new Currency(p.currency()));
            if (!selectable) {
                violations.add(Violation.of("positions[" + i + "]", ValidationCode.INSTRUMENT_NOT_IN_CATALOG,
                        p.ticker().strip().toUpperCase(java.util.Locale.ROOT) + " on "
                                + p.market().strip().toUpperCase(java.util.Locale.ROOT) + " in "
                                + p.currency() + " is not a selectable instrument."));
            }
        }
        return violations;
    }

    private static boolean isBlank(String s) {
        return s == null || s.strip().isEmpty();
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
