# Feature Specification: Bootstrap Executable Platform (EN001)

**Feature Branch**: `EN001-bootstrap-platform`

**Created**: 2026-08-31

**Status**: Approved

**Input**: Technical Enabler: "Create the SDD specification for technical enabler EN001-bootstrap-platform. The goal is to bootstrap the executable platform under implementation/platform/. This is a technical enabler, not a product feature. Do not invent user stories or business functionality."

**Authoritative Source**: `product/definition/enablers/EN001-bootstrap-platform/EN001-bootstrap-platform.md`

**Governing Architecture Decision**: `product/architecture/adrs/ADR-001-initial-backend-topology.md`

---

## Enabler Nature *(mandatory)*

EN001 is a **Technical Enabler**, not a product Feature Definition.

It does **not** introduce user-facing business functionality, portfolio behavior, or financial logic. Its only purpose is to create the minimum executable technical foundation ("platform baseline") that later vertical Feature Definitions (starting with `FD001 — Create Investment Portfolio`) will extend.

Because this is an enabler:

- The scenarios below describe **developer / platform-operator workflows**, not investor journeys.
- "Acceptance" is expressed as the enabler's **Verification Criteria (VC-001 … VC-009)** from the authoritative source.
- No business domain entities are defined. The Information Model under `product/definition/global/` is unaffected.

---

## User Scenarios & Testing *(mandatory)*

The beneficiaries of this enabler are the **engineering team** (human and AI contributors) who will implement product features on top of the platform, and anyone who needs to run the platform locally.

### User Story 1 - One-command local platform lifecycle (Priority: P1)

As a developer, I can start the complete local platform (local infrastructure, backend, frontend) with a single canonical command, and stop it again with a single canonical command, without manually installing databases or wiring services together.

**Why this priority**: This is the core purpose of EN001. Without a reproducible, one-command executable platform, no feature work can begin and no acceptance evidence for future features can be produced. Delivering only this story already yields a runnable platform baseline.

**Independent Test**: On a clean machine with only the documented prerequisites, run `./implementation/platform/start.sh`, observe that local infrastructure, backend, and frontend all reach a running state; then run `./implementation/platform/stop.sh` and observe that all platform-managed processes/containers are stopped. No manual database installation is required.

**Acceptance Scenarios**:

1. **Given** a clean local environment with documented prerequisites installed, **When** the developer runs `./implementation/platform/start.sh`, **Then** the local PostgreSQL instance starts (VC-003), the Spring Boot backend starts (VC-002), and the Angular frontend starts (VC-001).
2. **Given** the platform is running, **When** the developer runs `./implementation/platform/stop.sh`, **Then** all platform-managed processes and containers are stopped and no platform-managed containers remain running (VC-008).
3. **Given** the platform started successfully, **When** the developer inspects startup output/logs, **Then** each component reports a healthy/ready state and no component reports a fatal startup error (VC-007).
4. **Given** local infrastructure is already running from a previous start, **When** the developer runs `start.sh` again, **Then** the script completes without corrupting the environment or producing duplicate conflicting instances.

---

### User Story 2 - Backend health and database connectivity verification (Priority: P2)

As a developer or operator, I can query a single backend health endpoint and confirm that the backend runtime is healthy and that it can reach its PostgreSQL database.

**Why this priority**: A reliable, machine-checkable signal that "the backend is up and connected to persistence" is required for local development and as the pattern future features will rely on. It depends on P1 being in place but adds independently demonstrable value. EN001 uses the standard framework health mechanism (Spring Boot Actuator) — no custom endpoint.

**Independent Test**: With the platform running, issue a request to the backend health endpoint and confirm a successful response that reflects both application health and PostgreSQL connectivity.

**Acceptance Scenarios**:

1. **Given** the backend and PostgreSQL are running, **When** the health endpoint is called, **Then** it responds successfully indicating the application runtime is healthy (VC-005).
2. **Given** the backend is running and PostgreSQL is reachable, **When** the health endpoint is called, **Then** the response reflects that database connectivity is established (VC-004).
3. **Given** PostgreSQL is unavailable, **When** the health endpoint is called, **Then** the backend reports an unhealthy/degraded state through the health mechanism rather than returning a stack trace or crashing.
4. **Given** the backend starts before PostgreSQL is ready, **When** PostgreSQL becomes available shortly after, **Then** the backend establishes connectivity without requiring a manual restart.

