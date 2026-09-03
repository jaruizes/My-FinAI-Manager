# Feature Specification: Establish Containerized End-to-End Testing Foundation (EN002)

**Feature Branch**: `EN002-containerized-e2e-testing-foundation`

**Created**: 2026-09-01

**Status**: Draft

**Input**: Technical Enabler: "Create the formal SDD specification for EN002 — Establish Containerized End-to-End Testing Foundation. This is a technical enabler, not a product feature. Do not invent user stories or business functionality beyond the minimum smoke test needed to prove the E2E infrastructure."

**Authoritative Source**: `product/definition/enablers/EN002-containerized-e2e-testing-foundation/EN002-containerized-e2e-testing-foundation.md` (Status: **Approved**, 2026-09-01, incl. resolved decisions OD-1…OD-9 and a completed Human Approval checklist)

**Governing Architecture Decision**: `product/architecture/adrs/ADR-001-initial-backend-topology.md` (unchanged — `core-service` remains the single backend deployable)

---

## Enabler Nature *(mandatory)*

EN002 is a **Technical Enabler**, not a product Feature Definition.

It introduces **no investor-facing behavior**, no Portfolio/valuation/risk logic, and no business
API. Its purpose is to change the *local runtime topology* established by EN001 — moving the
backend and frontend from host processes into containers — and to add a reusable, fully
containerized **browser-based End-to-End (E2E) testing foundation** using Playwright.

Because this is an enabler:

- The scenarios below describe **developer / platform-operator workflows**, not investor journeys.
- "Acceptance" is expressed as the enabler's **Verification Criteria (VC-001 … VC-015)** from the
  authoritative source.
- No business domain entities are defined. The Information Model under `product/definition/global/`
  is unaffected.
- The only E2E test EN002 delivers is a **platform smoke test** that proves
  `Playwright → frontend → containerized stack` works. Feature-specific E2E journeys (e.g. for
  FD001) are explicitly **out of scope** and are the responsibility of the owning Feature
  Definition.

---

## User Scenarios & Testing *(mandatory)*

The beneficiaries of this enabler are the **engineering team** (human and AI contributors) who
run the platform locally, verify features, and will add E2E coverage for future Feature
Definitions on top of this foundation.

### User Story 1 - The whole platform runs as containers, started and stopped with one command (Priority: P1)

As a developer, I can start the complete local platform — database, backend, and frontend — as
containers with a single canonical command, and stop it with a single canonical command, without
running a Spring Boot process, an Angular dev server, or a database on my host.

**Why this priority**: This is the containerization half of EN002 and the prerequisite for
everything else. A reproducible container runtime is what E2E tests, feature verification, and
future CI all depend on. Delivering only this story already yields a fully containerized platform
baseline.

**Independent Test**: On a clean machine with only Docker (and Docker Compose) installed, run
`./implementation/platform/start.sh`; observe that PostgreSQL, backend, and frontend all reach a
healthy container state and that the frontend and backend URLs are reported; confirm no backend or
frontend process is running directly on the host; then run `./implementation/platform/stop.sh` and
observe the whole platform is torn down. Run `stop.sh` again and observe a safe no-op.

**Acceptance Scenarios**:

1. **Given** a clean environment with only Docker available, **When** the developer runs `start.sh`, **Then** PostgreSQL (VC-003), the backend (VC-001), and the frontend (VC-002) start as Docker Compose services and the script reports the frontend and backend URLs.
2. **Given** `start.sh` has completed, **When** the developer inspects running processes and the container runtime, **Then** the backend and frontend run **only** as containers — no host-level Spring Boot or `ng serve` process exists (VC-004).
3. **Given** the platform is running, **When** the developer queries the backend health endpoint and the frontend URL, **Then** the backend reports healthy with confirmed PostgreSQL connectivity and the frontend serves its application shell (VC-006).
4. **Given** the platform is running, **When** the developer runs `stop.sh`, **Then** the complete platform is stopped through Docker Compose (VC-005).
5. **Given** the platform is already stopped, **When** the developer runs `stop.sh` again, **Then** it exits successfully without error (VC-005).
6. **Given** Docker is not running, a required port is occupied, or required Compose configuration is missing, **When** the developer runs `start.sh`, **Then** it fails fast with a clear, non-technical message and a non-zero exit code.
7. **Given** the platform did not become ready within the bounded startup timeout, **When** `start.sh` gives up, **Then** it reports which component failed readiness and exits non-zero.

