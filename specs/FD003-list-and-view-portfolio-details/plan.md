# Implementation Plan: List and View Portfolio Details (FD003)

**Branch**: `FD003-list-and-view-portfolio-details` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/FD003-list-and-view-portfolio-details/spec.md`

**Authoritative feature**: `product/definition/features/FD003-list-and-view-portfolio-details/FD003-list-and-view-portfolio-details.md` (**Status: Approved** - Sec.19 all boxes checked, signed by jaruiz 2026-09-03; Sec.18 "no known product-blocking questions remain").
**Consumed capabilities**: FD001 (persisted `Portfolio` / `Position` data + `POST /api/portfolios` + the `Portfolio` / `Position` OpenAPI schemas), FD002 (the constrained Add Position dialog + `INSTRUMENT_NOT_IN_CATALOG` check - must not regress).
**Governing decisions**: ADR-001 (one `core-service`), ADR-002 (single seeded Default Investor), ADR-003 (Standard Spring Backend Architecture), EN002 (containerized Playwright E2E), `product/ux/design-system.md`. **No new ADR** - FD003 adds two read endpoints + two read views inside the existing `portfolio` module.

> **Approval status - cleared 2026-09-03.** FD003 Sec.19 signed; Sec.18 has no open questions; spec
> has **zero `[NEEDS CLARIFICATION]`**. All six technical Open Decisions (OD table) were confirmed
> by jaruiz 2026-09-03.

## Summary

FD003 makes the Investor's persisted Portfolios visible and inspectable - a **read-only** vertical
slice of the existing platform.

1. **Backend - two read operations** (`.../core/portfolio/`):
   - `PortfolioRepository` (domain port) gains two **read-only** methods:
     `List<Portfolio> findAllByInvestor(InvestorId)` (newest-first) and
     `Optional<Portfolio> findByIdForInvestor(PortfolioId, InvestorId)`.
   - `PortfolioJpaRepository` gains two **derived** queries (no JPQL - the repo's rule):
     `findAllByInvestorIdOrderByCreatedAtDescIdDesc` and `findByIdAndInvestorId`, both
     `@EntityGraph(attributePaths = "positions")` so the aggregate (and its Position count) loads
     in one query.
   - `PortfolioQueryService` (`@Service`, business) - `list()` (resolves the Default Investor via
     the existing `DefaultInvestorProvider`) and `view(PortfolioId)` (throws a new
     `PortfolioNotFoundException` when the id is not this Investor's).
   - `PortfolioQueryController` (`@RestController`, **new**, separate from `CreatePortfolioController`)
     - `GET /api/portfolios` -> `List<PortfolioSummaryResponse>` `{id, name, positionCount}`;
     `GET /api/portfolios/{portfolioId}` -> the existing `Portfolio` representation (reuses
     `PortfolioResponseMapper` + `CreatePortfolioResponse`). `PortfolioSummaryMapper` (`@Component`)
     builds the summary.
   - `PortfolioExceptionHandler` - `assignableTypes` widened to include the new controller;
     `@ExceptionHandler(PortfolioNotFoundException.class)` -> `404` `application/problem+json`
     `type = /problems/portfolio-not-found`.
   - **No schema migration, no write path, no new business event.**
2. **Contract - contract-first** (`implementation/platform/contracts/openapi/openapi.yaml`, 3.0.3):
   - `GET /api/portfolios` -> `200` array of a new `PortfolioSummary` schema `{id, name, positionCount}`.
   - `GET /api/portfolios/{portfolioId}` -> `200` `Portfolio` (**reused**) / `404` `Problem` (**reused**).
   - Contract tests for both + the `404` + the empty-list `200 []`.
3. **Frontend** (`implementation/platform/frontend/web/src/app/portfolio/`):
   - `portfolio-query.service.ts` - `list()` / `getById(id)` against the two endpoints.
   - `PortfolioListComponent` - the **Home page** content: a Portfolios table (name + Position
     count, one row each), a clear **empty state**, loading + recoverable-error states, a
     "Create portfolio" action (-> `/portfolios/new`), and keyboard-operable rows that open the
     detail. `app.routes.ts` `''` -> this component.
   - `PortfolioDetailPageComponent` at `portfolios/:id` - Portfolio name + a **read-only** Positions
     table (ticker, market, quantity, currency, + initial purchase date / average purchase price
     when present), loading / error / **not-found** states, **no** edit / add / remove controls.
   - `sidebar.component.ts` - the "Portfolios" nav entry points at `/` (where Portfolios now live).
   - View models: a new `PortfolioSummary`; the detail reuses the existing `PortfolioView` /
     `PositionView` from `portfolio-creation.models.ts` (the FD001 create-response shape).
4. **E2E - two mandatory scenarios** (`implementation/platform/e2e/`):
   - **E2E-002** (`tests/fd003-portfolio-empty.spec.ts`) - its own first-running Playwright project
     so it sees a fresh, empty database: open Home -> empty-state message, **0** Portfolio rows.
   - **E2E-001** (`tests/FD003-portfolio-list.spec.ts`) - create **3** Portfolios via
     `POST /api/portfolios` (catalogued instruments, 1 / 2 / 3 Positions) -> Home shows all three
     with **exact identity + Position count** (tolerating other rows created by FD001/FD002 specs) ->
     click one -> detail shows that Portfolio's name and **exactly** its persisted Positions, none
     from the other two.
   - `playwright.config.ts` - add the `portfolio-empty` project (declared first).
5. **Regression**: FD001 create + FD002 selection + their E2Es unchanged; `./mvnw verify` (incl.
   >= 90 % coverage + ArchUnit) green; `ng test` green; `./start.sh` / `./stop.sh` / `./e2e.sh`
   interfaces unchanged.

**No** write on `portfolio` / `position`, **no** schema migration, **no** new dependency, **no** new
deployable / messaging / scheduler, **no** sorting / filtering / searching / pagination, **no**
change to Position/Portfolio meaning or the FD001 `ticker + market` identity, **no** unapproved
`product/` edit.

## Technical Context

**Language / Runtime**: Backend - Java 21, Spring Boot 3.5.6 (unchanged). Frontend - Angular 20,
TypeScript 5.8, standalone components + `HttpClient` (unchanged). E2E - `@playwright/test` (EN002).

**Primary Dependencies**: all **unchanged / reused** - no new backend, frontend, or e2e dependency.

**Storage**: PostgreSQL 16. **Read-only.** FD003 adds two derived Spring Data queries over the
existing `portfolio` / `position` tables (owned by the `portfolio` module). No migration.
`spring.jpa.hibernate.ddl-auto: none` unchanged.

**Testing**: `./mvnw -B clean verify` (Surefire + Failsafe/Testcontainers + JaCoCo `check` +
ArchUnit); `ng test` (Karma/Jasmine, ChromeHeadless); `./e2e.sh` (Playwright, Chromium,
containerized). New contract tests use `@WebMvcTest` + `swagger-request-validator` (established by
FD001/EN004).

**Target Platform**: local workstation + the EN002 containerized platform. One `core-service`
(ADR-001). nginx already proxies `/api/` -> backend (path preserved) - no nginx change.

**Project Type**: Web application - vertical read slice across `frontend/`, `backend/core-service`,
`contracts/`, and `e2e/`.

**Performance Goals**: SC-012 - Home list and a Portfolio detail each render within 2 s for a
realistic small dataset (<= ~20 Portfolios, <= ~50 Positions each). The list loads each Portfolio
aggregate with its Positions eagerly (one query per graph via `@EntityGraph`); acceptable at this
scale (OD-FD003-1 notes the `COUNT`-projection optimization if the dataset grows).

**Constraints**:
- ADR-003 layout for backend code; ArchUnit-enforced. New classes: read methods on the existing
  `domain.ports.PortfolioRepository`; `PortfolioNotFoundException` in `domain.exceptions`;
  `PortfolioQueryService` in `business`; `PortfolioQueryController` + `PortfolioSummaryResponse` +
  `PortfolioSummaryMapper` in `infrastructure.api.rest[/dto,/mapper]`; derived queries in
  `infrastructure.persistence.repository`.
- `PortfolioJpaRepository` rule: **derived queries only, no JPQL / native SQL** - both new queries
  are derived.
- Read-only: **no** `INSERT` / `UPDATE` / `DELETE`, no new row, no business event (FR-015, SC-005).
  Read transactions are `readOnly`.
- Every query scoped to the current (Default) Investor (FR-003; ADR-002).
- No provider/persistence-shaped field in any response (FR-021).
- Contract-first: the OpenAPI operations + contract tests land before/with the controller.
- FD001 + FD002 behavior, contracts, and E2Es unchanged (FR-016, FR-017).
- Both mandatory E2E scenarios are **closure gates** (FR-030; FD003 Sec.16).

**Scale/Scope**: ~2 backend read methods + 1 service + 1 controller + 1 DTO + 1 mapper + 1
exception + 1 exception-handler method; 2 OpenAPI operations + 1 schema; 1 frontend service + 1 list
component + 1 detail page + routes + sidebar; 2 E2E specs + 1 Playwright project. No new module, no
new deployable.

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Human-Governed Source of Truth | **PASS** | Implements the human-approved FD003 within ADR-001/002/003. Edits no `product/` doc (FR-033). Tech unchanged - no new dependency. |
| II | Definitions/Enablers Are Authoritative Intent | **PASS** | Traces to exactly one Feature Definition (FD003). Every FR maps to an FD003 Sec./BR/AC (spec Traceability table). Consumes FD001/FD002; expands neither. |
| III | Derived Artifacts & Repository Layout | **PASS** | Artifacts under `specs/FD003-...`; implementation under `implementation/platform/`; no root `src/` / `apps/`. |
| IV | No Invention; Surface Material Ambiguity | **PASS** | Six technical ODs confirmed by the human 2026-09-03. No product behavior invented - FD003 Sec.17/Sec.18 are the authority. |
| V | Enablers Stay Technical / Features Extend the Platform | **PASS** | Vertical read slice of the existing platform; no isolated app; `./start.sh` / `./e2e.sh` stay coherent. |
| VI | Hexagonal Architecture & Deterministic Logic | **PASS** | Read logic in `business` behind `domain.ports`; `domain` framework-free; no LLM; deterministic. |
| VII | Test-First for Deterministic Logic; Testcontainers by Default | **PASS** | The query rules (investor scoping, not-found) are TDD'd in `PortfolioQueryServiceTest`; the persistence queries + scoping are covered by Testcontainers ITs; no manually-installed DB. |
| VIII | Contract-First External APIs | **PASS** | Two OpenAPI operations + a schema added first, with contract tests; business language; RFC 9457 `404`; no persistence leakage; reuses the existing `Portfolio` / `Problem` schemas. |

**Repository-structure / technology-policy quick check:**

| Check | Status | Evidence |
|---|---|---|
| One `core-service` deployable (ADR-001) | PASS | no new service; `compose.yaml` unchanged |
| ADR-003 module layout | PASS | new classes placed per AR-055...AR-058 |
| No new technology / dependency | PASS | `pom.xml` / `package.json` / e2e `package.json` untouched |
| No new deployable / broker / scheduler / cache / search engine / persistence tech | PASS | FR-032 |
| No schema change | PASS | read-only; no `V4` |
| No write path / business event | PASS | FR-015; read transactions only |
| No speculative infrastructure | PASS | 2 endpoints + 2 views + 2 E2E specs |

**Result: PASS.** The ODs are technical and do not change FD003 business behavior.

## Open Decisions (technical) - ALL CONFIRMED by jaruiz 2026-09-03

| ID | Decision point | Confirmed position | Alternatives rejected |
|---|---|---|---|
| OD-FD003-1 | List read model | Load the full `Portfolio` aggregate (with Positions via `@EntityGraph`) for the list and map to `PortfolioSummary` `{id, name, positionCount}` in the REST layer. Simple; one code path; fine at FD003 scale (<= ~20 Portfolios - A11). | A lean `COUNT` projection query - needs JPQL (violates the `PortfolioJpaRepository` "derived only" rule) and a new read model; **recorded** as the optimization if the dataset grows. |
| OD-FD003-2 | Not-found semantics | Well-formed unknown / other-Investor Portfolio id -> `404` `application/problem+json`, `type = /problems/portfolio-not-found`, via a new `PortfolioNotFoundException` (`domain.exceptions`) mapped by `PortfolioExceptionHandler`. A malformed (non-UUID) `{portfolioId}` -> `400` (Spring default from a `UUID`-typed path var; status only - analyze A3). Empty list -> `200 []`. | A `403`/`404` split for "exists but not yours" - single Investor; leaks existence. `200` with a null body - not RESTful. A custom `400` problem body for the malformed id - unnecessary; the framework default is fine. |
| OD-FD003-3 | E2E-002 database isolation | Add a Playwright project `portfolio-empty` (`testMatch` the empty-state spec; the default `chromium` project `testIgnore`s it **and declares `dependencies: ['portfolio-empty']`**). `dependencies` is Playwright's only contractual project-ordering primitive (declaration order alone is not guaranteed - analyze A1) -> E2E-002 always runs to completion on the fresh, empty DB before any portfolio-creating spec. `e2e.sh` **not** changed. | Rely on declaration order without `dependencies` - not a Playwright guarantee. Prefix the spec file to sort first (`00-...`) - fragile. A DB-truncate fixture - adds a `pg` dependency and edges toward "direct DB setup" (FD003 Sec.16). |
| OD-FD003-4 | Detail response DTO | Reuse `CreatePortfolioResponse` + `PortfolioResponseMapper.toResponse` for `GET /api/portfolios/{id}` (it already is the `Portfolio` schema shape). The Java class name is a minor internal misnomer. | Rename `CreatePortfolioResponse` -> `PortfolioResponse` - touches FD001 controller + mapper + contract test (regression surface) for a cosmetic gain; deferred. |
| OD-FD003-5 | Query controller | A new `PortfolioQueryController` (`GET` operations) separate from `CreatePortfolioController` (`POST`). Keeps each controller single-purpose and the FD001 controller untouched. | Merge into one `PortfolioController` - more FD001 churn, no benefit. |
| OD-FD003-6 | Home / sidebar wiring | Route `''` renders `PortfolioListComponent` (Home = the list - FD003 Sec.17.1). The list page carries a "Create portfolio" action -> `/portfolios/new` (unchanged). `sidebar.component.ts` "Portfolios" -> `/`. | A dedicated `/portfolios` route separate from Home - FD003 Sec.1: Portfolios display *on the Home page*. |

## Project Structure

### Documentation (this feature)

```text
specs/FD003-list-and-view-portfolio-details/
|-- plan.md              # this file
|-- research.md          # Phase 0 - D1...D9
|-- data-model.md        # Phase 1 - read model + response shapes (no schema change)
|-- quickstart.md        # Phase 1 - scenarios A-G => AC-001...AC-007 + SC + E2E-001/002
|-- contracts/
|   |-- openapi/portfolios.read.yaml    # mirror fragment of the two GET operations + PortfolioSummary
|   `-- portfolio-read-ports.md         # PortfolioRepository read methods + PortfolioQueryUseCase contract
|-- checklists/requirements.md          # 16/16 (already passing)
`-- tasks.md             # Phase 2 - /speckit-tasks (NOT this command)
```

### Source Code (repository)

```text
implementation/platform/
|-- contracts/openapi/openapi.yaml                 # + GET /api/portfolios, + GET /api/portfolios/{portfolioId}, + PortfolioSummary schema
|
|-- backend/core-service/src/
|   |-- main/java/com/myfinaimanager/core/portfolio/
|   |   |-- domain/
|   |   |   |-- ports/PortfolioRepository.java          # + findAllByInvestor(InvestorId), + findByIdForInvestor(PortfolioId, InvestorId)
|   |   |   `-- exceptions/PortfolioNotFoundException.java   # NEW
|   |   |-- business/
|   |   |   |-- PortfolioQueryUseCase.java              # NEW - list() / view(PortfolioId)
|   |   |   `-- PortfolioQueryService.java              # NEW - @Service; resolves the Default Investor
|   |   `-- infrastructure/
|   |       |-- api/rest/
|   |       |   |-- PortfolioQueryController.java        # NEW - GET /api/portfolios [/ {portfolioId}]
|   |       |   |-- PortfolioExceptionHandler.java       # + assignableTypes += PortfolioQueryController; + @ExceptionHandler(PortfolioNotFoundException)
|   |       |   |-- dto/PortfolioSummaryResponse.java    # NEW - { id, name, positionCount }
|   |       |   `-- mapper/PortfolioSummaryMapper.java   # NEW - Portfolio -> PortfolioSummaryResponse
|   |       `-- persistence/
|   |           |-- repository/PortfolioJpaRepository.java   # + 2 derived @EntityGraph queries
|   |           `-- PortfolioPersistenceAdapter.java     # implement the 2 read-port methods (readOnly template)
|   `-- test/java/com/myfinaimanager/core/portfolio/
|       |-- business/PortfolioQueryServiceTest.java              # NEW - unit (mocked port), TDD
|       |-- infrastructure/api/rest/PortfolioQueryControllerContractTest.java   # NEW - @WebMvcTest + swagger-request-validator
|       `-- infrastructure/persistence/PortfolioPersistenceAdapterIT.java       # + read-query cases (Testcontainers)
|
|-- frontend/web/src/app/
|   |-- home.component.ts                            # renders <app-portfolio-list> (or is replaced by it)
|   |-- app.routes.ts                                # '' -> list ; 'portfolios/:id' -> detail
|   |-- core/layout/sidebar.component.ts             # "Portfolios" -> '/'
|   `-- portfolio/
|       |-- portfolio-query.service.ts (+ spec)      # NEW - GET /api/portfolios [/ {id}]
|       |-- portfolio.models.ts                      # + PortfolioSummary ; reuse PortfolioView/PositionView
|       |-- portfolio-list.component.ts (+ spec)     # NEW - Home table + empty/loading/error + "Create portfolio"
|       `-- portfolio-detail.page.ts (+ spec)        # NEW - name + Positions table (read-only) + not-found
|
`-- e2e/
    |-- playwright.config.ts                         # + project "portfolio-empty" (declared first)
    |-- support/portfolios.ts                        # NEW - create a Portfolio via POST /api/portfolios (E2E-001 setup)
    `-- tests/
        |-- fd003-portfolio-empty.spec.ts            # NEW - E2E-002 (own project, fresh DB)
        `-- FD003-portfolio-list.spec.ts             # NEW - E2E-001 (list 3 exact + detail)
```

