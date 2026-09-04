# FD004 — Portfolio Valuation & Allocation · Implementation Evidence

**Date**: 2026-09-04 · **Branch**: `FD004-portfolio-valuation-and-allocation` · Spec-Kit `/speckit-implement`

All 68 tasks in [tasks.md](./tasks.md) complete. Repo remains uncommitted (established project state).

## Gate results

| Gate | Command | Result |
|---|---|---|
| Backend build + tests + ArchUnit + coverage (SC-009) | `./mvnw -o clean verify` | **BUILD SUCCESS** — Surefire **258** (3 skipped = EN005 opt-in smoke), Failsafe **69**, 0 failures/errors; `StandardArchitectureRulesTest` **21/21**; JaCoCo bundle line **95.96 %+** / branch **≥ 90 %** — `jacoco:check` passed |
| Frontend tests (SC-009) | `npm test -- --watch=false` (Node 20.19.1, ChromeHeadless) | **TOTAL: 71 SUCCESS** (58 prior + 13 FD004) |
| E2E incl. the two mandatory gates (SC-010) | `./e2e.sh` | **8 passed** — incl. `fd004-valuation.spec.ts` (E2E-001) and `fd004-provider-failure.spec.ts` (E2E-002); FD001/FD002/FD003 + `platform-smoke` still green; `finnhub-stub` container only, **no outbound Internet** |
| Runtime lifecycle (SC-005) | `./start.sh` → probes → `./stop.sh` ×2 | health `UP`; create → `201` + persisted + listed; `GET …/valuation` → `200 FAILED` (blank key, every money field `null` — **never `0`**); `not-a-uuid` → `400`; random UUID → `404` `/problems/portfolio-not-found`; `event=FinnhubIntegrationDisabled` logged; `stop.sh` idempotent |

## Scope review (SC-012)

- **Migrations**: only `V4__portfolio_valuation.sql` added (V1/V2/V3 untouched). No `ALTER`/`DROP` on `portfolio` / `position` / EN004 tables.
- **OpenAPI**: `git diff openapi.yaml` is **purely additive** — one path (`GET /api/portfolios/{portfolioId}/valuation`) + `PortfolioValuation` / `PositionValuation` / `SectorAllocation` schemas. FD003's `Portfolio` / `PortfolioSummary` schemas and `GET /api/portfolios[/{id}]` are byte-unchanged.
- **Dependencies**: no `pom.xml` / `package.json` change for FD004 (the `pom.xml` diff shown by git is pre-existing EN005 work). No new Maven/npm dependency. The E2E `finnhub-stub` is a ~60-line dependency-free Node script (E2E infra, not an app dependency).
- **No** new deployable service, message broker, scheduler, cache, search engine, new persistence technology, new external provider, third currency, historical-valuation table, or LLM.
- **No `product/` or `.specify/` edit** (`git status` clean for tracked files there).
- **`compose.yaml`**: one additive, behaviour-neutral change — `backend.environment` now forwards `FINNHUB_API_KEY: ${FINNHUB_API_KEY:-}` and `FINNHUB_BASE_URL: ${FINNHUB_BASE_URL:-https://finnhub.io/api/v1}` from the shell / `.env` (git-ignored) so `./start.sh` can run FD004 against live Finnhub. Blank key ⇒ identical behaviour to before (integration disabled). `.env.example` / `.env` document a blank `FINNHUB_API_KEY` with a real-secret warning. Added 2026-09-04 on the product owner's request, after the gate runs above.

## Deterministic / correctness gates (FD004 §28)

