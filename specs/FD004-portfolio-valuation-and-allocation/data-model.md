# Phase 1 — Data Model: Portfolio Valuation & Allocation (FD004)

**Plan**: [plan.md](./plan.md) · **Research**: [research.md](./research.md) · **Spec**: [spec.md](./spec.md)

Scope: the FD004 **valuation** domain model (in the `portfolio` module), its persistence schema
(`V4__portfolio_valuation.sql`), and the API read shape. **No change** to the `Portfolio` /
`Position` / EN004 catalog entities — FD004 reads them (FR-023, spec Key Entities tail).

All monetary / rate / weight values are **`BigDecimal`** (FR-008). "Absent" means `Optional.empty()`
in domain, `null` in the DTO, `NULL` in the column — **never `BigDecimal.ZERO`** (FR-017, FR-019).

---

## 1. Domain model (`com.myfinaimanager.core.portfolio.domain`)

### 1.1 `ValuationStatus` (enum — `domain.model`)

`PENDING` · `COMPLETED` · `PARTIAL` · `FAILED` (FR-015; FD004 §6). Exactly these four. The
calculator emits only `COMPLETED` / `PARTIAL` / `FAILED` (research D5); `PENDING` is a stored /
synthetic state only.

### 1.2 `PortfolioValuation` (aggregate root — `domain.model`)

| Field | Type | Rules |
|---|---|---|
| `portfolioId` | `PortfolioId` | required; the valued Portfolio (FD001) |
| `status` | `ValuationStatus` | required |
| `calculatedAt` | `Instant` | required; when this snapshot was computed (FR-020, FR-024; §13 BR-013) |
| `totalValueEUR` | `Optional<BigDecimal>` | present iff every valued Position has a EUR value (research D4); `> 0` when present; unscaled |
| `totalValueUSD` | `Optional<BigDecimal>` | mirror of the above for USD |
| `marketDataAsOf` | `Optional<Instant>` | earliest `priceObservedAt` among valued Positions (research D8) |
| `fxDataAsOf` | `Optional<Instant>` | earliest `observedAt` among FX rates actually used |
| `positions` | `List<PositionValuation>` | one per Portfolio Position; order = Portfolio's Position order |
| `sectors` | `List<SectorAllocation>` | one per distinct sector among **valued** Positions that have a EUR value; empty when no EUR basis |

Invariants (enforced in the constructor / factory used by the calculator):
- `status == FAILED` ⇒ `positions` may be fully unvalued; `sectors` empty; totals may both be absent.
- `status == COMPLETED` ⇒ every `positions[i].valued == true` **and** each has `valueInEUR` and
  `valueInUSD` present; both totals present; `sectors` non-empty (unless the Portfolio has zero
  Positions — not reachable via FD001 which requires ≥ 1).
- `sectors` weights and `positions` weights are present **only** when `totalValueEUR` is present and
  `> 0`; otherwise absent on every row (never `0`/`NaN` — spec Edge Case "Zero total EUR").
- Σ `positions[valued & has EUR].portfolioWeight` ≈ 1 and Σ `sectors.sectorWeight` ≈ 1 to 12-dp
  exactness (FR-013, SC-003).

### 1.3 `PositionValuation` (entity within the aggregate — `domain.model`)

| Field | Type | Rules |
|---|---|---|
| `ticker` | `String` | from the Position's canonical instrument (EN004); non-blank |
| `market` | `String` | MIC, from EN004; non-blank |
| `quantity` | `BigDecimal` | from the Position (FD001); `> 0` |
| `nativeCurrency` | `String` | `"EUR"` or `"USD"` (FD002 constrains the catalog to these) |
| `valued` | `boolean` | `true` iff a market price was available |
| `marketPrice` | `Optional<BigDecimal>` | present iff `valued`; `> 0`; unscaled |
| `nativeMarketValue` | `Optional<BigDecimal>` | present iff `valued`; `= quantity × marketPrice` (exact) |
| `valueInEUR` | `Optional<BigDecimal>` | present iff `valued` **and** the needed FX (for a USD Position) was available |
| `valueInUSD` | `Optional<BigDecimal>` | present iff `valued` **and** the needed FX (for an EUR Position) was available |
| `portfolioWeight` | `Optional<BigDecimal>` | fraction (scale 12); present iff `valueInEUR` present and `totalValueEUR > 0` |
| `sector` | `String` | the EN005 classification string, or the literal `"Unclassified"` (FR-011, FR-014) — **never null/empty** |
| `priceObservedAt` | `Optional<Instant>` | EN005 `MarketPrice.observedAt`; present iff `valued` |

