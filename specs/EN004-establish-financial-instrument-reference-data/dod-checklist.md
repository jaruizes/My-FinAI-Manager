# EN004 — Definition of Done Evaluation

Evaluated 2026-09-03 against `product/engineering/definition-of-done.md`. Change type:
**New functional module + schema migration + one new read-only API operation.**

## 1. Product and Specification

| Item | Status | Evidence |
|---|---|---|
| Traceable to an approved work item | PASS | EN004 enabler (Approved 2026-09-03); ADR-001 + ADR-003 (Approved) |
| Formal specification approved when required | PASS | `specs/EN004-…/spec.md` + `checklists/requirements.md` 16/16; `## Clarifications` 2026-09-03 |
| All behavior within approved scope | PASS | catalog + mapping-driven import + one search operation — exactly EN004 §§6–24; out-of-scope items (no markets endpoint, no live provider, no type filtering) respected |
| No new business requirement introduced | PASS | `product/` change limited to the pre-approved 2026-09-03 enabler-header sync; no `product/` intent changed |
| Acceptance scenarios implemented | PASS | US1–US5 gates green; `quickstart.md` A–G exercised |
| No speculative out-of-scope behavior | PASS | scope review (quickstart §G / VC-019); no new deployable / messaging / scheduler / search engine |

## 2. Architecture

| Item | Status | Evidence |
|---|---|---|
| Complies with `architecture.md` / `architecture-rules.md` | PASS | ADR-003 module layout; `StandardArchitectureRulesTest` (12 rules) green |
| Technology choices comply with `technology-policy.md` | PASS | Spring Data JPA (default), Flyway, PostgreSQL 16, Testcontainers; only new dependency `commons-csv` (leaf RFC 4180 utility) |
| Standard architecture boundaries respected | PASS | `infrastructure → business → domain` for the new module; rules are module-wildcarded |
| Domain logic does not depend on infrastructure | PASS | `domain_has_no_framework_dependencies` (now also bans `org.apache.commons..`) green; `YahooSymbolNormalizer` is plain Java |
| Module ownership clear; no cross-module persistence access | PASS | `financialinstrument` owns `market` + `financial_instrument`; no access to `portfolio`/`position`/`investor` (`grep` clean) |
| No unapproved technology introduced | PASS | `pom.xml` diff = +`commons-csv` only; no framework/DB/broker/cloud addition |
| Significant architectural change has an approved ADR | PASS | extends ADR-001 + ADR-003; **no new ADR** required (justified in `pr-evidence.md`) |
| Architecture conformance non-vacuous | PASS | T055 — deliberate `domain→infra` + `commons-csv`-in-business violations fail 2/12 rules → reverted → green |

## 3. Code Quality / Testing

| Item | Status | Evidence |
|---|---|---|
| TDD for new deterministic logic | PASS | `YahooSymbolNormalizerTest` (one case per `RejectionReason` + worked examples), `ReferenceDomainModelTest`, `NormalizationDataTest`, `SearchFinancialInstrumentsServiceTest` written RED-first |
| Unit / domain tests pass | PASS | Surefire **107**, 0F/0E |
| Integration tests (persistence involved) | PASS | Failsafe **51**, 0F/0E — Testcontainers PostgreSQL: schema, round-trip, catalog search, importer counters, upsert idempotency, failure-safety |
| ≥ 90 % coverage gate (line + branch, bundle) | PASS | JaCoCo `check` green — **line 96.25 % · branch 91.37 %** |
| Architecture conformance test | PASS | `StandardArchitectureRulesTest` (12) green + T055 deliberate-violation check |
| Contract test | PASS | `FinancialInstrumentSearchContractTest` (3) green vs `openapi.yaml` 3.0.3 (200 / 200-empty / 400 problem+json; no provider fields) |
| No generic heuristic where a deterministic rule is required | PASS | normalization is mapping-table-only; `SUFFIX_MISMATCH` rather than a guess; `SupportedCurrency` is an enum |

## 4. API / Contract

| Item | Status | Evidence |
|---|---|---|
| OpenAPI updated for API changes | PASS | `openapi.yaml` (+`GET /api/financial-instruments`, +`FinancialInstrument`/`FinancialInstrumentList`, reuses `Problem`); contract test validates request **and** response |
| Business language, no persistence/provider leakage in contract | PASS | response = `id,name,ticker,market,currency,active,isin`; **no** `providerSymbol`/source-exchange/`operatingMic`/`instrumentType`/`source*` (mapper + contract-test assertions; VC-010) |
| RFC 9457 problem responses | PASS | `400` `application/problem+json` `type:/problems/invalid-search-query` via `ProblemDetail` + `@RestControllerAdvice` |
| Compatibility changes intentional | PASS | additive only — new path + new schemas; `Problem` reused unchanged |

