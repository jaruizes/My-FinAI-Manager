# EN003 — Align Spring Backend with Standard Architecture · Closure Report

**Verified:** 2026-09-02 (re-run, supersedes the earlier run + addendum)
**Verifier:** `/project-verify` — independent gate
**Work item:** Technical Enabler EN003 (`product/definition/enablers/EN003-align-spring-backend-standard-architecture/EN003-align-spring-backend-standard-architecture.md`, Status **Approved**)
**Governing decision:** ADR-003 — Standard Spring Backend Architecture (Approved; amended 2026-09-02 — REST mapper placement)
**Repo baseline:** working tree on `cd1efc2` (nothing committed yet — see W001)

---

## Final Result

**READY TO CLOSE WITH WARNINGS**

Technically ready for human closure approval. No FAIL findings. All 18 Verification Criteria pass
with executable evidence gathered in this run, including the mandatory FD001 browser-E2E closure
gate (`./e2e.sh` exit 0). Three non-blocking warnings (W001–W003) plus one informational note
(W004).

---

## Summary

EN003 is a behaviour-preserving migration of `implementation/platform/backend/core-service` to
ADR-003. Verified this run:

- **Structure** — the `portfolio` module is module-first `domain/{model,ports,exceptions}` ·
  `business` · `infrastructure/{api/rest/{,dto,mapper}, persistence/{entity,repository,mapper}, config}`;
  the EN001 `com.myfinaimanager.core.platform.*` and `…core.bootstrap` packages are removed.
- **Enforcement** — `StandardArchitectureRulesTest` = **11** ArchUnit rules, green and non-vacuous;
  a deliberately injected `domain → infrastructure` reference and a mapper in the old
  `infrastructure.api.mapper` package each turn the build red; reverting restores green.
- **Persistence** — `JdbcClient` + hand-written SQL replaced by Spring Data JPA
  (`PortfolioPersistenceAdapter` → `PortfolioJpaRepository` → `PortfolioEntity`/`PositionEntity`,
  explicit `PortfolioPersistenceMapper`); the domain model carries no JPA annotations; Flyway owns
  the schema (`spring.jpa.hibernate.ddl-auto: none`); `SchemaIntegrityIT` proves Hibernate altered
  nothing.
- **Behaviour** — `./mvnw -B clean verify` → Surefire **63** + Failsafe **31**, 0 failures / 0
  errors; JaCoCo line **98.0 %** / branch **94.7 %** (gate ≥ 90 % met). Runtime `curl` against the
  containerized platform reproduces 201-create / 200-replay / 400-validation exactly (exact
  decimals `3.250` / `812.50`, `null` optionals, RFC 9457 problem body, no SQL/stack leakage).
  `./e2e.sh` → 2 passed, exit 0. `ng test` → 29 SUCCESS (frontend untouched).
- **Build / lifecycle** — the pinned Maven wrapper resolves **Apache Maven 3.9.11** with no host
  Maven on `PATH`; the backend image builds from the Maven project (`BUILD=1 ./start.sh`);
  `./start.sh` / `./stop.sh` operate the containerized platform.
- **Architecture-doc change** — the 2026-09-02 REST-mapper relocation (mappers →
  `infrastructure.api.rest.mapper`) was directed and approved by the architect (jaruiz); ADR-003
  carries a dated amendment and `architecture-rules.md` (AR-057/AR-058), `architecture.md`, and the
  EN003 enabler §8 were updated to match. No product behaviour, API, schema, or technology change.

---

## Scope Compliance