An **unvalued** Position: `valued = false`, every `Optional` monetary/`priceObservedAt` field empty,
`sector` still set (from the profile if it resolved, else `"Unclassified"`). It contributes nothing
to totals, weights, or sector allocation, and forces `status ∈ {PARTIAL, FAILED}` (FR-017, FR-018).

### 1.4 `SectorAllocation` (value object within the aggregate — `domain.model`)

| Field | Type | Rules |
|---|---|---|
| `sector` | `String` | classification string or `"Unclassified"`; distinct per list |
| `sectorValueEUR` | `BigDecimal` | `= Σ valueInEUR` of that sector's valued Positions (exact, unscaled); `> 0` |
| `sectorWeight` | `BigDecimal` | `= sectorValueEUR ÷ totalValueEUR` (scale 12, `HALF_UP`) |

Only emitted when `totalValueEUR` is present and `> 0`. A valued USD Position with no FX (no
`valueInEUR`) is **excluded** here (research D4).

### 1.5 `PortfolioValuationCalculator` (pure domain service — `domain.model`)

Signature and full algorithm: [contracts/valuation-calculation.md](./contracts/valuation-calculation.md).
No ports, no Spring, no I/O. Input DTOs (`domain.model`, package-private or nested): `PositionInput`
(ticker, market, `BigDecimal` quantity, `String` nativeCurrency, `Optional<BigDecimal>` price,
`Optional<Instant>` priceObservedAt, `Optional<String>` sector), `FxContext` (`Optional<BigDecimal>`
usdToEur, `Optional<BigDecimal>` eurToUsd, `Optional<Instant>` usdToEurObservedAt,
`Optional<Instant>` eurToUsdObservedAt).

### 1.6 Domain ports (`domain.ports`)

**`MarketDataGateway`** — ACL over EN005 (research D2, [contracts/market-data-gateway.md](./contracts/market-data-gateway.md)):

```java
Optional<PositionPricing> latestPrice(String ticker, String market, String currencyCode);
Optional<String>          sector(String ticker, String market, String currencyCode);
Optional<FxConversion>    fxRate(String fromCurrencyCode, String toCurrencyCode);
```

`PositionPricing(BigDecimal price, Instant observedAt)`, `FxConversion(BigDecimal rate, Instant
observedAt)` — `domain.model` records; `price`/`rate` `> 0`.

**`PortfolioValuationRepository`** — persistence port:

```java
void                        upsertLatest(PortfolioValuation valuation);   // replace the single snapshot for its portfolioId, one tx
Optional<PortfolioValuation> findByPortfolioId(PortfolioId portfolioId);
```

### 1.7 Domain event (`domain.events`)

`PortfolioCreatedEvent(PortfolioId portfolioId)` — published by `CreatePortfolioService` after a
genuine (`!replayed`) `save` (research D1).

---

## 2. Persistence schema — `V4__portfolio_valuation.sql`

Flyway forward migration (V1 baseline, V2 FD001, V3 EN004 → **V4**). Owned by the `portfolio`
module. **No `ALTER`/`DROP` on `portfolio`, `position`, or EN004 tables** (FR-022, FR-023, SC-012).
`spring.jpa.hibernate.ddl-auto = none`.

### 2.1 `portfolio_valuation`

| Column | Type | Constraints |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `portfolio_id` | `UUID` | `NOT NULL`, **`UNIQUE`**, `REFERENCES portfolio(id) ON DELETE CASCADE` — the latest-only guarantee (FR-020) |
| `status` | `TEXT` | `NOT NULL`, `CHECK (status IN ('PENDING','COMPLETED','PARTIAL','FAILED'))` |
| `calculated_at` | `TIMESTAMPTZ` | `NOT NULL` |
| `total_value_eur` | `NUMERIC` | nullable (absent ⇒ not producible) |
| `total_value_usd` | `NUMERIC` | nullable |
| `market_data_as_of` | `TIMESTAMPTZ` | nullable |
| `fx_data_as_of` | `TIMESTAMPTZ` | nullable |

### 2.2 `position_valuation`

