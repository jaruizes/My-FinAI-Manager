# Quickstart / Validation — EN003 Align Spring Backend with Standard Architecture

Runnable scenarios that prove EN003 preserves every observable behavior while restructuring the
backend to ADR-003. Each maps to a Verification Criterion (`VC-001 … VC-018`). Design detail is in
[plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), and
[contracts/persistence-port.md](./contracts/persistence-port.md) — not duplicated here.

## Prerequisites

- **Docker + Docker Compose v2** (the platform + E2E run in containers — EN002).
- JDK 21 to run the backend build locally (the Maven wrapper provides Maven).
- `implementation/platform/infrastructure/local/.env` present (`cp .env.example .env`).
- Node ≥ 20.19 only if running `ng test` / Playwright locally (the container path needs neither).

---

## A. Backend build & tests — `./mvnw verify`  →  VC-001, VC-004, VC-005, VC-006, VC-012, VC-013, VC-014

```bash
cd implementation/platform/backend/core-service
./mvnw -B clean verify
```

**Expected**:

- Build succeeds with **no host-installed Maven** (wrapper resolves the pinned 3.9.x). — **VC-001**
- **Surefire** (unit): `StandardArchitectureRulesTest`, `portfolio.domain.model.*Test`,
  `portfolio.business.CreatePortfolioServiceTest`,
  `portfolio.infrastructure.api.rest.CreatePortfolioControllerContractTest` — all green, assertions
  unchanged from FD001. — **VC-014**
- **`StandardArchitectureRulesTest`** enforces `..domain.. !→ ..business..`, `..domain.. !→ ..infrastructure..`,
  `..business.. !→ ..infrastructure..`, and the ADR-003 §16 placement rules — and is **non-vacuous**
  (the `portfolio` module has classes in all three areas). — **VC-004, VC-005, VC-006, VC-012**
- **Failsafe** (Testcontainers PostgreSQL): `PortfolioPersistenceAdapterIT`,
  `JpaDefaultInvestorProviderIT`, `SchemaIntegrityIT`, `CreatePortfolioIT`,
  `CreatePortfolioIdempotencyIT`, `CreatePortfolioAcceptanceIT`, `CreatePortfolioMultiPositionIT`,
  `CreatePortfolioValidationIT`, `CreatePortfolioOptionalDataIT`, `PlatformIntegrationIT` — all
  green. — **VC-013**
- **JaCoCo `check`**: ≥ 90 % line **and** branch (bundle) — still passes.
- **Contract test** validates live `POST /api/portfolios` payloads against the **unchanged**
  `openapi.yaml`. — **VC-014**

---

## B. Domain independence & no ad-hoc SQL  →  VC-004, VC-008, VC-009

```bash
cd implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core/portfolio
# domain must import no framework / persistence / transport packages:
! grep -rnE "org\.springframework|jakarta\.persistence|org\.hibernate|com\.fasterxml|java\.sql|org\.apache\.kafka" domain/
# ordinary persistence must not use JdbcClient / JdbcTemplate / embedded SQL:
! grep -rnE "JdbcClient|JdbcTemplate|\.sql\(" infrastructure/persistence/
# JPA annotations live only in infrastructure persistence:
grep -rl "@Entity" . | grep -v "infrastructure/persistence/entity/" && echo "LEAK" || echo "OK — @Entity only in infrastructure/persistence/entity"
```

**Expected**: the `grep` guards pass (no matches where forbidden); `StandardArchitectureRulesTest`
already enforces the same rules in the build. — **VC-004, VC-008, VC-009**

---

## C. ArchUnit fails on a real violation  →  VC-012 (SC-005)

```bash
cd implementation/platform/backend/core-service
# temporarily add an infrastructure import to a domain class, then:
./mvnw -B -q -Dtest=StandardArchitectureRulesTest test        # MUST fail
git checkout -- src/main/java/.../portfolio/domain/            # revert
./mvnw -B -q -Dtest=StandardArchitectureRulesTest test        # green again
```

**Expected**: the suite fails with a clear `domain → infrastructure` violation, then passes after
revert. — **VC-012**

---

## D. Persistence semantics preserved (JPA adapter)  →  VC-008, VC-010, VC-011

Covered by `PortfolioPersistenceAdapterIT` + `SchemaIntegrityIT` in step A. It asserts, against
real PostgreSQL:

- the whole aggregate persists and reads back **exactly** (a `3.250` quantity keeps its scale — SC-007);
- a forced mid-write failure (duplicate instrument) **rolls back entirely** → 0 `portfolio`, 0 `position` rows;
- a repeated idempotency key **resolves to the existing portfolio** (one row);
- `portfolio_idem_key_uk` and `position_instrument_uk` exist and are enforced;
- Flyway `flyway_schema_history` has the `V1` + `V2` rows and **Hibernate did not alter the schema**
  (`ddl-auto: none`). — **VC-011**

