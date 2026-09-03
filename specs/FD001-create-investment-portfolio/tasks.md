---
description: "Task list for FD001 — Create Investment Portfolio"
---

# Tasks: Create Investment Portfolio (FD001)

**Input**: Design documents from `/specs/FD001-create-investment-portfolio/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/openapi/portfolios.create.yaml`, `quickstart.md`

**Governance**: `.specify/memory/constitution.md` v1.0.0 (principles I–VIII); `product/engineering/{development-rules,testing-strategy,definition-of-done}.md`

**Tests**: **Included.** The project mandates TDD for deterministic domain logic (DR-004, constitution VII), a Testcontainers integration test for application-managed persistence (testing-strategy §3), a contract test for external REST (AR-011), and — since the FD001 §13 revision — an automated **browser end-to-end test** of the critical creation journey (**E2E-001**, Phase 9) run on the `EN002` containerized foundation. FD001 §13 / §16 make E2E-001 a **mandatory closure gate**.

> **How this feature is sliced.** FD001 is one tight vertical over a single API operation
> (`POST /api/portfolios`). **Phase 2 (Foundational) delivers the complete, tested backend** — the
> domain aggregate with every business rule, atomic persistence, idempotency, and the REST
> operation — because the rules are one cohesive TDD artifact and US1 cannot work without them.
> **US1** is then the minimal browser flow (the demoable MVP). **US2–US5** add the frontend
> affordances and end-to-end acceptance coverage for each behaviour slice. **Phase 9** adds the one
> mandatory browser E2E test (E2E-001) into the `EN002` foundation. Each story is still
> independently testable and adds user-visible value.

**Path conventions**
- Backend main: `implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core/portfolio/`
- Backend test: `implementation/platform/backend/core-service/src/test/java/com/myfinaimanager/core/portfolio/`
- Backend resources: `implementation/platform/backend/core-service/src/main/resources/`
- Frontend: `implementation/platform/frontend/web/src/app/portfolio/`
- Contract: `implementation/platform/contracts/openapi/openapi.yaml`
- **E2E test**: `implementation/platform/e2e/tests/FD001-create-portfolio.spec.ts` (run via `implementation/platform/e2e.sh` — `EN002`)

---

## Phase 1: Setup

- [X] T001 Merge the `POST /api/portfolios` operation and its schemas (`CreatePortfolioRequest`, `PositionInput`, `Portfolio`, `Position`, `Problem`, `ValidationProblem`) from `specs/FD001-create-investment-portfolio/contracts/openapi/portfolios.create.yaml` into `implementation/platform/contracts/openapi/openapi.yaml` — replace `paths: {}`, bump `info.version` to `0.1.0`, keep the file self-consistent. *(Authored in OpenAPI **3.0.3**, not 3.1 — the contract-test validator handles 3.0.3 parameter validation reliably; see research.md D9. `ValidationProblem` is a flat object rather than `allOf` for the same tooling reason.)*
- [X] T002 [P] Add the contract-test dependency `com.atlassian.oai:swagger-request-validator-mockmvc` (test scope) to `implementation/platform/backend/core-service/pom.xml`.
- [X] T003 [P] Enable the JaCoCo coverage gate in `implementation/platform/backend/core-service/pom.xml` — bind the `check` goal at ≥ 90% line and branch (bundle level), keeping the EN001 exclusions (`CoreServiceApplication`, `bootstrap/**`); add `com/myfinaimanager/core/portfolio/adapter/in/web/*Request*`, `*Response*` DTOs are NOT excluded.
- [X] T004 [P] Create the backend capability package tree under `.../core/portfolio/`: `domain/`, `application/port/in/`, `application/port/out/`, `adapter/in/web/`, `adapter/out/persistence/`, each with a `package-info.java` documenting its layer + inward-dependency rule (mirror the EN001 `platform/*` convention).
- [X] T005 [P] Create the frontend feature directory `implementation/platform/frontend/web/src/app/portfolio/` and `portfolio-creation.models.ts` with the view models `PortfolioDraft` / `PositionDraft` (mirror the contract; amounts as strings — data-model.md §4).
- [X] T006 [P] Set `spring.mvc.problemdetails.enabled: true` in `implementation/platform/backend/core-service/src/main/resources/application.yml` (RFC 9457 error bodies — research.md D4).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The complete, tested backend for creating a portfolio, plus the frontend scaffolding all stories build on.

**⚠️ CRITICAL**: No user-story phase can be completed until this phase is done.

### Persistence