**Structure Decision**: extend the existing `portfolio` backend module and frontend feature area
with read-only classes placed per ADR-003; add a separate query controller; make Home the list.
No new module, no new deployable, no new top-level directory, no schema migration.

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| E2E-002 sees Portfolios created by FD001/FD002/FD003-001 specs -> false failure | High | High | OD-FD003-3 - dedicated `portfolio-empty` Playwright project on the fresh DB, with the `chromium` project declaring `dependencies: ['portfolio-empty']` (contractual ordering, not declaration-order luck - analyze A1); E2E-001 tolerates extra rows and asserts its own 3 by identity. |
| List loads all Position rows for every Portfolio (N+positions) | Low | Low | FD003 scale is tiny (A11 <= ~20 Portfolios); `@EntityGraph` = one query per graph; `COUNT` projection recorded as the optimization (OD-FD003-1). |
| Widening `PortfolioExceptionHandler` `assignableTypes` changes FD001 error behavior | Low | Med | additive `@ExceptionHandler` only; the existing `PortfolioValidationException` / `PortfolioNotSavedException` handlers and their contract tests are unchanged; re-run the FD001 contract test. |
| `GET /api/portfolios/{id}` path collides with a future `POST /api/portfolios/{id}` | Low | Low | only `GET` is added; the path is the natural REST resource. |
| Home route change breaks the platform-smoke E2E | Med | Med | the smoke test's *intent* is "shell loads and is interactive", but it currently also asserts the sidebar "Portfolios" `href = /portfolios/new` and may assert the old Home copy - both change under FD003. **T027 explicitly audits and updates `platform-smoke.spec.ts`** (`href` -> `/`; drop/replace the placeholder-copy assertion) before running `./e2e.sh`. The list's empty/loading state must render without error on a fresh stack. |
| Deep-linking `/portfolios/:id` on refresh (no history) fails | Low | Med | the detail page loads by id from the API, not from router state; `portfolio-detail.page.spec.ts` covers the direct-load path. |
| Contract validator rejects the array response / new schema | Low | Med | keep 3.0.3; `PortfolioSummary` is a flat object; mirror the FD001 `Portfolio` schema style; contract test guards it. |
| A read accidentally opens a writable transaction | Low | Med | the persistence adapter's `readOnlyTemplate` is used for both new methods; an IT asserts no row count changes across a list+view. |

## Phase 0 - Research

See [research.md](./research.md). Decisions **D1-D9** cover: the read port + service shape (D1);
derived-query strategy + investor scoping + ordering (D2); the list read model (D3, OD-FD003-1);
not-found + empty-list semantics (D4, OD-FD003-2); the detail DTO reuse (D5, OD-FD003-4); the
separate query controller + exception-handler widening (D6, OD-FD003-5); the frontend list/detail
components + Home/sidebar wiring + states (D7, OD-FD003-6); the E2E-002 isolation via a first
Playwright project + E2E-001 API setup (D8, OD-FD003-3); and the test plan (D9). No
`NEEDS CLARIFICATION`.

## Phase 1 - Design & Contracts

Outputs: [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md).

**Post-Design Constitution re-check: PASS** - the design adds no framework dependency to `domain`,
no write path, no schema change, no new dependency; it is contract-first for the two operations,
TDD-able for the query rules, Testcontainers-covered for the persistence reads, and leaves FD001 +
FD002 behavior and contracts intact.
