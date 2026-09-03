# Data Model — EN002 Containerized E2E Testing Foundation (Phase 1)

EN002 introduces **no business or persistent domain entities**. The Information Model under
`product/definition/global/` is unaffected. This document instead captures the **operational
model** the enabler defines: images, services, configuration, volumes, and E2E artifacts.

---

## 1. Container images

| Image | Source | Base (build → runtime) | Contains | Must NOT contain |
|---|---|---|---|---|
| `core-service` | `implementation/platform/backend/core-service/Dockerfile` | `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre-jammy` (+`curl`) | the packaged Spring Boot jar, a JRE, `curl` (probe), non-root user | Maven, source tree, secrets |
| `web` (frontend) | `implementation/platform/frontend/web/Dockerfile` | `node:22-alpine` → `nginx:1.27-alpine` | `dist/web/browser/` static assets, `nginx.conf` | Node, `node_modules`, `ng`, secrets, env-specific hostnames |
| `e2e` | `implementation/platform/e2e/Dockerfile` | `mcr.microsoft.com/playwright:v<pinned>-noble` | Node, Playwright Test, Chromium (from base), `tests/`, `support/`, `playwright.config.ts` | secrets; unpinned Playwright version |

Version tags are pinned per **OD-EN002-1…3** (research.md).

---

## 2. Compose services

| Service | Image | Exposed port (container) | Published port — default platform | Published port — E2E (`compose.e2e.yaml`) | Health check |
|---|---|---|---|---|---|
| `postgres` | `postgres:16-alpine` | 5432 | `${POSTGRES_PORT:-5432}:5432` | `15432:5432` | `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB` |
| `backend` | build `core-service` | 8080 | `8080:8080` | `18080:8080` | `curl -fsS localhost:8080/actuator/health/readiness` |
| `frontend` | build `web` | 80 | `4200:80` | `14200:80` | `wget -q -O - http://localhost/` |
| `e2e` | build `e2e` | — | — (never published) | — | — (run via `docker compose run`) |

**Dependency ordering** (`condition: service_healthy`): `postgres → backend → frontend → e2e`.
`e2e` also carries `profiles: ["e2e"]` — excluded from a plain `docker compose up`.

---

## 3. Configuration model (externalized — no secrets in source)

`implementation/platform/infrastructure/local/.env` (copied from `.env.example`, git-ignored):

| Variable | Consumed by | Default (synthetic) | Notes |
|---|---|---|---|
| `POSTGRES_DB` | `postgres`, `backend` | `finai` | |
| `POSTGRES_USER` | `postgres`, `backend` | `finai` | non-production |
| `POSTGRES_PASSWORD` | `postgres`, `backend` | `finai_local_dev` | non-production, **not a secret**, still never a real credential |
| `POSTGRES_PORT` | `start.sh`, `compose.yaml` | `5432` | host publish only |
| `SPRING_DATASOURCE_URL` | `backend` | `jdbc:postgresql://postgres:5432/finai` | **container** hostname `postgres`, not `localhost` |
| `SPRING_DATASOURCE_USERNAME` | `backend` | `finai` | mirrors `POSTGRES_USER` |
| `SPRING_DATASOURCE_PASSWORD` | `backend` | `finai_local_dev` | mirrors `POSTGRES_PASSWORD` |
| `E2E_BASE_URL` | `e2e` (Playwright) | `http://frontend` | set by `compose.e2e.yaml`; Compose-internal DNS |

Image/version pins live as Compose `build.args` or a documented `versions` block — configuration,
never secrets.

**Invariant**: no credential, token, or certificate appears in any Dockerfile, Compose file,
`nginx.conf`, Playwright config, `.env.example`, or E2E artifact (FR-037, VC-013).

---

## 4. Volume model

| Volume | Scope | Lifecycle | Purpose |
|---|---|---|---|
| `postgres-data` (named, project `my-finai-manager-local`) | default platform | **persists** across `start.sh`/`stop.sh`; removed only by an explicit `docker compose down -v` | developer's working data |
| disposable postgres volume (project `finai-e2e`, anonymous) | each `e2e.sh` run | created on `up`, **removed by `e2e.sh`'s `down -v`** | clean DB per E2E run |
| `./e2e/test-results` bind mount | each `e2e.sh` run | host directory, git-ignored, overwritten per run | diagnostics land on the host |

**Invariant**: an `e2e.sh` run never references or removes the default platform's `postgres-data`
volume (OD-6, VC-010).

---

## 5. E2E artifact model

Produced under `implementation/platform/e2e/test-results/` (git-ignored):

| Artifact | When kept | Notes |
|---|---|---|
| Screenshot (`*.png`) | on test failure only | `screenshot: 'only-on-failure'` |
| Trace (`trace.zip`) | on test failure only | `trace: 'retain-on-failure'`; open with `npx playwright show-trace` |
| HTML report | every run | `reporter: html`, `open: 'never'` |
| Container logs (`containers/*.log`) | on E2E failure only (optional) | `e2e.sh` may dump `docker compose logs` |
| Video | never (initially) | `video: 'off'` (OD-8) |

**Invariant**: passing runs retain no trace/video; no artifact is committed; no artifact contains
secrets (OD-8, VC-013).

---

## 6. Lifecycle entry points (developer-facing "API" of the platform)

| Command | Effect | Exit code contract |
|---|---|---|
| `implementation/platform/start.sh` | Compose `up -d postgres backend frontend`; wait health; print URLs. `BUILD=1` → build first. Does **not** rebuild by default (OD-9). | 0 on ready; non-zero on Docker down / port busy / missing config / readiness timeout |
| `implementation/platform/stop.sh` | Compose `down` (keeps `postgres-data`). | 0 always when it can reach Docker; safe no-op when nothing runs |
| `implementation/platform/e2e.sh [playwright args]` | Isolated project `finai-e2e`: build → `up` deps → wait health → `run --rm e2e` → `down -v` (via `trap`). Forwards args to `playwright test`. | **propagates Playwright's exit code** (0 pass / non-zero fail) (VC-011) |

No other mechanism may start/stop the platform (FR-015).

---

## 7. State transitions (platform runtime)

```text
        start.sh                         stop.sh
STOPPED ─────────► STARTING ──health──► RUNNING ─────────► STOPPED
   ▲                  │ timeout / error                        │
   └──────────────────┘  (script exits non-zero, partial up)   │
                                                               │
   e2e.sh:  STOPPED(e2e project) ─build+up─► RUNNING(e2e) ─run─► TEARDOWN ─down -v─► STOPPED(e2e project)
            (always reaches TEARDOWN via trap, even on failure)
```

The default platform and the `finai-e2e` project are independent state machines (separate Compose
projects, networks, and volumes) and may be RUNNING simultaneously (OD-EN002-4 port shift).
