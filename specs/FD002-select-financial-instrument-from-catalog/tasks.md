---
description: "Task list — FD002 Select Financial Instrument from Catalog"
---

# Tasks: Select Financial Instrument from Catalog (FD002)

**Input**: `specs/FD002-select-financial-instrument-from-catalog/` — plan.md, spec.md, research.md
(D1–D12), data-model.md, contracts/ (portfolio-validation.delta, instrument-catalog-port,
add-position-ui-contract), quickstart.md (A–G).

**Tests**: REQUIRED — FD002 §14 / FR-026 / FR-028 mandate unit + integration + contract + a
mandatory browser E2E; constitution VII requires TDD for the new deterministic rule.

**Feature**: Approved 2026-09-03 (§19 signed). ODs OD-FD002-1…5 **all confirmed by jaruiz
2026-09-03**; `architecture-rules.md` AR-062 added (human-directed).

## Format: `[ID] [P?] [Story] Description`

- **[P]** = different files, no dependency on an incomplete task → parallelizable
- **[Story]** = US1 / US2 / US3 / US4 (Setup / Foundational / Polish have none)
- All paths are repository-relative.

## Path map (plan.md §Project Structure)

```text
BE  = implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core
BET = implementation/platform/backend/core-service/src/test/java/com/myfinaimanager/core
FE  = implementation/platform/frontend/web/src/app/portfolio
API = implementation/platform/contracts/openapi/openapi.yaml
E2E = implementation/platform/e2e
```

---

## Phase 1: Setup

- [X] T001 [P] Add `INSTRUMENT_NOT_IN_CATALOG` to `BE/portfolio/domain/model/ValidationCode.java` (enum constant only; javadoc: "FD002 — position's ticker+market is not an active EUR/USD catalogued listing").
- [X] T002 [P] **Contract-first** — edit `API`: add `INSTRUMENT_NOT_IN_CATALOG` to `ValidationProblem.properties.errors.items.properties.code.enum`, extend its `description`, and add the `notInCatalog` example to the `400` response of `POST /api/portfolios` exactly as `contracts/portfolio-validation.delta.md` specifies. Keep OpenAPI **3.0.3**. No behavior yet.
- [X] T003 [P] Create `FE/instrument.models.ts` — `CatalogListing { id, name, ticker, market, currency: 'EUR'|'USD', active, isin?: string|null }` and `InstrumentSearchState` union (`idle | searching | results | no-results | error`) per data-model.md §4.

**Checkpoint**: `./mvnw -B compile` green; `API` still parses (3.0.3); no runtime change.

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ Blocks all user stories.** T004 blocks US1/US3 (frontend search); T005–T006 block US2 (backend check).

- [X] T004 `FE/instrument-search.service.ts` + `FE/instrument-search.service.spec.ts` — TDD (spec RED first): `search(query): Observable<CatalogListing[]>` → `GET /api/financial-instruments?query=<trimmed>`; **no** request for `''`/`'   '`; maps the response array to `CatalogListing[]`; HTTP error → an `error` signal the caller renders. `@Injectable({providedIn:'root'})`, `inject(HttpClient)`. (research D5) *(Foundational — no story label; blocks US1/US3)*
- [X] T005 [P] Add `Optional<FinancialInstrumentListing> findSelectable(String ticker, String marketMic)` to `BE/financialinstrument/domain/ports/FinancialInstrumentCatalog.java` (signature + javadoc per `contracts/instrument-catalog-port.md` C2; invariants C-S1…C-S5). Returns the listing **including its currency** (the `portfolio` side does the currency match — A1).
- [X] T006 `BE/financialinstrument/infrastructure/persistence/FinancialInstrumentCatalogAdapter.java` — implement `findSelectable` (existing `findByTickerIgnoreCaseAndMarketMic` + `active && SupportedCurrency.isSupported(...)` guard, **or** a derived query `findByTickerIgnoreCaseAndMarketMicAndActiveTrueAndCurrencyIn`); `@Transactional(readOnly=true)`. Extend `BET/financialinstrument/infrastructure/persistence/FinancialInstrumentCatalogAdapterIT.java` (Testcontainers, TDD): exact hit (case-insensitive ticker, exact MIC) returns the listing with its currency; wrong market → empty; inactive seeded row → empty; GBP seeded row → empty; unknown → empty.

