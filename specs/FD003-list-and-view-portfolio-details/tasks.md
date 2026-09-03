---
description: "Task list — FD003 List and View Portfolio Details"
---

# Tasks: List and View Portfolio Details (FD003)

**Input**: `specs/FD003-list-and-view-portfolio-details/` — plan.md, spec.md, research.md (D1–D9),
data-model.md, contracts/ (portfolios.read.yaml, portfolio-read-ports.md), quickstart.md (A–G).

**Tests**: REQUIRED — FD003 §15 mandates unit + integration + contract + Home/detail rendering +
regression; FD003 §16 makes **E2E-001 and E2E-002** mandatory closure gates; constitution VII
requires TDD for the deterministic query rules.

**Feature**: Approved 2026-09-03 (§19 signed). ODs OD-FD003-1…6 **all confirmed by jaruiz
2026-09-03**. **Read-only** — no schema migration, no write path, no new dependency.

## Format: `[ID] [P?] [Story] Description`

- **[P]** = different files, no dependency on an incomplete task → parallelizable
- **[Story]** = US1 / US2 / US3 / US4 (Setup / Foundational / Polish have none)
- All paths repository-relative.

## Path map (plan.md §Project Structure)

```text
BE   = implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core/portfolio
BET  = implementation/platform/backend/core-service/src/test/java/com/myfinaimanager/core/portfolio
FE   = implementation/platform/frontend/web/src/app
API  = implementation/platform/contracts/openapi/openapi.yaml
E2E  = implementation/platform/e2e
```

---

## Phase 1: Setup

- [X] T001 [P] **Contract-first** — merge `contracts/openapi/portfolios.read.yaml` into `API`: add the `listPortfolios` (`GET /api/portfolios`) and `getPortfolio` (`GET /api/portfolios/{portfolioId}`) operations under `paths./api/portfolios`; add the **`PortfolioSummary`** schema `{ id (uuid), name, positionCount (integer ≥ 0) }`; **reuse** the existing `Portfolio` / `Position` / `Problem` schemas for the detail + `404`; keep OpenAPI **3.0.3**; update `info.description`. No behavior yet.
- [X] T002 [P] `FE/portfolio/portfolio.models.ts` (NEW) — `PortfolioSummary { id: string; name: string; positionCount: number }`, plus `ListState` / `DetailState` unions per data-model.md §5.3. The detail reuses `PortfolioView` / `PositionView` from `portfolio-creation.models.ts` (re-export or import).

**Checkpoint**: `./mvnw -B compile` unaffected; `API` still parses (3.0.3); no runtime change.

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ Blocks US1, US2, US3** — the shared read path (port → adapter → service → controller).