- [X] T007 Create Flyway migration `implementation/platform/backend/core-service/src/main/resources/db/migration/V2__portfolio.sql` — tables `investor`, `portfolio`, `position` with all columns, `CHECK` and `UNIQUE` constraints from `data-model.md` §3 (`portfolio.idempotency_key` NOT NULL UNIQUE; `position` UNIQUE `(portfolio_id, ticker, market)`; `NUMERIC` for quantity/price; `position` checks for quantity>0, price>0-or-null, date≤current_date, price-currency match); seed one `investor` row with a fixed well-known UUID, `display_name='Default Investor'`, `preferred_currency='EUR'`.

### Domain — value objects (TDD: write the failing test first, then implement)

- [X] T008 [P] Test + implement UUID identity value objects `PortfolioId`, `PositionId`, `InvestorId` in `.../portfolio/domain/` (`Ids` may share one test file `domain/IdentityValueObjectsTest.java`).
- [X] T009 [P] Test + implement `PortfolioName` (`domain/PortfolioName.java`, test `domain/PortfolioNameTest.java`) — non-blank after `strip()`, length 1–120, stored trimmed (BR-001, AC-003, A7).
- [X] T010 [P] Test + implement `Ticker` and `Market` (`domain/Ticker.java`, `domain/Market.java`, tests alongside) — non-blank, upper-cased; `Market` keeps an ISO 10383 MIC shape as-is else stores verbatim (BR-003, FR-015, A5).
- [X] T011 [P] Test + implement `Currency` (`domain/Currency.java` + test) — exactly 3 ASCII uppercase letters (ISO 4217 shape only, no registry lookup) (BR-006, FR-016, A5).
- [X] T012 [P] Test + implement `Quantity` (`domain/Quantity.java` + test) — `BigDecimal` strictly `> 0`, input scale preserved, no float path (BR-005, FR-009, FR-025, DR-011).
- [X] T013 [P] Test + implement `Money` (`domain/Money.java` + test) — `BigDecimal amount` + `Currency`; when used as a price the amount must be `> 0` (A3); equality/precision preserved (BR-007, FR-010, FR-020, FR-025).
- [X] T014 [P] Test + implement `InstrumentRef` (`domain/InstrumentRef.java` + test) — `Ticker` + `Market`, value equality drives duplicate detection (BR-003, FR-012, FR-014).
- [X] T015 [P] Implement `PortfolioStatus` enum `{ ACTIVE }` (`domain/PortfolioStatus.java`) (A12).
- [X] T016 [P] Implement `Violation(String field, String code, String message)` and `PortfolioValidationException extends RuntimeException` carrying `List<Violation>` in `.../portfolio/domain/` (research.md D2).

### Domain — entities (TDD)

- [X] T017 Test + implement `Position` (`domain/Position.java`, test `domain/PositionTest.java`) — factory `Position.of(InstrumentRef, Quantity, Currency, Optional<LocalDate>, Optional<Money>)`; collects `Violation`s for: quantity ≤ 0 (BR-005/AC-004), missing required field (§5), future `initialPurchaseDate` (A4/FR-011), non-positive `averagePurchasePrice` (A3/FR-010), `averagePurchasePrice` currency ≠ position currency (BR-007); absent optional = `Optional.empty()`, never 0/inferred (BR-008/BR-009/FR-019); stores only the aggregated holding, no lots (BR-010/FR-021). Depends on T008–T016.
- [X] T018 Test + implement `Portfolio` aggregate root (`domain/Portfolio.java`, test `domain/PortfolioTest.java`) — static factory `Portfolio.create(InvestorId, PortfolioName, List<Position>, Clock)`; validates and **collects every** `Violation` (not fail-fast): name required (BR-001), ≥ 1 Position (BR-002), no two Positions with equal `InstrumentRef` (BR-004/AC-005, index-qualified field path); immutable after creation; sets `status=ACTIVE`, `createdAt`; on any violation throws one `PortfolioValidationException` with the full list. Depends on T017.

### Application layer (framework-free)

