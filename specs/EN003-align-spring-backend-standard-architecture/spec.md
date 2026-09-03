# Feature Specification: Align Spring Backend with Standard Architecture (EN003)

**Feature Branch**: `EN003-align-spring-backend-standard-architecture`

**Created**: 2026-09-02

**Status**: Draft

**Input**: Technical Enabler: "Align the existing My-FinAI-Manager Spring Boot backend with the standard Spring architecture approved by ADR-003. Restructure the backend implementation without intentionally changing product behavior. This is a technical enabler — do not invent user stories or business functionality."

**Authoritative Source**: `product/definition/enablers/EN003-align-spring-backend-standard-architecture/EN003-align-spring-backend-standard-architecture.md` (Status: **Approved**, jaruiz, 2026-09-02; Human Approval checklist complete)

**Governing Architecture Decision**: `product/architecture/adrs/ADR-003-standard-spring-backend-architecture.md` (Status: **Approved**). *(The enabler §1 says "ADR-002" — that is a typo; the header, §19, and `technology-policy.md` all reference ADR-003, which is the governing decision.)*

---

## Enabler Nature *(mandatory)*

EN003 is a **Technical Enabler**, not a product Feature Definition.

It introduces **no investor-facing behavior**, no new Portfolio capability, and no API change. Its
purpose is to **restructure the existing `core-service` backend** so it matches the standard Spring
architecture prescribed by **ADR-003** — a module-first package layout with `domain` / `business` /
`infrastructure` areas, ports in `domain.ports`, adapters in `infrastructure`, and **Spring Data
JPA** replacing the current `JdbcClient` / hand-written-SQL Portfolio persistence — while keeping
every externally observable behavior identical.

Because this is an enabler:

- The scenarios below describe **backend-developer / maintainer workflows**, not investor journeys.
- "Acceptance" is expressed through the enabler's **Verification Criteria (VC-001 … VC-018)**.
- No business domain entities or rules are added, removed, or reinterpreted. The Information Model
  under `product/definition/global/` is unaffected.
- The existing FD001 test suites and the FD001 browser E2E test are **regression evidence** and
  **mandatory closure gates**.

---

## User Scenarios & Testing *(mandatory)*

The beneficiaries are the **engineering team** (human and AI contributors) who will build every
future Spring Feature Definition and Technical Enabler on top of this structure, plus anyone who
reviews, runs, or extends the backend.

EN003 is **one atomic migration** — the "stories" below are facets of it, ordered by importance;
none ships independently (you cannot half-rename a package), but each is independently *verifiable*.

### User Story 1 - Standard module architecture, enforced (Priority: P1)

As a backend developer opening the `portfolio` module, I find the ADR-003 layout —
`domain/{model,ports,exceptions}`, `business`, `infrastructure/{api,persistence,…}` — with the
dependency direction `infrastructure → business → domain`, so I extend that shape instead of
re-deriving the old `application` / `adapter` structure, and an automated check fails the build if
I break it.

**Why this priority**: ADR-003 exists because the earlier generic-Hexagonal freedom led
AI-assisted work to reproduce a non-standard structure. Making the standard concrete and
machine-enforced is the core of the enabler; every future Spring module depends on it.

**Independent Test**: Inspect `implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core/portfolio/`
and confirm the `domain` / `business` / `infrastructure` areas with the ADR-003 sub-packages; run
the architecture test suite and confirm it enforces `domain !→ business`, `domain !→ infrastructure`,
`business !→ infrastructure`, plus the adapter-placement rules, and fails on a deliberate violation.

**Acceptance Scenarios**:

1. **Given** the migrated backend, **When** the `portfolio` module is inspected, **Then** its code is organized as `portfolio/{domain,business,infrastructure}` with `domain/{model,ports,exceptions}` and `infrastructure/{api/rest[/dto,/mapper], persistence/{entity,repository,mapper}}`. *(VC-002, VC-003, VC-007)*
2. **Given** the architecture test suite, **When** it runs, **Then** it fails if any `..domain..` type depends on `..business..` or `..infrastructure..`, or any `..business..` type depends on `..infrastructure..`. *(VC-012, EN003 §16)*
3. **Given** the architecture test suite, **When** it runs, **Then** it fails if a JPA entity, Spring Data repository, or REST controller/DTO is placed outside its standard `infrastructure` package, or if `..domain..` depends on Spring Data / JPA / JDBC / HTTP / messaging. *(VC-004, VC-009, VC-012)*
4. **Given** the EN001 `com.myfinaimanager.core.platform.*` convention-placeholder packages, **When** the migration is complete, **Then** they are removed (superseded by ADR-003) and no empty "old convention" package remains to mislead future work. *(EN003 §2)*
5. **Given** all business-facing external-dependency interfaces (persistence, default-investor lookup), **When** the module is inspected, **Then** they live under `portfolio.domain.ports` and their method signatures contain no framework-specific types. *(VC-005, EN003 §6)*