| Gate | Evidence |
|---|---|
| Calculations deterministic; no `double`/`float` for money/rate | `PortfolioValuationCalculator` is pure `BigDecimal`; `PortfolioValuationCalculatorTest` C8 asserts byte-identical repeat runs; ArchUnit `portfolio_domain_uses_no_binary_floating_point_fields` (SC-006) |
| No live Finnhub in CI E2E | `./e2e.sh` runs against the `finnhub-stub` container; EN005's real adapter/mapper/cache/error path executes; no request reaches `finnhub.io` (SC-010) |
| E2E-001 + E2E-002 present and passing | `e2e/tests/fd004-valuation.spec.ts`, `e2e/tests/fd004-provider-failure.spec.ts` — both green |
| Portfolio creation never rolled back by a valuation failure | `PortfolioValuationOnCreationIT.a_market_data_outage_never_rolls_back_or_hides_the_created_portfolio` (SC-005, SC-008); `PortfolioValuationServiceTest` — `PortfolioRepository.save` never invoked; `PortfolioValuationPersistenceAdapterIT.valuation_writes_never_touch_the_portfolio_or_position_tables` |
| Missing price/FX never a fabricated `0` | `PortfolioValuationCalculatorTest` C2/C4/C7; `PortfolioValuationResponseMapperTest.an_unvalued_position_has_null_money_fields_never_zero`; `V4` CHECK `position_valuation_unvalued_has_no_money_chk`; frontend `portfolio-detail.page.spec.ts` + E2E-002 assert no `0.00`/`0%` |

## E2E-001 numbers verified (SC-001, SC-003)

Stub inputs AAPL @ 200 USD / Technology, SAN @ 5 EUR / Financial Services, USD→EUR 0.80, EUR→USD 1.25;
portfolio AAPL ×10 (USD) + SAN ×100 (EUR):

- total EUR **€2,100.00**, total USD **$2,625.00** (asserted exactly in `PortfolioValuationOnCreationIT` via `total_value_eur::text = '2100.00'` and in the E2E)
- AAPL weight **76.19 %** / Technology; SAN weight **23.81 %** / Financial Services; sector allocation Technology 76.19 % + Financial Services 23.81 %

## Key files

- Domain: `portfolio/domain/model/{PortfolioValuation,PositionValuation,SectorAllocation,ValuationStatus,PortfolioValuationCalculator,PositionInput,FxContext,PositionPricing,FxConversion}`, `portfolio/domain/events/PortfolioCreatedEvent`, `portfolio/domain/ports/{MarketDataGateway,PortfolioValuationRepository}`
- Business: `portfolio/business/{ValuePortfolioUseCase,PortfolioValuationQueryUseCase,PortfolioValuationService}`; `CreatePortfolioService` publishes `PortfolioCreatedEvent` (only `!replayed`)
- Infra: `portfolio/infrastructure/marketdata/EnMarketDataGatewayAdapter`, `portfolio/infrastructure/valuation/PortfolioValuationOnCreationListener`, `portfolio/infrastructure/persistence/{PortfolioValuationPersistenceAdapter,entity/*,repository/PortfolioValuationJpaRepository,mapper/PortfolioValuationPersistenceMapper}`, `portfolio/infrastructure/api/rest/{PortfolioValuationController,dto/PortfolioValuationResponse,mapper/PortfolioValuationResponseMapper}`, widened `PortfolioExceptionHandler`
- Migration: `db/migration/V4__portfolio_valuation.sql`
- Config: `application.yml` — `finnhub.base-url` now `${FINNHUB_BASE_URL:https://finnhub.io/api/v1}`
- Frontend: `portfolio/{portfolio-valuation.service.ts,valuation-format.ts}`, extended `portfolio-detail.page.ts`, `portfolio.models.ts` (+valuation view models)
- E2E: `e2e/finnhub-stub/{server.js,Dockerfile}`, `infrastructure/local/compose.e2e.yaml` (+`finnhub-stub` service + backend `FINNHUB_*`), `e2e.sh` (+stub build/health), `e2e/support/valuation.ts`, `e2e/tests/fd004-{valuation,provider-failure}.spec.ts`
- ArchUnit: `StandardArchitectureRulesTest` +3 rules (18 → 21)
- Docs: `implementation/platform/README.md`, `backend/core-service/README.md`

---

# Revision 2 — Two mandatory allocation pie charts (2026-09-05)

**Trigger**: FD004 Feature Definition update + §31 re-sign (jaruiz) — §17.1–§17.4, BR-014…BR-017,
AC-013…AC-015, §26 checks 11–15, §28, §29.16–§29.22. SDD flow re-run: spec (US7, FR-044…FR-049,
SC-014/SC-015, A12–A14) → plan ("Revision 2" section) → tasks (T069–T087) → implement.