- [X] T003 [P] `BE/domain/exceptions/PortfolioNotFoundException.java` (NEW) — `extends RuntimeException`; constructor `(PortfolioId id)` (store `id.toString()`); accessor `portfolioId()`. Javadoc per `contracts/portfolio-read-ports.md` C3.
- [X] T004 [P] `BE/domain/ports/PortfolioRepository.java` — add `List<Portfolio> findAllByInvestor(InvestorId investorId)` (newest-first) and `Optional<Portfolio> findByIdForInvestor(PortfolioId id, InvestorId investorId)`; javadoc = invariants P1–P6 from `contracts/portfolio-read-ports.md` C1. (existing methods unchanged)
- [X] T005 `BE/infrastructure/persistence/repository/PortfolioJpaRepository.java` — add two **derived** `@EntityGraph(attributePaths = "positions")` queries: `findAllByInvestorIdOrderByCreatedAtDescIdDesc(UUID investorId)` and `findByIdAndInvestorId(UUID id, UUID investorId)` (no JPQL). Implement the two `PortfolioRepository` read methods in `BE/infrastructure/persistence/PortfolioPersistenceAdapter.java` using the existing `readOnlyTemplate` + `mapper::toDomain`. Extend `BET/infrastructure/persistence/PortfolioPersistenceAdapterIT.java` (Testcontainers, **RED first**): seed via the write path 2 portfolios for the Default Investor (1 and 3 Positions) + 1 for a different investor id (2 Positions) → `findAllByInvestor(default)` returns only the 2, newest-first, each with its Positions; `findByIdForInvestor` hit / other-investor-miss / random-uuid-miss; `SELECT count(*)` on `portfolio` + `position` unchanged across a `findAll` + `findById` sequence (SC-005). (research D2; quickstart §C)
- [X] T006 `BE/business/PortfolioQueryUseCase.java` (NEW — `list()` / `view(PortfolioId)`) + `BE/business/PortfolioQueryService.java` (NEW — `@Service`, constructor `(PortfolioRepository, DefaultInvestorProvider)`; `list()` → `findAllByInvestor(provider.get())`; `view(id)` → `findByIdForInvestor(id, provider.get()).orElseThrow(() -> new PortfolioNotFoundException(id))`; never calls `save`). `BET/business/PortfolioQueryServiceTest.java` (NEW, **RED first**, mocked port + provider): `list()` returns/orders the repo result; `view` hit; `view` miss → `PortfolioNotFoundException` carrying the id; `save` never invoked. (research D1; quickstart §B)
- [X] T007 `BET/infrastructure/api/rest/PortfolioQueryControllerContractTest.java` (NEW — **RED first**, written against `openapi.yaml`; `@WebMvcTest(PortfolioQueryController.class)` + `@Import({PortfolioExceptionHandler.class, PortfolioSummaryMapper.class, PortfolioResponseMapper.class})` + `@MockitoBean PortfolioQueryUseCase` + `swagger-request-validator`): `GET /api/portfolios` → `200` array conforms to `openapi.yaml` (+ empty `[]` case); `GET /api/portfolios/{id}` → `200` conforms to `Portfolio`; unknown id (service throws `PortfolioNotFoundException`) → `404 application/problem+json` `$.type == "/problems/portfolio-not-found"` conforms; a **malformed** (non-UUID) `{portfolioId}` → `400` (Spring's `MethodArgumentTypeMismatchException` default) — assert status only, not a problem body; assert **no** `investorId`/`idempotencyKey`/entity/provider field in any body. **Re-run** `BET/infrastructure/api/rest/CreatePortfolioControllerContractTest.java` unchanged (the widened advice must not change FD001's `400`/`503`).
- [X] T008 `BE/infrastructure/api/rest/dto/PortfolioSummaryResponse.java` (NEW — `record (String id, String name, int positionCount)`); `BE/infrastructure/api/rest/mapper/PortfolioSummaryMapper.java` (NEW — `@Component`, `toResponse(Portfolio)` → `positions().size()`); `BE/infrastructure/api/rest/PortfolioQueryController.java` (NEW — `@RestController`; `GET /api/portfolios` → `List<PortfolioSummaryResponse>`; `GET /api/portfolios/{portfolioId}` with the path var typed **`UUID`** (a non-parseable segment yields Spring's default `400` — A3) → the existing `CreatePortfolioResponse` via `PortfolioResponseMapper`; construct `PortfolioId` from the `UUID`); widen `BE/infrastructure/api/rest/PortfolioExceptionHandler.java` `@RestControllerAdvice(assignableTypes = { CreatePortfolioController.class, PortfolioQueryController.class })` and add `@ExceptionHandler(PortfolioNotFoundException.class)` → `ProblemDetail` `404` `type=/problems/portfolio-not-found` `instance=/api/portfolios/{id}`. Makes T007 green. (research D5, D6; contracts C3/C4)

**Checkpoint**: `./mvnw -B clean verify` green — new unit + IT + contract tests pass, JaCoCo gate holds, ArchUnit **14/14** (new classes in the right ADR-003 packages — `PortfolioQueryController` under `infrastructure.api.rest`, `PortfolioSummaryMapper` under `…api.rest.mapper`, `PortfolioSummaryResponse` under `…api.rest.dto`, `PortfolioQueryService` under `business`, `PortfolioNotFoundException` under `domain.exceptions`); FD001 + FD002 suites unaffected.

---

## Phase 3: User Story 1 — See my saved Portfolios on the Home page (Priority: P1) 🎯 MVP

**Goal**: The Home page loads and shows the Investor's persisted Portfolios in a table — one row
per Portfolio, name + Position count.

**Independent Test**: `ng test` on the list component with 2 mocked summaries → a table with those
2 rows (name + count); loading + error states; a row is a keyboard link to `/portfolios/:id`. And
quickstart §E.

### Tests for US1 (write first, must fail)

- [X] T009 [P] [US1] `FE/portfolio/portfolio-query.service.ts` + `.spec.ts` (NEW) — TDD (spec RED first): `list(): Observable<PortfolioSummary[] | null>` → `GET /api/portfolios`; maps the response array; HTTP error → `null`. `@Injectable({providedIn:'root'})`, `inject(HttpClient)`. (research D7)
- [X] T010 [US1] `FE/portfolio/portfolio-list.component.spec.ts` (NEW, RED) — states: `loading` (spinner) → `loaded` with 2 summaries → a `<table>` with 2 `tbody tr`, each showing `name` and `positionCount`; `error` → recoverable message + **Retry** that re-calls `list()`; each row is a `routerLink`/keyboard-operable control to `/portfolios/{{id}}`; a "Create portfolio" action links to `/portfolios/new`. (contracts add-position-ui parallel; FD003 §12; design system)

### Implementation for US1

- [X] T011 [US1] `FE/portfolio/portfolio-list.component.ts` (NEW) — standalone component; on init calls `PortfolioQueryService.list()`; renders the 4 states (loading / results table / *empty — US2* / error+Retry); compact table per the design system; rows are accessible links to the detail; header "Create portfolio" → `/portfolios/new`. (research D7; FR-001, FR-002, FR-006, FR-007, FR-026, FR-027)
- [X] T012 [US1] `FE/home.component.ts` — replace the placeholder content with `<app-portfolio-list>` (import the standalone component; keep `HomeComponent` as the route-`''` host so the route stays stable — A5). `FE/app.routes.ts` — keep `''` → `HomeComponent`, keep `portfolios/new`. `FE/core/layout/sidebar.component.ts` — the "Portfolios" entry `routerLink="/"`; update `home.component.spec.ts` if it asserts the old placeholder text. (research D7; FR-028, OD-FD003-6)
- [X] T013 [US1] `ng test` (query service + list component) green; `./mvnw -B verify` unaffected.

**Checkpoint**: MVP — opening Home shows the Investor's Portfolios with correct names + Position counts. Independently demoable.

---

## Phase 4: User Story 2 — Clear empty state when I have no Portfolios (Priority: P1)

**Goal**: An Investor with no Portfolios sees an explicit "no portfolios" message — not a blank or
empty table.

**Independent Test**: list component with `[]` → the empty-state message + a "Create portfolio"
action, **0** `tbody tr`. And E2E-002.

### Tests for US2 (write first, must fail)

- [X] T014 [US2] `FE/portfolio/portfolio-list.component.spec.ts` — add the empty-state case (RED): `loaded` with `[]` → the message "No portfolios yet." (or equivalent) is shown, a "Create portfolio" call-to-action is present, and there are **0** Portfolio rows and **no** placeholder row. (FR-004; AC-004)

### Implementation for US2

- [X] T015 [US2] `FE/portfolio/portfolio-list.component.ts` — render the **empty** state when `loaded` and the list is empty: a clear message ("what's missing / what to do next" per design system §"Empty States") + the "Create portfolio" action; never a Portfolio row / placeholder row. (FR-004; design system)
- [X] T016 [US2] `ng test` (list component, incl. empty case) green.

**Checkpoint**: AC-004 holds — empty state, 0 rows, no misleading placeholder.

---

## Phase 5: User Story 3 — Open a Portfolio and see its Positions (Priority: P1)

**Goal**: Selecting a Portfolio row navigates to its detail view — name + a read-only Positions
table.

**Independent Test**: detail page with a mocked `PortfolioView` → name + one row per Position
(ticker/market/quantity/currency; date & avg price only when present); `404` → "not found" state;
**no** edit/add/remove controls. And E2E-001 detail assertions.

### Tests for US3 (write first, must fail)

- [X] T017 [P] [US3] `FE/portfolio/portfolio-query.service.spec.ts` — add `getById(id)` cases (RED): `200` → `PortfolioView`; `404` → `'not-found'`; other error → `null`. (research D7; FR-013, FR-014)
- [X] T018 [US3] `FE/portfolio/portfolio-detail.page.spec.ts` (NEW, RED) — reads the route `:id`, calls `getById`; `loaded` → the Portfolio name + a `<table>` with one row per Position, ticker/market/quantity/currency always shown, `initialPurchaseDate` / `averagePurchasePrice` shown **only when present**; values rendered exactly as received (no reformat); `not-found` → a "this portfolio doesn't exist" state with a link to Home; `error` → Retry; assert there is **no** button/control to edit, add a Position, or remove a Position anywhere in the view. (FR-009, FR-010, FR-012, FR-013; BR-009; SC-004)

### Implementation for US3

- [X] T019 [US3] `FE/portfolio/portfolio-query.service.ts` — add `getById(id): Observable<PortfolioView | 'not-found' | null>` (`404` → `'not-found'`, other error → `null`). (research D7)
- [X] T020 [US3] `FE/portfolio/portfolio-detail.page.ts` (NEW) — standalone component/page; reads `:id` from the route, calls `getById`, renders the 4 states (loading / loaded / not-found / error+Retry); read-only Positions table per FD003 §6 / §12; **no** mutation controls. (research D7; FR-009…FR-014, FR-026)
- [X] T021 [US3] `FE/app.routes.ts` — add `portfolios/:id` → `PortfolioDetailPageComponent`. (OD-FD003-6)
- [X] T022 [US3] `ng test` (query service getById + detail page) green.

**Checkpoint**: AC-006 + AC-007 hold — a row opens the detail; the detail shows exactly that Portfolio's Positions, read-only.

---

## Phase 6: User Story 4 — Both journeys proven end to end; FD001/FD002 unaffected (Priority: P2)

**Goal**: E2E-001 and E2E-002 pass against the real containerized platform; FD001 + FD002 unchanged.

**Independent Test**: `./e2e.sh` → 6 passed, exit 0.

### Tests for US4

- [X] T023 [P] [US4] `E2E/playwright.config.ts` — add a project `portfolio-empty` **declared first** (`testMatch: /fd003-portfolio-empty\.spec\.ts$/`); the default `chromium` project gets `testIgnore: /fd003-portfolio-empty\.spec\.ts$/` **and `dependencies: ['portfolio-empty']`** so Playwright *guarantees* the empty-state spec completes on the fresh DB before any portfolio-creating spec runs (declaration order alone is not a Playwright guarantee — A1). (research D8; OD-FD003-3)
- [X] T024 [P] [US4] `E2E/support/portfolios.ts` (NEW) — `createPortfolio(request, name, positions)` helper: `request.post('/api/portfolios', { headers: { 'Idempotency-Key': … }, data: { name, positions } })`, returns the created Portfolio id. Positions use catalogued instruments (AAPL·XNAS·USD, MSFT·XNAS·USD, ASML·XAMS·EUR). (research D8; FR-031)
- [X] T025 [US4] `E2E/tests/fd003-portfolio-empty.spec.ts` (NEW — **E2E-002**, `portfolio-empty` project): open Home on the fresh stack → assert the empty-state message is visible and `app-portfolio-list tbody tr` count is `0`. (FD003 §16 E2E-002; AC-004; SC-002)
- [X] T026 [US4] `E2E/tests/FD003-portfolio-list.spec.ts` (NEW — **E2E-001**): `beforeAll` creates 3 Portfolios via `createPortfolio` — `«run» Alpha` [1 position], `«run» Beta` [2], `«run» Gamma` [3] (unique run-scoped names) → open Home → for each, assert its row exists with the **exact** `positionCount` (other rows tolerated — FD003 §16) → click the Gamma row → assert the detail shows Gamma's name and **exactly** its 3 Positions with the created ticker/market/quantity/currency, and **none** of Alpha's/Beta's Positions. (FD003 §16 E2E-001, §17.12–§17.13; AC-006, AC-007; SC-001, SC-003, SC-004)

### Verification for US4

- [X] T027 [US4] **Audit `E2E/tests/platform-smoke.spec.ts` first (A2)** — T012 changes the sidebar "Portfolios" link from `/portfolios/new` to `/` and replaces the Home placeholder text; the smoke spec currently asserts `expect(portfoliosNav).toHaveAttribute('href', '/portfolios/new')` and may assert the old Home copy ("No features are available yet."). Update those assertions (`href` → `/`; drop / replace the placeholder-copy assertion with a shell-level check or the empty-state message) so the smoke spec reflects FD003's Home. Then `cd implementation/platform && ./e2e.sh` → **6 passed** (Chromium), exit 0 — `fd003-portfolio-empty` (first project) + `FD001-create-portfolio` + `FD002-select-instrument` (×2) + `FD003-portfolio-list` + `platform-smoke`, all green.
- [X] T028 [US4] `./mvnw -B clean verify` + `ng test` — all FD001 + FD002 suites green (create, validation, idempotency, `INSTRUMENT_NOT_IN_CATALOG`, the FD002 dialog); JaCoCo ≥ 90 % line **and** branch; ArchUnit **14/14**. (FR-016, FR-017; SC-007, SC-009)

**Checkpoint**: both FD003 mandatory E2E gates green; FD001 + FD002 fully intact.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T029 [P] Docs — `implementation/platform/backend/core-service/README.md`: the two read operations, `PortfolioQueryController` / `PortfolioQueryService`, `PortfolioNotFoundException` → `404`, "no write path". `implementation/platform/README.md`: a Capabilities row for "List and view Portfolios" (Home + `GET /api/portfolios[/{id}]`) and that **Home now shows the Portfolio list**. No `product/` edit.
- [X] T030 [P] `specs/FD003-list-and-view-portfolio-details/pr-evidence.md` per `definition-of-done.md` "Minimum Pull Request Evidence": what/why; trace to `AC-001…AC-007` + `SC-001…SC-012` + E2E-001/002; how each Risk-Register item turned out; how validated (no CI — `./mvnw verify` + `ng test` + `quickstart.md` §A–§G + `./e2e.sh`). **Explicit `git diff --stat` scope review (SC-011)**: only `openapi.yaml` (+2 ops + 1 schema), `core/portfolio/**` (read-only additions), `frontend/web/src/app/**` (list + detail + service + routes + sidebar), `e2e/**` (2 specs + 1 project + helper), 2 READMEs — **no** schema migration, **no** write on `portfolio`/`position`, **no** new deployable/messaging/scheduler, **no** sorting/filtering/pagination, **no** new dependency, **no** `product/` edit.
- [X] T031 [P] `specs/FD003-list-and-view-portfolio-details/dod-checklist.md` — evaluate `product/engineering/definition-of-done.md` item by item (product/spec traceable to FD003; architecture — ArchUnit green, ADR-003 placement; code quality — read-only, deterministic query behind a port; tests — TDD unit + Testcontainers ITs + contract + Home/detail rendering + 2 mandatory E2E; coverage ≥ 90 %; API/OpenAPI — contract-first, RFC 9457 `404`, reuse of `Portfolio` schema, no leakage; persistence — no migration, read-only transactions, no dual write, no new DB access outside the module; secrets/hygiene; observability — unchanged; platform lifecycle — `start.sh`/`stop.sh`/`e2e.sh` unchanged; documentation).
- [X] T032 Run the full `quickstart.md` (§A–§G) from a clean state; complete its "Verification Criteria coverage" + Success-Criteria tables with concrete evidence for `AC-001…AC-007` and `SC-001…SC-012` (incl. the SC-012 < 2 s list/detail render walkthrough).
- [X] T033 Confirm the JaCoCo bundle gate (≥ 90 % line **and** branch) with the FD003 code; **no** new coverage exclusion is added (`PortfolioQueryService`, `PortfolioQueryController`, `PortfolioSummaryMapper`, the new adapter methods stay in coverage).

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (P1)**: T001 ‖ T002 — independent files.
- **Foundational (P2)**: T003 ‖ T004 → T005 ‖ T006 → **T007 (contract test, RED)** → T008 (controller + DTO + mapper + handler — makes T007 green). **Blocks US1/US2/US3.**
- **US1 (P1)**: after Foundational. T009 (RED) → T010 (RED) → T011 → T012 → T013. MVP.
- **US2 (P1)**: after US1 (same `portfolio-list.component.*` files). T014 (RED) → T015 → T016.
- **US3 (P1)**: after Foundational; independent of US1/US2 except the shared `portfolio-query.service.*` (T009/T017/T019 sequential) and `app.routes.ts` (T012/T021 sequential). T017 ‖ T018 (RED) → T019 → T020 → T021 → T022.
- **US4 (P2)**: after US1 + US2 + US3 (needs the finished Home + detail). T023 ‖ T024 → T025 ‖ T026 → T027 (incl. the `platform-smoke.spec.ts` audit — A2) → T028.
- **Polish (P7)**: after US1–US4. T029 ‖ T030 ‖ T031 → T032 → T033.

### File-contention notes (NOT parallel)

- `FE/portfolio/portfolio-list.component.ts` / `.spec.ts` — T010/T011 (US1) then T014/T015 (US2).
- `FE/portfolio/portfolio-query.service.ts` / `.spec.ts` — T009 (US1) then T017/T019 (US3).
- `FE/app.routes.ts` — T012 (US1) then T021 (US3).
- `API` / `openapi.yaml` — T001 only.
- `BET/.../PortfolioPersistenceAdapterIT.java` — T005 only.
- `BE/.../PortfolioExceptionHandler.java` — T008 only.
- `E2E/tests/platform-smoke.spec.ts` — T027 only (audit + update for the FD003 Home / sidebar change).

### Parallel opportunities

- Setup: T001 ‖ T002.
- Foundational: T003 ‖ T004; then T005 ‖ T006.
- US3 tests: T017 ‖ T018.
- US4: T023 ‖ T024; T025 ‖ T026.
- Polish: T029 ‖ T030 ‖ T031.

---

## Implementation Strategy

### MVP (US1 only)

Setup → Foundational (T003–T008) → US1 (T009–T013). Delivers: opening Home lists the Investor's
Portfolios with correct names + Position counts. **Stop and demo.**

### Incremental

1. + US2 → the empty state (AC-004). Demo.
2. + US3 → open a Portfolio, see its Positions read-only (AC-006, AC-007). Demo.
3. + US4 → E2E-001 **and** E2E-002 green + FD001/FD002 non-regression → **FD003 closure gates satisfied**.
4. Polish → docs, evidence, DoD, quickstart run, coverage.

### Notes

- Verify every RED test fails before its implementation task.
- **Read-only**: `PortfolioQueryService` must never call `save`; the adapter uses the `readOnly`
  transaction template; an IT asserts row counts are unchanged (SC-005).
- The `POST /api/portfolios` request/response contract and FD002's `INSTRUMENT_NOT_IN_CATALOG` are
  untouched — re-run their contract tests after widening `PortfolioExceptionHandler`.
- E2E-002 MUST run before any portfolio-creating spec — enforced by its own first Playwright
  project (T023), not by naming.
- Commit after each task or logical group.