---

### User Story 3 - Reproducible integration testing against disposable infrastructure (Priority: P2)

As a developer, I can run the backend integration test suite and have it exercise a real, disposable PostgreSQL instance that is provisioned automatically, without any manually installed local database.

**Why this priority**: The project's testing strategy makes Testcontainers mandatory for integration tests against application-managed infrastructure. Establishing this baseline in EN001 means every future feature inherits a working integration-test pattern instead of re-inventing it. It is independently valuable and independently testable.

**Independent Test**: On a machine with no locally installed PostgreSQL, run the backend integration test task; observe that a disposable PostgreSQL container is started automatically, at least one integration test validates real database connectivity, and the container is disposed afterwards.

**Acceptance Scenarios**:

1. **Given** no PostgreSQL is installed on the host, **When** the integration test suite runs, **Then** at least one integration test executes successfully against a disposable PostgreSQL container (VC-006).
2. **Given** any machine with only a container runtime available (developer workstation or a future CI runner), **When** the integration test runs, **Then** it passes without any step that manually installs or provisions a database.
3. **Given** the integration test run completes (pass or fail), **When** the environment is inspected, **Then** the disposable database container has been removed.

---

### User Story 4 - Structural baseline ready for vertical feature extension (Priority: P3)

As a feature implementer, I find a repository structure under `implementation/platform/` with clearly separated backend (hexagonal skeleton), frontend shell, contracts location, and local infrastructure, so that the first product feature (FD001) can extend it without recreating technical foundations.

**Why this priority**: The enabler must leave behind a coherent structure future features extend rather than replace. This is foundational but only demonstrable once P1–P3 exist; it is verified largely by inspection and automated architecture checks.

**Independent Test**: Inspect the repository and run the backend architecture-conformance check; confirm the expected directory layout exists, the backend separates domain / application / inbound adapters / outbound adapters, and the dependency direction points inward with no framework/infrastructure imports in domain code.

**Acceptance Scenarios**:

1. **Given** the repository after EN001, **When** its structure is inspected, **Then** `implementation/platform/` contains `backend/core-service/`, `frontend/web/`, `contracts/openapi/`, `infrastructure/local/`, `start.sh`, and `stop.sh`.
2. **Given** the backend module, **When** the architecture-conformance check runs, **Then** domain code has no dependency on Spring, persistence drivers, HTTP frameworks, or other infrastructure, and all external dependencies are reached through explicit ports/adapters.
3. **Given** the contracts location, **When** it is inspected, **Then** it contains an OpenAPI skeleton document (`paths: {}`) that establishes the contract-first location and convention, with **no** invented product or operational endpoints (VC-009).
4. **Given** the frontend, **When** it is started, **Then** it presents an application shell with a routing foundation and a global styling integration point, and no product-specific screens.
5. **Given** the whole repository, **When** it is reviewed, **Then** no Portfolio, valuation, risk, recommendation, or other business behavior has been introduced (VC-009).

---

### Edge Cases

- **Container runtime not running**: `start.sh` must fail fast with a clear, human-readable message when the container runtime (e.g. Docker) is unavailable, rather than hanging or emitting a stack trace.
- **Port already in use**: When a required local port (backend, frontend, or database) is occupied, the platform must surface an explicit, actionable error.
- **Database not ready at backend startup**: Backend startup must tolerate PostgreSQL not being immediately available and converge once it is, without manual intervention.
- **`stop.sh` with nothing running**: Running `stop.sh` when the platform is not running must be a safe no-op that exits successfully.
- **`start.sh` run twice**: A second `start.sh` invocation must not create conflicting duplicate instances or corrupt local state.
- **Missing local configuration**: When required non-secret configuration is absent, the platform must report which configuration is missing; secrets must never be committed as defaults.
- **No host-installed infrastructure**: The integration-test path must not depend on a database installed on the host — on a developer workstation now, or on any CI runner a future enabler may add.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Repository structure & platform integrity

