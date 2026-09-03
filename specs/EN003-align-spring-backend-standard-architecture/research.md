# Research — EN003 Align Spring Backend with Standard Architecture (Phase 0)

Format per decision: **Decision / Rationale / Alternatives considered**. ADR-003 already fixes the
big choices (three-area module structure, `infrastructure → business → domain`, Spring Data JPA as
the persistence standard, Maven, ArchUnit enforcement, Flyway ownership). This document turns them
into concrete, buildable decisions (D1–D14) and resolves the enabler's §20 open items
(**OD-EN003-1…12**).

No decision changes any externally observable behavior (FR-031). Where a mapping/adapter choice
could shift behavior, the choice is the one that reproduces the current JDBC-path outcome, and a
rewritten integration test asserts it.

---

## D1 — Module + three-area package layout (OD-EN003-1, ADR-003)

**Decision**: relocate everything under `com.myfinaimanager.core.portfolio` to:

```text
portfolio/
├── domain/
│   ├── model/       — Portfolio, Position, NewPosition, PortfolioId, PositionId, InvestorId,
│   │                  PortfolioName, PortfolioStatus, Ticker, Market, Currency, Money, Quantity,
│   │                  InstrumentRef, ValidationCode, Violation
│   ├── ports/       — PortfolioRepository, DefaultInvestorProvider
│   └── exceptions/  — PortfolioValidationException, PortfolioNotSavedException
├── business/        — CreatePortfolioUseCase, CreatePortfolioCommand, CreatePortfolioResult,
│                      CreatePortfolioService
└── infrastructure/
    ├── api/
    │   ├── rest/        — CreatePortfolioController, PortfolioExceptionHandler
    │   │   └── dto/     — CreatePortfolioRequest (+PositionInput), CreatePortfolioResponse (+PositionResponse)
    │   └── mapper/      — CreatePortfolioRequestMapper, PortfolioResponseMapper
    ├── persistence/
    │   ├── entity/      — PortfolioEntity, PositionEntity, InvestorEntity
    │   ├── repository/  — PortfolioJpaRepository, InvestorJpaRepository
    │   ├── mapper/      — PortfolioPersistenceMapper
    │   ├── PortfolioPersistenceAdapter
    │   └── JpaDefaultInvestorProvider
    └── config/         — PortfolioModuleConfiguration
```

Delete `com.myfinaimanager.core.platform.*` (EN001 empty placeholders) and
`com.myfinaimanager.core.bootstrap.*` (its only class moves to `…infrastructure.config`).

**Rationale**: verbatim from ADR-003 "Modular Monolith Structure" + EN003 §4. Package-only moves —
class bodies unchanged except imports and (for `Portfolio`/`Position`) the package declaration.
`CoreServiceApplication` already component-scans `com.myfinaimanager.core`, so the new packages are
picked up with no scan-config change.

**Alternatives considered**:
- *Keep `application` / `adapter` names* — rejected: ADR-003 is prescriptive; the whole point of
  EN003 is to stop reproducing that structure.
- *Sub-package `domain.model` flat vs grouped (e.g. `model/vo`, `model/aggregate`)* — ADR-003 shows
  a flat `model/`; keep flat.

---

## D2 — Inbound use-case interface placement (OD-EN003-2)

**Decision**: `CreatePortfolioUseCase` (interface), `CreatePortfolioCommand`, `CreatePortfolioResult`
live in `…portfolio.business`. `CreatePortfolioService` implements the interface and is annotated
`@Service`. The REST controller (`infrastructure`) depends on `business` (`infrastructure → business`
is allowed).

**Rationale**: ADR-003's `domain.ports` is described for **outbound / external-capability** ports
(persistence, remote services, market data, messaging, AI, storage). The *driving* use-case
interface is the business layer's own API, not an external-dependency port — it belongs in
`business`. Keeping the interface (rather than the controller calling the concrete `@Service`)
preserves the existing test seam (`CreatePortfolioServiceTest`, the `@WebMvcTest` mock) with zero
behavior change.

**Alternatives considered**:
- *Put it in `domain.ports`* — misplaces a driving port among outbound ports; and it would force
  `domain` to reference `NewPosition`-carrying command types that are arguably application-shaped.
  (`NewPosition` itself stays in `domain.model` — it is raw domain input, already there.)
