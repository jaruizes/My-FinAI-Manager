# FD003 — List and View Portfolio Details · PR Evidence

## What requirement does this implement?

**FD003 — List and View Portfolio Details** (Feature Definition, Approved 2026-09-03 — §19 signed
by jaruiz). A **read-only** vertical slice: the Home page lists the investor's persisted portfolios
(name + position count, newest first) with a clear empty state; selecting a row opens a read-only
detail view of that portfolio and its positions. No creation, editing, sorting, filtering,
searching, or pagination.

## Which specification / tasks does it trace to?

- Feature: `product/definition/features/FD003-list-and-view-portfolio-details/FD003-list-and-view-portfolio-details.md` (§19 signed; §18 no open questions).
- Consumed capabilities: FD001 (`POST /api/portfolios`, the persisted `portfolio` / `position` data, the `Portfolio` / `Position` OpenAPI schemas), FD002 (`INSTRUMENT_NOT_IN_CATALOG` — must not regress; E2E-001 uses catalogued instruments).
- Governing: ADR-001 (one `core-service` — unchanged), ADR-002 (single seeded Default Investor), ADR-003 (Standard Spring Backend Architecture), EN002 (containerized Playwright E2E), `product/ux/design-system.md`. **No new ADR.**
- SDD artifacts: `specs/FD003-…/` — `spec.md` (33 FR, 12 SC, US1–US4), `plan.md` (Constitution Check PASS; OD-FD003-1…6 confirmed by jaruiz 2026-09-03), `research.md` (D1–D9), `data-model.md`, `contracts/` (`openapi/portfolios.read.yaml`, `portfolio-read-ports.md`), `quickstart.md` (A–G), `tasks.md` (T001–T033), `checklists/requirements.md` (16/16). `/speckit.analyze` findings A1–A5 remediated 2026-09-03.

## What changed?

| Area | Change |
|---|---|
| Contract | `openapi.yaml` — **+`GET /api/portfolios`** (`listPortfolios` → array of the **new `PortfolioSummary` schema** `{id,name,positionCount}`; empty array, never `404`) and **+`GET /api/portfolios/{portfolioId}`** (`getPortfolio` → the **reused `Portfolio` schema**; `404` `Problem` `/problems/portfolio-not-found`; `400` for a non-UUID segment). `info.description` updated. Additive, OpenAPI 3.0.3. |
| Backend — domain | **NEW** `portfolio.domain.exceptions.PortfolioNotFoundException` (carries the requested id). `portfolio.domain.ports.PortfolioRepository` — **+2 read methods** `findAllByInvestor(InvestorId)` (newest-first) and `findByIdForInvestor(PortfolioId, InvestorId)`; existing methods unchanged. |
| Backend — business | **NEW** `PortfolioQueryUseCase` + `PortfolioQueryService` (`@Service`, ctor `(PortfolioRepository, DefaultInvestorProvider)`): `list()` scopes to the Default Investor; `view(id)` throws `PortfolioNotFoundException` on a miss. Never calls `save`. Not `@Transactional`. |
| Backend — infrastructure | `PortfolioJpaRepository` — **+2 derived `@EntityGraph` queries** `findAllByInvestorIdOrderByCreatedAtDescIdDesc`, `findByIdAndInvestorId` (no JPQL). `PortfolioPersistenceAdapter` — implements the 2 read-port methods on its existing `readOnlyTemplate`. **NEW** `PortfolioQueryController` (`@RestController`, separate from `CreatePortfolioController`); `{portfolioId}` typed `UUID` → framework `400` on a non-UUID. **NEW** `dto/PortfolioSummaryResponse` (record) + `mapper/PortfolioSummaryMapper` (`positions().size()`). `PortfolioExceptionHandler` — `assignableTypes` widened to both controllers; **+1** `@ExceptionHandler(PortfolioNotFoundException)` → `404` `application/problem+json` `/problems/portfolio-not-found`; the FD001 `400`/`503` handlers unchanged. Detail reuses `PortfolioResponseMapper` / `CreatePortfolioResponse`. |
| Frontend — service / models | **NEW** `portfolio.models.ts` (`PortfolioSummary`, `ListState`, `DetailState`; re-exports `PortfolioView` / `PositionView`). **NEW** `portfolio-query.service.ts` — `list()` (`GET /api/portfolios`, error → `null`) and `getById(id)` (`GET /api/portfolios/:id`, `404` → `'not-found'`, other error → `null`). |
| Frontend — list | **NEW** `portfolio-list.component.ts` — the Home content: loading / results table (name + right-aligned position count, each row a keyboard-operable link to `/portfolios/:id`) / **empty state** ("You do not have any portfolios yet." + hint + "Create portfolio" → `/portfolios/new`) / recoverable error + Retry. `home.component.ts` now hosts `<app-portfolio-list>` (route `''` stable). |
| Frontend — detail | **NEW** `portfolio-detail.page.ts` at `portfolios/:id` — portfolio name + a **read-only** positions table (ticker/market/quantity/currency always; initial purchase date / average purchase price only when present, `—` otherwise, values verbatim); loading / not-found (link Home) / error + Retry. **No** edit/add/remove control. `app.routes.ts` — `+portfolios/:id`. |
| Frontend — nav | `sidebar.component.ts` — "Portfolios" `routerLink` `/portfolios/new` → **`/`** (Portfolios live on Home). `app.component.spec.ts` — the nav-entry assertion updated to `href='/'`. |
| E2E | `playwright.config.ts` — **+project `portfolio-empty`** (runs `fd003-portfolio-empty.spec.ts`); the `chromium` project `testIgnore`s it and declares **`dependencies: ['portfolio-empty']`** so E2E-002 always completes on the fresh DB first. **NEW** `support/portfolios.ts` (`createPortfolio` via `POST /api/portfolios`). **NEW** `tests/fd003-portfolio-empty.spec.ts` (E2E-002). **NEW** `tests/FD003-portfolio-list.spec.ts` (E2E-001 — creates 3 portfolios with 1/2/3 positions, asserts each by name + exact count on Home, opens the 3-position one, asserts its detail is exactly its positions). `platform-smoke.spec.ts` — the sidebar `href` assertion updated `/portfolios/new` → `/` (A2). |
| Tests (backend) | **NEW** `PortfolioQueryServiceTest` (mocked port + provider, 3 cases incl. `save` never called). **NEW** `PortfolioQueryControllerContractTest` (`@WebMvcTest` + `swagger-request-validator`, 5 cases: list `200` + empty `[]` + detail `200` + `404` problem + non-UUID `400`). `PortfolioPersistenceAdapterIT` **+4** Testcontainers cases (newest-first + investor scoping + not-found + 0-writes). `CreatePortfolioControllerContractTest` re-run unchanged (widened advice must not change `400`/`503`). |
| Tests (frontend) | **NEW** `portfolio-query.service.spec.ts` (7), `portfolio-list.component.spec.ts` (7), `portfolio-detail.page.spec.ts` (7). |
| Docs | `backend/core-service/README.md` (+the FD003 read path, +`findAll…`/`findById…` queries, +`PortfolioNotFoundException`); `implementation/platform/README.md` (+"List and view portfolio details" capability row, +Home-now-shows-the-list note). |