---

### User Story 2 - Portfolio persistence via Spring Data JPA, domain kept clean (Priority: P1)

As a backend developer, Portfolio persistence goes through a JPA persistence adapter →
Spring Data repository → JPA entities → PostgreSQL, with the canonical domain model carrying no
JPA annotations, no hand-written SQL for ordinary persistence, and Flyway still owning the schema.

**Why this priority**: The `JdbcClient` + embedded-SQL repository is the second thing ADR-003
explicitly rejects for ordinary persistence. Replacing it (without changing behavior) is half the
enabler's concrete work and unblocks the JPA-standard pattern for future modules.

**Independent Test**: Inspect the persistence package — confirm JPA entities under
`infrastructure/persistence/entity`, a Spring Data repository under
`infrastructure/persistence/repository`, an adapter implementing the domain port, an explicit
domain↔entity mapper, and **no** `JdbcClient` / `JdbcTemplate` / embedded SQL in the Portfolio
persistence path; run the persistence integration tests against Testcontainers PostgreSQL and
confirm the same atomicity, constraint, idempotency, and exact-decimal behavior as before.

**Acceptance Scenarios**:

1. **Given** the Portfolio persistence path, **When** it is inspected, **Then** it uses Spring Data JPA (a repository extending a Spring Data JPA repository type, JPA `@Entity` classes, a persistence adapter implementing `portfolio.domain.ports` persistence port) and contains no `JdbcClient` / `JdbcTemplate` / hand-written SQL for ordinary persistence. *(VC-008, EN003 §9, §11)*
2. **Given** the canonical domain model (`Portfolio`, `Position`, value objects, enums), **When** it is inspected, **Then** it contains no JPA / Hibernate / Spring annotations or imports; JPA entities are separate infrastructure types with explicit mapping to/from the domain. *(VC-004, VC-009, EN003 §9, §10)*
3. **Given** a valid create request, **When** the Portfolio and its Positions are persisted, **Then** the persisted result is functionally identical to before the migration: Portfolio identity, owning Investor, name, status, creation timestamp, idempotency-key behavior, each Position's `ticker + market`, quantity, currency, optional initial purchase date, optional average purchase price, and the aggregate is written atomically (all Positions or none). *(VC-010, EN003 §10)*
4. **Given** exact-decimal values for quantity and average purchase price, **When** they are stored and read back, **Then** they reproduce the input exactly (no rounding, no binary-float drift). *(VC-010; FD001 SC-007)*
5. **Given** the same idempotency key submitted twice, **When** the second submission is handled, **Then** exactly one Portfolio exists and the second call resolves to the already-created one — identical to the pre-migration semantics. *(VC-010; FD001 FR-031a / SC-011)*
6. **Given** the database constraints established by Flyway (`portfolio.idempotency_key` unique, `position (portfolio_id, ticker, market)` unique, `NUMERIC` columns, CHECK constraints), **When** the migration is complete, **Then** they are unchanged and still enforced; JPA/Hibernate schema auto-generation is not used to evolve or replace the schema. *(VC-011, EN003 §12)*
7. **Given** the JPA aggregate mapping, **When** it is reviewed, **Then** relationship, cascade, orphan-removal, and fetch strategy are set **deliberately** (not left to accidental defaults) so aggregate reconstruction and write semantics match the current behavior. *(EN003 §10)*

---

### User Story 3 - Behavior and regression fully preserved (Priority: P1)

As a maintainer, after the migration every existing automated test still passes with its
assertions intact, the FD001 browser E2E Create-Portfolio journey passes against the containerized
platform, the platform still starts and stops through the canonical scripts, and the backend
container still builds — proving nothing observable changed.

**Why this priority**: EN003's whole promise is "restructure without changing behavior". The
existing suites (and FD001 E2E) are the only proof of that. ADR-003 and the enabler make the FD001
E2E a **mandatory closure gate**.

**Independent Test**: Run the full backend build (`mvn verify` — unit, Spring IT, Testcontainers
IT, contract, ArchUnit, coverage gate); run the frontend build/tests; run `./e2e.sh` and confirm
`FD001-create-portfolio.spec.ts` passes; run `./start.sh` / `./stop.sh`; build the backend
container image. All green, with no test assertion weakened.

**Acceptance Scenarios**:

