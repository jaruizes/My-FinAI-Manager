# Quickstart / Validation — EN002 Containerized E2E Testing Foundation

Runnable scenarios that prove EN002 end to end. Each maps to the enabler's Verification Criteria
(`VC-001 … VC-015`). Details live in [plan.md](./plan.md), [research.md](./research.md),
[data-model.md](./data-model.md), and [contracts/container-interfaces.md](./contracts/container-interfaces.md) —
not duplicated here.

## Prerequisites

- **Docker + Docker Compose v2** running. That is the **only** runtime dependency — no local
  Spring Boot, Angular dev server, PostgreSQL, Node, or Playwright browsers required to *run* the
  platform or the E2E suite.
- `implementation/platform/infrastructure/local/.env` present
  (`cp infrastructure/local/.env.example infrastructure/local/.env`).
- First run pulls base images and builds the three platform images — allow a few minutes.

---

## A. Build the platform images (one-time / on change)

```bash
cd implementation/platform
BUILD=1 ./start.sh          # or: docker compose -f infrastructure/local/compose.yaml build
```

**Expected**: `core-service`, `web`, and `e2e`(on demand) images build with no error; the build
stages use Maven / Node, the runtime stages do not (`docker history <img>` shows no Maven/Node in
the final layers). — **VC-001, VC-002, VC-007**

---

## B. Fully containerized start  →  **VC-001…VC-004, VC-006, VC-012**

```bash
cd implementation/platform
./start.sh
```

**Expected**:

- `postgres`, `backend`, `frontend` reach `healthy` (script polls Compose health; bounded timeout).
- The script prints `Frontend: http://localhost:4200` and `Backend: http://localhost:8080`.
- `docker compose -f infrastructure/local/compose.yaml ps` shows 3 running services.
- `ps aux | grep -E 'spring-boot:run|ng serve'` → **nothing** (no host application process). — **VC-004**
- `curl -fsS http://localhost:8080/actuator/health` → `{"status":"UP", ...}` incl. the `db`
  component `UP`. — **VC-006**
- `curl -fsS http://localhost:4200/` → the app shell HTML.
- `curl -fsS http://localhost:4200/api/portfolios -X POST -H 'Content-Type: application/json'
  -H 'Idempotency-Key: qs-'$RANDOM$RANDOM -d '{"name":"QS","positions":[{"ticker":"ASML","market":"XAMS","quantity":"1","currency":"EUR"}]}'`
  → `201` — proves the **browser→nginx→backend→postgres** path (routing contract). — **VC-003**

---

## C. Fully containerized stop  →  **VC-005**

```bash
./stop.sh
docker compose -f infrastructure/local/compose.yaml ps    # → no services
./stop.sh                                                  # again → exits 0, safe no-op
```

**Expected**: all platform containers stopped through Compose; the `postgres-data` volume still
exists (`docker volume ls | grep postgres-data`) so developer data persists.

---

## D. One-command containerized E2E smoke test  →  **VC-007…VC-009, VC-011, VC-012**

```bash
cd implementation/platform
./e2e.sh
echo "exit=$?"
```

**Expected**:

- `e2e.sh` builds images, starts an **isolated** project `finai-e2e` (shifted host ports), waits
  for `postgres`/`backend`/`frontend` health, then runs Playwright in the `e2e` container.
- The Playwright `list` reporter shows `platform-smoke.spec.ts` **passing** (Chromium). — **VC-009**
- No Playwright browser is installed on the host (`npx playwright --version` on the host may be
  absent) — the browser ran inside the container. — **VC-007**
- The test drove `http://frontend/` in a real browser (not a direct backend API call). — **VC-008**
- `e2e.sh` exits **0**. — **VC-011**
- After the run, `docker compose -p finai-e2e ... ps` → nothing, and the disposable volume is gone
  (`down -v` ran). — **VC-010 (teardown half)**

Run a single test / filter:

```bash
./e2e.sh -g "shell loads"
```

---

## E. `e2e.sh` returns non-zero on failure  →  **VC-011**

```bash
# Temporarily break the routing contract, e.g. point nginx /api at a wrong port, rebuild, then:
./e2e.sh; echo "exit=$?"      # → non-zero
# (revert the change afterwards)
```