- *Drop the interface, controller → `@Service` directly* — valid and lighter, but changes two test
  classes' wiring for no behavioral gain. Deferred as an optional later cleanup.

---

## D3 — Spring Data JPA aggregate mapping (OD-EN003-3, OD-EN003-5, ADR-003 "Relational Persistence")

**Decision**: three infrastructure JPA entities mapped to the **existing** tables:

- **`PortfolioEntity`** (`@Entity @Table(name="portfolio")`):
  `@Id UUID id`; `UUID investorId` (`@Column(name="investor_id", nullable=false)` — a plain FK
  column, not a `@ManyToOne`); `String name`; `String status`; `String idempotencyKey`
  (`@Column(name="idempotency_key", nullable=false, unique=true)`); `Instant createdAt`
  (`@Column(name="created_at", nullable=false)`);
  `@OneToMany(mappedBy="portfolio", cascade=CascadeType.ALL, orphanRemoval=true) @OrderBy("id ASC") List<PositionEntity> positions`.
  A protected no-arg constructor + an all-args constructor (or builder) for the mapper.
- **`PositionEntity`** (`@Entity @Table(name="position")`):
  `@Id UUID id`;
  `@ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="portfolio_id", nullable=false) PortfolioEntity portfolio`;
  `String ticker`; `String market`; `BigDecimal quantity` (`@Column(nullable=false)`);
  `String currency`; `LocalDate initialPurchaseDate` (nullable); `BigDecimal averagePurchasePrice`
  (nullable); `String averagePurchasePriceCurrency` (nullable).
- **`InvestorEntity`** (`@Entity @Table(name="investor")`): `@Id UUID id`; `String displayName`;
  `String preferredCurrency`; `Instant createdAt`.

`equals`/`hashCode` on entities use the `id` only (assigned UUID, never null after construction).

**Rationale**: mirrors EN003 §9 target and the `V2__portfolio.sql` schema exactly.
`orphanRemoval=true` + `cascade=ALL` make a single `save(portfolioEntity)` persist the whole
aggregate — matching the current "insert portfolio then each position in one tx". `@OrderBy("id ASC")`
reproduces the JDBC `ORDER BY id` used for deterministic aggregate reconstruction. Plain
`investorId` column (not `@ManyToOne`) is the minimal mapping: the domain only carries `InvestorId`,
FD001 never navigates portfolio→investor, and the DB FK still enforces referential integrity.
`BigDecimal` with **no** `precision`/`scale` maps to `numeric` and preserves the exact input scale
(FD001 SC-007) — verified by the rewritten IT.

**Alternatives considered**:
- *`@ManyToOne InvestorEntity` on `PortfolioEntity`* — more "ORM-idiomatic" but adds a lazy
  association and a fetch concern for a value the domain treats as an id. Not needed.
- *`@Enumerated(EnumType.STRING) PortfolioStatus status`* — couples the entity to the domain enum
  (allowed: `infrastructure → domain`), but `String status` + mapper is simpler and the `CHECK
  (status IN ('ACTIVE'))` constraint already guards values. Keep `String`.
- *Embeddables for value objects (`@Embedded Money`, `@Embedded Quantity`)* — would drag JPA
  annotations toward the domain or require duplicate embeddable types; the explicit mapper (D4) is
  cleaner and keeps `domain` 100 % JPA-free (VC-009).

---

## D4 — Explicit domain ⇄ persistence mapping, domain stays JPA-free (OD-EN003-4, FR-017, VC-004/VC-009)

**Decision**: hand-written `PortfolioPersistenceMapper` in `…infrastructure.persistence.mapper`:

- `toEntity(Portfolio, idempotencyKey)` → `PortfolioEntity` with its `PositionEntity` children
  (each child's `portfolio` back-reference set; `averagePurchasePriceCurrency` set to the
  position's currency when a price is present, else null — preserving the `V2` `position_price_*`
  CHECK constraints).
- `toDomain(PortfolioEntity)` → `Portfolio.reconstitute(...)` with `Position.reconstitute(...)` for
  each child, rebuilding the value objects (`PortfolioName`, `InstrumentRef`, `Quantity`,
  `Currency`, `Money`, ids). Same construction the JDBC `loadAggregate` does today.