**Checkpoint**: `./mvnw -B verify -Dtest=FinancialInstrumentCatalogAdapterIT -Dit.test=FinancialInstrumentCatalogAdapterIT` green; `ng test --include='**/instrument-search.service.spec.ts'` green.

---

## Phase 3: User Story 1 — Add a Position by selecting a known instrument (Priority: P1) 🎯 MVP

**Goal**: An Investor searches the catalog by ticker/name inside Add Position, selects an
instrument, and the Position's ticker/market/currency are filled from that listing (single-listing
case); the FD001 flow continues.

**Independent Test**: `ng test` on the reworked dialog — type `aapl` → results → select
"Apple Inc." → `AAPL / XNAS / USD` shown, no free-text inputs → quantity `3` → confirm emits the
`PositionDraft`; and quickstart §E.

### Tests for US1 (write first, must fail)

- [X] T007 [US1] Rewrite `FE/add-position.dialog.spec.ts` for the search+select happy path (RED): **no** `input[formControlName="ticker"|"market"|"currency"]` (U1); typing drives `InstrumentSearchService`; `role="combobox"` + `role="listbox"`/`role="option"` present; `ArrowDown`+`Enter` selects; a **USD** single-listing instrument fills `AAPL·XNAS·USD` **and** a **EUR** single-listing instrument fills e.g. `SAN·XMAD·EUR` (FR-026 EUR + USD coverage), each showing **no** listing selector; "Add position" enabled only after a valid quantity; confirm emits `PositionDraft` with those three values. (contracts/add-position-ui-contract.md U1–U3, U5–U6)

### Implementation for US1

- [X] T008 [US1] Rework `FE/add-position.dialog.ts` — replace the 3 text inputs with the search combobox (input + `role="listbox"` results), wire input `valueChanges` → `debounceTime(250) | map(trim) | distinctUntilChanged() | filter(len>=1) | switchMap(search)`; render each option `«name» — «TICKER» · «MIC» · «CCY»` (+ `· ISIN «isin»`); keyboard nav via `aria-activedescendant`; `selected` state with a "Change" affordance; **single-listing** path applies `ticker/market/currency` directly; keep Quantity / Initial purchase date / Average purchase price (price label `(optional, in «CCY»)` from the selected listing). (research D6; U2/U5)
- [X] T009 [US1] `FE/portfolio-creation.models.ts` — add a non-serialized `selectedListing: CatalogListing | null` to the dialog's internal draft state; `PositionDraft` wire shape **unchanged**; widen `FieldError['code']` union with `INSTRUMENT_NOT_IN_CATALOG`. (data-model.md §4.3–§4.4; research D8)
- [X] T010 [US1] `FE/position-draft-list.component.ts` (+ its spec) — show the selected instrument **name** next to the ticker in the draft list; the request body built by `portfolio-api.service.ts` is byte-identical to FD001 (assert in the existing service spec).
- [X] T011 [US1] `ng test` (dialog + search service + draft list) green; `./mvnw -B verify` unaffected (no backend change yet).

**Checkpoint**: MVP — an Investor adds a Position by picking a catalogued single-listing instrument; ticker/market/currency are controlled; FD001 Save path unchanged. Independently demoable.

---

## Phase 4: User Story 2 — Invalid instrument/market/currency combinations cannot be created (Priority: P1)

**Goal**: No Position with a `ticker+market+currency` that is not an active EUR/USD catalogued
listing can be created — enforced in the UI (constrained selector) **and** on
`POST /api/portfolios` (`INSTRUMENT_NOT_IN_CATALOG`).

