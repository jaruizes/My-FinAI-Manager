---
description: "Task list for EN003 — Align Spring Backend with Standard Architecture"
---

# Tasks: Align Spring Backend with Standard Architecture (EN003)

**Input**: Design documents from `/specs/EN003-align-spring-backend-standard-architecture/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md` (D1–D14 + OD-EN003-1…12), `data-model.md`, `contracts/persistence-port.md`, `quickstart.md`

**Governance**: `.specify/memory/constitution.md` v1.0.0 (I–VIII); **ADR-003** (governing); `product/architecture/{architecture-rules,technology-policy}.md` (updated alongside ADR-003 — Spring Data JPA REQUIRED-by-default, Maven REQUIRED, Direct JDBC CONDITIONAL); `product/engineering/{development-rules,testing-strategy,definition-of-done}.md`.

**Tests**: EN003 is a **behavior-preserving migration** — there is no new deterministic logic to TDD. The existing FD001 suites are **regression evidence** and are *relocated / adapted with assertions intact* (EN003 §15). The two JDBC persistence ITs are *rewritten* for the JPA adapter with the **same scenarios and assertions**; one new `SchemaIntegrityIT` is added. The **FD001 Playwright E2E** is a **mandatory closure gate** (EN003 §17 / VC-017).

> **How EN003 is sliced.** One atomic migration. **Setup** adds the JPA dependency + Maven
> wrapper + config. **US1** (P1) does the whole structural move to `domain / business /
> infrastructure` (JDBC persistence merely relocated) and rewrites ArchUnit — the build stays green
> the whole time. **US2** (P1) swaps the relocated JdbcClient persistence for Spring Data JPA,
> behavior-identical. **US3** (P1) is the full regression + FD001 E2E gate. **US4** (P2) is the
> Maven-standard build + documentation alignment. **No product behavior, API, or schema change.**

**Path conventions** (all under `implementation/platform/backend/core-service/`)
- Main: `src/main/java/com/myfinaimanager/core/portfolio/`
- Test: `src/test/java/com/myfinaimanager/core/`
- Resources: `src/main/resources/` (`application.yml`, `db/migration/`)
- Build: `pom.xml`, `mvnw` / `.mvn/`, `Dockerfile`
- Target layout: `portfolio/{domain/{model,ports,exceptions}, business, infrastructure/{api/rest[/dto,/mapper], persistence/{entity,repository,mapper}, config}}` (data-model.md §1)

**Build command**: after Setup, use `./mvnw` (the wrapper). Set `export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"` for the Testcontainers ITs.

---

## Phase 1: Setup

**Purpose**: add the JPA dependency, the Maven wrapper, and the JPA runtime config — all additive, the build stays green.

- [X] T001 Update `implementation/platform/backend/core-service/pom.xml` — **add** `org.springframework.boot:spring-boot-starter-data-jpa`; update the JaCoCo `<excludes>`: replace `com/myfinaimanager/core/bootstrap/**` with `com/myfinaimanager/core/portfolio/infrastructure/config/**` (keep `CoreServiceApplication.class`). Do **not** remove `spring-boot-starter-jdbc` yet (T028). (research.md D9)
- [X] T002 [P] Add the pinned Maven wrapper to `implementation/platform/backend/core-service/`: run `mvn -N wrapper:wrapper -Dmaven=<pinned 3.9.x>` to generate `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`; commit all three. (research.md D13, OD-EN003-11)
- [X] T003 [P] Update `implementation/platform/backend/core-service/src/main/resources/application.yml` — add under `spring:`  `jpa: { open-in-view: false, hibernate: { ddl-auto: none }, properties: { hibernate: { jdbc: { time_zone: UTC } } } }`. Nothing else changes. (research.md D10)
- [X] T004 [P] Update `implementation/platform/backend/core-service/Dockerfile` build stage — `COPY .mvn/ .mvn/` + `COPY mvnw pom.xml ./`, then `RUN ./mvnw -B -q dependency:go-offline` and `RUN ./mvnw -B -q clean package -DskipTests`. Runtime stage unchanged. (research.md D13)

**Checkpoint**: `./mvnw -B clean verify` still green — JPA on the classpath, zero entities, `ddl-auto: none`, nothing else changed.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the target package tree exists so US1's moves have a home. **No user-story phase can start until this is done.**

