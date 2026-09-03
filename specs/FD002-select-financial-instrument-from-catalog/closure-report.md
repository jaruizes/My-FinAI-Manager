# FD002 — Select Financial Instrument from Catalog · Closure Report

**Verified:** 2026-09-03 · **Work item:** Feature Definition FD002 (Status: Approved — §19 signed by jaruiz 2026-09-03)
**Authoritative source:** `product/definition/features/FD002-select-financial-instrument-from-catalog/FD002-select-financial-instrument-from-catalog.md`

## Final Result

**READY TO CLOSE WITH WARNINGS**

## Summary

FD002 replaces the free-text Ticker / Market / Currency inputs in the FD001 **Add Position**
interaction with controlled catalog selection (search + select, EN004's
`GET /api/financial-instruments`), and adds a **server-side guarantee** that a Position can only
reference an active EUR/USD catalogued listing whose `ticker + market + currency` all match —
rejected on `POST /api/portfolios` with `ValidationProblem` code `INSTRUMENT_NOT_IN_CATALOG`.

`./mvnw -B clean verify` is green — **Surefire 115 + Failsafe 56, 0 failures**, JaCoCo bundle
**line 96.28 % · branch 91.01 %** (≥ 90 % both), ArchUnit **14/14**. `ng build` (production) and
`ng test` (**40 SUCCESS**) green. **`./e2e.sh` → 4 passed** including the mandatory FD002 closure
journey. Runtime verified against the containerized platform: search works; a catalogued position
→ `201`; a wrong-market or wrong-currency position → `400 INSTRUMENT_NOT_IN_CATALOG` with nothing
persisted; the FD001 `DUPLICATE_INSTRUMENT` rule still fires and the catalog check adds no spurious
violation for a valid combination; `./stop.sh` clean + idempotent.

No FAIL findings. No scope expansion, no unapproved material decision, no new dependency, no schema
migration, no new deployable, no committed secret. The warnings are repository-hygiene, a
pre-existing cosmetic string, and an optional narrative-doc update.

## Scope Compliance

| Aspect (FD002 §3) | Status | Evidence |
|---|---|---|
| Replace free-text Ticker / Market / Currency with controlled selection | PASS | `add-position.dialog.ts` — ARIA combobox search + select; **no** `input[formControlName="ticker\|market\|currency"]` (E2E + dialog spec assert count 0) |
| Search by ticker or instrument name, case-insensitive | PASS | `instrument-search.service` → `GET /api/financial-instruments`; runtime `?query=AAPL` and `?query=santander` |
| EUR / USD initial currencies | PASS | selection is bounded by EN004's catalog (EUR/USD only); backend rejects a mismatched currency |
| Local catalog is the runtime source; frontend never calls an external provider | PASS | FE calls only `GET /api/financial-instruments`; E2E asserts **every** request stays on the frontend origin (SC-007) |
| Provider-neutral representation | PASS | `CatalogListing` / response = `id,name,ticker,market,currency,active,isin`; no provider field exists |
| Integrate into the existing FD001 Add Position flow (no parallel path) | PASS | same dialog component, same `POST /api/portfolios`, same page |
| FD001 `ticker + market` Position identity unchanged | PASS | `Portfolio.create` untouched; `DUPLICATE_INSTRUMENT` still keyed on `ticker+market` (runtime check) |
| Mandatory browser E2E | PASS | `e2e/tests/FD002-select-instrument.spec.ts` green |
| **Scope expansion?** | PASS | changes confined to `portfolio` / `financialinstrument` / `openapi.yaml` / frontend `portfolio` area / `e2e` / 2 READMEs / 2 human-approved `product/` edits. No schema migration, no new dependency, no new deployable / messaging / scheduler / search engine, no lifecycle-script change. |

## Requirement Coverage

