## 1. Repository & backend build scaffold

- [x] 1.1 Create `implementation/platform/` tree (`frontend/`, `backend/`, `contracts/`, `infrastructure/`, and placeholder `start.sh`/`stop.sh`/`e2e.sh`); verify the directory layout matches `reference/engineering/architecture.md` and `git status` shows only new paths under `implementation/`.
- [x] 1.2 Scaffold the Spring Boot backend at `implementation/platform/backend/core-service/` with Maven Wrapper, `pom.xml` (Spring Boot, Web, Data JPA, Actuator, Flyway, PostgreSQL driver, springdoc-openapi; test: JUnit 5, AssertJ, Mockito, Testcontainers, ArchUnit), and `com.myfinaimanager.core.CoreServiceApplication`; verify `./mvnw -q compile` succeeds.
- [x] 1.3 Add `application.yml` with externalized datasource + `OTEL_*` config placeholders (no secrets, dev defaults only) and Actuator health readiness/liveness groups enabled; verify `./mvnw -q spring-boot:run` starts and `/actuator/health` responds when a local Postgres is reachable (or documents the expected failure without one).

## 2. Backend domain & business — bootstrap `platform` module (TDD)

- [x] 2.1 RED: write unit tests for `platform.domain.model.PlatformVersion` (rejects null/blank, preserves a valid identifier); run and confirm they fail to compile/pass.
- [x] 2.2 GREEN: implement `PlatformVersion` value object (no framework annotations) and `platform.domain.exceptions.PlatformVersionUnavailableException`; verify 2.1 tests pass.
- [x] 2.3 Define `platform.domain.ports.PlatformVersionRepository` (domain types only, returns `Optional<PlatformVersion>` or throws the domain exception); verify no `org.springframework` / `jakarta.persistence` import is present in the `domain` package.
- [x] 2.4 RED+GREEN: `platform.business.GetPlatformVersion` use case with a fake `PlatformVersionRepository` — returns the version when present, throws `PlatformVersionUnavailableException` when absent; verify the use-case unit tests pass and `business` has no `infrastructure` import.

## 3. Backend persistence adapter & Flyway

- [x] 3.1 Create `V1__platform_baseline.sql` creating `platform_version` (single-row constraint) and seeding `version = '0.1.0'`; verify Flyway validates the script (checksum, naming).
- [x] 3.2 Implement `platform.infrastructure.persistence` — `PlatformVersionEntity` (JPA), `PlatformVersionJpaRepository` (Spring Data), and `PlatformVersionRepositoryAdapter` mapping entity → `PlatformVersion` and implementing the domain port; verify it compiles and the adapter has no domain-annotation leakage.
- [x] 3.3 Write a Testcontainers integration test (`*IT`, Failsafe) that starts PostgreSQL, runs Flyway, and asserts the adapter returns `PlatformVersion("0.1.0")`; verify the test passes with no locally installed database.
- [x] 3.4 Extend the integration test with an "unavailable" case (empty/missing row) asserting the adapter surfaces `PlatformVersionUnavailableException`; verify it passes.

## 4. Backend REST adapter & OpenAPI contract

- [x] 4.1 Implement `platform.infrastructure.api.rest` — `HelloController` (`GET /api/v1/hello`), `HelloResponse` DTO, `HelloResponseMapper`; verify a `@WebMvcTest` returns `200` with body `{"version":"0.1.0"}` for a stubbed use case.
- [x] 4.2 Implement `HelloExceptionHandler` mapping `PlatformVersionUnavailableException` → `503` with body `{"error":"PLATFORM_VERSION_UNAVAILABLE"}` and no stack trace / SQL / connection details; verify a `@WebMvcTest` asserts status, error code, and absence of internal detail.
- [x] 4.3 Author `implementation/platform/contracts/openapi.yaml` (OpenAPI 3.0.x) describing `GET /api/v1/hello`, the `200` schema, and the `503` error schema; verify it passes an OpenAPI linter/parser.
- [x] 4.4 Add a contract integration test asserting the springdoc-generated spec stays semantically compatible with `contracts/openapi.yaml` (paths, operations, response schemas); verify the test passes and fails when the endpoint diverges.