---

### User Story 2 - Run the browser E2E smoke test against the containerized platform with one command (Priority: P1)

As a developer, I can run `./implementation/platform/e2e.sh` and have Playwright execute — inside
its own container, with no browser installed on my host — a browser-level smoke test that opens
the running frontend and confirms the containerized stack is wired together end to end. The
command returns a non-zero exit code when the E2E suite fails.

**Why this priority**: This is the E2E half of EN002 and the reason the enabler exists — to catch
integration mismatches (wrong API base path, unready components, frontend/backend config drift)
that layer-isolated tests miss. It is independently valuable: even with just the smoke test, every
future feature inherits a working E2E harness.

**Independent Test**: On a machine with Docker but no locally installed Playwright browsers, run
`./implementation/platform/e2e.sh`; observe that the required images build, an isolated
containerized platform starts, readiness is confirmed, the Playwright container runs the smoke
test against the real frontend, and the script exits with Playwright's exit code. Temporarily
break the frontend→backend wiring and confirm `e2e.sh` exits non-zero.

**Acceptance Scenarios**:

1. **Given** Docker is available and no Playwright browsers are installed on the host, **When** the developer runs `e2e.sh`, **Then** Playwright Test executes inside a container using bundled browser binaries (VC-007).
2. **Given** the containerized platform is running for E2E, **When** the smoke test executes, **Then** it drives the **frontend** entry point in a real browser (it does not call backend APIs directly as its primary check) and exercises `frontend → backend → PostgreSQL` (VC-008).
3. **Given** the containerized platform is healthy, **When** the smoke test runs, **Then** the frontend application shell renders and no platform-level runtime/navigation error blocks interaction, and the test passes (VC-009).
4. **Given** the E2E suite fails, **When** `e2e.sh` finishes, **Then** it returns a non-zero exit code; **Given** the E2E suite passes, **Then** it returns zero (VC-011).
5. **Given** PostgreSQL, backend, or frontend is not yet ready, **When** E2E execution starts, **Then** Playwright only begins after explicit readiness checks with bounded timeouts pass, not after a fixed sleep (VC-012).

---

### User Story 3 - E2E runs are isolated and repeatable and never destroy local development data (Priority: P2)

As a developer, I can run the E2E suite repeatedly and get the same result each time, and running
E2E never deletes or mutates the data in my normal local platform.

**Why this priority**: An E2E harness that contaminates itself between runs, or that wipes a
developer's working data, will be distrusted and abandoned. Isolation and repeatability are what
make the smoke test a reliable signal and a safe habit.

**Independent Test**: Start the normal platform and create some local data. Run `e2e.sh` two or
three times in a row. Confirm every run produces the same pass/fail result with no manual cleanup,
and confirm the normal platform's data is untouched afterwards.

**Acceptance Scenarios**:

1. **Given** a normal local platform with developer data, **When** `e2e.sh` runs, **Then** it uses an isolated Docker Compose project and disposable volumes so the normal platform's database and volumes are never affected (VC-010; OD-6).
2. **Given** a previous E2E run has completed, **When** `e2e.sh` runs again, **Then** it does not depend on artifacts, rows, or containers left by the earlier run — each run starts from a clean, disposable database (VC-010).
3. **Given** `e2e.sh` finishes (pass or fail), **When** the environment is inspected, **Then** the isolated E2E environment and its volumes have been torn down (OD-7).
4. **Given** the E2E tests need data, **When** they run, **Then** they use synthetic values only — never real personal portfolio information — and prefer the simplest reliable isolation strategy (unique generated values and/or a disposable database) over dedicated test-only application behavior.

---

### User Story 4 - A failing E2E test is diagnosable, and the workflow (including adding feature tests) is documented (Priority: P3)

