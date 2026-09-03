# FD003 — List and View Portfolio Details · Closure Report

**Verified:** 2026-09-03 · **Verifier:** `/project-verify` (evidence-based gate, no code modified)

## Final Result

**READY TO CLOSE WITH WARNINGS**

## Summary

FD003 delivers exactly its approved scope: the Home page lists the current Investor's persisted
Portfolios (name + Position count, newest first) with an explicit empty state, and a row opens a
read-only detail view of that Portfolio and its Positions. Implementation is a clean read-only
vertical slice of the existing `portfolio` module (ADR-003), two new contract-first read endpoints,
no schema migration, no write path, no new dependency, **no `product/` edit**.

All quality gates pass fresh: `./mvnw -B clean verify` **BUILD SUCCESS** (Surefire 123 + Failsafe
60, 0 failures; JaCoCo line 96.41 % / branch 91.01 % — met; ArchUnit 14/14); `ng test` 58 SUCCESS;
`ng build` clean; `./e2e.sh` **6 passed** including both mandatory gates E2E-001 and E2E-002;
runtime `./start.sh` → endpoints behave per contract → `./stop.sh` (idempotent).

The only findings are non-blocking: the working tree is uncommitted (the established state for this
repo across FD001/FD002/EN00x) and two deliberately-deferred cosmetic items recorded in the ODs.

## Scope Compliance

| Aspect | Status | Evidence |
|---|---|---|
| In-scope: load Investor's Portfolios on Home | PASS | `PortfolioListComponent` on route `''` via `HomeComponent`; `GET /api/portfolios` |
| In-scope: table, one row per Portfolio, name + Position count | PASS | `portfolio-list.component.ts` table; `portfolio-list.component.spec.ts`; E2E-001 |
| In-scope: row selectable → detail view | PASS | each row name is a `routerLink` to `/portfolios/:id`; `PortfolioDetailPageComponent` |
| In-scope: detail loads Portfolio + persisted Positions, one row per Position | PASS | `GET /api/portfolios/{portfolioId}`; `portfolio-detail.page.*`; E2E-001 detail assertions |
| In-scope: clear empty state | PASS | `.empty` block "You do not have any portfolios yet." + hint + Create action; E2E-002 |
| In-scope: reuse FD001 Portfolio/Position data | PASS | reads existing `portfolio`/`position` tables; reused `Portfolio` OpenAPI schema + `PortfolioResponseMapper` |
| In-scope: preserve visual design conventions | PASS | design-system tokens, dark table, right-aligned numeric column, empty/loading/error states |
| Out-of-scope: edit/delete/add/remove Position | PASS (absent) | `portfolio-detail.page.spec.ts` asserts 0 buttons / no Edit-Remove-Add controls |
| Out-of-scope: valuation / performance / risk / recommendations / stop-loss | PASS (absent) | no such field or endpoint |
| Out-of-scope: sorting / filtering / searching / pagination | PASS (absent) | fixed newest-first order in the query; no query params, no controls |
| Scope expansion | NONE | only additive read endpoints + read views inside `portfolio`; `INSTRUMENT_NOT_IN_CATALOG` (FD002) intact |

## Requirement Coverage

| Requirement | Status | Evidence |
|---|---|---|
| AC-001 List one Portfolio | PASS | `portfolio-list.component.spec` (rows render); E2E-001 (Alpha, 1 position) |
| AC-002 List multiple, one row each | PASS | `portfolio-list.component.spec` (2 rows); `PortfolioPersistenceAdapterIT` (2 for investor); E2E-001 (3) |
| AC-003 Row shows name + Position count | PASS | contract test `$[0].positionCount`; `PortfolioSummaryMapper` = `positions().size()`; E2E-001 exact counts 1/2/3 |
| AC-004 No Portfolios → message, no misleading row | PASS | `portfolio-list.component.spec` (empty case, 0 `tbody tr`); **E2E-002** on fresh DB |
| AC-005 Newly created Portfolio is listed | PASS | runtime: `POST` then `GET /api/portfolios` shows it newest-first; E2E-001 creates via API then lists |
| AC-006 Open Portfolio detail | PASS | row `routerLink`; `portfolio-detail.page.spec` (loads by route id, shows name); E2E-001 click → `/portfolios/{id}` |
| AC-007 Correct Position detail; other Portfolios excluded | PASS | `PortfolioPersistenceAdapterIT.findByIdForInvestor` (only its own positions); E2E-001 (Gamma = exactly its 3, none of Alpha/Beta) |
| §16 E2E-001 (mandatory) | PASS | `e2e/tests/FD003-portfolio-list.spec.ts` — creates 3 via `POST /api/portfolios`, asserts identity + exact counts + detail; green in `./e2e.sh` |
| §16 E2E-002 (mandatory) | PASS | `e2e/tests/fd003-portfolio-empty.spec.ts` — own `portfolio-empty` project, `chromium` `dependencies: ['portfolio-empty']`; green on fresh DB |
| §15 regression of Portfolio creation | PASS | FD001 Surefire/Failsafe suites + `FD001-create-portfolio` E2E + `FD002-select-instrument` ×2 all green |
| BR-001 Investor Portfolios only | PASS | `findAllByInvestorIdOrderBy…` / `findByIdAndInvestorId` scope in the query; IT covers foreign-investor exclusion |
| BR-002 Persisted only | PASS | reads the DB; drafts never reach it |
| BR-003 Empty state | PASS | AC-004 evidence |
| BR-005 Position count = persisted Positions | PASS | `positions().size()` on the eagerly-loaded aggregate |
| BR-009 Read-only detail | PASS | `PortfolioQueryService` never calls `save`; adapter uses `readOnlyTemplate`; `the_read_queries_write_nothing` IT asserts 0 row-count change |
| §14 List response only required info / detail returns Portfolio + Positions / no provider leak | PASS | `PortfolioSummary` = `{id,name,positionCount}`; detail = reused `Portfolio`; contract test asserts no `investorId`/`idempotencyKey`/entity field |