## 5. Architecture tests (ArchUnit)

- [x] 5.1 Add `ArchitectureTest` enforcing `domain ⊥ business`, `domain ⊥ infrastructure`, `business ⊥ infrastructure`, "no Spring/Spring Data/JPA/HTTP/jakarta.persistence types in `domain`", and "adapters reside under `infrastructure` (rest under `infrastructure.api.rest`, persistence under `infrastructure.persistence`)"; verify all rules pass.
- [x] 5.2 Add an ArchUnit rule asserting the backend contains only the `platform` functional module (guard against accidental product modules); verify it passes and would fail on a new sibling module.
- [x] 5.3 Verify `./mvnw -q verify` runs the architecture tests as part of the standard build (Surefire) and the whole command is green.

## 6. Frontend — Angular Home shell

- [x] 6.1 Scaffold the Angular app at `implementation/platform/frontend/web/` (standalone components, TypeScript, single `/` route); verify `npm ci && npm run build` succeeds.
- [x] 6.2 Add `_tokens.scss` / CSS custom properties implementing the design-system color, spacing, and typography tokens (dark theme); verify the Home view renders on a dark background using only tokens (no hard-coded hex in the component).
- [x] 6.3 Implement `HelloService` calling `GET /api/v1/hello` against relative base path `/api`, and `HomeComponent` with an explicit `loading | success | error` state; verify component unit tests: success renders the exact version, error renders an error message and renders no version, loading shown before resolution.
- [x] 6.4 Verify (unit/DOM test) the Home view exposes application identity and a version element, and contains no Portfolio/market/analysis/navigation-to-nonexistent-capability elements.

## 7. Containerization & local Compose runtime

- [x] 7.1 Add a multi-stage backend `Dockerfile` (build with Maven, run the jar with the OpenTelemetry Java agent attached via `JAVA_TOOL_OPTIONS`/`OTEL_*`); verify `docker build` produces a runnable image that serves `/actuator/health`.
- [x] 7.2 Add a multi-stage frontend `Dockerfile` (build Angular, serve static assets with nginx) plus `nginx.conf` proxying `/api/` to `http://core-service:8080/api/`; verify `docker build` succeeds and the container serves `/` and forwards `/api/v1/hello`.
- [x] 7.3 Author `implementation/platform/infrastructure/compose.yaml` with services `postgres`, `core-service`, `frontend`, `otel-collector`, `jaeger`, `prometheus`, `grafana`; healthchecks on `postgres` (`pg_isready`), `core-service` (`/actuator/health/readiness`), `frontend` (nginx `/`); `core-service depends_on postgres: service_healthy`; verify `docker compose config` is valid and `docker compose up -d` brings all services healthy.
- [x] 7.4 Verify from a browser against the running Compose stack that opening the frontend renders the platform version `0.1.0` end to end (manual check recorded in the PR evidence).

## 8. Observability pipeline

- [x] 8.1 Add `infrastructure/otel-collector/config.yaml` (OTLP receiver; exporters: OTLP→Jaeger for traces, `prometheus` exporter for metrics); verify the collector starts and reports the pipelines healthy.
- [x] 8.2 Add `infrastructure/prometheus/prometheus.yml` scraping the collector's metrics endpoint; verify Prometheus targets show the collector `UP`.
- [x] 8.3 Add `infrastructure/grafana/provisioning/` — Prometheus datasource YAML + one dashboard JSON with `hello`-endpoint panels (request count / rate / latency); verify Grafana starts with the datasource and dashboard already present (no manual setup).
- [x] 8.4 Execute a `hello` request against the running stack and verify: a matching trace is inspectable in Jaeger, the request metrics are queryable in Prometheus, and the Grafana dashboard shows the activity (record evidence).
- [x] 8.5 Verify that stopping the `otel-collector` container does not break `GET /api/v1/hello` (still returns `0.1.0`) and the export failure is visible in backend logs only.