**Scope**: frontend + E2E only. **No backend / domain / business / persistence / migration / API /
contract / npm-dependency change.** Charts consume the existing `PortfolioValuation` response
(`positions[].portfolioWeight`, `sectors[].sectorWeight`) verbatim.

## R2 gate results

| Gate | Result |
|---|---|
| `ng test` (Node 20.19.1, ChromeHeadless) | **TOTAL: 81 SUCCESS** — 71 (FD004 v1) → 81: **+7** `pie-chart.component.spec.ts`, **+3** `portfolio-detail.page.spec.ts` chart cases |
| `./e2e.sh` | **8 passed** — incl. `fd004-valuation.spec.ts` (E2E-001) now asserting both pie charts visible with `AAPL`/`Technology` `76.19 %` and `SAN`/`Financial Services` `23.81 %` legend slices, consistent with the detail's numbers (§26 checks 11–15); `fd004-provider-failure.spec.ts` + FD001/FD002/FD003 + `platform-smoke` green; offline |
| Backend | untouched — `./mvnw -q -o compile` OK; no `.java` in the R2 diff |
| Checkpoint sequence | R2-A (`./e2e.sh` green after the Frankfurter stub route — also unblocks EN005-R2 C4) → R2-B (`ng test` green, `PieChartComponent`) → R2-C (`ng test` green, detail extension) → R2-D (`./e2e.sh` green with chart assertions) → R2-E (docs + scope) — **each checkpoint green** |

## What shipped (R2)

- `frontend/web/src/app/portfolio/pie-chart.component.ts` (+ `.spec.ts`) — **NEW** standalone
  component: inline SVG arc `<path>` wedges (12 o'clock, clockwise; `largeArcFlag` per rule;
  single ~100 % slice → `<circle>`), an HTML legend (`label` + backend `%` via the shared
  `percent()` helper), `role="img"` + computed `aria-label`, `--chart-1…8` slice colours.
  **No financial calculation** — it normalises the given fractions for geometry only (BR-016).
- `portfolio.models.ts` — `AllocationSlice { label; fraction }` view-model.
- `styles/_tokens.scss` — `--chart-1 … --chart-8` dark categorical palette.
- `portfolio-detail.page.ts` (+ `.spec.ts`) — `tickerSlices()` (valued Positions, `portfolioWeight`,
  desc), `sectorSlices()` (`sectorWeight`, desc), `showCharts()` (valued snapshot + positive EUR
  basis); renders a responsive 2-up `.charts` grid (stacks on narrow) with the two `<app-pie-chart>`;
  **neither chart** for `FAILED` / `PENDING` / no-EUR-basis / absent valuation (SC-015).
- `e2e/finnhub-stub/server.js` — `+ GET /v1/latest?base=&symbols=` (Frankfurter shape, deterministic
  `USD→EUR 0.80` / `EUR→USD 1.25`) — prerequisite after EN005-R2 moved FX to Frankfurter (A14).
- `compose.e2e.yaml` — `+ FRANKFURTER_BASE_URL: http://finnhub-stub:8080` on the e2e backend.
- `e2e/support/valuation.ts` — `tickerChart` / `sectorChart` / `chartLegend` helpers.
- `e2e/tests/fd004-valuation.spec.ts` — chart assertions.
- `implementation/platform/README.md` + `backend/core-service/README.md` — note the two charts.

## R2 scope review (SC-012)

`git diff` for Revision 2 touches **only**: `frontend/web/src/app/portfolio/{pie-chart.component.ts,
pie-chart.component.spec.ts,portfolio-detail.page.ts,portfolio-detail.page.spec.ts,portfolio.models.ts}`,
`frontend/web/src/styles/_tokens.scss`, `e2e/finnhub-stub/server.js`, `e2e/support/valuation.ts`,
`e2e/tests/fd004-valuation.spec.ts`, `infrastructure/local/compose.e2e.yaml`, the two READMEs, and
the `specs/FD004-…` docs.

- **`package.json` / `package-lock.json` unchanged** — no new frontend dependency (FR-041, FR-049).
- **`openapi.yaml` / `pom.xml` / all backend `.java` unchanged** by R2 (the `openapi.yaml` +159 and
  `pom.xml` +10 in the working tree are pre-existing FD004-v1 / EN005-R2 changes).