REST mappers (`…infrastructure.api.rest.mapper`): `CreatePortfolioRequestMapper.toCommand(request, key)`
(the current controller `toCommand` helper) and `PortfolioResponseMapper.toResponse(portfolio)`
(the current `CreatePortfolioResponse.from` — same `toPlainString()` / `LocalDate::toString` /
`null` formatting, FR-015).

**Rationale**: EN003 §9 requires explicit mapping; ADR-003 forbids JPA in the domain. Hand-written
mappers add no dependency, are trivially unit-testable, and make the domain↔row translation
reviewable. The response mapper preserving exact formatting is guarded by the contract test +
full-slice ITs.

**Alternatives considered**:
- *MapStruct* — generates mappers at build time; a `provided`/annotation-processor dependency, no
  runtime footprint, and it must never leak into `domain`. Reasonable, but for three small
  aggregates hand-written mappers are lower-ceremony and easier to keep behavior-identical. If the
  team prefers MapStruct the plan can switch — it stays a build-only dependency.
- *Constructor expressions / DTO projections in the repository* — bypasses the domain model;
  rejected (the port returns `Portfolio`, not a projection).

---

## D5 — Repositories & aggregate reconstruction fetch (OD-EN003-6, FR-020)

**Decision**:
- `PortfolioJpaRepository extends JpaRepository<PortfolioEntity, UUID>` with:
  `@EntityGraph(attributePaths = "positions") Optional<PortfolioEntity> findByIdempotencyKey(String idempotencyKey);`
  (derived method + entity graph). Also `@EntityGraph(attributePaths = "positions") Optional<PortfolioEntity> findWithPositionsById(UUID id);` if needed by an IT.
- `InvestorJpaRepository extends JpaRepository<InvestorEntity, UUID>` with a method to fetch the
  single seeded investor deterministically — `Optional<InvestorEntity> findTopByOrderByCreatedAtAscIdAsc();`
  (derived, reproduces the JDBC `ORDER BY created_at, id LIMIT 1`).
- The `positions` association is `LAZY`; every finder that feeds the mapper uses `@EntityGraph` so
  the collection is initialized before the entity leaves the persistence context.
- `spring.jpa.open-in-view: false` (OD-EN003-8) — no accidental lazy loading in the web layer.

**Rationale**: derived query methods per ADR-003 SQL policy (no JPQL needed here). `@EntityGraph`
is the standard, explicit way to make aggregate reconstruction work outside a transaction without
`join fetch` string queries or N+1. `open-in-view: false` is a Spring best practice that also
guarantees the mapper — not a view render — is where the collection is read.

**Alternatives considered**:
- *`EAGER` on the `@OneToMany`* — global eager fetch is an anti-pattern (every portfolio load
  drags positions even when unwanted); `@EntityGraph` per query is targeted.
- *`@Query("select p from PortfolioEntity p join fetch p.positions where p.idempotencyKey = :k")`* —
  works, but a JPQL string where a derived method + entity graph is cleaner and ADR-003 prefers
  derived methods.

---

## D6 — Atomic aggregate write + idempotency-race handling (OD-EN003-7, FR-021, VC-010)

**Decision**: `PortfolioPersistenceAdapter` keeps the current programmatic-transaction shape:

```text
save(portfolio, idempotencyKey):
  try:
    transactionTemplate.execute:
        entity = mapper.toEntity(portfolio, idempotencyKey)
        portfolioJpaRepository.saveAndFlush(entity)     # flush forces the INSERTs inside the tx
    return portfolio
  catch DataIntegrityViolationException e:              # tx already rolled back
    return findByIdempotencyKey(idempotencyKey)         # fresh tx (@EntityGraph) → mapper.toDomain
             .orElseThrow(() -> new PortfolioNotSavedException("…constraint violation", e))
  catch DataAccessException e:
    throw new PortfolioNotSavedException("portfolio could not be saved", e)

findByIdempotencyKey(idempotencyKey):
  transactionTemplate (read-only) → portfolioJpaRepository.findByIdempotencyKey(...) → map to domain
```