- [X] T019 [P] Define inbound port `application/port/in/CreatePortfolioUseCase.java` + `CreatePortfolioCommand.java` (raw `name`, list of raw position inputs, `idempotencyKey`) + `CreatePortfolioResult.java` (`portfolioId`, `replayed`). Raw strings so the domain parses/validates (research.md D2).
- [X] T020 [P] Define outbound ports `application/port/out/PortfolioRepository.java` (`save(Portfolio)` atomic; `Optional<PortfolioId> findByIdempotencyKey(String)`) and `application/port/out/DefaultInvestorProvider.java` (`InvestorId get()`).
- [X] T021 Test + implement `application/CreatePortfolioService.java implements CreatePortfolioUseCase` (test `application/CreatePortfolioServiceTest.java`, ports mocked): idempotency-key lookup → return `{id, replayed:true}` if found; else `DefaultInvestorProvider.get()` → build the raw inputs into domain objects → `Portfolio.create(...)` (validation) → `PortfolioRepository.save(...)` → emit structured `event=PortfolioCreated` + one `event=PositionAdded` per Position (SLF4J, ECS JSON — research.md D6) → return `{id, replayed:false}`. No Spring imports. Depends on T018–T020.

### Outbound adapters (Spring; Testcontainers ITs)

- [X] T022 [P] Implement `adapter/out/persistence/JdbcDefaultInvestorProvider.java` — reads the single seeded `investor` row via `JdbcClient`; fail fast if absent. Integration test `adapter/out/persistence/JdbcDefaultInvestorProviderIT.java` (Testcontainers PostgreSQL via the EN001 `PostgresContainerSupport`) asserts the seed row is returned.
- [X] T023 Implement `adapter/out/persistence/JdbcPortfolioRepository.java` — `@Transactional save(Portfolio)` inserts the `portfolio` row then batch-inserts `position` rows via `JdbcClient` (map domain ⇄ rows explicitly; `average_purchase_price_currency` = position currency when price present); `findByIdempotencyKey`; on a `idempotency_key` unique-violation, re-read and return the existing `PortfolioId` (research.md D3). Depends on T007, T018, T020.
- [X] T024 Integration test `adapter/out/persistence/JdbcPortfolioRepositoryIT.java` (Testcontainers): `V2__portfolio.sql` applied + seed present; aggregate persists with all Positions; a forced failure mid-save rolls back → zero `portfolio` and zero `position` rows (FR-023, SC-010); `UNIQUE(portfolio_id,ticker,market)` and `UNIQUE(idempotency_key)` enforced; `NUMERIC` round-trips `quantity`/`price` with no precision loss (SC-007); `findByIdempotencyKey` returns the right id. Depends on T023.

### Inbound web adapter (Spring; contract test)

- [X] T025 [P] Implement web DTOs `adapter/in/web/CreatePortfolioRequest.java` / `CreatePortfolioResponse.java` (shape = the OpenAPI schemas; NOT domain types) with Jackson config for `BigDecimal` as plain string.
- [X] T026 Implement `adapter/in/web/CreatePortfolioController.java` — `POST /api/portfolios`; reads `Idempotency-Key` header (required); maps request → `CreatePortfolioCommand`; calls `CreatePortfolioUseCase`; `201` + `Location` on create, `200` + `Idempotency-Replayed: true` on replay; maps result → `CreatePortfolioResponse`. Depends on T021, T025.
- [X] T027 Implement `adapter/in/web/PortfolioExceptionHandler.java` (`@RestControllerAdvice`) — `PortfolioValidationException` → `400` `application/problem+json` `ValidationProblem` (`type=/problems/portfolio-validation`, `errors[]` from the `Violation` list, `field` paths preserved); persistence failure → `503` `type=/problems/portfolio-not-saved` with a generic non-technical `detail` (FR-023a); unsupported media type → `415`; never leak stack traces / SQL / framework class names (AR-012, DR-020). Depends on T016, T026.
- [X] T028 Contract test `adapter/in/web/CreatePortfolioControllerContractTest.java` (`@WebMvcTest`, use case mocked, `swagger-request-validator-mockmvc`) — a valid request/`201`, a validation `400` `ValidationProblem`, and a `200` replay all conform to `implementation/platform/contracts/openapi/openapi.yaml`; `errors[].field` paths match the schema. Depends on T001, T026, T027.

### Wiring, architecture, full-slice

