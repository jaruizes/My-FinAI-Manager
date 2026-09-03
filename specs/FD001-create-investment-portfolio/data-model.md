# Phase 1 Data Model: Create Investment Portfolio (FD001)

Two views: the **domain model** (`portfolio.domain`, framework-free, the authority for every rule)
and the **persistence schema** (PostgreSQL, owned by the `portfolio` module). The web DTOs are a
third, separate shape defined by the OpenAPI contract — not repeated here.

Traceability: every rule cites its FD001 origin (`BR-nnn`, `AC-nnn`, `§n`) or a spec assumption
(`A-nn`) / functional requirement (`FR-nnn`).

---

## 1. Domain model (`com.myfinaimanager.core.portfolio.domain`)

### Aggregate: `Portfolio` (root)

| Field | Type | Rule |
|---|---|---|
| `id` | `PortfolioId` (UUID) | system-generated |
| `investorId` | `InvestorId` (UUID) | non-null; the platform-seeded default Investor (FR-030, A2) |
| `name` | `PortfolioName` | non-blank after trim, ≤ 120 chars (BR-001, AC-003, A7) |
| `status` | `PortfolioStatus` | `ACTIVE` on creation; no other states in FD001 (A12) |
| `positions` | `List<Position>` | ≥ 1 (BR-002); no two with equal `InstrumentRef` (BR-004, AC-005) |
| `createdAt` | `Instant` | set at creation (ISO 8601 / `TIMESTAMPTZ`) |

**Construction**: static factory `Portfolio.create(InvestorId, PortfolioName, List<Position>, Clock)`.
It validates **all** invariants and, on failure, throws one `PortfolioValidationException`
containing every `Violation` found (not fail-fast — the Investor sees all problems at once, FR-024).

Invariants enforced by the root:

- `BR-001` name present → else `Violation("name", "REQUIRED", "Portfolio name is required")`
- `BR-002` at least one Position → else `Violation("positions", "AT_LEAST_ONE", …)`
- `BR-004` / `AC-005` no duplicate `InstrumentRef` among `positions` → `Violation("positions[i]", "DUPLICATE_INSTRUMENT", "A position for TICKER on MARKET already exists in this portfolio")`

Behavior: `Portfolio` is immutable once created (FD001 creates and persists; no mutation — §3).

### Entity: `Position` (child of `Portfolio`)

| Field | Type | Rule |
|---|---|---|
| `id` | `PositionId` (UUID) | system-generated |
| `instrument` | `InstrumentRef` | `Ticker` + `Market` — the within-portfolio identity (BR-003, §14.1) |
| `quantity` | `Quantity` | `BigDecimal` > 0 (BR-005, AC-004, FR-009); fractional allowed (A6) |
| `currency` | `Currency` | ISO 4217 shape, required (BR-006, FR-016); never inferred (BR-006, FR-017) |
| `initialPurchaseDate` | `Optional<LocalDate>` | if present: not in the future (A4, FR-011); else "not provided" (BR-008, AC-006, FR-019) |
| `averagePurchasePrice` | `Optional<Money>` | if present: amount > 0 (A3, FR-010) and `Money.currency == this.currency` (BR-007, AC-008, FR-020); else "not provided", **never 0 or inferred** (BR-009, AC-007, FR-019) |