As a developer, when an E2E test fails I get useful evidence (screenshot, trace) without digging,
and I can follow repository documentation to start the containerized platform, run all or one E2E
test, debug a failure, and add a feature-specific E2E test for a future Feature Definition.

**Why this priority**: The foundation is only reusable if the next person can operate and extend
it. This depends on US1–US3 existing but is verified largely by inspection and by following the
docs.

**Independent Test**: Force a smoke-test failure and confirm a screenshot and a Playwright trace
for the failed test are produced in the E2E artifacts location (and are git-ignored). Then, as a
new contributor, follow the documentation to start the platform, run the full E2E suite, run a
single test, and locate where a `FD001-…spec.ts` test would go.

**Acceptance Scenarios**:

1. **Given** an E2E test fails, **When** the run completes, **Then** a screenshot on failure and a retained Playwright trace for that test are available in `implementation/platform/e2e/test-results/`, and these artifacts are not committed to the repository (VC-013; OD-8).
2. **Given** an E2E run succeeds, **When** it completes, **Then** it does not retain excessive artifacts (no trace/video kept for passing tests by default) (OD-8).
3. **Given** diagnostic artifacts are produced, **When** they are reviewed, **Then** they contain no secrets or credentials (VC-013).
4. **Given** the repository after EN002, **When** the documentation is followed, **Then** a contributor can build/start the containerized platform, stop it, run the E2E suite, run a single E2E test, debug a failure, and add a feature-specific E2E test (VC-014).
5. **Given** the testing documentation, **When** it is read, **Then** it places E2E alongside Unit / Integration / Contract / Architecture tests with distinct, complementary roles and states that the E2E suite stays deliberately small and journey-focused (§22 of the enabler).

---

### Edge Cases

- **Docker unavailable**: `start.sh` and `e2e.sh` must fail fast with a clear message, not hang or emit a stack trace.
- **Required port occupied**: When the frontend, backend, or database port is in use, `start.sh` must surface an explicit, actionable error. (The isolated E2E environment should avoid fixed host-port collisions with the normal platform.)
- **Component never becomes ready**: Readiness waits must have bounded timeouts; on timeout the script reports which component failed and exits non-zero.
- **Browser cannot resolve Compose DNS**: Browser-side frontend code must reach the backend only via the frontend's reverse proxy (relative `/api`), never via a Docker service name (OD-2, OD-5).
- **`e2e.sh` invoked while the normal platform is running**: E2E must run in its own isolated Compose project so the two do not conflict and developer data is preserved.
- **Stale E2E environment from a crashed run**: A subsequent `e2e.sh` must be able to reclaim/recreate its isolated environment without manual cleanup.
- **Image out of date**: Normal `start.sh` uses existing images and does not rebuild unconditionally; rebuilding is a deliberate, explicit action (OD-9).
- **Secrets**: No credentials in images, Compose files, Dockerfiles, or diagnostic artifacts; local credentials are non-production synthetic values supplied via environment/configuration.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Containerized platform — backend image

- **FR-001**: The backend MUST provide a Dockerfile at `implementation/platform/backend/core-service/Dockerfile` that builds/packages `core-service` reproducibly and runs the existing service unchanged (no business behavior, no architecture change).
- **FR-002**: The backend image MUST use a multi-stage build so the runtime image contains a Java 21 runtime (Eclipse Temurin JRE) and the application only — **no** Maven/Gradle build tooling (OD-1).
- **FR-003**: The backend container MUST expose its configured application port, obtain all database configuration from environment variables, embed no credentials, and support a Compose health/readiness check via the existing Spring Boot health endpoint.

#### Containerized platform — frontend image

- **FR-004**: The frontend MUST provide a Dockerfile at `implementation/platform/frontend/web/Dockerfile` that builds the Angular production bundle reproducibly and serves it from a lightweight static web server (nginx) — `ng serve` MUST NOT be used in the runtime image (OD-2).
- **FR-005**: The frontend runtime (nginx) MUST serve static assets at `/` and reverse-proxy `/api/*` to the backend Compose service; the Angular application MUST call the backend using **relative** `/api` URLs and MUST NOT depend on Docker service DNS names or environment-specific hostnames in source (OD-2, OD-5).
- **FR-006**: The frontend container MUST expose its HTTP port and support a Compose HTTP readiness check.

