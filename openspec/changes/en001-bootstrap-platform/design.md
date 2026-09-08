## Context

See `proposal.md` — Why. The authoritative intent is `product/definition/enablers/EN001-bootstrap-platform/EN001-bootstrap-platform.md`.

Current state: the repository contains only governance/product/reference documents and an empty `openspec/` tree. There is no `implementation/` directory, no build, no containers. This change creates the first executable slice.

Binding constraints from `reference/engineering/`:

- Executable platform must live under `implementation/platform/` with `frontend/`, `backend/`, `contracts/`, `infrastructure/`, `start.sh`, `stop.sh` (AAC-021). EN001 additionally mandates `e2e.sh`.
- Spring Boot backend: module-first, `domain / business / infrastructure`, dependency direction `infrastructure → business → domain`, enforced with ArchUnit (AAC-001…AAC-004). Domain free of Spring/JPA/HTTP types.
- Maven (REQUIRED), Spring Data JPA / Hibernate (REQUIRED), Flyway (PREFERRED), PostgreSQL (PREFERRED), OpenAPI (REQUIRED for external REST), OpenTelemetry (PREFERRED), Testcontainers (REQUIRED where applicable), ArchUnit (PREFERRED for Java), Playwright + Chromium (PREFERRED). No technology outside `technology-policy.md`.
- No speculative infrastructure (AAC-019): no Kafka, Neo4j, Redis, search, vector store, Kubernetes, auth, second runtime.
- Frontend decoupled (AAC-009); browser must not depend on Docker-internal DNS (EN001 §10).
- UX: dark financial-dashboard shell, shared design tokens, explicit loading/success/error states (`reference/ux/design-system.md`). The Home shell stays deliberately minimal.

## Goals / Non-Goals

**Goals:**

- One coherent, runnable local platform proving `browser → Angular → REST → Spring Boot → PostgreSQL` plus a working local observability pipeline.
- A backend skeleton that already embodies the mandatory architecture so future modules copy a correct pattern.
- Deterministic, container-only verification (`./mvnw verify`, `./e2e.sh`) that needs no manually installed database or browser.
- Every EN001 Acceptance Criterion (AC-001…AC-014) mapped to an automated test where deterministic, or to a documented manual/automation check where not.

**Non-Goals (design-level):**

- No shared frontend component library beyond what the single Home view needs; tokens file only.
- No production Dockerfiles, image publishing, or multi-environment config; local Compose only.
- No multi-browser, visual-regression, load, or feature E2E scenarios.
- No abstraction layers "for later" (no generic port zoo) — only the `PlatformVersion` port the hello use case needs.
- No CI pipeline authoring in this change (the scripts must be CI-friendly, but wiring GitHub Actions is separate).

## Decisions

### D1 — Single backend deployable `core-service`, base package `com.myfinaimanager.core`

EN001 §10 requires exactly one coarse Spring Boot deployable and forbids additional microservices. Directory: `implementation/platform/backend/core-service/`. Alternatives (multi-module Maven reactor now) rejected as premature — AAC-005/AAC-007 require evidence before splitting.

### D2 — Functional module name `platform`

The hello capability lives in `com.myfinaimanager.core.platform.{domain,business,infrastructure}`. Named `platform` (not `hello`, not `bootstrap`) so it reads as "platform metadata" and resists becoming a business dumping ground (EN001 §3, BR-002). It owns exactly one concept: `PlatformVersion`.

Package layout:

```text
com.myfinaimanager.core
├── CoreServiceApplication.java
└── platform
    ├── domain
    │   ├── model/PlatformVersion.java          (value object: non-empty String)
    │   ├── ports/PlatformVersionRepository.java (outbound port, domain types only)
    │   └── exceptions/PlatformVersionUnavailableException.java
    ├── business
    │   └── GetPlatformVersion.java              (use case; @Service; no infra imports)
    └── infrastructure
        ├── api/rest
        │   ├── HelloController.java             (GET /api/v1/hello)
        │   ├── dto/HelloResponse.java
        │   ├── mapper/HelloResponseMapper.java
        │   └── HelloExceptionHandler.java       (maps unavailable → 503 + error code)
        └── persistence
            ├── entity/PlatformVersionEntity.java
            ├── repository/PlatformVersionJpaRepository.java (Spring Data)
            └── PlatformVersionRepositoryAdapter.java        (implements domain port)
```

### D3 — `hello` contract and failure semantics

- Success: `200 OK`, body `{ "version": "0.1.0" }`. No extra fields in v1 (keeps AC-006 assertion tight; EN001 FR-005 allows extras only "if justified" — nothing justifies them yet).
- Persistence unavailable / no version row: `503 Service Unavailable`, body `{ "error": "PLATFORM_VERSION_UNAVAILABLE" }`. Stable machine-readable identifier, no stack trace, no SQL, no connection details (DR-008, EN001 §8 Security, AC-008).
- OpenAPI document: `implementation/platform/contracts/openapi.yaml` (OpenAPI 3.0.x), describing the path, the `200` schema, and the `503` error schema.
- Contract test: backend integration test validates the live response against the committed OpenAPI schema (e.g. `swagger-request-validator` or `atlassian` OpenAPI validator on the approved list is not explicit — use `springdoc` to *generate* and a schema-assertion test to *verify* equality with the committed file). Decision: commit `openapi.yaml` as source of truth; a test asserts the springdoc-generated spec stays compatible with it. Rationale: `technology-policy.md` makes OpenAPI REQUIRED but names no specific validator library; `springdoc-openapi` is the conventional Spring companion and keeps the contract honest without a heavy new dependency.

