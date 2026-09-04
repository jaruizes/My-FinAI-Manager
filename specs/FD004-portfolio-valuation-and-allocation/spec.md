# Feature Specification: Portfolio Valuation & Allocation (FD004)

**Feature Branch**: `FD004-portfolio-valuation-and-allocation`

**Created**: 2026-09-04 · **Revised**: 2026-09-05 (Feature Definition update — two mandatory
allocation pie charts: §17.1–§17.4, BR-014…BR-017, AC-013…AC-015, §29.16–§29.22)

**Status**: Draft (spec) — Feature Definition **Approved** (2026-09-04 + 2026-09-05 chart addendum,
§31 signed by jaruiz)

**Input**: Feature Definition: "After a Portfolio is successfully created, the platform automatically
values it — latest market price per Position (via EN005 `MarketDataPort`), sector per instrument
(via EN005 `InstrumentProfilePort`), EUR/USD FX (via EN005 `FxRatePort`) — and deterministically
calculates each Position's market value, the Portfolio total in EUR and USD, each Position's weight,
and Portfolio allocation by sector. The latest valuation snapshot is persisted and shown in the
FD003 Portfolio detail. A valuation failure never rolls back or invalidates the created Portfolio."

**Authoritative Source**:
`product/definition/features/FD004-portfolio-valuation-and-allocation/FD004-portfolio-valuation-and-allocation.md`
(**Status: Approved** — §31 signed by jaruiz 2026-09-04; the §30 open questions were resolved by the
product owner on 2026-09-04 and recorded in §30).

**Governing decisions (all pre-existing)**: ADR-001 (single `core-service` deployable), ADR-002
(interim unauthenticated write — the "current Investor" is the single seeded Default Investor),
ADR-003 (Standard Spring Backend Architecture), EN002 (containerized browser E2E), the global design
system `product/ux/design-system.md`. **Consumed capabilities**: FD001 (Create Portfolio +
persisted `portfolio` / `position` data), FD002 (`INSTRUMENT_NOT_IN_CATALOG` — must not regress),
FD003 (the Portfolio list + detail views and their `GET /api/portfolios[/{id}]` contract), EN004
(canonical `ticker + market(MIC)` identity), **EN005 (the three provider-neutral ports
`MarketDataPort` / `InstrumentProfilePort` / `FxRatePort`, and the `MarketPrice` / `InstrumentProfile`
/ `FxRate` read models + neutral exception set)**.

> ## Governance note
>
> The FD004 Feature Definition is **Approved** (§31 signed by jaruiz 2026-09-04). The eight §30 open
> questions were **resolved by the product owner on 2026-09-04** and recorded in FD004 §30 —
> summarised in Clarifications below. This spec derives from FD004 §1–§29 and those decisions, adds
> **no** product behavior, and modifies **no** `product/` document (FD004 §30/§31 were updated as a
> human-directed decision record, not by this spec).

---

## Clarifications

### Session 2026-09-04

- Q: Valuation execution — synchronous after the create commit, or asynchronous/background?
  → A: **Synchronous, immediately after the Portfolio-creation transaction commits, in the same
  `POST /api/portfolios` request.** A valuation error is caught; the Portfolio still returns
  successfully. No background execution, no Kafka, no scheduler. *(FD004 §30.1; → FR-004)*
- Q: A dedicated valuation endpoint, or valuation embedded in the FD003 Portfolio-detail response?
  → A: **A dedicated operation `GET /api/portfolios/{portfolioId}/valuation`.** FD003's existing
  `Portfolio` detail schema and contract are left unchanged. *(FD004 §30.5; → FR-025)*
- Q: When is a valuation `FAILED` rather than `PARTIAL`?
  → A: **`FAILED` only when no Position can be valued, OR when neither total (EUR nor USD) can be
  produced at all; otherwise `PARTIAL`.** Missing FX for the cross-currency total → `PARTIAL` (the
  native-currency total still stands). *(FD004 §30.2; → FR-018)*
- Q: Display precision for money and percentages?
  → A: **2 decimal places for display**, both; full precision retained in storage/calculation.
  *(FD004 §30.3–§30.4; → FR-031)*
- Q: Is the Finnhub industry shown directly or normalised to a canonical taxonomy?
  → A: **Shown directly as the EN005 provider classification string**; `Unclassified` when absent.
  No canonical taxonomy in FD004. *(FD004 §30.7; → FR-014)*
- Q: Manual "revalue" action?
  → A: **Out of scope for FD004** (deferred to a possible future feature). *(FD004 §30.8)*

### Session 2026-09-05 — Feature Definition update: two mandatory allocation charts

The FD004 Feature Definition was updated (§4, §17.1–§17.4, §19, §22 BR-014…BR-017, §23 AC-013…AC-015,
§24, §26 checks 11–15, §28, §29.16–§29.22, §31 signed by jaruiz) to make the Portfolio detail
display **two mandatory circular / pie charts** when valuation data is available:

- Q: Are the allocation charts optional visual enhancements or mandatory FD004 functionality?
  → A: **Mandatory.** A ticker-allocation pie chart **and** a sector-allocation pie chart MUST be
  displayed in the Portfolio detail whenever valuation data is available; a missing/inconsistent
  chart is a **closure-gate failure** (FD004 §17, §28, §29.20). This supersedes the earlier
  "(a chart is optional)" wording. *(→ FR-028, FR-044…FR-048)*
- Q: What data drives each chart's slices?
  → A: **Exactly the deterministic FD004 results already returned by the valuation API** — the
  ticker chart from each **valued** Position's `portfolioWeight` (its `valueInEUR / totalValueEUR`),
  the sector chart from each `sectors[]` entry's `sectorWeight`. The frontend MUST NOT recompute
  allocation from raw provider data (FD004 §17.1, §17.3, §22 BR-016). **No valuation API / contract
  change is needed** — `positions[]` (`ticker`, `valued`, `portfolioWeight`) and `sectors[]`
  (`sector`, `sectorWeight`) already carry it. *(→ FR-046, FR-047)*
- Q: How do the charts behave for a `PARTIAL` valuation?
  → A: **They render only the successfully-valued portion, and the existing partial-state message
  MUST make clear the charts represent incomplete data** (FD004 §17.3, §22 BR-017). If there is no
  EUR basis (`totalValueEUR` absent → all `portfolioWeight` null), neither chart is drawn and the
  detail shows the state message instead — never an empty or fabricated chart. *(→ FR-048)*

### Session 2026-09-05 (part 2) — Portfolio-detail table trim (owner request)

The Position table in the Portfolio detail is trimmed now that the sector chart carries the sector
allocation (FR-029 is "MAY be extended" — this is a display refinement, not a scope change):

- Q: Keep the "Market Value" (native `nativeMarketValue`) column?
  → A: **Remove it.** It is redundant with `Value in EUR` / `Value in USD`; the useful native
  figure is the *price*, shown in the next point. *(→ FR-029)*
- Q: How is the market price shown?
  → A: **`Market Price` shows the value with its native currency** (e.g. `200.00 USD`), so the
  reader can tell EUR from USD prices at a glance without a separate column. *(→ FR-029)*
- Q: Keep the standalone "Sector allocation" percentage list under the table?
  → A: **Remove it.** The **Allocation by Sector** pie chart's legend already shows every sector +
  its percentage (`Technology 76.19 %`, …), so the list is duplicate information. The sector
  allocation is still "shown as percentages" — now via the chart legend (FR-028). *(→ FR-028,
  FR-029)*

---

## Overview *(mandatory)*

FD004 turns the read-only Portfolio detail (FD003) into a **valued** view. It is a **new business
capability** — deterministic Portfolio valuation and sector allocation — layered on the external
data EN005 already exposes as provider-neutral ports.