1. **Given** the pre-migration backend test suite (unit, domain, Spring integration, PostgreSQL Testcontainers, contract, architecture), **When** it is run after the migration, **Then** every test passes and no assertion was removed or weakened — tests were adapted only for package/class/type changes. *(VC-013, VC-014, EN003 §15)*
2. **Given** the external REST contract (`implementation/platform/contracts/openapi/openapi.yaml`), **When** the migrated backend serves `POST /api/portfolios`, **Then** request/response payloads, status codes, headers (`Location`, `Idempotency-Replayed`), and the RFC 9457 error bodies are byte-for-byte compatible with the pre-migration behavior and the contract test still passes. *(VC-014, EN003 §8; FD001 FR-036)*
3. **Given** the containerized platform, **When** `./e2e.sh` runs, **Then** `FD001-create-portfolio.spec.ts` passes (real browser → frontend → REST → refactored `core-service` → Spring Data JPA → PostgreSQL) and `./e2e.sh` exits 0. **EN003 MUST NOT be accepted or marked Completed while this test fails.** *(VC-017, EN003 §17)*
4. **Given** the canonical lifecycle, **When** `./start.sh` then `./stop.sh` are run, **Then** the fully containerized platform starts healthy and stops cleanly, exactly as before — the migration does not restore host-based Spring execution as the canonical runtime. *(VC-016, EN003 §14)*
5. **Given** the backend Docker build, **When** the `core-service` image is built, **Then** it builds successfully from the Maven project. *(VC-015, EN003 §14)*
6. **Given** the structured business-event logging (`PortfolioCreated`, `PositionAdded`) and the persistence-failure / validation-failure behavior, **When** exercised after the migration, **Then** they are unchanged (same events, same 400 / 503 outcomes, nothing partial persisted). *(VC-010, VC-018; FD001 FR-023 / FR-023a / FR-032)*

---

### User Story 4 - Maven-standard build and aligned documentation (Priority: P2)

As a contributor, the backend builds and tests through Maven with a pinned Maven wrapper, the
Dockerfile uses Maven, and the backend README / architecture references / package examples
describe the ADR-003 structure so the next person (or agent) follows the standard.

**Why this priority**: ADR-003 makes Maven the required Spring build tool and expects `mvnw` /
`.mvn/`. This backend is already Maven (no Gradle), so this story is small — a wrapper plus
documentation alignment — but it closes the "AI keeps reproducing the old way" loop.

**Independent Test**: Build the backend with `./mvnw verify` from a clean checkout (no host Maven
required); inspect the backend `README` / architecture references and confirm they show the
`domain / business / infrastructure` module layout and the Spring Data JPA persistence pattern,
with no lingering `JdbcClient` / `application` / `adapter` guidance.

**Acceptance Scenarios**:

1. **Given** a clean checkout, **When** `./mvnw verify` is run in `implementation/platform/backend/core-service`, **Then** the build and all tests succeed without a host-installed Maven. *(VC-001, EN003 §20)*
2. **Given** the backend Dockerfile and any build commands in `start.sh` / `e2e.sh` / compose, **When** they are inspected, **Then** they invoke Maven (already the case via EN002) and remain consistent with the wrapper. *(VC-001, VC-015, EN003 §14)*
3. **Given** the backend `README`, architecture diagram references, developer commands, package examples, and test instructions, **When** they are reviewed, **Then** they reflect the ADR-003 `domain / business / infrastructure` module structure and the Spring Data JPA persistence pattern, and contain no guidance to use the removed `application` / `adapter` convention or `JdbcClient` for ordinary persistence. *(VC-002, VC-003, VC-008, EN003 §19)*
4. **Given** human-governed architecture documents (`architecture.md`, `architecture-rules.md`, `technology-policy.md`), **When** implementation documentation is updated, **Then** those human-governed files are **not** silently reinterpreted or edited by the migration — they are already updated by ADR-003. *(EN003 §19; constitution I)*

---

### Edge Cases

