package com.myfinaimanager.core.financialinstrument.domain.model;

/**
 * Why a source row did not enter the selectable catalog (EN004 §9A; decision doc §"Import
 * Validation"). Every reason maps to exactly one {@code ImportCounters} bucket (see
 * {@code contracts/catalog-ports.md} §4). No row is ever guessed — an unresolved row is rejected.
 */
public enum RejectionReason {

    /** The Yahoo {@code Exchange} has no entry in {@code yahoo-exchange-to-mic-mapping.csv}. */
    NO_MAPPING,

    /** The exchange mapping has {@code supported_for_fd002 = false}. */
    NOT_SUPPORTED_FOR_FD002,

    /** The resolved currency is not EUR or USD. */
    UNSUPPORTED_CURRENCY,

    /** The exchange rule declares a suffix but the symbol does not end with it. Never guessed. */
    SUFFIX_MISMATCH,

    /** A generic exchange (e.g. {@code ENX}) whose MIC/currency cannot be resolved from the suffix. */
    AMBIGUOUS_EXCHANGE,

    /** The canonical MIC has no corresponding row in the {@code market} table. */
    MIC_UNRESOLVED,

    /** The canonical ticker is empty after mapping-driven suffix removal. */
    EMPTY_TICKER,

    /** Two rows in the same run normalise to the same {@code (ticker, MIC)} with conflicting data. */
    IDENTITY_CONFLICT
}