- **Automatic trigger** — a *successfully persisted* Portfolio (FD001) initiates a valuation
  operation. Portfolio creation and valuation are **independent outcomes**: a valuation failure must
  never roll back, delete, or hide the created Portfolio (FD004 §3, §13, §22 BR-002; AC-011).
- **Per-Position valuation** — for each Position: latest market price (`MarketDataPort`), sector
  (`InstrumentProfilePort`), then `nativeMarketValue = quantity × marketPrice`, and the value
  expressed in **both EUR and USD** using EUR/USD FX (`FxRatePort`). All monetary maths is
  **deterministic** and decimal-safe; **no LLM** computes or infers a monetary value or a sector
  (FD004 §8, §10, §22 BR-003/BR-008).
- **Portfolio totals & weights** — total value in EUR and USD (Σ of Position values); each
  Position's weight = its **EUR** value / total **EUR** value (EUR is the canonical allocation
  currency — §9, §22 BR-007).
- **Sector allocation** — Positions grouped by sector; per-sector percentage = sector EUR value /
  total EUR value. A valued Position with no sector is **`Unclassified`** (§10, §11, §22 BR-009).
- **Explicit status** — every valuation has a status `PENDING` / `COMPLETED` / `PARTIAL` / `FAILED`
  (§6). Missing price → the Position is **not** valued and is **never** zero; the valuation is
  `PARTIAL` (§16, §22 BR-010; AC-010).
- **Latest snapshot only** — FD004 persists and exposes the **latest** valuation snapshot per
  Portfolio; re-running valuation replaces it (§12, §15, §22 BR-011). **No historical valuation.**
- **Detail UX** — the FD003 Portfolio detail is extended with the EUR/USD totals, per-Position
  valuation columns (shown only when available), sector allocation percentages, and a clear
  **valuation-state** message. The UI **never** shows fabricated zero values (§17, §18).
- **Two mandatory allocation charts** — when valuation data is available the detail displays an
  **Allocation by Ticker** pie chart (one slice per valued Position, sized by normalised-EUR
  `portfolioWeight`) and an **Allocation by Sector** pie chart (one slice per sector, sized by
  `sectorWeight`). Both are **mandatory FD004 functionality and a closure gate** (§17.1–§17.4, §22
  BR-014…BR-017, §28); both are driven **only** by the deterministic FD004 valuation results — the
  frontend does not recompute allocation. No API/contract change (the response already carries the
  weights).

**Explicitly out of scope** (FD004 §4 "Out of Scope", §28, §30 guardrail): historical valuation or
performance; profit/loss vs purchase price; benchmarks; dividends / taxes / fees; stop-loss; risk
scoring; AI recommendations; news; rebalancing; automatic trading; continuous / intraday refresh;
**scheduled / periodic revaluation**; currencies other than EUR and USD; **LLM-based sector
inference**; manual sector editing; manual "revalue" action (deferred — §30.8). Authentication /
multi-investor identity remains out of scope (ADR-002).

---

## User Scenarios & Testing *(mandatory)*

The actor is the **Investor** (glossary). With no authentication yet, "the Investor" is the single
seeded **Default Investor** (ADR-002); FD004 still scopes valuation to that Investor's Portfolios.

Stories are prioritized slices. **US1 + US2 together form the viable MVP** — a created Portfolio
gets a deterministic EUR/USD valuation that survives provider failure.

### User Story 1 — My new Portfolio is valued automatically, and creation is never lost (Priority: P1)

As an Investor, when I successfully create a Portfolio, the platform starts valuing it on its own —
and if the market-data / FX lookups fail, I still keep my Portfolio and see it in the list; the
detail just tells me the valuation isn't available.

**Why this priority**: The trigger + creation-independence guarantee is FD004's foundational
contract (§3, §13, §22 BR-001/BR-002; AC-001, AC-011; closure gate §28). Without it, FD004 could
regress FD001/FD003.

**Independent Test**: Create a Portfolio via FD001; observe a valuation is initiated (a valuation
record exists, initially `PENDING` or resolved). Separately, with the EN005 boundary stubbed to
return failures, create a Portfolio → it is persisted, appears in the FD003 Home list, opens in the
FD003 detail, and the detail shows a "valuation unavailable / pending / partial / failed" state with
**no** fabricated numbers.

**Acceptance Scenarios**:

1. **Given** a valid Portfolio, **When** creation completes and it is persisted, **Then** the
   application initiates a valuation for it. *(FD004 §3, §5, §22 BR-001; AC-001)*
2. **Given** the EN005 ports return unavailable/error for every Position, **When** the Investor
   creates a Portfolio, **Then** the Portfolio is created, stored, and visible via FD003, and its
   valuation status is `FAILED` (no Position could be valued — FR-018). *(FD004 §13, §27; AC-011)*
3. **Given** a Portfolio whose valuation failed, **When** the Investor views the Home list and the
   detail, **Then** the Portfolio is present and the detail shows an explicit valuation-state
   message and **no** zero totals or zero Position values. *(FD004 §18, §27, §28)*
4. **Given** the valuation operation, **When** it runs, **Then** it does **not** open, extend, or
   roll back the Portfolio-creation transaction — a valuation error leaves the persisted Portfolio
   and its Positions untouched. *(FD004 §14, §22 BR-002)*

---

### User Story 2 — Deterministic EUR & USD valuation of every Position and the Portfolio (Priority: P1)

As an Investor, I want each Position's market value and the Portfolio's total shown in **both EUR
and USD**, computed the same way every time from the latest available price and FX rate.

**Why this priority**: The core deliverable — the numbers the Investor came for (§1, §8, §22
BR-003/BR-005/BR-006; AC-002…AC-006). Deterministic maths is a closure gate (§28).

**Independent Test**: With deterministic stub values — `AAPL` qty 10 @ 200 USD, `SAN` qty 100 @
5 EUR, `USD→EUR = 0.80`, `EUR→USD = 1.25` — the valuation produces `AAPL` native 2000 USD / 1600
EUR, `SAN` native 500 EUR / 625 USD, Portfolio total **2100 EUR** and **2625 USD**, with no
precision loss, reproducibly. *(FD004 §26 E2E-001)*

**Acceptance Scenarios**:

1. **Given** quantity `10` and market price `230 USD`, **When** the Position is valued, **Then**
   `nativeMarketValue = 2300 USD`. *(AC-002; §8, §22 BR-006)*
2. **Given** a USD Position with native value `V` and a valid `USD→EUR` rate `r`, **When** valuation
   runs, **Then** `valueInUSD = V` and `valueInEUR = V × r`. *(AC-003; §8)*
3. **Given** an EUR Position with native value `V` and a valid `EUR→USD` rate `r`, **When** valuation
   runs, **Then** `valueInEUR = V` and `valueInUSD = V × r`. *(AC-004; §8)*
4. **Given** every Position can be valued, **When** totals are computed, **Then**
   `totalValueEUR = Σ position.valueInEUR` and `totalValueUSD = Σ position.valueInUSD`, exactly.
   *(AC-005, AC-006; §8, §22 BR-005)*
5. **Given** the same inputs, **When** valuation runs twice, **Then** it produces byte-identical
   monetary results (decimal-safe; deterministic — no `double`/`float`, no LLM). *(§8, §22 BR-003;
   §28)*
6. **Given** a Position's canonical instrument, **When** it is priced/enriched, **Then** its
   `ticker + market(MIC)` identity is unchanged — EN004 stays canonical, Finnhub only prices it.
   *(FD004 §21, §29.16)*

---

### User Story 3 — Position weights and sector allocation, in a single canonical currency (Priority: P1)

As an Investor, I want to see how much each Position contributes to the Portfolio and how the
Portfolio is spread across sectors — as percentages that add up.

**Why this priority**: The "allocation" half of FD004 (§9, §11, §22 BR-007; AC-007, AC-008).