### D4 — Platform version seeded by Flyway, seeded value `0.1.0`

`V1__platform_baseline.sql` creates `platform_version(id, version)` (single row, `id` a fixed key or a `CHECK` enforcing one row) and inserts `('0.1.0')`. EN001 §11 Test Data permits selecting the value during specification; `0.1.0` matches EN001's own example and the product vision's "Version 1.0.0" trajectory. The E2E test and an integration test both assert against `0.1.0` from a shared constant/fixture.

Domain/JPA separation: `PlatformVersionEntity` (JPA, infrastructure) is mapped to the `PlatformVersion` value object by the adapter. Domain class carries no annotations (AAC-003, DR — no primitive obsession: `PlatformVersion` wraps and validates the string).

### D5 — Frontend: Angular standalone app, nginx reverse proxy for `/api`

- `implementation/platform/frontend/web/` — Angular (standalone components, no NgModules), TypeScript. Single route `/` → `HomeComponent`.
- `HomeComponent` calls `GET /api/v1/hello` via `HttpClient` on init, exposing a discriminated state (`loading` | `{status:'success', version}` | `{status:'error'}`). On error or non-200 it renders an explicit error message and **no** version (AC-008 frontend half, UX error state).
- Styling: a `_tokens.scss` (or CSS custom properties) file implementing the design-system color/spacing/typography tokens; dark background, single centered card showing app identity + platform version. Minimal — no sidebar/topbar yet (design system says navigation must not expose capabilities that don't exist).
- Runtime container: multi-stage build → static assets served by nginx. nginx also proxies `/api/` to the backend container (`proxy_pass http://core-service:8080/api/`). Browser only ever talks to the frontend origin → satisfies "no Docker-internal DNS in browser code" and "explicit, stable route" (EN001 §10). Alternative (Angular dev server + separate CORS config) rejected: not container-canonical, CORS is extra surface, dev server is not how the platform should run.
- The API base URL in browser code is the relative path `/api` (via Angular `environment` for local dev vs container). No hostnames in shipped JS.

### D6 — Observability: OpenTelemetry Java agent + Collector fan-out

```text
core-service  --OTLP/gRPC-->  otel-collector  --> Jaeger      (traces)
                                             --> Prometheus  (metrics, via collector /metrics scrape or remote-write)
Grafana  -->  Prometheus (datasource)      [+ Jaeger datasource optional]
```

- Instrumentation: OpenTelemetry Java agent attached to the backend container (zero-code auto-instrumentation of Spring MVC + JDBC), configured by `OTEL_*` env vars pointing at the collector. Rationale over manual SDK wiring: least code, still OTel-standard, satisfies "application code uses OpenTelemetry as the application-facing standard" while keeping the bootstrap minimal. A single explicit custom span/attribute around the hello use case is added only if the agent's auto span naming makes the Jaeger assertion unstable.
- Collector config (`implementation/platform/infrastructure/otel-collector/config.yaml`): OTLP receiver; exporters to Jaeger (OTLP) and Prometheus (`prometheus` exporter endpoint scraped by Prometheus, or `prometheusremotewrite`). Choose the `prometheus` exporter on the collector + a Prometheus scrape job — simplest, no remote-write setup.
- Grafana provisioning (`infrastructure/grafana/provisioning/`): datasource YAML (Prometheus) + one dashboard JSON with panels for `hello` request rate / latency / count (from `http.server.*` metrics). Provisioned at startup → AC-011 needs zero manual setup.
- Failure isolation: OTel exporters are best-effort; collector down must not break `hello` (AC — observability failure). The agent's default behavior (drop on export failure) already gives this; verified by a test/checklist item.

### D7 — Local runtime: one `compose.yaml`, scripts orchestrate + gate on health

- `implementation/platform/infrastructure/compose.yaml` defines: `postgres`, `core-service`, `frontend`, `otel-collector`, `jaeger`, `prometheus`, `grafana`. Healthchecks on `postgres` (`pg_isready`), `core-service` (Spring Boot Actuator `/actuator/health/readiness`), `frontend` (nginx `/`). `core-service depends_on: postgres: condition: service_healthy`.
- `start.sh`: `docker compose up -d --build`, then poll each service's health with a bounded timeout; on success print the endpoint table (frontend :8080-ish, backend, Jaeger :16686, Prometheus :9090, Grafana :3000); on any unhealthy service, print which one and `exit 1` (AC-004, UC-001 scenarios A/B).
- `stop.sh`: `docker compose down`; safe & idempotent when nothing is running (AC-013).
- `e2e.sh`: ensure platform is up (reuse `start.sh` logic or `--profile e2e`), then run the Playwright container against the frontend, propagate its exit code, copy `playwright-report/` + traces out on failure (AC-012).
- Actuator: add `spring-boot-starter-actuator`, expose `health` (with readiness/liveness groups) and `prometheus` is **not** needed (metrics go via the OTel agent) — keep the actuator surface minimal.

### D8 — Backend build: single `pom.xml` + Maven Wrapper, Surefire/Failsafe split

- `./mvnw verify` = compile + Surefire (unit + ArchUnit) + Failsafe (`*IT` Testcontainers integration + OpenAPI contract test). One command, success-only-on-green (AC-001).
- Test types:
  - Unit: `PlatformVersion` validation, `GetPlatformVersion` use case with a fake repository, `HelloResponseMapper`, exception→HTTP mapping (`@WebMvcTest`).
  - Architecture: `ArchitectureTest` with ArchUnit rules for the three forbidden dependencies + "domain has no Spring/JPA/jakarta.persistence/HTTP imports" + "adapters live under infrastructure".
  - Integration (`*IT`, Failsafe, Testcontainers PostgreSQL): Flyway runs; `PlatformVersionRepositoryAdapter` round-trips; `hello` endpoint returns seeded `0.1.0`; unavailable path (point adapter at a dropped table / empty DB) returns `503 PLATFORM_VERSION_UNAVAILABLE`.
  - Contract (`*IT`): generated OpenAPI stays compatible with committed `contracts/openapi.yaml`.
- Coverage: JaCoCo report produced; EN001 sets no numeric gate and `testing.md` defers gates to governance — no hard threshold added here, but the deterministic core (`domain` + `business`) is fully covered.

### D9 — E2E: Playwright in a container, Chromium, official image

- `implementation/platform/e2e/` — `package.json`, `playwright.config.ts` (Chromium project only, `baseURL` = frontend container URL on the compose network), one spec `platform-smoke.spec.ts`: open `/`, wait for success state, assert the visible version text is exactly `0.1.0`; a negative assertion that the error state is absent.
- Runs from `mcr.microsoft.com/playwright` image (browsers preinstalled) as a compose service on the same network → no host browser install (AC-012). `e2e.sh` runs `docker compose run --rm playwright`.

### D10 — ADR for the platform baseline

Add `reference/engineering/adrs/ADR-001-platform-baseline.md` recording: single `core-service` deployable, `com.myfinaimanager.core` package root, `platform` module for bootstrap metadata, nginx reverse-proxy routing, OTel Java agent + Collector fan-out to Jaeger/Prometheus, Compose-based canonical runtime. EN001 §10 says "no prior architecture decision is required to interpret these constraints", but `architecture.md` requires significant decisions to be recorded; this ADR captures the choices made *within* the EN's latitude (naming, routing mechanism, agent-vs-SDK).

## Risks / Trade-offs

- **Observability stack resource footprint on developer machines (7 containers).** → Keep images slim, single replica, no persistence volumes for Jaeger/Prometheus (in-memory / short retention); document minimum resources; assumption A-004 in EN001 already accepts this.
- **OTel Java agent auto-span names may drift between agent versions, making the Jaeger assertion brittle.** → Pin the agent version; if needed add one explicit named span around the hello use case and assert on that stable name. AC-009 is also allowed to be a manual/automation check.
- **Contract test approach (generated-vs-committed OpenAPI) can produce false diffs on cosmetic changes.** → Compare semantically (paths/operations/schemas) not byte-for-byte; treat `contracts/openapi.yaml` as source of truth and regenerate deliberately.
- **`start.sh` health-polling logic is shell and easy to get subtly wrong (indefinite waits).** → Bounded retries with explicit timeout + per-service failure message; covered by UC-001 scenarios; keep the polling helper small and reviewed.
- **nginx `proxy_pass` coupling frontend image to backend service name.** → Service name `core-service` is fixed in `compose.yaml`; documented; acceptable for a local canonical runtime (not a production concern in scope).
- **Playwright flakiness waiting on readiness.** → `e2e.sh` gates on the same health checks as `start.sh` before invoking Playwright; Playwright uses web-first assertions with bounded timeout, no fixed sleeps.
- **Scope creep into product behavior via the Home shell or the `platform` module.** → Spec requirement "No product behavior" + AC-014 validation step + ArchUnit "only `platform` module exists"; reviewer checklist item.

## Migration Plan

Greenfield — no data or system to migrate. Deployment = developer runs `./implementation/platform/start.sh`. Rollback = `./implementation/platform/stop.sh` and discard the branch; nothing outside `implementation/`, `reference/engineering/adrs/`, and `openspec/` is touched. The first Flyway migration is additive on an empty database.

## Open Questions

- Exact host port assignments for the seven services (avoiding common local conflicts) — a deployment detail resolvable during implementation without changing specs or tasks; `start.sh` will print whatever is chosen.
- Whether Grafana also gets a Jaeger datasource in addition to Prometheus — AC-011 only requires "the required datasource(s)" for the provisioned dashboard, which is Prometheus; a Jaeger datasource is a nice-to-have that doesn't affect the task breakdown.