#### Containerized platform — orchestration & networking

- **FR-007**: The canonical local Compose file MUST remain `implementation/platform/infrastructure/local/compose.yaml` and MUST orchestrate `postgres`, `backend`, and `frontend`, plus an opt-in `e2e` service behind an explicit Compose profile (or equivalent) that is NOT started by default.
- **FR-008**: Compose services MUST declare explicit dependency + readiness ordering: `backend` waits for `postgres` healthy; `frontend` waits for `backend` healthy; `e2e` (when requested) waits for `frontend` healthy.
- **FR-009**: Services MUST communicate over the Docker Compose network using service names (`postgres`, `backend`, `frontend`); the only browser-to-backend path MUST be through the frontend reverse proxy (FR-005).
- **FR-010**: Readiness/health checks MUST be explicit with bounded timeouts and actionable failure output: PostgreSQL via `pg_isready` (or equivalent), backend via the Spring Boot health endpoint, frontend via an HTTP check. Arbitrary fixed sleeps MUST NOT be the primary readiness mechanism.

#### Platform lifecycle

- **FR-011**: `implementation/platform/start.sh` MUST start the complete platform (`postgres`, `backend`, `frontend`) through Docker Compose, wait for the required health checks, and report the frontend and backend URLs. It MUST NOT start Spring Boot or Angular as host processes.
- **FR-012**: `implementation/platform/stop.sh` MUST stop the complete containerized platform through Docker Compose and MUST be a safe successful no-op when nothing is running. After EN002 the lifecycle scripts MUST NOT rely on PID files for backend/frontend processes.
- **FR-013**: `start.sh` MUST fail fast with clear, non-technical messages and a non-zero exit code when Docker is unavailable, a required port is unavailable, required Compose configuration is missing, or the platform does not become ready within a bounded timeout.
- **FR-014**: `start.sh` MUST use existing images for normal startup and MUST NOT rebuild images unconditionally; rebuilding MUST be a deliberate, explicit action (a documented build option or an explicit `docker compose build`) (OD-9).
- **FR-015**: `start.sh` and `stop.sh` MUST remain the only supported way to run/stop the local platform; no alternative undocumented mechanism may supersede them. Local build tools MAY still be used for development tasks (compilation, unit/integration tests).

#### E2E framework & runner

- **FR-016**: Playwright MUST be introduced as the project's approved browser-based E2E testing framework, located in a dedicated area `implementation/platform/e2e/` (`package.json`, `playwright.config.*`, `tests/`, `support/`, `Dockerfile`, `README.md`).
- **FR-017**: Playwright MUST run inside its own container built from the official Playwright image, pinned to a version that matches the project's Playwright dependency; the image MUST include the Node runtime, Playwright Test, the required browser binaries, the test sources, config, and support utilities (OD-3).
- **FR-018**: A developer MUST be able to run the E2E suite without any locally installed Playwright browser binaries.
- **FR-019**: The initial browser baseline MUST be **Chromium only** (OD-4); multi-browser execution is out of scope for EN002.
- **FR-020**: `implementation/platform/e2e.sh` MUST be the canonical E2E entry point. It MUST: build the required images, start an isolated `postgres`+`backend`+`frontend` environment, wait for readiness, execute the Playwright container, propagate Playwright's exit code, and tear the isolated environment down (including volumes) (OD-7). It MUST delegate runtime orchestration to Docker Compose and MUST NOT start application components directly on the host.

#### E2E target & smoke test

