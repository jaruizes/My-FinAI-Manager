# Quickstart — FD002 Select Financial Instrument from Catalog

Runnable validation of FD002. Each scenario maps to FD002 Acceptance Criteria (AC-001…AC-009) and
the spec Success Criteria (SC-001…SC-012). Uses only EN004's committed deterministic catalog — **no
Internet**.

## Prerequisites

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"     # Testcontainers / colima
cd implementation/platform
```

- EN004 delivered: `financial_instrument` / `market` populated on backend start; catalog contains
  `AAPL · XNAS · USD`, `MSFT · XNAS · USD`, `SPY · ARCX · USD` (ETF), `SAN · XMAD · EUR`,
  `IBE · XMAD · EUR`, `ADS · XETR · EUR`, `ASML · XAMS · EUR`, … plus deliberate non-selectable rows.
- FD001 delivered: `POST /api/portfolios`, the Create Portfolio page at `/portfolios/new`.

---

## A. Backend build + all suites (SC-009)

```bash
cd implementation/platform/backend/core-service
./mvnw -B clean verify
```

**Expected**: `BUILD SUCCESS`. Surefire + Failsafe green (incl. the new
`CreatePortfolioServiceTest` catalog cases, `CatalogInstrumentCatalogAdapterIT`,
`FinancialInstrumentCatalogAdapterIT.findSelectable*`, and the
`CreatePortfolioControllerContractTest` `INSTRUMENT_NOT_IN_CATALOG` case). JaCoCo bundle ≥ 90 %
line **and** branch. ArchUnit: 14 rules green (12 existing + the 2 new
`portfolio ↔ financialinstrument` boundary rules), non-vacuous. → **SC-009**

---

## B. The new validation rule — unit (AC-006; SC-003)

`CreatePortfolioServiceTest` with a fake `InstrumentCatalog` (`isSelectable(ticker, market, currency)` keys on all three):

| Given | When | Then |
|---|---|---|
| all positions selectable | `create(command)` | portfolio created; `save` called once |
| `positions[0]` = `AAPL / XMAD / USD` (wrong market — fake returns `false`) | `create` | `PortfolioValidationException`; `errors` has exactly `{ field: "positions[0]", code: INSTRUMENT_NOT_IN_CATALOG }`; `save` **never** called |
| `positions[0]` = `AAPL / XNAS / EUR` (right ticker+market, wrong currency — fake has only `AAPL/XNAS/USD`) | `create` | `INSTRUMENT_NOT_IN_CATALOG` on `positions[0]`; `save` never called *(A1 — currency participates)* |
| `positions[0]` quantity `"0"` (structural) **and** `positions[1]` non-catalogued | `create` | one exception carrying **both** `NOT_POSITIVE` (positions[0]) and `INSTRUMENT_NOT_IN_CATALOG` (positions[1]) |
| `positions[0]` blank ticker / market / currency | `create` | only the FD001 `REQUIRED` / `CURRENCY_FORMAT`; the fake catalog is **not** consulted for that position |

→ **SC-003** (rule holds without the UI), all-violations-in-one-pass preserved.

---

## C. `isSelectable` / `findSelectable` — Testcontainers (AC-007; SC-004)

`CatalogInstrumentCatalogAdapterIT` + `FinancialInstrumentCatalogAdapterIT` against real PostgreSQL,
catalog seeded via the `financialinstrument` writer port (active `AAPL·XNAS·USD` + `SAN·XMAD·EUR`,
an inactive listing, a GBP listing):

- `isSelectable("AAPL","XNAS","USD")` and `isSelectable("aapl","XNAS","usd")` → `true`
- `isSelectable("SAN","XMAD","EUR")` → `true`  → **EUR path (FR-026)**
- `isSelectable("AAPL","XNAS","EUR")` → `false` (currency mismatch)  → **A1**
- `isSelectable("AAPL","XMAD","USD")` → `false` (wrong market)
- an `active = false` seeded listing → `false`  → **AC-007 / SC-004**
- a seeded `GBP` listing → `false` (currency filter)
- `isSelectable("NOSUCH","XNAS","USD")` → `false`
- `findSelectable` returns at most one row **with its currency**; local query only (no outbound call).

---

## D. Contract — `INSTRUMENT_NOT_IN_CATALOG` (FR-020; SC-008)

`CreatePortfolioControllerContractTest` (`@WebMvcTest` + `swagger-request-validator`):

- service throws `PortfolioValidationException` with one `INSTRUMENT_NOT_IN_CATALOG` on
  `positions[0]` → response `400 application/problem+json`, **conforms to `openapi.yaml`**,
  `$.type == "/problems/portfolio-validation"`, `$.errors[0].code == "INSTRUMENT_NOT_IN_CATALOG"`,
  `$.errors[0].field == "positions[0]"`.
- Inspect `openapi.yaml`: `ValidationProblem.errors[].code.enum` contains `INSTRUMENT_NOT_IN_CATALOG`;
  no provider/persistence field anywhere in the operation. → **SC-008**

---

## E. Frontend — search & select (AC-001, AC-002, AC-003, AC-008; SC-001, SC-005)

```bash
cd implementation/platform/frontend/web
npm test    # or: ng test --watch=false --browsers=ChromeHeadless
```

`instrument-search.service.spec.ts`:
- trims the query; issues **no** request for `""` / `"   "`; maps the response to `CatalogListing[]`;
  HTTP error → `error` state.

`add-position.dialog.spec.ts`:
- there is **no** free-text `ticker` / `market` / `currency` input → **SC-001 / AC-001**
- type `aapl` → `results` state → listbox has `Apple Inc. — AAPL · XNAS · USD`
- select it (keyboard `ArrowDown`+`Enter`) → `selected` state shows `AAPL · XNAS · USD`; single
  listing → no listing selector; "Add position" enabled after quantity `3` → **AC-001, AC-003**
- type `iberdrola` → the `IBE · XMAD · EUR` row appears → **AC-002**
- type `zzzznope` → `no-results` state, message shown, no way to proceed → **AC-008 / SC-005**
- (synthetic) an instrument with two seeded listings → selecting it shows the Market/Currency
  selector with exactly those two options → **FR-007**
- search failure → `error` state + Retry button → **FR-015**

---

## F. End-to-end — the mandatory closure journey (AC-004, AC-005, AC-009; SC-007, SC-010)

```bash
cd implementation/platform
./e2e.sh
```

**Expected**: `2 passed` becomes `3 passed` (Chromium), exit 0 —
`FD001-create-portfolio.spec.ts` + `platform-smoke.spec.ts` **unchanged and green**, plus the new
`FD002-select-instrument.spec.ts`:

```text
/portfolios/new → name it → Add position →
type "Apple" → listbox → select "Apple Inc. — AAPL · XNAS · USD" (keyboard) →
dialog shows controlled AAPL / XNAS / USD  (assert: no free-text ticker/market/currency inputs) →
quantity "3" → Add position → Save → "created successfully" confirmation
```

- The E2E asserts **zero** outbound calls to any non-`/api` host (`page.on('request')`) → **SC-007**
- `syntheticPosition()` stays `ASML / XAMS / EUR` (catalogued) → FD001 E2E passes unchanged → **AC-009 / SC-006**
- FD002 cannot be closed if this spec is missing or red → **SC-010** (FR-028)

---

## G. FD001 non-regression + scope review (AC-009; SC-006, SC-011)

```bash
cd implementation/platform/backend/core-service && ./mvnw -B verify        # FD001 ITs + suites green
cd ../../frontend/web && npm test                                          # FD001 frontend specs green
cd ../.. && ./start.sh && curl -s localhost:4200/api/financial-instruments?query=AAPL && ./stop.sh
git diff --stat
```

**Expected**:
- Every FD001 Create Portfolio scenario still passes (single/multi position, duplicate rejection,
  optional data, idempotent Save, transient-failure) → **AC-009 / SC-006**
- `git diff` touches only: `openapi.yaml` (+1 enum value + example), `core/portfolio/**`
  (+`ValidationCode` value, +`InstrumentCatalog` port, +`CatalogInstrumentCatalogAdapter`,
  `CreatePortfolioService` dependency), `core/financialinstrument/**` (+`findSelectable`),
  `StandardArchitectureRulesTest` (+2 rules), `frontend/web/src/app/portfolio/**`,
  `e2e/tests/FD002-select-instrument.spec.ts`, and the READMEs — **no** schema migration, **no**
  Position/Portfolio meaning change, **no** new deployable/messaging/scheduler/search-engine, **no**
  new dependency, **no** unapproved `product/` edit → **SC-011**

---

## Verification Criteria coverage

| FD002 AC | Scenario(s) |
|---|---|
| AC-001 Select known instrument → controlled t/m/c | E, F |
| AC-002 Search by name | E |
| AC-003 Search by ticker | E, F |
| AC-004 Controlled Market | F (UI), B/C (backend) |
| AC-005 Controlled Currency | F (UI), B/C (backend) |
| AC-006 Invalid combination cannot be created | B, C, D |
| AC-007 Inactive instrument not selectable | C |
| AC-008 No results → informed, text not accepted | E |
| AC-009 FD001 flow continues unchanged | F, G |
| §14 mandatory E2E closure gate | F |

| SC | Scenario(s) |
|---|---|
| SC-001 no free-text t/m/c | E |
| SC-002 case-insensitive search + exact fill | E, F |
| SC-003 no non-catalogued Position creatable | B, C, D |
| SC-004 inactive / non-EUR-USD never offered | C, E |
| SC-005 clear no-results, no proceed path | E |
| SC-006 100 % FD001 scenarios still pass | F, G |
| SC-007 zero external-provider calls from the FE | F |
| SC-008 zero provider/persistence fields in payloads | D |
| SC-009 `./mvnw verify` + `ng test` green, gates hold | A, E |
| SC-010 mandatory E2E passes | F |
| SC-011 scope: no meaning change / new tech / product edit | G |
| SC-012 add a Position for a known ticker < 30 s | F (walkthrough) |

---

## Verification run — 2026-09-03

| AC / SC | Result | Concrete evidence |
|---|---|---|
| AC-001 / SC-002 | PASS | `add-position.dialog.spec.ts` "selects a USD single-listing instrument … fills ticker/market/currency"; E2E selects `Apple` → `AAPL/XNAS/USD` |
| AC-002 | PASS | dialog spec searches `santander` → `SAN·XMAD·EUR` row; E2E searches "Apple" by name |
| AC-003 | PASS | dialog spec `aapl` (lower case); E2E `san` |
| AC-004 / AC-005 | PASS | dialog spec: Market/Currency selector offers only the instrument's real listings; `CatalogInstrumentCatalogAdapterIT` rejects wrong market / wrong currency |
| AC-006 / SC-003 | PASS | `CreatePortfolioServiceTest` (`AAPL/XMAD/USD` → `INSTRUMENT_NOT_IN_CATALOG`, `save` never called; `AAPL/XNAS/EUR` currency-mismatch → rejected); `CreatePortfolioControllerContractTest` `400` conforms to `openapi.yaml` |
| AC-007 / SC-004 | PASS | `CatalogInstrumentCatalogAdapterIT.a_wrong_market_inactive_or_unknown_is_not_selectable` (inactive `SC·XNAS`); a non-EUR/USD listing cannot be persisted at all (`SupportedCurrency`) |
| AC-008 / SC-005 | PASS | dialog spec "no-results state and no way to accept the typed text"; E2E `FD002: a search that matches nothing …` |
| AC-009 / SC-006 | PASS | all FD001 backend suites green after seeding the catalog in `AbstractPortfolioIT`; `FD001-create-portfolio.spec.ts` (Add Position step adapted) green |
| §14 / SC-010 | PASS | `./e2e.sh` → **4 passed**, exit 0 — incl. `FD002-select-instrument.spec.ts` main journey |
| SC-001 | PASS | dialog spec "has no free-text ticker / market / currency inputs"; E2E helper asserts `input[formControlName="ticker|market|currency"]` count 0 |
| SC-007 | PASS | E2E asserts every http(s) request stays on the frontend origin (0 external) |
| SC-008 | PASS | contract test validates the `400` body against `openapi.yaml`; `CatalogListing` has business fields only |
| SC-009 | PASS | `./mvnw -B clean verify` → Surefire 115 + Failsafe 56, 0F/0E; JaCoCo **line 96.28 % · branch 91.01 %**; ArchUnit 14/14; `ng test` → 40 SUCCESS |
| SC-011 | PASS | `pr-evidence.md` scope review — no schema migration, no new dependency, no new deployable, no Position meaning/identity change; `product/` edits = FD002 §18/§19 sync + `architecture-rules.md` AR-062 (both human-approved) |
| SC-012 | PASS | E2E adds a Position (search → select → quantity → confirm) in ~1.4 s |
| ArchUnit non-vacuous (VC-017 style) | PASS | T030 — deliberate `portfolio.domain.ports → financialinstrument.domain.model` reference fails `portfolio_core_is_free_of_financialinstrument` → reverted → 14/14 |

`./mvnw -B clean verify` — Surefire **115** + Failsafe **56**, 0F/0E · `ng test` — **40** ·
`./e2e.sh` — **4 passed**.