- [X] T005 Create the empty target package tree under `implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core/portfolio/`: `domain/model/`, `domain/ports/`, `domain/exceptions/`, `business/`, `infrastructure/api/rest/dto/`, `infrastructure/api/rest/mapper/`, `infrastructure/persistence/entity/`, `infrastructure/persistence/repository/`, `infrastructure/persistence/mapper/`, `infrastructure/config/` — each with a `package-info.java` documenting its ADR-003 role and the inward-dependency rule.
- [X] T006 Create the architecture-test file `src/test/java/com/myfinaimanager/core/architecture/StandardArchitectureRulesTest.java` as a stub (`@AnalyzeClasses(packages = "com.myfinaimanager.core", importOptions = DoNotIncludeTests.class)`, no rules yet) so US1 can fill it; leave `HexagonalArchitectureRulesTest.java` in place until T016.

---

## Phase 3: US1 — Standard module architecture, enforced (Priority: P1)

**Goal**: the `portfolio` module is `domain/{model,ports,exceptions}` · `business` · `infrastructure/{api,persistence,config}` with `infrastructure → business → domain`, enforced by ArchUnit; the EN001 `platform.*` and `bootstrap` packages are gone. **Persistence is only relocated here — still JdbcClient — so behavior is provably unchanged.**

**Independent Test**: quickstart §A + §B + §C — `./mvnw verify` green with every FD001 test assertion intact; `StandardArchitectureRulesTest` enforces the ADR-003 §16 rules and is non-vacuous; a deliberate `domain → infrastructure` import makes it fail. Covers VC-002…VC-007, VC-012.