- **FR-021**: E2E tests MUST begin from the user-facing frontend entry point in a real browser and MUST NOT bypass the frontend by calling backend APIs directly as the primary validation mechanism; backend calls MAY be used only as secondary, explicitly justified test-support.
- **FR-022**: EN002 MUST include at least one platform-level E2E smoke test (`implementation/platform/e2e/tests/platform-smoke.spec.ts`) that, against the running containerized platform, opens the frontend, asserts the application shell renders, and asserts no platform-level runtime/navigation error prevents interaction.
- **FR-023**: The smoke test MUST NOT implement or assert Portfolio-specific or any other business behavior; its sole purpose is to prove `Playwright → frontend → containerized platform`.
- **FR-024**: E2E tests MUST describe user-observable behavior and MUST be named traceably: platform tests as `platform-smoke.spec.ts`, and (by convention, for future features) feature tests as `FD00N-<slug>.spec.ts`.

#### E2E environment isolation & test data

- **FR-025**: Each `e2e.sh` execution MUST use an isolated Docker Compose project and disposable volumes so that normal local development data is never deleted or mutated by E2E (OD-6).
- **FR-026**: Repeated E2E executions MUST NOT become dependent on artifacts from earlier runs; each run MUST start from a clean, disposable database.
- **FR-027**: E2E tests MUST use synthetic test data only and MUST NOT use real personal portfolio information. The isolation/reset strategy MUST prefer the simplest reliable option (unique generated values and/or a disposable database) and MUST NOT introduce production-only behavior; dedicated test-support endpoints are allowed only if explicitly approved in the plan.

#### E2E diagnostics

- **FR-028**: On test failure, Playwright MUST capture a screenshot and retain a trace for the failed test; video is disabled initially (OD-8).
- **FR-029**: Generated E2E artifacts MUST be written to `implementation/platform/e2e/test-results/` and MUST NOT be committed to the repository (git-ignored) (OD-8).
- **FR-030**: Successful runs MUST NOT retain excessive artifacts (no trace/video for passing tests by default). Diagnostic artifacts MUST NOT expose secrets or credentials.

#### Documentation & testing-strategy integration

- **FR-031**: The repository MUST document how to: build/start the containerized platform, stop it, run the full E2E suite, run a single E2E test, debug a failing E2E test, and add a feature-specific E2E test (VC-014).
- **FR-032**: Project testing documentation MUST position E2E alongside Unit, Integration, Contract, and Architecture tests with complementary roles, state that E2E does not replace lower-level tests, and state that the E2E suite stays deliberately small and focused on critical journeys.
- **FR-033**: EN002 MUST establish the convention (for future Feature Definitions, not implemented here) that a feature introducing or materially changing a critical `frontend → backend → persistence` journey normally requires at least one passing E2E test before it can be considered ready to close, and that the closure verifier checks this.

#### Technology & scope constraints

- **FR-034**: EN002 MUST use only Playwright (newly approved by this enabler as the initial browser-based E2E framework), Docker / Docker Compose (canonical local orchestration), nginx (frontend static runtime + `/api` proxy), an Eclipse Temurin Java 21 JRE base image, and the official Playwright base image. Any additional runtime infrastructure requires architecture review / an ADR.
- **FR-035**: EN002 MUST NOT change the backend service topology (ADR-001): `core-service` remains the single backend deployable component. Containerizing it does not split it.
- **FR-036**: EN002 MUST NOT introduce Portfolio, valuation, risk, recommendation, stop-loss, portfolio-review, or any other business behavior; MUST NOT introduce authentication/authorization infrastructure; MUST NOT introduce CI/CD (no GitHub Actions workflow), performance/load/visual-regression/mobile-native testing, multi-browser execution, or external market-data/news/LLM/SaaS integration testing (VC-015).
- **FR-037**: No secrets (credentials, tokens, certificates) may be committed in Dockerfiles, Compose files, E2E config, or artifacts; configuration MUST be externalized and local credentials MUST be non-production synthetic values.

### Key Entities

*None.* EN002 introduces no business or persistent domain entities. The only new artifacts are
infrastructure and test assets (Dockerfiles, Compose services, Playwright project, lifecycle
scripts).

### Traceability to Enabler Verification Criteria