`saveAndFlush` (not `save`) is essential — it makes Hibernate emit the INSERTs and surface a
unique-constraint violation **before** the method returns, so the `catch` runs.
`DataIntegrityViolationException` is Spring's translation of both the Hibernate
`ConstraintViolationException` and the JDBC `SQLIntegrityConstraintViolationException` — it covers
the idempotency-key race **and** the position `(portfolio_id, ticker, market)` uniqueness
defense-in-depth, exactly like the JDBC `DuplicateKeyException` path.

**Rationale**: reproduces the FD001 behavior bit for bit — 201 on create, 200 + `Idempotency-Replayed`
on a concurrent duplicate, `PortfolioNotSavedException` → 503 on any other integrity/transient
failure, nothing partial persisted. The existing `CreatePortfolioIdempotencyIT`,
`JdbcPortfolioRepositoryIT` "rolls back entirely" and "repeated idempotency key resolves to the
existing portfolio" cases are ported to `PortfolioPersistenceAdapterIT` with the same assertions.

**Alternatives considered**:
- *`@Transactional` on the adapter method + a self-invocation for the re-read* — self-invocation
  doesn't go through the proxy; splitting into two beans adds ceremony. The current
  `TransactionTemplate` pattern already solves the "re-read on a clean connection" problem.
- *Pre-check `findByIdempotencyKey` before insert only* — already done in the **business** layer
  (`CreatePortfolioService`); the adapter's catch is the race safety-net and must stay.
- *`repository.existsByIdempotencyKey` then insert* — TOCTOU race; the unique constraint + catch is
  the correct pattern.

---

## D7 — Hibernate DDL, schema integrity, Flyway (OD-EN003-8, FR-022, VC-011)

**Decision**: `spring.jpa.hibernate.ddl-auto: none`. Flyway (`V1`, `V2`) is unchanged and remains
the sole schema owner. Add `spring.jpa.properties.hibernate.jdbc.time_zone: UTC` (so `Instant`
`created_at` round-trips in UTC like the JDBC `Timestamp.from(...)` path). A new
**`SchemaIntegrityIT`** (Testcontainers) asserts: Flyway `flyway_schema_history` has the `V1` +
`V2` rows; the `portfolio_idem_key_uk` and `position_instrument_uk` constraints exist; the
`position` CHECK constraints exist; Hibernate did **not** create/alter any table (row/constraint
counts unchanged from a Flyway-only baseline).

**Rationale**: `validate` is attractive but noisy against `V2`'s unbounded `NUMERIC` columns and
`CHAR(3)` currency columns (Hibernate expects `numeric(p,s)` / `varchar`), producing false
failures or forcing `columnDefinition` clutter. `none` + an explicit integrity IT gives the same
safety with no friction. EN003 §12 forbids Hibernate schema generation replacing Flyway — `none`
enforces that.

**Alternatives considered**:
- *`validate`* — rejected for the `NUMERIC`/`CHAR` friction above; revisit if a future migration
  makes the columns precisely typed.
- *No integrity IT, trust `none`* — weaker; the IT is cheap and doubles as regression evidence for
  VC-011.

---

## D8 — ArchUnit rules for ADR-003 §16 (FR-009, VC-012)

**Decision**: rewrite `HexagonalArchitectureRulesTest` → `StandardArchitectureRulesTest`
(`@AnalyzeClasses(packages = "com.myfinaimanager.core", importOptions = DoNotIncludeTests.class)`):

Mandatory (non-vacuous — `portfolio` now has classes in all three areas):
- `noClasses().that().resideInAPackage("..portfolio.domain..").should().dependOnClassesThat().resideInAnyPackage("..portfolio.business..", "..portfolio.infrastructure..")`
- `noClasses().that().resideInAPackage("..portfolio.business..").should().dependOnClassesThat().resideInAPackage("..portfolio.infrastructure..")`
- `noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..", "com.fasterxml.jackson..", "java.sql..", "javax.sql..", "org.apache.kafka..")`
- generalize the first two to `..<module>.domain..` / `..<module>.business..` via a package pattern so future modules are covered automatically.

