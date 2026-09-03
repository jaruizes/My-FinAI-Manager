# Implementation Plan: Select Financial Instrument from Catalog (FD002)

**Branch**: `FD002-select-financial-instrument-from-catalog` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/FD002-select-financial-instrument-from-catalog/spec.md`

**Authoritative feature**: `product/definition/features/FD002-select-financial-instrument-from-catalog/FD002-select-financial-instrument-from-catalog.md` (**Status: Approved** — §19 signed by jaruiz, 2026-09-03; §18 Open Questions resolved in the enabler's "Open Question resolutions (2026-09-03)" subsection).
**Consumed capability**: `EN004 — Establish Financial Instrument Reference Data` (Approved) — the local catalog, its startup ingestion, and `GET /api/financial-instruments?query=…`.
**Governing decisions**: ADR-001 (single `core-service` deployable — unchanged), ADR-003 (Standard Spring Backend Architecture), the FD001 Create Portfolio capability + its `POST /api/portfolios` contract, EN002 containerized E2E, the global design system `product/ux/design-system.md`. **No new ADR is required** — FD002 is a vertical slice (frontend + one backend validation rule + a contract-additive change + E2E) inside the existing topology. One inter-module dependency (`portfolio` reading the `financialinstrument` catalog through its published port) is surfaced as **OD-FD002-4**.

> **Approval status — cleared 2026-09-03.** FD002 §19 is signed (`Status: Approved`); all §18 Open
> Questions are resolved (spec §Clarifications, Assumptions A5–A9). The two material decisions —
> constrained Market/Currency selectors after instrument selection (FR-007) and **backend**
> enforcement of the invalid-combination guarantee via a new `INSTRUMENT_NOT_IN_CATALOG` validation
> code (FR-011, FR-020) — are locked. `/speckit-plan` proceeds; three technical Open Decisions
> (OD table) want human confirmation before or during `/speckit-implement`.

## Summary

FD002 replaces the three free-text fields (Ticker, Market, Currency) in the FD001 **Add Position**
dialog with **controlled selection of a catalogued Financial Instrument listing**, and adds a
**server-side guarantee** that a Position can only reference an active EUR/USD catalogued listing.

1. **Frontend — controlled Add Position** (`implementation/platform/frontend/web/src/app/portfolio/`):
   - A new `InstrumentSearchService` calls `GET /api/financial-instruments?query=…` (debounced,
     min length 1, in-flight request cancelled on new input; never called for a blank box).
   - `add-position.dialog.ts` is reworked: the three text inputs become an accessible
     **search combobox** (search field + results listbox, ARIA `combobox`/`listbox`/`option`,
     keyboard-operable, visible focus) plus a **selected** state. When the chosen instrument has
     more than one supported catalogued listing, a **Market / Currency selector** offers only that
     instrument's real listing combinations (FR-007); with a single listing its values are applied
     directly. Quantity / initial purchase date / average purchase price are unchanged; the price
     hint reflects the selected currency. Distinct states: idle, searching, results, no-results,
     selected, error (FR-013–FR-015, FR-025; design system §Forms/§Dialogs/§Loading/§Error/§Accessibility).
   - `PositionDraft` and the `POST /api/portfolios` **request body are unchanged** on the wire — the
     selection just fills `ticker` / `market` / `currency`. `CreatePortfolioOutcome`'s `invalid`
     branch already carries `errors[]`; the page renders the new `INSTRUMENT_NOT_IN_CATALOG` entry
     like any other field error.
2. **Backend — catalog validation on portfolio creation** (`.../core/portfolio/`):
   - New anti-corruption port `portfolio.domain.ports.InstrumentCatalog` —
     `boolean isSelectable(Ticker ticker, Market market, Currency currency)` — owned by the
     `portfolio` domain, using `portfolio`'s own value objects. Returns `true` only when the catalog
     holds one active listing whose `ticker + market + currency` **all** match (analyze A1 —
     `AAPL + XNAS + EUR` against a USD listing is rejected).
   - `CreatePortfolioService` (business) calls it for every structurally-valid position and merges
     an `INSTRUMENT_NOT_IN_CATALOG` `Violation` (scoped to `positions[i]`) into the same
     `PortfolioValidationException` that carries the FD001 structural violations (all problems in
     one `400`). The `Portfolio.create` aggregate stays dependency-light (only `Clock`).
   - Adapter `portfolio.infrastructure.<pkg>.CatalogInstrumentCatalogAdapter` implements the port by
     calling the `financialinstrument` module's published read port (OD-FD002-4). It does **not**
     touch `financial_instrument` tables directly (AR-006).
3. **`financialinstrument` — one additive read method**:
   `FinancialInstrumentCatalog.findSelectable(String ticker, String marketMic)` →
   `Optional<FinancialInstrumentListing>` (exact ticker case-insensitive + exact MIC, `active = true`,
   `currency ∈ {EUR, USD}`), backed by the existing `findByTickerIgnoreCaseAndMarketMic` repo method
   plus the filter. **No schema change, no new endpoint, no ingestion change.**
4. **Contract — additive** (`implementation/platform/contracts/openapi/openapi.yaml`):
   add `INSTRUMENT_NOT_IN_CATALOG` to the `ValidationProblem` `errors[].code` enum with its meaning
   and an example; `type` stays `/problems/portfolio-validation`. `GET /api/financial-instruments`
   is reused **exactly as EN004 shipped it** — no change. OpenAPI stays 3.0.3.
5. **Tests + mandatory E2E**:
   - Backend: `CreatePortfolioServiceTest` with a fake `InstrumentCatalog` (rule TDD'd);
     `CatalogInstrumentCatalogAdapterIT` (Testcontainers, seeded catalog); a contract test for the
     new code; `financialinstrument` `findSelectable` IT.
   - Frontend: `InstrumentSearchService` spec; reworked `add-position.dialog.spec.ts`
     (search → results → select → single-listing apply / multi-listing selector → confirm; no-results;
     error).
   - **`implementation/platform/e2e/tests/FD002-select-instrument.spec.ts`** — the mandatory closure
     journey: Create Portfolio → Add Position → search + select an instrument → controlled
     ticker/market/currency → Save → persisted. `syntheticPosition()` stays `ASML / XAMS / EUR`
     (already in EN004's catalog), so `FD001-create-portfolio.spec.ts` keeps passing unchanged.
6. **Regression**: all FD001 backend suites + `ng test` + `FD001-create-portfolio.spec.ts` green;
   `./mvnw verify` (incl. ≥ 90 % line+branch gate and ArchUnit) green; `./start.sh` / `./stop.sh` /
   `./e2e.sh` unchanged as interfaces.

**No** change to Position/Portfolio meaning or the FD001 `ticker + market` identity, **no** new
deployable / messaging / scheduler / search engine / persistence technology, **no** Java/Angular
major-version change, **no** frontend call to an external provider, **no** unapproved `product/` edit.

## Technical Context

**Language / Runtime**: Backend — Java 21, Spring Boot 3.5.6 (unchanged). Frontend — Angular 20,
TypeScript 5.8, standalone components + Reactive Forms + `HttpClient` (unchanged). Bash lifecycle
scripts unchanged.

**Primary Dependencies**: all **unchanged / reused** — no new backend or frontend dependency.
Backend: `spring-boot-starter-web`, `-data-jpa`, `flyway`, `postgresql`, `spring-boot-starter-test`,
Testcontainers, `archunit-junit5`, `swagger-request-validator-mockmvc` 2.44.1, JaCoCo gate. Frontend:
Angular common/forms/router, RxJS, Karma/Jasmine. E2E: `@playwright/test` (EN002).

**Storage**: PostgreSQL 16. **No schema change.** FD002 reads `financial_instrument` / `market`
(owned by `financialinstrument`) only through that module's port, and writes `portfolio` / `position`
only through the existing FD001 path. No new migration.

**Testing**: `./mvnw verify` (Surefire + Failsafe/Testcontainers + JaCoCo `check` + ArchUnit);
`ng test` (Karma/Jasmine, ChromeHeadless); `./e2e.sh` (Playwright, Chromium, containerized). New
backend contract test uses `@WebMvcTest` + `swagger-request-validator` (established by FD001/EN004).

**Target Platform**: local workstation + the EN002 containerized platform. `core-service` stays one
Spring Boot deployable (ADR-001). nginx already proxies `/api/` → backend (path preserved) — no
nginx change.

**Project Type**: Web application — vertical slice across `frontend/`, `backend/core-service`,
`contracts/`, and `e2e/` of the single cumulative platform under `implementation/platform/`.

**Performance Goals**: none quantified. Search is over EN004's small curated catalog (tens of rows);
plain `ILIKE` (EN004). Frontend debounce ~250 ms; result rendering must stay scannable (design
system §"compact information density"). SC-012: an Investor adds a Position for a known ticker in
under 30 s.

**Constraints**:
- ADR-003 layout for any backend code (`infrastructure → business → domain`, ArchUnit-enforced).
- `portfolio.domain` / `portfolio.business` must not depend on `financialinstrument` **implementation**
  types — only the ACL port `portfolio.domain.ports.InstrumentCatalog` (OD-FD002-1). The single
  inter-module dependency lives in `portfolio.infrastructure` and goes through `financialinstrument`'s
  published port (AR-006 — OD-FD002-4).
- No provider/persistence field in any FD002 request or response (FR-021; EN004 VC-010).
- Contract-first: the OpenAPI `errors[].code` enum is edited (and a contract test added) **before**
  the backend emits the new code (constitution VIII).
- The FD001 Create Portfolio journey, its validation-code semantics, its idempotency, and its
  atomic persistence are unchanged (FR-012, FR-017).
- The FD002 E2E is a **closure gate** — FD002 cannot be closed while it is missing or failing (FR-028).
- Frontend never calls an external reference-data provider (FR-003; BR-008).
- Offline: tests + E2E use EN004's committed deterministic catalog; no Internet at request time.

**Scale/Scope**: ~1 new frontend service + 1 reworked dialog component (+ specs); ~1 new backend
port + 1 adapter + 1 service change + 1 enum value + 1 `financialinstrument` method (+ tests);
1 OpenAPI enum entry; 1 new E2E spec. No new module, no new deployable.

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Human-Governed Source of Truth | **PASS** | Implements the human-approved FD002 (§19 signed 2026-09-03) within ADR-001 + ADR-003. FD002's *implementation* edits no `product/` doc (FR-030); the §18/§19 approval sync was human-directed. Tech is policy-approved and **unchanged** (no new dependency). |
| II | Definitions/Enablers Are Authoritative Intent | **PASS** | Traces to exactly one Feature Definition (FD002). Consumes EN004; does not expand EN004's scope. Every FR maps to an FD002 §/BR/AC (spec Traceability table). |
| III | Derived Artifacts & Repository Layout | **PASS** | Artifacts under `specs/FD002-…/`; implementation under `implementation/platform/` (frontend/backend/contracts/e2e); no root `src/` / `backend/` / `apps/`. |
| IV | No Invention; Surface Material Ambiguity | **PASS (with ODs)** | Three technical decisions surfaced for human confirmation — OD-FD002-2 (instrument grouping key for the multi-listing selector), OD-FD002-4 (the `portfolio → financialinstrument` read dependency), OD-FD002-5 (`financialinstrument` gains one read method). Nothing invented into product behavior. |
| V | Enablers Stay Technical / Features Extend the Platform | **PASS** | FD002 is a vertical slice of the existing platform — no isolated app, no parallel Position-creation path (FR-016). Platform stays coherent + executable (`./start.sh`, `./e2e.sh`). |
| VI | Hexagonal Architecture & Deterministic Logic | **PASS** | New rule (`INSTRUMENT_NOT_IN_CATALOG`) is deterministic and lives in `portfolio.business` behind a domain port; `portfolio.domain` stays framework-free; no LLM anywhere. |
| VII | Test-First for Deterministic Logic; Testcontainers by Default | **PASS** | The catalog-membership rule is TDD'd in `CreatePortfolioServiceTest` (fake port) RED→GREEN; the real adapter + `findSelectable` are covered by Testcontainers ITs; no manually-installed DB. |
| VIII | Contract-First External APIs | **PASS** | `openapi.yaml` `errors[].code` enum edited + contract test added before the code is emitted; `GET /api/financial-instruments` reused unchanged; business language, RFC 9457, no provider leakage. |

**Repository-structure / technology-policy quick check** (EN004 plan style):

| Check | Status | Evidence |
|---|---|---|
| One `core-service` deployable (ADR-001) | PASS | no new service; `compose.yaml` unchanged |
| ADR-003 module layout | PASS | new port in `portfolio.domain.ports`, adapter in `portfolio.infrastructure`; `financialinstrument` change is one method on an existing domain port |
| No new technology | PASS | zero new backend/frontend/e2e dependencies |
| No new deployable / broker / scheduler / cache / search engine / persistence tech | PASS | FR-029 |
| No schema change | PASS | reads existing tables via ports; no migration |
| No speculative infrastructure | PASS | one port + one adapter + one enum value + one method + one OpenAPI enum entry + one E2E spec |

**Result: PASS.** Proceed to Phase 0. The three ODs are technical and do not change FD002 business
behavior; they are flagged for human confirmation, not silently chosen (constitution IV).

## Open Decisions (technical) — **ALL CONFIRMED by jaruiz 2026-09-03**

| ID | Decision point | Confirmed position | Alternatives rejected |
|---|---|---|---|
| OD-FD002-1 | Where the catalog-membership rule runs | ✅ `CreatePortfolioService` (business) via a `portfolio.domain.ports.InstrumentCatalog` ACL port; violations merged into the existing `PortfolioValidationException`. | (a) `InstrumentCatalog` parameter on `Portfolio.create` — widens the aggregate's deps, mixes reference-data validation with structural rules. (b) Orchestrate in the REST controller — business logic in an adapter (CLAUDE.md §7). |
| OD-FD002-2 | How the frontend groups listings of "the same instrument" for the FR-007 selector | ✅ Group search results by **normalized instrument `name`** (trimmed, case-insensitive). ≥ 2 catalog listings share the name → constrained selector; else apply the single listing directly. ISIN-based grouping is a recorded future refinement. EN004's current catalog has **no** multi-listing instruments → this branch is exercised by a synthetic test only. | Group by ISIN as primary — ISIN can differ across venues and is optional. |
| OD-FD002-3 | New validation code name + problem `type` | ✅ `errors[].code = INSTRUMENT_NOT_IN_CATALOG`; `type` stays `/problems/portfolio-validation` (same problem class, additive code — non-breaking). | A dedicated `type` — it is one of several position-validation failures. |
| OD-FD002-4 | `portfolio.infrastructure` reading the `financialinstrument` catalog through its published port | ✅ Allowed as an **explicit application-interface** cross-boundary call (AR-006). **`architecture-rules.md` gains AR-062** recording the allowed dependency (human-directed `product/` edit, applied 2026-09-03). ArchUnit enforces the boundary (research D11 / contracts C4). | Duplicate a listing-existence check inside `portfolio` — AR-006 violation / data duplication. A shared-kernel module — premature for one method. |
| OD-FD002-5 | Adding `findSelectable(ticker, mic)` to `financialinstrument.domain.ports.FinancialInstrumentCatalog` | ✅ Additive method on the existing port; backed by the existing repo finder + the active/EUR-USD filter. Widens the catalog's **consumable** surface only — no schema / contract / ingestion / endpoint change. | Reuse `search(query)` + filter in the adapter — free-text/ranked, N rows, wrong semantics for an exact membership check. |

## Project Structure

### Documentation (this feature)

```text
specs/FD002-select-financial-instrument-from-catalog/
├── plan.md              # this file
├── research.md          # Phase 0 — D1…D12
├── data-model.md        # Phase 1 — model deltas (small)
├── quickstart.md        # Phase 1 — scenarios A–G ⇒ AC-001…AC-009 + SC
├── contracts/
│   ├── portfolio-validation.delta.md      # the INSTRUMENT_NOT_IN_CATALOG addition to POST /api/portfolios
│   ├── instrument-catalog-port.md         # portfolio.domain.ports.InstrumentCatalog + financialinstrument.findSelectable
│   └── add-position-ui-contract.md        # the Add Position interaction states + a11y contract
├── checklists/
│   └── requirements.md   # 16/16 (already passing)
└── tasks.md             # Phase 2 — /speckit-tasks (NOT this command)
```

### Source Code (repository)

```text
implementation/platform/
├── contracts/openapi/openapi.yaml                 # + INSTRUMENT_NOT_IN_CATALOG in ValidationProblem.errors[].code (+ example)
│
├── backend/core-service/src/
│   ├── main/java/com/myfinaimanager/core/
│   │   ├── portfolio/
│   │   │   ├── domain/
│   │   │   │   ├── model/ValidationCode.java               # + INSTRUMENT_NOT_IN_CATALOG
│   │   │   │   └── ports/InstrumentCatalog.java            # NEW — isSelectable(Ticker, Market, Currency)
│   │   │   ├── business/CreatePortfolioService.java        # + InstrumentCatalog dependency; merge catalog violations
│   │   │   └── infrastructure/
│   │   │       └── catalog/CatalogInstrumentCatalogAdapter.java   # NEW — implements the port via financialinstrument's port
│   │   └── financialinstrument/
│   │       ├── domain/ports/FinancialInstrumentCatalog.java       # + findSelectable(String ticker, String marketMic)
│   │       └── infrastructure/persistence/FinancialInstrumentCatalogAdapter.java  # implement findSelectable
│   └── test/java/com/myfinaimanager/core/
│       ├── portfolio/business/CreatePortfolioServiceTest.java              # + catalog-rule cases (fake port) — TDD
│       ├── portfolio/infrastructure/catalog/CatalogInstrumentCatalogAdapterIT.java   # NEW — Testcontainers
│       ├── portfolio/infrastructure/api/rest/CreatePortfolioControllerContractTest.java  # + INSTRUMENT_NOT_IN_CATALOG case
│       └── financialinstrument/infrastructure/persistence/FinancialInstrumentCatalogAdapterIT.java  # + findSelectable cases
│
├── frontend/web/src/app/portfolio/
│   ├── instrument-search.service.ts        # NEW — GET /api/financial-instruments?query=
│   ├── instrument-search.service.spec.ts   # NEW
│   ├── instrument.models.ts                # NEW — CatalogListing view model + search states
│   ├── add-position.dialog.ts              # REWORK — combobox search/select + constrained listing selector
│   ├── add-position.dialog.spec.ts         # REWORK
│   ├── portfolio-creation.models.ts        # PositionDraft unchanged; add optional selected-listing ref for the view
│   └── (create-portfolio.page.ts, portfolio-api.service.ts — render the new error code; otherwise unchanged)
│
└── e2e/
    ├── support/data.ts                     # note: syntheticPosition stays ASML/XAMS/EUR (catalogued)
    └── tests/FD002-select-instrument.spec.ts   # NEW — mandatory closure journey