## 5. Persistence

| Item | Status | Evidence |
|---|---|---|
| Schema changes via approved migration mechanism | PASS | forward Flyway `V3__financial_instrument.sql`; `V1`/`V2` untouched; `ReferenceDataSchemaIntegrityIT` asserts V3 in history + constraints + columns |
| Hibernate does not own the schema | PASS | `spring.jpa.hibernate.ddl-auto: none`; portfolio `SchemaIntegrityIT` (sibling-tolerant) asserts no `hibernate_*` artifacts |
| Data ownership explicit | PASS | new module owns the two new tables; FK `financial_instrument.market_mic → market.mic` within the module |
| Integration tests use real disposable infra | PASS | Testcontainers singleton PostgreSQL 16 (`PostgresContainerSupport`) |
| No unsafe dual writes | PASS | single `TransactionTemplate` per import run; PostgreSQL only |
| Import is idempotent + fail-safe | PASS | `ReferenceDataUpsertAdapterIT` (2nd run `imported=0`, stable `ListingId`, omission ≠ delist); `ReferenceDataFailureSafetyIT` (corrupt/missing source → rollback, prior catalog intact) |

## 6. Security / Secrets / Hygiene

| Item | Status | Evidence |
|---|---|---|
| No secrets committed | PASS | `app.reference-data.*` values are `classpath:` locations only; reference CSVs carry public exchange/instrument data; synthetic test fixtures |
| No credential/token in import diagnostics | PASS | `ReferenceDataBootstrapRunner` logs counters + source id + `Rejection` raw values only |
| Generated build output not committed | PASS | `target/` git-ignored |
| New dependency intentional | PASS | `commons-csv` 1.12.0 — RFC 4180 parsing, confined to `infrastructure.reference.csv` (ArchUnit rule `csv_parsing_is_confined_to_infrastructure`) |

## 7. Observability

| Item | Status | Evidence |
|---|---|---|
| Structured logging for the new flow | PASS | `event=ReferenceDataImportCompleted` with all 7 counters; `event=ReferenceDataImportFailed` on failure; per-row `ReferenceDataRowRejected` at DEBUG |
| No observability infrastructure added | PASS | uses the existing ECS JSON logging; no new exporter/collector |

## 8. Documentation

| Item | Status | Evidence |
|---|---|---|
| Relevant documentation updated | PASS | `backend/core-service/README.md` (+`financialinstrument` module, corrected `api/rest/mapper` tree); `implementation/platform/README.md` (+catalog-search capability, +reference-data note); `pr-evidence.md`; `quickstart.md` VC table |
| No broken references | PASS | READMEs cite ADR-003, `StandardArchitectureRulesTest`, `V3__financial_instrument.sql`, `quickstart.md` — all exist |

## 9. Platform Lifecycle

| Item | Status | Evidence |
|---|---|---|
| `start.sh` / `stop.sh` / `e2e.sh` remain valid | PASS | 2026-09-03 — `BUILD=1 ./start.sh` healthy (import log + curl checks), restart idempotent, `./stop.sh` clean, `./e2e.sh` → 2 passed exit 0 |
| No alternative undocumented startup path | PASS | import runs inside the existing backend container via `ApplicationRunner`; no new entry point or script |
| Feature extends the existing platform | PASS | one `core-service`, one Compose stack, one OpenAPI file |

## Findings

- **W1 (non-blocking)** — `portfolio/infrastructure/persistence/SchemaIntegrityIT` was relaxed from
  `containsExactly("1","2")` to `contains("1","2")` + `noneMatch(startsWith("hibernate_"))` so it
  tolerates sibling migrations. This is a correct test adaptation for a multi-module schema, not a
  weakening — it still fully asserts Hibernate created/altered nothing.
- **W2 (non-blocking)** — the repository has substantial uncommitted prior work (EN001/EN002/EN003/
  FD001), so the literal `git diff` scope check in T053 could not be run against a clean baseline;
  scope was instead verified by path inspection and `grep` (documented in `pr-evidence.md`).

## Overall

**READY FOR HUMAN CLOSURE REVIEW** — no FAIL findings; all applicable mandatory items PASS;
`./mvnw verify` (158 tests), the containerized runtime checks, and `./e2e.sh` are all green.
