# EN004 — Establish Financial Instrument Reference Data · Closure Report

**Verified:** 2026-09-03 · **Work item:** Technical Enabler EN004 (Status: Approved 2026-09-03)
**Authoritative source:** `product/definition/enablers/EN004-establish-financial-instrument-reference-data/EN004-establish-financial-instrument-reference-data.md`

## Final Result

**READY TO CLOSE WITH WARNINGS**

## Summary

EN004 adds a new `com.myfinaimanager.core.financialinstrument` module to the single `core-service`
deployable (ADR-001 intact), built to ADR-003 (`domain` / `business` / `infrastructure`, Spring Data
JPA, Maven, ArchUnit). It delivers a local PostgreSQL Market + Financial Instrument catalog
(`V3__financial_instrument.sql`), a mapping-driven Yahoo→canonical normalization pipeline (no generic
suffix stripping), an idempotent fail-safe import that runs on backend start, and one provider-neutral
search operation `GET /api/financial-instruments`.

`./mvnw clean verify` is green — **Surefire 107 + Failsafe 51, 0 failures / 0 errors**, JaCoCo bundle
**line 96.25 % · branch 91.37 %** (≥ 90 % gate met). The containerized platform starts with a
populated catalog (27 processed / 21 imported / 1+2 skipped / 1+2 quarantined), the search endpoint
behaves per contract (ticker, name, blank→400 problem+json, no-match→`200 []`, no provider fields),
`./stop.sh` is clean and idempotent, and `./e2e.sh` → 2 passed (no FD001 regression).

No FAIL findings. No unapproved material decision. No scope expansion. No secrets. The warnings are
process / repository-hygiene items, none blocking.

## Scope Compliance

| Aspect | Status | Evidence |
|---|---|---|
| New functional module inside existing `core-service` (no new service) | PASS | `src/main/java/com/myfinaimanager/core/financialinstrument/` — ADR-003 layout; ADR-001 unchanged; no second deployable in `compose.yaml` |
| Local Market + Financial Instrument catalog in PostgreSQL | PASS | `V3` `market` + `financial_instrument`; runtime `select count(*)` → 14 markets, 21 instruments |
| EUR/USD initial currency scope | PASS | `SupportedCurrency` enum + `fin_instr_currency_chk CHECK (currency IN ('EUR','USD'))` |
| Mapping-driven normalization, no generic dot-strip | PASS | `YahooSymbolNormalizer` (mapping tables only); `SC-004` — no "strip after last period" rule in code; `YahooSymbolNormalizerTest` one case per `RejectionReason` |
| Provider independence in domain + contract | PASS | `domain` has zero framework/CSV imports; OpenAPI `FinancialInstrument` schema = business fields only; ArchUnit `csv_parsing_is_confined_to_infrastructure` |
| One FD002-facing endpoint only (search); no `/api/markets` | PASS | `openapi.yaml` adds exactly `GET /api/financial-instruments`; no markets operation |
| Deterministic offline fixtures | PASS | committed `markets.csv`, `instruments.sample.csv` + mapping CSVs; ITs are Testcontainers-only, no Internet |
| No Portfolio/Position semantics change | PASS | no edit under `…/core/portfolio/` production code; `V1`/`V2` untouched; `./e2e.sh` FD001 journey green |
| No new deployable / messaging / scheduler / search engine / persistence tech | PASS | import is a flag-guarded `ApplicationRunner`; search is a Spring Data `@Query` on PostgreSQL |
| No silent edit of human-governed `product/` docs | PASS | tracked `product/*.md` diffs (architecture.md, technology-policy.md, …) predate EN004 (EN002/EN003 work); the only EN004 `product/` change is the 2026-09-03 enabler §34 approval sync + "Reference-data provisioning decisions" subsection |
| New library `commons-csv` | WARNING | recorded in planning (OD-EN004-15 / research.md D12), ArchUnit-confined, JDK fallback noted — but absent from `technology-policy.md`. See W1. |