| Aspect | Status | Evidence |
|---|---|---|
| In-scope work delivered | PASS | package reorg, ports→`domain.ports`, adapters→`infrastructure`, Spring Data JPA persistence + entities + explicit mappers, ArchUnit rewrite (11 rules), Maven wrapper, `Dockerfile` `./mvnw`, test relocation, docs |
| No new Portfolio behaviour / API operation | PASS | `openapi.yaml` diff is 100 % FD001's `POST /api/portfolios` contract (no EN003 fingerprint); `GET /api/portfolios` → 405, `GET /api/portfolios/{id}` → 404 |
| No auth / Kafka / new service / service extraction | PASS | no security deps; no `org.apache.kafka` anywhere; single `core-service` deployable (ADR-001 intact) |
| No new persistence technology | PASS | PostgreSQL + Spring Data JPA (both approved); explicit `spring-boot-starter-jdbc` removed |
| No schema redesign / no `V3` | PASS | `db/migration/` = `V1__baseline.sql`, `V2__portfolio.sql`; `SchemaIntegrityIT` green |
| No Java / Spring major-version change | PASS | `pom.xml`: Java 21, `spring-boot-starter-parent` 3.5.6 — unchanged |
| Human-governed `product/` edits only under explicit architect authorization | PASS | ADR-003 amendment + AR-057/AR-058 + `architecture.md` + enabler §8 — REST-mapper placement only, directed by jaruiz 2026-09-02; no behaviour/scope change |

No missing scope. No unapproved scope expansion.

---

## Requirement Coverage

| VC | Status | Evidence (this run) |
|---|---|---|
| VC-001 — Maven | PASS | `./mvnw -B clean verify` → BUILD SUCCESS; `env -i` run (`command -v mvn` → none) → wrapper resolved **Apache Maven 3.9.11** |
| VC-002 — Functional-module structure | PASS | all business code under `com.myfinaimanager.core.portfolio.*` (module-first, AR-054) |
| VC-003 — `domain`/`business`/`infrastructure` per module | PASS | `portfolio/{domain/{model,ports,exceptions}, business, infrastructure/{api/rest/{,dto,mapper}, persistence/{entity,repository,mapper}, config}}` |
| VC-004 — Domain independence | PASS | `grep` → 0 framework/persistence/transport imports in `portfolio/domain/`; ArchUnit `domain_has_no_framework_dependencies` + `domain_does_not_use_spring_data_or_jpa` green |
| VC-005 — Ports in `domain.ports` | PASS | `PortfolioRepository`, `DefaultInvestorProvider` in `portfolio/domain/ports/`; signatures = `Optional<Portfolio>`, `String`, `InvestorId` only |
| VC-006 — Business → domain only | PASS | `grep` → 0 `infrastructure` imports in `portfolio/business/`; ArchUnit `business_does_not_depend_on_infrastructure` green |
| VC-007 — Adapter placement | PASS | ArchUnit `rest_controllers_live_in_infrastructure_api_rest`, `rest_mappers_live_in_infrastructure_api_rest_mapper`, `jpa_entities_live_in_infrastructure_persistence_entity`, `spring_data_repositories_live_in_infrastructure_persistence_repository`, `rest_dtos_are_not_used_by_domain_or_business` — all green |
| VC-008 — Spring Data JPA | PASS | `pom.xml`: `spring-boot-starter-data-jpa` present, explicit `spring-boot-starter-jdbc` removed; `PortfolioJpaRepository extends JpaRepository`; `grep -rE "JdbcClient\|JdbcTemplate\|\.sql\(" src/main/java` → **0 hits** |
| VC-009 — No JPA domain leakage | PASS | `@Entity` / `jakarta.persistence` only under `infrastructure/persistence/entity/`; domain types are plain Java; explicit `PortfolioPersistenceMapper` both directions |
| VC-010 — Persistence semantics preserved | PASS | `PortfolioPersistenceAdapterIT` (5) green — exact aggregate round-trip (`3.250` keeps scale), duplicate-instrument write rolls back → 0+0 rows, repeated idempotency key → 1 row/same id, detached read has no `LazyInitializationException`; runtime `curl` 201 → 200-replay |
| VC-011 — Flyway preserved | PASS | `application.yml` `spring.jpa.hibernate.ddl-auto: none`; `SchemaIntegrityIT` (3) green — `flyway_schema_history` = `1`,`2` all successful; 9 EN003-relevant constraints present; column sets unchanged; only the 4 expected tables |
| VC-012 — ArchUnit | PASS | `StandardArchitectureRulesTest` **11/11** green; deliberate `Money → CreatePortfolioResponse` reference + a stray `Mapper` in `api.mapper` → 3 rules **fail** (BUILD FAILURE) → revert → 11/11 green |
| VC-013 — Integration tests on Testcontainers | PASS | Failsafe **31** tests, 0 F / 0 E — all `@SpringBootTest` + `PostgresContainerSupport` (Testcontainers PostgreSQL 16, singleton) |
| VC-014 — Contracts compatible | PASS | `CreatePortfolioControllerContractTest` (4) green vs `openapi.yaml` (unchanged); runtime 400 → `application/problem+json`, `type:/problems/portfolio-validation`, `errors[]` — no framework/SQL leakage |
| VC-015 — Backend container builds | PASS | `BUILD=1 ./start.sh` built `finai/core-service:local`; `Dockerfile` build stage runs `./mvnw` |
| VC-016 — Platform lifecycle | PASS | `./start.sh` → all containers healthy, `/actuator/health` `UP` / `db: UP`; `./stop.sh` → clean `compose down`; second `./stop.sh` → exit 0 (idempotent) |
| VC-017 — FD001 E2E (mandatory gate) | PASS | `./e2e.sh` → `FD001-create-portfolio.spec.ts` ✓ + `platform-smoke.spec.ts` ✓ — **2 passed** (Chromium), `E2E_EXIT=0` |
| VC-018 — No product change | PASS | `ng test` 29 SUCCESS; no new API operation; `openapi.yaml` diff = FD001 only; no new tech; Java 21 / Spring Boot 3.5.6 unchanged |

