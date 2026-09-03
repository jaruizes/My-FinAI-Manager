# Contract — `financialinstrument.domain.ports`

The provider-neutral outbound ports the `business` layer depends on. Implemented by adapters in
`financialinstrument.infrastructure.persistence`. **No** JPA entity, CSV record, HTTP DTO, Yahoo
payload, or mapping-table type may appear in these signatures (ADR-003 AR-055; enabler §17;
VC-010). All types below are `financialinstrument.domain.model` types.

---

## 1. `FinancialInstrumentCatalog` — runtime search (FD002)

```java
public interface FinancialInstrumentCatalog {

    /**
     * Selectable listings matching {@code query} by exact ticker (case-insensitive) or by
     * name-contains (case-insensitive). Only listings that may be chosen for a new Position:
     * active == true AND currency in {EUR, USD}. Exact-ticker matches first, then name matches;
     * each block ordered by ticker ascending. Never contacts an external provider.
     *
     * @param query non-blank, trimmed search term (the business service validates this)
     * @return possibly empty, never null
     */
    List<FinancialInstrumentListing> search(String query);
}
```

**Invariants the adapter MUST preserve**

| # | Invariant | VC |
|---|---|---|
| C1 | Every returned listing has `active == true` and `currency ∈ {EUR, USD}` | VC-006, VC-009 |
| C2 | Every returned listing has a non-blank `ticker`, a resolvable `market` MIC that exists in the `market` table, and a `currency` | VC-009 |
| C3 | Case-insensitive: `search("aapl")` and `search("AAPL")` return the same rows | VC-007 |
| C4 | Name match is substring, case-insensitive: `search("santa")` returns "Banco Santander…" | VC-008 |
| C5 | No match ⇒ empty list (the REST layer returns `200 []`, not `404`) | FD002 AC-008 |
| C6 | The call performs **zero** outbound network I/O to a reference-data provider | VC-006, SC-007 |
| C7 | Ordering: exact-ticker matches precede name-only matches; ties broken by `ticker ASC` | (UX quality) |

---

## 2. `MarketCatalog` — MIC lookup (used by the importer and to enrich results)

```java
public interface MarketCatalog {

    Optional<Market> findByMic(Mic mic);

    /** Bulk lookup — used by the importer to detect MIC_UNRESOLVED / skippedUnsupportedMarket. */
    Map<Mic, Market> findAllByMic(Set<Mic> mics);
}
```

**Invariants**: returns only persisted Markets; never creates one as a side effect; provider-neutral.

---

## 3. `ReferenceCatalogWriter` — idempotent upsert (used only by the import business op)

```java
public interface ReferenceCatalogWriter {

    /** Insert or update by natural identity {@code mic}. */
    UpsertResult upsertMarket(Market market);

    /** Insert or update by natural identity (ticker + market MIC). */
    UpsertResult upsertListing(FinancialInstrumentListing listing);

    enum UpsertResult { INSERTED, UPDATED }
}
```

**Invariants the adapter MUST preserve**

| # | Invariant | VC |
|---|---|---|
| W1 | `upsertMarket` keyed on `mic`; `upsertListing` keyed on `(ticker, market_mic)` — a second call with the same identity returns `UPDATED` and creates **no** new row | VC-011 |
| W2 | `upsertListing` for a `market_mic` with no `market` row fails loudly (FK) — the importer must have loaded Markets first and/or classified the row as `skippedUnsupportedMarket` before calling this | VC-009, VC-012 |
| W3 | `ListingId` is `ListingId.deterministic(ticker, mic)` on insert (stable across runs/machines) | (reproducibility) |
| W4 | Writes participate in the caller's transaction (the importer wraps the whole run) — a rollback undoes every upsert in the run | VC-012 |
| W5 | Neither method deletes or deactivates any existing row (no delisting rule — enabler §15) | VC-012 |
| W6 | `provenance` (`source`, `sourceReference`, `lastImportedAt`) is written/refreshed on every upsert | enabler §21 |

---

## 4. Business operations (`financialinstrument.business`) that use these ports

| Operation | Uses | Behavior |
|---|---|---|
| `SearchFinancialInstrumentsService.search(String rawQuery)` | `FinancialInstrumentCatalog` | trim; if blank → throw `InvalidSearchQueryException` (→ HTTP 400); else delegate to `catalog.search(trimmed)` |
| `ImportReferenceDataService.run(ReferenceSources)` | `MarketCatalog`, `ReferenceCatalogWriter`, `YahooSymbolNormalizer` | one `TransactionTemplate`: load + upsert Markets → for each instrument row: normalize → `Accepted` ⇒ `upsertListing` + count `imported`/`updated`; `Rejected` ⇒ increment the matching counter + append a `Rejection`. Return `ImportReport`. A hard failure (I/O, parse, DB) → `ReferenceDataImportException` (carrying the partial report) and the whole tx rolls back. |

**The importer never persists a `Rejected` row.** Counter mapping:

| `RejectionReason` | counter |
|---|---|
| `UNSUPPORTED_CURRENCY` | `skippedUnsupportedCurrency` |
| `NO_MAPPING`, `NOT_SUPPORTED_FOR_FD002`, `MIC_UNRESOLVED` | `skippedUnsupportedMarket` |
| `AMBIGUOUS_EXCHANGE` | `quarantinedAmbiguous` |
| `SUFFIX_MISMATCH`, `EMPTY_TICKER`, `IDENTITY_CONFLICT` | `quarantinedInvalid` |