- [X] T029 Implement `bootstrap/PortfolioBeanConfiguration.java` (`@Configuration`) — construct `CreatePortfolioService` and bind `PortfolioRepository` → `JdbcPortfolioRepository`, `DefaultInvestorProvider` → `JdbcDefaultInvestorProvider`, injecting the seeded default-investor id / `Clock`. No annotations in `domain`/`application`. Depends on T021, T022, T023.
- [X] T030 Update `architecture/HexagonalArchitectureRulesTest.java` — generalize the layering rules so they apply to **every** capability package under `com.myfinaimanager.core` (`..domain..` must not depend on `..application..`/`..adapter..`/`..bootstrap..`/Spring/`java.sql..`; `..application..` must not depend on `..adapter..`/`..bootstrap..`/Spring; inbound adapters must not depend on outbound adapters). Remove `allowEmptyShould(true)` where the `portfolio` package now makes a rule non-vacuous. Depends on T018, T021, T023, T026.
- [X] T031 Full-slice integration test `portfolio/CreatePortfolioIT.java` (`@SpringBootTest(webEnvironment=RANDOM_PORT)` + Testcontainers) — happy-path `POST /api/portfolios` through the real controller + real DB + real migration: `201`, response body matches, rows in `portfolio` + `position` owned by the default Investor (FR-030, SC-012). Depends on T007, T023, T026, T027, T029.
- [X] T031a Integration test `portfolio/CreatePortfolioIdempotencyIT.java` (`@SpringBootTest(webEnvironment=RANDOM_PORT)` + Testcontainers) — POST `/api/portfolios` twice with the **same** `Idempotency-Key` and identical body: first → `201`; second → `200` + `Idempotency-Replayed: true` returning the same portfolio id; `select count(*) from portfolio` for that key = 1 (FR-031a, SC-011). Depends on T023, T026, T029.

### Frontend scaffolding

- [X] T032 [P] Implement `portfolio/portfolio-api.service.ts` (+ `portfolio-api.service.spec.ts`) — `createPortfolio(draft)` → `POST /api/portfolios` with an `Idempotency-Key` header (UUID via `crypto.randomUUID()`, generated once per Save attempt and reused on retry); returns the created portfolio; on `400` returns the parsed `errors[]`; on `503` returns a "not saved" result; on `200` treats it as success (replay). Depends on T001.
- [X] T033 Add route `{ path: 'portfolios/new', component: CreatePortfolioPageComponent }` in `implementation/platform/frontend/web/src/app/app.routes.ts` and a **"Portfolios"** nav entry routing to `portfolios/new` in `implementation/platform/frontend/web/src/app/core/layout/sidebar.component.ts` (design-system: nav is allowed now that a Portfolio capability exists).
- [X] T034 Implement `portfolio/create-portfolio.page.ts` (+ spec) — standalone component, reactive form: Portfolio name control, an **Add Position** action opening the dialog, the draft `PositionDraftListComponent`, a **Save** button. Save calls `PortfolioApiService`; Save is disabled while a request is in flight (FR-031a); consumes `src/styles/_tokens.scss`. (Story phases wire the concrete behaviours.) Depends on T005, T032.
- [X] T035 [P] Implement `portfolio/add-position.dialog.ts` (+ spec) — a focused dialog/drawer (design-system) with reactive-form controls for ticker, market, quantity, currency, and optional initial purchase date + average purchase price; emits a `PositionDraft` on confirm; format/required checks only (backend is authoritative — AR-013). Depends on T005.
- [X] T036 [P] Implement `portfolio/position-draft-list.component.ts` (+ spec) — renders the draft Positions with key details (ticker, market, quantity, currency) and per-row **remove** and **edit** actions; emits change events (FR-026). Depends on T005.

**Checkpoint**: `mvn verify` green (domain + service + repository IT + contract test + ArchUnit + `CreatePortfolioIT` + `CreatePortfolioIdempotencyIT` + JaCoCo ≥ 90%); `npm test` green for the new frontend units; `POST /api/portfolios` works end-to-end via `curl`.

---

## Phase 3: US1 — Create a portfolio with one position (Priority: P1) 🎯 MVP

**Goal**: An Investor opens the Create Portfolio screen, enters a name, adds one valid Position, saves, and sees a success confirmation; the Portfolio + Position are persisted exactly as entered.

**Independent Test**: quickstart §C1 — browser flow end to end, then `psql` shows one `portfolio` (ACTIVE, owner "Default Investor") and one `position` with the entered values and NULL optional fields. Covers AC-001, SC-001, SC-006, SC-012.

