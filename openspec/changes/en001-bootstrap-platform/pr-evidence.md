# EN001 — Executable Platform Foundation · Validation Evidence

**Change:** `en001-bootstrap-platform`
**Branch:** `enabler/EN001-bootstrap-platform`
**Date:** 2026-09-08
**Definition:** `product/definition/enablers/EN001-bootstrap-platform/EN001-bootstrap-platform.md`

All 47 implementation tasks complete. All mandatory EN001 Acceptance Criteria
verified. No product behaviour introduced.

---

## How the evidence was produced

| Command | Result |
|---|---|
| `cd implementation/platform/backend/core-service && ./mvnw clean verify` (JDK 21) | **BUILD SUCCESS** — Surefire 21 (13 unit + 8 ArchUnit), Failsafe 4 (2 Testcontainers IT + 2 OpenAPI contract IT), JaCoCo report generated |
| `./implementation/platform/start.sh` | exit 0 — all 7 containers healthy in ~40s (with image build) / ~23s (cached); endpoints printed |
| `./implementation/platform/e2e.sh` | exit 0 — containerized Playwright (Chromium) `1 passed`; report/traces under `e2e/` |
| `./implementation/platform/stop.sh` (x2) | exit 0 both times — clean stop, then safe no-op |
| Negative E2E (backend stopped) | Playwright smoke test **fails** (times out on version, no fabricated value); screenshot retained |
| `start.sh` with Postgres forced to exit | exit 1 — compose names the failed dependency; "platform is NOT ready" |

Frontend: `cd implementation/platform/frontend/web && npm ci && npm run build`
(bundle generated) and `npm test` (**5 specs green**, headless Chrome).

---

## Acceptance Criteria

| ID | Scenario | Evidence | Status |
|---|---|---|---|
| **AC-001** | Maven backend baseline | `./mvnw clean verify` → BUILD SUCCESS; compile + unit + architecture + integration tests execute and pass | ✅ |
| **AC-002** | Standard architecture enforced | `ArchitectureTest` (8 rules): `domain ⊥ business`, `domain ⊥ infrastructure`, `business ⊥ infrastructure`, no Spring/JPA/HTTP/Hibernate/serialization/JDBC types in `domain`, controllers under `infrastructure.api.rest`, Spring-Data repos & `@Entity` under `infrastructure.persistence`, only the `platform` module exists. Probed with a deliberate violation → build fails. | ✅ |
| **AC-003** | PostgreSQL integration via Testcontainers | `PlatformVersionRepositoryAdapterIT` — disposable `postgres:16-alpine` container, Flyway runs, adapter returns `PlatformVersion("0.1.0")`; no local database required | ✅ |
| **AC-004** | Complete local platform starts | `start.sh` → `postgres`, `core-service`, `frontend`, `otel-collector`, `jaeger`, `prometheus`, `grafana` all healthy; local endpoints printed; readiness gated on container health checks (`docker compose up --wait`), not sleeps | ✅ |
| **AC-005** | Empty Angular Home | `http://localhost:8080` serves the Angular shell (`<title>My-FinAI-Manager</title>`, `app-root`); Home component test asserts identity + a version element and **no** `nav` / `a` / `button` / `form` / `input` / `table` | ✅ |
| **AC-006** | Full browser-to-database E2E | `e2e/tests/platform-smoke.spec.ts` opens `/`, asserts `data-testid="version"` text is exactly `0.1.0` (seeded by Flyway), asserts the error state is absent. Passes in the containerized Chromium run; **fails** when the chain is broken. | ✅ |
| **AC-007** | REST contract | `implementation/platform/contracts/openapi.yaml` (OpenAPI 3.0) describes `GET /api/v1/hello`, `200` `HelloResponse`, `503` `ErrorResponse`. `HelloContractIT` asserts the springdoc-generated spec stays semantically compatible (path, operationId, response codes {200,503}, schema shape) and fails on divergence. | ✅ |
| **AC-008** | No fabricated version on persistence failure | Domain: `GetPlatformVersion` throws `PlatformVersionUnavailableException` when absent. Adapter translates `DataAccessException` to the same. REST: `HelloExceptionHandler` → `503` `{"error":"PLATFORM_VERSION_UNAVAILABLE"}`, no stack trace / SQL / connection string (`HelloControllerTest`, `PlatformVersionRepositoryAdapterIT` unavailable case). Frontend: `Home` enters `error` state and renders no version (`home.spec.ts`). | ✅ |
| **AC-009** | Observability trace in Jaeger | After `hello` traffic: `GET http://localhost:16686/api/traces?service=core-service&operation=GET /api/v1/hello` returns a trace whose spans include `GET /api/v1/hello`, `PlatformVersionJpaRepository.findAll`, and `SELECT myfinaimanager.platform_version` | ✅ |
| **AC-010** | Observability metrics in Prometheus | Targets `core-service`, `otel-collector`, `prometheus` all `up`. `sum(http_server_request_duration_seconds_count{http_route="/api/v1/hello"})` returns a cumulative count (e.g. `22`). | ✅ |
| **AC-011** | Grafana dashboard | `http://localhost:3000/api/health` ok; datasources `prometheus` + `jaeger` provisioned; `dash-db` search returns **"Platform — Hello Endpoint"** (panels: hello total, hello request rate, hello latency p95). Anonymous viewing enabled — no manual setup after startup. | ✅ |
| **AC-012** | Canonical E2E command | `./implementation/platform/e2e.sh` runs Playwright **in a container** (`mcr.microsoft.com/playwright:v1.49.1-noble`), Chromium only, against the containerized frontend, no host browser install; propagates the Playwright exit code; retains `playwright-report/` + `test-results/` on failure | ✅ |
| **AC-013** | Complete platform stops | `./implementation/platform/stop.sh` stops & removes all containers (and the ephemeral DB volume); running it again when already stopped exits 0 with no error | ✅ |
| **AC-014** | No product functionality invented | Only persisted object is `platform_version` (one row). No Portfolio / Financial Instrument / market-data / news / AI code. ArchUnit `onlyThePlatformFunctionalModuleExists` guards the backend; `home.spec.ts` guards the UI; no Kafka / Neo4j / Redis / search / vector / auth / Kubernetes dependencies added. | ✅ |

