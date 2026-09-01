# Phase 0 Research: Bootstrap Executable Platform (EN001)

EN001 introduces no product behavior. Research covers only integration patterns for the
already-approved technologies and records the version/tooling choices that are **not** yet fixed
by any authoritative document.

Governance correction (2026-09-01): version and runtime choices MUST NOT be silently decided by
the implementer. They are listed under **Open questions** below and must be resolved by a
maintainer (tasks.md OD-1…OD-6 / T001) before implementation starts.

---

## Decision Rule

These decisions refine implementation details for EN001.

They do not override:
- product/architecture/technology-policy.md
- approved ADRs
- EN001 enabler definition

## Approved and fixed (by `technology-policy.md` / `EN001-bootstrap-platform.md`)

| Concern | Choice | Authority |
|---|---|---|
| Web frontend | Angular + TypeScript | technology-policy (PREFERRED / REQUIRED) |
| Backend runtime / framework | Java + Spring Boot | technology-policy (ALLOWED); ADR-001 |
| Backend topology | one coarse-grained `core-service` deployable | ADR-001 |
| Relational database | PostgreSQL | technology-policy (PREFERRED) |
| Local orchestration | Docker Compose | enabler §9 |
| Relational migrations | Flyway | technology-policy ("Flyway / equivalent", PREFERRED) |
| Integration testing | Testcontainers | technology-policy (REQUIRED for applicable IT); testing-strategy §3 |
| External REST contracts | OpenAPI, under `implementation/platform/contracts/openapi/` | technology-policy (REQUIRED); enabler §7 |
| Architecture style | Hexagonal Architecture | architecture-rules AR-001 |
| Logging | structured, machine-readable | DR-029 |

---

## D1 — Backend health & database-connectivity mechanism

- **Decision**: Use Spring Boot Actuator `/actuator/health` with the built-in `db` health
  indicator (auto-configured from the `DataSource`), `show-details=always` (no auth in EN001),
  and liveness/readiness probe groups. **No custom endpoint, no controller, no hexagon slice.**
- **Rationale**: FR-019 explicitly allows "a simple health endpoint **or equivalent framework
  health mechanism**". Actuator satisfies VC-004 (connectivity) and VC-005 (health responds) with
  zero application code, and degrades gracefully (reports `DOWN`/`OUT_OF_SERVICE`, never a stack
  trace) when PostgreSQL is unavailable.