- [X] T007 [US1] Move all `com.myfinaimanager.core.portfolio.domain.*` types to `…portfolio.domain.model` — value objects (`PortfolioId`, `PositionId`, `InvestorId`, `PortfolioName`, `PortfolioStatus`, `Ticker`, `Market`, `Currency`, `Money`, `Quantity`, `InstrumentRef`), `Portfolio`, `Position`, `NewPosition`, `ValidationCode`, `Violation`. Update `package` declarations and **every** import across `src/main` and `src/test`. Bodies unchanged. (data-model.md §1)
- [X] T008 [US1] Move `PortfolioValidationException` (from `…domain`) and `PortfolioNotSavedException` (from `…application.port.out`) to `…portfolio.domain.exceptions`; update all references. (data-model.md §1; FR-011)
- [X] T009 [US1] Move the outbound ports `PortfolioRepository` and `DefaultInvestorProvider` (from `…application.port.out`) to `…portfolio.domain.ports` — **signatures unchanged**; update all references; delete the now-empty `application/port/out` package. (FR-011; contracts/persistence-port.md §1–§2)
- [X] T010 [US1] Move `CreatePortfolioUseCase`, `CreatePortfolioCommand`, `CreatePortfolioResult` (from `…application.port.in`) and `CreatePortfolioService` (from `…application`) to `…portfolio.business`; annotate `CreatePortfolioService` with `@Service`; delete the now-empty `application` / `application.port.in` packages. Orchestration logic unchanged. (research.md D2; FR-012, FR-013)
- [X] T011 [US1] Move `CreatePortfolioController` and `PortfolioExceptionHandler` (from `…adapter.in.web`) to `…portfolio.infrastructure.api.rest`; move `CreatePortfolioRequest` (+ nested `PositionInput`) and `CreatePortfolioResponse` (+ nested `PositionResponse`) to `…portfolio.infrastructure.api.rest.dto`. (FR-014; data-model.md §1)
- [X] T012 [US1] Extract the transport↔business mapping into `…portfolio.infrastructure.api.rest.mapper`: `CreatePortfolioRequestMapper.toCommand(request, idempotencyKey)` (was the controller `toCommand` helper) and `PortfolioResponseMapper.toResponse(portfolio)` (was `CreatePortfolioResponse.from`). Wire the controller to call them. **JSON representation byte-for-byte identical** — keep `toPlainString()`, `LocalDate::toString`, `null` for absent optionals. (FR-015; contracts/persistence-port.md §4)
- [X] T013 [US1] Move `JdbcPortfolioRepository` and `JdbcDefaultInvestorProvider` (from `…adapter.out.persistence`) to `…portfolio.infrastructure.persistence` — **implementation unchanged (still `JdbcClient` + `TransactionTemplate`)**, imports only; delete the now-empty `adapter` package tree.
- [X] T014 [US1] Create `…portfolio.infrastructure.config.PortfolioModuleConfiguration` (`@Configuration`, `@Bean Clock clock()` → `Clock.systemUTC()`); delete `com.myfinaimanager.core.bootstrap` (package + `PortfolioBeanConfiguration` + `package-info`). (research.md D11; OD-EN003-10)
- [X] T015 [US1] Delete `com.myfinaimanager.core.platform` entirely (all EN001 empty convention-placeholder `package-info.java` files). Remove any lingering doc/comment describing the old `application` / `adapter` layout. (FR-006)
- [X] T016 [US1] Relocate every test class to the mirrored package, updating imports and `@WebMvcTest` / autowire references but **changing no assertion**: `portfolio/domain/{PortfolioTest,ValueObjectsTest}` → `portfolio/domain/model/`; `portfolio/application/CreatePortfolioServiceTest` → `portfolio/business/`; `portfolio/adapter/in/web/CreatePortfolioControllerContractTest` → `portfolio/infrastructure/api/rest/`; `portfolio/adapter/out/persistence/{JdbcPortfolioRepositoryIT,JdbcDefaultInvestorProviderIT}` → `portfolio/infrastructure/persistence/`; `portfolio/{AbstractPortfolioIT,CreatePortfolioIT,CreatePortfolioIdempotencyIT,CreatePortfolioAcceptanceIT,CreatePortfolioMultiPositionIT,CreatePortfolioValidationIT,CreatePortfolioOptionalDataIT}` → package/import updates in place. `bootstrap/PlatformIntegrationIT` and `support/PostgresContainerSupport` stay (update imports only). (EN003 §15; FR-026)
- [X] T017 [US1] Fill `StandardArchitectureRulesTest` with the ADR-003 §16 rules (research.md D8): `..<module>.domain.. !→ ..<module>.business..`; `..<module>.domain.. !→ ..<module>.infrastructure..`; `..<module>.business.. !→ ..<module>.infrastructure..`; `..domain.. !→ {org.springframework.., jakarta.persistence.., org.hibernate.., org.springframework.data.., com.fasterxml.jackson.., java.sql.., javax.sql.., org.apache.kafka..}`; placement — `@Entity` classes in `..infrastructure.persistence.entity..`, Spring Data `Repository` interfaces in `..infrastructure.persistence.repository..`, `@RestController` in `..infrastructure.api.rest..`, `*Mapper` classes under `..infrastructure.api..` in `..infrastructure.api.rest.mapper..` (ADR-003 amendment 2026-09-02), `..api.rest.dto..` types not referenced by `..domain..`/`..business..`; messaging rule with `allowEmptyShould(true)`. Remove `allowEmptyShould` where the `portfolio` module makes the rule substantive. Then **delete** `HexagonalArchitectureRulesTest.java`. (11 rules total.)
- [X] T018 [US1] Run `./mvnw -B clean verify` — MUST be green: pure structural move, **every** FD001 test passes with assertions intact, `StandardArchitectureRulesTest` green and non-vacuous, JaCoCo ≥ 90 % line+branch still met. Then the deliberate-violation check: add a `domain.model` → `infrastructure` import, run `-Dtest=StandardArchitectureRulesTest` (MUST fail), revert, re-run (green). Record as VC-002…VC-007 / VC-012 evidence in `quickstart.md`.

**Checkpoint**: the ADR-003 structure is in place and enforced; behavior is provably unchanged (the whole FD001 suite still green with the relocated-but-still-JDBC code).

---

## Phase 4: US2 — Portfolio persistence via Spring Data JPA (Priority: P1)

**Goal**: replace the relocated `JdbcClient` Portfolio persistence with Spring Data JPA — JPA entities (infra only), Spring Data repositories, an explicit domain↔entity mapper, a persistence adapter implementing the `domain.ports` port — with **identical** atomicity, constraint, idempotency, and exact-decimal behavior; Flyway still owns the schema.

