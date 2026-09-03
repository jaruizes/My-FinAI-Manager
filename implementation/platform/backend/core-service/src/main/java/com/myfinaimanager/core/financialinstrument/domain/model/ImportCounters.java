package com.myfinaimanager.core.financialinstrument.domain.model;

/**
 * The tally of one reference-data import run — exactly the seven counters of the decision doc
 * §"Import Validation" (spec FR-032). A hard failure aborts the run as an exception and is
 * <strong>not</strong> a counter.
 *
 * <p>{@code processed} = source rows read; {@code imported} + {@code updated} = rows that entered
 * or refreshed the catalog; the {@code skipped*} / {@code quarantined*} counters account for every
 * rejected row (one bucket each — see {@code contracts/catalog-ports.md} §4).
 */
public record ImportCounters(
        int processed,
        int imported,
        int updated,
        int skippedUnsupportedCurrency,
        int skippedUnsupportedMarket,
        int quarantinedAmbiguous,
        int quarantinedInvalid) {

    public static ImportCounters zero() {
        return new ImportCounters(0, 0, 0, 0, 0, 0, 0);
    }

    public int rejected() {
        return skippedUnsupportedCurrency + skippedUnsupportedMarket + quarantinedAmbiguous + quarantinedInvalid;
    }
}