- **FR-001**: The executable platform MUST live entirely under `implementation/platform/` and MUST NOT introduce alternative repository-root implementation trees (e.g. `apps/`, `services/`, `src/`, root-level `backend/`, `frontend/`, `infrastructure/`, `tests/`).
- **FR-002**: After EN001, `implementation/platform/` MUST contain at least: `backend/core-service/`, `contracts/openapi/`, `frontend/web/`, `infrastructure/local/` (with a Compose definition), `start.sh`, and `stop.sh`.
- **FR-003**: The platform MUST be left in a coherent, fully executable state that future vertical Feature Definitions extend rather than replace.

#### Backend baseline

- **FR-004**: The backend MUST be a single coarse-grained deployable component located at `implementation/platform/backend/core-service/`, consistent with ADR-001. It MUST NOT be split into one service per functional domain.
- **FR-005**: The backend MUST establish the Hexagonal Architecture package convention — documented, empty packages for domain, application, inbound adapters, and outbound adapters, with the inward dependency rule stated — enforced by the FR-027 conformance check. EN001 MUST NOT create production domain/application/adapter classes to demonstrate the architecture; real modules arrive with FD001.
- **FR-006**: The convention and conformance check MUST forbid `domain`/`application` packages from depending on frameworks or infrastructure (e.g. Spring, persistence drivers, HTTP clients, serialization frameworks); external dependencies, when they appear with FD001, MUST be represented as ports with adapters.
- **FR-007**: The backend MUST NOT contain business/domain modules or classes that are not yet required by an approved Feature Definition; the minimal technical structure (Spring Boot entry point, config, empty package convention) is sufficient.
- **FR-008**: The backend MUST use structured logging and MUST NOT log secrets. No observability backend beyond structured logging is introduced by EN001.

#### Frontend baseline

- **FR-009**: The frontend MUST be an Angular (TypeScript) application located at `implementation/platform/frontend/web/`, providing application bootstrap, a base application shell, a routing foundation, and a global styling integration point.
- **FR-010**: The frontend MUST NOT implement product-specific screens or embed backend business rules, and MUST interact with the backend only through defined contracts.
- **FR-011**: The frontend MUST provide a designated location for shared design tokens/components to be added later (aligned with `product/ux/design-system.md`) without requiring those components to exist yet.

#### Persistence baseline

- **FR-012**: PostgreSQL MUST be the only relational persistence technology introduced, provided via the local infrastructure for local execution, backend connectivity, future migrations, and integration testing.
- **FR-013**: The backend MUST access PostgreSQL through an outbound adapter; no business schema MUST be introduced beyond what is technically required to prove connectivity.
- **FR-014**: A schema-migration mechanism (project-approved, e.g. Flyway or equivalent) MUST be wired so future features can add migrations; any baseline migration introduced by EN001 MUST contain no business schema.

#### Local infrastructure

- **FR-015**: Local infrastructure MUST be defined under `implementation/platform/infrastructure/local/` and orchestrated with Docker Compose.
- **FR-016**: For EN001, PostgreSQL MUST be the only mandatory local infrastructure dependency; the environment MUST remain intentionally minimal (no Kafka, Neo4j, Kubernetes, or other speculative infrastructure).

#### Contracts baseline

- **FR-017**: External business contracts MUST be located under `implementation/platform/contracts/`, with REST contracts under `implementation/platform/contracts/openapi/`.
- **FR-018**: EN001 MUST include an OpenAPI skeleton document (`paths: {}`) plus a short README that establish the contract-first location and convention. It MUST expose no operations — no invented product endpoints and no operational endpoints (operational health is provided by the framework mechanism, not modelled as a business contract). Feature Definitions starting with FD001 add the first operations.

#### Health verification

- **FR-019**: The backend MUST expose a simple health endpoint (or equivalent framework health mechanism) that indicates the application runtime is healthy.
- **FR-020**: The health mechanism MUST reflect PostgreSQL connectivity status where applicable, and MUST report an unhealthy/degraded state (not a crash or stack trace) when PostgreSQL is unavailable.