**Construction**: `Position.of(InstrumentRef, Quantity, Currency, Optional<LocalDate>, Optional<Money>)`
collects `Violation`s (bubbled into the aggregate's exception with an index-qualified field path).

`Position` records **only the current aggregated holding** — no transactions, no purchase lots
(BR-010, FR-021, §14.9–14.10).

### Value objects

| Type | Constraint | Rule |
|---|---|---|
| `PortfolioName` | non-blank after `strip()`, length 1–120 | BR-001, A7 |
| `Ticker` | non-blank after trim; upper-cased; length ≤ 20 | §5, BR-003 |
| `Market` | non-blank after trim; upper-cased; if it matches the ISO 10383 MIC shape (4 alphanum) it is kept as-is, otherwise stored verbatim as the Investor supplied it | BR-003, FR-015, A5 |
| `InstrumentRef` | `Ticker` + `Market`; value equality drives duplicate detection | BR-003, FR-012, FR-014 |
| `Currency` | exactly 3 ASCII uppercase letters (ISO 4217 **shape** only — no registry lookup) | BR-006, FR-016, A5 |
| `Quantity` | `BigDecimal`, `> 0`, scale preserved as entered | BR-005, FR-009, FR-025, DR-011 |
| `Money` | `BigDecimal amount` (`> 0` when used for a price) + `Currency` | BR-007, FR-010, FR-020, FR-025 |
| `PortfolioId`, `PositionId`, `InvestorId` | UUID wrappers | — |
| `PortfolioStatus` | enum `{ ACTIVE }` | A12 |

### Errors

- `PortfolioValidationException extends RuntimeException` — carries `List<Violation>`.
- `Violation(String field, String code, String message)` — `field` is a JSON-pointer-ish path
  (`name`, `positions`, `positions[1].quantity`, `positions[0].averagePurchasePrice`); `code` is
  one of the canonical tokens defined by the `code` enum in `contracts/openapi/openapi.yaml`
  (`REQUIRED`, `AT_LEAST_ONE`, `INVALID_NUMBER`, `NOT_POSITIVE`, `DUPLICATE_INSTRUMENT`,
  `FUTURE_DATE`, `CURRENCY_FORMAT`, `NAME_TOO_LONG`) — the contract is the single source of truth
  for this list; `message` is human-readable and non-technical. The web adapter maps this list
  straight into the `400` problem body's `errors[]` and the frontend maps `errors[]` back onto
  form controls (FR-024, D2, D4).

### What is NOT modelled (FD001 §3 / FR-033–FR-034)

No valuation, market price, weight, risk, recommendation, stop-loss, Investment Thesis,
Portfolio Review, or any post-creation mutation. `Financial Instrument` is only a `ticker+market`
reference — no canonical instrument entity, no enrichment, no ISIN/CFI/company (A5).

---

## 2. Application ports

| Port | Direction | Signature (conceptual) | Adapter |
|---|---|---|---|
| `CreatePortfolioUseCase` | inbound | `CreatePortfolioResult create(CreatePortfolioCommand)` | `CreatePortfolioController` |
| `PortfolioRepository` | outbound | `void save(Portfolio)` (atomic); `Optional<PortfolioId> findByIdempotencyKey(IdempotencyKey)` | `JdbcPortfolioRepository` |
| `DefaultInvestorProvider` | outbound | `InvestorId get()` | `JdbcDefaultInvestorProvider` |

- `CreatePortfolioCommand`: `name` (raw string), `positions` (list of raw position inputs),
  `idempotencyKey` (string). Raw strings so the **domain** does the parsing/validation, not the
  web layer (D2).
- `CreatePortfolioResult`: `portfolioId`, `replayed` (boolean — true when resolved via an existing
  idempotency key, D3).
- `CreatePortfolioService` orchestration: `findByIdempotencyKey` → if present return
  `{id, replayed:true}`; else `DefaultInvestorProvider.get()` → `Portfolio.create(...)` (validates)
  → `PortfolioRepository.save(...)` → emit `PortfolioCreated` + `PositionAdded` structured log
  lines (D6) → return `{id, replayed:false}`. Framework-free; constructor-wired in
  `PortfolioBeanConfiguration`.

---

## 3. Persistence schema (PostgreSQL — Flyway `V2__portfolio.sql`)

Owned exclusively by the `portfolio` module (AR-020). All money/quantity columns are `NUMERIC`
(exact decimal — DR-011, FR-025).

### `investor`  *(placeholder for the future identity capability; FD001 only seeds + reads)*

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` PK | |
| `display_name` | `TEXT NOT NULL` | |
| `preferred_currency` | `CHAR(3)` | ISO 4217; nullable |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | |

**Seed (in the same migration):** one row, fixed UUID, `display_name = 'Default Investor'`,
`preferred_currency = 'EUR'` (FR-030, A2, D10).

### `portfolio`

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` PK | |
| `investor_id` | `UUID NOT NULL REFERENCES investor(id)` | FR-030, SC-012 |
| `name` | `TEXT NOT NULL` | `CHECK (length(btrim(name)) BETWEEN 1 AND 120)` (BR-001, A7) — **not** unique (FR-004) |
| `status` | `TEXT NOT NULL DEFAULT 'ACTIVE'` | `CHECK (status IN ('ACTIVE'))` (A12) |
| `idempotency_key` | `TEXT NOT NULL UNIQUE` | D3 / FR-031a / SC-011 |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | |

Index: `UNIQUE (idempotency_key)` (implicit from the column constraint), `INDEX (investor_id)`.

### `position`

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` PK | |
| `portfolio_id` | `UUID NOT NULL REFERENCES portfolio(id) ON DELETE CASCADE` | aggregate child |
| `ticker` | `TEXT NOT NULL` | §5 |
| `market` | `TEXT NOT NULL` | BR-003, FR-015 |
| `quantity` | `NUMERIC NOT NULL` | `CHECK (quantity > 0)` (BR-005, FR-025) |
| `currency` | `CHAR(3) NOT NULL` | ISO 4217 shape (BR-006, FR-016) |
| `initial_purchase_date` | `DATE` | nullable = "not provided" (BR-008, FR-019); `CHECK (initial_purchase_date <= current_date)` (A4) |
| `average_purchase_price` | `NUMERIC` | nullable = "not provided" (BR-009, FR-019); `CHECK (average_purchase_price IS NULL OR average_purchase_price > 0)` (A3) |
| `average_purchase_price_currency` | `CHAR(3)` | when price present it equals `currency` (BR-007); `CHECK (average_purchase_price_currency IS NULL OR average_purchase_price_currency = currency)` |

Constraint: `UNIQUE (portfolio_id, ticker, market)` — defense-in-depth for BR-004 (D2). Primary
enforcement is in the domain, so the adapter should not rely on the DB error for the user message.

### Atomicity (FR-023 / SC-010)

`JdbcPortfolioRepository.save(Portfolio)` runs one `@Transactional` method: insert `portfolio`,
then batch-insert `position` rows. Any failure rolls the whole transaction back → no partial
Portfolio, no orphan Positions. A unique-violation on `idempotency_key` (concurrent duplicate
submit) is caught and resolved by re-reading the existing Portfolio (D3).

### Migration notes

- New file: `implementation/platform/backend/core-service/src/main/resources/db/migration/V2__portfolio.sql`.
- `V1__baseline.sql` is unchanged (EN001 empty baseline).
- Migration is applied on startup and by the Testcontainers integration test (`CreatePortfolioIT`
  asserts the schema and the seed row exist).

---

## 4. Frontend view models (`portfolio/portfolio-creation.models.ts`)

Mirror the **contract**, not the domain: `PortfolioDraft { name: string; positions: PositionDraft[] }`,
`PositionDraft { ticker; market; quantity: string; currency; initialPurchaseDate?: string;
averagePurchasePrice?: string }`. Amounts are kept as strings in the UI to avoid float drift and
are sent as JSON strings/numbers per the contract. Backend `errors[]` are mapped onto the
corresponding reactive-form controls by `field` path.