## Architecture

| Check | Status | Evidence |
|---|---|---|
| ADR-001 one `core-service` deployable | PASS | no new service; `compose.yaml` unchanged |
| ADR-002 single seeded Default Investor | PASS | `PortfolioQueryService` resolves via `DefaultInvestorProvider` (same seam as FD001) |
| ADR-003 module layout (domain/business/infrastructure, deps inward) | PASS | `PortfolioNotFoundException`→`domain.exceptions`; `PortfolioQueryUseCase`/`Service`→`business`; `PortfolioQueryController`→`infrastructure.api.rest`; DTO→`…rest.dto`; mapper→`…rest.mapper`; derived queries→`…persistence.repository` |
| Hexagonal / dependency direction / domain framework-free | PASS | `StandardArchitectureRulesTest` **14/14** (unchanged rule set — new classes need no new rule) |
| Module boundaries / no cross-module persistence access | PASS | all FD003 code inside `portfolio`; reads only its own `portfolio`/`position` tables; AR-062 N/A (no inter-module read) |
| Public API boundary | PASS | contract-first; RFC 9457 `404`; business language; no persistence model exposed |
| Sync/async decision | PASS | synchronous read; no event, no broker (FD003 §11) |
| Repository structure | PASS | implementation under `implementation/platform/`; no `apps/` `services/` `src/` root |

## Technology Policy

| Technology | Policy | Used | Result |
|---|---|---:|---|
| Spring Boot / Spring MVC | ALLOWED | Yes (existing) | PASS |
| Spring Data JPA (derived queries, `@EntityGraph`) | PREFERRED default | Yes | PASS |
| PostgreSQL 16 | PREFERRED | Yes (read-only, no migration) | PASS |
| Testcontainers | REQUIRED for app-managed infra ITs | Yes (`PortfolioPersistenceAdapterIT`) | PASS |
| swagger-request-validator (MockMvc) | ALLOWED (established FD001/EN004) | Yes | PASS |
| Angular 20 / RxJS / Angular Router | ALLOWED (existing) | Yes | PASS |
| Playwright | ALLOWED (EN002) | Yes | PASS |
| New library / framework / broker / cache / search engine / vector DB | — | **None** | PASS |

`pom.xml`, `frontend/web/package.json`, `e2e/package.json` unchanged (verified via `git status`).

## Tests

| Suite | Command | Result |
|---|---|---|
| Backend unit + component + ArchUnit | `./mvnw -B test` | **123 run, 0 failures, 0 errors** |
| Backend integration (Testcontainers PostgreSQL) | `./mvnw -B verify` (Failsafe) | **60 run, 0 failures, 0 errors** |
| — incl. `PortfolioQueryServiceTest` | (Surefire) | 3/3 — order, hit, miss→`PortfolioNotFoundException`, `save` never called |
| — incl. `PortfolioQueryControllerContractTest` | (Surefire, `@WebMvcTest` + swagger-request-validator) | 5/5 — list `200`+empty `[]`, detail `200`, unknown→`404` problem conforms, non-UUID→`400` |
| — incl. `PortfolioPersistenceAdapterIT` (+4 FD003 cases) | (Failsafe, Testcontainers) | 9/9 — newest-first + positions, investor scoping, not-found, 0-writes |
| — incl. `CreatePortfolioControllerContractTest` (regression) | (Surefire) | 5/5 unchanged — widened advice did not change FD001 `400`/`503` |
| Architecture conformance | ArchUnit `StandardArchitectureRulesTest` | **14/14** |
| Coverage gate | JaCoCo `check` | **line 96.41 % · branch 91.01 %** — "All coverage checks have been met" (≥ 90 % both) |
| Frontend unit | `ng test --watch=false --browsers=ChromeHeadless` | **58 SUCCESS** (incl. `portfolio-query.service` 7, `portfolio-list.component` 7, `portfolio-detail.page` 7) |
| E2E (containerized, Chromium) | `./e2e.sh` | **6 passed**, exit 0 — `[portfolio-empty]` E2E-002 first, then `[chromium]` FD001 + FD002 ×2 + FD003 E2E-001 + platform-smoke |

