# Quickstart — Validate FD004 (Portfolio Valuation & Allocation)

Run guide that proves FD004 end to end. Details live in [plan.md](./plan.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/). No implementation code here.

**Prerequisites**: colima/Docker running (`DOCKER_HOST=unix://${HOME}/.colima/default/docker.sock`,
`TESTCONTAINERS_RYUK_DISABLED=true`); Node 20.19.1 (nvm) + `CHROME_BIN` for `ng test`; repo root =
`implementation/platform/`. FD001/FD002/FD003/EN004/EN005 already implemented + verified.

---

## A. Backend build, tests, coverage, architecture (SC-006, SC-007, SC-009)

```bash
cd implementation/platform/backend/core-service
./mvnw -B clean verify
```

**Expect**: Surefire (unit) + Failsafe (Testcontainers IT) green; JaCoCo bundle gate **≥ 90 % line
AND branch**; `StandardArchitectureRulesTest` **20/20** (18 prior + the 2 FD004 rules —
research D11). New/updated tests all present and green:

- `PortfolioValuationCalculatorTest` — cases C1–C8 of [contracts/valuation-calculation.md](./contracts/valuation-calculation.md)
  (⇒ AC-002…AC-010, FR-005…FR-018, SC-001, SC-003, SC-004).
- `PortfolioValuationServiceTest` — needed-FX-only fetch; per-Position failure → `PARTIAL`;
  **`PortfolioRepository.save` never called** (SC-008); one `upsertLatest`.
- `EnMarketDataGatewayAdapterTest` — every `MarketDataException` subtype → `Optional.empty()`;
  `Sector.UNCLASSIFIED` → empty; happy path maps (FR-026, SC-007).
- `PortfolioValuationResponseMapperTest` — all 4 statuses; unvalued Position + absent totals → `null`,
  never `0` (FR-019).
- `PortfolioValuationControllerContractTest` — `@WebMvcTest` + `swagger-request-validator`:
  `200` COMPLETED, `200` synthetic `PENDING`, `404` `/problems/portfolio-not-found`, `400` non-UUID;
  body validates against `openapi.yaml` (FR-024, FR-025).
- `PortfolioValuationPersistenceAdapterIT` (Testcontainers) — `upsertLatest` inserts graph; a
  **second** `upsertLatest` for the same `portfolio_id` ⇒ **1** `portfolio_valuation` row, children
  replaced, **0 duplicates** (FR-021, SC-002); `NUMERIC` exact round-trip (SC-001).
- `PortfolioValuationOnCreationIT` (Testcontainers, fake `MarketDataGateway`) — create ⇒ snapshot
  row exists; **gateway throws for all Positions ⇒ Portfolio + Position rows byte-unchanged, a
  `FAILED` snapshot written, `POST /api/portfolios` still `201`** (FR-002, SC-005, SC-008); replayed
  create (idempotency key) ⇒ no second valuation.
- FD001/FD002/FD003/EN004/EN005 suites still green (FR-034).

**Contract check**: `git diff implementation/platform/contracts/openapi/openapi.yaml` shows **only**
the additive `getPortfolioValuation` path + `PortfolioValuation` / `PositionValuation` /
`SectorAllocation` schemas. FD003's `Portfolio` schema and `GET /api/portfolios[/{id}]` are
**unchanged** (SC-012).

---

## B. Frontend tests (SC-009)

```bash
cd implementation/platform/frontend/web
nvm use 20.19.1 && CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" npm test
```

**Expect** green, including:
- `portfolio-valuation.service.spec.ts` — maps the body; `404` → `'not-found'`; error → `null`.
- `valuation-format.spec.ts` — 2-dp money/percent; `null` → `—` (FR-031).
- `portfolio-detail.page.spec.ts` (extended) — `COMPLETED` ⇒ totals card (€ + $), extra Position
  columns (`Market price` with native currency; **no** `Market value` column), the sector
  percentages in the Allocation by Sector chart legend (no standalone list), "Valued at …";
  `PARTIAL` ⇒ blank cells for the unvalued row +
  "Partial valuation — market data unavailable for N position(s)"; absent/`FAILED` ⇒ "Valuation
  unavailable", **no `0.00` / `0 %`** anywhere (FR-019, FR-030); no editable control (FR-032).
- FD003 detail/list specs still green.

---

## C. Runtime smoke — local platform (FR-034, FR-041)

