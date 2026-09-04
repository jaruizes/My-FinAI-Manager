# FD004 — Portfolio Valuation & Allocation — Closure Report

**Verified**: 2026-09-04
**Verifier**: `/project-verify` (Claude Code)
**Authoritative source**: `product/definition/features/FD004-portfolio-valuation-and-allocation/FD004-portfolio-valuation-and-allocation.md` — **Status: Approved**, signed `jaruiz`, §31 all boxes checked, §30 open questions resolved 2026-09-04.

---

## Final Result

**READY TO CLOSE WITH WARNINGS**

---

## Summary

FD004 (including its two owner-requested revisions — "two mandatory allocation pie charts" and the
"Portfolio-detail table trim") is implemented, tested, and verified against the approved Feature
Definition. All 16 required checklist items, all mandatory tests (backend, frontend, architecture,
E2E), and runtime verification pass. The closure gate (§28) — deterministic calculations, no live
Finnhub in CI, E2E-001 and E2E-002 present and green, creation-survives-valuation-failure, no
fabricated zeros, both mandatory charts present and consistent — is satisfied. `tasks.md` is 94/95
checked; the one remaining unchecked task (T087) is literally "run `/project-verify`", satisfied by
this report. Warnings below are non-blocking (a stale illustrative field in the FD's own UX table,
and two informational notes) and do not gate closure.

---

## Scope Compliance

| Item | Status | Evidence |
|---|---|---|
| In-scope: automatic post-creation valuation | PASS | `PortfolioCreatedEvent` → `PortfolioValuationOnCreationListener` (sync `@EventListener`, catch-all) |
| In-scope: EN005 ports only (`MarketDataPort`/`InstrumentProfilePort`/`FxRatePort`) via ACL | PASS | `portfolio.domain.ports.MarketDataGateway` + `EnMarketDataGatewayAdapter` — sole importer of `..marketdata..` (AR-062; ArchUnit-enforced) |
| In-scope: EUR/USD totals, weights, sector allocation | PASS | `PortfolioValuationCalculator` (pure `BigDecimal`) |
| In-scope: latest-only persistence | PASS | `V4__portfolio_valuation.sql` (`portfolio_id UNIQUE`), `upsertLatest` = delete-then-insert |
| In-scope: Portfolio detail extension | PASS | `portfolio-detail.page.ts` |
| In-scope: two mandatory pie charts | PASS | `pie-chart.component.ts` — *Allocation by Ticker* + *Allocation by Sector* |
| Out-of-scope: history, P/L, benchmarks, dividends/taxes/fees, stop-loss, risk scoring, AI recommendations/news, rebalancing, auto-trading, intraday/scheduled refresh, non-EUR/USD, LLM sector inference, manual sector edit | PASS — none present | grep for `Kafka`/`@Scheduled`/`revalue` in `portfolio/` → **0 matches**; no history table; no P/L field; sector is the provider string verbatim, never LLM-touched |
| Out-of-scope: manual revalue action (§30.8) | PASS — none present | No revalue endpoint/button exists |

**No scope expansion detected.** The Revision-2.1 table trim (removing the "Market Value" column,
adding currency to "Market Price", dropping the standalone sector list) stays inside FD004 §17's own
"**may** be extended" latitude — it is a display refinement of an already-approved list of optional
columns, not new behavior, and does not touch API, schema, or calculation.

---

## Requirement Coverage

### Business Rules (§22)