**Independent Test**: quickstart §A + §D — the rewritten `PortfolioPersistenceAdapterIT` + `SchemaIntegrityIT` pass on Testcontainers PostgreSQL with the same scenarios/assertions as the old `JdbcPortfolioRepositoryIT`; no `JdbcClient` / embedded SQL remains in `src/main`; `@Entity` only in `infrastructure.persistence.entity`. Covers VC-008…VC-011.

- [X] T019 [US2] Add JPA entities in `…portfolio.infrastructure.persistence.entity` mapped to the **existing** tables (data-model.md §2): `PortfolioEntity` (`@Table(name="portfolio")`, `@Id UUID id`, `@Column("investor_id") UUID investorId`, `String name`, `String status`, `@Column("idempotency_key", unique=true, nullable=false) String idempotencyKey`, `@Column("created_at", nullable=false) Instant createdAt`, `@OneToMany(mappedBy="portfolio", cascade=ALL, orphanRemoval=true) @OrderBy("id ASC") List<PositionEntity> positions`, protected no-arg ctor); `PositionEntity` (`@Table(name="position")`, `@Id UUID id`, `@ManyToOne(fetch=LAZY, optional=false) @JoinColumn("portfolio_id") PortfolioEntity portfolio`, `String ticker/market/currency`, `BigDecimal quantity` (no precision/scale), nullable `LocalDate initialPurchaseDate`, nullable `BigDecimal averagePurchasePrice`, nullable `String averagePurchasePriceCurrency`); `InvestorEntity` (`@Table(name="investor")`, `@Id UUID id`, `displayName`, `preferredCurrency`, `createdAt`). `equals`/`hashCode` on id. (research.md D3; FR-016, FR-017, FR-019, FR-020)
- [X] T020 [US2] Add Spring Data repositories in `…portfolio.infrastructure.persistence.repository`: `PortfolioJpaRepository extends JpaRepository<PortfolioEntity, UUID>` with `@EntityGraph(attributePaths = "positions") Optional<PortfolioEntity> findByIdempotencyKey(String idempotencyKey)`; `InvestorJpaRepository extends JpaRepository<InvestorEntity, UUID>` with `Optional<InvestorEntity> findTopByOrderByCreatedAtAscIdAsc()`. Derived methods only — no JPQL. (research.md D5; FR-018)
- [X] T021 [US2] Add `PortfolioPersistenceMapper` in `…portfolio.infrastructure.persistence.mapper`: `toEntity(Portfolio, String idempotencyKey)` (children get the `portfolio` back-ref; `averagePurchasePriceCurrency` = position currency when a price is present, else null — keeps the `V2` `position_price_*` CHECKs satisfied); `toDomain(PortfolioEntity)` → `Portfolio.reconstitute(...)` + `Position.reconstitute(...)` rebuilding the value objects exactly as the current `JdbcPortfolioRepository.loadAggregate`. (research.md D4; data-model.md §2; FR-017)
- [X] T022 [US2] Implement `PortfolioPersistenceAdapter implements com.myfinaimanager.core.portfolio.domain.ports.PortfolioRepository` in `…portfolio.infrastructure.persistence` (`@Repository`, constructor-inject `PortfolioJpaRepository` + `PlatformTransactionManager` → `TransactionTemplate`): `save` runs `saveAndFlush(mapper.toEntity(...))` inside the tx; catches `DataIntegrityViolationException` → re-read `findByIdempotencyKey` in a fresh tx → return existing `Portfolio` **or** throw `PortfolioNotSavedException`; catches other `DataAccessException` → `PortfolioNotSavedException`. `findByIdempotencyKey` = read-only tx + `mapper.toDomain`. (research.md D6; contracts/persistence-port.md §1; FR-021, VC-010)
- [X] T023 [US2] Implement `JpaDefaultInvestorProvider implements …domain.ports.DefaultInvestorProvider` in `…portfolio.infrastructure.persistence` via `InvestorJpaRepository.findTopByOrderByCreatedAtAscIdAsc()` → `InvestorId.of(entity.getId())`; `IllegalStateException` if the seed is missing (unchanged message intent). (contracts/persistence-port.md §2)
- [X] T024 [US2] Delete `JdbcPortfolioRepository` and `JdbcDefaultInvestorProvider`. Confirm **zero** `JdbcClient` / `JdbcTemplate` / `.sql(` / native-query usage remains anywhere in `src/main/java`. (FR-018; SC-004)
- [X] T025 [US2] Rewrite `JdbcPortfolioRepositoryIT` → `PortfolioPersistenceAdapterIT` in `portfolio/infrastructure/persistence/` (`@SpringBootTest` + `PostgresContainerSupport`, autowire the `PortfolioRepository` port and a cleanup mechanism): **same scenarios, same assertions** — persists + reads the whole aggregate exactly (a `3.250` quantity keeps its scale — SC-007); a duplicate-instrument write (via `Portfolio.reconstitute` bypassing domain dedup) rolls back entirely → 0 `portfolio` + 0 `position` rows; a repeated idempotency key resolves to the existing portfolio (one row); `portfolio_idem_key_uk` + `position_instrument_uk` exist. Add a case: read a Portfolio + its Positions **outside a transaction** (no `LazyInitializationException`). (FR-027; VC-010, VC-013)
- [X] T026 [US2] Rewrite `JdbcDefaultInvestorProviderIT` → `JpaDefaultInvestorProviderIT` in `portfolio/infrastructure/persistence/` — autowire the `DefaultInvestorProvider` bean, assert it returns `00000000-0000-0000-0000-000000000001`. Same assertion as before.
- [X] T027 [US2] Add `SchemaIntegrityIT` in `portfolio/infrastructure/persistence/` (Testcontainers): assert `flyway_schema_history` contains the `1` and `2` version rows; `portfolio_idem_key_uk`, `position_instrument_uk`, and the `position` CHECK constraints exist in `information_schema`; the `portfolio`/`position`/`investor` column set is unchanged — i.e. Hibernate (`ddl-auto: none`) did not create or alter any table. (research.md D7; FR-022, VC-011)
- [X] T028 [US2] Update `implementation/platform/backend/core-service/pom.xml` — **remove** the explicit `spring-boot-starter-jdbc` dependency (now transitive via data-jpa; `JdbcClient` stays available for test-support per OD-EN003-9). Run `./mvnw -B clean verify` — green with JPA; the persistence + full-slice ITs prove behavior equivalence. Record as VC-008…VC-011 / VC-013 evidence in `quickstart.md`.