Placement (where practical — ADR-003 §16):
- JPA `@Entity` classes reside in `..infrastructure.persistence.entity..`
- Spring Data `Repository` interfaces reside in `..infrastructure.persistence.repository..`
- classes annotated `@RestController` reside in `..infrastructure.api.rest..`
- `*Mapper` classes under `..infrastructure.api..` reside in `..infrastructure.api.rest.mapper..`
  (ADR-003 amendment 2026-09-02 — a REST mapper is part of the REST adapter)
- REST DTO types (`..api.rest.dto..`) are not referenced by `..domain..` or `..business..`
- no class in `..domain..` depends on `org.springframework.data..` or `jakarta.persistence..`
- (`messaging` rule declared with `allowEmptyShould(true)` — no messaging adapter yet)

The suite has **11** `@ArchTest` rules; all are non-vacuous except the `messaging` rule.

Also keep a **freeze/negative check** in the task list: temporarily introduce a
`domain → infrastructure` import and confirm the suite fails (SC-005).

**Rationale**: EN003 §16 verbatim, expressed as ArchUnit. Generalizing by `..<module>..` pattern
means the next Spring module inherits enforcement for free (ADR-003 intent).

**Alternatives considered**:
- *`layeredArchitecture()` DSL* — ArchUnit's `Architectures.layeredArchitecture()` is expressive
  but its "layer" model is global; the module-first + three-area shape maps more directly to
  explicit `noClasses().that()...should()` rules.
- *Keep the old rule names* — the file is rewritten anyway; a clearer name (`StandardArchitectureRulesTest`)
  matches ADR-003 terminology.

---

## D9 — pom.xml changes (OD-EN003-9, OD-EN003-12, FR-024)

**Decision**:
- **Add** `org.springframework.boot:spring-boot-starter-data-jpa` (version via the Spring Boot BOM
  3.5.6 → Hibernate ORM 6.6.x).
- **Remove** the explicit `spring-boot-starter-jdbc` dependency — `data-jpa` brings `spring-jdbc`
  and the `DataSource` autoconfig transitively; `JdbcClient` autoconfiguration stays available for
  test-support.
- **Keep** everything else (`web`, `actuator`, `flyway-core` + `flyway-database-postgresql`,
  `postgresql`, all test deps, ArchUnit 1.3.0, swagger-request-validator 2.44.1, Failsafe
  `api.version=1.44`, JaCoCo `check` at ≥ 90 % line + branch).
- **JaCoCo `<excludes>`**: keep `com/myfinaimanager/core/CoreServiceApplication.class`; replace
  `com/myfinaimanager/core/bootstrap/**` with `com/myfinaimanager/core/portfolio/infrastructure/config/**`.
  JPA entities, mappers, repositories, and the persistence adapter stay **in** coverage — the
  rewritten ITs must exercise them.

**Rationale**: minimal, policy-aligned. Data JPA is now REQUIRED-by-default (`technology-policy.md`).
Dropping the redundant jdbc starter keeps the dependency set honest. Config classes carry no
behavior and are legitimately excluded; mapping code is behavior and is not.

**Alternatives considered**:
- *Keep `spring-boot-starter-jdbc` explicitly* — harmless but misleading (implies deliberate direct
  JDBC use, which ADR-003 now makes conditional).
- *Add `hibernate-validator` / Bean Validation on entities* — out of scope; domain validation is
  already complete in `Portfolio.create`, and DB CHECK constraints are the persistence guard.

---

## D10 — application.yml changes (OD-EN003-8)

**Decision**: add under `spring:`

```yaml
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: none
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
```

Everything else unchanged (datasource env vars, Flyway retry config, `mvc.problemdetails.enabled`,
actuator `health`-only exposure, ECS structured logging).

**Rationale**: the three settings that matter for a behavior-identical JPA migration — no OSIV, no
Hibernate DDL, UTC timestamps to match the current `Instant`/`Timestamp` handling.

**Alternatives considered**: `spring.jpa.show-sql` / `format_sql` — dev-only noise, leave off (ECS
structured logging is the policy).

---

## D11 — `Clock` bean + config (OD-EN003-10)

