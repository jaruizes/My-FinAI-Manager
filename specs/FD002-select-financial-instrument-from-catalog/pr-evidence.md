# FD002 — Select Financial Instrument from Catalog · PR Evidence

## What requirement does this implement?

**FD002 — Select Financial Instrument from Catalog** (Feature Definition, Approved 2026-09-03).
Replaces the free-text Ticker / Market / Currency inputs in the FD001 **Add Position** interaction
with controlled selection of a catalogued Financial Instrument listing (delivered by EN004), and
adds a **server-side guarantee** that a Position can only reference an active EUR/USD catalogued
listing.

## Which specification / tasks does it trace to?

- Feature: `product/definition/features/FD002-select-financial-instrument-from-catalog/FD002-select-financial-instrument-from-catalog.md` (§19 signed by jaruiz 2026-09-03; §18 Open Questions resolved).
- Consumed capability: `EN004 — Establish Financial Instrument Reference Data` (`GET /api/financial-instruments`).
- Governing: ADR-001 (one `core-service` — unchanged), ADR-003, FD001 `POST /api/portfolios`, EN002 containerized E2E, `product/ux/design-system.md`, **`architecture-rules.md` AR-062** (added for this feature).
- SDD artifacts: `specs/FD002-…/` — `spec.md` (30 FR, 12 SC, US1–US4), `plan.md` (Constitution Check PASS, OD-FD002-1…5 confirmed), `research.md` (D1–D12), `data-model.md`, `contracts/` (portfolio-validation.delta, instrument-catalog-port, add-position-ui-contract), `quickstart.md` (A–G), `tasks.md` (T001–T034).

## What changed?

| Area | Change |
|---|---|
| Frontend — search | **NEW** `instrument.models.ts` (`CatalogListing`, `InstrumentSearchState`); **NEW** `instrument-search.service.ts` → `GET /api/financial-instruments?query=` (trim, blank-query guard, error → `null`). |
| Frontend — Add Position | `add-position.dialog.ts` reworked: the three text inputs become an **ARIA combobox** (search input + `role="listbox"` results, keyboard nav via `aria-activedescendant`, visible focus) + `selected` state + a **constrained Market/Currency selector** shown only when the chosen instrument has ≥ 2 catalogued listings (grouped by normalized name). Six states: idle / searching / results / no-results / selected / error (+ Retry). Quantity / date / price unchanged; price hint reflects the selected currency. |
| Frontend — models / list | `PositionDraft` gains a **non-serialized** `instrumentName?` (for the draft list + edit restore); `toRequestBody` still cherry-picks the wire fields so `POST /api/portfolios` is byte-identical to FD001. `FieldError.code` union widened. `position-draft-list` gains an "Instrument" column. |
| Backend — new rule | **NEW** `portfolio.domain.ports.InstrumentCatalog` — `boolean isSelectable(Ticker, Market, Currency)` (portfolio value objects only). **NEW** `portfolio.infrastructure.catalog.CatalogInstrumentCatalogAdapter` — the **only** `portfolio → financialinstrument` reference; resolves the listing via `financialinstrument`'s port and completes the currency match. `CreatePortfolioService` gains the dependency and merges `INSTRUMENT_NOT_IN_CATALOG` violations with the FD001 structural violations into **one** `PortfolioValidationException` / one `400`. `Portfolio.create` unchanged. |
| Backend — `financialinstrument` | **+1 additive method** on the existing `FinancialInstrumentCatalog` port: `Optional<FinancialInstrumentListing> findSelectable(String ticker, String marketMic)` (exact ticker/MIC, `active`, returns the listing incl. currency). No schema / ingestion / endpoint change. |
| Contract | `openapi.yaml` — **+`INSTRUMENT_NOT_IN_CATALOG`** in the `ValidationProblem.errors[].code` enum + description + a `notInCatalog` example. `type` stays `/problems/portfolio-validation`. Additive, non-breaking. OpenAPI 3.0.3. |
| Architecture | `StandardArchitectureRulesTest` — **+2 rules** (14 total): `portfolio` may not depend on `..financialinstrument.infrastructure..` / `..business..`; `portfolio.domain`/`portfolio.business` may not depend on `..financialinstrument..` at all. |
| `product/` (human-directed) | FD002 §18/§19 approval sync; **`architecture-rules.md` AR-062 — Inter-Module Reads Go Through a Published Port**. |
| Tests | `CreatePortfolioServiceTest` +5 catalog cases (fake port, incl. currency-mismatch A1); `CatalogInstrumentCatalogAdapterIT` (**NEW**, Testcontainers); `FinancialInstrumentCatalogAdapterIT` +2 `findSelectable` cases; `CreatePortfolioControllerContractTest` +1 `INSTRUMENT_NOT_IN_CATALOG` case; `instrument-search.service.spec.ts` (**NEW**, 4); `add-position.dialog.spec.ts` rewritten (13). `AbstractPortfolioIT` + `CreatePortfolioMultiPositionIT` seed the catalog so FD001 ITs pass the new check. |
| E2E | **NEW** `e2e/tests/FD002-select-instrument.spec.ts` (mandatory closure journey + no-results); **NEW** `e2e/support/add-position.ts` helper; `FD001-create-portfolio.spec.ts` Add Position step adapted to the search interaction (assertions unchanged). |
| Docs | `backend/core-service/README.md` (+the `portfolio → financialinstrument` seam / AR-062); `implementation/platform/README.md` (+FD002 capability). |

