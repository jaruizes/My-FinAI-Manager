# Data Model — FD002 Select Financial Instrument from Catalog

FD002 introduces **no new persisted entity** and **no schema migration**. It reads reference data
owned by `financialinstrument` (EN004) and writes `portfolio` / `position` through the existing
FD001 path. This document records the small model *deltas* and the read/validation contracts.

---

## 1. Persistence — unchanged

| Table | Owner | FD002 impact |
|---|---|---|
| `financial_instrument`, `market` | `financialinstrument` (EN004) | **read only, via ports** — never queried directly by `portfolio` (AR-006) |
| `portfolio`, `position`, `investor` | `portfolio` (FD001) | unchanged — same `POST /api/portfolios` write path, same columns, same `(portfolio_id, ticker, market)` uniqueness |

No `V4` migration. `spring.jpa.hibernate.ddl-auto: none` unchanged.

---

## 2. Domain model deltas (`portfolio` module)

### 2.1 `ValidationCode` (enum) — one new constant

```text
ValidationCode {
  REQUIRED, AT_LEAST_ONE, INVALID_NUMBER, INVALID_DATE, NOT_POSITIVE,
  DUPLICATE_INSTRUMENT, FUTURE_DATE, CURRENCY_FORMAT, NAME_TOO_LONG,
  INSTRUMENT_NOT_IN_CATALOG            // NEW — FD002 FR-011
}
```

`INSTRUMENT_NOT_IN_CATALOG` — the position's **`ticker + market + currency`** is not one active
catalogued listing: unknown instrument, listing on a different market, inactive listing, **or the
submitted currency differs from the listing's currency** (e.g. `AAPL + XNAS + EUR` when the listing
is USD). Mirrors the `openapi.yaml` `ValidationProblem.errors[].code` enum (contract is the source
of truth for the wire value).

### 2.2 `portfolio.domain.ports.InstrumentCatalog` (port) — NEW

```java
public interface InstrumentCatalog {
    /** True iff one active catalogued listing exists for exactly this ticker + market
     *  whose currency equals {@code currency} (FD002 BR-004). */
    boolean isSelectable(Ticker ticker, Market market, Currency currency);
}
```

- Uses `portfolio`'s own `Ticker` / `Market` / `Currency` value objects — no `financialinstrument`
  type crosses into `portfolio.domain` / `portfolio.business`.
- Consumed by `CreatePortfolioService`. Implemented in `portfolio.infrastructure.catalog` (D2) — the
  adapter calls `findSelectable(ticker, mic)` then compares the listing currency.
- A bulk variant `Set<InstrumentRef> selectableOf(Set<InstrumentRef>)` MAY be added if a portfolio
  can carry many positions (D3 alternative) — decided in `/speckit-tasks`.

### 2.3 `Portfolio` aggregate — unchanged

`Portfolio.create(investorId, rawName, rawPositions, clock)` keeps its exact signature and its
single-pass, all-violations `PortfolioValidationException`. The catalog check is **merged in by the
service**, not by the aggregate (D1).

### 2.4 `CreatePortfolioService` — one new dependency

Constructor gains `InstrumentCatalog instrumentCatalog`. Flow per D1: structural violations from
`Portfolio.create` + catalog violations from `isSelectable(ticker, market, currency)` (only for
positions that carry all three) → one `PortfolioValidationException`. Idempotency replay,
default-investor resolution, atomic `save`, and the `PortfolioCreated` / `PositionAdded` log events
are unchanged. A `DataAccessException` from the catalog read is **not** caught here — it propagates
to the existing not-saved (503) path (contracts C1 P6).

---

## 3. `financialinstrument` module delta

### 3.1 `FinancialInstrumentCatalog` (port) — one new method

```java
Optional<FinancialInstrumentListing> findSelectable(String ticker, String marketMic);
```

- Exact ticker (case-insensitive) + exact MIC; `active == true` AND `currency ∈ {EUR, USD}`.
  Returns the listing **including its currency** — the `portfolio` adapter compares it to the
  submitted currency (the currency match is done in `portfolio`, not here — this port stays a
  ticker+MIC lookup).
- Implemented in `FinancialInstrumentCatalogAdapter` via
  `FinancialInstrumentJpaRepository.findByTickerIgnoreCaseAndMarketMic(...)` + the active/currency
  guard (or a derived-query equivalent). Read-only.
- No change to `search(...)`, to ingestion, or to `GET /api/financial-instruments`.

### 3.2 `FinancialInstrumentListing` (domain model) — unchanged

Already carries `id (ListingId)`, `name`, `ticker (Ticker)`, `market (Mic)`, `currency
(SupportedCurrency)`, `isin?`, `active`, `providerSymbol?`, provenance. FD002 reads `ticker`,
`market`, `currency`, `active`, `name`, `isin`.

---

## 4. Frontend view models (`portfolio` feature area)

### 4.1 `CatalogListing` — NEW (mirrors the EN004 `FinancialInstrument` schema)

```ts
interface CatalogListing {
  id: string;            // ListingId (uuid) — used as the option key
  name: string;
  ticker: string;        // normalized, uppercase
  market: string;        // ISO 10383 MIC
  currency: 'EUR' | 'USD';
  active: boolean;       // always true in search results
  isin?: string | null;
}
```

### 4.2 Instrument search state — NEW

```ts
type InstrumentSearchState =
  | { kind: 'idle' }
  | { kind: 'searching' }
  | { kind: 'results'; listings: CatalogListing[] }
  | { kind: 'no-results'; query: string }
  | { kind: 'error' };
```

### 4.3 `PositionDraft` — unchanged on the wire

Still `{ ticker, market, quantity, currency, initialPurchaseDate?, averagePurchasePrice? }`. The
dialog additionally holds, **not serialized**:

```ts
{ selectedListing: CatalogListing | null }   // for display + to drive the FR-007 listing selector
```

### 4.4 `FieldError.code` — string union gains `INSTRUMENT_NOT_IN_CATALOG`

Rendered by `create-portfolio.page` like any other position field error (no page-logic change).

---

## 5. Validation rules (where each lives)

| Rule | Layer | Code |
|---|---|---|
| ticker / market / currency required, number/date formats, positive quantity/price, no future date, no duplicate `ticker+market`, name length | `Portfolio.create` (domain) — FD001, unchanged | `REQUIRED` / `INVALID_NUMBER` / `INVALID_DATE` / `NOT_POSITIVE` / `FUTURE_DATE` / `CURRENCY_FORMAT` / `DUPLICATE_INSTRUMENT` / `NAME_TOO_LONG` / `AT_LEAST_ONE` |
| **position's `ticker + market + currency` is one active catalogued listing** (unknown / wrong market / inactive / currency ≠ the listing's) | `CreatePortfolioService` (business) via `InstrumentCatalog` port — **NEW** | `INSTRUMENT_NOT_IN_CATALOG` |
| frontend cannot assemble an invalid `ticker+market+currency` | frontend (constrained selector, FR-007/FR-010) | n/a (prevention, not a code) |

All rules still report together in one `400 /problems/portfolio-validation` with `errors[]`.