**Decision**: new `com.myfinaimanager.core.portfolio.infrastructure.config.PortfolioModuleConfiguration`
(`@Configuration`) with `@Bean Clock clock() { return Clock.systemUTC(); }`. `CreatePortfolioService`
becomes `@Service` (constructor-injected `PortfolioRepository`, `DefaultInvestorProvider`, `Clock` —
all beans now). Delete `com.myfinaimanager.core.bootstrap.PortfolioBeanConfiguration` and the
`bootstrap` package.

**Rationale**: ADR-003 permits Spring annotations in `business`; component scanning removes the
manual `@Bean` factory. The `Clock` still needs a bean — a tiny module config in `infrastructure`
is the right home (config is infrastructure). `Clock` is only used by the domain via a method
parameter (`Portfolio.create(..., Clock)`) — the domain never imports Spring.

**Alternatives considered**:
- *`@Bean Clock` on `CoreServiceApplication`* — works, but mixes a portfolio concern into the app
  root; a module config is cleaner and scales to future modules.
- *Inject `Clock` as `Clock.systemUTC()` default in the service* — hurts testability (the tests use
  `Clock.fixed(...)`); keep it a bean.

---

## D12 — Test migration (FR-026, FR-027, VC-013, VC-014, EN003 §15)

**Decision**: adapt every test for packages/types **without weakening assertions**.

