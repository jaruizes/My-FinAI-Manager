package com.myfinaimanager.core.financialinstrument.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of one reference-data import run — returned by the import business operation and
 * logged (structured) by the startup runner (EN004 §24). Not persisted, never exposed by the API.
 *
 * @param source     the source identifier for this run (e.g. {@code YAHOO_CSV})
 * @param startedAt / {@code finishedAt}  run boundaries
 * @param counters   the seven-counter tally
 * @param rejections one entry per rejected row, for actionable diagnostics
 */
public record ImportReport(
        String source,
        Instant startedAt,
        Instant finishedAt,
        ImportCounters counters,
        List<Rejection> rejections) {

    public ImportReport {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(counters, "counters");
        rejections = rejections == null ? List.of() : List.copyOf(rejections);
    }
}
