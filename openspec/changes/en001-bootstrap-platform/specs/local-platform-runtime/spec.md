## Purpose

Defines the fully containerized local runtime for My-FinAI-Manager and its canonical lifecycle entry points, so the complete platform can be started, verified, and stopped deterministically without host-running Angular, Spring Boot, or PostgreSQL processes.

## ADDED Requirements

### Requirement: Fully containerized local runtime

The canonical local runtime SHALL start the frontend, backend, PostgreSQL, and the full observability stack (OpenTelemetry Collector, Jaeger, Prometheus, Grafana) as containers orchestrated by Docker Compose under `implementation/platform/`.

`start.sh` and `stop.sh` MUST manage the runtime through containers and MUST NOT rely on host-running Angular, Spring Boot, or PostgreSQL processes.

#### Scenario: Required services run as containers

- **WHEN** the canonical start command completes successfully
- **THEN** containers exist and are running for PostgreSQL, backend, frontend, OpenTelemetry Collector, Jaeger, Prometheus, and Grafana

### Requirement: Canonical platform start

The repository SHALL provide `implementation/platform/start.sh` as the canonical entry point for starting the complete local platform. On success it SHALL bring the required application and observability containers to a healthy/reachable state and report usable local entry points (URLs/ports) for the frontend, backend, and observability UIs.

#### Scenario: Start brings the platform to Ready

- **WHEN** `./implementation/platform/start.sh` is executed with a Docker-compatible runtime available and required ports free
- **THEN** PostgreSQL becomes healthy before backend readiness is reported
- **AND** backend, frontend, and observability services become reachable
- **AND** the script prints the local endpoints for the frontend, backend, and observability UIs
- **AND** the script exit code is `0`

#### Scenario: PostgreSQL does not become ready

- **WHEN** PostgreSQL fails its readiness check or does not become ready within the bounded timeout
- **THEN** backend-dependent readiness is not reported as successful
- **AND** the start command fails with actionable diagnostics identifying PostgreSQL
- **AND** the platform is not reported as Ready

#### Scenario: A mandatory container fails to start

- **WHEN** any mandatory application or observability container cannot start
- **THEN** the start command reports the failing service by name
- **AND** it does not falsely report a healthy platform
- **AND** the script exit code is non-zero

### Requirement: Canonical platform stop

The repository SHALL provide `implementation/platform/stop.sh` as the canonical entry point for stopping the complete local platform. It SHALL stop all runtime containers cleanly, and invoking it when the platform is already stopped SHALL be safe (no error, no side effects beyond a no-op).

#### Scenario: Stop tears the platform down cleanly

- **WHEN** `./implementation/platform/stop.sh` is executed while the platform is running
- **THEN** all platform containers are stopped
- **AND** the script exit code is `0`

#### Scenario: Stop is safe when already stopped

- **WHEN** `./implementation/platform/stop.sh` is executed while the platform is already stopped
- **THEN** the command completes successfully without error

### Requirement: Stable browser-to-backend routing

The frontend-to-backend route in the containerized topology SHALL be explicit and stable. Browser code MUST NOT depend on Docker-internal service DNS names. A frontend reverse proxy or equivalent stable routing mechanism MAY be used so the browser calls a single stable origin.

#### Scenario: Browser reaches the backend through a stable route

- **WHEN** the containerized frontend is open in a browser and issues the `hello` request
- **THEN** the request resolves through a stable, externally reachable route
- **AND** the request does not require the browser to resolve a Docker-internal hostname

### Requirement: Bounded health and readiness checks

Mandatory runtime components SHALL expose or support bounded readiness checks sufficient for deterministic startup and E2E execution. Startup and test orchestration SHALL use readiness/health checks rather than arbitrary fixed sleeps as the primary synchronization mechanism.

#### Scenario: Readiness gates orchestration

- **WHEN** the platform is started or the E2E runner waits for the application
- **THEN** progression depends on bounded health/readiness checks
- **AND** a component that never becomes ready causes a bounded, diagnosable failure rather than an indefinite wait