## Requirement Coverage (Enabler Verification Criteria)

| VC | Status | Evidence |
|---|---|---|
| VC-001 Local Market Catalog in PostgreSQL | PASS | `V3` `market` table; `ReferenceDataSchemaIntegrityIT`, `ReferenceDataPersistenceRoundTripIT`; runtime 14 rows |
| VC-002 ISO 10383 MIC | PASS | `Mic` value object + `market_mic_shape_chk`; `MarketEntity` `CHAR(4)`; normalizer resolves MIC from mapping (`SAN.MC/MCE → XMAD`) |
| VC-003 Local Instrument Catalog in PostgreSQL | PASS | `V3` `financial_instrument`; runtime 21 rows; round-trip IT |
| VC-004 EUR/USD | PASS | `SupportedCurrency` + CHECK; `YahooCsvInstrumentSourceIT` counters (GBP `LSE` row → `skippedUnsupportedCurrency`) |
| VC-005 Normalized `ticker + market` identity | PASS | `InstrumentIdentity` + deterministic `ListingId`; `fin_instr_identity_uk UNIQUE (ticker, market_mic)` |
| VC-006 Local runtime search, no external provider | PASS | `FinancialInstrumentCatalogAdapter` → `FinancialInstrumentJpaRepository` `@Query`; no adapter on the request path; runtime `curl` returns from local DB |
| VC-007 Search by ticker | PASS | `curl ?query=AAPL` → Apple; `search("aapl")==search("AAPL")` IT |
| VC-008 Search by name | PASS | `curl ?query=iberdrola` → `IBE`; `search_by_name_is_a_case_insensitive_substring` IT |
| VC-009 Valid ticker + Market + Currency in results | PASS | every response row carries `ticker`,`market`(MIC),`currency`; contract test + `curl` |
| VC-010 No provider payload types in domain / contracts | PASS | `domain` import scan clean; `FinancialInstrumentResponse` = 7 business fields; contract test asserts absence of `providerSymbol`/`source`/`instrumentType`/`externalReference`; ArchUnit |
| VC-011 Repeatable import, no duplicates | PASS | `ReferenceDataUpsertAdapterIT` (2nd run `imported=0`, `updated=8`, `ListingId` stable); runtime 2nd boot log `imported=0 updated=21` |
| VC-012 Failure safety | PASS | `ReferenceDataFailureSafetyIT` — corrupt CSV → `ReferenceDataImportException`, row count unchanged; missing file → hard failure, zero rows written |
| VC-013 Spring Data JPA, domain JPA-free | PASS | `*JpaRepository` + adapters; `domain_does_not_use_spring_data_or_jpa` ArchUnit green; domain import scan clean |
| VC-014 Flyway | PASS | `V3__financial_instrument.sql`; `ReferenceDataSchemaIntegrityIT` asserts V3 in history + `ddl-auto: none` |
| VC-015 Testcontainers | PASS | all 6 EN004 ITs extend `PostgresContainerSupport` (singleton PostgreSQL 16) |
| VC-016 Deterministic offline fixtures | PASS | committed CSVs; identical counters on every run (`processed=27 imported/updated skip 1/2 quarantine 1/2`) |
| VC-017 ArchUnit | PASS | `StandardArchitectureRulesTest` 12 rules green; module-wildcarded (`..core.(*)..`); T055 deliberate-violation check fails 2 rules → revert → green (pr-evidence, quickstart §C) |
| VC-018 Containerized runtime | PASS | `BUILD=1 ./start.sh` healthy; import runs inside the backend container against the existing PostgreSQL container; `./stop.sh` clean + idempotent |
| VC-019 No Portfolio behavior change | PASS | no `portfolio` production-code change; FD001 identity intact; `./e2e.sh` FD001 spec green |
| VC-020 FD002 ready | PASS | `GET /api/financial-instruments` contract + contract test + runtime; deterministic offline catalog present; FD002's own E2E is FD002-owned (out of EN004 scope) |