- **Latent behavior bug surfaced by the migration** — if moving to JPA reveals a pre-existing defect (e.g. a mapping subtlety), the fix is in scope **only** to preserve the documented/observed pre-migration behavior, and MUST be explicitly called out (not folded in silently). Anything that would *change* observable behavior is out of scope and must be surfaced for human approval.
- **JPA aggregate write differs from the JDBC path** — e.g. cascade ordering, flush timing, or `DataIntegrityViolationException` mapping for the idempotency-key race. The migrated adapter must reproduce the pre-migration outcome (201 create / 200 replay, nothing partial on failure), verified by the existing idempotency and rollback integration tests.
- **Fetch strategy causes N+1 or lazy-init errors during aggregate reconstruction** — fetch/entity-graph strategy must be explicit so read-back of a Portfolio with its Positions works outside a transaction where the current tests expect it.
- **`spring-boot-starter-jdbc` removal** — Flyway and test-support may still rely on a `DataSource` / `JdbcTemplate`; removing the JDBC starter must not break Flyway or the ITs (the plan decides whether to keep it).
- **`ddl-auto`** — must be `none` (or `validate`), never `create` / `update`; Hibernate must not touch the schema. An IT should assert Flyway history is intact and Hibernate did not alter tables.
- **ArchUnit rule made vacuously true** — after the package move, rules that previously matched `..platform..` or `..adapter..` must be re-pointed so they are non-vacuous against the new packages; a rule that matches nothing must fail loudly rather than pass silently.
- **Contract drift via serialization change** — switching DTO construction/mapping must not change field names, null handling, decimal string formatting, or date formatting in the JSON; the contract test guards this and must stay green.
- **Test-support direct SQL** — integration-test cleanup that uses `JdbcClient` / `DELETE FROM …` is test infrastructure, not application persistence; it may remain (or move to repository deletes) — it is not what the "no ad-hoc SQL" rule targets.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Module & three-area structure

- **FR-001**: The backend MUST be organized **module-first**: business code lives under a functional-module package (`com.myfinaimanager.core.portfolio`), and architecture areas are nested inside each module — not a global layer-first layout. `core-service` remains the single deployable backend component (ADR-001, unchanged). *(EN003 §4; ADR-003)*
- **FR-002**: Each migrated functional module MUST contain the three areas `domain`, `business`, and `infrastructure`. *(VC-003)*
- **FR-003**: `domain` MUST contain `model/` (domain classes, aggregates, value objects, enums, deterministic domain behavior), `ports/` (interfaces business operations use to reach external capabilities), and `exceptions/` (domain/business exceptions). *(EN003 §6; ADR-003)*
- **FR-004**: `business` MUST contain the business operation / use-case implementation(s). It MAY depend on `domain` (models, ports, same-module business abstractions) and MUST NOT import `infrastructure`. Spring annotations for DI / transaction management MAY remain in `business` classes where appropriate. *(EN003 §7)*
- **FR-005**: `infrastructure` MUST contain all adapters and framework/provider-specific code, each in an explicit standard package: REST controllers in `infrastructure.api.rest`, REST DTOs in `infrastructure.api.rest.dto`, REST mappers in `infrastructure.api.rest.mapper`, persistence in `infrastructure.persistence` (`entity/`, `repository/`, `mapper/`, plus the adapter), messaging (when present) in `infrastructure.messaging`. *(EN003 §8, §9; VC-007)*
- **FR-006**: The EN001 `com.myfinaimanager.core.platform.*` convention-placeholder packages MUST be removed. Any residual guidance describing the old `application` / `adapter` layout MUST be removed or updated. *(EN003 §2)*

#### Dependency rules & architecture verification

- **FR-007**: The dependency direction MUST be `infrastructure → business → domain`. `infrastructure` MAY also depend directly on `domain` (to implement ports and mapping). `domain` MUST NOT depend on `business` or `infrastructure`; `business` MUST NOT depend on `infrastructure`. The migration MUST NOT introduce cycles between the three areas. *(EN003 §5; VC-006)*
- **FR-008**: `domain` code MUST NOT depend on Spring, Spring Data, JPA/Hibernate, HTTP/servlet, Kafka/messaging, JDBC/database-client, serialization frameworks, or provider SDKs. Port method signatures MUST NOT contain framework-specific types. *(EN003 §5, §6; VC-004, VC-005)*
- **FR-009**: An automated architecture test suite (ArchUnit) MUST enforce, at minimum: `..domain.. !→ ..business..`, `..domain.. !→ ..infrastructure..`, `..business.. !→ ..infrastructure..`. Where practical it MUST also verify: Spring Data repository interfaces are under `infrastructure.persistence`; JPA entities are under `infrastructure.persistence`; REST controllers are under `infrastructure.api.rest`; REST DTOs are not in `domain` / `business`; `domain` does not depend on Spring Data or JPA; messaging framework classes are under `infrastructure.messaging`. Rules MUST be non-vacuous against the new packages. *(EN003 §16; VC-012)*

#### Domain migration

- **FR-010**: All existing domain types (aggregates, entities-in-the-domain-sense, value objects, enums, domain exceptions, the raw input carrier) MUST be relocated under `portfolio.domain` **without changing their externally observable business semantics** — same validation, same rules, same values. *(EN003 §6; VC-018)*
- **FR-011**: The business-facing outbound ports currently under `application/port/out` (Portfolio persistence, default-investor provider) MUST move to `portfolio.domain.ports`. The domain/business exception currently under `application/port/out` (persistence-not-saved) MUST move to `portfolio.domain.exceptions`. *(EN003 §6)*