---

## Architecture

| Check | Result |
|---|---|
| ADR-003 three-area structure, module-first (AR-053, AR-054) | PASS |
| Dependency direction `infrastructure → business → domain`, no cycles (AR-002, AR-056) | PASS — ArchUnit-enforced |
| Domain framework-free (AR-002, AR-055, constitution VI) | PASS |
| Ports use domain types only (AR-055) | PASS |
| REST adapter conventions — controller `infrastructure.api.rest`, DTOs `…rest.dto`, mappers `…rest.mapper` (AR-058, amended) | PASS — all four placement rules green + non-vacuous |
| Spring Data JPA is the persistence adapter; entities infrastructure; hand-written SQL removed (AR-060) | PASS |
| Architecture automatically checked; violations fail the build (AR-061) | PASS |
| ADR-001 single `core-service` deployable | PASS — unchanged |
| Architecture change recorded in an ADR (AR-048, constitution) | PASS — ADR-003 (pre-approved) + its 2026-09-02 amendment |
| Architecture docs reflect current architecture (AR-049) | PASS — `architecture.md`, `architecture-rules.md`, ADR-003, enabler §8 all consistent with the code |

---

## Technology Policy

| Technology | Policy | Used | Result |
|---|---|---:|---|
| Spring Boot 3.5.6 | ALLOWED | Yes | PASS |
| Maven + Maven Wrapper | REQUIRED | Yes (Maven 3.9.11, `only-script`) | PASS |
| Spring Data JPA / Hibernate | REQUIRED by default | Yes | PASS |
| Direct JDBC | CONDITIONAL | Not in `src/main` (test cleanup only, spec A8) | PASS |
| PostgreSQL 16 · Flyway | PREFERRED | Yes (Flyway owns schema) | PASS |
| Testcontainers | REQUIRED for applicable ITs | Yes | PASS |
| ArchUnit 1.3.0 | AR-061 | Yes | PASS |
| Playwright | PREFERRED (browser E2E) | Yes (FD001 gate) | PASS |
| Kafka / Redis / Neo4j / new runtime | NOT ADDED | No | PASS |

No unapproved technology.

---

## Tests

