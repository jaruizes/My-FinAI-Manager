package com.myfinaimanager.core.financialinstrument.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * A trading venue identified by an ISO 10383 {@link Mic} (EN004 §6, §8; VC-001, VC-002). Reference
 * data — provider-neutral, framework-free. Identity is the {@code mic}. A more specific listing/
 * segment MIC is preferred as canonical; the operating MIC (e.g. {@code BMEX} for {@code XMAD}) is
 * kept only as metadata.
 */
public final class Market {

    private final Mic mic;
    private final String name;
    private final String country;          // ISO 3166-1 alpha-2, nullable
    private final Mic operatingMic;        // nullable
    private final boolean active;
    private final Provenance provenance;

    private Market(Mic mic, String name, String country, Mic operatingMic, boolean active, Provenance provenance) {
        this.mic = Objects.requireNonNull(mic, "mic");
        this.name = requireText(name, "market name");
        this.country = blankToNull(country);
        this.operatingMic = operatingMic;
        this.active = active;
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }

    /** Build + validate a Market from raw source strings. */
    public static Market fromRaw(NewMarket raw, Provenance provenance) {
        Objects.requireNonNull(raw, "raw market");
        Mic mic = new Mic(requireText(raw.mic(), "market mic"));
        String country = blankToNull(raw.countryIso2());
        if (country != null && country.strip().length() != 2) {
            throw new IllegalArgumentException("country must be an ISO 3166-1 alpha-2 code: " + country);
        }
        Mic operatingMic = isBlank(raw.operatingMic()) ? null : new Mic(raw.operatingMic());
        boolean active = parseActive(raw.active());
        return new Market(mic, raw.name().strip(),
                country == null ? null : country.strip().toUpperCase(java.util.Locale.ROOT),
                operatingMic, active, provenance);
    }

    /** Rebuild a Market loaded from persistence (keeps its stored state). */
    public static Market reconstitute(Mic mic, String name, String country, Mic operatingMic,
                                      boolean active, Provenance provenance) {
        return new Market(mic, name, country, operatingMic, active, provenance);
    }

    public Mic mic() {
        return mic;
    }

    public String name() {
        return name;
    }

    public Optional<String> country() {
        return Optional.ofNullable(country);
    }

    public Optional<Mic> operatingMic() {
        return Optional.ofNullable(operatingMic);
    }

    public boolean active() {
        return active;
    }

    public Provenance provenance() {
        return provenance;
    }

    private static boolean parseActive(String raw) {
        if (isBlank(raw)) {
            return true; // reference rows default to active (EN004 §15)
        }
        String s = raw.strip().toLowerCase(java.util.Locale.ROOT);
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

    private static String blankToNull(String v) {
        return isBlank(v) ? null : v;
    }

    private static boolean isBlank(String v) {
        return v == null || v.strip().isEmpty();
    }
}