- [X] T037 [US1] Wire `create-portfolio.page.ts` happy path: on **Save**, build a `PortfolioDraft` from the name + draft Positions, call `PortfolioApiService.createPortfolio`, and on success show a "Portfolio created successfully" confirmation message (FR-031); keep the screen usable (no navigation to a detail view — A9).
- [X] T038 [P] [US1] Frontend test `create-portfolio.page.spec.ts` — name + one Position (via the dialog) + Save → API called once with an `Idempotency-Key`; success message rendered; Save disabled during the in-flight request.
- [X] T039 [P] [US1] Backend acceptance test `portfolio/CreatePortfolioAcceptanceIT.java` (Testcontainers) — **AC-001**: valid name + one valid Position → `201`, plus a DB assertion that the stored Portfolio/Position equal the input with optional fields NULL (SC-006, not 0/inferred).
- [X] T040 [US1] Backend test: `CreatePortfolioServiceTest` case — a successful create emits exactly one `PortfolioCreated` and one `PositionAdded` structured log entry with the ids (FR-032, US1/AS3); assert via a log capture (e.g. `OutputCaptureExtension` or a test appender).
- [X] T041 [US1] Run quickstart §C1 against the running platform; record the result and the `psql` output as acceptance evidence.

**Checkpoint**: MVP — a portfolio can be created in the browser and is correctly persisted.

---

## Phase 4: US2 — Create a portfolio with several positions (Priority: P2)

**Goal**: The Investor adds multiple Positions (including the same ticker on different markets) before one Save; all persist under the one Portfolio.

**Independent Test**: quickstart §C2 — add `ASML/XAMS` and `ASML/XNAS`, Save, both persist as distinct Positions. Covers AC-002, BR-003, SC-002.

- [X] T042 [US2] `create-portfolio.page.ts` — allow adding an arbitrary number of Positions to the draft before Save (Add Position can be used repeatedly); the whole list is submitted in one request.
- [X] T043 [P] [US2] Frontend test — adding 3 Positions then Save sends all 3 in the request body; the draft list shows 3 rows.
- [X] T044 [P] [US2] Backend acceptance test `portfolio/CreatePortfolioMultiPositionIT.java` (Testcontainers) — **AC-002** (several distinct `ticker+market`, one Save → all persisted) and **BR-003** (same ticker, different market → both accepted).
- [X] T045 [US2] Add to `portfolio/CreatePortfolioMultiPositionIT.java` — **SC-002**: a `POST /api/portfolios` with ≥ 10 distinct Positions persists all of them within the performance budget (< 1 s locally, non-gating assertion / timing log). Sequential after T044 (same file).

**Checkpoint**: multi-position creation works end to end.

---

## Phase 5: US3 — Validation prevents invalid portfolios (Priority: P2)

**Goal**: Invalid Save attempts are rejected with field/Position-specific messages and nothing is persisted.

**Independent Test**: quickstart §C3 + §C7 — empty name, quantity 0, duplicate `ticker+market` each blocked with a specific message; `psql` shows no new rows; the `400` body is RFC 9457 with `errors[]`. Covers AC-003, AC-004, AC-005, SC-003, SC-008.

- [X] T046 [US3] `create-portfolio.page.ts` + `add-position.dialog.ts` — on a `400` from the API, map `errors[]` (by `field` path: `name`, `positions`, `positions[i].quantity`, …) back onto the corresponding reactive-form controls and show each message near its control (FR-024); block Save while unresolved errors exist.
- [X] T047 [P] [US3] Frontend test — a stubbed `400` `ValidationProblem` with `name/REQUIRED` and `positions[1]/DUPLICATE_INSTRUMENT` renders both messages against the right fields; nothing is persisted (API called once, no navigation).
- [X] T048 [P] [US3] Backend acceptance tests `portfolio/CreatePortfolioValidationIT.java` (Testcontainers) — **AC-003** (empty/whitespace name), **AC-004** (quantity 0 / negative / non-numeric), **AC-005** (duplicate `ticker+market`), and BR-002 (no Position): each returns `400` `ValidationProblem` and leaves zero rows (SC-003).
- [X] T049 [P] [US3] Backend contract test — `CreatePortfolioControllerContractTest` negative cases: the multi-violation `400` body validates against the schema and `errors[]` field paths conform (SC-008, quickstart §C7).
- [X] T050 [US3] Domain test hardening — `PortfolioTest` / `PositionTest`: a single `create` call with several simultaneous violations returns **all** of them in one `PortfolioValidationException` (FR-024); each `Violation.code` is one of the canonical tokens defined by the `code` enum in `contracts/openapi/openapi.yaml`.

**Checkpoint**: every rejection path is proven and surfaces a specific message.

---

## Phase 6: US4 — Optional acquisition information (Priority: P3)

**Goal**: Positions can be added with a blank purchase date and/or price; the Portfolio still saves; the system never invents a value; a provided price is in the Position currency; non-positive price / future date are rejected.

**Independent Test**: quickstart §C4 — blank date+price saves with NULL columns; `812.50 EUR` stored as-is with currency `EUR`; `-5` and a future date rejected. Covers AC-006, AC-007, AC-008, SC-004, SC-005.