```bash
cd implementation/platform && ./start.sh
curl -s localhost:8080/actuator/health           # {"status":"UP"}
# With no key set, EN005 is disabled — startup log contains event=FinnhubIntegrationDisabled.
# For live valuation numbers: put FINNHUB_API_KEY=<real key> in infrastructure/local/.env
# (git-ignored) or `FINNHUB_API_KEY=xxx ./start.sh`; compose.yaml forwards it to the backend.
```

Create a Portfolio (FD001 UI or `POST /api/portfolios`), then:

```bash
curl -s localhost:8080/api/portfolios/<id>/valuation | jq .
```

**Expect** without a key: `200` with `status` = `FAILED` (every `latestPrice` empty ⇒ no Position
valued), all monetary fields `null` (never `0`). With a valid key: `COMPLETED` / `PARTIAL` with real
EUR/USD values. Either way the created Portfolio is untouched and listed by `GET /api/portfolios`
(SC-005). A `PENDING` result is possible only in the sub-second window before the synchronous
listener writes. `curl localhost:8080/api/portfolios/not-a-uuid/valuation` ⇒ `400`;
`curl localhost:8080/api/portfolios/$(uuidgen)/valuation` ⇒ `404` `/problems/portfolio-not-found`.

```bash
./stop.sh && ./stop.sh    # idempotent
```

---

## D. E2E — the two mandatory closure gates (FR-038, FR-039, SC-005, SC-010, SC-013)

```bash
cd implementation/platform && ./e2e.sh
```

`compose.e2e.yaml` starts the `finnhub-stub` service; the backend runs with
`FINNHUB_BASE_URL=http://finnhub-stub:8080`, `FINNHUB_API_KEY=e2e-stub`. Frontend, backend,
calculations, persistence, PostgreSQL are all **real**; only the Finnhub boundary is stubbed; **no
outbound Internet** (SC-010, FD004 §25).

**`fd004-valuation.spec.ts` (E2E-001)** — stub in normal mode returns `AAPL` @ `200` USD /
`Technology`, `SAN.MC` @ `5` EUR / `Financial Services`, `USD→EUR 0.80`, `EUR→USD 1.25`. The test
creates the Portfolio (AAPL `XNAS` USD qty 10; SAN `XMAD` EUR qty 100), opens the FD003 detail, and
asserts:

| Check | Expected |
|---|---|
| Creation | Portfolio persisted, appears in the Home list |
| Totals card | `€2,100.00` and `$2,625.00` |
| AAPL row | price `200.00`, market value `2,000.00`, EUR `1,600.00`, USD `2,000.00`, weight `76.19 %`, sector `Technology` |
| SAN row | `5.00` / `500.00` / `500.00` / `625.00` / `23.81 %` / `Financial Services` |
| Sector allocation | `Technology 76.19 %`, `Financial Services 23.81 %` (Σ `100.00 %` ± rounding tolerance) |
| State line | "Valued at …" |
| Persistence | Portfolio still present after reload |

**`fd004-provider-failure.spec.ts` (E2E-002)** — stub in degraded mode (`429` + empty bodies). The
test creates a one-Position Portfolio and asserts: the Portfolio **is** persisted and in the Home
list; the detail shows a valuation-state message (`PARTIAL` / `FAILED` / "Valuation unavailable" per
the rules); **no** `0.00` / `0 %` in any valuation cell or total (SC-005).

**Also expect**: the existing FD001/FD002/FD003 + `platform-smoke` specs stay green; total spec
count grows by exactly 2; no request reaches `finnhub.io`.

---

## E. Acceptance-criteria coverage map (SC-013)

| FD004 AC / gate | Proven by |
|---|---|
| AC-001 valuation initiated after creation | `PortfolioValuationOnCreationIT`; E2E-001 |
| AC-002 native value = qty × price | Calc C1; E2E-001 (AAPL/SAN rows) |
| AC-003 / AC-004 USD→EUR / EUR→USD | Calc C1 |
| AC-005 / AC-006 totals = Σ | Calc C1; `…PersistenceAdapterIT` (SC-001); E2E-001 |
| AC-007 Position weight from EUR values | Calc C1; E2E-001 |
| AC-008 sector grouping + % | Calc C1; E2E-001 |
| AC-009 missing sector → `Unclassified`, still valued | Calc C6 |
| AC-010 missing price → no fabricated zero, incomplete | Calc C2; frontend spec; E2E-002 |
| AC-011 valuation failure keeps the Portfolio | `PortfolioValuationOnCreationIT` (SC-008); E2E-002 |
| AC-012 completed valuation shown in detail | `portfolio-detail.page.spec.ts`; E2E-001 |
| §26 E2E-001 (mandatory) | `fd004-valuation.spec.ts` |
| §27 E2E-002 (mandatory) | `fd004-provider-failure.spec.ts` |
| §28 closure gates (determinism / no live Finnhub / no rollback / no fabricated zero) | ArchUnit (SC-006/007); `./e2e.sh` offline (SC-010); `…OnCreationIT` (SC-008); Calc C2/C4 + frontend spec (SC-004) |