### FD002 Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC-001 Select a known instrument → controlled ticker/market/currency | PASS | `add-position.dialog.spec.ts` (USD + EUR single-listing); E2E selects "Apple" → `AAPL/XNAS/USD` |
| AC-002 Search by instrument name | PASS | dialog spec `santander`; E2E "Apple"; runtime `?query=santander` |
| AC-003 Search by ticker | PASS | dialog spec `aapl` (lower case); E2E `san` |
| AC-004 Controlled Market (no arbitrary text) | PASS | dialog spec — Market/Currency selector offers only the instrument's real listings; `CatalogInstrumentCatalogAdapterIT` wrong-market → not selectable; runtime `AAPL/XMAD` → `400` |
| AC-005 Controlled Currency (EUR/USD only) | PASS | derived from the listing; `CreatePortfolioServiceTest` + `CatalogInstrumentCatalogAdapterIT` currency-mismatch → rejected; runtime `AAPL/XNAS/EUR` → `400` |
| AC-006 Invalid combination cannot be created | PASS | frontend cannot assemble it (constrained selector); backend rejects `INSTRUMENT_NOT_IN_CATALOG`, nothing persisted (`CreatePortfolioServiceTest` asserts `save` never called; runtime confirms) |
| AC-007 Inactive instrument not selectable | PASS | EN004 `search` excludes inactive; `CatalogInstrumentCatalogAdapterIT` inactive `SC·XNAS` → not selectable |
| AC-008 No search results → informed, text not accepted | PASS | dialog spec + E2E `FD002: a search that matches nothing …` ("No matching instrument found.", Add button disabled) |
| AC-009 Existing FD001 flow continues unchanged | PASS | all FD001 backend suites green (catalog seeded in `AbstractPortfolioIT`); `FD001-create-portfolio.spec.ts` green (Add Position step adapted, assertions unchanged); runtime `DUPLICATE_INSTRUMENT` still fires |
| §14 / §17.11 mandatory E2E closure gate | PASS | `./e2e.sh` → 4 passed, exit 0 |

### FD002 Business Rules

| BR | Status | Evidence |
|---|---|---|
| BR-001 Controlled Instrument Selection | PASS | dialog cannot confirm without a selected instrument; FR-009 |
| BR-002 Controlled Market Selection (ISO 10383 MIC) | PASS | market = the listing's MIC |
| BR-003 Supported Currencies EUR/USD | PASS | catalog + backend enforce |
| BR-004 Valid Instrument Listing (`ticker+market+currency` a real listing) | PASS | backend `INSTRUMENT_NOT_IN_CATALOG` (currency-aware — analyze A1) |
| BR-005 Catalog entry is the source of selection | PASS | ticker/market/currency filled from the chosen listing; no free-text override |
| BR-006 Active instruments only | PASS | AC-007 evidence |
| BR-007 Catalog source independence | PASS | provider-neutral; FR-021 |
| BR-008 Local catalog is the runtime source | PASS | FE → `/api` only; E2E network assertion |
| BR-009 Existing Position identity remains `ticker + market` | PASS | `Portfolio.create` unchanged; catalog check is additional |

### Spec Success Criteria

| SC | Status | Evidence |
|---|---|---|
| SC-001 0 free-text t/m/c inputs | PASS | dialog spec + E2E helper (count 0) |
| SC-002 case-insensitive search + exact fill | PASS | dialog spec; `CatalogInstrumentCatalogAdapterIT` (`aapl` / `AAPL`) |
| SC-003 0 non-catalogued Positions creatable | PASS | `CreatePortfolioServiceTest` (nothing persisted), contract test, runtime |
| SC-004 inactive / non-EUR-USD never offered | PASS | `CatalogInstrumentCatalogAdapterIT`; a non-EUR/USD listing cannot be persisted (`SupportedCurrency`) |
| SC-005 clear no-results, no proceed path | PASS | dialog spec + E2E |
| SC-006 100 % FD001 scenarios still pass | PASS | Failsafe 56 + FD001 E2E green |
| SC-007 0 external-provider calls from the FE | PASS | E2E asserts all requests on the frontend origin |
| SC-008 0 provider/persistence fields in payloads | PASS | contract test validates the `400` against `openapi.yaml`; `CatalogListing` business-only |
| SC-009 `./mvnw verify` + `ng test` green, gates hold | PASS | 115 + 56, coverage 96.28 %/91.01 %, ArchUnit 14/14, `ng test` 40 |
| SC-010 mandatory E2E passes | PASS | `./e2e.sh` 4 passed |
| SC-011 scope: no meaning change / new tech / product edit | PASS | `git status` review — see Scope Compliance |
| SC-012 add a Position for a known ticker < 30 s | PASS | E2E adds a Position (search → select → quantity → confirm) in ~1.4 s |