**Independent Test**: `CreatePortfolioServiceTest` (fake port) + `CatalogInstrumentCatalogAdapterIT`
+ the contract test; and quickstart §B/§C/§D.

### Tests for US2 (write first, must fail)

- [X] T012 [P] [US2] `BET/portfolio/business/CreatePortfolioServiceTest.java` — add cases with a **fake `InstrumentCatalog`** whose `isSelectable(ticker, market, currency)` keys on all three (RED): (a) all selectable → created, `save` once; (b) `positions[0]` not selectable → `PortfolioValidationException` with exactly `{field:"positions[0]", code:INSTRUMENT_NOT_IN_CATALOG}`, `save` never called; (c) structural error on `positions[0]` **and** non-catalogued `positions[1]` → one exception carrying **both**; (d) `positions[0]` with a blank ticker/market/currency → only the FD001 `REQUIRED`/`CURRENCY_FORMAT`, the fake catalog **not** consulted for it; (e) `positions[0]` = `AAPL/XNAS/EUR` where the fake has only `AAPL/XNAS/USD` → `INSTRUMENT_NOT_IN_CATALOG` (currency mismatch — A1). (research D1; quickstart §B)
- [X] T013 [P] [US2] `BET/portfolio/infrastructure/api/rest/CreatePortfolioControllerContractTest.java` — add the `INSTRUMENT_NOT_IN_CATALOG` case (RED): service throws it → `400 application/problem+json` **conforms to `openapi.yaml`**, `$.type=="/problems/portfolio-validation"`, `$.errors[0].code=="INSTRUMENT_NOT_IN_CATALOG"`, `$.errors[0].field=="positions[0]"`. (contracts/portfolio-validation.delta.md)
- [X] T014 [P] [US2] `BET/portfolio/infrastructure/catalog/CatalogInstrumentCatalogAdapterIT.java` — NEW (RED, `@SpringBootTest` + `PostgresContainerSupport`): seed via the `financialinstrument` writer port active `AAPL·XNAS·USD` **and** `SAN·XMAD·EUR`, an inactive listing, a GBP listing. Assert `isSelectable(AAPL,XNAS,USD)` & `isSelectable(aapl,XNAS,usd)` → true; `isSelectable(SAN,XMAD,EUR)` → true (**EUR path — FR-026**); `isSelectable(AAPL,XNAS,EUR)` → false (**currency mismatch — A1**); wrong market / inactive / GBP / unknown → false. (contracts C1/C3; quickstart §C)
- [X] T015 [US2] `FE/add-position.dialog.spec.ts` — add the **constrained listing selector** case (RED, synthetic 2-listing instrument in the mocked search response): selecting the instrument renders a Market/Currency selector with exactly those two `MIC·CCY` options and no other; cannot confirm until a listing is chosen; choosing one sets `ticker/market/currency`. (contracts U4; research D7)

### Implementation for US2

