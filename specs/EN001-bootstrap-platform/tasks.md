---
description: "Task list for EN001 — Bootstrap Executable Platform"
---

# Tasks: Bootstrap Executable Platform (EN001)

**Input**: Design documents from `/specs/EN001-bootstrap-platform/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`

**Governance**: `.specify/memory/constitution.md` v1.0.0 (principles I–VIII); `product/engineering/definition-of-done.md`

> **EN001 is a Technical Enabler, not a product feature.** The "US1–US4" phase labels below are a
> Spec Kit formatting convention mapped to the *developer / platform-operator* capability groups in
> `spec.md` — they are **not** investor-facing product User Stories. No business behavior, schema,
> API, authentication, or CI/CD is created here. The goal is the **smallest executable platform**
> that leaves the repository ready for `FD001 — Create Investment Portfolio` to extend.

**Tests**: Included where they validate real infrastructure or real boundaries — the Testcontainers
integration test (FR-025/FR-026) and the ArchUnit conformance guardrail (FR-027). No contract tests
(no business contract exists yet). No unit tests for domain logic (no domain logic exists yet —
per correction #5, real modules emerge with FD001).

---

## Open Decisions (resolve before Phase 1)

Per correction #6, these are **not** decided here. The authoritative documents
(`product/architecture/technology-policy.md`, `EN001-bootstrap-platform.md`) name the technologies
but **not** their versions. A maintainer MUST record choices in `research.md` before T002+.

| ID | Decision needed | Notes / constraints |
|----|-----------------|---------------------|
| OD-1 | Backend JDK / Java LTS version | technology-policy: "actively supported releases". Affects build config, Testcontainers, later CI. |
| OD-2 | Spring Boot version line | Determines built-in structured-logging support and managed dependency versions. |
| OD-3 | Backend build tool (Maven or Gradle) | Not specified by any authoritative doc; the earlier plan draft assumed Maven — treat as open. Affects every backend build/run/test command. |
| OD-4 | Angular major version + Node.js LTS version | technology-policy names Angular + TypeScript only. |
| OD-5 | PostgreSQL major version | Needs a pinned tag for both `compose.yaml` and the Testcontainers image. |
| OD-6 | Relational migration tool | `technology-policy.md` names **Flyway** as PREFERRED — treat as resolved (Flyway) unless a maintainer objects. |

Tasks below reference these as "the OD-n choice" rather than hard-coding a version.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Repository skeleton and buildable (not yet wired) projects.

- [X] T001 Record the OD-1…OD-5 decisions (and confirm OD-6) in `specs/EN001-bootstrap-platform/research.md` under "Open questions"; do not proceed to T002 until done.
- [X] T002 Create the platform directory tree `implementation/platform/{backend/core-service,contracts/openapi,frontend/web,infrastructure/local}` per `plan.md` "Project Structure".
- [X] T003 [P] Scaffold the Spring Boot backend project at `implementation/platform/backend/core-service/` — build tool per OD-3, Java per OD-1, Spring Boot per OD-2. Dependencies: web, actuator, JDBC, `flyway-core`, PostgreSQL driver. Test dependencies: Spring Boot test starter, `org.testcontainers:postgresql`, `com.tngtech.archunit:archunit-junit5`. Configure a unit vs integration (`*IT`) test split. **No** Spring Security dependency.
- [X] T004 [P] Scaffold the Angular frontend project at `implementation/platform/frontend/web/` — Angular/Node versions per OD-4; SCSS, routing, standalone components.
- [X] T005 [P] Add repo-root `.gitignore` (`implementation/platform/.run/`, `implementation/platform/infrastructure/local/.env`, `**/target/`, `**/build/`, `**/dist/`, `**/node_modules/`) and `.editorconfig`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared runtime scaffolding — backend boots and serves `/actuator/health`, frontend
builds, PostgreSQL is defined, config / logging / migrations are wired. No business components.

**⚠️ CRITICAL**: No user-story phase may start until this phase is complete.

- [X] T006 Implement the Spring Boot entry point `implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core/CoreServiceApplication.java` — bootstrap only, no business components.
- [X] T007 [P] Establish the hexagonal package **convention** under `.../src/main/java/com/myfinaimanager/core/`: create `platform/` (placeholder capability namespace) with `package-info.java` files documenting the `domain` / `application` / `adapter` layering and dependency-direction rules, plus a `bootstrap/` package for framework wiring. **Create no production classes** — real modules arrive with FD001 (correction #5).
- [X] T008 [P] Add `.../src/main/resources/application.yml` — env-driven datasource (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`), Flyway enabled with `connect-retries`; Actuator: expose **only** `health`, `management.endpoint.health.show-details=always`, DB health indicator enabled, liveness/readiness probes enabled. No security configuration.
- [X] T009 [P] Add `.../src/main/resources/logback-spring.xml` — structured JSON logs under the local/dev profile; never log credentials (DR-030).
- [X] T010 [P] Create the empty Flyway baseline `.../src/main/resources/db/migration/V1__baseline.sql` — header comment only, **no tables** (data-model.md).
- [X] T011 [P] Create `implementation/platform/infrastructure/local/compose.yaml` — single PostgreSQL service (image tag per OD-5), named volume, `pg_isready` healthcheck, env from `.env`.
- [X] T012 [P] Create `implementation/platform/infrastructure/local/.env.example` — synthetic non-secret placeholders for local DB name / user / password.
- [X] T013 [P] Angular bootstrap under `implementation/platform/frontend/web/src/` — `main.ts`, `app/app.config.ts`, `app/app.routes.ts` (one default route to a placeholder view), minimal `app/app.component.ts|html|scss`.
- [X] T014 [P] Global styling integration point — `implementation/platform/frontend/web/src/styles/styles.scss` + `src/styles/_tokens.scss` (token names aligned with `product/ux/design-system.md`); reference from the Angular build config.
- [X] T015 [P] Create `implementation/platform/contracts/openapi/openapi.yaml` (skeleton: `openapi`, `info`, `servers`, `paths: {}` + a comment that business operations are added by Feature Definitions starting with FD001) and `implementation/platform/contracts/openapi/README.md` describing the contract-first convention and location. No endpoints.

**Checkpoint**: backend compiles and boots locally serving `GET /actuator/health`; `frontend/web` builds.

---

## Phase 3: US1 — One-command local platform lifecycle (Priority: P1) 🎯 MVP

**Capability**: `./implementation/platform/start.sh` brings up PostgreSQL + backend + frontend;
`./implementation/platform/stop.sh` tears them all down. No manual database install.

**Independent Test**: On a clean environment with only documented prerequisites, `start.sh` →
all three processes running (quickstart Scenario 1); `stop.sh` → nothing platform-managed left
running; second `stop.sh` = safe no-op (quickstart Scenario 3). Covers VC-001, VC-002, VC-003,
VC-007, VC-008.

- [X] T016 [US1] Implement `implementation/platform/start.sh` — fail fast with one clear stderr line if Docker is not running; `docker compose -f infrastructure/local/compose.yaml up -d`; wait for the postgres healthcheck; start the backend (OD-3 run command) in the background writing `implementation/platform/.run/backend.pid`; start the frontend dev server in the background writing `.run/frontend.pid`; print the backend URL, the frontend URL, and the `/actuator/health` URL.
- [X] T017 [US1] Implement `implementation/platform/stop.sh` — kill PIDs in `.run/backend.pid` / `.run/frontend.pid` if present (exit 0 as a no-op if absent); `docker compose -f infrastructure/local/compose.yaml down`; remove `implementation/platform/.run/`.
- [X] T018 [P] [US1] Add predictable-failure handling to both scripts — required port in use, missing `infrastructure/local/.env` → single actionable stderr message, no stack traces (spec Edge Cases); a repeated `start.sh` must not spawn conflicting duplicates.
- [X] T019 [P] [US1] Ensure the frontend default route renders a non-blank placeholder view in `implementation/platform/frontend/web/src/app/` so `start.sh` yields a visibly running UI (the real shell is US4).
- [X] T020 [US1] Create `implementation/platform/README.md` (stub) — prerequisites (Docker; JDK per OD-1; Node per OD-4) and `start.sh` / `stop.sh` usage; note first-run dependency download may exceed the SC-006 15-minute budget.
- [X] T021 [US1] Execute quickstart.md Scenario 1 and Scenario 3 end-to-end; record results; close any gap so all three components start and stop through the scripts.

**Checkpoint**: MVP — the executable platform starts and stops with one command each.

---

## Phase 4: US2 — Backend health & PostgreSQL connectivity (Priority: P2)

**Capability**: The Spring Boot Actuator health mechanism confirms the backend runtime is healthy
and reflects PostgreSQL connectivity. **No custom endpoint, no hexagon slice, no contract test**
(correction #2) — Actuator's built-in `db` health indicator is the mechanism.

**Independent Test**: With the platform running, `GET /actuator/health` returns `UP` with a `db`
component `UP`; stopping PostgreSQL yields `OUT_OF_SERVICE`/`DOWN` (no stack trace) that recovers
without a restart (quickstart Scenario 2). Covers VC-004, VC-005.

- [X] T022 [US2] Verify and, if needed, adjust the Actuator configuration in `implementation/platform/backend/core-service/src/main/resources/application.yml` so `GET /actuator/health` reports `UP` + `db: UP` when PostgreSQL is reachable and a degraded status (no stack trace) when it is not, recovering automatically when it returns.
- [X] T023 [US2] Execute quickstart.md Scenario 2 against the running platform (healthy → DB stopped → recovery); record results.

**Checkpoint**: Runtime health and DB-connectivity are observable through the standard framework mechanism.

---

## Phase 5: US3 — Reproducible integration testing against disposable PostgreSQL (Priority: P2)

**Capability**: The backend test suite provisions its own disposable PostgreSQL via Testcontainers —
no manually installed database (FR-025, FR-026).

**Independent Test**: With no PostgreSQL on the host and the platform not running, the backend
`verify` build starts a disposable PostgreSQL container, runs at least one integration test that
proves real connectivity + migration, and disposes the container (quickstart Scenario 4).
Covers VC-006.

- [X] T024 [P] [US3] Add `implementation/platform/backend/core-service/src/test/java/com/myfinaimanager/core/support/PostgresContainerSupport.java` — JUnit 5 `@Testcontainers`, PostgreSQL container (image tag per OD-5), `@DynamicPropertySource` binding `SPRING_DATASOURCE_*`.
- [X] T025 [US3] Add integration test `implementation/platform/backend/core-service/src/test/java/com/myfinaimanager/core/bootstrap/PlatformIntegrationIT.java` — boots the full application context against the disposable container; asserts Flyway applied `V1__baseline.sql` (`flyway_schema_history` present), a `SELECT 1` via the configured `DataSource` succeeds, and `GET /actuator/health` returns `UP` with `db: UP`. Container disposed automatically; no host-installed DB (depends on T006, T008, T010, T024).
- [X] T026 [US3] Execute quickstart.md Scenario 4 locally with no host PostgreSQL (backend `verify` build); record results.

**Checkpoint**: Integration-test baseline is reproducible on any machine with a container runtime.

---

## Phase 6: US4 — Structural baseline ready for vertical feature extension (Priority: P3)

**Capability**: A coherent, inspectable structure — documented hexagonal conventions with an active
ArchUnit guardrail, an Angular app shell aligned to the design system, a contracts location, and
demonstrably no business behavior — that FD001 will extend.

**Independent Test**: the ArchUnit test runs green and would fail on a forbidden dependency; the
repo layout and skeleton OpenAPI match `plan.md`; the frontend shows an app shell with no product
navigation; a grep for business terms across `implementation/platform/` finds no behavior
(quickstart Scenarios 5 & 6). Covers VC-009.

- [X] T027 [P] [US4] Add ArchUnit guardrail `implementation/platform/backend/core-service/src/test/java/com/myfinaimanager/core/architecture/HexagonalArchitectureRulesTest.java` — encode layering rules for the `..platform..` namespace: `..domain..` must not depend on `..application..`/`..adapter..`/`..bootstrap..`/`org.springframework..`/`java.sql..`; `..application..` must not depend on `..adapter..`/`..bootstrap..`/Spring; inbound adapters must not depend on outbound adapters. Add a comment noting the rules are guardrails now and become substantive when FD001 adds real modules.
- [X] T028 [P] [US4] Frontend app shell under `implementation/platform/frontend/web/src/app/core/layout/` — `AppShellComponent`, `SidebarComponent`, `TopBarComponent`: dark three-area layout (top bar + sidebar + content), structure only, **no navigation entries for capabilities that do not exist** (design-system.md).
- [X] T029 [US4] Wire the shell into `implementation/platform/frontend/web/src/app/app.component.ts|html|scss`; the default route renders an empty content area inside the shell; consume tokens from `src/styles/_tokens.scss` (depends on T028, T014).
- [X] T030 [P] [US4] Expand `implementation/platform/README.md` — directory map, hexagonal layer convention, where FD001 adds its module, `start.sh`/`stop.sh`, and how to run the backend tests.
- [X] T031 [US4] Execute quickstart.md Scenario 5 and Scenario 6 — ArchUnit green; structure matches plan; `grep -riE 'portfolio|position|valuation|risk|recommendation|stop-loss' implementation/platform/` returns only incidental matches (VC-009); record results.

**Checkpoint**: All four capability groups verified; platform ready for FD001.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Definition-of-Done gates that span the whole enabler.

- [X] T032 [P] Add code-coverage reporting to the backend build (tool appropriate to OD-3), excluding only `CoreServiceApplication` and framework wiring; report on the `verify` build. No numeric gate is enforced by EN001 (there is no behavioral code yet — DoD §5 applies once FD001 adds logic).
- [X] T033 [P] Security/privacy pass — confirm `infrastructure/local/.env` is git-ignored, no credentials or tokens anywhere in `implementation/platform/` source, structured logs emit no secrets (DR-030, AR-037, FR-031).
- [X] T034 Execute the full quickstart.md (Scenarios 1–6) end-to-end and complete the VC-001…VC-009 evidence table in `quickstart.md`.
- [ ] T035 [P] Assemble PR evidence per `product/engineering/definition-of-done.md` "Minimum Pull Request Evidence" in the PR description; explicitly note that EN001 defers CI/CD, authentication, and any business OpenAPI contract to later work, and flag for maintainers (do **not** edit `product/` without approval — constitution I) whether `product/architecture/diagrams/containers.md` should annotate the realized `core-service`.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: T001 (decisions) blocks everything; then T002 blocks T003–T005.
- **Foundational (Phase 2)**: after Setup. **Blocks all capability phases.**
- **US1 (Phase 3, P1)**: after Foundational. Independent of US2–US4.
- **US2 (Phase 4, P2)**: after Foundational. Config-and-verify only; independent of US1.
- **US3 (Phase 5, P2)**: after Foundational (T025 depends on T006/T008/T010/T024). Independent of US1/US2, though it re-verifies US2's Actuator behavior.
- **US4 (Phase 6, P3)**: after Foundational. T027 depends on the T007 package convention; frontend tasks depend on T013/T014.
- **Polish (Phase 7)**: after the targeted capability phases.

### Dependency summary

```text
T001 → T002 → Foundational ┬→ US1 (P1, MVP)
                           ├→ US2 (P2)
                           ├→ US3 (P2)
                           └→ US4 (P3)
                                        → Polish
```

### Parallel opportunities

- Setup: T003, T004, T005 in parallel after T002.
- Foundational: T007–T015 all `[P]` after T006.
- US1: T018 + T019 in parallel after T016/T017.
- US3: T024 before T025.
- US4: T027 + T028 + T030 in parallel; T029 after T028.
- Across people: after Foundational, one takes US1, another US2+US3, another US4.

---

## Parallel Example: Foundational phase

```bash
# After T006 (entry point), run these together — all different files:
Task: "T007 Establish hexagonal package convention + package-info docs"
Task: "T008 application.yml (datasource + Flyway + Actuator health)"
Task: "T009 logback-spring.xml structured logging"
Task: "T010 V1__baseline.sql empty baseline"
Task: "T011 infrastructure/local/compose.yaml (PostgreSQL)"
Task: "T012 infrastructure/local/.env.example"
Task: "T013 Angular bootstrap (main.ts, app.config, routes, app.component)"
Task: "T014 styles.scss + _tokens.scss"
Task: "T015 contracts/openapi/openapi.yaml skeleton + README"
```

---

## Implementation Strategy

### MVP first (US1 only)

1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 US1 → 4. **STOP & VALIDATE**: quickstart
Scenarios 1 & 3 (VC-001, 002, 003, 007, 008). A demoable executable platform.

### Incremental delivery

1. Setup + Foundational → foundation ready.
2. + US1 → one-command lifecycle (MVP).
3. + US2 → Actuator health reflects DB connectivity.
4. + US3 → reproducible Testcontainers integration test.
5. + US4 → architecture guardrail, app shell, VC-009 evidence.
6. Polish → coverage wiring, security pass, full VC-001…VC-009 sign-off.

### Constitution / DoD checkpoints

- Every phase keeps `start.sh` / `stop.sh` valid (constitution V; correction #8).
- No production domain/application classes are created (correction #5); T007 establishes conventions,
  T027 enforces them.
- T025 uses a real disposable container, never a mock (constitution VII; correction #7).
- No Spring Security, no CI workflow, no business schema/API/endpoint (corrections #2–#4, #10).
- Versions are decided by a maintainer in T001, not by the implementer (correction #6).
- Completion judged by `definition-of-done.md`; T034 maps evidence to VC-001…VC-009.

---

## Notes

- `[P]` = different files, no dependency on an incomplete task.
- Commit after each task or logical group; keep the platform buildable at every checkpoint.
- If any task appears to need a business schema, a business endpoint, authentication, CI, or a
  chosen version not covered by an OD, **stop and surface it** (constitution IV).
- Total: 35 tasks — Setup 5, Foundational 10, US1 6, US2 2, US3 3, US4 5, Polish 4.

---

## Implementation Status — 2026-09-01

**Decisions**: OD-1…OD-6 resolved by a maintainer in `research.md` → Java 21 · Spring Boot 3.5.6 ·
Maven · Angular 20 + Node 22 LTS · PostgreSQL 16 (`postgres:16-alpine`) · Flyway.

**Done & verified: 34 / 35** (only T035 — PR assembly — is not applicable in this session).

### Backend (`mvn clean verify` — BUILD SUCCESS)

- `HexagonalArchitectureRulesTest` (ArchUnit) — **4/4 pass**
- `PlatformIntegrationIT` (Testcontainers `postgres:16-alpine`, full Spring context) — **3/3 pass**:
  Flyway applies `V1__baseline.sql`, `SELECT 1` via the `DataSource` succeeds, `/actuator/health`
  = `UP` with `db: UP`. JaCoCo report generated. (VC-006)
- Fix for modern Docker Engines (min API 1.44): `api.version=1.44` system property on the Failsafe
  fork — see `research.md` D11 and the `pom.xml` comment.

### Frontend (`npm ci` + `ng build` + `ng test`)

- Production `ng build` ✅ (226 kB initial) · `ng test` **3/3 pass** (headless Chrome) — shell
  renders sidebar + top bar, zero product nav entries.
- Note: Angular 20 CLI needs Node ≥ 20.19 / ≥ 22.12 (documented in README).

### Full-stack lifecycle (`./start.sh` → `./stop.sh`, live)

| Check | Result |
|---|---|
| `start.sh` → PostgreSQL healthy | ✅ VC-003 |
| `start.sh` → backend up, `GET /actuator/health` = `UP`, `db: UP` | ✅ VC-002 / VC-004 / VC-005 / VC-007 |
| `start.sh` → frontend serves `HTTP 200` on :4200 | ✅ VC-001 |
| Stop PostgreSQL → `/actuator/health` = `503 DOWN`, `db: DOWN` (clean error, no stack trace, no crash) | ✅ (spec edge case) |
| Restart PostgreSQL → health auto-recovers to `200 UP`, no app restart | ✅ (spec edge case) |
| `stop.sh` → backend + frontend killed, postgres container stopped & removed, network removed | ✅ VC-008 |
| second `stop.sh` → safe no-op, exit 0 | ✅ |
| `start.sh` with Docker daemon down → fail-fast, one clear stderr line, exit 1 | ✅ (spec edge case) |

### VC-009 & security

- No portfolio / valuation / risk / recommendation / stop-loss behaviour anywhere in
  `implementation/platform/` (matches are doc/comment references to FD001 only).
- No Spring Security dependency; no `.github/` CI; `openapi.yaml` has `paths: {}`;
  `V1__baseline.sql` creates no tables; no production domain/application classes.
- `infrastructure/local/.env` is git-ignored; no secrets in source; structured JSON logging.

### Known environment note

`docker-java` (bundled by Testcontainers) does not read the `DOCKER_API_VERSION` env var and
falls back to API 1.32, which Docker Engine 28+ rejects. The `pom.xml` sets `api.version=1.44` for
the Failsafe fork so `mvn verify` works on colima / Rancher Desktop / Docker Desktop / OrbStack /
CI without per-machine setup. Separately, if a global `DOCKER_DEFAULT_PLATFORM=linux/amd64` is set
alongside a previously-cached native-arch `postgres:16-alpine`, `docker compose` can report a
platform mismatch — `docker rmi postgres:16-alpine` then re-run `start.sh` (noted in the README).

### Remaining

- **T035** — assemble PR evidence per `definition-of-done.md` when the PR is opened; flag to
  maintainers whether `product/architecture/diagrams/containers.md` should now annotate the
  realized `core-service` (do not edit `product/` without approval — constitution I).

---

## Phase 8: Convergence

Assessed 2026-09-01 against `spec.md`, `plan.md`, `research.md`, and constitution v1.0.0. One gap
found. (Backend startup tolerance to PostgreSQL-not-ready — US2/AS4 + edge case — was re-verified
live and **passes**: Flyway `connect-retries` lets the backend converge to healthy without a
restart. `DOCKER_DEFAULT_PLATFORM` friction and the `api.version=1.44` fix are already justified
in `research.md` D11 / the README — no task needed.)

- [X] T036 Make the backend actually emit structured, machine-readable logs per FR-008 / DR-029 (partial). The intent is configured (`application.yml` → `logging.structured.format.console: ${LOG_STRUCTURED_FORMAT:ecs}`) but `src/main/resources/logback-spring.xml` includes Spring Boot's plain `console-appender.xml`, which overrides it — `mvn spring-boot:run` was observed emitting the default plain-text pattern (`2026-... INFO 75351 --- [core-service] ...`), not ECS JSON. Fix so console output is structured JSON by default: either delete `logback-spring.xml` and rely on Spring Boot's built-in structured logging, or have `logback-spring.xml` use `StructuredLogEncoder` (keep the "no secrets in logs" comment / DR-030). Verify by running the backend and confirming JSON lines (`"@timestamp"`, `"log.level"`, `"ecs.version"`); keep an env override for plain console during local debugging. Update `research.md` D9 and the Implementation Status note ("structured JSON logging") to match reality.

  **Done 2026-09-01**: removed `logback-spring.xml`; kept `application.yml`'s
  `logging.structured.format.console: ${LOG_STRUCTURED_FORMAT:ecs}` (+ DR-030 no-secrets comment).
  Verified — `mvn spring-boot:run` emits ECS JSON (`"@timestamp"` / `"log.level"` /
  `"ecs.version":"8.11"` / `"service.name":"core-service"`, 25/25 lines); `LOG_STRUCTURED_FORMAT=`
  gives plain console; health still `UP`/`db: UP`; `mvn clean verify` green (ArchUnit 4/4, IT 3/3).
  `research.md` D9 updated. The Implementation Status "structured JSON logging" line is now
  accurate — no correction needed.
