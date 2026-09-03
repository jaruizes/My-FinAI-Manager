package com.myfinaimanager.core.financialinstrument.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A selectable Financial Instrument listing in the local catalog (EN004 §6; VC-003, VC-005, VC-009).
 * A single economic instrument may have several listings (distinct {@code ticker + market} rows).
 * Provider-neutral and framework-free: {@code providerSymbol} (the raw Yahoo symbol) is retained
 * only as source metadata and is never exposed through the public API.
 *
 * <p>Identity: {@link InstrumentIdentity} ({@code ticker + market}). {@link #id} is derived
 * deterministically from that identity.
 */
public final class FinancialInstrumentListing {

    private final ListingId id;
    private final String name;
    private final InstrumentIdentity identity;
    private final SupportedCurrency currency;
    private final Isin isin;                       // nullable
    private final String externalReference;        // nullable
    private final InstrumentType instrumentType;   // nullable (descriptive, never a search filter)
    private final String providerSymbol;           // nullable (source metadata)
    private final boolean active;
    private final Provenance provenance;

    private FinancialInstrumentListing(ListingId id, String name, InstrumentIdentity identity,
                                       SupportedCurrency currency, Isin isin, String externalReference,
                                       InstrumentType instrumentType, String providerSymbol,
                                       boolean active, Provenance provenance) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = requireText(name, "instrument name");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.isin = isin;
        this.externalReference = blankToNull(externalReference);
        this.instrumentType = instrumentType;
        this.providerSymbol = blankToNull(providerSymbol);
        this.active = active;
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }

    /** Build + validate a listing from raw (already mapping-normalized) source strings. */
    public static FinancialInstrumentListing fromRaw(NewListing raw, Provenance provenance) {
        Objects.requireNonNull(raw, "raw listing");
        Ticker ticker = new Ticker(requireText(raw.ticker(), "ticker"));
        Mic mic = new Mic(requireText(raw.mic(), "mic"));
        InstrumentIdentity identity = new InstrumentIdentity(ticker, mic);
        SupportedCurrency currency = SupportedCurrency.parse(requireText(raw.currency(), "currency"));
        Isin isin = isBlank(raw.isin()) ? null : new Isin(raw.isin());
        InstrumentType type = isBlank(raw.instrumentType()) ? null : InstrumentType.fromSource(raw.instrumentType());
        boolean active = parseActive(raw.active());
        return new FinancialInstrumentListing(
                ListingId.deterministic(ticker, mic),
                raw.name().strip(),
                identity,
                currency,
                isin,
                normalise(raw.externalReference()),
                type,
                normalise(raw.providerSymbol()),
                active,
                provenance);
    }

    /** Rebuild a listing loaded from persistence. */
    public static FinancialInstrumentListing reconstitute(ListingId id, String name, InstrumentIdentity identity,
                                                          SupportedCurrency currency, Isin isin,
                                                          String externalReference, InstrumentType instrumentType,
                                                          String providerSymbol, boolean active,
                                                          Provenance provenance) {
        return new FinancialInstrumentListing(id, name, identity, currency, isin, externalReference,
                instrumentType, providerSymbol, active, provenance);
    }

    public ListingId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public InstrumentIdentity identity() {
        return identity;
    }

    public Ticker ticker() {
        return identity.ticker();
    }

    public Mic market() {
        return identity.market();
    }

    public SupportedCurrency currency() {
        return currency;
    }

    public Optional<Isin> isin() {
        return Optional.ofNullable(isin);
    }

    public Optional<String> externalReference() {
        return Optional.ofNullable(externalReference);
    }

    public Optional<InstrumentType> instrumentType() {
        return Optional.ofNullable(instrumentType);
    }

    public Optional<String> providerSymbol() {
        return Optional.ofNullable(providerSymbol);
    }

    public boolean active() {
        return active;
    }

    public Provenance provenance() {
        return provenance;
    }

    private static boolean parseActive(String raw) {
        if (isBlank(raw)) {
            return true;
        }
        String s = raw.strip().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "true", "t", "yes", "y", "1", "active" -> true;
            case "false", "f", "no", "n", "0", "inactive" -> false;
            default -> throw new IllegalArgumentException("unrecognised active flag: " + raw);
        };
    }

    private static String requireText(String v, String field) {
        if (isBlank(v)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return v.strip();
    }

    private static String normalise(String v) {
        return isBlank(v) ? null : v.strip();
    }

    private static String blankToNull(String v) {
        return isBlank(v) ? null : v;
    }

    private static boolean isBlank(String v) {
        return v == null || v.strip().isEmpty();
    }
}