**Checkpoint**: `./mvnw verify` green with Spring Data JPA; `domain` still 100 % framework-free (ArchUnit); persistence semantics proven equivalent.

---

## Phase 5: US3 — Behavior and regression fully preserved (Priority: P1)

**Goal**: prove nothing observable changed — full backend suite green, API responses byte-identical, containerized platform runs, and the **FD001 browser E2E passes** (mandatory closure gate).

**Independent Test**: quickstart §A + §E + §F + §H. Covers VC-013…VC-018.

- [X] T029 [US3] Run `./mvnw -B clean verify` in `implementation/platform/backend/core-service` — Surefire + Failsafe (Testcontainers) + JaCoCo `check` (≥ 90 % line **and** branch) + `StandardArchitectureRulesTest`, all green. Record the test counts and coverage. (VC-013)
- [X] T030 [US3] `cd implementation/platform && ./start.sh`; confirm all containers healthy and `db: UP`; `curl` `POST http://localhost:4200/api/portfolios` for the **201 create**, the **200 replay** (same `Idempotency-Key` → `Idempotency-Replayed: true`), and a **400** (`{"name":"","positions":[{"quantity":"0",...}]}` → `application/problem+json`, `type: /problems/portfolio-validation`, `errors[]`); confirm the JSON shapes (field names, decimal strings, `null` optionals, `Location` header, RFC 9457 body — no SQL/stack/framework names) match FD001's recorded behavior; `./stop.sh` clean. (VC-014, VC-016)
- [X] T031 [US3] Build the backend container image: `export DOCKER_HOST=…; docker build implementation/platform/backend/core-service -t finai/core-service:local` (or `BUILD=1 ./start.sh`) — succeeds from the Maven project (Dockerfile uses `./mvnw`). (VC-015)
- [X] T032 [US3] `cd implementation/platform && ./e2e.sh` — `FD001-create-portfolio.spec.ts` **and** `platform-smoke.spec.ts` pass (Chromium), `./e2e.sh` exits **0**. **EN003 is NOT complete while this fails** (EN003 §17). (VC-017; FD001 §13/§16)
- [X] T033 [US3] `cd implementation/platform/frontend/web && export PATH="$HOME/.nvm/versions/node/v20.19.1/bin:$PATH" CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" && npx ng test --watch=false --browsers=ChromeHeadless` — unchanged pass count (EN003 makes no frontend change). Record all US3 results as VC-013…VC-018 evidence.