| Enabler VC | Description | Covered by |
|---|---|---|
| VC-001 | Backend container builds & runs as a Compose service | US1 (AS1); FR-001, FR-002, FR-003 |
| VC-002 | Frontend container builds & is served as a Compose service | US1 (AS1); FR-004, FR-005, FR-006 |
| VC-003 | PostgreSQL runs in the Compose topology; backend connects | US1 (AS1, AS3); FR-007, FR-008 |
| VC-004 | Fully containerized start — no host backend/frontend process | US1 (AS2); FR-011, FR-012 |
| VC-005 | Fully containerized stop; safe when already stopped | US1 (AS4, AS5); FR-012 |
| VC-006 | After start: frontend reachable, backend healthy, DB healthy | US1 (AS3); FR-010, FR-011 |
| VC-007 | Playwright runs in a container, no host browser binaries | US2 (AS1); FR-017, FR-018 |
| VC-008 | E2E executes against the actual running frontend & stack | US2 (AS2); FR-020, FR-021 |
| VC-009 | Platform smoke test passes against the containerized platform | US2 (AS3); FR-022, FR-023 |
| VC-010 | Smoke test repeatable without manual cleanup / contamination | US3 (AS1, AS2, AS3); FR-025, FR-026 |
| VC-011 | `e2e.sh` is a stable entry point; non-zero exit on failure | US2 (AS4); FR-020 |
| VC-012 | Explicit readiness/health checks with bounded timeouts | US2 (AS5); FR-010 |
| VC-013 | A failing E2E test produces useful diagnostics; no secrets | US4 (AS1, AS3); FR-028, FR-030 |
| VC-014 | Documentation covers build/start/stop/run/single/debug/add | US4 (AS4); FR-031 |
| VC-015 | No product scope introduced | US4 (AS5); FR-035, FR-036 |

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer on a clean machine with only Docker installed can bring up the entire platform (database, backend, frontend) as containers with **one command** and no host-installed database, backend, or Angular dev server.
- **SC-002**: After `start.sh` completes, all three platform components run **only** as containers — an inspection of host processes finds no Spring Boot or `ng serve` process.
- **SC-003**: After a successful `start.sh`, the frontend is reachable and the backend health check reports healthy with confirmed database connectivity within 90 seconds.
- **SC-004**: One `stop.sh` command leaves zero platform containers running; a second `stop.sh` exits 0.
- **SC-005**: A single command (`e2e.sh`) runs the browser smoke test against the containerized stack and exits 0 on success / non-zero on failure, with no Playwright browser installed on the host.
- **SC-006**: The smoke test run three times consecutively produces the same result each time with no manual cleanup between runs.
- **SC-007**: Running `e2e.sh` while the normal platform holds developer data leaves that data and its volumes unchanged (verified before/after).
- **SC-008**: When an E2E test fails, a screenshot and a trace for that test are available in the E2E artifacts directory within the same run, and none of the produced artifacts are tracked by git.
- **SC-009**: A new contributor following the documentation reaches a first successful containerized `start.sh` and a first successful `e2e.sh` in under 20 minutes (excluding one-time image downloads).
- **SC-010**: A review of the resulting change finds no Portfolio or other business behavior, no authentication infrastructure, and no CI/CD workflow (VC-015).
- **SC-011**: 100% of the enabler's verification criteria (VC-001 … VC-015) have associated executable or inspectable evidence.

---

## Assumptions

- **EN001 is in place**: the executable platform baseline (`core-service`, `frontend/web`,
  `contracts/`, `infrastructure/local/compose.yaml`, `start.sh`, `stop.sh`) exists and is
  functional; EN002 modifies it rather than recreating it.
- **Docker is the canonical local runtime.** Contributors have a working Docker / Docker Compose
  (or compatible OCI) runtime. Local Java/Node build tools remain available for development tasks
  (compilation, unit/integration tests) but are not required to *run* the platform.
- **Resolved technical decisions are authoritative.** The enabler's OD-1…OD-9 are treated as
  decided and are not re-opened by this spec: multi-stage Temurin JRE backend image (OD-1); nginx
  serving the Angular build and reverse-proxying `/api` with relative frontend URLs (OD-2, OD-5);
  official pinned Playwright image (OD-3); Chromium-only baseline (OD-4); isolated Compose project
  + disposable volumes for E2E (OD-6); `e2e.sh` owns a self-contained, torn-down-after E2E
  environment (OD-7); screenshot+trace on failure, video off, artifacts in
  `e2e/test-results/`, artifacts never committed (OD-8); normal `start.sh` does not rebuild images
  unconditionally (OD-9).
