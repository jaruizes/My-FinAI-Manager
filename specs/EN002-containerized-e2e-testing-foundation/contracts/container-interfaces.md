# Container Interface Contract — EN002

EN002 introduces **no new external REST API**. The platform's external business contract remains
`implementation/platform/contracts/openapi/openapi.yaml` (FD001's `POST /api/portfolios`), and
nginx forwards it **byte-for-byte** — the frontend already calls it with relative `/api` URLs.

What EN002 *does* define is the **interface between Compose services** (and between `e2e.sh` and
the platform). This document is that contract; contract drift here breaks the containerized
platform or the E2E run.

---

## 1. `postgres` service

| Aspect | Contract |
|---|---|
| Image | `postgres:16-alpine` (unchanged from EN001) |
| Listens | `5432/tcp` on the Compose network as host `postgres` |
| Consumes (env) | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| Readiness | `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB` exits 0 |
| Data | default platform: named volume `postgres-data` (persistent). E2E: disposable volume, removed on `down -v` |
| Guarantee to `backend` | a reachable PostgreSQL 16 with the configured DB/role once `service_healthy` |

## 2. `backend` service (`core-service`)

| Aspect | Contract |
|---|---|
| Build context | `implementation/platform/backend/core-service` (its `Dockerfile`) |
| Listens | `8080/tcp` on the Compose network as host `backend`; published to the host for debugging |
| Consumes (env) | `SPRING_DATASOURCE_URL` (must point at `postgres:5432`), `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| Depends on | `postgres: condition: service_healthy` |
| Readiness | `GET /actuator/health/readiness` → `200 {"status":"UP"}` (JVM booted, Flyway migrated, DB reachable) |
| Provides | the existing REST surface under `/api/**` and Actuator under `/actuator/**`. **No new endpoint.** |
| Startup behaviour | tolerates `postgres` briefly unavailable and converges (unchanged from EN001) |
| Must not | contain build tooling, embedded credentials, or run as root |

## 3. `frontend` service (nginx)

| Aspect | Contract |
|---|---|
| Build context | `implementation/platform/frontend/web` (its `Dockerfile` + `nginx.conf`) |
| Listens | `80/tcp` on the Compose network as host `frontend`; published to the host (`4200` default / `14200` E2E) |
| Depends on | `backend: condition: service_healthy` (so `backend` DNS resolves when nginx starts) |
| Readiness | `GET http://frontend/` → `200` and serves `index.html` |
| Routing contract | `location / ` → static assets with SPA fallback `try_files $uri $uri/ /index.html`; `location /api/` → `proxy_pass http://backend:8080` **preserving the full `/api/...` path** and forwarding `Host` / `X-Forwarded-*` |
| Serves | the Angular production build from `dist/web/browser/` |
| Must not | run `ng serve`; embed a backend hostname in the JS bundle; require the browser to resolve a Docker service name |

### Browser → backend routing rule (OD-5)

```text
Browser (host or inside e2e container)
   │  same-origin request to  /api/portfolios
   ▼
frontend (nginx)  ──  location /api/  ──►  http://backend:8080/api/portfolios
   ▼
backend (core-service)  ──►  postgres:5432
```

The browser **never** addresses `backend` directly. `/actuator/**` is **not** proxied by nginx
(operational, not part of the browser surface).

## 4. `e2e` service (Playwright runner)

| Aspect | Contract |
|---|---|
| Build context | `implementation/platform/e2e` (its `Dockerfile`) |
| Base image | `mcr.microsoft.com/playwright:v<pinned>-noble` — bundled Chromium; no `playwright install` needed |
| Profile | `e2e` — never started by a plain `docker compose up` |
| Depends on | `frontend: condition: service_healthy` |
| Consumes (env) | `E2E_BASE_URL` (default `http://frontend`) |
| Command | `npx playwright test` (args forwarded from `e2e.sh "$@"`) |
| Reads | `tests/**`, `support/**`, `playwright.config.ts` (baked into the image) |
| Writes | `/work/test-results/**` → bind-mounted to `implementation/platform/e2e/test-results/` on the host |
| Exit code | Playwright's exit code — **0 = all passed, non-zero = failure** (propagated by `e2e.sh`, VC-011) |
| Must not | require host-installed browsers; retain trace/video for passing tests; write secrets into artifacts |

## 5. `e2e.sh` ⇄ platform contract

| Step | Contract |
|---|---|
| Project name | `finai-e2e` (via `-p`) — isolated from `my-finai-manager-local` |
| Compose files | `-f infrastructure/local/compose.yaml -f infrastructure/local/compose.e2e.yaml` |
| Volumes | disposable only; `down -v` on exit (via `trap`) removes them; the default `postgres-data` volume is never referenced |
| Host ports | shifted (`15432 / 18080 / 14200`) so it coexists with a running default platform |
| Readiness gate | polls Compose health for `postgres`, `backend`, `frontend` with a bounded timeout before running Playwright; on timeout → dump logs, `down -v`, exit non-zero |
| Output | Playwright exit code; diagnostics in `e2e/test-results/` |

## 6. `start.sh` / `stop.sh` contract (unchanged interface, new implementation)

| Command | Contract |
|---|---|
| `start.sh` | brings `postgres` + `backend` + `frontend` to `healthy` as **containers only**; prints frontend/backend URLs; exits non-zero with a clear message on Docker-down / port-busy / missing-config / readiness-timeout; does **not** rebuild images unless `BUILD=1` |
| `stop.sh` | `docker compose down` for the default platform; keeps `postgres-data`; safe no-op when already stopped; **no PID files** |

## 7. External REST contract — unchanged

`implementation/platform/contracts/openapi/openapi.yaml` is **not modified** by EN002. The
contract test (`CreatePortfolioControllerContractTest`) continues to guard it. nginx is a
transparent forwarder for `/api/**`.