**Independent Test**: With the E2E-001 numbers, `AAPL` weight = `1600 / 2100 ≈ 76.19 %`, `SAN`
weight = `500 / 2100 ≈ 23.81 %`; sector allocation `Technology ≈ 76.19 %`, `Financial Services ≈
23.81 %`; weights and sector percentages each sum to ~100 % within display rounding. *(FD004 §11,
§26)*

**Acceptance Scenarios**:

1. **Given** a Position's EUR value and the Portfolio's total EUR value, **When** its weight is
   computed, **Then** `weight = valueInEUR / totalValueEUR` and `weightPercentage = weight × 100`.
   *(AC-007; §9, §22 BR-007)*
2. **Given** Positions with sectors, **When** allocation is computed, **Then** Positions are grouped
   by sector, `sectorValueEUR = Σ valueInEUR` per sector, and `sectorWeight = sectorValueEUR /
   totalValueEUR`. *(AC-008; §11)*
3. **Given** a `COMPLETED` valuation, **When** the weights and sector percentages are summed, **Then**
   each total is ~100 % (only display-rounding differences allowed). *(§11)*
4. **Given** a valued Position whose instrument profile has no sector, **When** allocation is
   computed, **Then** that Position is placed in **`Unclassified`** and still contributes its EUR
   value to the total. *(AC-009; §10, §16, §22 BR-009)*
5. **Given** the sector allocation, **When** it is presented, **Then** **percentages are shown** —
   in the mandatory Allocation by Sector chart's legend (FR-046). *(FD004 §17)*

---

### User Story 4 — Partial and failed valuations are explicit; a missing price is never zero (Priority: P1)

As an Investor, if some data is missing, I want to be told exactly what could and could not be
valued — and I must never see a Position or total silently shown as `0`.

**Why this priority**: Trust and correctness (§6, §16, §18, §22 BR-010; AC-010; closure gate §28).

