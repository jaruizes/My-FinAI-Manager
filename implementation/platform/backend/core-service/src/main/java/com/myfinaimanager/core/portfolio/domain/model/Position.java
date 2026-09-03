package com.myfinaimanager.core.portfolio.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * The investor's current aggregated holding of one financial instrument within a portfolio
 * (glossary "Position"; BR-010, FR-021). FD001 stores only the current aggregate — no transactions
 * and no purchase lots.
 *
 * <p>Invariants (guaranteed by construction; the user-facing multi-error validation is in
 * {@link Portfolio#create}):
 * <ul>
 *   <li>quantity &gt; 0 (BR-005);</li>
 *   <li>currency is present and explicit (BR-006, FR-017);</li>
 *   <li>if an average purchase price is present, its currency equals this position's currency
 *       (BR-007, FR-020) and its amount is &gt; 0 (spec A3);</li>
 *   <li>a missing optional value is {@link Optional#empty()} — never {@code 0}, never inferred
 *       (BR-008, BR-009, FR-019).</li>
 * </ul>
 */
public final class Position {

    private final PositionId id;
    private final InstrumentRef instrument;
    private final Quantity quantity;
    private final Currency currency;
    private final LocalDate initialPurchaseDate;      // nullable
    private final Money averagePurchasePrice;         // nullable

    private Position(PositionId id, InstrumentRef instrument, Quantity quantity, Currency currency,
                     LocalDate initialPurchaseDate, Money averagePurchasePrice) {
        this.id = Objects.requireNonNull(id);
        this.instrument = Objects.requireNonNull(instrument);
        this.quantity = Objects.requireNonNull(quantity);
        this.currency = Objects.requireNonNull(currency);
        if (averagePurchasePrice != null && !averagePurchasePrice.currency().equals(currency)) {
            throw new IllegalArgumentException(
                    "average purchase price currency must equal the position currency");
        }
        this.initialPurchaseDate = initialPurchaseDate;
        this.averagePurchasePrice = averagePurchasePrice;
    }

    /** Create a brand-new position (assigns a fresh id). Used by {@link Portfolio#create}. */
    static Position create(InstrumentRef instrument, Quantity quantity, Currency currency,
                           Optional<LocalDate> initialPurchaseDate, Optional<Money> averagePurchasePrice) {
        return new Position(PositionId.newId(), instrument, quantity, currency,
                initialPurchaseDate.orElse(null), averagePurchasePrice.orElse(null));
    }

    /** Rebuild a position loaded from persistence (keeps its id). */
    public static Position reconstitute(PositionId id, InstrumentRef instrument, Quantity quantity,
                                        Currency currency, Optional<LocalDate> initialPurchaseDate,
                                        Optional<Money> averagePurchasePrice) {
        return new Position(id, instrument, quantity, currency,
                initialPurchaseDate.orElse(null), averagePurchasePrice.orElse(null));
    }

    public PositionId id() {
        return id;
    }

    public InstrumentRef instrument() {
        return instrument;
    }

    public Quantity quantity() {
        return quantity;
    }

    public Currency currency() {
        return currency;
    }

    public Optional<LocalDate> initialPurchaseDate() {
        return Optional.ofNullable(initialPurchaseDate);
    }

    public Optional<Money> averagePurchasePrice() {
        return Optional.ofNullable(averagePurchasePrice);
    }
}
