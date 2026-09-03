# EN003 — Align Spring Backend with Standard Architecture · PR Evidence

## What requirement does this implement?

**EN003 — Establish the standard Spring backend architecture** (Technical Enabler, Approved).
A behavior-preserving migration of `implementation/platform/backend/core-service` to the layout and
persistence standard defined by **ADR-003 — Standard Spring Backend Architecture**.

## Which specification / tasks does it trace to?

- Enabler: `product/definition/enablers/EN003-align-spring-backend-standard-architecture/…`
- Governing decision: `product/architecture/adrs/ADR-003-standard-spring-backend-architecture.md`
- SDD artifacts: `specs/EN003-align-spring-backend-standard-architecture/` — `spec.md` (US1–US4,
  FR-001…FR-032, SC-001…SC-011, VC-001…VC-018), `plan.md` (Constitution Check PASS, Migration Risk
  Register), `research.md` (D1–D14), `data-model.md`, `contracts/persistence-port.md`,
  `quickstart.md` (scenarios A–H + the 2026-09-02 verification run), `tasks.md` (T001–T040).

## What changed?

| Area | Change |
|---|---|
| Package structure | The whole `portfolio` module moved to ADR-003 `domain/{model,ports,exceptions}` · `business` · `infrastructure/{api/rest[/dto,/mapper], persistence/{entity,repository,mapper}, config}`. The EN001 `com.myfinaimanager.core.platform.*` placeholders and `…core.bootstrap` package were **deleted**. |
| Transport mapping | Controller `toCommand` helper → `CreatePortfolioRequestMapper`; `CreatePortfolioResponse.from` → `PortfolioResponseMapper` (both `@Component` in `infrastructure.api.rest.mapper`). JSON representation unchanged. |
| Persistence | `JdbcClient` Portfolio persistence **replaced** by Spring Data JPA: `PortfolioEntity` / `PositionEntity` / `InvestorEntity` (infra-only `@Entity`), `PortfolioJpaRepository` / `InvestorJpaRepository` (derived queries, `@EntityGraph`), `PortfolioPersistenceMapper` (explicit domain⇄entity), `PortfolioPersistenceAdapter implements domain.ports.PortfolioRepository`, `JpaDefaultInvestorProvider`. `spring.jpa.hibernate.ddl-auto: none` — Flyway still owns the schema. |
| Architecture test | `HexagonalArchitectureRulesTest` **deleted**, replaced by `StandardArchitectureRulesTest` (11 ADR-003 §16 rules incl. the 2026-09-02 REST-mapper amendment, non-vacuous). |
| Build | Added the pinned **Maven wrapper** (`mvnw`, `.mvn/…` → Maven 3.9.11); `pom.xml`: **+** `spring-boot-starter-data-jpa`, **−** explicit `spring-boot-starter-jdbc`, JaCoCo excludes updated (`bootstrap/**`→`infrastructure/config/**`, `infrastructure/persistence/entity/**`); `Dockerfile` build stage uses `./mvnw`; `application.yml` gains `spring.jpa` block. |
| Tests | Every FD001 suite relocated to the mirrored package with **assertions intact**. `JdbcPortfolioRepositoryIT` → `PortfolioPersistenceAdapterIT`, `JdbcDefaultInvestorProviderIT` → `JpaDefaultInvestorProviderIT` (same scenarios/assertions + a detached-read case). New `SchemaIntegrityIT`. |
| Docs | New `implementation/platform/backend/core-service/README.md`; backend section added to `implementation/platform/README.md`. |

**Not changed**: `openapi.yaml`, `db/migration/` (no `V3`), the frontend, the platform lifecycle
scripts, Java 21 / Spring Boot 3.5.6.

## Why was this design chosen?

ADR-003 is the authority. Key implementation decisions (research.md): `investorId` as a plain
column not `@ManyToOne` (FD001 has no Investor aggregate to navigate); `status` as `String` +
mapper↔enum; `BigDecimal` columns with **no** `precision`/`scale` so the investor's exact input
scale round-trips (SC-007); hand-written mappers (no MapStruct) keeping the domain annotation-free;
`TransactionTemplate` + `saveAndFlush` + `DataIntegrityViolationException` re-read to preserve the
idempotency-race semantics exactly; `ddl-auto: none` + `SchemaIntegrityIT` rather than
`validate` (noisy against unbounded `NUMERIC` / `CHAR(3)`).

## How was it tested?

No CI — validated locally (`quickstart.md` "Verification run — 2026-09-02"):

- `./mvnw -B clean verify` (also run with **no host Maven on PATH** — wrapper resolved Maven 3.9.11):
  **Surefire 62** + **Failsafe 31**, 0 failures / 0 errors; JaCoCo bundle gate ≥ 90 % line **and**
  branch — passed.
- ArchUnit non-vacuous check: deliberate `domain → infrastructure` reference → suite fails →
  reverted → green.
- `./start.sh` + `curl` 201 / 200-replay / 400 — response shapes byte-compatible with FD001
  (exact decimals, `null` optionals, RFC 9457 problem body, no SQL/stack leakage); `./stop.sh` clean.
- `BUILD=1 ./start.sh` — backend image builds from the Maven project.
- **`./e2e.sh` → 2 passed (Chromium), exit 0** — the FD001 browser E2E closure gate (EN003 §17).
- `ng test` → 29 SUCCESS (frontend untouched).

## What architecture boundaries are affected?

Internal only. ADR-001 intact — still one `core-service` deployable, no new service, no new
deployment boundary. `domain → business → infrastructure` (inward) now enforced by
`StandardArchitectureRulesTest`. Data ownership unchanged (`portfolio` module owns
`portfolio`/`position`; reads the seeded `investor` row).

## Were any ADRs required?

No new ADR. EN003 **implements** the already-approved ADR-003. `technology-policy.md` (Spring Data
JPA REQUIRED-by-default, Maven REQUIRED, Direct JDBC CONDITIONAL) and `architecture-rules.md` were
updated by the human alongside ADR-003 — **not** by this change.

## Migration Risk Register outcome (plan.md)

| Risk | Outcome |
|---|---|
| JPA flush timing hides write errors until commit | Handled — `saveAndFlush` inside the tx; `PortfolioPersistenceAdapterIT` rollback case proves mid-write failure → 0 rows. |
| `LazyInitializationException` on detached reads | Avoided — `@EntityGraph` eager-loads positions; mapper fully materialises the domain aggregate inside the read tx; dedicated IT asserts detached usage. |
| `NUMERIC` / `CHAR(3)` `validate` friction | Avoided — `ddl-auto: none` + `SchemaIntegrityIT`. |
| JSON formatting drift | None — `PortfolioResponseMapper` keeps `toPlainString()` / `LocalDate::toString` / `null`; contract test + `curl` diff confirm. |
| Vacuous ArchUnit rules | Avoided — `portfolio` has classes in all three areas; `allowEmptyShould` removed from the entity/repository placement rules; deliberate-violation check proves enforcement. |
| `spring-boot-starter-jdbc` removal breaks test-support | Fine — `spring-jdbc` / `JdbcClient` remain transitively via `data-jpa`; used only by test cleanup/assertions (OD-EN003-9). |
| `bootstrap` package removal | `PortfolioModuleConfiguration` (`@Configuration`, `@Bean Clock`) replaces `PortfolioBeanConfiguration`; `@Service` on `CreatePortfolioService`. |

## What evidence shows acceptance criteria pass?

`quickstart.md` → "Verification Criteria coverage" + "Verification run — 2026-09-02": every
`VC-001 … VC-018` mapped to a concrete, green result.