**Independent Test**: Stub one Position with no market price → that Position is unvalued (not `0`),
the valuation status is `PARTIAL`, and the detail names the shortfall ("market data unavailable for
1 Position"). Stub a required FX rate as unavailable → status is `PARTIAL` while the native-currency
total still stands (FR-018).

**Acceptance Scenarios**:

1. **Given** a Position with no valid market price, **When** valuation runs, **Then** the Position
   is **not** valued, is **not** assigned value `0`, and the Portfolio valuation status is
   `PARTIAL`. *(AC-010; §16 "Missing Price", §22 BR-010)*
2. **Given** price and FX are available but the instrument profile has no sector, **When** valuation
   runs, **Then** the Position **is** valued, its sector is `Unclassified`, and the valuation may
   still be `COMPLETED`. *(§16 "Missing Sector Only"; AC-009)*
3. **Given** a required EUR↔USD conversion is unavailable, **When** valuation runs, **Then** the
   status is `PARTIAL` while the native-currency total is still produced, and `FAILED` only if
   neither the EUR nor the USD total can be produced. *(§16 "Missing FX"; FR-018)*
4. **Given** any incomplete valuation, **When** the detail is shown, **Then** it displays the state
   explicitly and shows **no** fabricated zero totals or Position values. *(§18)*
5. **Given** a valuation status, **When** it is stored/exposed, **Then** it is exactly one of
   `PENDING` / `COMPLETED` / `PARTIAL` / `FAILED`. *(§6)*

---

### User Story 5 — The Portfolio detail shows the valuation, weights, sectors and state (Priority: P1)

As an Investor, when I open a Portfolio I want its total value in EUR and USD, each Position's
market price (with currency) / EUR value / USD value / weight / sector, the sector allocation
percentages (in the sector chart legend), and a clear indication of when it was valued (or that it
isn't).

**Why this priority**: FD004's user-visible payoff; extends FD003's detail (§17, §18; AC-012).

**Independent Test**: `ng test` on the extended detail component with a mocked `COMPLETED`
valuation → totals row (€ and $), the extra Position columns populated (`Market Price` carries the
native currency), the sector percentages in the Allocation by Sector chart legend, and a "Valued
at …" line. With a `PARTIAL` valuation → the extra columns are
blank for the unvalued Position and a "Partial valuation — …" line is shown. With no valuation →
"Valuation pending / unavailable" and no numeric columns.

**Acceptance Scenarios**:

1. **Given** a `COMPLETED` valuation, **When** the Investor opens the Portfolio, **Then** the detail
   shows total value in **EUR and USD**, per-Position `Market Price` (with native currency) / `Value
   in EUR` / `Value in USD` / `Portfolio Weight` / `Sector`, and the **sector allocation
   percentages** in the Allocation by Sector chart legend. *(AC-012; §17)*
2. **Given** a valuation that is `PENDING` / `PARTIAL` / `FAILED` / absent, **When** the Investor
   opens the Portfolio, **Then** the detail shows the corresponding state text (e.g. "Valuation
   pending", "Partial valuation — market data unavailable for N Positions", "Valuation unavailable")
   and **no** fabricated zeros. *(§18)*
3. **Given** a valuation, **When** the detail is shown, **Then** it also states **when** the
   valuation was calculated and carries enough market/FX freshness information to judge the result.
   *(§13 BR-013, §18)*
4. **Given** the FD003 detail behavior, **When** FD004 extends it, **Then** the existing FD003
   read-only list + detail and their `GET /api/portfolios[/{id}]` contract still work unchanged
   (additive only). *(FD003 non-regression)*
5. **Given** the Investor, **When** viewing any valuation figure, **Then** there is **no** control
   to edit a calculated value. *(§22 BR-012)*

---

### User Story 7 — Two mandatory allocation pie charts in the Portfolio detail (Priority: P1)

As an Investor, when I open a valued Portfolio I want to **see** how it is split — one pie chart by
ticker and one by sector — so I can grasp the mix at a glance, not just read a table.

**Why this priority**: The FD004 Feature Definition makes both charts **mandatory functionality**
and a **closure gate** (§17.1–§17.4, §22 BR-014…BR-017, §28, §29.16–§29.22, §31). They are the
visual half of the "allocation" in "Portfolio Valuation & Allocation".

**Independent Test**: `ng test` on the extended detail — with a mocked `COMPLETED` valuation
(E2E-001 numbers), an **Allocation by Ticker** chart renders one slice per valued Position
(`AAPL`, `SAN`) with its percentage, and an **Allocation by Sector** chart renders one slice per
sector (`Technology`, `Financial Services`) with its percentage; each chart's slice percentages
match the backend `portfolioWeight` / `sectorWeight` and sum to ~100 %. With a `PARTIAL` valuation
the charts show only the valued portion and the "Partial valuation — …" message is present. With no
EUR basis (all weights null) neither chart is drawn.

**Acceptance Scenarios**:

1. **Given** a `COMPLETED` valuation with multiple valued Positions, **When** the Investor opens the
   detail, **Then** a circular / pie chart is displayed with **one slice per ticker**, each slice's
   percentage equal to that Position's normalised-EUR `portfolioWeight`, and each slice labelled
   with at least the ticker and the percentage. *(AC-013; §17.1, §22 BR-014)*
2. **Given** a `COMPLETED` valuation with Positions in multiple sectors, **When** the Investor opens
   the detail, **Then** a circular / pie chart is displayed with **one slice per sector**, each
   slice's percentage equal to that sector's `sectorWeight`, `Unclassified` shown as its own slice
   when present, and each slice labelled with at least the sector and the percentage.
   *(AC-014; §17.2, §22 BR-015)*
3. **Given** both charts, **When** they are rendered, **Then** their percentages are **consistent
   with** the Position weights and sector allocation the valuation API returned — the frontend does
   **not** compute allocation from raw provider data — and for a `COMPLETED` valuation each chart's
   slices sum to ~100 % (display-rounding only). *(AC-015; §17.3, §22 BR-016)*
4. **Given** a `PARTIAL` valuation, **When** the detail is shown, **Then** the charts represent only
   the successfully-valued portion **and** the partial-state message makes clear the charts show
   incomplete data. *(§17.3, §22 BR-017)*
5. **Given** a valuation with no EUR basis (`totalValueEUR` absent → every `portfolioWeight` null) or
   an absent / `FAILED` / `PENDING` valuation, **When** the detail is shown, **Then** **neither**
   chart is drawn (no empty circle, no fabricated slice) — the valuation-state message stands in.
   *(§18; spec Edge Case "Zero total EUR")*
6. **Given** the charts, **When** the detail is rendered, **Then** they follow the global design
   system (dark palette, legible labels, responsive — may stack vertically on narrow screens) and
   the valuation API / `GET /api/portfolios[/{id}]` contract is **unchanged**. *(§17.4; design
   system; FD003 non-regression)*

---

### User Story 6 — Both journeys proven end to end against a controlled EN005 boundary (Priority: P2)

As a maintainer, the create→value→display journey and the provider-failure journey are proven by
Playwright against the real containerized stack with the **Finnhub boundary stubbed** — no live
Finnhub in CI.

**Why this priority**: E2E-001 and E2E-002 are **mandatory closure gates** (§25, §26, §27, §28,
§29.19–§29.20).

**Independent Test**: `./e2e.sh` grows by 2 passing specs; the existing FD001/FD002/FD003 +
`platform-smoke` specs stay green; no test reaches `finnhub.io`.

**Acceptance Scenarios**:

1. **Given** a controlled EN005 boundary returning the E2E-001 deterministic values, **When** the
   Playwright test creates the two-Position Portfolio and opens its detail, **Then** it asserts
   creation succeeded, valuation was initiated, total EUR = `2,100`, total USD = `2,625`, `AAPL` and
   `SAN` valuations and sectors are correct, weights and sector allocations are correct within
   approved rounding, **the Allocation-by-Ticker pie chart is visible with `AAPL` ≈ 76.19 % and
   `SAN` ≈ 23.81 % slices, the Allocation-by-Sector pie chart is visible with `Technology` ≈
   76.19 % and `Financial Services` ≈ 23.81 % slices, both chart slice sets are consistent with the
   detail's numbers**, and the Portfolio remains persisted. *(FD004 §26 checks 1–16)*
2. **Given** the controlled EN005 boundary returning unavailable/error responses, **When** the
   Playwright test creates a Portfolio, **Then** the Portfolio remains created and visible in the
   Home list, its detail shows **no** fabricated valuation values, and the valuation state indicates
   pending / partial / failed / unavailable per the implemented rules. *(FD004 §27)*
3. **Given** `./e2e.sh`, **When** it runs, **Then** the frontend, backend, business calculations,
   application persistence, and PostgreSQL are all real; only the Finnhub boundary is controlled;
   the run needs no outbound Internet. *(FD004 §25)*

---

### Edge Cases

- **Valuation vs creation transaction** — valuation MUST NOT participate in the create transaction;
  the trigger is **synchronous, after the create transaction commits, in the same request**
  (FD004 §30.1; FR-004) — a valuation error is caught and the create still succeeds.
- **`PENDING` status** — valuation is synchronous in the create request, so a persisted valuation
  normally lands `COMPLETED` / `PARTIAL` / `FAILED`. `PENDING` stays a valid stored value for the
  brief window before the synchronous valuation writes its result (and for a future manual/async
  revaluation); the detail shows "Valuation pending" for it.
- **Re-valuation idempotency** — running valuation again for the same Portfolio MUST replace the
  latest snapshot, not accumulate Position/sector rows (§15, §22 BR-011).
- **All Positions unvalued** — if no Position can be priced, the valuation is `FAILED` and the
  detail shows "Valuation unavailable"; the Portfolio is untouched.
- **Zero total EUR with a computable weight** — if `totalValueEUR` is `0` (e.g. only unvalued
  Positions), Position/sector weights are undefined and MUST NOT be shown as `0 %` or `NaN` — the
  status is `PARTIAL`/`FAILED` and weights are simply absent.
- **Instrument not resolvable to a Finnhub symbol** — EN005 returns `InstrumentNotResolved`; that
  Position is treated as "missing price" (unvalued, not zero, → `PARTIAL`).
- **FX for a same-currency Portfolio** — an all-EUR (or all-USD) Portfolio still needs one FX rate
  to express the *other* total; if that rate is unavailable the native-currency total is still
  shown and the cross-currency total is absent (→ `PARTIAL`).
- **Sector string, not a taxonomy** — the sector value from EN005 is the provider classification
  string (e.g. `"Technology"`); FD004 **displays it directly**, `Unclassified` when absent — no
  canonical sector taxonomy (FD004 §30.7; FR-014).
- **Display precision** — money and percentages are displayed to **2 decimal places** (FD004
  §30.3–§30.4; FR-031); full precision is retained in storage/calculation, so E2E assertions compare
  stored values exactly and displayed values to 2 dp.
- **Finnhub payloads** — no Finnhub DTO, field name, endpoint, or key may appear in FD004 code or
  the public API (FD004 §19, §20).

---

## Requirements *(mandatory)*

> Every FR below traces to an FD004 section / BR / AC / resolved §30 decision (Clarifications, above).

### Trigger & creation independence

- **FR-001**: A **successfully persisted** Portfolio (FD001) MUST initiate exactly one Portfolio
  valuation operation. *(FD004 §3, §5, §22 BR-001; AC-001)*
- **FR-002**: The valuation operation MUST NOT be part of the Portfolio-creation transaction and
  MUST NOT be able to roll back, delete, or invalidate the persisted Portfolio or its Positions —
  regardless of valuation outcome. *(FD004 §3, §13, §14, §22 BR-002; AC-011; closure gate §28)*
- **FR-003**: The post-persistence trigger mechanism MUST NOT introduce Kafka or another external
  message broker, MUST NOT introduce a scheduler, and MUST NOT perform continuous / intraday /
  periodic revaluation. *(FD004 §4, §14, §30 guardrail)*
- **FR-004**: The valuation MUST run **synchronously, immediately after the Portfolio-creation
  transaction commits, within the same `POST /api/portfolios` request** (FD004 §30.1). A valuation
  error MUST be caught so the create request still returns successfully (FR-002). No background
  worker, no message, no scheduler.

### Deterministic valuation calculations

- **FR-005**: For each Position, `nativeMarketValue = quantity × marketPrice`, using the **latest
  available** market price from EN005 `MarketDataPort`. *(FD004 §8, §22 BR-006; AC-002)*
- **FR-006**: For a **USD** Position: `valueInUSD = nativeMarketValue`;
  `valueInEUR = nativeMarketValue × (USD→EUR)`. For an **EUR** Position:
  `valueInEUR = nativeMarketValue`; `valueInUSD = nativeMarketValue × (EUR→USD)`. FX rates come from
  EN005 `FxRatePort`. *(FD004 §8; AC-003, AC-004)*
- **FR-007**: `totalValueEUR = Σ position.valueInEUR` and `totalValueUSD = Σ position.valueInUSD`
  over the Positions that could be valued. *(FD004 §8, §22 BR-005; AC-005, AC-006)*
- **FR-008**: All monetary, currency-conversion, and percentage calculations MUST be
  **deterministic** and use decimal-safe numeric types. Binary floating-point MUST NOT be used for
  any monetary or rate value. **No LLM** may calculate or infer a monetary value. *(FD004 §8, §22
  BR-003; closure gate §28)*
- **FR-009**: Each Position's weight MUST be `valueInEUR / totalValueEUR`
  (`weightPercentage = weight × 100`) — **EUR is the canonical allocation currency**. *(FD004 §9,
  §22 BR-007; AC-007)*

### Sector classification & allocation

- **FR-010**: Each Position's sector MUST be obtained through EN005 `InstrumentProfilePort`. **No
  LLM** and no heuristic may infer a missing sector. *(FD004 §10, §22 BR-008; closure gate §28)*
- **FR-011**: A Position that **can be valued** but whose profile has **no sector** MUST be
  classified as **`Unclassified`** and still contribute its EUR value to totals and allocation.
  *(FD004 §10, §16, §22 BR-009; AC-009)*
- **FR-012**: Sector allocation MUST group valued Positions by sector, compute
  `sectorValueEUR = Σ valueInEUR` per sector, and `sectorWeight = sectorValueEUR / totalValueEUR`.
  *(FD004 §11; AC-008)*
- **FR-013**: For a `COMPLETED` valuation, Position weights and sector weights MUST each sum to
  ~100 %, allowing only display-rounding differences. *(FD004 §11)*
- **FR-014**: The sector value MUST be the **EN005 provider classification string, displayed
  directly** (e.g. `"Technology"`); `Unclassified` when absent. FD004 MUST NOT introduce a canonical
  sector taxonomy (FD004 §30.7).

### Valuation status & partial data

- **FR-015**: Every Portfolio valuation MUST carry an explicit status — exactly one of `PENDING`,
  `COMPLETED`, `PARTIAL`, `FAILED`. A Portfolio remains valid regardless of valuation status.
  *(FD004 §6)*
- **FR-016**: `COMPLETED` MUST mean all required monetary inputs (price + necessary FX) were
  available for **every** Position; a missing **sector only** does NOT prevent `COMPLETED`.
  *(FD004 §6, §16)*
- **FR-017**: A Position with **no valid market price** MUST be recorded as **unvalued** — it MUST
  NOT be assigned value `0` — and the Portfolio valuation status MUST be `PARTIAL` (or `FAILED` if
  nothing meaningful remains). *(FD004 §16, §22 BR-010; AC-010; closure gate §28)*
- **FR-018**: The valuation status MUST be **`FAILED` only when no Position can be valued, OR when
  neither the EUR total nor the USD total can be produced at all**; **otherwise `PARTIAL`**. Missing
  FX for the cross-currency total → `PARTIAL` (the native-currency total still stands). *(FD004 §16,
  §30.2)*
- **FR-019**: The UI MUST NOT display fabricated zero values (totals, Position values, weights, or
  percentages) when the underlying valuation input is missing. *(FD004 §18, §28)*

### Persistence

- **FR-020**: FD004 MUST persist the **latest** Portfolio valuation snapshot per Portfolio only —
  status, `calculatedAt`, `totalValueEUR`, `totalValueUSD`, market-data freshness (`marketDataAsOf`),
  FX freshness (`fxDataAsOf`), the per-Position valuations, and the per-sector allocations.
  **Historical valuation retention is NOT required and MUST NOT be added.** *(FD004 §12, §22 BR-011,
  BR-013)*
- **FR-021**: Re-running valuation for a Portfolio MUST **replace/update** the latest snapshot —
  it MUST NOT create duplicate Position-valuation or sector-allocation state. *(FD004 §15, §22
  BR-011)*
- **FR-022**: Any new database schema MUST use the project's approved migration mechanism (Flyway
  forward migration), be owned by the `portfolio` module (or a clearly-owned valuation area), and
  MUST NOT modify FD001/EN004 tables. Hibernate MUST NOT own the schema. *(ADR-003; constitution
  VII; FD004 §30.6 — exact schema is a planning decision)*
- **FR-023**: Valuation MUST reuse FD001/FD003 persisted `Portfolio` / `Position` data and EN004
  canonical identity (`ticker + market(MIC)`) as the source of what to value; it MUST NOT duplicate
  or restate Position business data. *(FD004 §21, §29.16)*

### API & contract

- **FR-024**: FD004 MUST provide an application-facing way to obtain a Portfolio's valuation —
  status, `calculatedAt`, total EUR, total USD, per-Position valuation (price, native/EUR/USD value,
  weight, sector, price-observed-at), and sector allocation. The response MUST carry everything the
  **two allocation charts** need: per valued Position `ticker` + normalised-EUR `portfolioWeight`
  (ticker chart), and per `sectors[]` entry `sector` + `sectorWeight` (sector chart). Contract-first
  (OpenAPI 3.0.3, RFC 9457 errors, a contract test), business language. *(FD004 §19; constitution
  VIII)* — **the current `PortfolioValuation` response already satisfies this; no schema change.**
- **FR-025**: The valuation is exposed as a **dedicated operation**
  `GET /api/portfolios/{portfolioId}/valuation` (FD004 §30.5). FD003's existing
  `GET /api/portfolios[/{portfolioId}]` contract and `Portfolio` schema MUST remain **unchanged**.
  A non-UUID id → `400`; an unknown / non-current-investor portfolio id → `404`
  `application/problem+json`. A portfolio with no valuation snapshot yet → an explicit `PENDING`
  result (exact representation finalised in planning, within this decision).
- **FR-026**: No Finnhub DTO, field name, endpoint URL, API key, or authentication detail may
  appear in the FD004 public API or in FD004 domain/business code. FD004 consumes EN005 **only**
  through `MarketDataPort` / `InstrumentProfilePort` / `FxRatePort`. *(FD004 §19, §20; AR-025)*
- **FR-027**: Provider-neutral: no provider-specific or persistence-shaped structure (entity names,
  JPA/column names, Finnhub payloads) leaks through the valuation API. *(FD004 §19; constitution
  VIII)*

### UX — Portfolio detail extension (FD003)

- **FR-028**: The FD003 Portfolio detail MUST be extended (additively) with: the Portfolio **total
  value in EUR and in USD**; the sector allocation as **percentages** — surfaced through the
  **Allocation by Sector** chart's legend (FR-046), not a separate standalone list; the **two
  mandatory allocation pie charts** (FR-044); and a clear **valuation-state** line.
  *(FD004 §17, §18; AC-012)*

- **FR-044**: When valuation data is available, the Portfolio detail MUST display **two mandatory
  circular / pie charts** — **Allocation by Ticker** and **Allocation by Sector** — as FD004
  functionality, not an optional enhancement. A missing chart, or a chart inconsistent with the
  valuation data, is a **closure-gate failure**. *(FD004 §17.1–§17.4, §22 BR-014/BR-015, §28,
  §29.16/§29.18/§29.20)*
- **FR-045**: The **Allocation by Ticker** chart MUST render **one slice per valued Position**; each
  slice's magnitude MUST be that Position's normalised-EUR weight (`portfolioWeight = valueInEUR /
  totalValueEUR`); each slice MUST be labelled with at least the **ticker** and its **percentage**
  (2 dp). The chart MUST NOT use quantity, share count, or purchase price as the allocation measure.
  *(FD004 §17.1, §22 BR-014; AC-013)*
- **FR-046**: The **Allocation by Sector** chart MUST render **one slice per sector** present in the
  valued Positions (including `Unclassified` when applicable); each slice's magnitude MUST be that
  sector's `sectorWeight = sectorValueEUR / totalValueEUR`; each slice MUST be labelled with at
  least the **sector** and its **percentage** (2 dp). *(FD004 §17.2, §22 BR-015; AC-014)*
- **FR-047**: Both charts MUST be driven **only** by the deterministic valuation/allocation results
  the FD004 valuation API returns (`positions[].portfolioWeight`, `sectors[].sectorWeight`). The
  frontend MUST NOT compute financial allocation from raw external-provider payloads or from the
  Position table's own numbers. For a `COMPLETED` valuation, each chart's slice percentages MUST sum
  to ~100 % (display-rounding only) and MUST be consistent with the Position weights / sector
  allocation shown elsewhere in the detail. *(FD004 §17.3, §22 BR-016; AC-015)*
- **FR-048**: For a `PARTIAL` valuation, the charts MUST represent **only** the successfully-valued
  portion **and** the valuation-state message MUST make clear the charts show incomplete data. When
  there is no EUR basis (`totalValueEUR` absent / every `portfolioWeight` null) or the valuation is
  absent / `PENDING` / `FAILED`, **neither** chart MUST be drawn — no empty circle, no fabricated
  slice; the valuation-state message stands in its place. *(FD004 §17.3, §18, §22 BR-017)*
- **FR-049**: The charts follow the global design system (`product/ux/design-system.md`) — dark
  palette, legible slice/legend labels, keyboard/contrast accessible, responsive (may stack
  vertically on narrow screens). No new frontend charting dependency is required or permitted
  without approval — a self-contained rendering is the governed default. *(design system; FD004
  §17.4; technology policy)*
- **FR-029**: The detail's Position table MAY be extended with `Market Price` (shown **with the
  Position's native currency**, e.g. `200.00 USD`), `Value in EUR`, `Value in USD`, `Portfolio
  Weight` (each shown **only when valuation is available**), and `Sector` (shown **only when profile
  is available**). Ticker / market / quantity / currency remain always shown (FD003). The native
  `Market Value` (`quantity × price`) is **not** a column — it is redundant with `Value in EUR` /
  `Value in USD` — and there is **no** standalone sector-percentage list under the table (the
  Allocation by Sector chart legend carries that — FR-028). *(FD004 §17)*
- **FR-030**: The valuation-state message MUST distinguish at least: pending, valued-at-`<time>`,
  partial (naming how many Positions/inputs are missing), and unavailable. It MUST NOT show
  fabricated zeros. *(FD004 §18)*
- **FR-031**: All new UI follows the global design system (`product/ux/design-system.md`) — dark
  compact tables, right-aligned numeric columns, consistent decimal/currency formatting, an explicit
  state indicator, keyboard/contrast accessibility. Percentages/currency use the approved display
  precision of **2 decimal places** for money and percentages (FD004 §30.3–§30.4). *(design system;
  FD004 §17)*
- **FR-032**: The valuation view is **read-only** — no control edits a calculated value. *(FD004
  §22 BR-012)*

### EN005 / EN004 boundary & non-regression

- **FR-033**: FD004 MUST NOT change EN005 (`marketdata` module) behavior, its ports, or its
  contracts; it consumes them as-is. If a port shape is insufficient, that is an EN005 change raised
  for approval, not an FD004 change. *(FD004 §20)*
- **FR-034**: FD004 MUST NOT regress FD001 (create), FD002 (`INSTRUMENT_NOT_IN_CATALOG`), FD003
  (list + read-only detail + `GET /api/portfolios[/{id}]`), EN004 (catalog), or EN005 — their
  automated suites and E2Es MUST stay green. *(consumed capabilities)*
- **FR-035**: Deterministic valuation logic MUST live in `business` behind `domain` ports
  (Hexagonal); `domain`/`business` MUST NOT depend on Spring, JPA, HTTP, or any Finnhub type;
  ArchUnit MUST enforce this. *(ADR-003; AR-001/002; constitution VI)*

### Testing & closure gates

- **FR-036**: Automated business tests MUST use **deterministic** market/profile/FX values (stubbed
  EN005 ports) and MUST NOT depend on live Finnhub. Coverage: post-creation trigger; deterministic
  Position values; USD→EUR; EUR→USD; totals; weights; sector grouping/percentages; `Unclassified`;
  missing price; missing FX; EN005 failure; **Portfolio creation surviving valuation failure**;
  idempotent revaluation; latest-snapshot persistence; the detail API/UI; **the ticker-allocation
  chart rendering; the sector-allocation chart rendering; chart percentages matching the backend
  Position weights / sector allocation; `Unclassified` shown correctly in the sector chart; charts
  suppressed for a no-EUR-basis / absent / FAILED / PENDING valuation**. *(FD004 §24)*
- **FR-037**: Persistence/integration tests against PostgreSQL MUST use Testcontainers per project
  policy. *(constitution VII; testing-strategy)*
- **FR-038**: FD004 MUST deliver **E2E-001** (create the deterministic two-Position EUR+USD
  Portfolio → valuation initiated → assert totals, per-Position valuation, sectors, weights, sector
  allocation within approved rounding → **the Allocation-by-Ticker pie chart is visible with the
  expected ticker slices + percentages** → **the Allocation-by-Sector pie chart is visible with the
  expected sector slices + percentages** → **chart values are consistent with the detail's
  deterministic numbers** → Portfolio persisted) and **E2E-002** (controlled EN005 failure →
  Portfolio created and visible → detail shows no fabricated values → explicit valuation state) —
  both against the real containerized stack with **the external market-data boundary controlled**
  (Finnhub quote/profile stub + the FX-provider stub), no live provider in CI. *(FD004 §25, §26,
  §27; §26 checks 11–15)*
- **FR-039**: FD004 MUST NOT be closed if: calculations are not deterministic; CI E2E requires a
  live market-data provider; E2E-001 or E2E-002 is missing/failing; Portfolio creation is rolled
  back because valuation fails; missing price/FX is represented as a fabricated zero; **the
  ticker-allocation pie chart is missing or inconsistent with Position weights; or the
  sector-allocation pie chart is missing or inconsistent with the sector allocation**. *(FD004 §28)*

### Scope guardrails

- **FR-040**: FD004 MUST NOT add historical valuation/performance, P&L vs purchase price,
  benchmarks, dividends/taxes/fees, stop-loss, risk scoring, AI recommendations, news, rebalancing,
  trading, continuous/intraday/scheduled revaluation, a third currency, LLM sector inference, manual
  sector editing, or a manual "revalue" action. *(FD004 §4, §30 guardrail)*
- **FR-041**: FD004 MUST NOT introduce a new deployable service (ADR-001), a message broker, a
  scheduler, a new persistence technology, a new external provider, or a **new frontend charting
  library / npm dependency**. Any material architecture decision surfaced in planning MUST be raised
  for human approval (and an ADR where warranted). *(ADR-001; constitution IV; AR-048; technology
  policy)*
- **FR-042**: FD004 MUST NOT silently edit a human-governed `product/` document. The §30 open
  questions and the §31 approval are **FD004 owner** actions; this spec records them, it does not
  resolve them. *(constitution I, IV)*

---

## Key Entities *(include if feature involves data)*

- **Portfolio Valuation (snapshot)** — the latest valuation of one Portfolio. Attributes:
  `portfolioId`, `status` (`PENDING`/`COMPLETED`/`PARTIAL`/`FAILED`), `calculatedAt`,
  `totalValueEUR`, `totalValueUSD`, `marketDataAsOf` (market-price freshness), `fxDataAsOf` (FX
  freshness), the collection of Position Valuations, the collection of Sector Allocations. Exactly
  one per Portfolio (latest only — replaced on re-valuation). *(FD004 §12)*
- **Position Valuation** — the valuation of one Position within a snapshot. Attributes: the
  Position reference (`ticker`, `market`), `quantity`, `marketPrice`, `nativeCurrency`,
  `nativeMarketValue`, `valueInEUR`, `valueInUSD`, `portfolioWeight`, `sector` (a classification
  string or `Unclassified`), `priceObservedAt`. A Position that could not be priced appears as
  **unvalued** (monetary fields absent — never `0`). *(FD004 §7)*
- **Sector Allocation** — one row per sector present in the valued Positions (including
  `Unclassified`). Attributes: `sector`, `sectorValueEUR`, `sectorWeight` (fraction / percentage).
  *(FD004 §11)*
- **Valuation freshness / source** — `calculatedAt` plus the market-data and FX "as of" timestamps
  carried from EN005's `observedAt` / `source` metadata, so the Investor can judge when the result
  was produced. *(FD004 §12, §22 BR-013)*
- **Allocation chart slice** *(view-model only — not persisted, not a new API field)* — a `{ label,
  fraction }` pair. The **ticker chart** derives its slices from the valuation snapshot's valued
  `positions[]` (`label = ticker`, `fraction = portfolioWeight`); the **sector chart** from
  `sectors[]` (`label = sector`, `fraction = sectorWeight`). Built entirely from the FD004 valuation
  response the detail already fetches. *(FD004 §17.1–§17.3)*

*(No change to the `Portfolio` / `Position` entities or to the `PortfolioValuation` API response —
FD004 reads them; the charts consume the response as-is.)*

---

## Traceability to Feature-Definition Acceptance Criteria

| FD004 AC | Covered by |
|---|---|
| AC-001 Automatic valuation initiated after creation | US1 (AS1); FR-001 |
| AC-002 Position native market value = qty × price | US2 (AS1); FR-005 |
| AC-003 USD → EUR value | US2 (AS2); FR-006 |
| AC-004 EUR → USD value | US2 (AS3); FR-006 |
| AC-005 Total EUR = Σ Position EUR | US2 (AS4); FR-007 |
| AC-006 Total USD = Σ Position USD | US2 (AS4); FR-007 |
| AC-007 Position weight from EUR values | US3 (AS1); FR-009 |
| AC-008 Sector grouping + percentages | US3 (AS2); FR-012 |
| AC-009 Missing sector → `Unclassified`, still valued/allocated | US3 (AS4), US4 (AS2); FR-011 |
| AC-010 Missing price → no fabricated zero, incomplete valuation | US4 (AS1); FR-017, FR-019 |
| AC-011 Valuation failure does not delete the Portfolio | US1 (AS2, AS3, AS4); FR-002 |
| AC-012 Completed valuation shown in Portfolio detail | US5 (AS1); FR-028, FR-029 |
| AC-013 Ticker-allocation pie chart (one slice / ticker, normalised-EUR weight) | US7 (AS1); FR-044, FR-045 |
| AC-014 Sector-allocation pie chart (one slice / sector, `Unclassified` slice) | US7 (AS2); FR-044, FR-046 |
| AC-015 Chart percentages consistent with backend weights / allocation | US7 (AS3); FR-047 |
| §26 E2E-001 (mandatory, incl. checks 11–15 — both charts) | US6 (AS1); FR-038 |
| §27 E2E-002 (mandatory) | US6 (AS2); FR-038 |
| §28 Closure gates (incl. both charts present + consistent) | FR-002, FR-008, FR-017, FR-019, FR-038, FR-039, FR-044, FR-047 |

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For the E2E-001 deterministic inputs, the persisted valuation has **total EUR =
  2100.00** and **total USD = 2625.00**, `AAPL` `valueInEUR = 1600.00` / `valueInUSD = 2000.00`,
  `SAN` `valueInEUR = 500.00` / `valueInUSD = 625.00` — **exactly**, with no precision loss —
  verified by an integration test and the E2E.
- **SC-002**: Running valuation for the same Portfolio **2 (and 3) times** produces the **same**
  monetary results and **zero** additional Position-valuation or sector-allocation rows (latest
  snapshot replaced) — verified by an idempotency integration test.
- **SC-003**: For the E2E-001 inputs, `AAPL` weight and `Technology` allocation are
  **76.19 %** and `SAN` weight and `Financial Services` allocation are **23.81 %** (to the approved
  display precision), and Position weights and sector percentages each sum to **100 % ± the approved
  rounding tolerance** — verified by test + E2E.
- **SC-004**: **100 %** of valuations where at least one Position has no market price are recorded
  with status `PARTIAL` (or `FAILED`), the unvalued Position has **no** monetary value (not `0`),
  and the detail names the shortfall — verified by tests covering missing price, missing FX, and a
  full EN005 outage.
- **SC-005**: After a full EN005 outage during creation, **100 %** of created Portfolios remain
  persisted and visible in the FD003 Home list and detail, with **zero** fabricated valuation
  numbers shown — verified by an integration test and E2E-002.
- **SC-006**: **Zero** occurrences of `double` / `float` for a monetary or rate value in FD004
  `domain` / `business`; **zero** LLM calls on the valuation or sector path — verified by ArchUnit
  and code review.
- **SC-007**: **Zero** Finnhub types (DTOs, field names, endpoint strings, key) appear in FD004
  `domain` / `business` or the valuation public API — verified by ArchUnit and OpenAPI inspection.
- **SC-008**: The valuation operation performs **zero** writes to, and **zero** rollbacks of, the
  `portfolio` / `position` tables — verified by an integration test asserting FD001 row state is
  unchanged across a create + (failing) valuation.
- **SC-009**: `./mvnw verify` (unit + Testcontainers integration + contract + ArchUnit + ≥ 90 %
  line & branch coverage) and `ng test` pass; the FD001/FD002/FD003/EN004/EN005 suites and the
  coverage gate stay green.
- **SC-010**: `./e2e.sh` runs **E2E-001 and E2E-002** green against the real containerized stack
  with the Finnhub boundary controlled and **no outbound Internet**; the existing E2E specs stay
  green.
- **SC-011**: The Portfolio detail renders the valuation, per-Position columns, sector percentages,
  **both allocation pie charts**, and the state message within **2 s** for a realistic Portfolio
  (≤ ~50 Positions) — verified by the E2E walkthrough.
- **SC-012**: `git diff` scope review shows **no** historical-valuation schema, **no** scheduler /
  broker / new deployable / new provider / new persistence technology, **no** third currency, **no**
  LLM, **no new frontend charting dependency** (`package.json` unchanged), **no** change to
  FD001/EN004 tables, **no** change to the `PortfolioValuation` API response or FD003's existing
  contract semantics, and **no** unapproved `product/` edit.
- **SC-013**: **100 %** of FD004 `AC-001…AC-015` and the two mandatory E2E gates have associated
  executable evidence.
- **SC-014**: For the E2E-001 `COMPLETED` valuation, the **Allocation by Ticker** chart shows
  exactly two slices — `AAPL` `76.19 %` and `SAN` `23.81 %` — and the **Allocation by Sector** chart
  shows exactly two slices — `Technology` `76.19 %` and `Financial Services` `23.81 %`; each chart's
  slice percentages sum to `100 %` (±0.01 display tolerance) and match the Position-weight / sector
  numbers shown elsewhere in the detail — verified by `ng test` and E2E-001.
- **SC-015**: For a `PARTIAL` valuation the charts render only the valued portion and the
  partial-state message is present; for a no-EUR-basis / absent / `FAILED` / `PENDING` valuation
  **neither** chart element is present in the DOM (no empty `<svg>`/circle, no `0 %` slice) —
  verified by `ng test`.

---

## Assumptions

> All eight FD004 §30 questions are **resolved** (FD004 §30, signed §31, 2026-09-04) — see
> Clarifications. The assumptions below fill only *non-material, planning-level* gaps.

- **A1 — Consumed capabilities are in place**: FD001, FD003, EN004, EN005 are implemented and
  verified (EN005 `MarketDataPort` / `InstrumentProfilePort` / `FxRatePort` return provider-neutral
  `MarketPrice` / `InstrumentProfile` / `FxRate` with `observedAt` / `source`, and a neutral
  exception set; a blank Finnhub key yields `MarketDataNotConfigured`).
- **A2 — Single deployable**: FD004 is implemented inside the existing `core-service` (ADR-001) as
  additions to the `portfolio` module (or a clearly-owned valuation sub-area). A new persistence
  schema + the synchronous trigger mechanism are raised in planning; **no new ADR is anticipated**
  (no new deployable / broker / scheduler / persistence technology).
- **A3 — Default Investor**: valuation is scoped to the single seeded Default Investor (ADR-002);
  multi-investor scoping is a drop-in later.
- **A4 — Trigger is an in-process post-commit hook** *(→ FR-003, FR-004; FD004 §30.1)*: an
  application-level invocation right after the FD001 create transaction commits, in the same
  request; exact mechanism (e.g. a `TransactionSynchronization` afterCommit, or an explicit call in
  the create use case after `save`) is a planning detail. No Kafka, no scheduler, no background
  worker.
- **A5 — `COMPLETED` requires price + necessary FX for every Position** (FD004 §16); a missing
  sector alone keeps `COMPLETED`. A same-currency Portfolio still needs one FX rate for the *other*
  total.
- **A6 — Persistence** *(→ FR-020/FR-022; FD004 §30.6)*: a new Flyway forward migration adds a
  latest-only valuation structure (`portfolio_valuation` + `position_valuation` +
  `sector_allocation`) owned by the `portfolio` module; FD001/EN004 tables untouched; exact columns
  are a planning decision.
- **A7 — "no valuation yet" representation** *(→ FR-025)*: `GET /api/portfolios/{id}/valuation` for
  a portfolio whose synchronous valuation somehow has not produced a snapshot returns an explicit
  `PENDING` result (not `404`, not a fabricated `COMPLETED`); confirmed in planning.
- **A8 — Deterministic decimal type**: `BigDecimal` end-to-end for prices, values, rates, and
  weights (matches FD001/EN005). Any calculation rounding policy is set in planning within FD004's
  "display-rounding only" constraint (§11); display uses 2 dp (FD004 §30.3–§30.4).
- **A9 — OpenAPI 3.0.3** for the new `GET /api/portfolios/{portfolioId}/valuation` operation
  (matches the platform's `swagger-request-validator`); RFC 9457 `application/problem+json` errors.
- **A10 — E2E stub mechanism**: the two mandatory E2E specs run against a **controlled EN005
  boundary** — the exact mechanism (a test profile / bean override that swaps the Finnhub adapter
  for a deterministic fake, or a WireMock-style Finnhub stub) is a planning decision; it must keep
  frontend / backend / calculations / persistence / PostgreSQL real (FD004 §25).
- **A11 — Manual "revalue" action is out of scope** (FD004 §30.8) — deferred to a future feature.
- **A12 — Chart rendering** *(→ FR-044…FR-049; FD004 §17.4)*: the two pie charts are rendered
  **self-contained** (inline SVG arc slices or an equivalent CSS technique) with **no new frontend
  dependency** — consistent with the platform's "no speculative dependency" policy and the original
  FD004 "no new dependency" constraint. A donut vs. solid pie, exact legend placement, and colour
  ramp are design-system details settled in planning. One slice per valued Position / per sector,
  **no** "Other" grouping (FD004 portfolios are ≤ ~50 Positions — SC-011).
- **A13 — Charts are additive to the FD003/FD004 detail** — the existing `portfolio-detail` page
  and the `PortfolioValuation` API response are the only inputs; the change is frontend + E2E +
  frontend tests. **No backend, domain, business, persistence, or API/contract change** (the
  response already carries `positions[].portfolioWeight` and `sectors[].sectorWeight`).
- **A14 — E2E market-data boundary** *(→ FR-038; supersedes A10's Finnhub-only framing)*: since
  EN005 Revision 2 the FX provider is **Frankfurter** (not Finnhub). The controlled E2E boundary
  must therefore stub **both** the Finnhub quote/profile endpoints **and** the Frankfurter
  `/v1/latest` endpoint (one combined stub service, or the existing stub extended). Wiring the
  Frankfurter stub route + `FRANKFURTER_BASE_URL` is a **prerequisite task** for E2E-001 to reach
  `COMPLETED` — planning must include it. It also advances EN005 Revision 2 checkpoint C4.

## Dependencies

- **FD001 — Create Investment Portfolio**: the trigger point and the persisted `Portfolio` /
  `Position` data FD004 values; FD004 must not roll back or regress it.
- **FD003 — List and View Portfolio Details**: the Portfolio detail FD004 extends and the
  `GET /api/portfolios[/{id}]` contract FD004 must keep backward-compatible.
- **EN004 — Financial Instrument Reference Data**: canonical `ticker + market(MIC)` identity.
- **EN005 — Establish External Market Data Capabilities**: the **only** way FD004 obtains external
  price / profile / FX data — three provider-neutral ports + neutral read models + neutral
  exceptions. Providers (Finnhub for price/profile, Frankfurter for FX since Revision 2) stay
  entirely hidden behind them. FD004's charts require no EN005 change.
- **EN002 — Containerized E2E**: the containerized platform and `./e2e.sh` the two mandatory E2E
  specs run in, with the **market-data boundary** (Finnhub quote/profile + Frankfurter FX)
  controlled.
- **ADR-001 / ADR-002 / ADR-003**: governing architecture — unchanged; FD004 implements within
  them (a new schema + the trigger mechanism are raised in planning).
- **Governance**: `.specify/memory/constitution.md`; `product/architecture/{architecture,
  architecture-rules,technology-policy}.md`; `product/engineering/{development-rules,
  testing-strategy,definition-of-done}.md`; `product/ux/design-system.md`;
  `product/definition/global/` (glossary, domains, information model — Portfolio Management,
  Market Intelligence / Market Data).

## Resolved Product Decisions (FD004 §30 — signed 2026-09-04)

| FD004 §30 | Decision | Spec reference |
|---|---|---|
| §30.1 Execution model | **Synchronous, after the create transaction commits, same request** | FR-004; Clarifications |
| §30.2 `PARTIAL` vs `FAILED` | **`FAILED` only when no Position valuable OR no total producible; else `PARTIAL`** | FR-018; Clarifications |
| §30.3 Monetary display precision | **2 decimal places** (full precision stored) | FR-031; A8 |
| §30.4 Percentage display precision | **2 decimal places** (exact fractions stored) | FR-031; A8 |
| §30.5 API shape | **Dedicated `GET /api/portfolios/{portfolioId}/valuation`**; FD003 contract unchanged | FR-024, FR-025; Clarifications |
| §30.6 Persistence schema | **Latest-only** valuation structure, `portfolio` module owned, new Flyway migration; columns = planning | FR-020, FR-022; A6 |
| §30.7 Sector representation | **Provider classification string shown directly**; `Unclassified` absent; no taxonomy | FR-014; Clarifications |
| §30.8 Manual revalue action | **Out of scope for FD004** (deferred) | A11; Out of Scope |

Planning-level decisions still open (technical, non-material to FD004 intent — resolved in
`research.md` / `plan.md`): the exact post-commit trigger primitive; the valuation schema columns;
the calculation rounding policy (within "display-rounding only"); the E2E EN005 stub mechanism;
the "no snapshot yet" representation on the valuation endpoint.

## Out of Scope

Carried from FD004 §4 "Out of Scope" + §28 + §30 guardrail:

- Historical valuation, historical performance, profit/loss vs purchase price, benchmark
  comparison, dividends / taxes / fees, stop-loss, risk scoring.
- AI recommendations, news analysis, **LLM-based sector inference**, manual sector editing.
- Portfolio rebalancing, automatic trading.
- Continuous / intraday refresh, **scheduled / periodic revaluation**, a manual "revalue" action
  (deferred — OD-6).
- Currencies other than EUR and USD.
- A new deployable service, a message broker (Kafka), a scheduler, a new persistence technology, a
  new external data provider, a canonical sector taxonomy (unless separately approved — OD-4), a
  **new frontend charting library / npm dependency**.
- Any change to EN005's ports/behavior, to EN004's catalog, to the FD001 Position identity or
  tables, to the **`PortfolioValuation` API response**, or to FD003's existing contract semantics.
- Chart interactivity beyond labels/legend (drill-down, tooltips with extra data, animation),
  chart types other than the two mandated pie/circular charts, a "top N + Other" slice grouping —
  none required by FD004 §17.
- Editing or reinterpreting human-governed `product/` documents (FD004 §30/§31 were updated as a
  human-directed decision record).