## Architecture

| Check | Status | Evidence |
|---|---|---|
| ADR-001 — one coarse-grained `core-service` | PASS | no new deployable; `compose.yaml` still postgres + backend + frontend |
| ADR-003 — module-first `domain/business/infrastructure` | PASS | module tree matches enabler §5 / ADR-003 §"Modular Monolith Structure" |
| Dependency direction `infrastructure → business → domain` | PASS | `domain_does_not_depend_on_business`, `domain_does_not_depend_on_infrastructure`, `business_does_not_depend_on_infrastructure` — all green |
| Domain framework-free | PASS | `domain_has_no_framework_dependencies` (incl. `org.apache.commons..`) green; manual import scan clean |
| Adapter placement | PASS | `@Entity` under `infrastructure.persistence.entity`; repos under `…persistence.repository`; `@RestController` under `infrastructure.api.rest`; mappers under `infrastructure.api.rest.mapper` (ADR-003 2026-09-02 amendment) |
| CSV confined to infrastructure | PASS | `csv_parsing_is_confined_to_infrastructure` green; `commons-csv` only in `infrastructure.reference.csv` |
| Rules non-vacuous | PASS | `financialinstrument` has classes in all three areas; T055 deliberate-violation check |
| `portfolio` architecture test still green | PASS | `StandardArchitectureRulesTest` covers both modules, 12/12 |

## Technology Policy

| Technology | Policy | Used | Result |
|---|---|---|---|
| Spring Boot / Java 21 | ALLOWED | Yes (no version bump) | PASS |
| Maven (`./mvnw`) | REQUIRED | Yes | PASS |
| Spring Data JPA / Hibernate | REQUIRED by default | Yes | PASS |
| PostgreSQL 16 | PREFERRED | Yes | PASS |
| Flyway | PREFERRED | Yes (`V3`) | PASS |
| Testcontainers | REQUIRED for applicable ITs | Yes (all 6 EN004 ITs) | PASS |
| OpenAPI 3.0.3 | REQUIRED for REST | Yes (+1 operation) | PASS |
| PostgreSQL full-text/`ILIKE` search (no engine) | PREFERRED initial option | Yes (`@Query`) | PASS |
| Dedicated search engine (OpenSearch/ES) | ADR REQUIRED | No | PASS |
| Scheduler / messaging / cache | CONDITIONAL | No | PASS |
| `org.apache.commons:commons-csv` 1.12.0 | *(not in matrix)* | Yes — `infrastructure.reference.csv` only | **WARNING (W1)** — recorded in planning (OD-EN004-15), ArchUnit-confined, reversible; `technology-policy.md` has no entry for a CSV parsing utility |

## Tests

| Suite | Command | Result |
|---|---|---|
| Unit / domain / normalization / contract (Surefire) | `./mvnw clean verify` | **107 run, 0 failed, 0 skipped** |
| Integration (Failsafe, Testcontainers PostgreSQL) | same run | **51 run, 0 failed, 0 skipped** |
| EN004 ITs | — | `ReferenceDataSchemaIntegrityIT` (3), `ReferenceDataPersistenceRoundTripIT` (4), `FinancialInstrumentCatalogAdapterIT` (5), `YahooCsvInstrumentSourceIT` (3), `ReferenceDataUpsertAdapterIT` (3), `ReferenceDataFailureSafetyIT` (2) — all green |
| Contract | `FinancialInstrumentSearchContractTest` | 3 green — 200 array / 200 `[]` / 400 problem+json conform to `openapi.yaml`; no provider fields |
| Architecture | `StandardArchitectureRulesTest` | 12 green + deliberate-violation check (fails 2 → revert → green) |
| Coverage | JaCoCo `check` | **PASS** — line 96.25 %, branch 91.37 % (bundle ≥ 90 % line **and** branch). `YahooSymbolNormalizer` branch 95 %. |
| E2E (regression) | `./e2e.sh` | **2 passed (Chromium), exit 0** — FD001 journey + platform smoke unaffected |