| Column | Type | Constraints |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `portfolio_valuation_id` | `UUID` | `NOT NULL`, `REFERENCES portfolio_valuation(id) ON DELETE CASCADE` |
| `ticker` | `TEXT` | `NOT NULL` |
| `market` | `TEXT` | `NOT NULL` |
| `quantity` | `NUMERIC` | `NOT NULL` |
| `valued` | `BOOLEAN` | `NOT NULL` |
| `native_currency` | `CHAR(3)` | `NOT NULL` |
| `market_price` | `NUMERIC` | nullable (never `0` for "unknown") |
| `native_market_value` | `NUMERIC` | nullable |
| `value_eur` | `NUMERIC` | nullable |
| `value_usd` | `NUMERIC` | nullable |
| `portfolio_weight` | `NUMERIC` | nullable; exact fraction, scale 12 |
| `sector` | `TEXT` | `NOT NULL` (`'Unclassified'` when absent) |
| `price_observed_at` | `TIMESTAMPTZ` | nullable |
| index | `ix_position_valuation_parent (portfolio_valuation_id)` | |

### 2.3 `sector_allocation`

| Column | Type | Constraints |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `portfolio_valuation_id` | `UUID` | `NOT NULL`, `REFERENCES portfolio_valuation(id) ON DELETE CASCADE` |
| `sector` | `TEXT` | `NOT NULL` |
| `sector_value_eur` | `NUMERIC` | `NOT NULL` |
| `sector_weight` | `NUMERIC` | `NOT NULL`; exact fraction, scale 12 |
| index | `ix_sector_allocation_parent (portfolio_valuation_id)` | |

### 2.4 Write behavior

`PortfolioValuationPersistenceAdapter.upsertLatest(v)` — one write transaction:
`DELETE FROM portfolio_valuation WHERE portfolio_id = :pid` (cascades to children), then
`INSERT` the new parent + `position_valuation` rows + `sector_allocation` rows. Result: **exactly
one** `portfolio_valuation` row per `portfolio_id` at all times (FR-021, SC-002). Zero writes to
`portfolio` / `position` (SC-008).

JPA entities: `PortfolioValuationEntity` (`@OneToMany(cascade = ALL, orphanRemoval = true)` to both
children), `PositionValuationEntity`, `SectorAllocationEntity` — under
`portfolio/infrastructure/persistence/entity/**` (JaCoCo-excluded package; confirm wildcard).

---

## 3. API read shape — `PortfolioValuation` (OpenAPI 3.0.3)

Full fragment: [contracts/openapi/portfolio-valuation.read.yaml](./contracts/openapi/portfolio-valuation.read.yaml).
Mirrored into `implementation/platform/contracts/openapi/openapi.yaml`. Business language only — no
entity, column, JPA, or Finnhub name (FR-027, SC-007).

```
PortfolioValuation:
  portfolioId        string(uuid)                 required
  status             enum[PENDING,COMPLETED,PARTIAL,FAILED]   required
  calculatedAt       string(date-time)            nullable   (null only for the synthetic PENDING body)
  totalValueEUR      string(decimal)              nullable
  totalValueUSD      string(decimal)              nullable
  marketDataAsOf     string(date-time)            nullable
  fxDataAsOf         string(date-time)            nullable
  positions          PositionValuation[]          required (may be empty)
  sectors            SectorAllocation[]           required (may be empty)

PositionValuation:
  ticker             string     required
  market             string     required
  quantity           string(decimal)  required
  nativeCurrency     enum[EUR,USD]    required
  valued             boolean    required
  marketPrice        string(decimal)  nullable
  nativeMarketValue  string(decimal)  nullable
  valueInEUR         string(decimal)  nullable
  valueInUSD         string(decimal)  nullable
  portfolioWeight    string(decimal)  nullable    (fraction, e.g. "0.761904761905")
  sector             string     required          ("Technology" | "Unclassified" | …)
  priceObservedAt    string(date-time)  nullable

SectorAllocation:
  sector             string     required
  sectorValueEUR     string(decimal)  required
  sectorWeight       string(decimal)  required     (fraction)
```

Decimals are serialized as **strings** to preserve `BigDecimal` precision on the wire (matches the
FD001/FD003 money representation — confirm during implementation and follow whatever FD003's
`Portfolio` schema does for `averagePurchasePrice`). Percentages are **not** pre-multiplied — the
client multiplies by 100 and formats to 2 dp (FR-031, research D9).

Errors: `400` (non-UUID `portfolioId`, framework) and `404` (`/problems/portfolio-not-found`,
unknown or not-current-investor) as `application/problem+json` (RFC 9457), identical to FD003.

---

## 4. Entity ↔ domain ↔ DTO mapping

