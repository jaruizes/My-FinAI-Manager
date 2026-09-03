package com.myfinaimanager.core.financialinstrument.domain.ports;

import com.myfinaimanager.core.financialinstrument.domain.model.FinancialInstrumentListing;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port — runtime read access to the local Financial Instrument catalog (FD002).
 * Implemented by a persistence adapter; the business layer depends only on this interface.
 * See {@code contracts/catalog-ports.md} §1 for the invariants (C1–C7).
 */
public interface FinancialInstrumentCatalog {

    /**
     * Selectable listings matching {@code query} by exact ticker (case-insensitive) or by
     * name-contains (case-insensitive). Only listings that may be chosen for a new Position:
     * {@code active == true} AND {@code currency ∈ {EUR, USD}}. Exact-ticker matches first, then
     * name matches; each block ordered by ticker ascending. Never contacts an external provider.
     *
     * @param query non-blank, already-trimmed search term (the business service validates this)
     * @return possibly empty, never {@code null}
     */
    List<FinancialInstrumentListing> search(String query);

    /**
     * The one <strong>selectable</strong> listing with exactly this {@code ticker}
     * (case-insensitive) on exactly this {@code marketMic}, or empty — the exact-match counterpart
     * of {@link #search(String)}. "Selectable" = {@code active == true} AND
     * {@code currency ∈ {EUR, USD}}. The returned listing carries its currency; a caller that must
     * also match a submitted currency does that comparison itself (FD002 FR-011;
     * {@code specs/FD002-…/contracts/instrument-catalog-port.md} C2). Local PostgreSQL only — never
     * contacts an external provider. At most one result ({@code (ticker, market_mic)} is unique).
     */
    Optional<FinancialInstrumentListing> findSelectable(String ticker, String marketMic);
}
