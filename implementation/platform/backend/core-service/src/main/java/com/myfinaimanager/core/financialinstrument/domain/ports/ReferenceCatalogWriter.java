package com.myfinaimanager.core.financialinstrument.domain.ports;

import com.myfinaimanager.core.financialinstrument.domain.model.FinancialInstrumentListing;
import com.myfinaimanager.core.financialinstrument.domain.model.Market;

/**
 * Outbound port — idempotent upsert of reference data, used only by the import business operation.
 * Keyed on natural identity ({@code mic} for Markets, {@code ticker + market} for listings): a
 * second call with the same identity returns {@link UpsertResult#UPDATED} and creates no new row
 * (VC-011). Writes participate in the caller's transaction; neither method deletes or deactivates
 * an existing row (no delisting rule — EN004 §15). See {@code contracts/catalog-ports.md} §3.
 */
public interface ReferenceCatalogWriter {

    UpsertResult upsertMarket(Market market);

    UpsertResult upsertListing(FinancialInstrumentListing listing);

    enum UpsertResult {
        INSERTED,
        UPDATED
    }
}