- **Exact versions decided in planning.** The precise Playwright version (and matching base-image
  tag), the Temurin base-image tag, and the nginx image tag are pinned by a maintainer in
  `research.md` during `/speckit-plan`, consistent with the OD decisions — not chosen ad hoc by
  the implementer.
- **Playwright config runs against an already-running app.** Playwright is configured to target
  the running frontend URL and does **not** manage the application lifecycle itself (it does not
  start a web server); `e2e.sh` / Compose own the platform lifecycle.
- **E2E database isolation** is achieved by the isolated Compose project + a disposable database
  volume; the normal platform's PostgreSQL volume persists between normal runs and is never
  targeted by E2E. Whether the normal platform keeps a persistent volume is unchanged from EN001.
- **Frontend readiness** is an HTTP check against the nginx-served index; **backend readiness**
  reuses the existing Spring Boot health endpoint; **database readiness** uses `pg_isready`.
- **Health endpoint stays open locally.** No authentication is added; the backend health endpoint
  remains reachable without credentials in the local/E2E topology.
- **`technology-policy.md` records Playwright.** This enabler is the approval event for Playwright
  as the initial browser-based E2E framework; `product/architecture/technology-policy.md` has been
  updated (with human approval) to list Playwright (status PREFERRED) for browser E2E testing and
  to add a "Browser End-to-End" testing policy subsection.
- **CI is out of scope.** Validation is by running `start.sh`, `stop.sh`, `e2e.sh`, and the
  documented steps locally. Any CI wiring is a separate future enabler.
- **The FD001 E2E test is follow-up, not EN002.** After EN002, `FD001 — Create Investment
  Portfolio` is expected to add `implementation/platform/e2e/tests/FD001-create-portfolio.spec.ts`;
  that scenario is not implemented here.

---

## Dependencies

- **EN001 — Bootstrap Executable Platform** must be complete: EN002 containerizes and extends its
  backend, frontend, Compose file, and lifecycle scripts.
- **ADR-001 — Initial Backend Topology** remains binding and unchanged: `core-service` stays the
  single backend deployable; containerization does not split it. No new ADR is required by EN002
  (the OD decisions are recorded in the approved enabler).
- **Authoritative enabler definition**: `product/definition/enablers/EN002-containerized-e2e-testing-foundation/EN002-containerized-e2e-testing-foundation.md`.
- **Governance**: `product/architecture/technology-policy.md` (Playwright added — PREFERRED for browser E2E), `architecture-rules.md`; `product/engineering/testing-strategy.md` (E2E section), `development-rules.md`, `definition-of-done.md` (E2E-required-for-cross-layer-journeys rule).
- **Downstream consumers**: `FD001 — Create Investment Portfolio` (adds the first feature E2E test and gains a containerized verification path); all future Feature Definitions reuse this foundation.

---

## Out of Scope

Explicitly excluded from EN002 (from the enabler's §3 "Out of Scope" plus derived boundaries):

- Complete E2E coverage for FD001 or any other Feature Definition — only the platform smoke test
  is delivered here.
- Performance testing, load testing, visual-regression testing, mobile-native testing,
  production-environment E2E testing.
- Multi-browser / cross-browser execution beyond the Chromium baseline.
- CI/CD integration of any kind, including any GitHub Actions workflow (deferred to a future
  Technical Enabler).
- Authentication-specific E2E infrastructure or any authentication/authorization mechanism.
- External market-data, news, LLM, or SaaS integration testing.
- Any Portfolio, position, valuation, risk, recommendation, stop-loss, or portfolio-review
  business behavior, schema, or API.
- Kubernetes, cloud infrastructure, Terraform, production image registries/publishing.
- Changes to the backend service topology, new deployable components, messaging, or new
  persistence technologies.
- Dedicated test-only application/runtime behavior for E2E data setup, unless explicitly approved
  in the plan (FR-027).