#### Business migration

- **FR-012**: The create-Portfolio operation implementation currently under `application` MUST move to `portfolio.business`, keeping its orchestration logic and outcomes identical (idempotency lookup → resolve default investor → build & validate the aggregate → persist atomically → record `PortfolioCreated` + `PositionAdded` structured events → return the result). *(EN003 §7; FD001 FR-032; VC-018)*
- **FR-013**: The placement of the inbound use-case interface and its command/result types is an open technical decision (EN003 §20). The chosen placement MUST keep `business` free of `infrastructure` dependencies and MUST let the REST controller (in `infrastructure`) invoke the business operation. *(EN003 §7, §20)*

#### REST adapter migration

- **FR-014**: The REST controller and the RFC 9457 exception handler MUST move to `infrastructure.api.rest`; the request/response DTOs to `infrastructure.api.rest.dto`; transport↔business/domain mapping to `infrastructure.api.rest.mapper` (extracted from the DTO factory methods / controller helper). The controller MUST only map transport in, delegate to `business`, map result out — it MUST NOT own Portfolio business rules. *(EN003 §8; VC-007)*
- **FR-015**: The external OpenAPI contract MUST be preserved exactly — no operation, schema, field name, status code, header, or error-body change. The JSON representation (field names, null handling, decimal-string and date formatting) MUST be byte-for-byte compatible. The contract test MUST continue to pass. *(EN003 §8; VC-014; DR-018, DR-019; FD001 FR-036)*

#### Persistence migration to Spring Data JPA

- **FR-016**: Portfolio relational persistence MUST migrate from `JdbcClient` / embedded SQL to **Spring Data JPA**: JPA `@Entity` classes under `infrastructure.persistence.entity`, a Spring Data repository (extending a Spring Data JPA repository type) under `infrastructure.persistence.repository`, an explicit domain↔entity mapper under `infrastructure.persistence.mapper`, and a persistence adapter that implements the `portfolio.domain.ports` persistence port. *(EN003 §9; VC-008)*
- **FR-017**: JPA annotations and JPA entities MUST remain in `infrastructure.persistence`. Domain models MUST NOT be turned into JPA entities or carry JPA annotations. Domain↔persistence conversion MUST be explicit. *(EN003 §9, §10; VC-009)*
- **FR-018**: Ordinary Portfolio persistence MUST NOT embed general-purpose SQL through `JdbcClient` / `JdbcTemplate` / direct JDBC / native queries. Spring Data JPA MUST use derived repository methods, standard persistence operations, and specifications/JPQL only where justified. EN003 MUST NOT introduce a direct-SQL exception unless it is strictly required to preserve an existing behavior that cannot reasonably be done with JPA — and any such exception MUST be isolated in `infrastructure` and documented. *(EN003 §11; ADR-003 "Relational Persistence")*
- **FR-019**: The JPA mapping MUST preserve, at minimum: Portfolio identity, Investor identity, Portfolio name, Portfolio status, creation timestamp, idempotency-key behavior, Position identity, `ticker + market`, quantity, currency, optional initial purchase date, optional average purchase price, aggregate persistence semantics (all-or-nothing), and every current relational constraint. It MUST NOT weaken domain validation or database constraints. *(EN003 §10; VC-010)*
- **FR-020**: Aggregate persistence MUST set relationship, cascade, orphan-removal, and fetch strategy **deliberately** (not rely on accidental JPA defaults). Fetch strategy MUST be explicit wherever it affects aggregate reconstruction or performance. *(EN003 §10)*
- **FR-021**: The idempotency-key race behavior (concurrent identical submission → one Portfolio, second call returns the existing one; 201 on create, 200 + `Idempotency-Replayed` on replay) MUST be preserved with the JPA adapter (e.g. handling the unique-constraint violation and re-reading). Nothing partial MUST be persisted on any failure. *(EN003 §10; FD001 FR-023 / FR-031a; VC-010)*

#### Flyway & schema

- **FR-022**: Flyway MUST remain the schema-evolution mechanism. Hibernate/JPA schema auto-generation (`ddl-auto`) MUST NOT be used to create or evolve the schema (it MUST be `none` or `validate`). The existing `V1` / `V2` migrations and the `portfolio` / `position` / `investor` schema MUST be reused. *(EN003 §12; VC-011)*
- **FR-023**: EN003 MUST NOT redesign tables to simplify JPA. If a concrete mapping incompatibility requires a schema change, it MUST be a new **forward** Flyway migration, minimal, and explicitly documented — not a redesign and not an edit of an applied migration. *(EN003 §12; §3 Out of Scope)*