| Rule | Status | Evidence |
|---|---|---|
| BR-001 Automatic trigger | PASS | `CreatePortfolioService` publishes `PortfolioCreatedEvent` after save (`!replayed`) |
| BR-002 Creation independence | PASS | Listener is catch-all, best-effort `FAILED` snapshot, never rethrows; `CreatePortfolioAcceptanceIT`, `PortfolioValuationOnCreationIT` |
| BR-003 Deterministic calculation | PASS | `PortfolioValuationCalculator` — pure function, `BigDecimal` only, no `double`/`float` (ArchUnit `portfolio_domain_uses_no_binary_floating_point_fields`) |
| BR-004 EN005 data access via ports | PASS | `MarketDataGateway` ACL; ArchUnit fences `..marketdata..` access to the one adapter |
| BR-005 Dual currency total | PASS | `PortfolioValuationCalculatorTest`; runtime check below (`totalValueEUR`/`totalValueUSD` both present) |
| BR-006 Position market value = qty × price | PASS | `PortfolioValuationCalculatorTest` (AC-002 case) |
| BR-007 Canonical EUR allocation | PASS | `portfolioWeight = valueInEUR / totalValueEUR`, `HALF_UP` scale 12 |
| BR-008 Sector via EN005 | PASS | `EnMarketDataGatewayAdapter.sector()` |
| BR-009 Missing sector → `Unclassified` | PASS | `PortfolioValuationCalculatorTest` |
| BR-010 Missing price ≠ zero | PASS | `PositionValuation.unvalued(...)`; DB CHECK `position_valuation_unvalued_has_no_money_chk` |
| BR-011 Latest valuation only | PASS | `portfolio_valuation.portfolio_id UNIQUE`; `PortfolioValuationPersistenceAdapterIT` |
| BR-012 Read-only valuation | PASS | No mutating endpoint exists for valuation; frontend has 0 edit controls (`portfolio-detail.page.spec.ts` "has no edit / add / remove position control") |
| BR-013 Data freshness | PASS | `calculatedAt`, `marketDataAsOf`, `fxDataAsOf` on the snapshot and in the runtime response (see below) |
| BR-014 Ticker pie chart | PASS | `tickerSlices()` from `portfolioWeight`; `pie-chart.component.spec.ts` |
| BR-015 Sector pie chart | PASS | `sectorSlices()` from `sectorWeight` |
| BR-016 Chart data consistency | PASS | Charts driven only by API weights (`portfolio-detail.page.spec.ts` cross-checks chart-legend % sets are identical between both charts) |
| BR-017 Partial-valuation visualization | PASS | `showCharts()` + partial-state message; "renders the charts for a PARTIAL valuation alongside the partial-state message" spec |

### Acceptance Criteria (§23)

| AC | Status | Evidence |
|---|---|---|
| AC-001 Automatic valuation | PASS | `PortfolioValuationOnCreationIT` |
| AC-002 Position market value (10×230=2300) | PASS | `PortfolioValuationCalculatorTest` |
| AC-003 USD→EUR | PASS | same |
| AC-004 EUR→USD | PASS | same |
| AC-005 Total EUR | PASS | same |
| AC-006 Total USD | PASS | same |
| AC-007 Position weight | PASS | same |
| AC-008 Sector allocation | PASS | same |
| AC-009 Missing sector → Unclassified | PASS | same |
| AC-010 Missing price never fabricated zero | PASS | `PortfolioValuationCalculatorTest`; frontend `portfolio-detail.page.spec.ts` asserts no `0.00`/`0%` |
| AC-011 Valuation failure doesn't delete Portfolio | PASS | `fd004-provider-failure.spec.ts` (E2E-002) green |
| AC-012 Portfolio detail shows totals/valuation/weights/sector | PASS | `portfolio-detail.page.spec.ts`, `fd004-valuation.spec.ts` |
| AC-013 Ticker pie chart | PASS | `pie-chart.component.spec.ts`, `portfolio-detail.page.spec.ts` "renders both …", `fd004-valuation.spec.ts` |
| AC-014 Sector pie chart w/ Unclassified | PASS | same + `pie-chart.component.spec.ts` legend test |
| AC-015 Chart consistency | PASS | `portfolio-detail.page.spec.ts` legend %-set cross-check; `fd004-valuation.spec.ts` |

### §26 E2E-001 (16 checks) / §27 E2E-002

All 16 E2E-001 checks and E2E-002 are exercised by `e2e/tests/fd004-valuation.spec.ts` and
`e2e/tests/fd004-provider-failure.spec.ts` respectively — both **PASS** (see Tests below). The
deterministic fixture (AAPL 10×200 USD Technology, SAN 100×5 EUR Financial Services, FX 0.80/1.25)
matches FD004 §26 exactly; totals (€2,100.00/$2,625.00), per-position values/sectors, weights
(76.19 %/23.81 %), both chart legends, and post-reload persistence are all asserted.

---

## Architecture

