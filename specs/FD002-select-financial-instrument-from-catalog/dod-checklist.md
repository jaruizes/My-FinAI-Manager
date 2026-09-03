# FD002 — Definition of Done Evaluation

Evaluated 2026-09-03 against `product/engineering/definition-of-done.md`. Change type:
**Product feature — frontend interaction change + one server-side validation rule + additive API code.**

## 1. Product and Specification

| Item | Status | Evidence |
|---|---|---|
| Traceable to an approved work item | PASS | FD002 §19 signed by jaruiz 2026-09-03 |
| Formal spec approved / checklist | PASS | `spec.md` + `checklists/requirements.md` 16/16; Clarifications 2026-09-03 |
| Behavior within approved scope | PASS | replaces free-text t/m/c with catalog selection + backend guarantee; nothing beyond FD002 §3 |
| No new business requirement invented | PASS | only human-approved `product/` edits (§18/§19 sync, AR-062) |
| Acceptance scenarios implemented | PASS | AC-001…AC-009 mapped in `quickstart.md`; US1–US4 tasks complete |
| No speculative out-of-scope behavior | PASS | scope review in `pr-evidence.md` (git diff) |

## 2. Architecture

| Item | Status | Evidence |
|---|---|---|
| Complies with `architecture.md` / `architecture-rules.md` | PASS | ADR-003 layout; new port in `portfolio.domain.ports`, adapter in `portfolio.infrastructure.catalog` |
| Technology policy | PASS | **no new dependency** (`pom.xml` / `package.json` untouched) |
| Module ownership / no cross-module persistence access | PASS | `portfolio` reads `financialinstrument` only via its published `domain.ports` port (AR-062), from `infrastructure` only |
| Domain free of other-module / framework types | PASS | `portfolio.domain` / `portfolio.business` reference no `financialinstrument` type; ArchUnit `portfolio_core_is_free_of_financialinstrument` green |
| Architecture conformance test | PASS | `StandardArchitectureRulesTest` **14/14** + T030 deliberate-violation check (fails the right rule → revert → green) |
| Significant architectural change has an approved decision | PASS | AR-062 raised (OD-FD002-4) and human-approved before being applied; no new ADR needed (no topology/tech change) |

## 3. Code Quality / Testing

| Item | Status | Evidence |
|---|---|---|
| TDD for the new deterministic rule | PASS | `CreatePortfolioServiceTest` catalog cases (fake port) drive the `INSTRUMENT_NOT_IN_CATALOG` behavior |
| Unit tests pass | PASS | Surefire **115**, 0F/0E |
| Integration tests (persistence) | PASS | Failsafe **56**, 0F/0E — `CatalogInstrumentCatalogAdapterIT`, `FinancialInstrumentCatalogAdapterIT` (Testcontainers) |
| ≥ 90 % coverage gate (line + branch) | PASS | JaCoCo `check` green — **line 96.28 % · branch 91.01 %** |
| Coverage exclusions justified | PASS | no new exclusion; the thin catalog adapter is covered by its IT |
| Contract test | PASS | `CreatePortfolioControllerContractTest.an_instrument_not_in_catalog_400_body_conforms_to_the_contract` |
| Frontend unit tests | PASS | `ng test` → **40 SUCCESS** (search service, reworked dialog incl. a11y + states, draft list) |
| Meaningful assertions | PASS | tests assert behaviour (rejection + nothing persisted, states, keyboard select), not just execution |

## 4. API / Contract

| Item | Status | Evidence |
|---|---|---|
| OpenAPI updated for the API change | PASS | `openapi.yaml` +`INSTRUMENT_NOT_IN_CATALOG` enum value + description + `notInCatalog` example |
| Business language, no persistence/provider leakage | PASS | `CatalogListing` / response = business fields only; contract test validates against `openapi.yaml`; no provider field exists to leak |
| Stable machine-readable errors (RFC 9457) | PASS | `/problems/portfolio-validation` + `errors[].code` unchanged mechanism |
| Breaking changes explicit/approved | PASS | additive enum value — non-breaking (FD001 clients treat unknown codes as generic) |
| No second search endpoint | PASS | `GET /api/financial-instruments` reused as-is (FR-019) |

## 5. Persistence

| Item | Status | Evidence |
|---|---|---|
| Schema changes via approved migration | N/A | **no schema change** — reads existing `financial_instrument` / `market` via ports; no `V4` |
| Data ownership explicit | PASS | `financialinstrument` owns the catalog; `portfolio` reads it through the published port only (AR-006/AR-062) |
| Transaction boundaries intentional | PASS | catalog read is `@Transactional(readOnly=true)` in `financialinstrument`; portfolio write tx unchanged; a `DataAccessException` propagates to the 503 path, not a validation `false` |
| No dual write | PASS | PostgreSQL only; portfolio write path unchanged |

## 6. Security / Secrets / Hygiene

| Item | Status | Evidence |
|---|---|---|
| No secrets | PASS | no new config; frontend never calls an external provider (E2E asserts every request stays on the frontend origin) |
| Frontend decoupled (AR-013) | PASS | backend is authoritative for validation; the FE constrains, the BE re-checks |
| Build output not committed | PASS | no `target/` / `dist/` / `node_modules` |
| Unrelated files | PASS | scope review — only `portfolio` / `financialinstrument` / `openapi.yaml` / frontend `portfolio` area / e2e / 2 READMEs / 2 approved `product/` edits |

## 7. Observability

| Item | Status | Evidence |
|---|---|---|
| Logging unchanged | PASS | `PortfolioCreated` / `PositionAdded` events unchanged; no new log surface |

## 8. Resilience

| Item | Status | Evidence |
|---|---|---|
| Catalog unavailable | PASS | a transient catalog read failure propagates to the existing `PortfolioNotSavedException` (503) path — never silently "not selectable" |
| Listing inactivated mid-flight | PASS | backend re-check rejects it; nothing persisted |

## 9. Documentation

| Item | Status | Evidence |
|---|---|---|
| Relevant docs updated | PASS | `backend/core-service/README.md` (AR-062 seam), `implementation/platform/README.md` (FD002 capability), `pr-evidence.md`, this checklist, `quickstart.md` |
| No broken references | PASS | READMEs cite `StandardArchitectureRulesTest`, AR-062, `INSTRUMENT_NOT_IN_CATALOG` — all exist |

## 10. Platform Lifecycle

| Item | Status | Evidence |
|---|---|---|
| `start.sh` / `stop.sh` / `e2e.sh` valid | PASS | `./e2e.sh` exercised 2026-09-03 → 4 passed, exit 0; none of the three scripts changed |
| Feature extends the existing platform | PASS | same `core-service`, same frontend app, same OpenAPI file; no isolated app |

## 11. Product Acceptance / mandatory E2E

| Item | Status | Evidence |
|---|---|---|
| Mandatory browser E2E present + green | PASS | `e2e/tests/FD002-select-instrument.spec.ts` — Create Portfolio → Add Position → search + select → controlled t/m/c → Save → confirmation; passes (FR-028 / SC-010). FD002 is not closable without it. |
| FD001 journey not regressed | PASS | `FD001-create-portfolio.spec.ts` (Add Position step adapted, assertions unchanged) + all FD001 backend suites green (SC-006) |

## Not applicable

CI/CD (no pipeline — local `./mvnw verify` + `ng test` + `./e2e.sh`); External integrations (file-
based catalog, no live provider); AI/LLM; Kafka/async.

## Overall

**READY FOR HUMAN CLOSURE REVIEW** — no FAIL findings; all applicable mandatory items PASS; the
mandatory FD002 E2E closure gate is green and FD001 is intact.
