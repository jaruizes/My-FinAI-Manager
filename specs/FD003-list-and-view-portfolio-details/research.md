# Research — FD003 List and View Portfolio Details

Phase 0 of `/speckit-plan`. Each decision: **Decision · Rationale · Alternatives considered**.
Inputs: the approved FD003 Feature Definition (§17 Explicit Product Decisions, §16 mandatory E2E),
FD001 (`Portfolio` / `Position` domain + OpenAPI schemas, `PortfolioRepository`,
`PortfolioPersistenceAdapter`, `DefaultInvestorProvider`, `PortfolioExceptionHandler`), FD002
(`INSTRUMENT_NOT_IN_CATALOG` — must not regress), ADR-002/003, `architecture-rules.md` (AR-055…AR-061),
`product/ux/design-system.md`, the constitution, the existing Playwright/e2e setup.

No `NEEDS CLARIFICATION` markers exist. Six residual items are technical Open Decisions
(OD-FD003-1…6) carried into `plan.md` for confirmation.

---

## D1 — Read port + query service shape (D → FR-018, FR-023, constitution VI/VII)

**Decision**:
- Extend the existing **`portfolio.domain.ports.PortfolioRepository`** with two **read-only**
  methods (no separate read-only port — it is still "look up portfolios"):
  ```java
  List<Portfolio> findAllByInvestor(InvestorId investorId);              // newest-first
  Optional<Portfolio> findByIdForInvestor(PortfolioId id, InvestorId investorId);
  ```
- New **`portfolio.business.PortfolioQueryService`** (`@Service`) implementing
  **`PortfolioQueryUseCase`**:
  ```java
  List<Portfolio> list();                       // resolves the Default Investor, calls findAllByInvestor
  Portfolio view(PortfolioId id);               // findByIdForInvestor or throw PortfolioNotFoundException
  ```
  It depends only on `PortfolioRepository` + `DefaultInvestorProvider` (both existing).
- `PortfolioQueryService` is **read-only** — it never calls `save`, never opens a write transaction
  (the adapter uses its `readOnlyTemplate`).

**Rationale**: The query "logic" is thin (scope to the Investor; 404 on miss) but it is still
*business orchestration* and belongs in `business` behind a port, not in the controller (CLAUDE.md
§7; ADR-003). Reusing `PortfolioRepository` keeps one persistence collaborator for the aggregate.
`DefaultInvestorProvider` is the same seam FD001 uses, so multi-investor auth drops in later without
touching FD003.

**Alternatives considered**:
- *A dedicated `PortfolioReadRepository` port* — more interfaces for no isolation benefit; the
  aggregate has one owner.
- *Query straight from the controller* — business rule (investor scoping, not-found) in an adapter;
  rejected.
- *CQRS read model / separate read schema* — massively over-engineered for a read of one small
  aggregate (AR — prefer the simplest design).

---

## D2 — Derived queries, investor scoping, ordering (D → FR-003, FR-006, FR-024, A3)