---

## E. Fully containerized platform still works  →  VC-015, VC-016

```bash
cd implementation/platform
./start.sh
curl -sS http://localhost:8080/actuator/health | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['status'], d['components']['db']['status'])"
```

Then exercise the four response shapes and compare to FD001's recorded behavior (**VC-014**):

```bash
KEY=$(uuidgen)
# 201 create
curl -s -i -XPOST http://localhost:4200/api/portfolios -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" \
  -d '{"name":"EN003 check","positions":[{"ticker":"ASML","market":"XAMS","quantity":"12","currency":"EUR"}]}' | sed -n '1p;/^{/p'
# 200 replay (same key) + Idempotency-Replayed: true
curl -s -i -XPOST http://localhost:4200/api/portfolios -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" \
  -d '{"name":"EN003 check","positions":[{"ticker":"ASML","market":"XAMS","quantity":"12","currency":"EUR"}]}' | sed -n '1p;/Idempotency-Replayed/p'
# 400 validation problem+json
curl -s -XPOST http://localhost:4200/api/portfolios -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"name":"","positions":[{"ticker":"ASML","market":"XAMS","quantity":"0","currency":"EUR"}]}'
```

**Expected**: `201` with the same JSON shape (`status`, `positions[]`, `createdAt`, decimal strings,
`null` optionals); `200` + `Idempotency-Replayed: true`; `400` `application/problem+json` with
`type: /problems/portfolio-validation` and `errors[]` (`name/REQUIRED`, `positions[0].quantity/NOT_POSITIVE`).
No stack trace, no SQL, no framework class names.

```bash
./stop.sh                       # platform stops through Docker Compose
docker build implementation/platform/backend/core-service -t finai/core-service:local   # or: BUILD=1 ./start.sh
```

**Expected**: `./stop.sh` clean; the backend image builds from the Maven project. — **VC-015, VC-016**

---

## F. FD001 browser E2E — mandatory closure gate  →  VC-017

```bash
cd implementation/platform
./e2e.sh
echo "exit=$?"
```

**Expected**: `FD001-create-portfolio.spec.ts` **and** `platform-smoke.spec.ts` pass (Chromium),
`./e2e.sh` exits **0** — the real journey `browser → frontend → REST → refactored core-service →
Spring Data JPA → PostgreSQL` works with no mocks. **EN003 is NOT complete while this fails**
(EN003 §17). — **VC-017**

---

## G. No product change  →  VC-018

```bash
git diff --stat main...HEAD
git diff main...HEAD -- implementation/platform/contracts/openapi/openapi.yaml   # → empty
git diff main...HEAD -- implementation/platform/backend/core-service/src/main/resources/db/migration/   # → empty (no V3)
git diff main...HEAD -- implementation/platform/frontend/                        # → empty (or trivial integration-preserving only)
```

**Expected**: changes are limited to backend package moves, the JPA persistence layer, `pom.xml` /
`application.yml` / `Dockerfile` / Maven wrapper, the ArchUnit suite, test relocations, and backend
docs. **No** `openapi.yaml` change, **no** new Flyway migration, **no** new API operation, **no**
auth/messaging/service change, **no** Java/Spring major-version bump. — **VC-018**

---

## H. Frontend regression (no change expected)

```bash
cd implementation/platform/frontend/web && npx ng test --watch=false --browsers=ChromeHeadless
```

**Expected**: unchanged pass count — EN003 does not touch the frontend.

---

## Verification Criteria coverage

| VC | Scenario(s) |
|---|---|
| VC-001 Maven build | A |
| VC-002 Functional-module structure | A (`StandardArchitectureRulesTest`), code review, `data-model.md` §1 |
| VC-003 `domain` / `business` / `infrastructure` per module | A, code review |
| VC-004 Domain independence | A, B |
| VC-005 Ports in `domain.ports` | A, code review |
| VC-006 Business → domain only | A |
| VC-007 Adapter placement | A, B, code review |
| VC-008 Spring Data JPA persistence | A, B, D |
| VC-009 No JPA leakage into domain | A, B |
| VC-010 Persistence / tx / constraint / idempotency preserved | A, D, E |
| VC-011 Flyway still owns schema | D (`SchemaIntegrityIT`) |
| VC-012 ArchUnit enforcement | A, C |
| VC-013 Testcontainers integration tests pass | A |
| VC-014 REST/OpenAPI compatible | A (contract test), E (`curl` diff) |
| VC-015 Backend container builds | A, E |
| VC-016 `start.sh` / `stop.sh` operate the platform | E |
| VC-017 FD001 E2E passes | F |
| VC-018 No product change | G, H |

---

## Verification run — 2026-09-02 (all green)

