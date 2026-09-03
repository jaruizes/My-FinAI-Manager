# Implementation Plan: Align Spring Backend with Standard Architecture (EN003)

**Branch**: `EN003-align-spring-backend-standard-architecture` | **Date**: 2026-09-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/EN003-align-spring-backend-standard-architecture/spec.md`

**Authoritative enabler**: `product/definition/enablers/EN003-align-spring-backend-standard-architecture/EN003-align-spring-backend-standard-architecture.md` (Status: **Approved**)
**Governing ADR**: `product/architecture/adrs/ADR-003-standard-spring-backend-architecture.md` (Status: **Approved**) — ADR-001 unchanged.

## Summary

EN003 **restructures the existing `core-service` backend** to the standard Spring architecture of
ADR-003 **without changing any observable behavior**:

1. **Module-first + three-area layout** — move the `portfolio` code from
   `domain` / `application` / `adapter` (+ `bootstrap`) to
   `portfolio/{domain/{model,ports,exceptions}, business, infrastructure/{api/rest[/dto,/mapper], persistence/{entity,repository,mapper}}}`.
   Delete the EN001 `core.platform.*` placeholder packages.
2. **Persistence → Spring Data JPA** — replace `JdbcPortfolioRepository` (JdbcClient + hand-written
   SQL + `TransactionTemplate`) and `JdbcDefaultInvestorProvider` with JPA entities
   (`PortfolioEntity`, `PositionEntity`, `InvestorEntity` — infrastructure only), Spring Data
   repositories, an explicit domain↔entity mapper, and persistence adapters that implement the
   (relocated) `portfolio.domain.ports` interfaces. Flyway keeps owning the schema
   (`ddl-auto: none`).
3. **Dependency rules enforced** — rewrite the ArchUnit suite for `infrastructure → business → domain`
   plus the ADR-003 §16 placement rules.
4. **Build** — add a pinned Maven wrapper (`mvnw` / `.mvn/`); the project is already Maven and the
   EN002 Dockerfile already builds with Maven.
5. **Regression** — every existing test adapted (packages/types only, assertions intact) and green;
   the **FD001 Playwright E2E is a mandatory closure gate** (`./e2e.sh` exit 0); `start.sh` /
   `stop.sh` / container build unchanged.
6. **Docs** — backend `README` / implementation architecture references updated to the ADR-003
   structure. Human-governed `product/` docs are **not** touched (already updated by ADR-003).

**No** product behavior, API, schema (beyond a possible documented minimal mapping migration),
authentication, messaging, new service, or Java/Spring major-version change.

## Technical Context

**Language / Runtime**: Java 21, Spring Boot **3.5.6** (unchanged — EN001 decisions). Bash (lifecycle scripts, unchanged).

**Primary Dependencies**:
- **Added**: `spring-boot-starter-data-jpa` (Hibernate ORM 6.x via the Spring Boot BOM).
- **Removed**: explicit `spring-boot-starter-jdbc` (transitive via data-jpa; `JdbcClient`/`JdbcTemplate` stay available for test-support only).
- **Unchanged**: `spring-boot-starter-web`, `-actuator`, `flyway-core` + `flyway-database-postgresql`, `postgresql` driver, `spring-boot-starter-test`, `spring-boot-testcontainers`, `testcontainers` (junit-jupiter + postgresql), `archunit-junit5` **1.3.0**, `swagger-request-validator-mockmvc` **2.44.1**, JaCoCo gate (≥ 90 % line + branch).
- **Added tooling**: Maven wrapper `mvnw` / `mvnw.cmd` / `.mvn/wrapper/` pinned to a maintainer-chosen Maven 3.9.x.

**Storage**: PostgreSQL 16 — **same schema** (`investor` / `portfolio` / `position`, Flyway `V1` + `V2`). JPA maps to the existing tables. Hibernate DDL disabled.

**Testing**: `./mvnw verify` — Surefire (`*Test`) + Failsafe (`*IT`) + JaCoCo `check`. Testcontainers PostgreSQL for all persistence + full-slice ITs (`api.version=1.44` Failsafe systemProperty preserved). ArchUnit conformance. Frontend `ng test` (regression only, no change). Playwright `./e2e.sh` (FD001 + smoke).

**Target Platform**: Local developer workstation + the EN002 containerized platform. `core-service` is one Spring Boot deployable (ADR-001). No cloud, no CI.

**Project Type**: Cumulative platform under `implementation/platform/` — a Spring backend refactor within the existing `core-service` module.

**Performance Goals**: none new. The JPA aggregate write/read must not regress the "instant" create expectation (FD001 SC-001); the existing multi-position IT (≥ 10 positions) and its non-gating timing assertion stay.

**Constraints**:
- ADR-003 is prescriptive — the package structure, the three-area dependency direction, JPA-as-standard, Maven, and ArchUnit enforcement are **not** open. Only EN003 §20 items are open (below).
- Every externally observable behavior identical: API payloads/status/headers/error bodies byte-for-byte; idempotency (201 create / 200 replay); atomic aggregate write; exact-decimal round-trip; structured `PortfolioCreated` / `PositionAdded` events; 400 validation / 503 not-saved outcomes.
- `domain` free of Spring / Spring Data / JPA / HTTP / JDBC / messaging / serialization / provider SDKs (FR-008; ArchUnit).
- Flyway owns the schema; `spring.jpa.hibernate.ddl-auto` = `none`.
- No edit to `product/architecture/*` or other human-governed docs (FR-030).
- `start.sh` / `stop.sh` / `e2e.sh` / `compose.yaml` interface unchanged (FR-025).

**Open technical decisions (EN003 §20 — resolved in research.md within ADR-003 bounds; recommended defaults recorded)**:

| ID | Decision | Recommended default |
|----|----------|---------------------|
| OD-EN003-1 | Base package layout for the module | `com.myfinaimanager.core.portfolio.{domain.{model,ports,exceptions}, business, infrastructure.{api.rest, api.rest.dto, api.rest.mapper, persistence.{entity,repository,mapper}, config}}` |
| OD-EN003-2 | Inbound use-case interface + command/result placement | keep `CreatePortfolioUseCase` + `CreatePortfolioCommand` + `CreatePortfolioResult` in `…portfolio.business` (business API); controller depends on `business` |
| OD-EN003-3 | JPA entity naming | `PortfolioEntity` / `PositionEntity` / `InvestorEntity` in `…infrastructure.persistence.entity` |
| OD-EN003-4 | Mapper style | hand-written mappers (`PortfolioPersistenceMapper`, `CreatePortfolioRequestMapper`, `PortfolioResponseMapper`) — no mapping-library dependency |
| OD-EN003-5 | Aggregate relationship mapping | `PortfolioEntity` `@OneToMany(mappedBy="portfolio", cascade=ALL, orphanRemoval=true)` `List<PositionEntity>` + `@OrderBy("id")`; `PositionEntity` `@ManyToOne(fetch=LAZY) @JoinColumn("portfolio_id")` |
| OD-EN003-6 | Fetch strategy for reconstruction | association LAZY; the finder repository methods use `@EntityGraph(attributePaths = "positions")` so the aggregate is fully loaded for mapping outside a transaction |
| OD-EN003-7 | Idempotency-race handling | programmatic `TransactionTemplate` (as today): `repository.saveAndFlush(entity)` inside the tx → catch `DataIntegrityViolationException` → re-read `findByIdempotencyKey` in a fresh tx → return existing or throw `PortfolioNotSavedException` |
| OD-EN003-8 | `ddl-auto` | `none` (Flyway owns schema; unbounded `NUMERIC` and `CHAR(3)` make `validate` noisy) + `spring.jpa.open-in-view: false` + `hibernate.jdbc.time_zone: UTC` |
| OD-EN003-9 | Explicit `spring-boot-starter-jdbc` | drop it (data-jpa supersedes); keep `JdbcClient` for test-support cleanup **or** switch IT cleanup to `repository.deleteAllInBatch()` — plan default: keep test-support `JdbcClient` (A8) |
| OD-EN003-10 | `Clock` bean + remaining config | a small `…portfolio.infrastructure.config.PortfolioModuleConfiguration` (`@Bean Clock systemUTC`); delete `core.bootstrap.PortfolioBeanConfiguration` |
| OD-EN003-11 | Maven wrapper version | maintainer-pinned Maven 3.9.x (latest 3.9 at implementation) |
| OD-EN003-12 | JaCoCo exclusions | keep `CoreServiceApplication`; replace `bootstrap/**` with `…portfolio/infrastructure/config/**`; JPA entities / mappers / adapters stay **in** coverage (exercised by ITs) |

All are safe, reversible, ADR-003-compliant implementation details.

**Scale/Scope**: ~35 production classes relocated/renamed; ~5 new infrastructure persistence classes; ~3 new mapper classes; 2 JDBC adapters replaced; 1 config class; ArchUnit suite rewritten; ~16 test classes relocated (2 persistence ITs rewritten for JPA); pom + application.yml + Dockerfile touched; Maven wrapper added; backend README updated. **No frontend change. No `openapi.yaml` change. No new Flyway migration expected.**

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.0.0**, principles I–VIII.

| # | Principle | Status | Evidence |
|---|---|---|---|
| I | Human-Governed Source of Truth (respect ADRs + technology-policy) | PASS | Implements **ADR-003** (approved, governing). ADR-001 intact (`core-service` not split). Uses only policy-approved tech — `technology-policy.md` (updated alongside ADR-003) now makes **Spring Data JPA REQUIRED-by-default** and **Maven REQUIRED**; EN003 *corrects* the FD001 `JdbcClient` deviation. `product/architecture/*` and other human-governed docs are **not** edited (FR-030). |
| II | Definitions/Enablers Are Authoritative Intent | PASS | Every plan element traces to EN003 `FR-001…FR-032` / `VC-001…VC-018`. Scope is the enabler's §3 — a behavior-preserving refactor, nothing added. |
| III | Derived Artifacts & Repository Layout | PASS | Design artifacts under `specs/EN003-…/`. All code stays under `implementation/platform/backend/core-service/`. No new root trees, no CI. |
| IV | No Invention; Surface Material Ambiguity | PASS | ADR-003 is the authority. Residual choices are **technical** (EN003 §20) — recorded as OD-EN003-1…12 with defaults, resolved in `research.md`. Any *material* deviation from ADR-003 is surfaced for human approval (FR-032). |
| V | Technical Enablers Stay Technical | PASS | No investor user stories; US1–US4 are backend-developer/maintainer workflows. No Portfolio behavior added/changed. Platform stays executable via `start.sh` / `stop.sh` (EN002 containerized). |
| VI | Hexagonal Architecture & Deterministic Logic | PASS | EN003 **strengthens** hexagonal: a prescriptive three-area structure with `infrastructure → business → domain`, enforced by ArchUnit. `domain` stays framework-free (FR-008). Deterministic domain logic (`Portfolio.create`, value objects) is **relocated, not modified**. No LLM. |
| VII | Test-First for Deterministic Logic; Testcontainers by Default | PASS | No new deterministic domain logic (relocation only) ⇒ nothing to TDD from scratch. Existing domain tests move with assertions intact (FR-026). The **JPA persistence adapter** is exercised by rewritten Testcontainers-PostgreSQL ITs asserting the same atomicity / constraints / idempotency / exact-decimal behavior — **never a mocked database** (FR-027). |
| VIII | Contract-First External APIs | PASS | `implementation/platform/contracts/openapi/openapi.yaml` is **unchanged**. The contract test (`swagger-request-validator`) stays and must remain green; DTOs move packages but their JSON representation is byte-for-byte identical (FR-015; DR-018/DR-019). |

### Development-Workflow / Compliance gates

| Gate | Status | Notes |
|---|---|---|
| Plan includes a Constitution Check | PASS | This section. |
| Completion measured by `definition-of-done.md` | PASS | Carried into `/speckit-tasks`. Coverage gate ≥ 90 % preserved; **FD001 E2E is a hard closure gate** (VC-017 / FR-028). |
| ADR required for material architecture change | PASS | **ADR-003 already approved** and is the reason EN003 exists. No new ADR. ADR-001 unchanged. |
| Structured logging; no secrets; externalized config | PASS | Logging config unchanged (ECS JSON). `application.yml` gains only JPA settings (no secrets); datasource still env-driven. |
| No speculative infrastructure | PASS | Adds `spring-boot-starter-data-jpa` (policy: REQUIRED-by-default) and a Maven wrapper. No new service / broker / DB / cache. |

**Result: PASS.** No violations. Complexity Tracking not required — see "Migration risk register" below for the non-blocking watch-items.

## Migration Risk Register *(non-blocking — mitigations in research.md / tasks)*

| Risk | Mitigation |
|---|---|
| JPA aggregate write differs from the JDBC path (flush timing, cascade order) → idempotency-race or rollback behavior changes | `saveAndFlush` inside a programmatic tx; catch `DataIntegrityViolationException`; rewritten idempotency + rollback ITs assert the exact pre-migration outcome. |
| `LazyInitializationException` when mapping the aggregate to domain outside a transaction | `@EntityGraph(attributePaths="positions")` on every finder; `open-in-view: false`; an IT reads a Portfolio + Positions outside a tx. |
| Unbounded `NUMERIC` / `CHAR(3)` vs Hibernate `validate` | `ddl-auto: none`; a schema-integrity IT asserts the Flyway history rows and that the key constraints exist (unchanged). |
| Decimal / date / null JSON formatting drifts when the response mapper moves | contract test + full-slice ITs assert byte-for-byte responses; mapper keeps `toPlainString()` / `LocalDate::toString` / `null`. |
| ArchUnit rule becomes vacuous after the package move (`..adapter..` no longer exists) | rules re-pointed to `..domain..` / `..business..` / `..infrastructure..`; `allowEmptyShould` removed where the rule is now substantive; a deliberate-violation check in the tasks. |
| `spring-boot-starter-jdbc` removal breaks Flyway / test-support | data-jpa provides the `DataSource` + `spring-jdbc`; verify `mvn verify` + platform startup; keep test-support `JdbcClient` if needed (OD-EN003-9). |
| `bootstrap` package removal breaks JaCoCo exclusion / component scan | move `Clock` bean to `…infrastructure.config`; update the JaCoCo `<excludes>`; `@SpringBootApplication` scan root (`com.myfinaimanager.core`) already covers the new packages. |

## Project Structure

### Documentation (this feature)

```text
specs/EN003-align-spring-backend-standard-architecture/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 — D1…Dn resolving OD-EN003-1…12 + JPA/ArchUnit/build patterns
├── data-model.md        # Phase 1 — domain relocation map + domain↔JPA-entity↔table mapping
├── contracts/
│   └── persistence-port.md   # the (unchanged) domain persistence ports + invariants the JPA adapter must preserve
├── quickstart.md        # Phase 1 — validation scenarios mapped to VC-001…VC-018
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks output (NOT created here)
```

The external REST contract lives at `implementation/platform/contracts/openapi/openapi.yaml` and
is **unchanged** by EN003 — no fragment under `specs/EN003-…/contracts/openapi/`.

### Source Code (repository root) — target after migration

```text
implementation/platform/backend/core-service/
├── pom.xml                          # + spring-boot-starter-data-jpa; − explicit starter-jdbc; JaCoCo excludes updated
├── mvnw  mvnw.cmd  .mvn/wrapper/     # NEW — pinned Maven wrapper (FR-024)
├── Dockerfile                       # build stage uses ./mvnw (was mvn)
├── src/main/resources/
│   ├── application.yml              # + spring.jpa.{hibernate.ddl-auto: none, open-in-view: false, properties.hibernate.jdbc.time_zone: UTC}
│   └── db/migration/                # V1__baseline.sql, V2__portfolio.sql — UNCHANGED (no new migration expected)
│
└── src/main/java/com/myfinaimanager/core/
    ├── CoreServiceApplication.java              # unchanged (@SpringBootApplication, scans com.myfinaimanager.core)
    ├── platform/  ────────────────────────────  # DELETED (EN001 empty convention placeholders — FR-006)
    ├── bootstrap/ ────────────────────────────  # DELETED (PortfolioBeanConfiguration → infrastructure/config)
    │
    └── portfolio/
        ├── domain/
        │   ├── model/               # Portfolio, Position, NewPosition, PortfolioId, PositionId, InvestorId,
        │   │                        #   PortfolioName, PortfolioStatus, Ticker, Market, Currency, Money,
        │   │                        #   Quantity, InstrumentRef, ValidationCode, Violation  (relocated, unmodified)
        │   ├── ports/               # PortfolioRepository, DefaultInvestorProvider   (moved from application/port/out)
        │   └── exceptions/          # PortfolioValidationException, PortfolioNotSavedException
        │
        ├── business/                # CreatePortfolioService (@Service), CreatePortfolioUseCase,
        │                            #   CreatePortfolioCommand, CreatePortfolioResult
        │
        └── infrastructure/
            ├── api/
            │   └── rest/            # CreatePortfolioController, PortfolioExceptionHandler
            │       ├── dto/         # CreatePortfolioRequest (+ PositionInput), CreatePortfolioResponse (+ PositionResponse)
            │       └── mapper/      # CreatePortfolioRequestMapper (request → command),
            │                        #   PortfolioResponseMapper (domain Portfolio → response DTO)
            │                        #   (ADR-003 amendment 2026-09-02: api.rest.mapper, part of the REST adapter)
            ├── persistence/
            │   ├── entity/          # PortfolioEntity, PositionEntity, InvestorEntity  (JPA @Entity — infra only)
            │   ├── repository/      # PortfolioJpaRepository, InvestorJpaRepository  (Spring Data JPA)
            │   ├── mapper/          # PortfolioPersistenceMapper (domain ⇄ entity)
            │   ├── PortfolioPersistenceAdapter.java   # implements portfolio.domain.ports.PortfolioRepository
            │   └── JpaDefaultInvestorProvider.java    # implements portfolio.domain.ports.DefaultInvestorProvider
            └── config/
                └── PortfolioModuleConfiguration.java  # @Configuration @Bean Clock systemUTC

src/test/java/com/myfinaimanager/core/
├── architecture/StandardArchitectureRulesTest.java   # REWRITTEN — ADR-003 §16 rules
├── CoreServiceApplicationTests? / bootstrap/PlatformIntegrationIT.java  # EN001 — kept, imports updated if needed
├── support/PostgresContainerSupport.java             # unchanged
└── portfolio/
    ├── domain/model/{PortfolioTest, ValueObjectsTest}.java     # moved, assertions intact
    ├── business/CreatePortfolioServiceTest.java                # moved, ports mocked
    └── infrastructure/
        ├── api/rest/CreatePortfolioControllerContractTest.java # moved (@WebMvcTest + swagger-request-validator)
        └── persistence/
            ├── PortfolioPersistenceAdapterIT.java              # REPLACES JdbcPortfolioRepositoryIT — JPA adapter, Testcontainers
            └── JpaDefaultInvestorProviderIT.java               # REPLACES JdbcDefaultInvestorProviderIT
    # full-slice ITs (package/import updates only, assertions intact):
    ├── AbstractPortfolioIT, CreatePortfolioIT, CreatePortfolioIdempotencyIT,
    ├── CreatePortfolioAcceptanceIT, CreatePortfolioMultiPositionIT,
    └── CreatePortfolioValidationIT, CreatePortfolioOptionalDataIT
```

**Structure Decision**: the migration is **entirely inside** `implementation/platform/backend/core-service/`.
`core-service` stays one deployable (ADR-001). The `portfolio` module becomes the reference
implementation of ADR-003's `domain / business / infrastructure` layout, enforced by ArchUnit; the
EN001 `platform` placeholder packages and the `bootstrap` config package are removed. No frontend,
contract, CI, or lifecycle-script change.

## Complexity Tracking

No Constitution Check violations. Section intentionally empty (see "Migration Risk Register" for
non-blocking watch-items).

## Phase 0 — Research

See [research.md](./research.md). Resolves OD-EN003-1…12 within ADR-003, plus: Spring Data JPA
aggregate mapping for the `Portfolio`/`Position` aggregate; preserving the idempotency-race and
atomic-write semantics with JPA; `@EntityGraph` vs `join fetch` for aggregate reconstruction;
`ddl-auto: none` + a schema-integrity IT instead of `validate`; keeping the domain JPA-free with an
explicit mapper; ArchUnit rules for ADR-003 §16; dropping the explicit JDBC starter; the Maven
wrapper. No `NEEDS CLARIFICATION` on enabler intent remain (EN003 §21 approval complete).

## Phase 1 — Design & Contracts

- [data-model.md](./data-model.md) — no new business entities. Documents (a) the domain-type
  relocation map (old package → `domain.model` / `domain.ports` / `domain.exceptions`), and
  (b) the domain ⇄ JPA-entity ⇄ table mapping for `Portfolio`/`Position`/`Investor`, field by
  field, with the constraints and invariants each mapping must preserve.
- [contracts/persistence-port.md](./contracts/persistence-port.md) — the `PortfolioRepository` and
  `DefaultInvestorProvider` port contracts (relocated to `portfolio.domain.ports`, signatures
  unchanged) and the behavioral guarantees the JPA `PortfolioPersistenceAdapter` must reproduce
  (atomic aggregate write, idempotency-key resolution, `PortfolioNotSavedException` on transient
  failure, exact-decimal round-trip, order-by-id). The external REST contract
  (`openapi.yaml`) is unchanged and not duplicated here.
- [quickstart.md](./quickstart.md) — validation scenarios for `./mvnw verify`, the ArchUnit
  deliberate-violation check, the JPA persistence ITs, the contract test, `./start.sh` /
  `./stop.sh`, the backend container build, and `./e2e.sh` (FD001 E2E gate), each mapped to
  `VC-001…VC-018`.

### Post-Design Constitution Re-Check

Re-evaluated after Phase 1: still **PASS**. The design adds no product behavior, no API operation,
no schema change (a minimal forward migration only if an unavoidable mapping incompatibility is
found — none expected), no authentication, and no technology beyond policy-approved Spring Data
JPA + a Maven wrapper. `domain` stays framework-free (verified by the rewritten ArchUnit suite).
The external contract is untouched. No new ADR triggered.