---

## Guardrail conformance

| Guardrail | Evidence |
|---|---|
| `architecture.md` / `architecture-rules.md` | Module-first `com.myfinaimanager.core.platform.{domain,business,infrastructure}`; dependency direction enforced by ArchUnit (AAC-001..AAC-004, AAC-022); executable under `implementation/platform/` with `start.sh`/`stop.sh` (AAC-021). ADR-001 records the baseline decisions (AAC-020). |
| `technology-policy.md` | Angular + TypeScript; Spring Boot + Maven (REQUIRED); Spring Data JPA/Hibernate (REQUIRED); Flyway; PostgreSQL; OpenAPI (REQUIRED); OpenTelemetry; JUnit 5 + AssertJ + Mockito; Testcontainers (REQUIRED where applicable); ArchUnit; Playwright + Chromium. No technology outside the approved matrix; no speculative infrastructure (AAC-019). |
| `development-rules.md` | Simplest correct implementation; `PlatformVersion` value object (no primitive obsession, no annotations); explicit failure states (DR-006/007/009); externalised config, no secrets committed — local dev credentials only (DR-010/011); no unrelated refactoring (DR-016). |
| `testing.md` | Behaviour tested at the lowest reliable level; real PostgreSQL for integration; OpenAPI contract validated; architecture verified automatically; one high-value browser E2E. |
| `ux/design-system.md` | Dark theme via shared tokens in `src/styles/_tokens.scss` (colours, spacing, radius, typography); Home renders explicit `loading` / `success` / `error` states; no component hard-codes visual values; no navigation to non-existent capabilities. |

---

## Notes for the reviewer (non-blocking)

- **Build JDK:** `core-service` builds/tests on **Java 21** (`.sdkmanrc` pins
  `21.0.2-open`). Newer JDKs break ArchUnit's bundled ASM.
- **Testcontainers ↔ Docker API:** the build sets `-Dapi.version=1.44`
  (property `docker.api.version`) so docker-java connects to modern engines.
- **buildx:** `start.sh` builds images with `docker build` (not
  `docker compose build`) to avoid a hard dependency on buildx ≥ 0.17.
- **Architecture:** the observability containers use ephemeral storage (no
  volumes) to keep the developer-machine footprint reasonable (EN001 A-004).
- Repository is uncommitted on the enabler branch, pending Solution Validation
  and human review.