- [X] T016 [P] [US2] `BE/portfolio/domain/ports/InstrumentCatalog.java` — NEW interface `boolean isSelectable(Ticker ticker, Market market, Currency currency)` using `portfolio`'s own value objects; javadoc = invariants P1–P6 from `contracts/instrument-catalog-port.md` C1 (the full `ticker + market + currency` must be one active catalogued listing).
- [X] T017 [US2] `BE/portfolio/infrastructure/catalog/CatalogInstrumentCatalogAdapter.java` — NEW `@Component implements InstrumentCatalog`; constructor takes `financialinstrument.domain.ports.FinancialInstrumentCatalog`; `isSelectable(t,m,ccy) → catalog.findSelectable(t.value(), m.value()).filter(l -> l.currency().name().equalsIgnoreCase(ccy.code())).isPresent()` (contracts C3; `Currency.code()`, `Ticker/Market.value()`). Let a `DataAccessException` propagate (C1 P6). This is the **only** `portfolio → financialinstrument` reference.
- [X] T018 [US2] `BE/portfolio/business/CreatePortfolioService.java` — add `InstrumentCatalog` constructor dependency; per research D1: catch/collect structural violations from `Portfolio.create`, then for every position whose ticker is non-blank, market is non-blank, **and** `Currency.hasValidShape(currency)` is true (a malformed/blank one is already owned by the FD001 `REQUIRED`/`CURRENCY_FORMAT` check) call `isSelectable(new Ticker(t), new Market(m), new Currency(c))` and collect `INSTRUMENT_NOT_IN_CATALOG` violations (`positions[i]`, message = "«ticker» on «market» in «currency» is not a selectable instrument."); if any violation exists (structural or catalog) → throw one `PortfolioValidationException(all)`; else `save`. Do **not** catch the catalog read's `DataAccessException` — it flows to the existing not-saved (503) path. Idempotency replay + log events unchanged.
- [X] T019 [US2] `FE/add-position.dialog.ts` — the FR-007 constrained selector: from the current results, `listingsForInstrument = results.filter(r => normName(r.name) === normName(selected.name))`; `>= 2` → render the Market/Currency `<select>` limited to those; `<= 1` → apply directly (already done in T008). `normName = s => s.trim().toUpperCase()` (deterministic — A7). (research D7; U4)
- [X] T020 [US2] `BET/architecture/StandardArchitectureRulesTest.java` — add the 2 boundary rules from `contracts/instrument-catalog-port.md` C4: `portfolio` must not depend on `..financialinstrument.infrastructure..` / `..financialinstrument.business..`; `..portfolio.domain..`/`..portfolio.business..` must not depend on `..financialinstrument..`. Now 14 rules; both non-vacuous (T017 exercises the allowed path).
- [X] T021 [US2] `./mvnw -B verify` — T012/T013/T014 green, ArchUnit 14/14 green, JaCoCo gate holds; `ng test` (incl. T015) green.

**Checkpoint**: AC-006 + AC-007 hold at the UI **and** the API; a non-catalogued Position submitted directly to `POST /api/portfolios` is rejected with nothing persisted.

---

## Phase 5: User Story 3 — Clear "no match" / loading / error handling (Priority: P2)

**Goal**: When search matches nothing, is loading, or fails, the dialog says so plainly and never
turns typed text into an instrument.

**Independent Test**: `ng test` — the state machine; quickstart §E (no-results/error rows).

### Tests for US3 (write first, must fail)

- [X] T022 [US3] `FE/add-position.dialog.spec.ts` — add state cases (RED): `idle` (blank box → **no** HTTP call, hint shown); `searching` (spinner while in flight); `no-results` ("No matching instrument found.", **no** control to accept the text); `error` (message + **Retry** re-runs the last query); the "Add position" button disabled in idle/searching/results/no-results/error. (contracts U3)

### Implementation for US3

- [X] T023 [US3] `FE/add-position.dialog.ts` — implement the six states per `contracts/add-position-ui-contract.md` U3 (idle · searching · results · no-results · selected · error), the `Retry` action, and the guarantee that a blank/whitespace box issues no request (EN004 returns `400` for that). Map each state's presentation to the design-system anchor in research D12.
- [X] T024 [US3] `ng test` green; assert the accessibility contract (U8): search `<label>`, keyboard-operable listbox, `aria-activedescendant`, error/no-results text not color-only.

**Checkpoint**: AC-008 + FR-013 / FR-014 / FR-015 hold.

---

## Phase 6: User Story 4 — FD001 journey still works, proven end to end (Priority: P2)

**Goal**: Every FD001 Create Portfolio behavior still passes with the new interaction + backend
check, and the mandatory FD002 browser E2E is green.

**Independent Test**: `./e2e.sh` → 3 passed; full `./mvnw verify` + `ng test`.

### Tests for US4