**Not changed**: no Flyway migration, no `pom.xml` / `package.json` dependency, no new deployable /
messaging / scheduler / search engine, no `compose.yaml` / `start.sh` / `stop.sh` / `e2e.sh`, no
Java/Angular major version, `Portfolio` / `Position` meaning + the FD001 `ticker + market` identity,
`create-portfolio.page.ts`, `portfolio-api.service.ts` (wire body identical).

## Why this design?

- **Rule in the service, not the aggregate** (research D1): reference-data membership is a distinct
  category from FD001 structural rules; keeping it in `CreatePortfolioService` (which may depend on
  ports) leaves `Portfolio.create` a pure function of raw input + `Clock`. All violations still
  reported in one pass (FD001 FR-024).
- **ACL port + adapter seam** (D2 / AR-062): `portfolio.domain`/`business` never see a
  `financialinstrument` type; the one cross-module call is an in-process method through a published
  domain port, from `portfolio.infrastructure` only, ArchUnit-enforced.
- **Currency in the check** (analyze A1): the guarantee is "the full `ticker + market + currency` is
  one active listing"; the `financialinstrument` port stays a ticker+MIC lookup and the currency
  comparison is done in the `portfolio` adapter — so a raw `AAPL + XNAS + EUR` (USD listing) is
  rejected.
- **Additive contract**: `INSTRUMENT_NOT_IN_CATALOG` is one more position-validation code in the
  existing `ValidationProblem` — non-breaking for FD001 clients.
- **Wire shape unchanged** (D8): the frontend fills ticker/market/currency from the listing;
  `POST /api/portfolios` body is byte-identical to FD001.

## How was it tested?

Local only (no CI):

- **`./mvnw -B clean verify`** → **Surefire 115 + Failsafe 56, 0 failures**. JaCoCo bundle
  **line 96.28 % · branch 91.01 %** (≥ 90 % both). ArchUnit **14/14** green.
- **`ng test`** (Node 20.19, ChromeHeadless) → **40 SUCCESS**.
- **`./e2e.sh`** → **4 passed** (Chromium), exit 0 — `FD001-create-portfolio` (adapted) +
  `platform-smoke` + `FD002-select-instrument` (main journey: search "Apple" → select → controlled
  `AAPL/XNAS/USD`, then a EUR instrument; assert no free-text t/m/c inputs; **assert every request
  stays on the frontend origin** — SC-007) + `FD002-select-instrument` (no-results).
- **ArchUnit non-vacuous** (T030): a deliberate `portfolio.domain.ports → financialinstrument.domain.model`
  reference → `portfolio_core_is_free_of_financialinstrument` fails → reverted → 14/14 green.

## Architecture boundaries

Internal only. ADR-001 intact (one `core-service`). The single new coupling is
`portfolio.infrastructure.catalog → financialinstrument.domain.ports` — an approved instance of
**AR-062**, machine-checked by 2 new ArchUnit rules. No new external operation
(`GET /api/financial-instruments` reused; `POST /api/portfolios` gains one response code).

## ADRs

No new ADR. `architecture-rules.md` gained **AR-062** (a rule, not an ADR — it records an allowed
in-deployable module dependency pattern; no topology/technology change). Both `product/` edits were
raised (OD-FD002-4) and human-approved before being applied (FR-030).

## Risk-Register outcome (plan.md)

| Risk | Outcome |
|---|---|
| FD001 E2E / suites regress (backend now rejects non-catalogued positions) | **Handled** — `AbstractPortfolioIT` + `CreatePortfolioMultiPositionIT` seed the catalog; `syntheticPosition()` (ASML·XAMS·EUR) is in EN004's fixtures; FD001 ITs + adapted E2E green. |
| Inter-module coupling drifts to direct table access | **Avoided** — ACL port + adapter-only; 2 ArchUnit rules; deliberate-violation check. |
| FR-007 "multiple listings" has no grouping key | **Accepted (OD-FD002-2)** — group by normalized name; EN004's catalog has no multi-listing instruments so this path is exercised by a synthetic frontend test only. |
| Contract test rejects the enum edit | **Avoided** — additive enum entry, 3.0.3, mirrors the FD001 example; `an_instrument_not_in_catalog_400_body_conforms_to_the_contract` green. |
| Listing goes inactive between search and Save | **Handled** — backend re-checks on `POST /api/portfolios` → `INSTRUMENT_NOT_IN_CATALOG`, nothing persisted. |
| Combobox accessibility under-built | **Handled** — ARIA combobox/listbox/`aria-activedescendant`, keyboard select, `<label>`; `add-position.dialog.spec.ts` asserts it. |
| Debounce causes a blank-query 400 | **Avoided** — the service and dialog both refuse a blank query. |

## OD outcomes

OD-FD002-1…5 all confirmed by jaruiz 2026-09-03 and implemented as planned; A1 tightened the rule to
include currency.

## `product/` change

Exactly two human-directed edits: the FD002 §18/§19 approval sync, and `architecture-rules.md`
**AR-062**. No change to any `product/` business rule, scope, acceptance criterion, or other
document.

## What evidence shows acceptance criteria pass?

`quickstart.md` → "Verification Criteria coverage" + Success-Criteria tables — every `AC-001…AC-009`
and `SC-001…SC-012` mapped to a green result from the run above.