| Domain (`PortfolioValuation`) | Column (`portfolio_valuation`) | DTO (`PortfolioValuationResponse`) |
|---|---|---|
| `portfolioId` | `portfolio_id` | `portfolioId` |
| `status` | `status` | `status` |
| `calculatedAt` | `calculated_at` | `calculatedAt` |
| `totalValueEUR?` | `total_value_eur` (NULL) | `totalValueEUR` (null) |
| `totalValueUSD?` | `total_value_usd` (NULL) | `totalValueUSD` (null) |
| `marketDataAsOf?` | `market_data_as_of` (NULL) | `marketDataAsOf` (null) |
| `fxDataAsOf?` | `fx_data_as_of` (NULL) | `fxDataAsOf` (null) |
| `positions[]` | `position_valuation` rows | `positions[]` |
| `sectors[]` | `sector_allocation` rows | `sectors[]` |

`PositionValuation` / `SectorAllocation` map field-for-field (§1.3 / §1.4 ↔ §2.2 / §2.3 ↔ §3);
every domain `Optional.empty()` ⇒ `NULL` column ⇒ `null` JSON.

**Synthetic PENDING** (`findByPortfolioId` empty, Portfolio exists — research D7): DTO
`{ portfolioId, status: "PENDING", calculatedAt: null, all totals null, positions: [], sectors: [] }`
— constructed in the controller/mapper, **not** persisted.

---

## 5. Traceability

| Element | Requirement |
|---|---|
| `PortfolioValuation` latest-only, `portfolio_id UNIQUE` | FR-020, FR-021, SC-002 |
| `PositionValuation.valued` + nullable monetary fields | FR-017, FR-019, SC-004 |
| `nativeMarketValue = quantity × marketPrice` (exact) | FR-005, SC-001 |
| `valueInEUR` / `valueInUSD` via FX; canonical EUR weights | FR-006, FR-009, SC-001, SC-003 |
| `SectorAllocation`, `Unclassified` | FR-011, FR-012, FR-014, SC-003 |
| `ValuationStatus` set + rules | FR-015…FR-018, SC-004, SC-005 |
| `MarketDataGateway` ACL, no `marketdata`/Finnhub types in domain | FR-026, FR-027, FR-035, SC-006, SC-007 |
| `V4` Flyway, no FD001/EN004 table change, `ddl-auto: none` | FR-022, FR-023, SC-012 |
| `GET /api/portfolios/{portfolioId}/valuation`, FD003 contract unchanged | FR-024, FR-025, SC-012 |
| `calculatedAt` / `marketDataAsOf` / `fxDataAsOf` freshness | FR-020, FR-024; §13 BR-013 |
| `BigDecimal` everywhere, no `double`/`float` | FR-008, SC-006 |

---

## Revision 2 — Allocation chart view-models (frontend only)

**No persistence, no API, no contract change.** The two charts are pure view-models built in
`portfolio-detail.page.ts` from the `PortfolioValuation` response it already fetches.

### `AllocationSlice` (frontend view-model — `portfolio.models.ts`)

| Field | Type | Source |
|---|---|---|
| `label` | `string` | ticker chart: `PositionValuationView.ticker`; sector chart: `SectorAllocationView.sector` |
| `fraction` | `number` | ticker chart: `Number(PositionValuationView.portfolioWeight)`; sector chart: `Number(SectorAllocationView.sectorWeight)` — the **backend** deterministic weight, never recomputed (BR-016) |

### Derivations

| Chart | Input | Rule |
|---|---|---|
| Allocation by Ticker | `valuation.positions[]` | keep `valued && portfolioWeight != null`; `{label: ticker, fraction: Number(portfolioWeight)}`; sort desc by `fraction` |
| Allocation by Sector | `valuation.sectors[]` | `{label: sector, fraction: Number(sectorWeight)}`; sort desc by `fraction`; `Unclassified` is already its own entry |

### Render gate (`showCharts()`)

Both charts render iff: `valuation` present **and** `status ∈ {COMPLETED, PARTIAL}` **and**
`totalValueEUR` present and `> 0` **and** ≥ 1 ticker slice. Otherwise neither chart is in the DOM
(FR-048) — the valuation-state line is the sole allocation signal.

### Design tokens

`_tokens.scss` gains `--chart-1 … --chart-8` (dark categorical ramp). Slice colour = index → token,
cycling past 8. Legend always carries `label` + `xx.xx %` (colour is never the only signal).

### Revision 2.1 — detail-table trim (2026-09-04)

No new/changed entity or view-model. `PositionValuationView.nativeMarketValue` is retained (still on
the API) but **no longer projected** into a table column, and there is no standalone
sector-allocation view-model — the Allocation by Sector chart's `sectorSlices()` derivation above is
the sole sector-percentage projection. `Market price` is presented as
`` `${decimal2(marketPrice)} ${nativeCurrency}` `` for a valued row.
