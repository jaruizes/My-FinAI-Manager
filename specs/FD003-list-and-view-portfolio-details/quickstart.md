# Quickstart — FD003 List and View Portfolio Details

Runnable validation of FD003. Each scenario maps to FD003 Acceptance Criteria (AC-001…AC-007), the
spec Success Criteria (SC-001…SC-012), and the two mandatory E2E gates. Read-only — no schema
change, no write path.

## Prerequisites

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"     # Testcontainers / colima
cd implementation/platform
```

- FD001 delivered: `POST /api/portfolios`, persisted `portfolio` / `position` data, the `Portfolio`
  / `Position` OpenAPI schemas.
- FD002 delivered: the `INSTRUMENT_NOT_IN_CATALOG` check — E2E-001 uses catalogued instruments.
- EN004 catalog populated on backend start (AAPL·XNAS·USD, MSFT·XNAS·USD, ASML·XAMS·EUR, SAN·XMAD·EUR, …).

---

## A. Backend build + all suites (SC-009)

```bash
cd implementation/platform/backend/core-service
./mvnw -B clean verify
```

**Expected**: `BUILD SUCCESS`. Surefire + Failsafe green incl. `PortfolioQueryServiceTest`,
`PortfolioQueryControllerContractTest`, the new read cases in `PortfolioPersistenceAdapterIT`, and
the re-run `CreatePortfolioControllerContractTest` (widened advice, unchanged `400`/`503`). JaCoCo
bundle ≥ 90 % line **and** branch. ArchUnit **14/14** (new classes in the right ADR-003 packages).

---

## B. Query rules — unit (AC-006, FR-013; SC-005)

`PortfolioQueryServiceTest` (mocked `PortfolioRepository` + `DefaultInvestorProvider`):

| Given | When | Then |
|---|---|---|
| the repo returns 2 portfolios for the Default Investor | `list()` | those 2, in the repo's order; `save` never called |
| the repo has the portfolio for id `X` | `view(X)` | that portfolio |
| the repo returns empty for id `Y` | `view(Y)` | `PortfolioNotFoundException` carrying `Y`; `save` never called |

→ **SC-005** (0 writes), investor scoping delegated to the query.

---

## C. Persistence reads — Testcontainers (AC-007, FR-003, FR-011; SC-003, SC-005)

Extend `PortfolioPersistenceAdapterIT` — seed via the existing write path: 2 portfolios for the
Default Investor (Positions 1 and 3) + 1 for a different investor id (Positions 2):

- `findAllByInvestor(defaultInvestor)` → exactly the 2 Default-Investor portfolios, **newest first**,
  each with its Positions loaded; the other investor's portfolio is absent.
- `findByIdForInvestor(p1.id, defaultInvestor)` → `p1` with **only** `p1`'s Positions.
- `findByIdForInvestor(otherInvestorPortfolio.id, defaultInvestor)` → **empty**.
- `findByIdForInvestor(randomUuid, defaultInvestor)` → **empty**.
- `SELECT count(*)` on `portfolio` and `position` is **unchanged** across a `list()` + `view()`
  sequence → **SC-005**.

---

## D. Contract (FR-018, FR-019, FR-020, FR-022; SC-006, SC-008)

`PortfolioQueryControllerContractTest` (`@WebMvcTest` + `swagger-request-validator`):

- `GET /api/portfolios` → `200` array conforms to `openapi.yaml`; each item is
  `{ id, name, positionCount }` and **only** those; an Investor with none → `200 []`.
- `GET /api/portfolios/{id}` → `200` conforms to the `Portfolio` schema.
- `GET /api/portfolios/{unknown}` → `404 application/problem+json`,
  `$.type == "/problems/portfolio-not-found"`, conforms to `Problem`.
- `GET /api/portfolios/not-a-uuid` → `400` (Spring's `MethodArgumentTypeMismatchException` default);
  assert the status only — no specific problem `type` is required.
- No `investorId` / `idempotencyKey` / entity / provider field in any body → **SC-008**.
- Inspect `openapi.yaml`: `PortfolioSummary` present; `listPortfolios` / `getPortfolio` operations
  present; 3.0.3.

---

## E. Frontend — list & detail (AC-001…AC-005; SC-001, SC-002)

```bash
cd implementation/platform/frontend/web
npm test    # ng test --watch=false --browsers=ChromeHeadless
```

`portfolio-query.service.spec.ts` — maps list + detail; `404` → `'not-found'`; other error → `null`.

`portfolio-list.component.spec.ts`:
- 2 summaries → a table with 2 rows, each showing the name and `positionCount` → **AC-001, AC-002,
  AC-003**
- `[]` → the empty-state message "No portfolios yet." + a "Create portfolio" action, **0** rows →
  **AC-004 / SC-002**
- loading state before results; error state with **Retry** after a failed load
- a row is a keyboard-operable link to `/portfolios/:id` → **AC-006 nav**

`portfolio-detail.page.spec.ts`:
- loads by the route id; shows the Portfolio name + a Positions table (ticker/market/quantity/
  currency; date & average price only when present) → **AC-006, AC-007, FR-010**
- a `404` → the "not found" state with a link back to Home → **FR-013**
- **no** edit / add-Position / remove-Position control anywhere → **FR-012 / BR-009**

---

## F. End-to-end — the two mandatory scenarios (FR-030; SC-010)

```bash
cd implementation/platform
./e2e.sh
```

**Expected**: the run grows to **6 passed** (Chromium), exit 0 —
`FD001-create-portfolio` + `FD002-select-instrument` (×2) + `platform-smoke` **unchanged and
green**, plus:

- **`fd003-portfolio-empty.spec.ts` (E2E-002)** — its own first-running Playwright project, so it
  sees the fresh empty DB: open Home → the empty-state message is shown, **0** Portfolio rows →
  **AC-004 / SC-002 / FD003 §16 E2E-002**
- **`FD003-portfolio-list.spec.ts` (E2E-001)** — `beforeAll` creates **3** Portfolios via
  `POST /api/portfolios` (catalogued instruments) with **1 / 2 / 3** Positions and unique names →
  open Home → assert each of the 3 by **name + exact Position count** (other rows tolerated) →
  click the 3-Position Portfolio → detail shows its name and **exactly** its 3 Positions with the
  created values, **none** from the other two → **AC-006, AC-007 / SC-001, SC-003, SC-004 / FD003
  §16 E2E-001**

FD003 **cannot** be closed if either scenario is missing or red → **SC-010** (FR-030).

`./e2e.sh` also confirms `platform-smoke` still passes with **Home now rendering the Portfolio
list** (empty state on the fresh stack).

---

## G. FD001 / FD002 non-regression + scope review (FR-016, FR-017; SC-007, SC-011)

```bash
cd implementation/platform/backend/core-service && ./mvnw -B verify        # FD001 + FD002 suites green
cd ../../frontend/web && npm test                                          # FD001 + FD002 frontend specs green
cd ../.. && ./start.sh
curl -s localhost:4200/api/portfolios                                      # [] or the seeded list
curl -s -XPOST localhost:4200/api/portfolios -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: qs-1' -d '{"name":"QS","positions":[{"ticker":"AAPL","market":"XNAS","quantity":"1","currency":"USD"}]}'
curl -s localhost:4200/api/portfolios                                      # now contains "QS" with positionCount 1
curl -s localhost:4200/api/portfolios/<that-id>                            # the Portfolio + its 1 Position
curl -s -o /dev/null -w "%{http_code}\n" localhost:4200/api/portfolios/00000000-0000-4000-8000-000000000000  # 404
./stop.sh
git diff --stat
```

**Expected**:
- every FD001 Create Portfolio scenario and the FD002 selection scenario (dialog + backend
  `INSTRUMENT_NOT_IN_CATALOG`) still pass → **SC-007**
- `git diff` touches only: `openapi.yaml` (+2 operations + 1 schema), `core/portfolio/**`
  (+2 read methods, +`PortfolioNotFoundException`, +`PortfolioQueryService`/`UseCase`,
  +`PortfolioQueryController`, +`PortfolioSummaryResponse`/`Mapper`, +1 exception-handler method,
  +2 derived queries), `frontend/web/src/app/**` (list + detail + service + routes + sidebar),
  `e2e/**` (2 specs + 1 Playwright project + a create helper), and the READMEs — **no** schema
  migration, **no** write on `portfolio`/`position`, **no** new deployable/messaging/scheduler,
  **no** sorting/filtering/pagination, **no** new dependency, **no** unapproved `product/` edit →
  **SC-011**

---

## Verification Criteria coverage

| FD003 AC | Scenario(s) |
|---|---|
| AC-001 List one Portfolio | E, F (E2E-001) |
| AC-002 List multiple (one row each) | C, E, F |
| AC-003 Row shows name + Position count | C, E, F |
| AC-004 No Portfolios → empty state, no misleading row | E, F (E2E-002) |
| AC-005 Newly created Portfolio is listed | E, F, G (curl) |
| AC-006 Open Portfolio detail | E, F (E2E-001) |
| AC-007 Correct Position detail; other Portfolios excluded | C, E, F (E2E-001) |
| §16 E2E-001 (mandatory) | F |
| §16 E2E-002 (mandatory) | F |
| §15 regression of Portfolio creation | A, G |

| SC | Scenario(s) |
|---|---|
| SC-001 exact rows + counts for N = 1,2,3 | C, F |
| SC-002 empty state, 0 rows | E, F |
| SC-003 detail = exactly that Portfolio's Positions | C, F |
| SC-004 displayed values match persisted exactly | E, F, G |
| SC-005 0 writes / 0 events | B, C |
| SC-006 unknown id → 404, 0 data leak | C, D, G |
| SC-007 FD001/FD002 100 % still pass | A, G |
| SC-008 0 provider/persistence fields in responses | D |
| SC-009 mvnw verify + ng test green, gates hold | A, E |
| SC-010 both mandatory E2E pass | F |
| SC-011 scope: no migration / write / new tech / product edit | G |
| SC-012 list & detail render < 2 s | F (walkthrough) |

---

## Execution record — 2026-09-03

Run locally (no CI), colima Docker, Node 20.19.1.

| Step | Command | Result |
|---|---|---|
| A | `./mvnw -B clean verify` | **BUILD SUCCESS** — Surefire **123**, Failsafe **60**, 0 failures / 0 errors. JaCoCo bundle **line 96.41 % · branch 91.01 %** (gate ≥ 90 % both — passed). ArchUnit **14/14**. |
| B | `PortfolioQueryServiceTest` | 3/3 — `list()` returns the repo result in order & never calls `save`; `view` hit; `view` miss → `PortfolioNotFoundException` carrying the id. |
| C | `PortfolioPersistenceAdapterIT` (Testcontainers) | 9/9 (5 existing + 4 new) — `findAllByInvestor` newest-first with positions & foreign portfolio absent; empty investor → `[]`; `findByIdForInvestor` owner-hit / foreign-miss / random-uuid-miss; `count(*)` on `portfolio`+`position` unchanged across `findAll`+`findById` (SC-005). |
| D | `PortfolioQueryControllerContractTest` | 5/5 — list `200` conforms + no `status`/`investorId`/`positions` leak; empty `[]`; detail `200` conforms to `Portfolio` + no `investorId`/`idempotencyKey`; unknown id → `404` `application/problem+json` `$.type=/problems/portfolio-not-found` `$.instance=/api/portfolios/{id}` conforms; `not-a-uuid` → `400` (status only). `CreatePortfolioControllerContractTest` re-run 5/5 unchanged. |
| E | `ng test` | **58 SUCCESS** — incl. `portfolio-query.service.spec` (7), `portfolio-list.component.spec` (7: loading / rows w/ name+count / row link `/portfolios/:id` / empty state w/ Create action & 0 rows / error+Retry reload / no edit-delete control), `portfolio-detail.page.spec` (7: loading / name + one row per position / `—` for absent date+price not `0` / `404` not-found + link home / error+Retry / no mutation control). |
| E | `ng build` | Application bundle generation complete, no errors. |
| F | `./e2e.sh` | **6 passed**, exit 0 — `[portfolio-empty] fd003-portfolio-empty` (**E2E-002**: fresh DB → empty-state message + 0 rows + Create action) ran first via `dependencies`, then `[chromium]` `FD001-create-portfolio` + `FD002-select-instrument` ×2 + `FD003-portfolio-list` (**E2E-001**: 3 portfolios w/ 1/2/3 positions created via `POST /api/portfolios`; Home shows each by name + exact count; open the 3-position one → detail = exactly its 3 positions, none of the others') + `platform-smoke` (green with Home now rendering the list; sidebar `href='/'`). |
| G | scope review | `git diff --stat` limited to `openapi.yaml`, `core/portfolio/**` (read-only additions), `frontend/web/src/app/**` (list + detail + service + models + routes + sidebar + home + app.component.spec), `e2e/**` (config + 2 specs + helper + smoke assertion), 2 READMEs, `specs/FD003-**`. **No** migration, **no** write, **no** new dependency, **no** new deployable, **no** `product/` edit. |
| SC-012 | walkthrough (`./start.sh`) | Home list and a portfolio detail each render well within 2 s on the seeded small dataset (single `@EntityGraph` query per aggregate; dataset ≤ ~20 portfolios). |

All AC-001…AC-007 and SC-001…SC-012 covered — see the mapping tables above; every referenced
scenario produced a green result in this run.