## Architecture

| Check | Status | Evidence |
|---|---|---|
| ADR-001 — one `core-service` deployable | PASS | no new service; `compose.yaml` unchanged |
| ADR-003 — module layout | PASS | new port in `portfolio.domain.ports`; adapter in `portfolio.infrastructure.catalog`; `financialinstrument` change is one method on an existing domain port |
| Dependency direction `infrastructure → business → domain` | PASS | `StandardArchitectureRulesTest` (dir rules) green |
| **AR-062** — inter-module reads via a published port | PASS | `portfolio → financialinstrument` only via `financialinstrument.domain.ports.FinancialInstrumentCatalog`, only from `portfolio.infrastructure.catalog.CatalogInstrumentCatalogAdapter`; `portfolio.domain`/`business` reference no `financialinstrument` type |
| ArchUnit enforcement | PASS | 2 new rules (`portfolio_touches_financialinstrument_only_via_its_domain_ports`, `portfolio_core_is_free_of_financialinstrument`) → **14 rules**, all green |
| ArchUnit non-vacuous | PASS | T030 — deliberate `portfolio.domain.ports → financialinstrument.domain.model` reference fails `portfolio_core_is_free_of_financialinstrument` → reverted → 14/14 |
| Business logic out of controllers/adapters | PASS | the rule lives in `CreatePortfolioService`; the adapter only delegates + compares currency |
| AR-013 — frontend decoupled, backend authoritative | PASS | FE constrains; BE re-validates every Position |

## Technology Policy

| Technology | Policy | Used | Result |
|---|---|---|---|
| Spring Boot / Java 21, Angular 20 | ALLOWED / PREFERRED | Yes (no version change) | PASS |
| Maven (`./mvnw`) | REQUIRED | Yes | PASS |
| Spring Data JPA | REQUIRED by default | Yes (existing) | PASS |
| PostgreSQL 16 | PREFERRED | Yes (read-only, via ports) | PASS |
| Testcontainers | REQUIRED for applicable ITs | Yes (`CatalogInstrumentCatalogAdapterIT`, `FinancialInstrumentCatalogAdapterIT`) | PASS |
| OpenAPI 3.0.3 | REQUIRED for REST | Yes (additive enum value) | PASS |
| Playwright | PREFERRED (EN002) | Yes (new spec) | PASS |
| PostgreSQL search (no dedicated engine) | PREFERRED initial option | Yes (reused EN004) | PASS |
| **New dependency** | — | **None** | PASS — `pom.xml` / `package.json` / `package-lock.json` untouched |

## Tests

| Suite | Command | Result |
|---|---|---|
| Backend unit (Surefire) | `./mvnw -B clean verify` | **115 run, 0 failed** |
| Backend integration (Failsafe, Testcontainers) | same | **56 run, 0 failed** |
| New backend tests | — | `CreatePortfolioServiceTest` +5 catalog cases (fake port; incl. currency-mismatch A1); `CatalogInstrumentCatalogAdapterIT` (Testcontainers, 3); `FinancialInstrumentCatalogAdapterIT` +2 `findSelectable`; `CreatePortfolioControllerContractTest` +1 `INSTRUMENT_NOT_IN_CATALOG` (contract-validated) |
| Coverage | JaCoCo `check` | **PASS** — line 96.28 %, branch 91.01 % |
| Architecture | `StandardArchitectureRulesTest` | **14/14** + non-vacuous check |
| Frontend unit | `ng test` (Node 20.19, ChromeHeadless) | **40 SUCCESS** — `instrument-search.service.spec` (4), `add-position.dialog.spec` (13 incl. a11y + 6 states + constrained selector), `position-draft-list` |
| Frontend build | `ng build --configuration production` | clean |
| E2E | `./e2e.sh` (Chromium, containerized) | **4 passed**, exit 0 — FD001 (adapted) + smoke + FD002 journey + FD002 no-results |

Testing-strategy compliance: the new deterministic rule is covered by a fake-port unit test and a
Testcontainers adapter IT; the contract change has a contract test; the mandatory browser E2E is
present and green. No manually-installed infrastructure.