| Suite | Command | Result |
|---|---|---|
| Unit + domain + ArchUnit + contract (Surefire) | `./mvnw -B clean verify` | **63** run · 0 fail · 0 error — `StandardArchitectureRulesTest` 11, `PortfolioTest` 26, `ValueObjectsTest` 15, `CreatePortfolioServiceTest` 7, `CreatePortfolioControllerContractTest` 4 |
| Integration (Failsafe, Testcontainers PostgreSQL) | same | **31** run · 0 fail · 0 error — `PortfolioPersistenceAdapterIT` 5, `SchemaIntegrityIT` 3, `JpaDefaultInvestorProviderIT` 1, `PlatformIntegrationIT` 3, `CreatePortfolio*IT` ×6 = 19 |
| ArchUnit deliberate-violation | inject `domain→infra` + stray `api.mapper` mapper | BUILD FAILURE (3 rules) → revert → 11/11 green |
| Frontend unit (Karma/Jasmine) | `ng test --watch=false --browsers=ChromeHeadless` | 29 SUCCESS |
| Browser E2E (Playwright, containerized) | `./e2e.sh` | 2 passed · exit 0 |
| Coverage (JaCoCo bundle `check`) | `jacoco:check` | line 98.0 %, branch 94.7 % — gate met |

Assertions relocated, not weakened (SC-001): the two JDBC persistence ITs are rewritten as
`PortfolioPersistenceAdapterIT` / `JpaDefaultInvestorProviderIT` with the same scenarios/assertions
(+ one added detached-read case); `SchemaIntegrityIT` is new; all FD001 domain/service/contract/
full-slice suites moved package unchanged.

---

## Build

| Build | Result |
|---|---|
| `./mvnw -B clean verify` (backend) | BUILD SUCCESS |
| `./mvnw -v` with no host `mvn` on `PATH` | Apache Maven 3.9.11 (wrapper self-resolved) |
| Backend container image (`BUILD=1 ./start.sh`) | built `finai/core-service:local` from the Maven project |
| Frontend `ng test` | 29 SUCCESS |

---

## Runtime Verification

`./start.sh` (BUILD=1): postgres + backend + frontend all **Healthy**; `/actuator/health` →
`{"status":"UP", … "db":{"status":"UP"}}`.

`POST http://localhost:4200/api/portfolios` (through the nginx frontend proxy):

- **201** — `Location: /api/portfolios/{uuid}`; body `status:"ACTIVE"`, `quantity:"3.250"`,
  `averagePurchasePrice:"812.50"` (exact scale), `initialPurchaseDate:null`.
- **200 replay** (same `Idempotency-Key`) — `Idempotency-Replayed: true`.
- **400** — `application/problem+json`; `type:/problems/portfolio-validation`,
  `errors:[{field:"name",code:"REQUIRED"},{field:"positions[0].quantity",code:"NOT_POSITIVE"}]` —
  no stack trace, no SQL, no framework class names.

`./stop.sh` → clean `docker compose down`; second `./stop.sh` → exit 0 (idempotent). Platform left
stopped.

---

## Security and Repository Hygiene

| Check | Result |
|---|---|
| Committed secrets / `.env` / keys / tokens | PASS — none; `.env` is git-ignored; only synthetic `finai_local_dev` / `finai_test` credentials in config |
| Build artifacts committed (`target/`, `*.class`, `node_modules/`) | PASS — none tracked; `.gitignore` covers them |
| Implementation in approved location | PASS — everything under `implementation/platform/`; no `apps/`/`services/`/`src/` root trees |
| Maven wrapper files | intentional to commit (`.mvn/` not git-ignored; `only-script` → no `.jar`) |
| Logs leak secrets | PASS — structured ECS logs carry only `event`, ids, ticker/market |

---

## Documentation

