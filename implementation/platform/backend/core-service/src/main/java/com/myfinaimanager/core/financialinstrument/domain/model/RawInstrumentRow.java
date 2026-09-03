package com.myfinaimanager.core.financialinstrument.domain.model;

/**
 * One un-normalized instrument row from a source file — the raw provider symbol as supplied, a
 * human-readable name, and the raw provider exchange code. The business importer runs the
 * mapping-driven normalizer over {@code rawSymbol + exchangeCode}; a source adapter only reads the
 * file (EN004 §18 — the business layer owns normalization/validation).
 */
public record RawInstrumentRow(String rawSymbol, String name, String exchangeCode) {
}