No required test is failing or skipped.

## Build

| Build | Command | Result |
|---|---|---|
| Backend | `./mvnw -B clean verify` | **BUILD SUCCESS** |
| Frontend | `ng build` | Application bundle generation complete, no errors |
| Contract | `swagger-request-validator` against `contracts/openapi/openapi.yaml` in the contract tests | PASS (3.0.3, request + response validated) |
| Container images | `./e2e.sh` builds backend + frontend + e2e images fresh | all built, platform healthy |

## Runtime Verification

`./start.sh` (BUILD=1) → all containers healthy. Observed:

| Check | Result |
|---|---|
| `GET /actuator/health` | `{"status":"UP"}` (db UP) |
| `GET /api/portfolios` | `200` — array of `{id,name,positionCount}`, newest-first; no `investorId`/`status`/`positions` |
| `POST /api/portfolios` then `GET /api/portfolios` | the new portfolio appears first (AC-005) |
| `GET /api/portfolios/{id}` | `200` — reused `Portfolio` schema with its Positions; no `investorId`/`idempotencyKey` |
| `GET /api/portfolios/{unknown-uuid}` | `404` `application/problem+json` `type=/problems/portfolio-not-found`, `instance` set |
| `GET /api/portfolios/not-a-uuid` | `400` (framework default; no lookup) |
| `GET /portfolios/{id}` (deep link) | `200` — nginx SPA fallback serves the app |
| `./stop.sh` then `./stop.sh` again | both safe (idempotent); platform stopped; data volume kept |

Platform left **stopped** after verification.

## Security and Repository Hygiene

| Check | Status | Evidence |
|---|---|---|
| No committed secrets / `.env` / keys / tokens | PASS | secret scan of new files — none; no `.env` staged |
| Investor-owned resource isolation | PASS | every read scoped in the query to the Default Investor; foreign id → empty → `404` (no existence oracle); IT covers it |
| Inputs validated at trust boundary | PASS | `{portfolioId}` parsed as `UUID` at the controller edge |
| Synthetic test data only | PASS | E2E uses `uniquePortfolioName()` + catalogued tickers; unit/IT use fixed synthetic UUIDs |
| No build output / `node_modules` / `target` / `dist` committed | PASS | `git check-ignore` confirms all are ignored; none staged |
| Implementation in approved locations | PASS | all under `implementation/platform/{backend,frontend,contracts,e2e}` |
| Logs | PASS | structured logging unchanged; no sensitive data added |

## Documentation

| Item | Status | Evidence |
|---|---|---|
| Non-obvious reasoning documented | PASS | Javadoc on the port methods / `PortfolioNotFoundException` / `PortfolioQueryService`; component doc-comments state the states + read-only intent |
| Public contract documented | PASS | `openapi.yaml` operation descriptions; `specs/FD003-…/contracts/portfolio-read-ports.md` |
| Feature docs reflect approved behavior | PASS | `backend/core-service/README.md` (+FD003 read path) and `implementation/platform/README.md` (+capability row, +"Home now shows the list") updated |
| ADR added/updated | N/A | no significant architecture decision |
| Architecture diagrams | N/A | approved architecture unchanged |
| `product/` docs updated for global changes | N/A | no global definition changed; **no `product/` edit by this feature** |
| No generated doc contradicts product docs | PASS | specs trace to FD003; SDD artifacts consistent (`/speckit.analyze` A1–A5 remediated) |

## Definition of Done