| Rule | Status | Evidence |
|---|---|---|
| Hexagonal boundaries (`domain`/`business`/`infrastructure`, deps inward) | PASS | ArchUnit `StandardArchitectureRulesTest` — **22/22 green** |
| `portfolio` domain/business free of `..marketdata..` | PASS | dedicated ArchUnit rule |
| `..marketdata..` reached only from `portfolio.infrastructure.marketdata` (AR-062) | PASS | dedicated ArchUnit rule |
| No `double`/`float` in `portfolio.domain` | PASS | dedicated ArchUnit rule |
| Only provider `client` packages use `RestClient` | PASS | dedicated ArchUnit rule (Finnhub + Frankfurter) |
| Provider-selection wiring stays out of domain/business | PASS | dedicated ArchUnit rule |
| Single `core-service` deployable (ADR-001) | PASS | no new deployable introduced |
| No microservice-per-domain | PASS | valuation lives inside the existing `portfolio` module, not a new service |
| Standard module layout (ADR-003) | PASS | `domain/{model,ports,exceptions,events}`, `business`, `infrastructure/{api,persistence,marketdata,valuation}` |
| Frontend: standalone Angular component, no new dependency | PASS | `pie-chart.component.ts` — inline SVG, `package.json` unchanged |

---

## Technology Policy

| Technology | Policy | Used | Result |
|---|---|---:|---|
| Spring Boot / Java 21 | ALLOWED/PREFERRED | Yes | PASS |
| PostgreSQL + Flyway | PREFERRED | Yes (`V4__portfolio_valuation.sql`) | PASS |
| Spring Data JPA | in use per ADR-003 | Yes | PASS |
| Angular | PREFERRED | Yes | PASS |
| New frontend charting library | not introduced | No — self-contained inline SVG | PASS (avoids an unneeded dependency) |
| Kafka / message broker | CONDITIONAL, not needed here | No | PASS |
| Frankfurter / Finnhub (EN005 adapters) | consumed only through EN005 provider-neutral ports | Yes, via ACL | PASS |

---

## Tests

| Suite | Command | Result |
|---|---|---|
| Backend unit (Surefire) | `./mvnw -o clean verify` | **230 passed, 0 failed, 2 skipped** (Finnhub live-compat smoke tests, intentionally skipped without a real key) |
| Backend integration (Failsafe, Testcontainers PostgreSQL) | same | **71 passed, 0 failed** |
| Architecture (ArchUnit) | same | **22/22 passed** |
| Contract (`swagger-request-validator-mockmvc`, OpenAPI 3.0.3) | same (part of IT suite) | PASS — `PortfolioValuationControllerContractTest` |
| Coverage (JaCoCo bundle gate) | same | **PASS** — line 97.67 % (1679/1719), branch 91.26 % (564/618), both ≥ 90 % gate |
| Frontend unit/component (Karma/Jasmine, ChromeHeadless, Node 20.19.1) | `npm test -- --watch=false` | **81 passed, 0 failed** |
| Containerized E2E (Playwright, disposable stack) | `./e2e.sh` | **8/8 passed**, incl. `fd004-valuation.spec.ts` (E2E-001) and `fd004-provider-failure.spec.ts` (E2E-002), fully offline via `finnhub-stub` (Finnhub `/quote`+`/stock/profile2` and Frankfurter `/v1/latest`) |

No required test failed, was skipped for convenience, or weakened.

---

## Build

`./mvnw -o clean verify` → **BUILD SUCCESS**. `npm test` compiles/type-checks cleanly. Frontend
production bundle was built and served by `./start.sh` (see Runtime below) — confirms no build-time
regression.

---

## Runtime Verification

`./start.sh` (fresh `BUILD=1` rebuild) → all three containers (`postgres`, `backend`, `frontend`)
reported **Healthy**.

- `GET /actuator/health` → `{"status":"UP", db: UP, ...}`
- `GET http://localhost:4200/` → `200`
- `POST /api/portfolios` (MSFT ×5 USD) → `201`, portfolio persisted
- `GET /api/portfolios/{id}/valuation` → **`COMPLETED`**, `totalValueEUR`/`totalValueUSD` both
  present, `marketPrice`/`nativeMarketValue`/`valueInEUR`/`valueInUSD`/`portfolioWeight`/`sector`
  all populated, `marketDataAsOf`/`fxDataAsOf` present — end-to-end proof the real EN005 adapters
  (Finnhub price/profile + Frankfurter FX) work against the live providers when a key is configured.