#### Platform lifecycle

- **FR-021**: `implementation/platform/start.sh` MUST be the canonical local entry point that starts (directly or indirectly) required local infrastructure, the backend, and the frontend.
- **FR-022**: `implementation/platform/stop.sh` MUST be the canonical local entry point that stops the complete local environment; running it when nothing is running MUST be a safe successful no-op.
- **FR-023**: The lifecycle scripts MUST fail fast with clear, non-technical error messages on predictable failure conditions (container runtime unavailable, required port in use, missing required non-secret configuration).
- **FR-024**: No alternative, undocumented mechanism for starting or stopping the platform may supersede these scripts.

#### Integration-testing baseline

- **FR-025**: The backend MUST include at least one integration test that validates PostgreSQL integration using a real disposable PostgreSQL container via Testcontainers.
- **FR-026**: Integration tests MUST NOT depend on a manually installed local or CI PostgreSQL instance, and disposable containers MUST be cleaned up after the run.

#### Architecture conformance

- **FR-027**: An automated architecture-conformance check MUST encode the backend hexagonal package convention — inward dependency direction, and no framework/infrastructure dependencies in `domain`/`application` packages. In EN001 it acts as a guardrail (there are no production business classes yet); it becomes substantive when FD001 adds real modules.

#### Technology & scope constraints

- **FR-028**: EN001 MUST use only technologies already approved by `product/architecture/technology-policy.md`: Angular, TypeScript, Java, Spring Boot, PostgreSQL, Docker/Docker Compose, OpenAPI, Testcontainers, Hexagonal Architecture. Specific framework/runtime **versions** and the backend build tool are NOT fixed by this spec or the authoritative documents; they MUST be chosen by a maintainer before implementation (recorded in `research.md`), not decided by the implementer. Introducing any additional major technology requires architecture review / an ADR.
- **FR-029**: EN001 MUST use a single backend runtime (Java/Spring Boot per ADR-001); Python, Kafka, Neo4j, LLM/AI integration, MCP, Kubernetes, production cloud infrastructure, CI/CD of any kind (including GitHub Actions workflows), market-data integration, and news-provider integration are explicitly out of scope.
- **FR-030**: EN001 MUST NOT introduce authentication or authorization of any kind (no Spring Security, no permit-all configuration — with no security library on the classpath the framework health endpoint is simply open locally), and MUST NOT introduce any Portfolio or financial business behavior (VC-009).
- **FR-031**: No secrets (credentials, tokens, certificates) may be committed; configuration MUST be externalized, and any local development credentials MUST be non-production synthetic values supplied via configuration, not source code.

### Traceability to Enabler Verification Criteria

| Enabler VC | Description | Covered by |
|---|---|---|
| VC-001 | Frontend starts | US1 (AS1); FR-009, FR-021 |
| VC-002 | Backend starts | US1 (AS1); FR-004, FR-021 |
| VC-003 | PostgreSQL starts | US1 (AS1); FR-012, FR-015 |
| VC-004 | Backend connectivity to PostgreSQL | US2 (AS2); FR-013, FR-020 |
| VC-005 | Health endpoint responds | US2 (AS1); FR-019 |
| VC-006 | Testcontainers integration test passes | US3 (AS1); FR-025, FR-026 |
| VC-007 | `start.sh` starts the whole platform | US1 (AS1, AS3); FR-021 |
| VC-008 | `stop.sh` stops the whole platform | US1 (AS2); FR-022 |
| VC-009 | No business functionality invented | US4 (AS3, AS5); FR-007, FR-018, FR-030 |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer on a clean machine with only the documented prerequisites can bring up the entire platform with one command and no manual database installation.
- **SC-002**: After `start.sh` completes, all three platform components (frontend, backend, relational database) are in a running state, verifiable from the command output or a follow-up status check.
- **SC-003**: The backend health check reports "healthy" including confirmed database connectivity within 60 seconds of a successful start.
- **SC-004**: One `stop.sh` command leaves zero platform-managed processes or containers running, verifiable by inspecting the container runtime.
- **SC-005**: The integration-test suite provisions its own disposable database and passes on any machine with a container runtime, with no step that installs a database.
- **SC-006**: A new contributor following the documented steps reaches a first successful local start in under 15 minutes (excluding one-time prerequisite installs and image downloads).
- **SC-007**: A review of the resulting code finds no Portfolio, valuation, risk, recommendation, stop-loss, or other business behavior (VC-009).
- **SC-008**: The automated architecture-conformance check passes and fails the build if domain code gains a forbidden framework/infrastructure dependency.
- **SC-009**: 100% of the enabler's verification criteria (VC-001 … VC-009) have associated executable or inspectable evidence.