**Checkpoint**: every regression signal green; FD001 E2E gate satisfied.

---

## Phase 6: US4 — Maven-standard build and aligned documentation (Priority: P2)

**Goal**: the backend builds via `./mvnw` from a clean checkout; backend docs/examples describe the ADR-003 structure and the Spring Data JPA pattern; no human-governed `product/` doc is touched.

**Independent Test**: quickstart §A (clean checkout) + doc review. Covers VC-001; supports VC-002/VC-003/VC-008.

- [X] T034 [US4] From a clean checkout (or `git stash` + fresh clone dir), run `./mvnw -B verify` in `implementation/platform/backend/core-service` with **no host Maven on PATH** — succeeds (wrapper resolves the pinned 3.9.x). (VC-001)
- [X] T035 [US4] Create/update `implementation/platform/backend/core-service/README.md` and the backend section of `implementation/platform/README.md`: the ADR-003 `domain / business / infrastructure` module layout with the `portfolio` module as the reference; the Spring Data JPA persistence pattern (`domain.ports` port → `PortfolioPersistenceAdapter` → `PortfolioJpaRepository` → `PortfolioEntity` → PostgreSQL, Flyway owns schema); developer commands using `./mvnw`; the test layout. Remove any `application` / `adapter` / `JdbcClient`-for-ordinary-persistence guidance. (FR-029; EN003 §19)
- [X] T036 [US4] `git diff main...HEAD -- product/` → confirm **no** file under `product/` was edited by EN003 (ADR-003 / `architecture-rules.md` / `technology-policy.md` were already updated by the human). Update only implementation-side architecture references / package examples. (FR-030; constitution I)

**Checkpoint**: build is Maven-wrapper-standard; docs point the next contributor at ADR-003.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T037 [P] `git diff --stat main...HEAD` scope review (VC-018 / SC-010): **no** change to `implementation/platform/contracts/openapi/openapi.yaml`; **no** new file under `db/migration/` (no `V3`); **no** new API operation, authentication/authorization, messaging, or backend service; **no** Java/Spring major-version bump; frontend unchanged (or trivial integration-preserving only).
- [X] T038 [P] Assemble PR evidence per `product/engineering/definition-of-done.md` "Minimum Pull Request Evidence" in `specs/EN003-align-spring-backend-standard-architecture/pr-evidence.md`: what/why/trace to `VC-001…VC-018`; the persistence-technology change (ADR-003; `technology-policy.md` JPA REQUIRED-by-default); how each of the plan's **Migration Risk Register** items turned out; how it was validated (no CI — `./mvnw verify` + `quickstart.md` + `./e2e.sh`); architecture boundaries (ADR-001 intact, no new ADR).
- [X] T039 Run the `product/engineering/definition-of-done.md` checklist against the change and record status: scope (VC-018), architecture (`StandardArchitectureRulesTest` green + non-vacuous), tests (all suites + coverage ≥ 90 %), contract (`openapi.yaml` unchanged + contract test green), persistence (Spring Data JPA + Flyway + Testcontainers ITs), secrets/hygiene, observability (structured events unchanged), documentation, platform lifecycle (`start.sh`/`stop.sh`/`e2e.sh` unchanged).
- [X] T040 Run the full `quickstart.md` (§A–§H) from a clean state; complete the "Verification Criteria coverage" table with concrete evidence for `VC-001 … VC-018`.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (P1)**: T001 sequential (pom) → T002 ‖ T003 ‖ T004. Checkpoint before Foundational.
- **Foundational (P2)**: after Setup. T005 ‖ T006. **Blocks all user stories.**
- **US1 (P1)**: after Foundational. Order — T007 → T008 → T009 → T010 → T011 → T012 (needs T010/T011) → T013 → T014 → T015 → T016 (needs T007–T015) → T017 (needs T016) → T018 (needs all). Largely sequential (a package move touches shared files; keep the build compilable after each task).
- **US2 (P1)**: after **US1** (needs the `infrastructure.persistence` package + relocated port). T019 → T020 → T021 → T022 (needs T019–T021) ‖ T023 → T024 → T025 ‖ T026 ‖ T027 → T028.
- **US3 (P1)**: after **US2**. T029 → T030 → T031 → T032 → T033 (T030–T033 largely independent once T029 is green).
- **US4 (P2)**: after US2 (build shape settled); T035/T036 after US3 (docs describe the finished state). T034 ‖ T035 ‖ T036.
- **Polish (P8)**: after US3 + US4. T037 ‖ T038 → T039 → T040.