**Not changed**: no Flyway migration (no `V4`), no write on `portfolio` / `position`, no business
event, no `pom.xml` / `package.json` / e2e `package.json` dependency, no new deployable / broker /
scheduler / cache / search engine, no `compose.yaml` / `start.sh` / `stop.sh` / `e2e.sh`, no
Java/Angular major version, no change to `Portfolio` / `Position` meaning or the FD001 `ticker +
market` identity, no change to `create-portfolio.page.ts` / `portfolio-api.service.ts`, no `product/`
edit.

## Why this design?

- **Read methods on the existing `PortfolioRepository`, not a new port** (research D1): the
  aggregate has one owner; "look up portfolios" is the same responsibility. The query rule (scope to
  the investor; `404` on a miss) is business orchestration → `PortfolioQueryService` in `business`
  behind the port, not in the controller.
- **Load the full aggregate for the list** (D3 / OD-FD003-1): at FD003 scale (≤ ~20 portfolios) one
  `@EntityGraph` query per graph keeps a single persistence code path; a `COUNT` projection is
  recorded as the drop-in optimization if a realistic dataset ever makes the list slow.
- **Derived queries only** (D2): `findAllByInvestorIdOrderByCreatedAtDescIdDesc` /
  `findByIdAndInvestorId` honour the repo's "no JPQL" rule; investor scoping is *in the query*, so a
  foreign portfolio is never returned then filtered. `id desc` tiebreak makes the order total.
- **Separate `PortfolioQueryController`** (D6 / OD-FD003-5): one controller per HTTP concern; the
  FD001 POST controller is untouched. The advice is reused (one `/problems/*` formatting site) by
  widening `assignableTypes`.
- **Reuse the `Portfolio` schema for the detail** (D5 / OD-FD003-4): FD003 §14 — an equivalent
  approved representation already exists; the Java name `CreatePortfolioResponse` is a minor internal
  misnomer, not worth an FD001 regression to rename now.
- **`404` vs `400`** (D4 / analyze A3): a well-formed unknown id is a `404` problem; a non-UUID
  segment is not a resource reference → the `UUID`-typed path variable yields Spring's default `400`
  (status only, no custom handler).