- [X] T025 [P] [US4] **Audit** FD001 backend ITs + `E2E/support/data.ts` for any position using a `(ticker, market)` **not** in EN004's catalog. `syntheticPosition()` (`ASML/XAMS/EUR`) is catalogued — keep it. For any FD001 IT that creates a portfolio and now hits the catalog check: seed the needed listing(s) via the `financialinstrument` writer port in the IT's setup (as `FinancialInstrumentCatalogAdapterIT` does), or switch the fixture ticker to a catalogued one. `PostgresContainerSupport` already disables startup import.
- [X] T026 [US4] `E2E/tests/FD002-select-instrument.spec.ts` — NEW mandatory closure journey: `/portfolios/new` → name → **Add position** → type `Apple` → results listbox shows `Apple Inc. — AAPL · XNAS · USD` → select via keyboard → assert controlled `AAPL/XNAS/USD` and **no** free-text ticker/market/currency inputs → quantity `3` → **Add position** → **Save** → "created successfully" confirmation. Also add one **EUR** instrument to the same portfolio (search a catalogued EUR name, e.g. `Iberdrola` → `IBE · XMAD · EUR`) so the journey exercises both currencies. Assert every result row shown is `active` and EUR/USD (A3 — the real API, not a mock). Assert **zero** outbound requests to a non-`/api` host (`page.on('request')`). Synthetic unique portfolio name. (research D9; quickstart §F)

### Implementation / verification for US4

- [X] T027 [US4] `./e2e.sh` → **3 passed** (Chromium), exit 0 — `FD001-create-portfolio.spec.ts` + `platform-smoke.spec.ts` **unchanged** and green, plus `FD002-select-instrument.spec.ts`. Fix any FD001 E2E breakage by seeding the catalog / using a catalogued ticker, never by weakening the backend check.
- [X] T028 [US4] `./mvnw -B clean verify` + `ng test` — all FD001 suites green (single/multi position, duplicate rejection, optional data, idempotent Save, transient-failure); JaCoCo ≥ 90 % line **and** branch; ArchUnit 14/14. (SC-006, SC-009)