- **No migration.** **No `product/` or `.specify/` edit** by this work — the FD004 §17/§29 update is
  the product owner's (`product/definition/features/FD004-…` is the owner's untracked file; §31
  re-signed).

## R2 AC / SC evidence

| Item | Proven by |
|---|---|
| AC-013 ticker pie chart (slice/ticker, EUR weight) | `pie-chart.component.spec.ts` + `portfolio-detail.page.spec.ts` "renders both …" + `fd004-valuation.spec.ts` |
| AC-014 sector pie chart (`Unclassified` slice) | same + `PieChartComponent` legend test |
| AC-015 / SC-014 chart %s == backend weights, sum ≈ 100 % | `portfolio-detail.page.spec.ts` asserts legend % == sector-list % ; `fd004-valuation.spec.ts` asserts both charts vs the detail numbers |
| FR-048 / SC-015 charts suppressed / partial | `portfolio-detail.page.spec.ts` "draws NEITHER chart …" + "renders the charts for a PARTIAL …" |
| §26 checks 11–15 | `fd004-valuation.spec.ts` |
| §28 closure gate (both charts present + consistent) | `./e2e.sh` green |
| A14 E2E stubs Frankfurter FX | `e2e/finnhub-stub/server.js` `/v1/latest`; `./e2e.sh` reaches `COMPLETED` |

**Next**: `/project-verify FD004-portfolio-valuation-and-allocation` (re-close with the charts).

---

## Revision 2.1 — Portfolio-detail table trim (2026-09-04)

Owner request within FR-029's "MAY be extended" latitude — a display refinement, not a scope change:

1. **"Market value" column removed** — `quantity × price` is redundant with `Value (EUR)` /
   `Value (USD)`.
2. **"Market price" now shows the native currency** — `200.00 USD` / `160.00 EUR`.
3. **Standalone "Sector allocation" list removed** — the *Allocation by Sector* chart legend already
   carries every sector + its % (FR-028).

### Gate results

| Gate | Command | Result |
|---|---|---|
| Frontend unit / component | `npm test` (Node 20.19.1, ChromeHeadless) | **81 passed** |
| Containerized E2E | `./e2e.sh` | **8 passed** (incl. `fd004-valuation.spec.ts` with the currency + no-`.sectors` + no-`Market value`-header assertions) |
| Backend | untouched — no `.java` / `pom.xml` / `openapi.yaml` change | n/a |

### Scope review (SC-012)

`git diff` for Revision 2.1 touches **only**: `frontend/web/src/app/portfolio/{portfolio-detail.page.ts,
portfolio-detail.page.spec.ts}`, `e2e/support/valuation.ts`, `e2e/tests/fd004-valuation.spec.ts`,
the two READMEs, and the `specs/FD004-…` docs (`spec.md`, `plan.md`, `tasks.md`, this file).

- **`package.json` / `package-lock.json` unchanged** — no dependency change.
- **`openapi.yaml` / `pom.xml` / all backend `.java` unchanged** — the `PortfolioValuation` response
  still carries `nativeMarketValue`; the frontend simply stops rendering it.
- **No migration. No `product/` or `.specify/` edit.**

### AC / SC evidence

| Item | Proven by |
|---|---|
| FR-028 sector % via the Allocation by Sector chart legend, no standalone list | `portfolio-detail.page.spec.ts` (`.sectors` element asserted **absent**; both chart legends carry `76.19%` / `23.81%`) + `fd004-valuation.spec.ts` (`.sectors` count 0) |
| FR-029 `Market Value` column dropped | `portfolio-detail.page.spec.ts` (header list `not.toContain('Market value')`) + `fd004-valuation.spec.ts` (no `Market value` `<th>`) |
| FR-029 `Market Price` shows native currency | `portfolio-detail.page.spec.ts` (`200.00 USD` / `160.00 EUR` in the rows) + `fd004-valuation.spec.ts` (`200.00 USD` / `5.00 EUR`) |
| SC-014 chart %s still consistent, sum ≈ 100 % | `portfolio-detail.page.spec.ts` (ticker legend %s === sector legend %s) + `fd004-valuation.spec.ts` |
| §28 closure gate (both charts present + consistent) | `./e2e.sh` green |