- **Home = the list** (D7 / OD-FD003-6): FD003 §17.1. A page of data, not a focused action, so not a
  dialog. Rows are links for keyboard / deep-link / open-in-new-tab.
- **E2E-002 isolation via a dependent Playwright project** (D8 / OD-FD003-3, analyze A1): `e2e.sh`
  gives one disposable DB per run; `dependencies: ['portfolio-empty']` is Playwright's only
  contractual ordering primitive, so the empty-state check always runs first. No `e2e.sh` change, no
  new dependency.

## How was it tested?

Local only (no CI):

- **`./mvnw -B clean verify`** → **Surefire 123 + Failsafe 60, 0 failures / 0 errors**. JaCoCo
  bundle **line 96.41 % · branch 91.01 %** (≥ 90 % both — gate passed). **ArchUnit 14/14** green
  (the new classes land in the right ADR-003 packages — no new rule needed).
- **`ng test`** (Node 20.19.1, ChromeHeadless) → **58 SUCCESS**.
- **`./e2e.sh`** → **6 passed** (exit 0): `[portfolio-empty] fd003-portfolio-empty` (E2E-002, fresh
  DB) → `[chromium]` `FD001-create-portfolio` + `FD002-select-instrument` (×2) + `FD003-portfolio-list`
  (E2E-001) + `platform-smoke`, all green. `platform-smoke` passes with Home now rendering the list.
- Manual: `quickstart.md` §A–§G (see its coverage tables).

## Architecture boundaries

Internal only. ADR-001 intact (one `core-service`; `compose.yaml` unchanged). No new outbound port,
no HTTP between modules, no new coupling — the FD003 code is entirely inside the `portfolio` module.
Two new external **read** operations, contract-first. Read-only: `PortfolioQueryService` never calls
a mutator; the adapter runs both new methods on its `readOnly` transaction template; an IT asserts
`portfolio` / `position` row counts are unchanged across a `list()` + `view()` sequence (SC-005).

## ADRs

No new ADR — two read endpoints + two read views inside the existing module, no topology /
persistence-technology / messaging / security / deployment change.

## Risk-Register outcome (plan.md)

| Risk | Outcome |
|---|---|
| E2E-002 sees portfolios created by other specs → false failure | **Handled** — dedicated `portfolio-empty` project with `dependencies: ['portfolio-empty']` on `chromium` (contractual ordering, not declaration-order luck); E2E-001 tolerates extra rows and asserts its own 3 by identity. Run: E2E-002 first, green. |
| List loads all position rows for every portfolio (N+positions) | **Accepted** — FD003 scale is tiny; `@EntityGraph` = one query per graph; `COUNT` projection recorded (OD-FD003-1). |
| Widening `PortfolioExceptionHandler` `assignableTypes` changes FD001 error behavior | **Avoided** — additive `@ExceptionHandler` only; `CreatePortfolioControllerContractTest` re-run unchanged (`400` / `503` bodies intact). |
| `GET /api/portfolios/{id}` path collides with a future `POST /api/portfolios/{id}` | **N/A** — only `GET` added; natural REST resource path. |
| Home route change breaks `platform-smoke` | **Handled** — `platform-smoke.spec.ts` sidebar `href` assertion updated to `/` (A2); smoke green with Home rendering the empty list on the fresh stack. |
| Deep-linking `/portfolios/:id` on refresh fails | **Handled** — the detail page loads by id from the API, not router state; `portfolio-detail.page.spec.ts` covers the direct-load path. |
| Contract validator rejects the array / new schema | **Avoided** — 3.0.3, `PortfolioSummary` is a flat object; `PortfolioQueryControllerContractTest` green incl. the empty `[]` case. |
| A read accidentally opens a writable transaction | **Avoided** — `readOnlyTemplate` for both methods; `the_read_queries_write_nothing` IT asserts 0 row-count change. |

## OD outcomes

OD-FD003-1…6 all confirmed by jaruiz 2026-09-03 and implemented as planned. `/speckit.analyze`
findings applied: A1 (`dependencies: ['portfolio-empty']`), A2 (`platform-smoke` audit), A3
(malformed id → `400` in spec / contracts / handler), A4 (contract test RED-first), A5
(`HomeComponent` kept as route host).

## `product/` change

**None.** No `product/` document was edited by this feature.

## What evidence shows acceptance criteria pass?

`quickstart.md` → "Verification Criteria coverage" + Success-Criteria tables — every `AC-001…AC-007`
and `SC-001…SC-012` mapped to a green result from the run above (incl. the SC-012 < 2 s
list/detail render walkthrough).