**Checkpoint**: FD002 closure gate (E2E) green; FD001 fully intact.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T029 [P] Docs: `implementation/platform/backend/core-service/README.md` — the `portfolio → financialinstrument` read dependency (AR-062), the `InstrumentCatalog` port + `CatalogInstrumentCatalogAdapter`, `INSTRUMENT_NOT_IN_CATALOG`; `implementation/platform/README.md` — Add Position is now catalog-driven (search + select), the new validation code. No further `product/` edit.
- [X] T030 [P] ArchUnit deliberate-violation spot check (VC-017 style): add a forbidden `import` from `portfolio.infrastructure` to `financialinstrument.infrastructure.persistence` → `-Dtest=StandardArchitectureRulesTest` MUST fail → revert → green. Record in `quickstart.md` §A.
- [X] T031 [P] `specs/FD002-select-financial-instrument-from-catalog/pr-evidence.md` per `definition-of-done.md` "Minimum Pull Request Evidence": what/why; trace to `AC-001…AC-009` + `SC-001…SC-012`; the two human-approved `product/` edits (FD002 §18/§19 sync, `architecture-rules.md` AR-062); how each Risk-Register item turned out; how validated (no CI — `./mvnw verify` + `ng test` + `quickstart.md` §A–§G + `./e2e.sh`). **Include an explicit `git diff --stat` scope review (SC-011)**: only `openapi.yaml` (+1 enum value + example), `core/portfolio/**`, `core/financialinstrument/**` (+`findSelectable`), `StandardArchitectureRulesTest` (+2 rules), `frontend/web/src/app/portfolio/**`, `e2e/tests/FD002-select-instrument.spec.ts`, the two READMEs, and the two approved `product/` edits — **no** schema migration, **no** new dependency, **no** new deployable/messaging/scheduler/search-engine, **no** Position/Portfolio meaning or identity change, **no** other `product/` edit.
- [X] T032 [P] `specs/FD002-select-financial-instrument-from-catalog/dod-checklist.md` — evaluate `product/engineering/definition-of-done.md` item by item (product/spec traceable to FD002; architecture — ArchUnit green + non-vacuous, AR-062; code quality — port-based, deterministic rule, no free-text controls; tests — TDD unit + Testcontainers ITs + contract + E2E; coverage ≥ 90 %; API/OpenAPI — additive + contract test, RFC 9457, no provider leakage; persistence — no migration, reads via ports, no dual write; secrets/hygiene; observability — unchanged log events; platform lifecycle — `start.sh`/`stop.sh`/`e2e.sh` unchanged; documentation).
- [X] T033 Run the full `quickstart.md` (§A–§G) from a clean state; complete its "Verification Criteria coverage" + Success-Criteria tables with concrete evidence for `AC-001…AC-009` and `SC-001…SC-012` (incl. the SC-012 < 30 s add-a-Position walkthrough).
- [X] T034 Confirm the JaCoCo bundle gate (≥ 90 % line **and** branch) with the FD002 code; **no** new coverage exclusion is added (the new service branch, the adapter, and `findSelectable` stay in coverage; `portfolio.infrastructure.catalog` is a thin adapter — covered by `CatalogInstrumentCatalogAdapterIT`).

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (P1)**: T001 ‖ T002 ‖ T003 — independent files. → checkpoint.
- **Foundational (P2)**: T004 (FE search infra) ‖ T005 → T006 (BE catalog read). Blocks all stories.
- **US1 (P1)**: after T004. T007 (spec RED) → T008 → T009 ‖ T010 → T011. MVP.
- **US2 (P1)**: after T006 (BE) and after T008 (FE selector builds on the dialog). Tests T012 ‖ T013 ‖ T014 ‖ T015 → impl T016 → T017 → T018 (BE); T019 (FE, after T008); T020 → T021 (gate).
- **US3 (P2)**: after T008 (same dialog file). T022 (RED) → T023 → T024.
- **US4 (P2)**: after US1 + US2 + US3 (needs the finished dialog + the backend check). T025 → T026 → T027 → T028 (FD001 non-regression gate).
- **Polish (P7)**: after US1–US4. T029 ‖ T030 ‖ T031 ‖ T032 → T033 → T034.

### File-contention notes (NOT parallel)

- `FE/add-position.dialog.ts` — T008, T019, T023 are sequential (US1 → US2 → US3).
- `FE/add-position.dialog.spec.ts` — T007, T015, T022 are sequential.
- `BET/.../CreatePortfolioServiceTest.java` — T012 only.
- `API` / `openapi.yaml` — T002 only.
- `BET/architecture/StandardArchitectureRulesTest.java` — T020 only.

### Parallel opportunities

- Setup: T001 ‖ T002 ‖ T003.
- Foundational: T004 ‖ (T005 → T006).
- US2 tests: T012 ‖ T013 ‖ T014 (‖ T015 — different file).
- US2 impl: T016 ‖ (start) then T017 → T018; T019 in parallel with T017/T018 (FE vs BE).
- Polish: T029 ‖ T030 ‖ T031 ‖ T032.

---

## Implementation Strategy

### MVP (US1 only)

Setup → Foundational T004 → US1 (T007–T011). Delivers: an Investor picks a catalogued
single-listing instrument in Add Position; ticker/market/currency are controlled; FD001 Save
unchanged. **Stop and demo.**

### Incremental

1. + US2 → the invalid-combination guarantee (UI + API); AC-006/AC-007. Demo.
2. + US3 → robust no-results / loading / error UX; AC-008.
3. + US4 → mandatory E2E green + FD001 non-regression proven → **FD002 closure gate satisfied**.
4. Polish → docs, evidence, DoD, quickstart run, coverage confirmation.

### Notes

- Verify every RED test fails before its implementation task.
- Never weaken the backend catalog check to make an FD001 test pass — seed the catalog or use a
  catalogued ticker (T025).
- `POST /api/portfolios` request body stays byte-identical to FD001 (research D8).
- Commit after each task or logical group.