## Assumptions

- **Prerequisites**: Contributors have a working Docker / Docker Compose (or compatible OCI) runtime and the Java and Node/Angular toolchains required to build and run the components; these are documented but not installed by EN001.
- **Backend runtime**: Per ADR-001 and the enabler's technology constraints, the single initial backend runtime is Java + Spring Boot. Python is not introduced.
- **Migration tooling**: A project-approved relational migration tool (Flyway or equivalent for the chosen runtime) is wired with an empty/baseline configuration so future features can add migrations; EN001 adds no business schema.
- **Health mechanism**: Spring Boot Actuator (`/actuator/health` with its built-in database health indicator, liveness/readiness probes) is the health mechanism. No custom health endpoint or code is created.
- **Authentication**: The bootstrap platform runs with no authentication. No Spring Security dependency is added. A real identity model (OAuth 2.0 / OpenID Connect) is deferred to a later enabler/feature when required.
- **Contracts**: The OpenAPI artifact is an empty skeleton (`paths: {}`) plus a README establishing the location and convention. No operation is defined or implemented in EN001.
- **Script internals**: How `start.sh`/`stop.sh` run the backend and frontend (host toolchains vs containers) is an implementation decision for the plan, provided the scripts remain the stable canonical entry points and the full platform can be started and stopped through them.
- **Observability**: Structured logging only; OpenTelemetry and observability backends are deferred until a feature requires them.
- **Local credentials**: Local database credentials are non-production synthetic values provided through externalized configuration, never committed as source.
- **CI scope**: CI/CD of any kind — including any GitHub Actions workflow — is out of scope for EN001 and deferred to a future Technical Enabler. Validation is performed by running the local build/test commands and `quickstart.md`.
- **Version decisions**: The exact Java/JDK version, Spring Boot version, Angular/Node versions, PostgreSQL major version, and backend build tool (Maven vs Gradle) are open decisions for a maintainer (see `research.md` "Open questions" and `tasks.md` OD-1…OD-6), not chosen by this spec.

## Dependencies

- **ADR-001 — Initial Backend Topology** must be approved; it fixes the single coarse-grained `core-service` backend topology this spec assumes.
- Authoritative enabler definition: `product/definition/enablers/EN001-bootstrap-platform/EN001-bootstrap-platform.md`.
- Architecture governance: `product/architecture/architecture.md`, `architecture-rules.md`, `technology-policy.md`.
- Engineering governance: `product/engineering/testing-strategy.md`, `development-rules.md`, `definition-of-done.md`.
- Follow-up consumer: `FD001 — Create Investment Portfolio` will extend this platform and must not recreate these foundations.

## Out of Scope

Explicitly excluded from EN001 (may be introduced later when justified by a Feature Definition or a further Technical Enabler):

- Any Portfolio, position, valuation, risk, recommendation, stop-loss, or portfolio-review business functionality.
- Kafka, Neo4j, additional databases, caches, search engines, vector stores.
- Python backend services, LLM/AI integration, MCP.
- Kubernetes, production cloud infrastructure, Terraform.
- Any CI/CD pipeline or workflow, including GitHub Actions.
- Market-data provider and news-provider integrations.
- Authentication and authorization of any kind (no Spring Security).
- Advanced observability backends (tracing/metrics collectors, dashboards).
- Business schema, business migrations, or business/operational API endpoints.
- Production domain/application classes created only to demonstrate Hexagonal Architecture (real modules arrive with FD001).