```

**Structure Decision**: extend the existing `portfolio` frontend feature area and backend module;
add one adapter package (`portfolio.infrastructure.catalog`); make one additive change to the
`financialinstrument` catalog port. No new module, no new deployable, no new top-level directory.

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| FD001 E2E / suites regress because the backend now rejects non-catalogued positions | Med | High | `syntheticPosition()` (ASML·XAMS·EUR) is already in EN004's catalog → `FD001-create-portfolio.spec.ts` and the FD001 ITs pass unchanged; add a catalog-seed check to the FD001 IT fixtures if any use a non-catalogued ticker (audit in Phase 2). |
| Inter-module coupling (`portfolio` → `financialinstrument`) drifts toward direct table access | Low | High | ACL port + adapter-only dependency; add an ArchUnit rule: `portfolio` must not depend on `financialinstrument.infrastructure.persistence` (only `..financialinstrument.domain.ports..` / `..domain.model..`). |
| The FR-007 "multiple listings" selector has no reliable grouping key | Med | Med | OD-FD002-2 — group by normalized name; the current catalog has no multi-listing instruments so real risk is deferred; covered by a synthetic frontend test only. Revisit with ISIN if EN004 later adds dual listings. |
| Contract test rejects the enum edit (3.1 vs 3.0.3 / validator quirks) | Low | Med | additive enum entry only; keep 3.0.3; mirror the FD001 `ValidationProblem` example style. |
| Listing goes inactive between search and Save | Low | Low | backend re-checks on `POST /api/portfolios` (FR-011) → `INSTRUMENT_NOT_IN_CATALOG`, nothing persisted; frontend shows it as a field error and the Investor re-picks. |
| Accessibility of the combobox is under-built | Med | Med | follow the ARIA combobox pattern (roles, `aria-activedescendant`, keyboard); `add-position.dialog.spec.ts` asserts keyboard select + focus; design system §Accessibility is the contract. |
| Debounce / min-length causes a blank-query `400` from EN004 | Low | Low | never issue a search for an empty/whitespace box; min length 1 after trim (matches EN004's contract). |

## Phase 0 — Research

See [research.md](./research.md). Decisions **D1–D12** cover: enforcement placement (D1), the ACL
port + adapter seam (D2), the `financialinstrument.findSelectable` method (D3), the new validation
code + contract shape (D4), the frontend search service (debounce/cancel/min-length) (D5), the
combobox interaction + state model (D6), the instrument-grouping key (D7), the request/response
wire shape staying unchanged (D8), the E2E approach + FD001 non-regression (D9), the test plan (D10),
the ArchUnit rule for the inter-module dependency (D11), and the design-system mapping for the six
UI states (D12). No `NEEDS CLARIFICATION` remains — the two spec Clarifications are resolved and the
three residual items are technical ODs, not blockers.

## Phase 1 — Design & Contracts

Outputs: [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md).

**Post-Design Constitution re-check: PASS** — the design adds no framework dependency to any
`domain`, keeps the one inter-module dependency in `infrastructure` behind published ports, is
contract-first for the enum change, TDD-able for the new rule, Testcontainers-covered for the
adapters, and leaves FD001 + EN004 behavior intact. The ODs remain technical.