| Item | Result |
|---|---|
| Backend `README.md` | PASS — describes the ADR-003 layout + Spring Data JPA pattern; commands use `./mvnw`; references `StandardArchitectureRulesTest` + ADR-003 |
| `implementation/platform/README.md` | PASS — "Backend architecture" section; `mvn`→`./mvnw`; architecture-test row points at ADR-003 |
| ADR-003 / `architecture-rules.md` / `architecture.md` / enabler §8 | PASS — all consistent with `infrastructure.api.rest.mapper` after the 2026-09-02 amendment |
| SDD artifacts (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`, `checklists/`, `tasks.md`, `pr-evidence.md`, `dod-checklist.md`) | PASS — present, traceable, updated for the mapper relocation |
| No stale `application` / `adapter` / `JdbcClient` guidance | PASS — none in the backend/platform READMEs |

---

## Definition of Done

| Item | Applicable | Status | Evidence |
|---|---:|---|---|
| Traceable to an approved work item | Yes | PASS | EN003 enabler + ADR-003, both Approved |
| Formal spec approved | Yes | PASS | `spec.md` + `checklists/requirements.md` 16/16 |
| All behaviour within approved scope | Yes | PASS | scope table |
| No new business requirement silently introduced | Yes | PASS | contract unchanged; `ng test` / E2E identical |
| Acceptance scenarios implemented | Yes | PASS | US1–US4 → green suites + runtime checks |
| Complies with `architecture.md` / `architecture-rules.md` | Yes | PASS | AR-002, AR-053…AR-061 verified |
| Technology choices comply with `technology-policy.md` | Yes | PASS | technology table |
| Domain logic independent of infrastructure | Yes | PASS | grep + ArchUnit |
| Module ownership clear; no cross-module persistence access | Yes | PASS | `portfolio` owns `portfolio`/`position`; reads seeded `investor` |
| No unapproved technology | Yes | PASS | `pom.xml` diff = +data-jpa / −jdbc |
| Significant architecture change has an ADR | Yes | PASS | ADR-003 + its 2026-09-02 amendment |
| Architecture docs updated when the approved architecture changed | Yes | PASS | ADR-003 amendment + AR-057/AR-058 + `architecture.md` + enabler §8 + ArchUnit rule |
| Code quality — safe numerics, explicit optionals/errors, no unrelated refactor | Yes | PASS | `BigDecimal` no precision/scale; `Optional` ⇄ `null` mapping; `PortfolioNotSavedException` explicit; migration-only diff |
| Unused code/deps removed | Yes | PASS | JDBC adapters + `HexagonalArchitectureRulesTest` + `platform.*`/`bootstrap` + old `api/mapper` package + explicit jdbc starter |
| TDD for new deterministic logic | No | N/A | behaviour-preserving migration — no new deterministic domain logic; relocated domain suites pass unchanged |
| Unit / integration / contract / architecture / E2E tests present & passing | Yes | PASS | tests table |
| Overall coverage ≥ 90 % | Yes | PASS | line 98.0 %, branch 94.7 % |
| Coverage exclusions justified | Yes | WARNING | W003 — `infrastructure/persistence/entity/**` exclusion |
| REST contract OpenAPI-defined, matches impl, machine-readable errors | Yes | PASS | `openapi.yaml` unchanged; contract test + runtime 400 |
| Persistence ownership explicit; migrations tested; constraints present; tx boundaries intentional | Yes | PASS | `SchemaIntegrityIT`, `PortfolioPersistenceAdapterIT`, deliberate cascade/fetch/`TransactionTemplate` |
| No accidental dual-write | Yes | PASS | single aggregate write per transaction |
| Structured logs; no secret leakage | Yes | PASS | ECS JSON; `PortfolioCreated`/`PositionAdded` unchanged |
| Idempotency implemented & tested; failure leaves no partial state | Yes | PASS | `PortfolioPersistenceAdapterIT`, `CreatePortfolioIdempotencyIT`, runtime replay |
| Repository hygiene | Yes | PASS | hygiene table |
| Platform executable via `start.sh` / `stop.sh` | Yes | PASS | runtime verification |
| Reviewed against spec + architecture rules | Yes | PASS | this report + `pr-evidence.md` |
| Documentation current | Yes | PASS | READMEs + SDD artifacts |

No applicable mandatory DoD item fails.

---

## Task Completion Cross-Check

`tasks.md` — **40 / 40** tasks `[X]`, 0 unchecked. Spot-checked against the repository: the
package tree, the JPA persistence layer, the 11-rule ArchUnit suite, the Maven wrapper, the
`Dockerfile` `./mvnw` change, the relocated tests, and the READMEs all exist as described. No false
completion detected.

---

## Findings

### FAILURES

*None.*

### WARNINGS

**W001 — Large uncommitted change stack; EN003 has no commit boundary.**
The working tree carries FD001 (portfolio implementation + `openapi.yaml` contract), EN002
(containerization, `e2e.sh`), and EN003 — all uncommitted on `cd1efc2`. "No `product/` behaviour
change" and "no `openapi.yaml` change" by EN003 are established by traceability + inspection + a
passing contract test, not a clean `git diff`. *Rule*: DoD "Minimum Pull Request Evidence";
constitution ("AI-generated work … scoped, traceable, reviewable"). *Remediation*: commit FD001 and
EN002 first (or stage EN003's files as a clearly separated commit) so EN003 lands as its own
reviewable diff.

**W002 — `product/architecture/diagrams/containers.md` still labels the backend "Hexagonal
Architecture (enforced by ArchUnit)".**
Not wrong (AR-001 frames the three-area model as the Spring implementation of Hexagonal), and
`containers.md` is not in ADR-003's "updates the interpretation of" list. *Rule*: AR-049 / DoD §13.
*Remediation*: optional human edit to name the ADR-003 structure; **EN003 must not touch it**
(FR-030). Non-blocking.

**W003 — JaCoCo coverage exclusion `com/myfinaimanager/core/portfolio/infrastructure/persistence/entity/**`.**
Added by EN003 alongside `CoreServiceApplication.class` and `infrastructure/config/**`. The JPA
entities are declarative mapping structures (getters, id-based `equals`/`hashCode`, no branching
logic); their mapping behaviour is covered by `PortfolioPersistenceAdapterIT` / `SchemaIntegrityIT`;
the exclusion is commented in `pom.xml`. Bundle branch coverage is 94.7 % and does not depend on
the exclusion being decisive. *Rule*: `testing-strategy.md` "exclusions must be limited and
justified". *Remediation*: human sign-off on the exclusion, or drop it and let the ITs carry entity
coverage.

**W004 (informational) — create-response vs replay-response `positions` order.**
A fresh **201** lists positions in submission order (from the in-memory aggregate); a **200 replay**
returns them ordered by position UUID (`@OrderBy("id ASC")`, matching the pre-EN003 JDBC
`ORDER BY id` read path). **Not a regression** — behaviour is identical to before EN003; the
OpenAPI contract does not constrain position order and the contract test passes. Noted so a
reviewer does not mistake it for a defect; a human may separately decide whether FD001 should
define a stable order.

---

## Required Remediation

None blocking. Recommended before / at commit:

1. **W001** — land FD001 and EN002 as their own commits so EN003 is a self-contained diff.
2. **W003** — human sign-off on the entity coverage exclusion.
3. **W002 / W004** — optional human follow-ups outside EN003's scope.

---

## Final Decision

**READY TO CLOSE WITH WARNINGS.**

EN003 delivers its approved scope: the `core-service` backend matches ADR-003 (module-first
`domain/business/infrastructure`, ports in `domain.ports`, Spring Data JPA persistence with a clean
domain, an 11-rule ArchUnit suite, Maven wrapper) with **no** observable behaviour, API, schema, or
technology change. The 2026-09-02 REST-mapper relocation is architect-authorized, documented in
ADR-003, and machine-enforced. Every Verification Criterion has green evidence and the FD001
browser-E2E closure gate passes (`./e2e.sh` exit 0). The warnings are non-blocking.

Human closure approval may proceed. This skill does not change the enabler's status.