| VC | Evidence |
|---|---|
| VC-001 | `./mvnw -B clean verify` run with `env -i` (**no host Maven on PATH**, `command -v mvn` → none) → wrapper resolved **Apache Maven 3.9.11**, **BUILD SUCCESS**. |
| VC-002 / VC-003 / VC-005 | `portfolio` module is `domain/{model,ports,exceptions}` · `business` · `infrastructure/{api/rest[/dto,/mapper],persistence/{entity,repository,mapper},config}`; `PortfolioRepository` + `DefaultInvestorProvider` in `domain.ports`. `com.myfinaimanager.core.platform.*` and `…core.bootstrap` deleted. `StandardArchitectureRulesTest` green (11 rules). |
| VC-004 / VC-009 | `domain` imports no `org.springframework` / `jakarta.persistence` / `org.hibernate` / `com.fasterxml` / `java.sql` / `org.apache.kafka`; `domain_has_no_framework_dependencies` + `domain_does_not_use_spring_data_or_jpa` green. |
| VC-006 / VC-007 | `business_does_not_depend_on_infrastructure`, `rest_controllers_live_in_infrastructure_api_rest`, `jpa_entities_live_in_infrastructure_persistence_entity`, `spring_data_repositories_live_in_infrastructure_persistence_repository`, `rest_dtos_are_not_used_by_domain_or_business` — all green (no `allowEmptyShould` on the entity/repository rules — substantive). |
| VC-008 | Portfolio persistence is `PortfolioPersistenceAdapter` (JPA) → `PortfolioJpaRepository` (`JpaRepository`, derived queries, `@EntityGraph`) → `PortfolioEntity`/`PositionEntity`. `grep -rE "JdbcClient\|JdbcTemplate\|\.sql\(" src/main/java` → only javadoc mentions, **no usage**. Explicit `spring-boot-starter-jdbc` removed from `pom.xml`. |
| VC-010 | `PortfolioPersistenceAdapterIT` (5 tests) green: whole aggregate round-trips exactly (`3.250` keeps scale — SC-007); duplicate-instrument write rolls back → 0+0 rows; repeated idempotency key → one row, same id; loaded portfolio fully usable outside a transaction (no `LazyInitializationException`); both unique constraints present. |
| VC-011 | `SchemaIntegrityIT` (3 tests) green: `flyway_schema_history` = versions `1`,`2` all successful; the 9 EN003-relevant constraints exist; `portfolio`/`position`/`investor` column sets exactly as `V2` created; only tables = `flyway_schema_history` + the 3 business tables (Hibernate `ddl-auto: none` altered nothing). |
| VC-012 | Deliberate `domain.model → infrastructure.api.rest.dto` reference added → `-Dtest=StandardArchitectureRulesTest` **failed** on `domain_does_not_depend_on_infrastructure` + `rest_dtos_are_not_used_by_domain_or_business`; reverted → green. |
| VC-013 | `./mvnw clean verify`: **Surefire 62** (0 F / 0 E), **Failsafe 31** (0 F / 0 E) — `PortfolioPersistenceAdapterIT`, `JpaDefaultInvestorProviderIT`, `SchemaIntegrityIT`, `PlatformIntegrationIT`, `CreatePortfolio{,Idempotency,Acceptance,MultiPosition,Validation,OptionalData}IT`. JaCoCo bundle gate ≥ 90 % line **and** branch — passed. |
| VC-014 | `CreatePortfolioControllerContractTest` (4 tests) green against the **unchanged** `openapi.yaml` (3.0.3). Running platform `curl`: `201` + `Location` + `"quantity":"3.250"` / `"averagePurchasePrice":"812.50"` (exact scale) + `null` optionals; `200` + `Idempotency-Replayed: true` + identical body; `400` `application/problem+json` `type:/problems/portfolio-validation` `errors:[name/REQUIRED, positions[0].quantity/NOT_POSITIVE]` — no SQL/stack/framework names. |
| VC-015 | `BUILD=1 ./start.sh` built `finai/core-service:local` from the Maven project (Dockerfile build stage runs `./mvnw`). |
| VC-016 | `./start.sh` → all containers healthy, `health` = `UP` / `db: UP`; `./stop.sh` → clean Compose down, 0 finai containers left. |
| VC-017 | `./e2e.sh` → `FD001-create-portfolio.spec.ts` **+** `platform-smoke.spec.ts` → **2 passed** (Chromium), `exit=0`. |
| VC-018 | `openapi.yaml` not touched by EN003; no `V3` migration; **0** frontend files changed by EN003; no new API operation / auth / messaging / service; no Java (21) / Spring Boot (3.5.6) version change. `ng test` → **29 SUCCESS** (unchanged). |

**Note on the local environment**: the Testcontainers ITs require `TESTCONTAINERS_RYUK_DISABLED=true`
(colima/virtiofs cannot bind-mount the docker socket into Ryuk). This is a pre-existing local-dev
setting, unrelated to EN003.