#### Build tool

- **FR-024**: The backend MUST build and test with **Maven** (already the case — this backend has no Gradle). A pinned **Maven wrapper** (`mvnw`, `mvnw.cmd`, `.mvn/`) MUST be added so a clean checkout builds without a host-installed Maven. The backend Dockerfile and any build commands in the lifecycle scripts MUST use Maven and stay consistent with the wrapper. *(EN003 §14, §20; VC-001, VC-015; ADR-003 "Build Tool"; technology-policy "Spring Build Tool: Maven REQUIRED")*

#### Containerization & lifecycle

- **FR-025**: The platform MUST remain fully containerized per EN002. `start.sh`, `stop.sh`, `e2e.sh`, and `compose.yaml` MUST remain the canonical runtime/test entry points. The migration MUST NOT restore host-based Spring execution as the canonical platform runtime. The backend container image MUST continue to build. *(EN003 §14; VC-015, VC-016)*

#### Testing & regression

- **FR-026**: All existing backend tests — unit, domain, Spring integration, PostgreSQL Testcontainers integration, contract, and architecture — MUST be adapted for the package/class/type changes and MUST continue to pass **with their assertions intact** (no assertion removed or weakened). The coverage gate (DoD §5) MUST still pass. *(EN003 §15; VC-013)*
- **FR-027**: The persistence integration tests MUST verify, against real Testcontainers PostgreSQL, that the JPA adapter preserves: atomic aggregate write (all Positions or none on failure), the unique constraints, the idempotency-key resolution, and exact-decimal round-trip for quantity and price. Testcontainers MUST be used (no mocked database). *(EN003 §15; VC-013; testing-strategy Testcontainers policy)*
- **FR-028**: The **FD001 Create Portfolio Playwright E2E test** MUST pass against the fully containerized, refactored backend (`./e2e.sh` exits 0). **EN003 MUST NOT be accepted, closed, or marked Completed while `FD001-create-portfolio.spec.ts` is missing or failing.** A passing lower-level suite does not override this gate. *(EN003 §17; VC-017; FD001 §13 / §16)*

#### Documentation

- **FR-029**: Implementation documentation affected by the migration MUST be updated: the backend `README`, build instructions, developer commands, package examples, test instructions, and any implementation-side architecture references — to describe the ADR-003 `domain / business / infrastructure` module structure and the Spring Data JPA persistence pattern. *(EN003 §19; VC-002, VC-003, VC-008)*
- **FR-030**: Human-governed architecture documents (`product/architecture/architecture.md`, `architecture-rules.md`, `technology-policy.md`) MUST NOT be silently edited or reinterpreted by the migration — they are already updated by ADR-003. If the implementation reveals a needed change to them, it MUST be surfaced for human approval. *(EN003 §19; constitution I)*

#### Scope guardrails

- **FR-031**: EN003 MUST NOT introduce or change any Portfolio (or other) business behavior, business rule, acceptance criterion, or API operation; MUST NOT add instrument-catalog behavior, authentication/authorization, Kafka, a new backend service, service extraction, a new persistence technology, or a Java/Spring major-version upgrade (unless strictly required by the migration and separately approved); MUST NOT redesign the database schema beyond a documented minimal JPA-mapping-compatibility migration. Frontend behavior MUST NOT change except as strictly required to preserve existing integration. *(EN003 §3 Out of Scope; VC-018)*
- **FR-032**: Any material deviation from ADR-003 discovered during implementation MUST be surfaced for human approval before proceeding — the open items in EN003 §20 are *technical* decisions, not permission to change the approved architecture. *(EN003 §20, §21; constitution IV)*

### Key Entities

*No new business entities.* The domain model (`Portfolio` aggregate, `Position`, value objects,
enums) is **relocated, not redefined**. EN003 introduces **infrastructure-only** persistence types
— JPA entities (`PortfolioEntity`, `PositionEntity`, an investor entity) that mirror the existing
`portfolio` / `position` / `investor` tables and are mapped to/from the domain model. These are
persistence models, never domain models, and never appear in the API contract (DR-018).

### Traceability to Enabler Verification Criteria

