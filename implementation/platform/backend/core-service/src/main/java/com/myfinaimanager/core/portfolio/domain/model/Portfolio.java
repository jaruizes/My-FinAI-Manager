package com.myfinaimanager.core.portfolio.domain.model;

import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioValidationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Aggregate root for FD001. A named collection of {@link Position}s owned by one investor.
 *
 * <p>{@link #create} is the single entry point for creating a portfolio: it parses and validates
 * raw investor input against every FD001 business rule in one pass and, on any failure, throws a
 * {@link PortfolioValidationException} carrying <strong>all</strong> {@link Violation}s (FR-024) —
 * nothing is persisted. A portfolio is immutable once created (FD001 §3: no post-creation
 * mutation).
 */
public final class Portfolio {

    private final PortfolioId id;
    private final InvestorId investorId;
    private final PortfolioName name;
    private final PortfolioStatus status;
    private final List<Position> positions;
    private final Instant createdAt;

    private Portfolio(PortfolioId id, InvestorId investorId, PortfolioName name,
                      PortfolioStatus status, List<Position> positions, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.investorId = Objects.requireNonNull(investorId);
        this.name = Objects.requireNonNull(name);
        this.status = Objects.requireNonNull(status);
        this.positions = List.copyOf(positions);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    /**
     * Validate raw input and build the portfolio aggregate.
     *
     * @throws PortfolioValidationException if the portfolio or any position breaks a business rule
     *                                      (contains every violation found)
     */
    public static Portfolio create(InvestorId investorId, String rawName,
                                   List<NewPosition> rawPositions, Clock clock) {
        Objects.requireNonNull(investorId, "investorId");
        Objects.requireNonNull(clock, "clock");

        List<Violation> violations = new ArrayList<>();
        PortfolioName name = validateName(rawName, violations);

        List<NewPosition> positionInputs = rawPositions == null ? List.of() : rawPositions;
        if (positionInputs.isEmpty()) {
            violations.add(Violation.of("positions", ValidationCode.AT_LEAST_ONE,
                    "A portfolio must have at least one position."));
        }

        LocalDate today = LocalDate.now(clock);
        List<Position> parsed = new ArrayList<>();
        Set<InstrumentRef> seen = new HashSet<>();

        for (int i = 0; i < positionInputs.size(); i++) {
            Optional<Position> position = parsePosition(positionInputs.get(i), i, today, violations);
            if (position.isEmpty()) {
                continue;
            }
            Position p = position.get();
            if (!seen.add(p.instrument())) {
                violations.add(Violation.of("positions[" + i + "]", ValidationCode.DUPLICATE_INSTRUMENT,
                        "A position for " + p.instrument().ticker().value() + " on "
                                + p.instrument().market().value()
                                + " already exists in this portfolio."));
            } else {
                parsed.add(p);
            }
        }

        if (!violations.isEmpty()) {
            throw new PortfolioValidationException(violations);
        }
        return new Portfolio(PortfolioId.newId(), investorId, name, PortfolioStatus.ACTIVE,
                parsed, clock.instant());
    }

    /** Rebuild a portfolio loaded from persistence (keeps its id and timestamps). */
    public static Portfolio reconstitute(PortfolioId id, InvestorId investorId, PortfolioName name,
                                         PortfolioStatus status, List<Position> positions,
                                         Instant createdAt) {
        return new Portfolio(id, investorId, name, status, positions, createdAt);
    }

    private static PortfolioName validateName(String rawName, List<Violation> violations) {
        if (rawName == null || rawName.strip().isEmpty()) {
            violations.add(Violation.of("name", ValidationCode.REQUIRED, "Portfolio name is required."));
            return null;
        }
        if (rawName.strip().length() > PortfolioName.MAX_LENGTH) {
            violations.add(Violation.of("name", ValidationCode.NAME_TOO_LONG,
                    "Portfolio name must be at most " + PortfolioName.MAX_LENGTH + " characters."));
            return null;
        }
        return new PortfolioName(rawName);
    }

    private static Optional<Position> parsePosition(NewPosition in, int i, LocalDate today,
                                                    List<Violation> violations) {
        String base = "positions[" + i + "]";
        boolean ok = true;

        Ticker ticker = null;
        if (isBlank(in.ticker())) {
            violations.add(Violation.of(base + ".ticker", ValidationCode.REQUIRED, "Ticker is required."));
            ok = false;
        } else {
            ticker = new Ticker(in.ticker());
        }

        Market market = null;
        if (isBlank(in.market())) {
            violations.add(Violation.of(base + ".market", ValidationCode.REQUIRED, "Market is required."));
            ok = false;
        } else {
            market = new Market(in.market());
        }

        Currency currency = null;
        if (isBlank(in.currency())) {
            violations.add(Violation.of(base + ".currency", ValidationCode.REQUIRED, "Currency is required."));
            ok = false;
        } else if (!Currency.hasValidShape(in.currency())) {
            violations.add(Violation.of(base + ".currency", ValidationCode.CURRENCY_FORMAT,
                    "Currency must be a 3-letter ISO 4217 code (e.g. EUR)."));
            ok = false;
        } else {
            currency = new Currency(in.currency());
        }

        Quantity quantity = null;
        if (isBlank(in.quantity())) {
            violations.add(Violation.of(base + ".quantity", ValidationCode.REQUIRED, "Quantity is required."));
            ok = false;
        } else {
            BigDecimal q = tryDecimal(in.quantity());
            if (q == null) {
                violations.add(Violation.of(base + ".quantity", ValidationCode.INVALID_NUMBER,
                        "Quantity must be a valid number."));
                ok = false;
            } else if (q.signum() <= 0) {
                violations.add(Violation.of(base + ".quantity", ValidationCode.NOT_POSITIVE,
                        "A valid quantity greater than zero is required."));
                ok = false;
            } else {
                quantity = new Quantity(q);
            }
        }

        Optional<LocalDate> purchaseDate = Optional.empty();
        if (!isBlank(in.initialPurchaseDate())) {
            LocalDate d = tryDate(in.initialPurchaseDate());
            if (d == null) {
                violations.add(Violation.of(base + ".initialPurchaseDate", ValidationCode.INVALID_DATE,
                        "Initial purchase date must be a valid date (YYYY-MM-DD)."));
                ok = false;
            } else if (d.isAfter(today)) {
                violations.add(Violation.of(base + ".initialPurchaseDate", ValidationCode.FUTURE_DATE,
                        "Initial purchase date cannot be in the future."));
                ok = false;
            } else {
                purchaseDate = Optional.of(d);
            }
        }

        Optional<Money> price = Optional.empty();
        if (!isBlank(in.averagePurchasePrice())) {
            BigDecimal p = tryDecimal(in.averagePurchasePrice());
            if (p == null) {
                violations.add(Violation.of(base + ".averagePurchasePrice", ValidationCode.INVALID_NUMBER,
                        "Average purchase price must be a valid number."));
                ok = false;
            } else if (p.signum() <= 0) {
                violations.add(Violation.of(base + ".averagePurchasePrice", ValidationCode.NOT_POSITIVE,
                        "Average purchase price must be greater than zero."));
                ok = false;
            } else if (currency != null) {
                price = Optional.of(new Money(p, currency));
            }
        }

        if (!ok || ticker == null || market == null || currency == null || quantity == null) {
            return Optional.empty();
        }
        return Optional.of(Position.create(new InstrumentRef(ticker, market), quantity, currency,
                purchaseDate, price));
    }

    private static boolean isBlank(String s) {
        return s == null || s.strip().isEmpty();
    }

    private static BigDecimal tryDecimal(String s) {
        try {
            return new BigDecimal(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate tryDate(String s) {
        try {
            return LocalDate.parse(s.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public PortfolioId id() {
        return id;
    }

    public InvestorId investorId() {
        return investorId;
    }

    public PortfolioName name() {
        return name;
    }

    public PortfolioStatus status() {
        return status;
    }

    public List<Position> positions() {
        return positions;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