## 9. Lifecycle scripts

- [x] 9.1 Implement `implementation/platform/start.sh`: `docker compose up -d --build`, then bounded per-service health polling; on success print the endpoint table (frontend, backend, Jaeger, Prometheus, Grafana); on any unhealthy service print its name and exit non-zero; verify a normal run exits `0` and prints endpoints.
- [x] 9.2 Verify `start.sh` failure behavior: with `postgres` forced to fail, the script reports PostgreSQL and exits non-zero without reporting the platform Ready.
- [x] 9.3 Implement `implementation/platform/stop.sh` (`docker compose down`); verify it exits `0` when running and is a safe no-op when already stopped.
- [x] 9.4 Implement `implementation/platform/e2e.sh`: ensure the platform is healthy (reuse start logic), run the Playwright container against the frontend, propagate its exit code, and copy the Playwright report/traces out on failure; verify exit code propagation for both a passing and a deliberately failing run.

## 10. Browser E2E (Playwright)

- [x] 10.1 Scaffold `implementation/platform/e2e/` (`package.json`, `playwright.config.ts` with a Chromium-only project, `baseURL` = frontend service) running from the official Playwright image as a Compose service; verify `docker compose run --rm playwright --version` works without host browser installs.
- [x] 10.2 Write `platform-smoke.spec.ts`: open `/`, wait for the success state, assert the visible version text is exactly `0.1.0`, and assert the error state is absent; verify the test passes via `./e2e.sh`.
- [x] 10.3 Verify the negative path: with the backend stopped, `platform-smoke.spec.ts` fails and the Home page shows the explicit error state (no fabricated version); confirm `e2e.sh` returns non-zero.

## 11. Documentation & ADR

- [x] 11.1 Add `reference/engineering/adrs/ADR-001-platform-baseline.md` recording the decisions in `design.md` (single `core-service`, `com.myfinaimanager.core`, `platform` module, nginx reverse-proxy routing, OTel agent + Collector fan-out, Compose runtime); verify it follows the ADR structure required by `architecture.md` (problem, decision, alternatives, consequences).
- [x] 11.2 Add `implementation/platform/README.md` documenting local execution and verification entry points (`start.sh`, `stop.sh`, `e2e.sh`, `./mvnw verify`, endpoint URLs, observability UIs); verify a reader can start and verify the platform from the README alone.
- [x] 11.3 Update the repository `README.md` to point to `implementation/platform/` as the executable platform; verify links resolve.

## 12. Full verification & acceptance-criteria traceability

- [x] 12.1 Run `./mvnw verify` from `backend/core-service/` and confirm compile + Surefire (unit + ArchUnit) + Failsafe (Testcontainers + contract) are all green (AC-001, AC-002, AC-003, AC-007, AC-008).
- [x] 12.2 Run `./implementation/platform/start.sh` and confirm all seven services reach healthy and endpoints are printed (AC-004); open the frontend and confirm the empty Home shell renders the version (AC-005, AC-006).
- [x] 12.3 Run `./implementation/platform/e2e.sh` and confirm the containerized Chromium smoke test passes and the exit code propagates (AC-012, and the mandatory browser-to-database scenario).
- [x] 12.4 Verify observability acceptance: trace in Jaeger (AC-009), metrics in Prometheus (AC-010), provisioned Grafana dashboard exposing `hello` telemetry with no manual setup (AC-011).
- [x] 12.5 Run `./implementation/platform/stop.sh` twice and confirm clean stop then safe no-op (AC-013).
- [x] 12.6 Produce `pr-evidence.md` in the change folder mapping every EN001 Acceptance Criterion (AC-001…AC-014) to its test/command/manual check and result, and confirm no Portfolio/market/AI behavior was introduced (AC-014).