| Item | Applicable | Status | Evidence |
|---|---|---|---|
| 1 Product & Specification (traceable, approved, in-scope, no invented rule, AC implemented) | Yes | PASS | FD003 §19 signed; `spec.md` traceability; scope table above |
| 2 Architecture (architecture.md / rules / tech policy / hexagonal / ownership / ADR) | Yes | PASS | ArchUnit 14/14; ADR-001/002/003 respected; no new tech |
| 3 Code Quality (readable, cohesive, no speculative abstraction, safe numerics, explicit errors) | Yes | PASS | decimals stay strings; `ListState`/`DetailState` unions; `404`/`null`/`'not-found'` explicit |
| 4 Testing (TDD, unit, integration, contract, arch, E2E, edge cases, all pass) | Yes | PASS | contract + service tests RED-first; Testcontainers IT; E2E-001/002; empty/`404`/`400`/foreign-id cases |
| 5 Coverage (≥ 90 %, meaningful assertions, exclusions justified) | Yes | PASS | line 96.41 % / branch 91.01 %; **no new exclusion**; assertions check order/scoping/leakage/0-writes |
| 6 APIs & Contracts (OpenAPI, matches, validation, error contract, no leakage, compat) | Yes | PASS | additive `listPortfolios`/`getPortfolio`/`PortfolioSummary`; contract test; RFC 9457; no leak |
| 7 Persistence (ownership, migration, constraints, tx boundaries, no dual write) | Partial | PASS | ownership explicit; **no migration needed** (read-only); reads on `readOnly` tx template; no write |
| 8 External Integrations | No | N/A | FD003 contacts no external provider |
| 9 AI / LLM | No | N/A | no LLM |
| 10 Security & Privacy (authz backend-side, resource isolation, no secrets, input validation) | Yes | PASS | investor scoping in the query; `UUID` boundary parse; no secrets; ADR-002 interim auth unchanged |
| 11 Observability | Partial | PASS | existing structured logging / actuator; no new external call to trace |
| 12 Resilience | Partial | PASS | reads idempotent; FE manual Retry on recoverable error; read-only → no partial-state corruption |
| 13 Documentation | Yes | PASS | READMEs + contract docs updated; no ADR needed |
| 14 Repository Hygiene | Yes | PASS | no artifacts committed; synthetic test data; no dependency change |
| 15 CI/CD | No CI | N/A | validated locally: `mvnw clean verify` + `ng test` + `ng build` + `./e2e.sh` all green |
| 16 Review (vs spec, vs arch rules, AI code reviewed, limitations explicit) | Yes | PASS | this report + `pr-evidence.md` + `dod-checklist.md`; deferred items recorded (OD-1, OD-4) |
| 17 Product Acceptance (evidence per scenario, matches intent, understandable, no undocumented assumptions) | Yes | PASS | quickstart execution record; E2E-001/002 green; Assumptions A1–A12 in `spec.md` |

## Findings

### FAILURES

None.

### WARNINGS

**W001 — Working tree is uncommitted.** All FD003 changes (18 new files + 14 modified) are unstaged
/ untracked; `specs/FD003-…/` is untracked. This is the established state of this repository
(FD001, FD002, EN002–EN004 are likewise uncommitted per project memory), so it is **non-blocking**
for the technical closure decision, but the feature is not yet recorded in version control.
*Remediation:* branch + commit the FD003 change set (backend, frontend, contracts, e2e, docs, specs)
with a message tracing to FD003.

**W002 — Deferred cosmetic items (accepted, recorded).** (a) The list operation loads the full
`Portfolio` aggregate rather than a `COUNT` projection (OD-FD003-1) — fine at FD003 scale; the
projection is recorded as the drop-in optimization if a realistic dataset makes the list slow.
(b) `GET /api/portfolios/{portfolioId}` reuses the Java type `CreatePortfolioResponse` for the
`Portfolio` schema (OD-FD003-4) — an internal misnomer; renaming it touches the FD001 controller +
contract test and was deliberately deferred. Neither affects behavior, the contract, or any AC.
*Remediation:* none required for closure; revisit if/when a follow-up feature touches these areas.

**W003 — Local dev database retains pre-existing portfolios.** The persistent `./start.sh` volume
still holds portfolios from earlier work-item verification (e.g. "Verify EN003"). This is expected
for the shared dev volume and does not affect FD003 (E2E uses a disposable DB per run). *Remediation:*
optional `docker compose … down -v` if a clean local list is wanted.

## Required Remediation

None for the technical closure decision. Recommended before/at merge:

1. Commit the FD003 change set to version control (W001).

## Final Decision

**READY TO CLOSE WITH WARNINGS.**

FD003 fully satisfies its approved scope, every acceptance criterion (AC-001…AC-007), both mandatory
E2E closure gates (E2E-001, E2E-002), all applicable architecture rules and Definition-of-Done
items, and the quality gates (build, tests, ≥ 90 % coverage, contract validation, runtime). No FAIL
finding, no unapproved material decision, no scope expansion, no new technology, no `product/` edit.
The warnings are non-blocking (uncommitted tree — the repo norm here; two accepted deferrals;
pre-existing local dev data).

Final closure remains a human decision. Recommended human action: approve closure and commit the
change set.