**Expected**: the smoke test fails (frontend can't reach the backend), `e2e.sh` exits non-zero,
and the isolated environment is still torn down.

---

## F. Repeatability & no contamination  →  **VC-010**

```bash
./e2e.sh && ./e2e.sh && ./e2e.sh ; echo "exit=$?"
```

**Expected**: three consecutive runs, all pass, **no manual cleanup** between them — each run gets
a fresh disposable database.

---

## G. E2E never destroys local developer data  →  **VC-010, OD-6**

```bash
./start.sh
curl -s -X POST http://localhost:4200/api/portfolios -H 'Content-Type: application/json' \
  -H "Idempotency-Key: keep-$RANDOM" \
  -d '{"name":"DEV DATA — keep me","positions":[{"ticker":"MSFT","market":"XNAS","quantity":"5","currency":"USD"}]}'
docker exec my-finai-manager-postgres psql -U finai -d finai -c "select count(*) from portfolio;"   # e.g. 1

./e2e.sh                                                     # runs the isolated E2E env

docker exec my-finai-manager-postgres psql -U finai -d finai -c "select count(*) from portfolio;"   # STILL 1
./stop.sh
```

**Expected**: the developer's portfolio row and volume are unchanged after `e2e.sh`.

---

## H. Diagnostics on failure  →  **VC-013**

After scenario **E** (a forced failure):

```bash
ls implementation/platform/e2e/test-results/
npx playwright show-trace implementation/platform/e2e/test-results/**/trace.zip   # if Playwright is on the host
```

**Expected**: a screenshot and a `trace.zip` for the failed test exist; an HTML report exists;
**no** video for the run; `git status` shows `e2e/test-results/` is **ignored** (nothing to
commit); grep the artifacts for the DB password → **not present**. — **VC-013**

For a **passing** run: `e2e/test-results/` contains the HTML report but **no** trace or video.

---

## I. Documentation  →  **VC-014**

Confirm the repository documents, in `implementation/platform/README.md` and
`implementation/platform/e2e/README.md`:

- build/start the containerized platform; stop it;
- run the full E2E suite; run a single E2E test;
- debug a failing E2E test (trace viewer);
- add a feature-specific E2E test (`FD00N-<slug>.spec.ts` naming + where it goes).

---

## J. No product scope  →  **VC-015**

```bash
git diff --stat main...HEAD   # review the change set
```

**Expected**: changes are limited to `implementation/platform/{backend,frontend,e2e,infrastructure}`
Dockerfiles/config, `start.sh` / `stop.sh` / `e2e.sh`, `README`s, `.gitignore`, and the `specs/`
artifacts. **No** new domain/application/adapter class, **no** new REST operation in
`openapi.yaml`, **no** migration, **no** auth, **no** `.github/` workflow.

---

## Verification Criteria coverage

| VC | Scenario(s) | Evidence (this implementation, 2026-09-01) |
|---|---|---|
| VC-001 Backend container | A, B | `docker build` OK; runtime image 115 MB; `docker history` → 0 Maven refs |
| VC-002 Frontend container | A, B | `docker build` OK; runtime image 21 MB (nginx:alpine) |
| VC-003 PostgreSQL in topology + backend connects | B | `/actuator/health` → `db: UP`; `POST /api/portfolios` via `:4200` → 201 |
| VC-004 Fully containerized start (no host process) | B | `ps aux` → no `spring-boot:run` / `ng serve` after `./start.sh` |
| VC-005 Fully containerized stop (safe repeat) | C | `./stop.sh` ×2 → exit 0; `postgres-data` volume retained |
| VC-006 Reachability (frontend / backend health / DB) | B | health `UP`+`db UP`; frontend `/` → 200; deep link `/portfolios/new` → 200 |
| VC-007 Playwright in a container, no host browsers | A, D | `e2e` image built FROM `mcr.microsoft.com/playwright:v1.62.1-noble`; smoke ran with no host Playwright install |
| VC-008 E2E against the running frontend & stack | D | smoke `page.goto('/')` drives nginx → Angular; nginx access log shows the browser request |
| VC-009 Smoke test passes | D | `platform-smoke.spec.ts` → `1 passed` (Chromium) |
| VC-010 Repeatable, no contamination, dev data safe | F, G | 3 consecutive `./e2e.sh` → all pass; default platform `portfolio` count 2→2 across an `./e2e.sh` run; `finai-e2e` containers/volumes/networks = 0 after teardown |
| VC-011 `e2e.sh` entry point, non-zero on failure | D, E | healthy → exit 0; forced break → exit 1 |
| VC-012 Explicit readiness checks, bounded timeouts | B, D | `pg_isready` / `/actuator/health/readiness` / nginx HTTP; `start.sh` + `e2e.sh` poll with bounded deadlines, actionable failure |
| VC-013 Failure diagnostics; no secrets | H | forced failure → `test-failed-1.png` + `trace.zip` + `containers/*.log` + HTML report; `grep finai_local_dev` in artifacts → none; passing run keeps only the HTML report |
| VC-014 Documentation | I | `implementation/platform/README.md` + `e2e/README.md` cover build/start/stop, run all/one, debug, add a feature test |
| VC-015 No product scope | J | change set = Dockerfiles / nginx.conf / compose(+e2e) / `{start,stop,e2e}.sh` / e2e project / docs / specs; `mvn verify` + `ng test` still green (no app source change) |

### Execution log

- `mvn -B clean verify` (host, backend) → **BUILD SUCCESS** (84 tests, JaCoCo gate met) — unchanged by EN002.
- `ng test --watch=false --browsers=ChromeHeadless` (host, frontend) → **26/26 SUCCESS** — unchanged by EN002.
- `BUILD=1 ./start.sh` → builds 2 images, all 3 services healthy, exit 0.
- `./stop.sh` ×2 → exit 0, volume kept.
- `./e2e.sh` → isolated `finai-e2e` project, smoke `1 passed`, exit 0, full teardown.
- `E2E_BASE_URL=http://frontend:9999 ./e2e.sh` (forced failure) → exit 1, diagnostics captured, teardown still ran.
- Environment note: local Buildx plugin is 0.11.2 (`docker compose build` needs ≥ 0.17), so `start.sh` / `e2e.sh` build images with `docker build` and run `docker compose up --no-build`. `compose.yaml` also carries `build:` for environments with a newer Buildx. (research.md D1/D5 note.)