- [X] T051 [US4] `add-position.dialog.ts` — optional Initial Purchase Date and Average Purchase Price inputs; when left blank they are omitted from the `PositionDraft` (not sent as `""`/`0`); the price input is labelled with the Position's selected currency (BR-007).
- [X] T052 [P] [US4] Frontend test — a Position added with blank optional fields sends no `initialPurchaseDate` / `averagePurchasePrice` keys; a Position with `averagePurchasePrice` sends it as a decimal string.
- [X] T053 [P] [US4] Backend acceptance tests `portfolio/CreatePortfolioOptionalDataIT.java` (Testcontainers) — **AC-006** (no date), **AC-007** (no price → stored NULL, not inferred, SC-004), **AC-008** (price interpreted in the Position currency, SC-005) with DB assertions.
- [X] T054 [US4] Add to `portfolio/CreatePortfolioOptionalDataIT.java` — negative cases **A3** (`averagePurchasePrice` ≤ 0 → `400` `NOT_POSITIVE`) and **A4** (future `initialPurchaseDate` → `400` `FUTURE_DATE`); assert no rows persisted. Sequential after T053 (same file).
- [X] T055 [US4] Backend test — `PositionTest`: `Money` for the price is always constructed with the Position's `Currency`, so a price/position currency mismatch is impossible by construction (BR-007, FR-020).

**Checkpoint**: optional acquisition data behaves exactly per FD001 §5 / BR-007..BR-009.

---

## Phase 7: US5 — Review and correct the draft before saving (Priority: P3)

**Goal**: The Investor sees the Positions added so far and can remove or correct one before Save.

**Independent Test**: quickstart §C step — add 3 Positions, remove the 2nd, edit the 1st's quantity, Save → persisted Portfolio has 2 Positions with the corrected quantity. Covers FD001 §11 (US5 AS1–4).

- [X] T056 [US5] `position-draft-list.component.ts` + `create-portfolio.page.ts` — the draft list shows every added Position with ticker/market/quantity/currency (FR-026); a **Remove** action drops it from the draft (FR-027); an **Edit** action reopens `add-position.dialog.ts` pre-filled and replaces the entry on confirm (FR-028).
- [X] T057 [P] [US5] Frontend test — add 3 → list shows 3; remove index 1 → list shows 2 and the removed Position is not in the next Save payload; edit index 0's quantity → the Save payload carries the corrected value.
- [X] T058 [P] [US5] Frontend test — removing the last remaining Position disables Save and shows "at least one Position is required" (BR-002, US5/AS4).
- [X] T059 [US5] Run the US5 quickstart scenario end to end; record the `psql` result as evidence.

**Checkpoint**: the draft is fully editable before persistence.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T060 [P] Frontend: loading state during Save (design-system "Loading States"), and a non-blocking error presentation for the `503` "couldn't save, please try again" that keeps the draft intact (FR-023a); test `create-portfolio.page.spec.ts` for the `503` path.
- [X] T061 [P] Backend: verify structured (ECS JSON) logging of `PortfolioCreated` / `PositionAdded` contains no sensitive data and no secrets (DR-030); no `System.out`/`printStackTrace`.
- [X] T062 [P] Update `implementation/platform/README.md` — add a "Portfolio creation" line to the platform capabilities, the `POST /api/portfolios` URL, and a pointer to `specs/FD001-create-investment-portfolio/quickstart.md`.
- [X] T063 [P] Update `product/architecture/diagrams/containers.md` **"Current Realized State"** — move Portfolio Management from "Not yet realized" into the current-state diagram (the `core-service` now has a `portfolio` module, `POST /api/portfolios`, and `portfolio`/`position`/`investor` tables). *(Human-governed file — edit **prepared** and applied to the working tree; **maintainer must confirm before it is committed**, per constitution I.)*
- [X] T064 Run the full `quickstart.md` (Sections A, B, and C1–C7) end to end on the running platform; complete the Acceptance/Success-Criteria coverage table with evidence for AC-001…AC-008 and SC-001…SC-012.
- [X] T065 [P] Assemble PR evidence per `product/engineering/definition-of-done.md` "Minimum Pull Request Evidence"; explicitly note the accepted security debt (unauthenticated `POST /api/portfolios`, spec A11). **`product/architecture/adrs/ADR-002-interim-unauthenticated-write-access.md` is drafted (Status: Proposed) — it MUST be human-approved (its "Human Approval" checklist completed) before this PR merges.**
- [X] T066 Run `product/engineering/definition-of-done.md` checklist against the change: scope, architecture (ArchUnit green), tests (all suites + coverage ≥ 90%), contract (OpenAPI updated + contract test), persistence (V2 migration + ownership), security/secrets, observability (structured events), documentation; record the DoD status in the PR.