- **Alternatives considered**: A hand-authored `GET /api/platform/status` implemented through a
  full domain→application→adapter path — **rejected** (governance correction #2): it invents
  production classes only to demonstrate the architecture, which real modules (FD001) should do
  instead.

## D2 — Startup resilience to database not-ready

- **Decision**: Do not fail the backend boot when PostgreSQL is briefly unavailable. Configure
  Flyway `connect-retries`; Actuator readiness reports `OUT_OF_SERVICE` until the DB is reachable,
  then recovers. `start.sh` additionally waits for the Postgres healthcheck before launching the
  backend (defense in depth).
- **Rationale**: spec Edge Case — "backend starts before PostgreSQL is ready … establishes
  connectivity without a manual restart".
- **Alternatives considered**: fail-fast on boot — rejected (violates the edge case, hurts DX).

## D3 — Hexagonal package convention without production code

- **Decision**: Create the `com.myfinaimanager.core.platform` namespace with `package-info.java`
  files documenting `domain` / `application` (`port/in`, `port/out`) / `adapter` (`in/web`,
  `out/persistence`) and the inward dependency rule, plus a `bootstrap` package reserved for
  framework wiring. **No classes** beyond the Spring Boot entry point.
- **Rationale**: correction #5 — establish conventions and constraints now; let real modules
  emerge with FD001. An ArchUnit test (D4) turns the convention into an active guardrail.
- **Alternatives considered**: seed one example domain/application/adapter triad — rejected
  (artificial production code).

## D4 — Architecture conformance guardrail

- **Decision**: One ArchUnit test (`HexagonalArchitectureRulesTest`) encoding: `..domain..` must
  not depend on `..application..`/`..adapter..`/`..bootstrap..`/`org.springframework..`/`java.sql..`;
  `..application..` must not depend on `..adapter..`/`..bootstrap..`/Spring; inbound adapters must
  not depend on outbound adapters. Runs in the normal test phase.
- **Rationale**: FR-027, AR-050, testing-strategy §6, correction #9 — validates real boundaries
  (the package convention) without requiring production classes. Vacuously green today; catches
  the first violation FD001 could introduce.
- **Alternatives considered**: defer all architecture testing to FD001 — rejected (spec FR-027 +
  US4 want the guardrail in place); Spring Modulith — heavier than one namespace needs.

## D5 — Local infrastructure orchestration

- **Decision**: Single `infrastructure/local/compose.yaml` with one PostgreSQL service
  (major version per OD-5), named volume, `pg_isready` healthcheck, env from a git-ignored `.env`
  with a committed `.env.example` of synthetic non-secret placeholders.
- **Rationale**: FR-015/FR-016 — Compose, PostgreSQL only, "intentionally minimal". The
  healthcheck lets `start.sh` wait deterministically.
- **Alternatives considered**: Testcontainers-only (no persistent local DB for manual rundown —
  the enabler wants a real local instance); raw `docker run` (Compose is the named mechanism).

## D6 — Lifecycle scripts (`start.sh` / `stop.sh`)

- **Decision**: POSIX-friendly Bash.
  - `start.sh`: verify Docker running (fail fast, clear message) → `docker compose ... up -d` →
    wait for Postgres healthy → start backend (OD-3 run command) in background → `.run/backend.pid`
    → start frontend dev server in background → `.run/frontend.pid` → print URLs incl.
    `/actuator/health`.
  - `stop.sh`: kill `.run/*.pid` if present (safe no-op if absent) → `docker compose ... down` →
    remove `.run/`.
  - Predictable failures (Docker down, port in use, missing `.env`) → one actionable stderr line,
    no stack traces.
- **Rationale**: FR-021…FR-024, spec Edge Cases, correction #8. PID files keep `stop.sh` reliable
  and make a repeated `start.sh` detectable.
- **Alternatives considered**: running backend/frontend as Compose services too (heavier images,
  slower inner loop — scripts can adopt this internally later without changing the entry-point
  contract).

## D7 — Integration test with Testcontainers

- **Decision**: JUnit 5 + `org.testcontainers:postgresql`. One `PlatformIntegrationIT` boots the
  full context against a disposable PostgreSQL container (image per OD-5), asserts Flyway applied
  `V1__baseline.sql`, a `SELECT 1` via the `DataSource` succeeds, and `/actuator/health` = `UP`
  with `db: UP`. `PostgresContainerSupport` centralizes the container + `@DynamicPropertySource`.
- **Rationale**: FR-025/FR-026, testing-strategy §3 — real disposable infra, no manual DB install.
- **Alternatives considered**: embedded/H2 Postgres — rejected (testing-strategy §3: don't mock
  the infrastructure whose integration is the point of the test).

## D8 — Contracts location & placeholder

- **Decision**: `implementation/platform/contracts/openapi/openapi.yaml` as an OpenAPI 3.1
  skeleton (`info`, `servers`, `paths: {}`) plus a `README.md` describing the contract-first
  convention. No operations. FD001 adds the first paths/schemas.
- **Rationale**: FR-017/FR-018 — establish the location and "placeholder structure" for the
  contract-first workflow without inventing endpoints (corrections #2, #10). Operational health
  is a framework mechanism, not a business contract, so it is not modelled here.
- **Alternatives considered**: a real status operation + contract test — rejected (correction #2).

## D9 — Structured logging

- **Decision**: Use Spring Boot's **built-in** structured console logging (Elastic Common Schema
  JSON) via `logging.structured.format.console: ${LOG_STRUCTURED_FORMAT:ecs}` in `application.yml`.
  **No custom `logback-spring.xml`** — a `logback-spring.xml` that `<include>`s Spring Boot's plain
  `console-appender.xml` silently overrides the structured format (this was the state fixed by
  convergence task T036). `LOG_STRUCTURED_FORMAT=` (empty) gives a readable plain console for local
  debugging. No log shipping / collector.
- **Verified**: `mvn spring-boot:run` emits ECS JSON lines (`"@timestamp"`, `"log.level"`,
  `"ecs.version":"8.11"`, `"service.name":"core-service"`); the empty-value override yields the
  plain pattern. `mvn clean verify` still green.
- **Rationale**: DR-029 (structured logging REQUIRED), AR-039; `CLAUDE.md §16` — no observability
  infra beyond what EN001 needs.
- **Alternatives considered**: keep a custom `logback-spring.xml` with an explicit
  `StructuredLogEncoder` — more moving parts than SB's property; OpenTelemetry exporter — preferred
  *direction* but needs a backend, deferred.

## D11 — Testcontainers ↔ modern Docker Engine API version

- **Decision**: Set the Docker API version for the integration-test JVM to **1.44** via the
  Failsafe plugin (`<systemPropertyVariables><api.version>${testcontainers.docker.api.version}</api.version></systemPropertyVariables>`
  in `core-service/pom.xml`).
- **Rationale**: Testcontainers 1.21.3 (managed by the Spring Boot 3.5.x BOM) bundles a shaded
  docker-java that, when no API version is configured, hard-codes a fallback of **1.32**
  (`DockerClientProviderStrategy.getClientForConfig`). Docker Engine 28+ (Docker Desktop,
  OrbStack, colima, Rancher Desktop, recent Linux, CI runners) enforces a minimum API version of
  **1.44** and rejects 1.32 with HTTP 400 (`client version 1.32 is too old`). `DOCKER_API_VERSION`
  (the Docker CLI env var) is **not** read by docker-java, and bumping the non-shaded `docker-java`
  artifacts has no effect on the shaded client — the reliable fix is the `api.version` **system
  property**, which the shaded `DefaultDockerClientConfig.createDefaultConfigBuilder()` does read.
- **Verified**: `mvn clean verify` green — `PlatformIntegrationIT` 3/3 against a real
  `postgres:16-alpine` Testcontainer; `HexagonalArchitectureRulesTest` 4/4.
- **Alternatives considered**: `~/.testcontainers.properties` (per-machine, doesn't help CI/other
  devs); `src/test/resources/docker-java.properties` (not picked up by the shaded loader here);
  overriding `docker-java-bom` (no effect — shaded); requiring each dev to export env (fragile).
- **Maintenance**: bump `testcontainers.docker.api.version` if a future engine raises its minimum.

## D10 — Frontend shell scope

- **Decision**: Standalone-component Angular app: `app.component` shell (dark sidebar + top bar +
  empty content), `core/layout/` structural components, `app.routes.ts` with one default route,
  `src/styles/_tokens.scss` as the design-token integration point aligned with
  `product/ux/design-system.md`. No product navigation entries.
- **Rationale**: enabler §6 asks for bootstrap + shell + routing foundation + styling integration
  point + a place for shared tokens/components — nothing more. design-system.md: "must not expose
  navigation options for capabilities that do not yet exist".
- **Alternatives considered**: building the full token/component set now — rejected (speculative;
  design-system.md says components "emerge from actual product needs").

## Removed from an earlier draft (governance corrections 2026-09-01)

- `GET /api/platform/status` and its domain/application/adapter classes and contract test (#2).
- Spring Security + permit-all `WebSecurityConfiguration` (#3) — with no Spring Security on the
  classpath, Actuator health is reachable without any security config.
- GitHub Actions / CI workflow (#4) — deferred to a future Technical Enabler.
- Hard-coded versions: Java 21, Spring Boot 3.5.x, Maven, Angular 20, Node 22, PostgreSQL 16 (#6)
  — now Open questions.

---

## Open questions (must be resolved by a maintainer before implementation — tasks OD-1…OD-6)

| ID | Question | Constraint |
|----|----------|-----------|
| OD-1 | Backend JDK / Java LTS version? | "actively supported release" (technology-policy Version Policy). |
| OD-2 | Spring Boot version line? | Current supported line; determines structured-logging + dependency versions. |
| OD-3 | Backend build tool — Maven or Gradle? | Not fixed by any authoritative doc. |
| OD-4 | Angular major version + Node.js LTS version? | technology-policy names Angular + TypeScript only. |
| OD-5 | PostgreSQL major version? | Needs a pinned tag for `compose.yaml` and the Testcontainers image. |
| OD-6 | Migration tool — confirm Flyway? | technology-policy names Flyway PREFERRED; treat as resolved unless a maintainer objects. |

No `NEEDS CLARIFICATION` on product behavior remain — the enabler is fully specified. The open
items above are technical version/tooling decisions reserved for human approval.

## Resolved Technical Decisions

### OD-1 — Java Version

**Decision:** Java 21 LTS

**Rationale:**
- Long-term support release.
- Mature ecosystem support.
- Compatible with the selected Spring Boot line.
- Appropriate baseline for a new project.

**Status:** Approved

---

### OD-2 — Spring Boot Version

**Decision:** Spring Boot 3.5.x

**Rationale:**
- Stable current Spring Boot generation.
- Compatible with Java 21.
- Provides Actuator, JDBC, Testcontainers integration support and current Spring ecosystem compatibility.

**Status:** Approved

---

### OD-3 — Backend Build Tool

**Decision:** Maven

**Rationale:**
- Simple and explicit build lifecycle.
- Good support for unit/integration test separation.
- Familiar ecosystem support for Spring Boot, Testcontainers and ArchUnit.

**Status:** Approved

---

### OD-4 — Angular / Node.js Baseline

**Decision:**
- Angular 20
- Node.js 22 LTS

**Rationale:**
- Modern supported Angular baseline.
- Node.js LTS provides a stable runtime for local development and tooling.

**Status:** Approved

---

### OD-5 — PostgreSQL Version

**Decision:** PostgreSQL 16

**Rationale:**
- Mature and widely supported major version.
- Suitable for both Docker Compose and Testcontainers.
- No current requirement depends on features from a newer version.

**Status:** Approved

---

### OD-6 — Database Migration Tool

**Decision:** Flyway

**Rationale:**
- Already defined as preferred by the project technology policy.
- Appropriate for versioned relational schema evolution.

**Status:** Approved