## Build

`./mvnw -B clean verify` → **BUILD SUCCESS**. `ng build --configuration production` → clean bundle.
No Gradle, no Java/Angular major-version change.

## Runtime Verification

`./start.sh` (containerized) →

| Step | Result |
|---|---|
| `/actuator/health` | `UP` (db `UP`) |
| `GET /api/financial-instruments?query=AAPL` / `?query=santander` | correct provider-neutral listings |
| `POST /api/portfolios` — `AAPL / XNAS / USD` (catalogued) | `201` |
| `POST /api/portfolios` — `AAPL / XMAD / USD` (wrong market) | `400` `INSTRUMENT_NOT_IN_CATALOG` on `positions[0]`, `type` `/problems/portfolio-validation`, nothing persisted |
| `POST /api/portfolios` — `AAPL / XNAS / EUR` (wrong currency — A1) | `400` `INSTRUMENT_NOT_IN_CATALOG` |
| `POST /api/portfolios` — duplicate `AAPL / XNAS / USD` ×2 | `400` `['DUPLICATE_INSTRUMENT']` only (catalog check adds no spurious violation for a valid combo) |
| `./stop.sh` ×2 | clean, idempotent |

Platform left **stopped**.

## API and Contract Verification

| Item | Status | Evidence |
|---|---|---|
| OpenAPI updated for the change | PASS | `openapi.yaml` `ValidationProblem.errors[].code` enum += `INSTRUMENT_NOT_IN_CATALOG` + description + `notInCatalog` example; `type` unchanged |
| Implementation matches the contract | PASS | `CreatePortfolioControllerContractTest.an_instrument_not_in_catalog_400_body_conforms_to_the_contract` + runtime body matches the example shape |
| Contract test exists | PASS | as above |
| Business terminology, no persistence leakage | PASS | `errors[].code` token; no entity/provider field |
| Compatibility | PASS | additive enum value — non-breaking (unknown codes fall back to a generic error) |
| No second search endpoint | PASS | `GET /api/financial-instruments` reused verbatim |

## Persistence Verification

| Item | Status | Evidence |
|---|---|---|
| Schema changes via approved migration | N/A | **no schema change** — reads `financial_instrument` / `market` via ports; no `V4` |
| Ownership explicit; no unauthorized cross-module DB access | PASS | `financialinstrument` owns the tables; `portfolio` reads only through the published port (AR-006 / AR-062); ArchUnit-enforced |
| Integration tests use real disposable infra | PASS | Testcontainers PostgreSQL 16 |
| No unsafe dual write | PASS | PostgreSQL only; the catalog read is a separate read-only transaction; portfolio write path unchanged |

## Security and Repository Hygiene

| Check | Status | Evidence |
|---|---|---|
| No secrets committed | PASS | no `.env` / key / token in the change set |
| Frontend contacts no external provider | PASS | E2E network assertion |
| Build output not committed | PASS | no `target/` / `dist/` / `node_modules` |
| Implementation in approved locations | PASS | everything under `implementation/platform/…`; no rogue root dir |
| Unrelated files | WARNING (W3) | stray `product/definition/features/FD003-list-and-view-portfolio-details.md/` directory (a `.md`-suffixed folder containing the FD003 stub) — not created by FD002 |
| Repository committed state | WARNING (W1) | FD002 (and FD001, EN002–EN004) implementation is **uncommitted**; FD002 cannot be reviewed as an isolated commit. Scope was verified by `git status` inspection instead. |

## Documentation

| Item | Status | Evidence |
|---|---|---|
| Backend README updated | PASS | `backend/core-service/README.md` — the `portfolio → financialinstrument` seam / AR-062 / `INSTRUMENT_NOT_IN_CATALOG` |
| Platform README updated | PASS | `implementation/platform/README.md` — FD002 capability row + the interaction/validation change |
| PR evidence | PASS | `specs/FD002-…/pr-evidence.md` |
| DoD checklist | PASS | `specs/FD002-…/dod-checklist.md` |
| Quickstart + verification run | PASS | `specs/FD002-…/quickstart.md` §"Verification run — 2026-09-03" |
| `architecture.md` narrative | WARNING (W4) | `architecture.md` does not mention the inter-module read pattern that `architecture-rules.md` AR-062 now codifies. Optional; `architecture-rules.md` is authoritative for rules and the DoD does not require the narrative update for this feature. |

