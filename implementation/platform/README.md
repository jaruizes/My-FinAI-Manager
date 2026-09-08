# My-FinAI-Manager — Executable Platform

The complete local platform for My-FinAI-Manager, delivered by **EN001 —
Establish Executable Platform Foundation**.

It proves, end to end, that the approved frontend, backend, relational
persistence, automated testing, architecture validation, containerized runtime,
and observability foundations work together:

```
Browser → Angular → REST → Spring Boot → PostgreSQL
                       └────────── OpenTelemetry ──────────→ Collector → Jaeger / Prometheus → Grafana
```

There is **no product functionality** here yet — only a minimal `hello`
capability that returns the platform version persisted in PostgreSQL.

---

## Layout

```
implementation/platform/
├── backend/core-service/     Spring Boot service (module-first: domain / business / infrastructure)
├── frontend/web/             Angular application + nginx reverse proxy
├── contracts/openapi.yaml    Platform OpenAPI contract (GET /api/v1/hello)
├── infrastructure/           Docker Compose runtime + observability config
├── e2e/                      Containerized Playwright (Chromium) browser E2E
├── start.sh  stop.sh  e2e.sh Canonical lifecycle entry points
```

---

## Prerequisites

- A Docker-compatible engine running (Docker Desktop, colima, …).
- For working on the backend directly: **JDK 21** (`cd backend/core-service && sdk env`).
- For working on the frontend directly: **Node 20.19+**.

Everything else runs in containers — no local PostgreSQL, JDK, Node, or browser
is required to start or verify the platform.

---

## Run the platform

```bash
./implementation/platform/start.sh
```

Builds the application images, starts all seven containers, and waits on their
health checks. On success it prints the local endpoints:

| Service | URL |
|---|---|
| Frontend (Home) | http://localhost:8080 |
| Business API | http://localhost:8081/api/v1/hello |
| OpenAPI document | http://localhost:8081/v3/api-docs |
| Jaeger (traces) | http://localhost:16686 |
| Prometheus (metrics) | http://localhost:9090 |
| Grafana (dashboards) | http://localhost:3000 — anonymous viewing enabled; `admin` / `admin` |

Open http://localhost:8080 — the Home page shows the platform version
(`0.1.0`) read from PostgreSQL.

Stop everything (also removes the ephemeral database):

```bash
./implementation/platform/stop.sh
```

Useful environment variables for `start.sh`: `PLATFORM_SKIP_BUILD=1` (reuse
existing images), `PLATFORM_START_TIMEOUT=<seconds>`.

---

## Verify the platform

### Backend build, unit, architecture, and integration tests

```bash
cd implementation/platform/backend/core-service
./mvnw verify
```

Runs compilation + Surefire (domain/business unit tests, ArchUnit architecture
rules) + Failsafe (PostgreSQL integration via Testcontainers, OpenAPI contract).
Requires a running Docker engine for the integration tests; no local database.

### Frontend unit tests

```bash
cd implementation/platform/frontend/web
npm ci
npm test          # headless Chrome; set CHROME_BIN if Chrome is not auto-detected
```

### Containerized browser E2E

```bash
./implementation/platform/e2e.sh
```

Ensures the platform is healthy, then runs the Playwright (Chromium) smoke test
**in a container** against the containerized frontend, proving the full
`browser → … → PostgreSQL` path. The script propagates Playwright's exit code;
diagnostics (report, traces, screenshots, video) are written to
`e2e/playwright-report/` and `e2e/test-results/` and retained on failure.

---

## Observability

After running `start.sh` and generating a few `hello` requests:

- **Jaeger** (http://localhost:16686) — search for service `core-service`; a
  `GET /api/v1/hello` trace includes the nested PostgreSQL `SELECT` span.
- **Prometheus** (http://localhost:9090) — e.g.
  `http_server_request_duration_seconds_count{http_route="/api/v1/hello"}`.
- **Grafana** (http://localhost:3000) — the **Platform — Hello Endpoint**
  dashboard is provisioned automatically (no manual setup).

Stopping the `otel-collector` container does **not** affect `GET /api/v1/hello` —
observability is best-effort and never corrupts application behaviour.

---

## Architecture notes

See `reference/engineering/adrs/ADR-001-platform-baseline.md` for the decisions
behind this baseline (single `core-service`, the `platform` module, nginx
routing, the OpenTelemetry agent + Collector, the Compose runtime, Java 21).