Coverage exclusions: `financialinstrument/infrastructure/config/**` + `…/persistence/entity/**` — mirror the `portfolio` exclusions (declarative wiring + JPA mapping structures); justified per `testing-strategy.md` "pure configuration wiring".

## Build

`./mvnw -B clean verify` → **BUILD SUCCESS** (31 s). No Gradle. No Java/Spring major-version change.
Backend container image builds from the Maven project (`Dockerfile` runs `./mvnw`) — confirmed by `./start.sh` / `./e2e.sh`.

## Runtime Verification

| Step | Result |
|---|---|
| `./start.sh` | all containers healthy; `/actuator/health` → `UP` (db `UP`) |
| Startup import | `event=ReferenceDataImportCompleted processed=27 imported=0 updated=21 skippedUnsupportedCurrency=1 skippedUnsupportedMarket=2 quarantinedAmbiguous=1 quarantinedInvalid=2` (idempotent — data volume persisted from a prior run) |
| `curl ?query=AAPL` | `[{id,name:"Apple Inc.",ticker:"AAPL",market:"XNAS",currency:"USD",active:true,isin:null}]` — no provider fields |
| `curl ?query=iberdrola` (lower-case partial name) | `IBE · XMAD · EUR` |
| `curl ?query=" "` (blank) | `400 application/problem+json` `type:/problems/invalid-search-query` |
| `curl ?query=ZZZNOPE` | `200 []` |
| DB row counts | 14 markets, 21 instruments |
| `./stop.sh` ×2 | clean, idempotent, no containers left running |
| `./e2e.sh` | 2 passed, exit 0; environment torn down |

Platform left **stopped** after verification.

## Security and Repository Hygiene

| Check | Status | Evidence |
|---|---|---|
| No secrets committed | PASS | reference CSVs contain only public exchange/instrument data; no key/token/password in the diff |
| `.env` not committed | PASS | `implementation/platform/infrastructure/local/.env` is git-ignored; only `.env.example` tracked |
| No credentials in import logs | PASS | `ReferenceDataBootstrapRunner` logs counters + source id + raw `Rejection` values only |
| Build output not committed | PASS | no `target/` / `*.class` / `node_modules` tracked |
| Implementation in approved locations | PASS | everything under `implementation/platform/…`; no rogue `/apps` `/services` `/src` root |
| Unrelated files | WARNING (W3) | stray `temp.md` at repo root (prior-session scratch, not EN004) |
| Repository committed state | WARNING (W2) | the entire tree (EN001–EN004, FD001, FD002 defs) is **uncommitted** — EN004 cannot be reviewed as an isolated commit; T053 `git diff` scope review ran by inspection, not against a clean baseline |

## Documentation

| Item | Status | Evidence |
|---|---|---|
| Backend README updated | PASS | `backend/core-service/README.md` — new `financialinstrument` module section; `api/rest/mapper` tree corrected |
| Platform README updated | PASS | `implementation/platform/README.md` — catalog-search capability + reference-data note |
| PR evidence | PASS | `specs/EN004-.../pr-evidence.md` — full change list, test log, risk-register outcomes |
| DoD checklist | PASS | `specs/EN004-.../dod-checklist.md` |
| Quickstart + VC evidence | PASS | `specs/EN004-.../quickstart.md` §"Verification run — 2026-09-03" |
| Human-governed `product/` docs stale? | PASS (note) | "Market" / "Currency" as first-class information objects are FD002's to introduce (spec §Enabler Nature); EN004 has no `product/` doc-update obligation. `architecture.md`'s conceptual module tree already lists `financialinstrument`. |
| Spec internal consistency | WARNING (W4) | spec US5 Acceptance Scenario 3 still lists **8** counters incl. `failed`; authoritative FR-032 lists **7** and states a hard failure is not a counter. Implementation follows FR-032 (`ImportCounters` = 7 fields). Cosmetic. |

## Definition of Done