### Story dependency summary

```text
Setup → Foundational → US1 (structure + ArchUnit) → US2 (JPA persistence) → US3 (regression + FD001 E2E gate) ─┐
                                                                             US4 (Maven + docs) ──────────────┴→ Polish
```

EN003 is **linear** — one atomic migration. The stories are sequential facets, not parallel tracks.

### Parallel opportunities

- Setup: T002, T003, T004 in parallel (different files) after T001.
- Foundational: T005 ‖ T006.
- US2: T022 ‖ T023; T025 ‖ T026 ‖ T027 (different test files).
- US3: T030 / T031 / T032 / T033 once T029 is green.
- US4: T034 ‖ T035 ‖ T036.
- Polish: T037 ‖ T038.

Within US1 the moves are **not** parallel — each is a cross-cutting rename that must leave the build compilable.

---

## Implementation Strategy

### One migration, verified in slices

1. Setup → JPA + wrapper on the classpath, build green.
2. Foundational → target packages exist.
3. **US1** → whole structural move; `./mvnw verify` green with the FD001 suite intact + ArchUnit enforcing ADR-003. **STOP & VALIDATE** (quickstart §A/§C) — this alone proves "restructure without behavior change".
4. **US2** → JdbcClient → Spring Data JPA, behavior-identical; `./mvnw verify` green; persistence ITs prove equivalence.
5. **US3** → full regression + `./start.sh` curl diff + container build + **`./e2e.sh` (FD001 gate)** + `ng test`.
6. **US4** → `./mvnw` from clean checkout + docs.
7. Polish → scope check, PR evidence, DoD, full quickstart.

### Constitution / DoD checkpoints

- `domain` stays framework-free — enforced by `StandardArchitectureRulesTest` from T017 (constitution VI; VC-004).
- No product behavior / API / schema change — T037 (`git diff`) + the contract test + FD001 E2E (VC-014, VC-017, VC-018).
- Persistence ITs run on real Testcontainers PostgreSQL, never mocked (constitution VII; T025–T027).
- Coverage gate ≥ 90 % preserved (T018, T029).
- **FD001 E2E (T032) is a hard closure gate** — EN003 cannot close if it fails (EN003 §17; ADR-003 "Migration").
- ADR-003 is the authority; any *material* deviation (EN003 §20 items are technical, not that) → stop and surface (constitution IV; FR-032).

---

## Notes

- `[P]` = different files, no dependency on an incomplete task.
- `[US#]` labels map tasks to the spec's user stories for traceability.
- After Setup, always invoke the build as `./mvnw` (the wrapper), not host `mvn`.
- Keep the build **compilable and green after every US1 task** — a package move that doesn't compile is not done.
- The two JDBC persistence ITs are **rewritten**, not deleted — every scenario and assertion is ported to the JPA adapter (EN003 §15).
- If the JPA mapping surfaces a real incompatibility that needs a schema change — **stop**: it must be one minimal, documented forward Flyway migration (`V3`), not a redesign, and it changes VC-011/§12 evidence (FR-023). None is expected (research OD-EN003-12).
- If any step would need: a new API operation, an auth change, Kafka, a new service, a new persistence technology, or a Java/Spring major upgrade — **stop and surface it** (constitution IV; EN003 §3).
- Total: **40 tasks** — Setup 4, Foundational 2, US1 12, US2 10, US3 5, US4 3, Polish 4.