- Compiled frontend bundle (`main-*.js`) inspected directly: contains `"Market price"` and
  `"Allocation by Sector"`, contains **zero** occurrences of `"Market value"` or `"Sector
  allocation"` — confirms the Revision-2.1 table trim is what's actually deployed, not just what's
  in source.
- `./stop.sh` → all containers stopped/removed cleanly; **second `./stop.sh` is idempotent** (no
  error, "nothing to do").

Platform was left stopped after verification.

---

## Security and Repository Hygiene

| Check | Result |
|---|---|
| `.env` committed | **No** — only `.env.example` is tracked, and it contains no real key (blank `FINNHUB_API_KEY=`, commented `FRANKFURTER_BASE_URL`) |
| `.gitignore` excludes `.env`/`.env.*` | Yes |
| Real API key ever committed (`git log -p -- '*.env'`) | **No** |
| `node_modules/`, backend `target/`, frontend `dist/` committed | **No** (0 matches each) |
| Secrets/tokens in logs | No — Finnhub key sent only as `X-Finnhub-Token` header; structured logs never print it |
| Synthetic test data only | Yes — E2E fixtures (AAPL/SAN/MSFT) and the runtime-verification portfolio created during this report are all synthetic |

---

## Documentation

- `implementation/platform/README.md` and `backend/core-service/README.md` — FD004 paragraphs
  updated for both the pie charts (Revision 2) and the table trim (Revision 2.1); accurate against
  current behavior.
- `specs/FD004-portfolio-valuation-and-allocation/` — `spec.md`, `plan.md`, `tasks.md`,
  `research.md`, `data-model.md`, `quickstart.md`, `pr-evidence.md` all carry "Revision 2" and
  "Revision 2.1" sections tracing every change back to FD004 and to the owner's requests; no
  human-governed document was edited by the agent.
- `checklists/requirements.md` — **16/16 passing**, 0 unchecked.

### WARNINGS

- **W001** — FD004 §17's own illustrative "Position table may be extended with" list (the
  human-governed Feature Definition, not a derived artifact) still lists `Market Value` as an
  optional field. The Revision-2.1 implementation legitimately omits it (the field was always
  "may", never "must"), so this is **not a scope violation**, but the FD's own example table is now
  slightly stale relative to the current UI. Recorded here for the product owner's awareness — the
  agent will not edit `product/` without instruction.