| DoD item | Applicable | Status | Evidence |
|---|---|---|---|
| Traceable to an approved work item | Yes | PASS | EN004 enabler Approved 2026-09-03; ADR-001 + ADR-003 |
| Formal spec approved / checklist | Yes | PASS | `spec.md` + `checklists/requirements.md` 16/16; Clarifications 2026-09-03 |
| Behavior within approved scope | Yes | PASS | scope table above; no out-of-scope capability |
| No new business requirement invented | Yes | PASS | enabler is technical; no `product/` intent changed |
| Architecture rules / ADR-003 | Yes | PASS | ArchUnit 12 green + non-vacuous |
| Technology policy | Yes | WARNING | W1 — `commons-csv` not in matrix (recorded in planning, confined, reversible) |
| Module ownership / no cross-module DB access | Yes | PASS | `financialinstrument` owns `market` + `financial_instrument`; `grep` for `portfolio` refs → none |
| TDD for deterministic logic | Yes | PASS | normalizer + domain model + search service tests written RED-first (research.md, tasks.md) |
| Unit tests pass | Yes | PASS | Surefire 107, 0F/0E |
| Integration tests (persistence) | Yes | PASS | Failsafe 51, 0F/0E — Testcontainers |
| ≥ 90 % coverage gate (line + branch) | Yes | PASS | 96.25 % / 91.37 %; `YahooSymbolNormalizer` 95 % branch |
| Coverage exclusions justified | Yes | PASS | config + `@Entity` only, mirrors `portfolio` |
| Architecture conformance test | Yes | PASS | `StandardArchitectureRulesTest` + deliberate-violation check |
| Contract test | Yes | PASS | `FinancialInstrumentSearchContractTest` vs `openapi.yaml` 3.0.3 |
| OpenAPI updated for API change | Yes | PASS | `+GET /api/financial-instruments` + schemas; `Problem` reused |
| Business language, no persistence/provider leakage in contract | Yes | PASS | 7 business fields; contract test asserts absence of provider fields |
| Stable machine-readable errors (RFC 9457) | Yes | PASS | `ProblemDetail` + `@RestControllerAdvice`, `type:/problems/invalid-search-query` |
| Schema changes via approved migration | Yes | PASS | Flyway `V3`; `ReferenceDataSchemaIntegrityIT` |
| Constraints for correctness present | Yes | PASS | PK, FK→`market`, `UNIQUE(ticker,market_mic)`, currency/ISIN/ticker-length/type CHECKs |
| Transaction boundaries intentional | Yes | PASS | one `TransactionTemplate` per import run; `ReferenceDataFailureSafetyIT` |
| No dual-write consistency problem | Yes | PASS | PostgreSQL only |
| Structured logging, no secrets | Yes | PASS | ECS JSON `event=ReferenceDataImport*` with the 7 counters |
| Resilience (fail-safe import) | Yes | PASS | hard failure → rollback + `ReferenceDataImportException`; startup runner logs & swallows so the app still serves |
| Documentation updated | Yes | PASS | 2 READMEs + pr-evidence + dod-checklist + quickstart |
| Repository hygiene | Yes | WARNING | W2 (uncommitted tree), W3 (`temp.md`) |
| Platform lifecycle (`start`/`stop`/`e2e`) valid | Yes | PASS | all three exercised 2026-09-03 |
| Product acceptance / E2E | Yes (as applicable) | PASS | EN004 has no investor journey; FD001 E2E green; FD002 E2E is FD002-owned |
| CI/CD | No | N/A | repository has no CI pipeline; validation is local `./mvnw verify` + scripts |
| External integrations | No | N/A | no live provider — file-based import only |
| AI/LLM | No | N/A | deterministic normalization, no AI |
| Async/Kafka | No | N/A | no messaging |

## Findings

### FAILURES

None.

### WARNINGS