## Definition of Done

| Item | Applicable | Status | Evidence |
|---|---|---|---|
| Traceable to an approved work item | Yes | PASS | FD002 §19 signed 2026-09-03 |
| Spec approved / checklist | Yes | PASS | `spec.md` + `checklists/requirements.md` 16/16 |
| Behavior within approved scope | Yes | PASS | Scope Compliance table |
| No invented requirement | Yes | PASS | only human-approved `product/` edits (§18/§19, AR-062) |
| Architecture rules / ADR-003 | Yes | PASS | ArchUnit 14/14 + non-vacuous |
| Technology policy | Yes | PASS | no new dependency |
| Module ownership / no cross-module DB access | Yes | PASS | AR-062 seam, ArchUnit-enforced |
| TDD for the new deterministic rule | Yes | PASS | `CreatePortfolioServiceTest` fake-port cases |
| Unit tests pass | Yes | PASS | Surefire 115 |
| Integration tests (persistence) | Yes | PASS | Failsafe 56, Testcontainers |
| ≥ 90 % coverage (line + branch) | Yes | PASS | 96.28 % / 91.01 % |
| Architecture conformance test | Yes | PASS | `StandardArchitectureRulesTest` + T030 |
| Contract test | Yes | PASS | `an_instrument_not_in_catalog_400_body_conforms_to_the_contract` |
| OpenAPI updated for API change | Yes | PASS | additive enum value + example |
| Business language, no persistence/provider leakage | Yes | PASS | contract test + `CatalogListing` |
| RFC 9457 problem responses | Yes | PASS | `/problems/portfolio-validation` mechanism unchanged |
| Schema migration mechanism | No | N/A | no schema change |
| Constraints for correctness | Yes | PASS | existing `(ticker, market_mic)` uniqueness / `active` used by the check |
| Transaction boundaries intentional | Yes | PASS | read-only catalog read; `DataAccessException` → existing 503 path, not a validation `false` |
| No dual write | Yes | PASS | PostgreSQL only |
| Structured logging, no secrets | Yes | PASS | `PortfolioCreated` / `PositionAdded` unchanged; no new log surface |
| Resilience (catalog unavailable / listing inactivated mid-flight) | Yes | PASS | propagates to 503; backend re-check rejects a now-inactive listing |
| Frontend follows the design system | Yes | PASS | dialog/combobox per `add-position-ui-contract.md`; a11y test (label, listbox, `aria-activedescendant`, keyboard) |
| Documentation updated | Yes | PASS | 2 READMEs + evidence artifacts |
| Repository hygiene | Yes | WARNING | W1 (uncommitted), W3 (stray FD003 dir) |
| Platform lifecycle valid | Yes | PASS | `./e2e.sh` + `./start.sh` / `./stop.sh` exercised; none changed |
| Product acceptance / mandatory E2E | Yes | PASS | `FD002-select-instrument.spec.ts` green — closure gate satisfied |
| FD001 not regressed | Yes | PASS | Failsafe 56 + FD001 E2E green + runtime `DUPLICATE_INSTRUMENT` |
| CI/CD | No | N/A | no pipeline — local `./mvnw verify` + `ng test` + `./e2e.sh` |
| External integrations | No | N/A | file-based catalog (EN004), no live provider |
| AI/LLM, Kafka/async | No | N/A | none |

## Task Completion Cross-Check

`tasks.md` — **34/34 `[X]`**. Spot-checked against the repo:

- T001 `ValidationCode.INSTRUMENT_NOT_IN_CATALOG` ✅ present.
- T002 `openapi.yaml` enum + `notInCatalog` example ✅ present; contract test validates it.
- T004 `instrument-search.service.ts` (+spec) ✅ present, 4 tests.
- T005/T006 `FinancialInstrumentCatalog.findSelectable` + adapter + 2 ITs ✅ present, green.
- T008/T019/T023 reworked `add-position.dialog.ts` ✅ combobox + selector + 6 states.
- T016/T017/T018 `InstrumentCatalog` port + `CatalogInstrumentCatalogAdapter` + service change ✅ present, runtime-verified.
- T020 ArchUnit +2 rules ✅ 14 rules green.
- T025 `AbstractPortfolioIT` / `CreatePortfolioMultiPositionIT` catalog seeding ✅ present; FD001 ITs green.
- T026/T027 `FD002-select-instrument.spec.ts` + helper ✅ present, `./e2e.sh` 4 passed.
- T029–T034 READMEs, deliberate-violation check, `pr-evidence.md`, `dod-checklist.md`, quickstart run, coverage ✅ present.