**Checkpoint**: all Polish items done — **but FD001 is NOT closeable until Phase 9 (E2E-001) passes** (spec FR-036 / SC-013; FD001 §13, §16).

---

## Phase 9: End-to-End Verification — E2E-001 (mandatory closure gate)

**Goal**: prove the critical Create Portfolio journey works through a real browser against the
fully containerized platform. Added after the authoritative Feature Definition §13 made an
automated E2E test mandatory, now that `EN002` provides the containerized Playwright foundation.

**Independent Test**: `implementation/platform/e2e.sh` runs `FD001-create-portfolio.spec.ts`
(Chromium) against the isolated containerized stack and exits `0`. Covers **FR-036**, **FR-037**,
**SC-013**, and FD001 §13 **E2E-001**.

**Prerequisite**: `EN002` is in place (`implementation/platform/e2e.sh`, `implementation/platform/e2e/`,
the Playwright runner). This phase does **not** modify the E2E infrastructure — it only adds one
feature test into it (spec A14).

- [X] T067 [US1] Write `implementation/platform/e2e/tests/FD001-create-portfolio.spec.ts` (Playwright, Chromium) — the E2E-001 journey, starting from the **frontend** (do not call `POST /api/portfolios` directly as the primary check — FR-021 / EN002 §13):
  1. `page.goto('/portfolios/new')` (via `baseURL` = `http://frontend`); assert the Create Portfolio screen renders (`app-create-portfolio-page`, the "Portfolio name" field, the **Add position** and **Save** controls).
  2. Fill the Portfolio name with a unique synthetic value from `implementation/platform/e2e/support/data.ts` (`uniquePortfolioName()`), then open the position dialog (**Add position**), fill ticker `ASML` / market `XAMS` / quantity `1` / currency `EUR` (use `syntheticPosition()`), and confirm the position.
  3. Assert the draft position list shows the added position, then click **Save**.
  4. Assert the success confirmation is shown (text "Portfolio created successfully."); assert **no** `pageerror` fired during the flow.
  No mocking of frontend↔backend or persistence. A DB assertion is **not** required (A13) — the persistence guarantees are already covered by the backend ITs; a lightweight secondary check is permitted only if it uses the real stack and is commented as such.
- [X] T068 [US1] Run `./implementation/platform/e2e.sh` (or `./e2e.sh -g "create"`) against the containerized platform; confirm `FD001-create-portfolio.spec.ts` **passes** (Chromium) and the script exits `0`. Then: (a) add a short "E2E — FD001-create-portfolio" scenario + result to `specs/FD001-create-investment-portfolio/quickstart.md`; (b) add the E2E result row to the coverage table and to `specs/FD001-create-investment-portfolio/pr-evidence.md` (E2E-001 satisfied); (c) note in the PR that FD001 §16 "E2E-001 exists and passes" is now satisfiable — **the maintainer checks that box** (human-governed).

**Checkpoint**: `./e2e.sh` green including `FD001-create-portfolio.spec.ts` → the FD001 closure gate (FR-036 / SC-013 / FD001 §16) is satisfied. FD001 is now technically ready to close pending the outstanding human approvals (ADR-002; `containers.md`; FD001 §16).

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (P1)**: T001 (contract merge) before T028/T032; otherwise T002–T006 parallel.
- **Foundational (P2)**: after Setup. Internal order — T007 (migration) ‖ T008–T016 (value objects, parallel) → T017 (Position) → T018 (Portfolio) → T019–T020 (ports) → T021 (service) → T022/T023 (persistence adapters) → T024 (repo IT) ‖ T025 → T026 → T027 → T028 (contract test) → T029 (wiring) → T030 (ArchUnit) → T031 (full-slice IT) ‖ T031a (idempotency IT). Frontend scaffolding T032–T036 parallel to the backend once T001 is done.
- **US1 (P3, MVP)**: after Foundational.
- **US2 (P4)**: after US1 (extends the same page).
- **US3 (P5)**: after US1 (error-mapping on the same page); independent of US2.
- **US4 (P6)**: after US1; independent of US2/US3.
- **US5 (P7)**: after US1; touches the draft list / page.
- **Polish (P8)**: after the targeted stories.
- **End-to-End Verification (P9)**: after US1 (needs the browser Create flow working) and after `EN002` (the containerized platform + `e2e.sh`). T067 → T068. **Mandatory closure gate** — FD001 cannot be closed until T068 is green (spec FR-036 / SC-013; FD001 §13, §16).

