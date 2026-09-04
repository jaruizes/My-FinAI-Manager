---
description: "Task list for FD004 — Portfolio Valuation & Allocation"
---

# Tasks: Portfolio Valuation & Allocation (FD004)

**Input**: `specs/FD004-portfolio-valuation-and-allocation/` — [plan.md](./plan.md), [spec.md](./spec.md),
[research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **Included and required** — FD004 spec explicitly mandates them (FR-036 deterministic
business tests, FR-037 Testcontainers, FR-038 the two E2E gates) and constitution VII requires
TDD RED-first for deterministic domain logic (`PortfolioValuationCalculator`).

**Organization**: by user story (US1–US6 from spec.md). US1–US5 are P1; US6 is P2.
**MVP = Phase 3 (US1) + Phase 4 (US2)** — a created Portfolio gets a deterministic EUR/USD
valuation that survives provider failure.

**Confirmed 2026-09-04**: the 9 plan Open Decisions (OD-FD004-1…9) are accepted as recommended.

## Path conventions

- Backend main: `implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core/portfolio/`
- Backend test: `implementation/platform/backend/core-service/src/test/java/com/myfinaimanager/core/portfolio/`
- Arch test: `implementation/platform/backend/core-service/src/test/java/com/myfinaimanager/core/architecture/StandardArchitectureRulesTest.java`
- Migrations: `implementation/platform/backend/core-service/src/main/resources/db/migration/`
- Contract: `implementation/platform/contracts/openapi/openapi.yaml`
- Frontend: `implementation/platform/frontend/web/src/app/portfolio/`
- E2E: `implementation/platform/e2e/`
- Compose: `implementation/platform/infrastructure/local/compose.e2e.yaml`

Abbrev.: **BE-M** = backend main path above, **BE-T** = backend test path above, **FE** = frontend path above.

---

## Phase 1: Setup

- [x] T001 Record the pre-change green baseline: from `implementation/platform/backend/core-service` run `./mvnw -q -DskipTests compile`; from `implementation/platform/frontend/web` run `npm run build`. Both must succeed before FD004 changes.
- [x] T002 [P] In `implementation/platform/backend/core-service/pom.xml` confirm the JaCoCo `<excludes>` glob already covers `**/portfolio/infrastructure/persistence/entity/**`; if the current pattern is narrower, add exactly that entry. Add **no** other FD004 exclude.
- [x] T003 [P] Create the new packages with `package-info.java`: `BE-M/domain/events/`, `BE-M/infrastructure/marketdata/`, `BE-M/infrastructure/valuation/` (Javadoc: purpose + the AR-062 / catch-all constraints from [plan.md](./plan.md)).

---

## Phase 2: Foundational (blocking prerequisites)

**⚠️ Every user story depends on this phase. No US work starts until Phase 2 is complete.**

### Domain model & ports

- [x] T004 [P] `BE-M/domain/model/ValuationStatus.java` — enum `PENDING, COMPLETED, PARTIAL, FAILED` (FR-015; FD004 §6).
- [x] T005 [P] `BE-M/domain/events/PortfolioCreatedEvent.java` — `record PortfolioCreatedEvent(PortfolioId portfolioId)` (research D1).
- [x] T006 [P] `BE-M/domain/model/PositionPricing.java` + `FxConversion.java` — `record`s `(BigDecimal price/rate, Instant observedAt)`, reject null / ≤ 0 ([contracts/market-data-gateway.md](./contracts/market-data-gateway.md)).
- [x] T007 [P] `BE-M/domain/ports/MarketDataGateway.java` — the 3 `Optional`-returning methods with primitive `String` args ([contracts/market-data-gateway.md](./contracts/market-data-gateway.md); FR-026, AR-062).
- [x] T008 [P] `BE-M/domain/ports/PortfolioValuationRepository.java` — `void upsertLatest(PortfolioValuation)` + `Optional<PortfolioValuation> findByPortfolioId(PortfolioId)` ([data-model.md](./data-model.md) §1.6).
- [x] T009 `BE-M/domain/model/PositionValuation.java`, `SectorAllocation.java`, then `PortfolioValuation.java` (aggregate) — fields, `Optional` monetary fields (never `ZERO`), and the constructor invariants in [data-model.md](./data-model.md) §1.2–§1.4 (FR-017, FR-019).
- [x] T010 `BE-M/domain/model/PortfolioValuationCalculator.java` + nested/`package-private` `PositionInput`, `FxContext` records — signature per [contracts/valuation-calculation.md](./contracts/valuation-calculation.md); body throws `UnsupportedOperationException` for now (behavior is TDD'd in US2–US4). Pure class: no imports of Spring / JPA / HTTP / `marketdata`.

### Contract (contract-first — before the controller)

- [x] T011 Merge [contracts/openapi/portfolio-valuation.read.yaml](./contracts/openapi/portfolio-valuation.read.yaml) into `implementation/platform/contracts/openapi/openapi.yaml`: add path `GET /api/portfolios/{portfolioId}/valuation` (200 `PortfolioValuation`, 400, 404 `Problem`) and schemas `PortfolioValuation` / `PositionValuation` / `SectorAllocation`. **Do not touch** the existing `Portfolio` schema or `GET /api/portfolios[/{id}]` (SC-012). Keep OpenAPI 3.0.3. Run any existing `openApi().isValid(...)` check.

### Persistence (V4 migration + JPA stack)

- [x] T012 `implementation/platform/backend/core-service/src/main/resources/db/migration/V4__portfolio_valuation.sql` — `portfolio_valuation` (`portfolio_id UUID NOT NULL UNIQUE REFERENCES portfolio(id) ON DELETE CASCADE`, `status` CHECK, `calculated_at`, nullable totals + `*_as_of`), `position_valuation` (FK `ON DELETE CASCADE`, nullable money cols, `sector TEXT NOT NULL`), `sector_allocation` (FK `ON DELETE CASCADE`), + the two parent indexes. Exact DDL in [data-model.md](./data-model.md) §2. **No `ALTER`/`DROP`** on `portfolio` / `position` / EN004 tables (FR-022, FR-023).
- [x] T013 [P] `BE-M/infrastructure/persistence/entity/PortfolioValuationEntity.java`, `PositionValuationEntity.java`, `SectorAllocationEntity.java` — `@OneToMany(cascade = ALL, orphanRemoval = true)` parent→children; map every column from T012.
- [x] T014 [P] `BE-M/infrastructure/persistence/repository/PortfolioValuationJpaRepository.java` — `extends JpaRepository<PortfolioValuationEntity, UUID>` + derived `Optional<…> findByPortfolioId(UUID)` + `void deleteByPortfolioId(UUID)`.
- [x] T015 `BE-M/infrastructure/persistence/mapper/PortfolioValuationPersistenceMapper.java` — domain `PortfolioValuation` ⇄ entity graph, field-for-field per [data-model.md](./data-model.md) §4; `Optional.empty()` ⇄ `null`.
- [x] T016 `BE-M/infrastructure/persistence/PortfolioValuationPersistenceAdapter.java` implements `PortfolioValuationRepository` — `upsertLatest` in **one** write transaction (own `TransactionTemplate`, matching `PortfolioPersistenceAdapter`): `deleteByPortfolioId` then insert new graph; `findByPortfolioId` read-only. **Zero writes to `portfolio`/`position`** (SC-008).
- [x] T017 `BE-T/infrastructure/persistence/PortfolioValuationPersistenceAdapterIT.java` (Testcontainers, `extends AbstractPortfolioIT` / `PostgresContainerSupport`) — **RED→GREEN**: insert full graph; a **second** `upsertLatest` for the same `portfolio_id` ⇒ 1 `portfolio_valuation` row, children replaced, **0 duplicates** (FR-021, SC-002); `NUMERIC` exact round-trip of `2100.00` (SC-001); parent delete cascades to children.

### ACL adapter to EN005

- [x] T018 [P] `BE-M/infrastructure/marketdata/EnMarketDataGatewayAdapter.java` (`@Component`) implements `MarketDataGateway` — the **only** `portfolio` class importing `com.myfinaimanager.core.marketdata.*`. Build `InstrumentIdentifier` / `SupportedCurrency` from the `String` args; delegate to `MarketDataPort` / `InstrumentProfilePort` / `FxRatePort`; map per [contracts/market-data-gateway.md](./contracts/market-data-gateway.md); **catch every `MarketDataException` subtype + `IllegalArgumentException` → `Optional.empty()`**; `Sector.UNCLASSIFIED` → empty; structured DEBUG log per unavailable outcome (no key/URL/stack).
- [x] T019 [P] `BE-T/infrastructure/marketdata/EnMarketDataGatewayAdapterTest.java` (Mockito mocks of the 3 EN005 ports) — one case per row of the translation table (each exception subtype → empty; bad currency → empty; unclassified profile → empty; happy path maps `MarketPrice`/`FxRate`/`sector`).

### Architecture fitness

- [x] T020 `StandardArchitectureRulesTest.java` — add 2 `@ArchTest` rules (18 → 20): (1) no class in `..core.portfolio.domain..` or `..core.portfolio.business..` depends on `..core.marketdata..`; (2) `..core.marketdata..` is referenced from `..core.portfolio..` only by `..core.portfolio.infrastructure.marketdata..`. Add a `fields()` (or nested-class) check that no field in `..portfolio.domain.model.PortfolioValuation` / `PositionValuation` / `SectorAllocation` / `PortfolioValuationCalculator` is `double`/`float` (SC-006). Prove each new rule non-vacuous (temp violating import → rule fails → revert).

**Checkpoint**: `./mvnw -B test -Dtest='*Portfolio*,StandardArchitectureRulesTest'` + the new IT compile and the arch rules pass; domain model, ports, contract, persistence, and the EN005 ACL exist.

---

## Phase 3: User Story 1 — Automatic valuation + creation is never lost (Priority: P1) 🎯 MVP

**Goal**: a persisted Portfolio triggers exactly one synchronous valuation; a valuation failure
never rolls back or hides the Portfolio; `GET /api/portfolios/{id}/valuation` returns the snapshot,
a synthetic `PENDING` when none, `404` for unknown, `400` for non-UUID.

**Independent test**: create a Portfolio (FD001) → a `portfolio_valuation` row exists; with the
gateway stubbed to fail everywhere → the Portfolio is persisted, listed (FD003), and the endpoint
returns `FAILED` with no fabricated numbers.

### Tests for US1 (write first — RED)

- [x] T021 [P] [US1] `BE-T/infrastructure/api/rest/PortfolioValuationControllerContractTest.java` — `@WebMvcTest(PortfolioValuationController.class)` + `swagger-request-validator-mockmvc` against `openapi.yaml`: `200` for a `COMPLETED` snapshot; `200` `{status:"PENDING", nulls, empty arrays}` when no snapshot; `404` `/problems/portfolio-not-found` for an unknown id; `400` for `portfolioId=not-a-uuid`.
- [x] T022 [P] [US1] `BE-T/PortfolioValuationOnCreationIT.java` (Testcontainers; a `@TestConfiguration` `MarketDataGateway` fake bean) — create via the FD001 flow ⇒ a snapshot row exists; **fake throws for every Position ⇒ `portfolio` + `position` rows byte-unchanged, a `FAILED` snapshot is written, `POST /api/portfolios` still returns `201`** (FR-002, SC-005, SC-008); a replayed create (same idempotency key) ⇒ **no** second valuation.
- [x] T023 [P] [US1] `BE-T/business/PortfolioValuationServiceTest.java` (Mockito: `MarketDataGateway`, `PortfolioValuationRepository`, `PortfolioRepository`) — **`PortfolioRepository.save(...)` is never invoked**; exactly one `upsertLatest`; an unknown/other-investor portfolio id ⇒ `PortfolioNotFoundException`.

### Implementation for US1

- [x] T024 [US1] `BE-M/business/ValuePortfolioUseCase.java` (port) + `BE-M/business/PortfolioValuationService.java` (`@Service`) — `value(PortfolioId)`: `PortfolioRepository.findByIdForInvestor(id, currentInvestor)` (absent ⇒ `PortfolioNotFoundException`); per Position call `MarketDataGateway.latestPrice/sector`; build `PositionInput`s + `FxContext`; call `PortfolioValuationCalculator.calculate(...)`; `PortfolioValuationRepository.upsertLatest(result)`. No `save` on `portfolio`/`position`.
- [x] T025 [US1] In `PortfolioValuationService`, gather **only the needed FX directions** (research D8): any USD Position ⇒ `fxRate("USD","EUR")`; any EUR Position ⇒ `fxRate("EUR","USD")`; single-currency Portfolio still fetches the one other-direction rate.
- [x] T026 [US1] `BE-M/business/CreatePortfolioService.java` — inject `ApplicationEventPublisher`; after `repository.save(candidate, key)` returns **and only when `!replayed`** (same guard as `recordBusinessOutcomes`), publish `new PortfolioCreatedEvent(saved.id())`. If `StandardArchitectureRulesTest` forbids `org.springframework..` in `business`, instead publish from a thin `portfolio.infrastructure` wrapper around the create controller (research D1 Alternative 2) and note the deviation.
- [x] T027 [US1] `BE-M/infrastructure/valuation/PortfolioValuationOnCreationListener.java` — `@EventListener` (synchronous) on `PortfolioCreatedEvent`; call `ValuePortfolioUseCase.value(...)` inside `try/catch(Exception)` → log `event=PortfolioValuationFailed portfolioId=… reason=…` (structured, no secrets) and, in a second independent `try/catch`, best-effort `upsertLatest` a `FAILED` snapshot; never rethrow.
- [x] T028 [P] [US1] `BE-M/infrastructure/api/rest/dto/PortfolioValuationResponse.java` (+ nested `PositionValuationDto`, `SectorAllocationDto`) — records mirroring the OpenAPI schema; decimals as `String` (match the FD003 `Portfolio` money representation — verify).
- [x] T029 [US1] `BE-M/infrastructure/api/rest/mapper/PortfolioValuationResponseMapper.java` — domain → DTO (all 4 statuses; `Optional.empty()` → `null`); plus a `pending(PortfolioId)` factory for the synthetic `PENDING` body ([data-model.md](./data-model.md) §4).
- [x] T030 [US1] `BE-M/infrastructure/api/rest/PortfolioValuationController.java` (`@RestController`) — `GET /api/portfolios/{portfolioId}/valuation`, `{portfolioId}` typed `UUID`; resolve current Investor, `PortfolioRepository.findByIdForInvestor` (absent ⇒ `PortfolioNotFoundException`); then `PortfolioValuationRepository.findByPortfolioId` → present maps the snapshot, absent returns `mapper.pending(id)` with `200`.
- [x] T031 [US1] `BE-M/infrastructure/api/rest/PortfolioExceptionHandler.java` — add `PortfolioValuationController.class` to `@RestControllerAdvice(assignableTypes = {...})`; no change to the existing handler methods.
- [x] T032 [P] [US1] `BE-T/infrastructure/api/rest/PortfolioValuationResponseMapperTest.java` — unit: COMPLETED / PARTIAL / FAILED / synthetic PENDING; an unvalued `PositionValuation` → DTO monetary fields `null` (never `"0"`).
- [x] T033 [US1] Make T021 / T022 / T023 green. Re-run the FD001 (`CreatePortfolio*IT`) and FD003 (`PortfolioQueryController*`) suites — unchanged and green (FR-034).

**Checkpoint**: creation triggers a persisted valuation; failure is contained; the endpoint works. With the stub calculator every valuation is `FAILED`/`PENDING` — real numbers arrive in US2.

---

## Phase 4: User Story 2 — Deterministic EUR & USD valuation (Priority: P1) 🎯 MVP

**Goal**: per-Position `nativeMarketValue = quantity × price`; EUR & USD values via FX; Portfolio
totals as exact sums — reproducible, `BigDecimal`, no LLM.

**Independent test**: `PortfolioValuationCalculatorTest` case C1 → AAPL 2000 USD / 1600 EUR,
SAN 500 EUR / 625 USD, totals 2100.00 EUR / 2625.00 USD, byte-identical across repeated runs.

### Tests for US2 (write first — RED)

- [x] T034 [P] [US2] `BE-T/domain/model/PortfolioValuationCalculatorTest.java` — case **C1** core rows from [contracts/valuation-calculation.md](./contracts/valuation-calculation.md): native value (AC-002), USD→EUR (AC-003), EUR→USD (AC-004), totals = Σ exact (AC-005/006), and **C8** determinism (call 3×, assert `.equals`). RED against the `UnsupportedOperationException` stub.

### Implementation for US2

- [x] T035 [US2] `PortfolioValuationCalculator` — implement Step 1 (per-Position native value, `quantity.multiply(price)`, exact) + Step 2 (USD/EUR values via `FxContext`, absent when the needed rate is absent) + Step 3 (totals present only when every valued Position has that side). Emit `PositionValuation` with `valued=true`, `sector = input.sector.orElse("Unclassified")`. Still return a placeholder `status`/no-weights for now (US3/US4 finish it) — or implement the trivial "all valued & both totals" → `COMPLETED` to keep C1 green.
- [x] T036 [US2] `BE-T/business/PortfolioValuationServiceTest.java` — add: with a mocked gateway returning the C1 values, the persisted `PortfolioValuation` carries totals `2100`/`2625` and the 2 Position values (SC-001 at the service level).
- [x] T037 [US2] `BE-T/PortfolioValuationValuesIT.java` (Testcontainers, fake gateway → C1 values) — create the AAPL+SAN Portfolio ⇒ persisted `portfolio_valuation.total_value_eur` `compareTo("2100.00") == 0`, `total_value_usd` `== "2625.00"`, `position_valuation` rows exact (SC-001).

**Checkpoint (MVP)**: a created Portfolio is valued in EUR and USD with exact, reproducible numbers, shown via the endpoint. US1+US2 = demoable MVP.

---

## Phase 5: User Story 3 — Position weights & sector allocation (Priority: P1)

**Goal**: each Position's weight = `valueInEUR / totalValueEUR` (canonical EUR); sector allocation
= valued Positions grouped by sector with `sectorWeight` fractions; both sum to ~100 %.

**Independent test**: C1 → AAPL weight `0.761904761905`, SAN `0.238095238095`;
`Technology 76.19 %`, `Financial Services 23.81 %`; C6 → missing sector → `Unclassified`, still `COMPLETED`.

### Tests for US3 (write first — RED)

- [x] T038 [P] [US3] `PortfolioValuationCalculatorTest` — add: weights (AC-007), sector grouping + `sectorValueEUR` + `sectorWeight` (AC-008), Σ-weights and Σ-sector-weights ≈ 1 to 12-dp (FR-013), **C6** (missing sector → `Unclassified`, still `COMPLETED`), **C7** (`totalValueEUR` absent/0 → no weights, no `SectorAllocation`, no `NaN`/`0 %`).

### Implementation for US3

- [x] T039 [US3] `PortfolioValuationCalculator` — implement Step 4: `portfolioWeight = valueInEUR.divide(totalValueEUR, 12, HALF_UP)` only when `totalValueEUR` present and `> 0`; group valued Positions **that have `valueInEUR`** by `sector`; `sectorValueEUR = Σ` (exact); `sectorWeight` same divide; order `SectorAllocation` by descending `sectorValueEUR` then `sector`. Guard: `totalValueEUR` absent/0 ⇒ emit no weights and no allocation rows.
- [x] T040 [US3] `BE-T/PortfolioValuationValuesIT.java` — extend: persisted `position_valuation.portfolio_weight` and `sector_allocation` rows match C1 (2 sectors, fractions to 12 dp).
- [x] T041 [US3] `PortfolioValuationResponseMapperTest` — add: `sectors[]` populated and ordered; `portfolioWeight` serialized as the fraction string; absent when no EUR basis.

**Checkpoint**: weights + sector allocation are computed, persisted, and exposed.

---

## Phase 6: User Story 4 — Partial/failed explicit; a missing price is never zero (Priority: P1)

**Goal**: `ValuationStatus` per the D5 rules; an unpriced Position is unvalued (never `0`) → `PARTIAL`;
`FAILED` only when nothing is usable; missing FX for the cross total → `PARTIAL` with the native total intact.

**Independent test**: C2 (missing price → PARTIAL, unvalued not 0), C3 (missing FX → PARTIAL, native
total stands), C4 (nothing priced → FAILED), C5 (single-currency, other FX missing → PARTIAL).

### Tests for US4 (write first — RED)

- [x] T042 [P] [US4] `PortfolioValuationCalculatorTest` — add the full D5 status table + cases **C2, C3, C4, C5**; assert every monetary field of an unvalued `PositionValuation` is absent (FR-017), and `marketDataAsOf` / `fxDataAsOf` = the earliest observed instant actually used (Step 6).
- [x] T043 [P] [US4] `BE-T/business/PortfolioValuationServiceTest.java` — add: gateway returns empty for one Position's price ⇒ that Position unvalued, status `PARTIAL`, one `upsertLatest`; gateway empty for all ⇒ `FAILED`.

### Implementation for US4

- [x] T044 [US4] `PortfolioValuationCalculator` — implement Step 5 (status: `FAILED` iff `Vv==0` or neither total producible; `COMPLETED` iff `Vv==Vp` and every valued Position has both EUR & USD values — missing sector OK; else `PARTIAL`) + Step 6 (freshness min-instants). Never emit `PENDING`.
- [x] T045 [US4] `BE-T/PortfolioValuationPartialIT.java` (Testcontainers, fake gateway) — create with one price missing ⇒ persisted status `PARTIAL`, unvalued `position_valuation` has `NULL` `market_price` / `native_market_value` / `value_eur` / `value_usd` / `portfolio_weight` (SC-004); create during a full gateway outage ⇒ `FAILED`, and `portfolio` / `position` row counts + columns unchanged (SC-008).
- [x] T046 [US4] Confirm `PortfolioValuationOnCreationListener`'s best-effort `FAILED` path writes a well-formed snapshot (status `FAILED`, positions listed as unvalued, no totals) when `value(...)` itself throws.

**Checkpoint**: all four statuses are correct end to end; no fabricated zeros anywhere in domain, DB, or DTO.

---

## Phase 7: User Story 5 — The Portfolio detail shows valuation, weights, sectors & state (Priority: P1)

**Goal**: extend the FD003 detail (additively) with EUR/USD totals, per-Position valuation columns
(shown only when valued), sector allocation percentages, and an explicit valuation-state line —
read-only, 2-dp display, design-system compliant, no fabricated zeros.

**Independent test**: `ng test` on the extended detail — COMPLETED shows totals + columns + sector
list + "Valued at …"; PARTIAL blanks the unvalued row + "Partial valuation — … N position(s)";
absent/FAILED shows "Valuation unavailable" and no `0.00`/`0 %`.

### Tests for US5 (write first — RED)

- [x] T047 [P] [US5] `FE/portfolio-valuation.service.spec.ts` — `getValuation(id)` maps the body; `404` → `'not-found'`; other error → `null`.
- [x] T048 [P] [US5] `FE/valuation-format.spec.ts` — `money(amount, 'EUR'|'USD')` and `percent(fraction)` → 2-dp strings; `null` → `'—'`.

### Implementation for US5

- [x] T049 [P] [US5] `FE/portfolio.models.ts` — add `ValuationStatus`, `PortfolioValuationView`, `PositionValuationView`, `SectorAllocationView` (fields nullable exactly as the API).
- [x] T050 [P] [US5] `FE/valuation-format.ts` — `money` / `percent` helpers via `Intl.NumberFormat`, `null` → `'—'` (FR-031).
- [x] T051 [US5] `FE/portfolio-valuation.service.ts` — `getValuation(id): Observable<PortfolioValuationView | 'not-found' | null>`; `GET /api/portfolios/:id/valuation`; mirror `portfolio-query.service.ts`.
- [x] T052 [US5] `FE/portfolio-detail.page.ts` — additively: a **totals card** (€ / $, dashed when null); Position-table columns `Market Price` / `Market Value` / `Value in EUR` / `Value in USD` / `Portfolio Weight` (cell = 2-dp value when `valued` & present, else `—`) + `Sector`; a **sector allocation** block (`sector` + `xx.xx %`); a **valuation-state line** (`PENDING`→"Valuation pending", `COMPLETED`→"Valued at {calculatedAt}", `PARTIAL`→"Partial valuation — market data unavailable for {N} position(s)", `FAILED`/`null`→"Valuation unavailable"). No control edits a value (FR-032). Keep FD003's always-on columns.
- [x] T053 [US5] `FE/portfolio-detail.page.spec.ts` — extend: COMPLETED (totals + columns + sector list + "Valued at"); PARTIAL (blank cells for the unvalued row + count in the message); absent/FAILED ("Valuation unavailable", **assert no `0.00` / `0 %`** in any valuation cell/total); no edit control present.
- [x] T054 [US5] Design-system pass on the new markup (`product/ux/design-system.md`): dark compact table, right-aligned numeric columns, consistent currency/percent formatting, explicit state indicator, contrast/keyboard. Make T047 / T048 / T053 green; existing FD003 detail/list specs stay green.

**Checkpoint**: the detail renders the valuation in every state without fabricated numbers.

---

## Phase 8: User Story 6 — Both journeys proven E2E against a controlled Finnhub boundary (Priority: P2)

**Goal**: `./e2e.sh` gains E2E-001 (create → value → display, deterministic stub) and E2E-002
(provider failure → Portfolio survives, no fabricated zeros); only the Finnhub boundary is
controlled; no outbound Internet; existing specs stay green.

### Implementation for US6

- [x] T055 [US6] `implementation/platform/e2e/finnhub-stub/` — a small committed HTTP stub (Node `http`/Express **or** `wiremock/wiremock` + `mappings/`) serving `GET /quote?symbol=`, `GET /stock/profile2?symbol=`, `GET /forex/rates?base=` with canned JSON copied from `specs/EN005-establish-finnhub-market-data-integration/contracts/finnhub-provider-contract.md` §5. Two modes (env var or two mapping sets): **normal** (AAPL 200 USD/Technology, SAN.MC 5 EUR/Financial Services, USD→EUR 0.80, EUR→USD 1.25) and **degraded** (`429` + empty bodies). Add a `Dockerfile` if not using a public image.
- [x] T056 [US6] `implementation/platform/infrastructure/local/compose.e2e.yaml` — add the `finnhub-stub` service (profile `e2e`); on `backend` set `FINNHUB_API_KEY=e2e-stub` and `FINNHUB_BASE_URL=http://finnhub-stub:8080`. Do **not** change `compose.yaml`.
- [x] T057 [US6] Confirm `implementation/platform/backend/core-service/src/main/resources/application.yml` binds `finnhub.base-url: ${FINNHUB_BASE_URL:https://finnhub.io/api/v1}` (EN005). Add the env indirection if the property is currently hard-coded. **No production default change** (SC-012).
- [x] T058 [P] [US6] `implementation/platform/e2e/support/valuation.ts` — helpers to read the totals card, a Position row's valuation cells, the sector-allocation list, and the state line from the FD003 detail.
- [x] T059 [US6] `implementation/platform/e2e/tests/fd004-valuation.spec.ts` (E2E-001) — stub normal; create AAPL(`XNAS`, USD, qty 10) + SAN(`XMAD`, EUR, qty 100) via the FD001 helper; open the detail; assert creation + Home-list presence, totals `€2,100.00` / `$2,625.00`, AAPL & SAN valuation cells + sectors, weights `76.19 %` / `23.81 %`, sector allocation `Technology 76.19 %` / `Financial Services 23.81 %`, state line "Valued at …", Portfolio still present after reload. Wire any needed `playwright.config.ts` project/`dependencies`.
- [x] T060 [US6] `implementation/platform/e2e/tests/fd004-provider-failure.spec.ts` (E2E-002) — stub degraded; create a one-Position Portfolio; assert it is persisted + in the Home list, the detail shows a valuation-state message, and **no** `0.00` / `0 %` in any valuation cell or total (SC-005).
- [x] T061 [US6] Run `./e2e.sh` — E2E-001 + E2E-002 green; FD001/FD002/FD003 + `platform-smoke` specs green; spec count +2; no request reaches `finnhub.io` (SC-010).

**Checkpoint**: both mandatory closure-gate journeys pass against the real containerized stack, offline.

---

## Phase 9: Polish & cross-cutting

- [x] T062 [P] `implementation/platform/backend/core-service/README.md` — document the `portfolio` valuation area, the `MarketDataGateway` ACL, and `GET /api/portfolios/{portfolioId}/valuation`.
- [x] T063 [P] `implementation/platform/README.md` — add the FD004 capability row (auto-valuation + allocation; `V4` migration; the valuation endpoint).
- [x] T064 Full backend gate: from `implementation/platform/backend/core-service` run `./mvnw -B clean verify` — Surefire + Failsafe green, `StandardArchitectureRulesTest` **20/20**, JaCoCo bundle **≥ 90 % line AND branch** (SC-009). Fix coverage by adding behavior tests, not by adding excludes.
- [x] T065 Full frontend gate: `npm test` (Node 20.19.1, `CHROME_BIN` set) — all green including the FD004 specs (SC-009).
- [x] T066 Runtime smoke per [quickstart.md](./quickstart.md) §C: `./start.sh`; health `UP`; create a Portfolio; `GET …/valuation` ⇒ `FAILED`/`PENDING` (blank local key); `not-a-uuid` ⇒ `400`; random UUID ⇒ `404`; `./stop.sh` ×2 idempotent. Portfolio still listed (SC-005).
- [x] T067 Execute [quickstart.md](./quickstart.md) §A–§F end to end; capture evidence into `specs/FD004-portfolio-valuation-and-allocation/pr-evidence.md` and tick §F.
- [x] T068 Scope review (SC-012): `git diff` shows `openapi.yaml` additive only (FD003 `Portfolio` + `GET /api/portfolios[/{id}]` unchanged), `V4` the only migration, no `portfolio`/`position`/EN004 table change, no `double`/`float` for money/rate, no new Maven/npm dependency, no scheduler/broker/new deployable/new provider, **no `product/` edit**. Record in `pr-evidence.md`.

---

## Dependencies & execution order

- **Phase 1 (Setup)** → **Phase 2 (Foundational)** blocks everything.
- **Phase 3 (US1)** needs Phase 2. Delivers the trigger + endpoint with a stub calculator.
- **Phase 4 (US2)** needs Phase 2; independent of US1 for the calculator, but the end-to-end
  demo/IT (T036–T037) uses the US1 service + persistence. Do US1 then US2 for the MVP.
- **Phase 5 (US3)** and **Phase 6 (US4)** both extend `PortfolioValuationCalculator.java` +
  `PortfolioValuationCalculatorTest.java` — run **after US2 and sequentially** (US3 then US4);
  not `[P]` against each other.
- **Phase 7 (US5)** needs the endpoint (US1) live; the calculator depth (US2–US4) makes the E2E
  meaningful but the component specs mock the service, so US5 can proceed once US1 is done.
- **Phase 8 (US6)** needs US1–US5 (real end-to-end behavior + UI).
- **Phase 9 (Polish)** last.

### Within a story

- Test tasks (RED) before implementation.
- Domain model → business service → infrastructure adapter/controller → mapper/DTO.
- `[P]` = different files, no ordering dependency.

## Parallel opportunities

- **Phase 2**: T004–T008 all `[P]`; T013 + T014 `[P]`; T018 + T019 `[P]` (after T007).
- **Phase 3**: T021 + T022 + T023 `[P]` (tests); T028 `[P]` with T024–T027.
- **Phase 4/5/6**: the RED test task is `[P]` with nothing in-story (single calculator file).
- **Phase 7**: T047 + T048 `[P]`; T049 + T050 `[P]`.
- **Phase 9**: T062 + T063 `[P]`.

## Implementation strategy

1. **MVP** = Phase 1 → Phase 2 → Phase 3 (US1) → Phase 4 (US2). Stop, run T064/T065, demo: a created
   Portfolio is valued in EUR & USD, survives provider failure, and is visible via the endpoint.
2. Add US3, then US4 — allocation + explicit partial/failed correctness.
3. Add US5 — the visible payoff in the FD003 detail.
4. Add US6 — the two mandatory E2E closure gates.
5. Polish + `/project-verify FD004-portfolio-valuation-and-allocation`.

## Notes

- No SDD extension hooks configured (`.specify/extensions.yml` absent).
- Do not commit/push — the repo is intentionally uncommitted; closure is human-governed.
- Every SC (SC-001…SC-013) and AC (AC-001…AC-012) has an owning task — see [quickstart.md](./quickstart.md) §E.

---

# Revision 2 — Two mandatory allocation pie charts (2026-09-05)

**Trigger**: FD004 Feature Definition update (§17.1–§17.4, BR-014…BR-017, AC-013…AC-015, §26 checks
11–15, §28, §29.16–§29.22, §31 re-signed). Spec delta: **US7**, `FR-044…FR-049`, `SC-014`/`SC-015`,
`A12`–`A14`. Plan: `plan.md` "Revision 2" section; research `D-chart-1…6`.

**Scope**: **frontend + E2E only.** No backend / domain / business / persistence / API / contract /
migration / npm-dependency change. Charts are driven entirely by the existing `PortfolioValuation`
response (`positions[].portfolioWeight`, `sectors[].sectorWeight`).

**Tests**: `PieChartComponent` slice geometry is deterministic ⇒ **TDD RED-first** (constitution VII).

**Confirmed 2026-09-05**: the 8 R2 Open Decisions (OD-R2-1…8) accepted as recommended.

Task IDs continue from v1 (last was T068).

## Phase 10: Checkpoint R2-A — E2E market-data stub serves Frankfurter FX (prerequisite, A14)

**Why first**: EN005 Revision 2 (C1) moved FX to Frankfurter; the e2e `finnhub-stub` only serves
Finnhub endpoints, so `./e2e.sh` currently can't reach a `COMPLETED` FD004 valuation. This must be
green before the chart E2E assertions are added. It also advances EN005-R2 checkpoint C4.

- [x] T069 [US6] `implementation/platform/e2e/finnhub-stub/server.js` — add `GET /v1/latest?base=&symbols=` → Frankfurter response shape `{ "amount":1.0, "base":"<BASE>", "date":"<today ISO date>", "rates": { "<SYMBOL>": <rate> } }` with `USD→EUR = 0.80`, `EUR→USD = 1.25` (the deterministic FD004 E2E-001 rates). Keep `/quote` + `/stock/profile2`; leave `/forex/rates` (harmless, unused after EN005-R2).
- [x] T070 [US6] `implementation/platform/infrastructure/local/compose.e2e.yaml` — add `FRANKFURTER_BASE_URL: http://finnhub-stub:8080` to the `backend` service `environment` (alongside `FINNHUB_BASE_URL` / `FINNHUB_API_KEY`).
- [x] T071 [US6] Run `./e2e.sh` — **existing** `fd004-valuation.spec.ts` (E2E-001) reaches a `COMPLETED` valuation again (totals `€2,100.00` / `$2,625.00`, weights `76.19 %` / `23.81 %`); `fd004-provider-failure.spec.ts` + FD001/FD002/FD003 + `platform-smoke` still green; no request to `finnhub.io` / `api.frankfurter.dev`.

**Checkpoint R2-A**: `./e2e.sh` green (pre-chart).

## Phase 11: Checkpoint R2-B — `PieChartComponent` (US7, TDD RED-first)

- [x] T072 [P] [US7] `implementation/platform/frontend/web/src/styles/_tokens.scss` — add a dark categorical palette `--chart-1 … --chart-8` (research D-chart-3 values) under `:root`.
- [x] T073 [P] [US7] `implementation/platform/frontend/web/src/app/portfolio/portfolio.models.ts` — add `export interface AllocationSlice { label: string; fraction: number; }`.
- [x] T074 [US7] `implementation/platform/frontend/web/src/app/portfolio/pie-chart.component.spec.ts` — **RED**: `@Input() slices: AllocationSlice[]`, `@Input() title: string`. Assert: N slices → N `<path>` (or a single `<circle>` when N == 1); 2 slices where the first is > 50 % → that path's `d` has `largeArcFlag = 1`; slices that don't sum to exactly 1 (rounding) still close the ring; legend `<li>` per slice with text = `label` + ` ` + `xx.xx %` (via `percent()`); `<svg role="img">` with an `aria-label` naming the title + top slices; `slices = []` → renders nothing meaningful (no `<path>`, no `<circle>`); a slice with `fraction = 0` produces no visible wedge.
- [x] T075 [US7] `implementation/platform/frontend/web/src/app/portfolio/pie-chart.component.ts` — standalone component; inline SVG `viewBox="0 0 100 100"`, arc-path geometry per research D-chart-1 (start at 12 o'clock, clockwise; `largeArc` rule; single-slice → `<circle>`); 1 px `--color-surface` stroke between slices; slice colour `--chart-${(i % 8) + 1}`; an HTML `<ul>` legend (`label` + `percent(String(fraction))`); `role="img"` + computed `aria-label`; design-system card styling (`--color-surface-elevated`, `--radius-md`, spacing tokens). **No** financial calculation — it only normalises and draws the given fractions. Make T074 green.

## Phase 12: Checkpoint R2-C — Portfolio detail extension (US7)

- [x] T076 [US7] `implementation/platform/frontend/web/src/app/portfolio/portfolio-detail.page.ts` — add `import { PieChartComponent }` to `imports`; add `tickerSlices()` (valued positions with non-null `portfolioWeight` → `{label: ticker, fraction: Number(portfolioWeight)}`, sorted desc by fraction), `sectorSlices()` (`sectors[]` → `{label: sector, fraction: Number(sectorWeight)}`, sorted desc), and `showCharts()` (research D-chart-2 predicate). In the template, when `showCharts()`, render a responsive 2-up block (CSS grid `repeat(auto-fit, minmax(240px, 1fr))`, stacks on narrow) with `<app-pie-chart [slices]="tickerSlices()" title="Allocation by Ticker">` and `<app-pie-chart [slices]="sectorSlices()" title="Allocation by Sector">`. When `!showCharts()`, render neither (the valuation-state line already stands). No control edits any value (FR-032).
- [x] T077 [US7] `implementation/platform/frontend/web/src/app/portfolio/portfolio-detail.page.spec.ts` (extend) — `COMPLETED` (E2E-001 numbers): both `app-pie-chart` present; ticker legend contains `AAPL` + `76.19%` and `SAN` + `23.81%`; sector legend contains `Technology` + `76.19%` and `Financial Services` + `23.81%`; the chart legend percentages **equal** the Position-weight / sector-allocation percentages shown elsewhere in the detail (SC-014). `PARTIAL` (one unvalued position): charts render the valued portion **and** the "Partial valuation — …" message is present (SC-015). `FAILED` / `PENDING` / valuation `null` / no-EUR-basis (all `portfolioWeight` null): **no** `app-pie-chart` element in the DOM; no `0 %` / `0.00 %` text anywhere (SC-015). Existing FD003/FD004 detail assertions unchanged.
- [x] T078 [US7] Run `cd implementation/platform/frontend/web && nvm use 20.19.1 && CHROME_BIN=… npm test` — all green incl. T074 + T077; FD003 detail/list + prior FD004 specs unaffected.

**Checkpoint R2-C**: `ng test` green.

## Phase 13: Checkpoint R2-D — E2E chart assertions (US6, FD004 §26 checks 11–15)

- [x] T079 [P] [US6] `implementation/platform/e2e/support/valuation.ts` — add helpers: `tickerChart(page)` / `sectorChart(page)` locators; `chartSlicePercents(chart)` → `{ label → percentText }` read from the legend; `chartVisible(chart)`.
- [x] T080 [US6] `implementation/platform/e2e/tests/fd004-valuation.spec.ts` (extend) — after opening the detail and the existing table/total assertions, assert: the **Allocation by Ticker** chart is visible with legend slices `AAPL` ≈ `76.19 %` and `SAN` ≈ `23.81 %`; the **Allocation by Sector** chart is visible with `Technology` ≈ `76.19 %` and `Financial Services` ≈ `23.81 %`; each chart's slice percentages sum to `100 %` (±0.01) and match the weights shown in the Position table / sector list (§26 checks 11–15, SC-014); the Portfolio remains persisted. `fd004-provider-failure.spec.ts` unchanged (E2E-002 has no `COMPLETED` valuation → no charts, already asserted "no fabricated values").
- [x] T081 [US6] Run `./e2e.sh` — `fd004-valuation.spec.ts` green with the chart checks; all other specs green; offline.

**Checkpoint R2-D**: `./e2e.sh` green (with charts).

## Phase 14: Checkpoint R2-E — Docs, full verify, scope review

- [x] T082 [P] [US7] `implementation/platform/README.md` — FD004 capability row / paragraph: note the Portfolio detail now shows two mandatory allocation pie charts (by ticker, by sector) driven by the deterministic valuation.
- [x] T083 [P] [US7] `implementation/platform/backend/core-service/README.md` — the FD004 section: one line noting the frontend charts consume the existing `PortfolioValuation` response (no API change).
- [x] T084 [US7] Full frontend gate: `npm test` green (Node 20.19.1). Backend is untouched — a `./mvnw -q -o compile` sanity check only (no Java changed). `./e2e.sh` green.
- [x] T085 [US7] `specs/FD004-portfolio-valuation-and-allocation/pr-evidence.md` — append a "Revision 2 — allocation charts" section: gate results (`ng test`, `./e2e.sh`), the AC-013/AC-014/AC-015 + SC-014/SC-015 evidence, and the R2 scope review.
- [x] T086 [US7] Scope review (SC-012): `git diff` shows **`package.json` unchanged**; `openapi.yaml` / `pom.xml` / **all backend Java** unchanged; **no migration**; changes limited to `frontend/web/src/app/portfolio/*`, `frontend/web/src/styles/_tokens.scss`, `e2e/finnhub-stub/server.js`, `compose.e2e.yaml`, `e2e/tests/fd004-valuation.spec.ts`, `e2e/support/valuation.ts`, the two READMEs, and the `specs/FD004-…` docs; **no `product/` or `.specify/` edit** (the FD004 §17/§29 update is the owner's, §31 re-signed).
- [ ] T087 [US7] `/project-verify FD004-portfolio-valuation-and-allocation` (re-close with the charts).

**Checkpoint R2-E**: all gates green; ready for human closure.

## Revision 2 — dependencies & order

- **R2-A (T069–T071)** MUST land first — `./e2e.sh` green — before any chart E2E assertion.
- **R2-B (T072–T075)**: T072 + T073 `[P]`; T074 (RED) before T075.
- **R2-C (T076–T078)** needs R2-B.
- **R2-D (T079–T081)** needs R2-A + R2-C.
- **R2-E (T082–T087)** last; T082 + T083 `[P]`.

## Revision 2 — coverage

| Requirement / gate | Task(s) |
|---|---|
| FR-044 both charts mandatory when data available | T076, T077, T080 |
| FR-045 ticker chart — slice per valued Position, EUR weight, labelled | T074, T075, T076, T077 |
| FR-046 sector chart — slice per sector, `Unclassified` slice, labelled | T074, T075, T076, T077 |
| FR-047 charts driven only by backend weights; sum ≈ 100 %; consistent | T075 (no calc), T077 (legend % == table %), T080 |
| FR-048 PARTIAL shows valued portion + message; no chart for no-EUR-basis/absent/FAILED/PENDING | T076 (`showCharts`), T077, SC-015 |
| FR-049 design system, a11y, responsive, no new dependency | T072, T075, T086 |
| AC-013 / AC-014 / AC-015 | T077 (unit) + T080 (E2E) |
| SC-014 chart %s match & sum to 100 % | T077, T080 |
| SC-015 charts suppressed / partial behaviour | T077 |
| §26 checks 11–15 (E2E-001) | T080 |
| §28 closure gate (charts present + consistent) | T081, T087 |
| A14 — E2E stubs Frankfurter FX | T069, T070, T071 |
| SC-012 — no dependency / no backend / no API change | T086 |

---

# Revision 2.1 — Portfolio-detail table trim (2026-09-04)

Owner request (FD004 §17 latitude — FR-029 is "MAY be extended"): in the Portfolio detail,
(a) **remove** the native **"Market value"** column (redundant with Value EUR / Value USD),
(b) show **"Market price" with the Position's native currency** (`200.00 USD`),
(c) **remove** the standalone **"Sector allocation"** list — the Allocation by Sector chart legend
already carries every sector + its percentage (FR-028). Frontend + docs + E2E only — no backend,
no API/contract, no schema, no dependency, no `product/` edit.

## Phase 15: Checkpoint R2.1 — detail-table trim (US7)

- [x] T088 [P] [US7] `specs/FD004-…/spec.md` — Clarifications "Session 2026-09-05 (part 2)"; FR-028 (sector % via chart legend, no list), FR-029 (drop `Market Value`, `Market Price` shows native currency), US3 AS5 + US5 AS1 + the US5 narrative updated.
- [x] T089 [US7] `frontend/web/src/app/portfolio/portfolio-detail.page.ts` — remove the `Market value` `<th>`/`<td>` + `marketValueOf()`; `priceOf()` returns `"<price> <nativeCurrency>"` when valued (else `—`); delete the `<section class="sectors">` block, its styles and `pct()`; keep `sectors()` (feeds `sectorSlices()`); refresh the class doc comment.
- [x] T090 [US7] `frontend/web/src/app/portfolio/portfolio-detail.page.spec.ts` — drop the `.sectors` assertions; assert the `Market value` header is **absent**, `Market price` present, and the cell shows `200.00 USD` / `160.00 EUR`; cross-check the sector chart legend % against the Position table `Weight` column.
- [x] T091 [P] [US7] `e2e/support/valuation.ts` — remove the now-unused `sectorAllocationText` helper.
- [x] T092 [US7] `e2e/tests/fd004-valuation.spec.ts` — drop the `sectorAllocationText` import + the sector-list block; assert `.sectors` count 0, no `Market value` header, and `200.00 USD` / `5.00 EUR` in the position rows; keep the sector-chart-legend assertions.
- [x] T093 [P] [US7] `implementation/platform/README.md` + `backend/core-service/README.md` — FD004 paragraph: "per-Position market price (with currency) / EUR / USD value / weight / sector"; sector % via the sector chart legend; no separate list.
- [x] T094 [US7] Frontend gate `npm test` green (Node 20.19.1); `./e2e.sh` green; backend untouched.
- [x] T095 [US7] `pr-evidence.md` — "Revision 2.1" section (gate results + scope review).

**Checkpoint R2.1**: all gates green; folded into the FD004 closure.

## Revision 2.1 — coverage

| Requirement / gate | Task(s) |
|---|---|
| FR-028 sector % surfaced via the Allocation by Sector chart legend, no standalone list | T088, T089, T090, T092 |
| FR-029 `Market Value` dropped; `Market Price` shows native currency | T088, T089, T090, T092 |
| SC-012 — no dependency / backend / API / schema change | T089 (frontend-only), T094 |
| §28 closure gate still satisfied (charts present + consistent) | T094 |
