## Why

My-FinAI-Manager has approved product intent (vision, features, enablers) and engineering guardrails, but no executable platform. Every future Feature Definition needs an executable, repeatable, observable, and testable baseline to extend rather than repeatedly creating or repairing technical foundations.

Enabler **EN001 — Establish Executable Platform Foundation** (`product/definition/enablers/EN001-bootstrap-platform/EN001-bootstrap-platform.md`) is the human-approved source of intent for this change. It requires proving, end to end, that the approved frontend, backend, relational persistence, automated testing, architecture validation, containerized runtime, and observability foundations work together before any product Feature is implemented.

## What Changes

- Create the initial Angular frontend application with a minimal empty Home shell (no product functionality).
- Create the initial Spring Boot backend deployable (`core-service`) built with Maven and organized module-first (`domain` / `business` / `infrastructure`) with the mandatory dependency direction.
- Configure PostgreSQL as the relational database, Spring Data JPA / Hibernate for persistence, and Flyway for schema evolution.
- Introduce a minimal `platform` module holding the persisted platform version (technical information object, not a business module).
- Expose `GET /api/v1/hello` through the external business API, returning the platform version read from PostgreSQL, and describe it in the platform OpenAPI contract.
- Make the Angular Home page invoke `hello` and render the returned version, with explicit `loading` / `success` / `error` states and no fabricated version on failure.
- Containerize PostgreSQL, backend, frontend, Playwright E2E runner, OpenTelemetry Collector, Jaeger, Prometheus, and Grafana; provide the canonical Docker Compose local runtime.
- Provide canonical lifecycle scripts `implementation/platform/start.sh`, `stop.sh`, `e2e.sh` with bounded readiness checks.
- Establish backend PostgreSQL integration tests with Testcontainers, architecture tests with ArchUnit, and a containerized Playwright browser E2E test proving `browser → Angular → REST → Spring Boot → PostgreSQL`.
- Configure backend application observability with OpenTelemetry (OTLP → Collector → Jaeger for traces, Prometheus for metrics) and provision at least one Grafana dashboard exposing telemetry related to the `hello` endpoint.
- Document the local developer execution and verification entry points.

No product behavior (Portfolio, Financial Instrument, market data, news, AI/LLM) is introduced. No Kafka, Neo4j, Redis, search engine, vector store, authentication, or Kubernetes is introduced.

## Capabilities

### New Capabilities

- `platform-foundation`: The application shell and the bootstrap `hello` capability — Angular Home shell, `GET /api/v1/hello`, platform-version persistence governed by Flyway, OpenAPI contract for the endpoint, frontend consumption of the endpoint, and the "no fabricated version" rule.
- `local-platform-runtime`: The fully containerized local runtime — Docker Compose topology for frontend, backend, PostgreSQL and the observability stack; canonical `start.sh` / `stop.sh` / `e2e.sh` lifecycle entry points; stable browser-to-backend routing; and bounded health/readiness checks.
- `platform-observability`: The local observability baseline — backend OpenTelemetry telemetry, OpenTelemetry Collector, Jaeger trace inspection, Prometheus metrics, and a provisioned Grafana datasource plus at least one dashboard containing telemetry related to the `hello` endpoint.
- `engineering-verification`: The automated verification foundations — Maven backend build, PostgreSQL integration testing with Testcontainers, ArchUnit architecture tests enforcing the mandatory Spring boundaries, and containerized Playwright (Chromium) browser E2E execution with result propagation and retained diagnostics on failure.

### Modified Capabilities

- None. This is the first change; `openspec/specs/` is currently empty.

## Impact

- **New source tree**: `implementation/platform/` (`frontend/`, `backend/`, `contracts/`, `infrastructure/`, `start.sh`, `stop.sh`, `e2e.sh`) per `reference/engineering/architecture.md`.
- **APIs**: new external contract `GET /api/v1/hello`; new `implementation/platform/contracts/` OpenAPI document.
- **Database**: new PostgreSQL schema, first Flyway migration creating and seeding the platform-version record (fixture `version = 0.1.0`).
- **Dependencies (all from the approved technology policy)**: Angular + TypeScript; Spring Boot + Maven; Spring Data JPA / Hibernate; Flyway; PostgreSQL driver; OpenTelemetry (SDK / Spring Boot starter / agent); JUnit 5, AssertJ, Testcontainers, ArchUnit; Playwright.
- **Infrastructure**: Docker Compose; container images for OpenTelemetry Collector, Jaeger, Prometheus, Grafana (with provisioned datasource + dashboard).
- **Tooling / CI**: canonical local scripts; deterministic verification suitable for GitHub Actions (no AI/LLM execution required).
- **Governance**: an ADR is expected for the initial platform baseline and topology decisions (`reference/engineering/adrs/`). Acceptance Criteria AC-001…AC-014 from EN001 are preserved and mapped to executable tests where deterministic.