### Story dependency summary

```text
Setup → Foundational ┬→ US1 (P1, MVP) ┬→ US2 (P2)
                     │                 ├→ US3 (P2)
                     │                 ├→ US4 (P3)
                     │                 ├→ US5 (P3)
                     │                 └→ E2E-001 (P9, needs EN002) ── closure gate
                     └──────────────────────────────→ Polish
```

### Parallel opportunities

- Setup: T002, T003, T004, T005, T006.
- Foundational value objects: T008–T016 (all `[P]`, different files).
- Foundational: backend chain and frontend scaffolding (T032–T036) run in parallel after T001.
- Within each story: the `[P]` frontend-test and backend-test tasks run together once the story's implementation task lands.
- Across people: after Foundational, US2 / US3 / US4 / US5 can be taken by different developers. Each story now has its **own** backend IT file (`CreatePortfolioMultiPositionIT`, `CreatePortfolioValidationIT`, `CreatePortfolioOptionalDataIT`, `CreatePortfolioAcceptanceIT`) so the `[P]` backend-test tasks run in parallel across stories; coordinate only on the two shared frontend files `create-portfolio.page.ts` / `add-position.dialog.ts`.

---

## Parallel Example: Foundational value objects

```bash
Task: "T009 [P] Test + implement PortfolioName"
Task: "T010 [P] Test + implement Ticker and Market"
Task: "T011 [P] Test + implement Currency"
Task: "T012 [P] Test + implement Quantity"
Task: "T013 [P] Test + implement Money"
Task: "T014 [P] Test + implement InstrumentRef"
```

---

## Implementation Strategy

### MVP first

1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 US1 → **STOP & VALIDATE**: quickstart §C1
(a portfolio can be created in the browser and is correctly persisted; AC-001, SC-001/006/012).

### Incremental delivery

1. Setup + Foundational → a tested `POST /api/portfolios` + frontend scaffolding.
2. + US1 → browser create, happy path (MVP).
3. + US2 → multiple positions.
4. + US3 → validation feedback surfaced end to end.
5. + US4 → optional acquisition data.
6. + US5 → editable draft.
7. Polish → loading/error states, docs, `containers.md`, DoD sign-off.
8. **E2E-001 (P9)** → `FD001-create-portfolio.spec.ts` passes via `./e2e.sh` — the mandatory closure gate (FR-036 / SC-013 / FD001 §16).

### Constitution / DoD checkpoints

- Domain value objects + `Position` + `Portfolio` are developed test-first (constitution VII, DR-004).
- `domain` / `application` stay Spring-free — enforced by T030 (constitution VI).
- Persistence and idempotency are covered by real Testcontainers ITs, never mocks (constitution VII).
- The OpenAPI contract is merged (T001) before the controller (T026); T028 guards drift (constitution VIII).
- Coverage gate ≥ 90% enabled (T003); `portfolio.domain` near 100% branch (DoD §5).
- Accepted security debt (unauthenticated write) is documented; ADR-002 is drafted (Proposed) and must be approved before merge (T065) — not silently shipped.
- **E2E-001 is a hard closure gate** (T067–T068): FD001 §13 / §16 forbid accepting or closing FD001 while `FD001-create-portfolio.spec.ts` is missing or failing. It drives the real browser → frontend → REST → `core-service` → PostgreSQL stack with no mocks (constitution VII spirit; EN002 §13).

---

## Notes

- `[P]` = different files, no dependency on an incomplete task.
- Commit after each task or logical group; keep `mvn verify` + `npm test` green at every checkpoint.
- If any task appears to need: a portfolio list/view/edit/delete endpoint, valuation/risk/recommendation logic, real authentication, a messaging broker, or a technology not already on the platform — **stop and surface it** (constitution IV; FD001 §3).
- Total: 69 tasks — Setup 6, Foundational 31 (incl. T031a), US1 5, US2 4, US3 5, US4 5, US5 4, Polish 7, **E2E Verification 2 (T067–T068)**.
- **T001–T066 are complete (`[X]`)** from the initial FD001 implementation. **T067–T068 (E2E-001) are new and outstanding** — added after FD001 §13 mandated an automated E2E test once `EN002` landed. FD001 is not closeable until they are done.