No false completion detected.

## Detect Unapproved Decisions

None. Every material decision (OD-FD002-1…5) is recorded in `plan.md` / `research.md` and was
confirmed by jaruiz 2026-09-03. The A1 remediation (currency participates in the check) tightens
the rule toward the Feature Definition's BR-004 and was applied through `/speckit.analyze` with the
user's "yes". The two `product/` edits (§18/§19 sync, `architecture-rules.md` AR-062) were raised
(OD-FD002-4) and human-approved before being applied (FR-030 satisfied).

## Findings

### FAILURES

None.

### WARNINGS

**W1 — FD002 implementation is uncommitted.**
FD002 (and the still-uncommitted FD001 / EN002–EN004 implementation) sits as working-tree changes.
FD002 cannot be committed or reviewed as an isolated change set. *Why it matters:* scope, diff, and
revertability. Scope was nonetheless verified by `git status` inspection — the change set is confined
to `portfolio` / `financialinstrument` / `openapi.yaml` / frontend `portfolio` area / `e2e` / two
READMEs / two human-approved `product/` edits. *Remediation:* commit the prior work items, then
commit FD002 on its own.

**W2 — Pre-existing cosmetic grammar in the `400` `detail`.**
`PortfolioExceptionHandler` renders `"The portfolio has 1 problem that need to be fixed."` — "need"
is not singularised for N = 1. This is **FD001** code, not introduced by FD002, and `detail` is
non-normative free text (the machine-readable `code`/`field` are correct). *Remediation (optional):*
`n == 1 ? " that needs " : " that need "`.

**W3 — Stray `FD003` work-item directory.**
`product/definition/features/FD003-list-and-view-portfolio-details.md/` is an untracked directory
whose name carries a `.md` suffix and which contains the file
`FD003-list-and-view-portfolio-details.md`. Not created by FD002. *Remediation:* rename the folder
to `FD003-list-and-view-portfolio-details/` (drop the `.md` on the directory) or organise it to
match the `product/definition/features/<id>/<id>.md` convention.

**W4 — `architecture.md` narrative does not mention the AR-062 pattern.**
`architecture-rules.md` gained AR-062 (inter-module reads via a published port) and it is the
authoritative rules document; `architecture.md` (the narrative) has no corresponding sentence.
Non-blocking — the DoD does not require the narrative update for this feature. *Remediation
(optional, human-governed):* add a line to `architecture.md` §Modular Monolith referencing AR-062.

**W5 (informational) — the FR-007 multi-listing selector is exercised only by a synthetic test.**
EN004's current curated catalog has **no** economic instrument with more than one supported listing,
so the "constrained Market/Currency selector" branch is covered only by a synthetic frontend unit
test (grouping by normalized name — OD-FD002-2, human-confirmed). No real-data coverage is possible
until EN004 loads a dual listing. No action required.

## Required Remediation

None blocking. Before the human closes FD002:

1. **W1** — commit the prior work items and FD002 as its own change set.
2. **W3** — fix the stray `FD003` directory name.
3. **W2 / W4** — optional cosmetic / narrative fixes.

## Final Decision

**READY TO CLOSE WITH WARNINGS.** All FD002 Acceptance Criteria, Business Rules, and spec Success
Criteria pass with concrete test / contract / runtime evidence; the mandatory browser E2E closure
gate is green; FD001 is not regressed; architecture rules (incl. the new AR-062 boundary) are
machine-enforced and non-vacuous; coverage and the build gates hold; no unapproved decision, no
scope expansion, no committed secret. The four warnings are repository-hygiene and optional
cosmetic items that do not affect the correctness or completeness of FD002. Final closure remains a
human decision.