- **W002** — EN005 (Portfolio Valuation & Allocation's sole upstream dependency for market data) is
  itself mid-revision: Revision 2 checkpoints C3 (DB-first instrument profile → `financialinstrument`
  module), C4 (`finnhub-stub`→`market-data-stub` rename), and C5 (ArchUnit/JaCoCo/docs polish) are
  still open. This does **not** block FD004 — the runtime check above proves FD004 already consumes
  EN005's public ports correctly (`COMPLETED` valuation with real Finnhub price + Frankfurter FX) —
  but EN005 itself is not yet ready for its own closure.
- **W003** — The repository, previously reported as fully uncommitted, now shows `git log` commit
  `488bc6f` containing the entire FD004 v1 + Revision 2 + Revision 2.1 tree (the working tree is
  currently clean against `HEAD`). This commit was **not** made by this verification run or any
  prior agent turn in this session (no `git commit`/`git add` was executed). The product owner
  should confirm this commit is intentional/expected before treating it as the closure commit.

---

## Definition of Done

| Item | Applicable | Status | Evidence |
|---|---:|---|---|
| Traceable to approved Feature Definition | Yes | PASS | FD004 §31 Approved, signed 2026-09-04 |
| Formal spec approved (checklist) | Yes | PASS | `checklists/requirements.md` 16/16 |
| All implemented behavior within approved scope | Yes | PASS | Scope Compliance table above |
| No silent new business requirement | Yes | PASS | Revision-2.1 stays inside FD004 §17 "may" latitude |
| Acceptance scenarios implemented | Yes | PASS | AC-001..015 table above |
| Architecture compliance (`architecture.md`, `architecture-rules.md`, ADR-001/002/003) | Yes | PASS | ArchUnit 22/22; module layout |
| Technology policy compliance | Yes | PASS | Technology Policy table |
| Hexagonal boundaries respected | Yes | PASS | ArchUnit fences |
| No module directly reads another's persistence | Yes | PASS | AR-062 via `MarketDataGateway` port |
| TDD for deterministic logic | Yes | PASS | `PortfolioValuationCalculatorTest` RED-first (memory of implementation); `pie-chart.component.spec.ts` RED-first |
| Coverage ≥ 90 % (line + branch) | Yes | PASS | JaCoCo 97.67 % / 91.26 % |
| Integration tests via Testcontainers | Yes | PASS | `PostgresContainerSupport`, `AbstractPortfolioIT` |
| Contract tests | Yes | PASS | `PortfolioValuationControllerContractTest` |
| OpenAPI updated & matches implementation | Yes (new endpoint in v1; unchanged by R2/R2.1) | PASS | `contracts/openapi/openapi.yaml` `/api/portfolios/{portfolioId}/valuation`, no Finnhub leak |
| E2E covers critical journeys | Yes | PASS | E2E-001 + E2E-002 green |
| Security/secrets review | Yes | PASS | Security section above |
| Observability (structured logs) | Yes | PASS | `event=PortfolioCreated/PositionAdded/ProviderCall` structured JSON logs observed at runtime |
| Resilience (provider failure doesn't corrupt state) | Yes | PASS | BR-002, E2E-002 |
| Documentation current | Yes | PASS, with W001 | READMEs + specs updated; FD's own illustrative table slightly stale (non-blocking) |
| Repository hygiene | Yes | PASS | Security/Hygiene section |
| No unapproved technology | Yes | PASS | Technology Policy table |
| Platform lifecycle (`start.sh`/`stop.sh`) intact and idempotent | Yes | PASS | Runtime Verification section |

---

## Task Completion Cross-Check

`tasks.md`: **94/95 checked**. The single unchecked task, **T087** — "`/project-verify
FD004-portfolio-valuation-and-allocation` (re-close with the charts)" — is exactly this
verification run; it is intentionally left for the human/process to check off, per this skill's
rule not to self-mark task completion. No checked task was found to lack implementation evidence
(spot-checked against the requirement matrices above); no implemented behavior was found without a
corresponding checked task.

---

## Detect Unapproved Decisions

None found. Every technical decision traced to either the FD004 plan's confirmed ODs (v1), the
Revision-2 ODs (OD-R2-1..8, self-contained SVG chart, no dependency), or the Revision-2.1 change
(explicitly requested by the product owner, staying inside FD004 §17's "may" latitude). No new
service, database, framework, API, broker, or authentication model was introduced.

---

## Findings

### FAILURES

None.

### WARNINGS

- W001 — FD004 §17's own example Position-table field list still shows `Market Value`; the
  human-governed document was not updated to reflect the owner's own Revision-2.1 request. Product
  owner may want to touch up the FD's illustrative table (optional field is still optional; no
  functional impact).
- W002 — EN005 Revision 2 (FD004's sole upstream dependency) has open checkpoints C3–C5; does not
  block FD004 but is not itself ready for closure.
- W003 — Repository now shows a committed state (`488bc6f`) that this agent did not create; the
  product owner should confirm this was an intentional commit before treating it as final.

## Required Remediation

None required to close FD004. Optional, non-blocking:

1. If desired, the product owner may touch up FD004 §17's illustrative table to drop `Market Value`
   for consistency with the now-implemented UI (W001).
2. Continue/close EN005 Revision 2 (C3–C5) on its own timeline (W002) — informational only.
3. Confirm commit `488bc6f` was intentional (W003).

## Final Decision

**READY TO CLOSE WITH WARNINGS.** No FAIL findings; all mandatory requirements, architecture rules,
technology-policy checks, tests, build, runtime verification, and Definition-of-Done gates pass. The
three warnings above are non-blocking and require no rework before human closure approval.
