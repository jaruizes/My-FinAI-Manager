# ADR-001 — Executable Platform Baseline

> **Status:** Proposed
> **Date:** 2026-09-08
> **Deciders:** (human approval pending)
> **Enabler:** EN001 — Establish Executable Platform Foundation

---

## Context and Problem

EN001 requires the first complete, executable version of My-FinAI-Manager: an
integrated local platform proving `browser → Angular → REST → Spring Boot →
PostgreSQL` plus a working local observability pipeline, before any product
Feature is implemented.

`reference/engineering/architecture.md` and `technology-policy.md` fix the
solution space (module-first Spring, Hexagonal boundaries, Maven, Spring Data
JPA, Flyway, PostgreSQL, OpenAPI, OpenTelemetry, Docker, Testcontainers,
ArchUnit, Playwright/Chromium). Several concrete decisions remain *inside* that
latitude and are recorded here because `architecture.md` requires significant
decisions to be captured in an ADR.

---

## Decisions

### D1 — One coarse backend deployable `core-service`

A single Spring Boot deployable at `implementation/platform/backend/core-service/`,
base package `com.myfinaimanager.core`. EN001 §10 mandates exactly one coarse
backend and forbids additional microservices at this stage (AAC-005, AAC-007).

**Alternatives considered:** a multi-module Maven reactor now — rejected as
premature structure with no functional modules to separate yet.

### D2 — A single functional module `platform` for bootstrap metadata

The `hello` capability lives in `com.myfinaimanager.core.platform.{domain,
business,infrastructure}` and owns exactly one concept, `PlatformVersion`. Named
`platform` (not `hello`/`bootstrap`) so it reads as platform metadata and resists
becoming a business dumping ground (EN001 BR-002). An ArchUnit rule fails the
build if any sibling module appears.

### D3 — `hello` contract and failure semantics

- `200 OK` → `{ "version": "<persisted>" }`, no additional fields (keeps the
  acceptance assertion tight; FR-005 permits extras only when justified).
- Persistence unavailable → `503 Service Unavailable` →
  `{ "error": "PLATFORM_VERSION_UNAVAILABLE" }`: a stable machine-readable code,
  no stack trace / SQL / connection detail (AC-008, DR-008).
- `implementation/platform/contracts/openapi.yaml` (OpenAPI 3.0) is the source of
  truth; an integration test asserts the springdoc-generated spec stays
  semantically compatible with it.

### D4 — Seeded platform version `0.1.0`, owned by Flyway

`V1__platform_baseline.sql` creates a single-row `platform_version` table and
inserts `0.1.0` (EN001's own example; matches the product's Version 1.0.0
trajectory). `PlatformVersionEntity` (JPA, infrastructure) is mapped to the
`PlatformVersion` domain value object by the adapter — the domain type carries no
annotations (AAC-003, AAC-012).

### D5 — Frontend routing via an nginx reverse proxy

The Angular container serves static assets **and** proxies `/api/` to
`http://core-service:8080`. The browser only ever calls its own origin, so no
Docker-internal service name appears in shipped JavaScript (EN001 §10). `ng serve`
uses an equivalent `proxy.conf.json` for local development.

**Alternatives considered:** Angular dev server + CORS on the backend — rejected:
not container-canonical, adds CORS surface, and the dev server is not how the
platform should run.

### D6 — OpenTelemetry via the Java agent, Collector fan-out

The OpenTelemetry Java agent is attached to the backend container
(`JAVA_TOOL_OPTIONS=-javaagent:…`) and configured entirely by `OTEL_*` env vars.
A single OpenTelemetry Collector receives OTLP and fans out: traces → Jaeger
(OTLP), metrics → a Prometheus exporter scraped by Prometheus. Grafana is
provisioned at startup with the datasources and one dashboard covering the
`hello` endpoint (AC-009…AC-011). When no OTLP endpoint is configured the agent
exports nothing and the application runs normally (observability failure never
corrupts application behaviour).

**Alternatives considered:** manual OpenTelemetry SDK wiring in application code —
rejected as more code for no benefit at this stage; the agent is still the
OpenTelemetry standard.

### D7 — Docker Compose is the canonical local runtime

`implementation/platform/infrastructure/compose.yaml` defines the seven services.
`start.sh` / `stop.sh` / `e2e.sh` are the only supported entry points. `start.sh`
builds the two application images with the classic `docker build` (not
`docker compose build`, which requires a newer buildx than is always present),
then `docker compose up -d --wait` gates on the containers' health checks.

### D8 — Build and run the backend on Java 21

`core-service` targets Java 21 (LTS) — the container JRE, and what CI/production
should use. A `.sdkmanrc` pins it for local development. Newer JDKs are not used
for the build because tooling (ArchUnit/ASM) lags the newest class-file versions.

### D9 — Testcontainers Docker API version pinned in the build

docker-java (used by Testcontainers) defaults to Docker Engine API v1.32, which
modern engines reject. The Maven build sets `-Dapi.version=1.44` for Surefire and
Failsafe so integration tests connect on any current daemon (override with
`-Ddocker.api.version=…`).

---

## Consequences

**Positive**

- A single coherent runtime; every future Feature extends an executable,
  observable, testable platform instead of rebuilding foundations.
- The backend skeleton already embodies the mandatory architecture (enforced by
  ArchUnit), so new modules copy a correct pattern.
- Deterministic, container-only verification (`./mvnw verify`, `./e2e.sh`) needs
  no manually installed database or browser.

**Negative / risks**

- Seven containers on a developer machine (accepted by EN001 assumption A-004).
  Observability stores are ephemeral (no volumes) to limit footprint.
- The frontend image is coupled to the backend service name `core-service` via
  `nginx.conf` — acceptable for a local canonical runtime.
- `start.sh` is Bash and must stay compatible with the macOS system Bash 3.2
  (no associative arrays); readiness is delegated to `docker compose --wait`.
- OTel Java agent auto-span names may drift between agent versions; the agent
  version is pinned and AC-009 can also be a manual/automation check.

**Follow-ups**

- Revisit backend granularity only when a concrete driver appears (AAC-007).
- Production Dockerfiles, image publishing, CI wiring, and authentication are out
  of EN001 scope and will be separate decisions.