| Current test | After |
|---|---|
| `architecture/HexagonalArchitectureRulesTest` | rewritten → `architecture/StandardArchitectureRulesTest` (D8) |
| `portfolio/domain/PortfolioTest`, `…/ValueObjectsTest` | → `portfolio/domain/model/…`; imports only; assertions unchanged |
| `portfolio/application/CreatePortfolioServiceTest` | → `portfolio/business/…`; `@Mock` the `domain.ports`; assertions unchanged (incl. `OutputCaptureExtension` event assertions) |
| `portfolio/adapter/in/web/CreatePortfolioControllerContractTest` | → `portfolio/infrastructure/api/rest/…`; `@WebMvcTest(CreatePortfolioController.class)` + `@Import(PortfolioExceptionHandler.class)`; `@MockitoBean CreatePortfolioUseCase`; contract assertions unchanged |
| `portfolio/adapter/out/persistence/JdbcPortfolioRepositoryIT` | **rewritten** → `portfolio/infrastructure/persistence/PortfolioPersistenceAdapterIT` — `@SpringBootTest` + `PostgresContainerSupport`; autowire the `PortfolioRepository` port (bean = `PortfolioPersistenceAdapter`); same scenarios: persists+reads aggregate exactly (3.250 precision), rolls back entirely on a duplicate-instrument insert, repeated idempotency key resolves to existing, unique constraints present |
| `portfolio/adapter/out/persistence/JdbcDefaultInvestorProviderIT` | **rewritten** → `…/JpaDefaultInvestorProviderIT` — asserts the seeded UUID |
| `portfolio/{AbstractPortfolioIT, CreatePortfolioIT, CreatePortfolioIdempotencyIT, CreatePortfolioAcceptanceIT, CreatePortfolioMultiPositionIT, CreatePortfolioValidationIT, CreatePortfolioOptionalDataIT}` | package/import updates only; `AbstractPortfolioIT` cleanup keeps `JdbcClient` `DELETE FROM …` (test-support — A8) **or** switches to `portfolioJpaRepository.deleteAllInBatch()` (plan's call); assertions unchanged |
| `bootstrap/PlatformIntegrationIT` (EN001) | keep; update imports if it referenced `platform`/`bootstrap`; still boots the context + checks Flyway + `/actuator/health` |
| `support/PostgresContainerSupport` | unchanged |
| **NEW** `portfolio/infrastructure/persistence/SchemaIntegrityIT` | Flyway history + constraints present + Hibernate didn't alter schema (D7) |

**Rationale**: EN003 §15 — the suites are the regression proof; they must move with the code and
keep proving the same things. The two JDBC ITs necessarily become JPA ITs (they test the adapter
implementation) but every *scenario* and *assertion* carries over.

**Alternatives considered**: writing brand-new JPA tests and deleting the old ones — loses the
explicit 1:1 mapping of "this scenario still passes"; port them instead.

---

## D13 — Maven wrapper + Dockerfile (OD-EN003-11, FR-024, VC-001, VC-015)

**Decision**: run `mvn -N wrapper:wrapper -Dmaven=<pinned 3.9.x>` in
`implementation/platform/backend/core-service/` to generate `mvnw`, `mvnw.cmd`, `.mvn/wrapper/`
(`maven-wrapper.properties` pins the distribution URL + SHA). Commit them. Update the backend
`Dockerfile` build stage to `COPY .mvn/ .mvn/` + `COPY mvnw pom.xml ./` then
`RUN ./mvnw -B -q dependency:go-offline` and `RUN ./mvnw -B -q clean package -DskipTests`. `start.sh`
/ `e2e.sh` are unaffected (they call `docker build`; the Dockerfile owns the Maven invocation).
Update `quickstart.md` and any developer docs to use `./mvnw`.

**Rationale**: ADR-003 "Build Tool" expects `pom.xml` + `mvnw` + `mvnw.cmd` + `.mvn/`. The wrapper
makes a clean checkout (and the container build) reproducible without a host Maven. The base image
`maven:3.9-eclipse-temurin-21` still provides Maven, but using `./mvnw` keeps the pinned version
authoritative.

**Alternatives considered**:
- *No wrapper, rely on the container's Maven + host Maven* — leaves the Maven version unpinned;
  ADR-003 explicitly lists the wrapper files.
- *`maven-wrapper` plugin managed in `pom.xml`* — the generated files are the standard artifact;
  keep it simple.

---

## D14 — Behavior-preservation verification approach (FR-028, VC-017, EN003 §17)

**Decision**: the migration is "done" only when, in order:
1. `./mvnw verify` green — Surefire + Failsafe + JaCoCo `check` (≥ 90 % line + branch) + ArchUnit.
2. The ArchUnit deliberate-violation check fails as expected, then is reverted (SC-005).
3. `./start.sh` → all containers healthy; `curl` the `POST /api/portfolios` happy path, replay,
   400, 503 → identical to FD001's recorded responses; `./stop.sh`.
4. Backend container image builds (`docker build implementation/platform/backend/core-service`).
5. **`./e2e.sh` → `FD001-create-portfolio.spec.ts` + `platform-smoke.spec.ts` pass, exit 0** —
   the mandatory closure gate.
6. Frontend `ng test` green (regression — no frontend change expected).

No CI (out of scope). `quickstart.md` enumerates these as VC-mapped scenarios.

**Rationale**: EN003 §15 + §17. A passing lower-level suite does not override the FD001 E2E gate
(§17). The `curl` diff of the four response shapes is the concrete "byte-for-byte" check for
FR-015 / VC-014 beyond the contract test.

**Alternatives considered**: snapshot-testing the JSON responses into files — the contract test +
full-slice ITs + the E2E already cover this; a snapshot harness is extra machinery.

---

## Open questions (reserved for a maintainer — safe defaults recorded)

| ID | Question | Recommended default | Impact if changed |
|----|----------|---------------------|-------------------|
| **OD-EN003-2** | Keep or drop the `CreatePortfolioUseCase` interface | keep, in `…business` | drop → controller depends on the `@Service` directly; update 2 tests |
| **OD-EN003-4** | Hand-written mappers vs MapStruct | hand-written (no dep) | MapStruct → add a build-only annotation processor; must not touch `domain` |
| **OD-EN003-8** | `ddl-auto: none` vs `validate` | `none` + `SchemaIntegrityIT` | `validate` → add `columnDefinition`/precision to entities to satisfy Hibernate |
| **OD-EN003-9** | Keep test-support `JdbcClient` cleanup vs repo `deleteAllInBatch` | keep `JdbcClient` (test infra, A8) | switch → `AbstractPortfolioIT` uses the JPA repo; no behavior impact |
| **OD-EN003-11** | Pinned Maven version for the wrapper | latest 3.9.x at implementation | any supported 3.9.x |
| **OD-EN003-12** | Whether a minimal Flyway `V3` mapping migration is needed | **not expected** — the `V2` schema maps cleanly | if an unavoidable incompatibility appears → one minimal, documented forward migration (FR-023) |

None affect enabler intent, verification criteria, ADR-003, or observable behavior.