| Enabler VC | Description | Covered by |
|---|---|---|
| VC-001 | Backend builds & tests with Maven | US4 (AS1, AS2); FR-024 |
| VC-002 | Business code organized by functional module | US1 (AS1); FR-001 |
| VC-003 | Each module uses `domain` / `business` / `infrastructure` | US1 (AS1); FR-002, FR-003 |
| VC-004 | Domain has no Spring Data / JPA / REST / Kafka / JDBC / infra deps | US1 (AS3), US2 (AS2); FR-008, FR-017 |
| VC-005 | Business-facing ports under `domain.ports` | US1 (AS5); FR-011 |
| VC-006 | Business depends on domain, not infrastructure | US1 (AS2); FR-007 |
| VC-007 | Adapters under the standard infrastructure packages | US1 (AS1), US2 (AS1); FR-005, FR-014, FR-016 |
| VC-008 | Portfolio persistence uses Spring Data JPA (not JdbcClient) | US2 (AS1); FR-016, FR-018 |
| VC-009 | JPA annotations/entities stay in infrastructure persistence | US2 (AS2); FR-017 |
| VC-010 | Persistence / transaction / constraint / idempotency semantics preserved | US2 (AS3–AS7), US3 (AS6); FR-019, FR-020, FR-021 |
| VC-011 | Flyway still owns schema evolution | US2 (AS6); FR-022, FR-023 |
| VC-012 | ArchUnit enforces dependency direction + placement | US1 (AS2, AS3); FR-009 |
| VC-013 | Applicable PostgreSQL integration tests pass on Testcontainers | US3 (AS1); FR-026, FR-027 |
| VC-014 | Existing REST/OpenAPI behavior remains compatible | US3 (AS1, AS2); FR-015 |
| VC-015 | Backend container builds from the Maven project | US3 (AS5), US4 (AS2); FR-024, FR-025 |
| VC-016 | `start.sh` / `stop.sh` operate the containerized platform | US3 (AS4); FR-025 |
| VC-017 | FD001 Create Portfolio E2E passes | US3 (AS3); FR-028 |
| VC-018 | No new Portfolio business behavior | US1–US4 (all); FR-010, FR-012, FR-031 |

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the pre-migration backend automated tests pass after the migration, with **zero** assertions removed or weakened (tests changed only for package/class/type references).
- **SC-002**: The FD001 Create Portfolio browser E2E test passes against the containerized refactored backend — `./e2e.sh` exits 0 — in 100% of runs on a healthy environment.
- **SC-003**: **Zero** types under `…core.<module>.domain…` reference Spring, Spring Data, JPA/Hibernate, HTTP/servlet, Kafka, or JDBC (verified by the architecture test suite).
- **SC-004**: **Zero** `JdbcClient` / `JdbcTemplate` / embedded-SQL / native-query statements remain in the Portfolio ordinary-persistence path; any documented exception is isolated in `infrastructure` and named in the plan.
- **SC-005**: The architecture test suite fails on a deliberately introduced `domain → infrastructure` (or `business → infrastructure`) dependency, and every architecture rule is non-vacuous against the new packages.
- **SC-006**: For the same request inputs, the `POST /api/portfolios` responses (status, headers, JSON body) are identical before and after the migration for 100% of the contract-test and full-slice-IT scenarios; the OpenAPI file is unchanged.
- **SC-007**: `./mvnw verify` succeeds from a clean checkout with no host-installed Maven; the backend container image builds; `./start.sh` brings the platform healthy and `./stop.sh` stops it.
- **SC-008**: The database schema after the migration is identical to before (same tables, columns, constraints, Flyway history) except for at most one documented minimal forward migration required for JPA mapping compatibility; `ddl-auto` is `none` or `validate` and an integration check confirms Hibernate did not alter the schema.
- **SC-009**: Idempotency, atomic-aggregate-write, and exact-decimal behavior are proven equivalent by the persistence integration tests (same scenarios, same assertions) running on Testcontainers PostgreSQL.
- **SC-010**: A review of the change set finds **no** new API operation, **no** new business rule, **no** authentication/authorization change, **no** new runtime/broker/database technology, and **no** Java/Spring major-version change (VC-018).
- **SC-011**: 100% of the enabler's verification criteria (VC-001 … VC-018) have associated executable or inspectable evidence.

---

## Assumptions