---

## F. Definition-of-Done quick gate

- [ ] `./mvnw verify` green — unit + Testcontainers IT + contract + ArchUnit 20/20 + coverage ≥ 90 % (SC-009)
- [ ] `ng test` green (SC-009)
- [ ] `./e2e.sh` green incl. E2E-001 + E2E-002, offline (SC-010)
- [ ] `openapi.yaml` diff additive only; FD003 contract unchanged (SC-012)
- [ ] `V4__portfolio_valuation.sql` is the only schema change; no `portfolio`/`position`/EN004 table touched (FR-022, FR-023)
- [ ] No `double`/`float` for money/rate in `portfolio.domain`/`business`; no LLM on the valuation path (SC-006)
- [ ] No Finnhub type in FD004 domain/business or the valuation API (SC-007)
- [ ] No new deployable / broker / scheduler / persistence tech / provider; no `product/` edit (SC-012, FR-041, FR-042)
- [ ] `backend/core-service/README.md` + `implementation/platform/README.md` note the valuation area + endpoint
- [ ] closure-report via `/project-verify FD004-portfolio-valuation-and-allocation`

---

## H. Revision 2 — Allocation pie charts

### H0. E2E prerequisite (A14 — do first)

EN005 Revision 2 moved FX to Frankfurter, so the E2E stub must also serve it:

```bash
# e2e/finnhub-stub/server.js gains:  GET /v1/latest?base=&symbols=  → Frankfurter rates shape
# compose.e2e.yaml backend env gains: FRANKFURTER_BASE_URL: http://finnhub-stub:8080
cd implementation/platform && ./e2e.sh          # must reach a COMPLETED FD004 valuation again
```

### H1. Frontend unit (`ng test`) — SC-014, SC-015

```bash
cd implementation/platform/frontend/web
nvm use 20.19.1 && CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" npm test
```

**Expect** green, incl.:
- `pie-chart.component.spec.ts` — one `<path>` per slice; 1 slice → `<circle>`; `largeArcFlag`
  correct for a > 50 % slice; legend `label` + `xx.xx %`; `role="img"` + `aria-label`; 0 slices →
  empty.
- `portfolio-detail.page.spec.ts` — `COMPLETED` (E2E-001 numbers): **Allocation by Ticker** legend
  `AAPL 76.19%` / `SAN 23.81%`; **Allocation by Sector** legend `Technology 76.19%` /
  `Financial Services 23.81%`; chart %s equal the table weights (SC-014). `PARTIAL` → charts +
  partial message. `FAILED` / `PENDING` / no-EUR-basis / `null` → **no** `app-pie-chart` in the DOM
  (SC-015); no `0 %` slice.

### H2. E2E (`./e2e.sh`) — FD004 §26 checks 11–15, SC-010

`fd004-valuation.spec.ts` additionally asserts: after opening the detail, both pie charts are
visible; the ticker chart has `AAPL` ≈ `76.19 %` and `SAN` ≈ `23.81 %` slices; the sector chart has
`Technology` ≈ `76.19 %` and `Financial Services` ≈ `23.81 %` slices; the chart values match the
detail's deterministic numbers; the Portfolio remains persisted.

### H3. Runtime spot-check (optional, real key)

`./start.sh` (real `FINNHUB_API_KEY` in `.env`), create an all-US portfolio (AAPL + MSFT), open
`/portfolios/:id` → two pie charts render, ticker slices sum to 100 %, sector chart shows
`Technology` 100 % (both US tech). A EUR position added → stays `PARTIAL`, charts show the valued
portion + the partial line.

### H4. Scope review (SC-012)

`git diff` shows: `package.json` **unchanged**; `openapi.yaml` / `pom.xml` / all backend Java
**unchanged**; no migration; changes limited to `frontend/web/src/app/portfolio/*`,
`frontend/web/src/styles/_tokens.scss` (+`--chart-*`), `e2e/finnhub-stub/server.js`,
`compose.e2e.yaml`, `e2e/tests/fd004-valuation.spec.ts`, `e2e/support/valuation.ts`, the two
READMEs; **no `product/` edit** (the FD004 §17/§29 update is the owner's).