**W1 — `commons-csv` is not in `technology-policy.md`.**
`org.apache.commons:commons-csv` 1.12.0 was added for RFC 4180 parsing. It is a leaf utility
(Apache-2.0, zero transitive deps), used only in `infrastructure.reference.csv.CsvReferenceFileReader`,
fenced by the ArchUnit rule `csv_parsing_is_confined_to_infrastructure`, and a JDK-only fallback is
recorded (research.md D12 / plan.md OD-EN004-15). It was **not** introduced silently — it is in the
approved plan. However `technology-policy.md` (which "an AI agent must not introduce an unapproved
technology silently" and principle 8 "introducing a technology outside this policy requires
architectural review") has no entry for a CSV parsing utility. *Why it matters:* keeps the policy the
single source of truth for the dependency surface. *Rule:* `technology-policy.md` §"Technology
Introduction Process"; CLAUDE.md §9. *Remediation:* the human confirms the dependency is acceptable
as a leaf-library implementation detail, or adds a one-line entry (e.g. "CSV parsing — Apache Commons
CSV — ALLOWED, infrastructure-only") — no ADR needed either way.

**W2 — The entire repository is uncommitted.**
EN001, EN002, EN003, FD001, FD002 definitions **and** EN004 all sit as uncommitted working-tree
changes (~280 files). *Why it matters:* EN004 cannot be reviewed, diffed, or reverted as an isolated
change set; task T053's `git diff --stat` scope review had to be done by path inspection instead of
against a clean baseline (already noted as W2 in `dod-checklist.md`). Scope was nonetheless verified —
no `portfolio` production-code change, no `V1`/`V2` edit, no frontend/compose/lifecycle-script change,
no tracked `product/*.md` change attributable to EN004. *Remediation:* commit the prior enablers
(EN001–EN003, FD001) so EN004 can be committed and reviewed on its own.

**W3 — Stray `temp.md` at repo root.**
An untracked scratch file containing a prior `/speckit.specify` prompt for FD001. Not created by
EN004. *Remediation:* delete before committing.

**W4 — Spec internal inconsistency (counter list).**
`spec.md` US5 Acceptance Scenario 3 lists eight counters including `failed`; the authoritative
`FR-032` lists seven and states a hard failure is aborted/rolled back, not counted. The
implementation correctly follows FR-032 (`ImportCounters` has exactly the 7 fields). *Remediation:*
align US5 AS3 wording with FR-032 (cosmetic; no code change).

**W5 — Orchestration/config branch coverage is low relative to the normalizer.**
`ImportReferenceDataService` (71 % branch), `ReferenceMappingConfiguration` (67 %),
`ReferenceDataPersistenceMapper` (81 %). The overall gate is met (91.37 %) and the safety-critical
`YahooSymbolNormalizer` is at 95 %. `ReferenceDataBootstrapRunner`'s failure-swallowing path is
exercised only via the runtime demo, not a unit/IT. *Why it matters:* defensive branches for
malformed mapping CSVs / a failed startup import have thin automated coverage. *Remediation
(optional):* add a small IT that points the runner/config at a malformed mapping CSV and asserts the
`event=ReferenceDataImportFailed` log + continued startup.

## Required Remediation

None blocking. Before the human closes EN004:

1. **W1** — decide whether `commons-csv` warrants a `technology-policy.md` line (recommended: add a
   one-line ALLOWED / infrastructure-only entry) or is accepted as an implementation detail.
2. **W2 / W3** — commit the prior enablers and remove `temp.md`, then commit EN004 as its own change
   set.
3. **W4** — align `spec.md` US5 AS3 with FR-032 (7 counters).
4. **W5 (optional)** — add a failed-startup-import IT.

## Final Decision

**READY TO CLOSE WITH WARNINGS.** All mandatory requirements (VC-001 … VC-020), architecture rules,
DoD gates, and the test / build / runtime / E2E checks pass. No FAIL findings, no unapproved material
decision, no scope expansion, no committed secret. The four warnings are process, policy-bookkeeping,
and documentation items that do not affect the technical correctness or completeness of EN004. Final
closure remains a human decision.