- **A1 — Build tool starting point**: this backend is **already Maven** (`pom.xml`, no Gradle), and the EN002 Dockerfile already builds it with Maven. The ADR-003 "Gradle → Maven" migration is therefore **N/A** here; EN003's build work is limited to adding a pinned Maven wrapper (`mvnw` / `.mvn/`) and keeping commands consistent.
- **A2 — Runtime versions unchanged**: Java 21 and Spring Boot 3.5.x stay as they are (EN001 decisions). `spring-boot-starter-data-jpa` is added; whether `spring-boot-starter-jdbc` is kept (Flyway / test-support may want a `DataSource` / `JdbcTemplate`) is a plan decision.
- **A3 — Old convention removed**: the EN001 `com.myfinaimanager.core.platform.*` empty convention packages are deleted (superseded by ADR-003). The "example" a future contributor follows is the migrated `portfolio` module plus the ArchUnit rules — not a set of empty placeholder packages.
- **A4 — Inbound use-case placement**: the driving/use-case interface and its command/result types default to the `business` package (the business operation's own API); the `infrastructure` REST controller depends on `business`, which ADR-003 allows. `domain.ports` is reserved for outbound/external-capability ports. (Open technical decision — EN003 §20.)
- **A5 — Persistence-not-saved exception**: it is a domain/business-observable outcome (mapped to HTTP 503), so it lives in `portfolio.domain.exceptions`.
- **A6 — Schema reuse**: JPA entities map to the existing `portfolio` / `position` / `investor` tables. Natural keys / unique constraints (`portfolio.idempotency_key`, `position (portfolio_id, ticker, market)`), `NUMERIC` columns, and CHECK constraints are kept. A schema change is made only if a concrete JPA-mapping incompatibility forces it, as a documented minimal forward migration.
- **A7 — Idempotency implementation**: the current JDBC adapter catches a duplicate-key error and re-reads within a programmatic transaction. The JPA adapter reproduces this by handling `DataIntegrityViolationException` (or equivalent) on the idempotency-key unique constraint and re-reading — same observable result. (Open technical detail — EN003 §20.)
- **A8 — Test-support SQL**: integration-test cleanup that uses `JdbcClient` / `DELETE FROM …` is test infrastructure and may remain (or be replaced with repository deletes at the plan's discretion) — the "no ad-hoc SQL" rule targets **application** persistence.
- **A9 — `ddl-auto`**: set to `none` (or `validate`); Hibernate never creates or evolves the schema. An IT asserts the Flyway history and table structure are unchanged.
- **A10 — Behavior preservation over cleanup**: if the migration reveals a latent bug, EN003 fixes it **only** to keep the pre-migration observable behavior, and flags it. Any change that would alter observable behavior is out of scope and requires human approval.
- **A11 — Mapper style**: hand-written mappers vs a mapping library (e.g. MapStruct) is an open technical decision (EN003 §20); hand-written is the low-dependency default unless the plan justifies otherwise. A mapping library, if chosen, is a build-time-only dependency and must not appear in `domain`.
- **A12 — Bean wiring**: because ADR-003 permits Spring annotations in `business`, the current manual `@Configuration` bean wiring (`bootstrap/PortfolioBeanConfiguration`) may be simplified to component scanning (`@Service` on the business operation, a small config for `Clock`). Placement of any remaining configuration is a plan decision; it belongs in `infrastructure` or a module bootstrap package, never in `domain`.

## Dependencies

- **EN001 — Bootstrap Executable Platform**: provides the `core-service`, PostgreSQL + Flyway, and the ArchUnit setup this enabler restructures.
- **FD001 — Create Investment Portfolio**: provides the backend code being migrated (domain, application service, JDBC persistence, REST adapter, contract) and its test suites — all of which are the regression evidence for EN003.
- **EN002 — Establish Containerized End-to-End Testing Foundation**: provides the containerized platform, `e2e.sh`, and the FD001 Playwright E2E test that is EN003's **mandatory closure gate** (VC-017 / FR-028).
- **ADR-001 — Initial Backend Topology**: unchanged — `core-service` stays the single backend deployable; module-first packaging does not split it.
- **ADR-003 — Standard Spring Backend Architecture**: the governing decision. EN003 implements it for the existing backend and MUST NOT deviate from it without human approval.
- **`product/architecture/technology-policy.md`** (updated alongside ADR-003): *Spring Build Tool = Maven (REQUIRED)*, *Spring Relational Persistence = Spring Data JPA / Hibernate (REQUIRED by default)*, *Direct JDBC = CONDITIONAL*.
- Governance: `product/architecture/architecture.md`, `architecture-rules.md`; `product/engineering/development-rules.md`, `testing-strategy.md`, `definition-of-done.md`.

## Out of Scope

Carried from EN003 §3 "Out of Scope" plus derived boundaries:

- New Portfolio behavior, new Portfolio API operations, instrument-catalog behavior.
- Authentication / authorization changes; Kafka introduction; new backend services; service extraction.
- Database schema **redesign** (only a documented minimal JPA-mapping-compatibility forward migration is allowed, if unavoidable).
- Any new persistence technology; any new runtime, broker, cache, or major infrastructure component.
- Java / Spring **major-version upgrades** (unless strictly required by the migration and separately approved).
- Frontend behavior changes other than those strictly required to preserve the existing frontend↔backend integration.
- New CI/CD platform or deployment topology; changes to the EN002 containerization model or the canonical `start.sh` / `stop.sh` / `e2e.sh` interface.
- Editing or reinterpreting human-governed architecture documents (already updated by ADR-003).