**Decision**: `PortfolioJpaRepository` gains two **derived** queries (honouring its "derived only,
no JPQL/native SQL" rule):
```java
@EntityGraph(attributePaths = "positions")
List<PortfolioEntity> findAllByInvestorIdOrderByCreatedAtDescIdDesc(UUID investorId);

@EntityGraph(attributePaths = "positions")
Optional<PortfolioEntity> findByIdAndInvestorId(UUID id, UUID investorId);
```
- **Investor scoping** is in the query (`…AndInvestorId`), not a post-filter — a Portfolio that is
  not this Investor's is simply not returned (→ `findByIdForInvestor` returns empty → `404`).
- **Order**: `createdAt` descending, `id` descending as a deterministic tiebreak (A3). User-facing
  sorting is out of scope (FD003 §3); a fixed order is required only so the table and the E2E
  assertions are deterministic. Ascending is equally acceptable if planning prefers it.
- `@EntityGraph(positions)` loads the aggregate + its Positions in **one** query per portfolio graph
  (no N+1), so the persistence mapper can reconstitute the full `Portfolio` and the Position count
  is `entity.getPositions().size()`.

**Rationale**: Derived queries keep the repo rule intact and are perfectly expressive for
"by investor, ordered" and "by id + investor". `@EntityGraph` is already the pattern this repo uses
(`findByIdempotencyKey`).

**Alternatives considered**:
- *`@Query` JPQL with a `count`* — violates the repo's stated rule; deferred as an optimization
  (OD-FD003-1 / D3).
- *`created_at` only, no tiebreak* — two portfolios created in the same millisecond could reorder
  between calls; the `id` tiebreak makes it total.

---

## D3 — List read model: full aggregate vs projection (OD-FD003-1)

**Decision**: The list operation **loads the full `Portfolio` aggregate** (D2) and the REST layer
maps each to `PortfolioSummary { id, name, positionCount }` (`PortfolioSummaryMapper`). The
`positionCount` is the size of the loaded Positions collection.

**Rationale**: FD003's dataset is tiny (A11 — ≤ ~20 Portfolios, ≤ ~50 Positions each). Loading the
aggregate is a handful of small queries and keeps **one** persistence code path (the existing
`PortfolioPersistenceMapper.toDomain`) rather than a parallel projection path. Simplicity now,
optimize only on evidence.

**Alternatives considered**:
- *A `COUNT` projection query* (`select new …Summary(p.id, p.name, size(p.positions)) …`) — one
  cheap query, no Position rows loaded. **Rejected now**: needs JPQL (repo rule), a new read model,
  and a second mapper — cost > benefit at this scale. **Recorded** as the drop-in optimization if a
  realistic dataset ever shows the list is slow (SC-012 is the trigger).
- *A denormalised `position_count` column on `portfolio`* — a schema change + a write-path
  concern; firmly out of scope (FR-023).

---

## D4 — Not-found + empty-list semantics (OD-FD003-2 → FR-022, SC-006)

**Decision**:
- **`GET /api/portfolios/{portfolioId}`** for an id that is not the current Investor's →
  **`404 application/problem+json`**, `type = /problems/portfolio-not-found`,
  `title = "Portfolio not found"`, `instance = /api/portfolios/{id}`. Backed by a new
  `portfolio.domain.exceptions.PortfolioNotFoundException` (carries the id), mapped by
  `PortfolioExceptionHandler`.
- **`GET /api/portfolios`** for an Investor with none → **`200`** with an **empty JSON array**
  (never `404`).
- No distinction between "does not exist" and "exists but belongs to another Investor" — there is
  one Investor (ADR-002) and the distinction would leak existence.
- A **malformed** `{portfolioId}` (not a UUID) → **`400`**: `PortfolioQueryController` types the
  path variable as `java.util.UUID`, so Spring's `MethodArgumentTypeMismatchException` default `400`
  fires before any lookup (no custom handler, no guaranteed problem `type`). "Unknown resource"
  (`404`) stays distinct from "not a resource reference" (`400`) — analyze A3.

**Rationale**: RFC 9457 for the error (constitution VIII; matches FD001's `/problems/*` family).
An empty collection is a valid, successful list result — `200 []` is the REST norm and lets the
frontend render the empty state without treating it as an error (BR-003, FR-004).

**Alternatives considered**:
- *`204 No Content` for an empty list* — loses the "it's an array" shape; harder for the client.
- *`403` for "not yours"* — single Investor; also an existence oracle.

---

## D5 — Detail response DTO (OD-FD003-4 → FR-020)

**Decision**: `GET /api/portfolios/{portfolioId}` returns the **existing `Portfolio` OpenAPI
schema** (`id, name, status, positions[], createdAt` + `Position`), produced by the **existing**
`PortfolioResponseMapper.toResponse(Portfolio)` → `CreatePortfolioResponse`. No new detail schema,
no new mapper.

**Rationale**: FD003 §14 — "unless an existing approved contract already provides an equivalent
representation". It does. The `Portfolio` schema already carries exactly what FD003 §6 needs
(ticker, market, quantity, currency, optional date / average price) and the mapper already formats
decimals losslessly (FR-010, SC-004). The Java class name `CreatePortfolioResponse` is a minor
internal misnomer — not worth an FD001 rename now (regression surface).

**Alternatives considered**:
- *Rename `CreatePortfolioResponse` → `PortfolioResponse`* — cosmetic; touches the FD001 controller,
  mapper, and contract test. Deferred to a later tidy-up (OD-FD003-4).
- *A slimmer detail schema without `status` / `createdAt`* — no benefit; `status` is always
  `ACTIVE` and `createdAt` is harmless public info already in the create response.

---

## D6 — Query controller + exception-handler widening (OD-FD003-5 → FR-018, FR-022)

**Decision**:
- New **`PortfolioQueryController`** (`@RestController`, `infrastructure.api.rest`) — owns
  `GET /api/portfolios` and `GET /api/portfolios/{portfolioId}` only. `CreatePortfolioController`
  (POST) is untouched.
- **`PortfolioExceptionHandler`** — change
  `@RestControllerAdvice(assignableTypes = CreatePortfolioController.class)` to
  `assignableTypes = { CreatePortfolioController.class, PortfolioQueryController.class }` and add
  `@ExceptionHandler(PortfolioNotFoundException.class)` → `ProblemDetail` `404`. The existing
  `PortfolioValidationException` (400) and `PortfolioNotSavedException` (503) handlers are
  unchanged.

**Rationale**: One controller per HTTP concern keeps each small and the FD001 controller
regression-free. Reusing the existing advice keeps one place for the portfolio `/problems/*` family
and one RFC 9457 formatting style; widening `assignableTypes` is additive.

**Alternatives considered**:
- *A separate `@RestControllerAdvice` for the query controller* — a second problem-formatting site
  to keep consistent; no benefit.
- *Merge GET into `CreatePortfolioController`* — more FD001 churn, mixes concerns.

---

## D7 — Frontend list + detail + Home/sidebar wiring + states (OD-FD003-6 → FR-001…FR-014, FR-026…FR-028)

**Decision**:
- **`portfolio-query.service.ts`** (`@Injectable({providedIn:'root'})`) — `list(): Observable<PortfolioSummary[] | null>`
  and `getById(id): Observable<PortfolioView | 'not-found' | null>` (`null` = recoverable error;
  `'not-found'` from a `404`). Mirrors `PortfolioApiService`'s "shape the request, classify the
  response" style (AR-013).
- **`PortfolioListComponent`** — the Home route (`''`). A `<table>` of `{ name, positionCount }`
  rows; each row is an accessible link/button → `routerLink="/portfolios/{{id}}"` (keyboard +
  pointer, visible focus). States: **loading** (skeleton/spinner), **results** (table), **empty**
  ("No portfolios yet." + a "Create portfolio" call-to-action → `/portfolios/new`), **error**
  (recoverable message + Retry). A header "Create portfolio" action is present in the non-empty
  state too.
- **`PortfolioDetailPageComponent`** — route `portfolios/:id`. Reads the id from the route and
  calls `getById`. Shows the Portfolio **name** and a read-only `<table>` of Positions
  (`ticker`, `market`, `quantity`, `currency`, `initialPurchaseDate` / `averagePurchasePrice` shown
  only when present). States: **loading**, **loaded**, **not-found** ("This portfolio doesn't
  exist." + a link back to Home), **error** (Retry). **No** edit / add / remove controls anywhere.
- **`app.routes.ts`** — `''` → `PortfolioListComponent` (via `HomeComponent` or directly);
  `portfolios/:id` → `PortfolioDetailPageComponent`; `portfolios/new` unchanged.
- **`sidebar.component.ts`** — the "Portfolios" entry `routerLink` → `/` (Portfolios live on Home).
- **View models** (`portfolio.models.ts`) — `PortfolioSummary { id: string; name: string; positionCount: number }`;
  the detail reuses `PortfolioView` / `PositionView` already defined in `portfolio-creation.models.ts`.

**Rationale**: FD003 §17.1 puts the list on Home. A dialog is wrong here (it's a page of data, not a
focused action — design system §"Dialogs"). The 4 list states / 4 detail states map directly to the
design-system §"Loading States" / §"Empty States" / §"Error States". Row-as-link is the accessible,
deep-link-friendly way to "select a row" (FD003 §12).

**Alternatives considered**:
- *A `/portfolios` route distinct from Home* — contradicts FD003 §1/§17.1.
- *Client-side caching of the list* — premature; the dataset is tiny and the list re-loads on
  navigation to Home.
- *Row `(click)` handler instead of a link* — worse for keyboard / deep-link / open-in-new-tab.

---

## D8 — E2E: E2E-002 isolation + E2E-001 setup (OD-FD003-3 → FR-030, FR-031)

**Decision**:
- **`playwright.config.ts`** gains a project declared **first**:
  ```ts
  projects: [
    { name: 'portfolio-empty', testMatch: /fd003-portfolio-empty\.spec\.ts$/, use: { ...devices['Desktop Chrome'] } },
    { name: 'chromium', testMatch: /\.spec\.ts$/, testIgnore: /fd003-portfolio-empty\.spec\.ts$/,
      dependencies: ['portfolio-empty'], use: { ...devices['Desktop Chrome'] } },
  ]
  ```
  `dependencies: ['portfolio-empty']` makes Playwright **guarantee** the `portfolio-empty` project
  runs to completion before the `chromium` project starts — declaration order alone is *not* a
  Playwright ordering guarantee (analyze A1). With `workers: 1` and `e2e.sh`'s one disposable DB per
  run, the empty-state spec therefore always sees the **fresh, empty** database before the `chromium`
  project's FD001 / FD002 / FD003-001 specs create anything.
- **`tests/fd003-portfolio-empty.spec.ts` (E2E-002)** — open Home → assert the empty-state message
  is visible and `app-portfolio-list tbody tr` count is `0`.
- **`tests/FD003-portfolio-list.spec.ts` (E2E-001)** — in `beforeAll`, create **3** Portfolios via
  `request.post('/api/portfolios')` (a real production API — allowed by FD003 §16) with catalogued
  instruments from EN004's fixtures and **1 / 2 / 3** distinct Positions
  (e.g. Alpha = [AAPL·XNAS·USD]; Beta = [AAPL·XNAS·USD, MSFT·XNAS·USD]; Gamma = [AAPL, MSFT,
  ASML·XAMS·EUR]), unique run-scoped names. Then: open Home → for each of the 3, assert its **row
  exists** with the **exact** Position count (tolerating other rows from FD001/FD002 specs) → click
  Gamma's row → assert the detail shows Gamma's name and **exactly** its 3 Positions with the
  created values, and **none** of Alpha's / Beta's Positions.
- **`support/portfolios.ts`** — a `createPortfolio(request, name, positions)` helper wrapping the
  API call + `Idempotency-Key`.

**Rationale**: `e2e.sh` provides one disposable DB per invocation; the only way both mandatory
scenarios pass in that one run is to run the empty-state check before any creation. A first Playwright
project is explicit and needs **no** `e2e.sh` change and **no** new dependency. E2E-001 tolerating
extra rows follows FD003 §16 ("verify the identity and count of each expected Portfolio rather than
only verifying that three rows exist").

**Alternatives considered**:
- *Rely on declaration order alone (no `dependencies`)* — Playwright does **not** guarantee project
  execution order from declaration order; `dependencies` is the only contractual ordering primitive
  (analyze A1).
- *Prefix the empty spec `00-…` to sort first* — works by convention but is fragile and unobvious.
- *A `TRUNCATE portfolio, position` fixture before E2E-002* — needs a Postgres client dependency in
  the e2e package and reads as "direct DB manipulation" (FD003 §16 steers away from it).
- *Direct DB inserts for E2E-001 setup* — explicitly discouraged by FD003 §16; the creation API is
  available.

---

## D9 — Test plan (D → FR-029, FR-030, constitution VII)

**Decision**:

| Layer | Tests |
|---|---|
| Unit (backend, TDD) | `PortfolioQueryServiceTest` — with a mocked `PortfolioRepository` + `DefaultInvestorProvider`: (a) `list()` returns what the repo returns for the Default Investor, in order; (b) `view(id)` returns the portfolio when the repo has it; (c) `view(id)` throws `PortfolioNotFoundException` (carrying the id) when the repo returns empty; (d) neither method calls `save`. |
| Integration (Testcontainers) | Extend `PortfolioPersistenceAdapterIT` — seed (via the existing write path) two Portfolios for the Default Investor + one for a different investor id: `findAllByInvestor` returns only the Default Investor's, newest-first, each with its Positions; `findByIdForInvestor` returns the aggregate for a hit and empty for another investor's id / an unknown id; a `list()` + `view()` sequence changes **0** rows (`SELECT count(*)` on `portfolio` / `position` unchanged). |
| Contract | `PortfolioQueryControllerContractTest` (`@WebMvcTest(PortfolioQueryController.class)` + `@Import` the mapper + advice + `swagger-request-validator`): `GET /api/portfolios` `200` array conforms to `openapi.yaml` (+ empty `[]` case); `GET /api/portfolios/{id}` `200` `Portfolio` conforms; unknown id → `404` `application/problem+json` `type=/problems/portfolio-not-found` conforms; assert **no** persistence/provider field in any body. |
| Contract (regression) | Re-run `CreatePortfolioControllerContractTest` — the widened advice must not change FD001's `400` / `503` bodies. |
| Frontend unit | `portfolio-query.service.spec.ts` — maps list + detail, classifies `404` → `'not-found'`, other errors → `null`. `portfolio-list.component.spec.ts` — loading → results (rows w/ name + count) / empty ("No portfolios yet." + Create action, 0 rows) / error (Retry); row is a keyboard-operable link to `/portfolios/:id`. `portfolio-detail.page.spec.ts` — loads by route id; name + Positions table; optional fields shown only when present; `not-found` state; **no** edit/add/remove controls present. |
| Architecture | `StandardArchitectureRulesTest` unchanged — the new classes sit in the correct ADR-003 packages; run it to confirm still 14/14. |
| E2E | `fd003-portfolio-empty.spec.ts` (E2E-002, first project) + `FD003-portfolio-list.spec.ts` (E2E-001). `FD001-create-portfolio.spec.ts`, `FD002-select-instrument.spec.ts` unchanged and green. **`platform-smoke.spec.ts` needs a small update** (A2): it asserts the sidebar "Portfolios" `href` is `/portfolios/new` and may assert the old Home placeholder copy — both change under FD003 (sidebar → `/`, Home → the list). Re-point the `href` assertion to `/` and drop / replace the placeholder-copy assertion; the shell-loads-and-is-interactive intent is unchanged. |
| Regression | full `./mvnw -B clean verify` (Surefire + Failsafe + JaCoCo ≥ 90 % line+branch + ArchUnit); `ng test`; `./e2e.sh`. |

**Rationale**: The query rules are deterministic → TDD. Persistence reads + investor scoping →
Testcontainers. The two new operations → contract tests, plus an FD001 contract regression for the
widened advice. The two mandatory browser scenarios are first-class E2E specs. Coverage gate
unchanged; the new service + controller + mapper are covered by the above.
