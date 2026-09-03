# Research — EN002 Containerized E2E Testing Foundation (Phase 0)

Format per decision: **Decision / Rationale / Alternatives considered**. The enabler already
resolved the big choices in **OD-1…OD-9**; this document turns them into concrete, buildable
decisions (D1–D14) and records the residual version/tag pins as **Open questions (OD-EN002-1…5)**.

All decisions honour: ADR-001 (single `core-service`), `technology-policy.md` (Playwright now
PREFERRED; OCI containers PREFERRED), and the "no product behaviour / no CI / no auth" scope of
EN002.

---

## D1 — Backend image: multi-stage, Temurin JRE runtime (OD-1)

**Decision**: `implementation/platform/backend/core-service/Dockerfile`, two stages:

1. **build** — `maven:3.9-eclipse-temurin-21`; `COPY pom.xml` then `mvn -q -o? dependency:go-offline`
   (cache layer), then `COPY src`, `mvn -q -DskipTests package`. Produces
   `target/core-service-0.0.1-SNAPSHOT.jar`.
2. **runtime** — `eclipse-temurin:21-jre-jammy`; install `curl` (`apt-get install -y
   --no-install-recommends curl && rm -rf /var/lib/apt/lists/*`); create a non-root user; `COPY
   --from=build /workspace/target/core-service-*.jar /app/app.jar`; `EXPOSE 8080`;
   `ENTRYPOINT ["java","-jar","/app/app.jar"]`.

A `.dockerignore` excludes `target/`, `.idea/`, `*.iml`, `*.log`.

**Rationale**: OD-1 mandates a multi-stage build with no build tooling in the runtime image. Temurin
21 matches the pinned project JDK. `curl` (~2 MB) gives Compose a reliable in-container health probe
against `/actuator/health` — the `jre-jammy` image ships neither `curl` nor `wget`. Non-root and a
thin runtime layer follow `technology-policy.md` container rules (reproducible, no secrets, minimal
packages).

**Alternatives considered**:
- *Spring Boot Maven plugin `build-image` / Paketo buildpacks* — produces an image without a
  Dockerfile, but adds a buildpack toolchain dependency and is less transparent than a ~15-line
  multi-stage Dockerfile; the enabler explicitly asks for a Dockerfile.
- *Layered-jar extraction* (`java -Djarmode=layertools extract`) for better rebuild caching — a
  valid optimisation; deferred as a later refinement (recorded here), not needed for correctness.
- *`21-jre-alpine`* (musl, ships BusyBox `wget`, ~30 MB smaller) — acceptable; kept as OD-EN002-2
  fallback. Jammy chosen as the low-risk default (glibc, widely exercised with Spring Boot).

---

## D2 — Frontend image: Angular build → nginx static + `/api` proxy (OD-2, OD-5)

**Decision**: `implementation/platform/frontend/web/Dockerfile`, two stages:

1. **build** — `node:22-alpine`; `COPY package*.json`, `npm ci`; `COPY .`, `npm run build`
   (Angular `production` config, the default). Output: `dist/web/browser/` (the `@angular/build:application`
   builder nests the client bundle under `browser/`).
2. **runtime** — `nginx:1.27-alpine`; `COPY --from=build /app/dist/web/browser /usr/share/nginx/html`;
   `COPY frontend/web/nginx.conf /etc/nginx/conf.d/default.conf`; `EXPOSE 80`.

`nginx.conf`:

```nginx
server {
  listen 80;
  server_name _;
  root /usr/share/nginx/html;

  location /api/ {
    proxy_pass http://backend:8080;
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }

  location / {
    try_files $uri $uri/ /index.html;   # SPA fallback for Angular client-side routes
  }
}
```

**Rationale**: OD-2 fixes nginx + reverse-proxy; OD-5 fixes the routing (`/` → static, `/api/*` →
`backend:8080`). The Angular code already issues **relative** `/api` requests
(`portfolio-api.service.ts` posts to `'/api/portfolios'`), so no source change is needed and no
Docker service DNS name leaks into the browser bundle. `try_files … /index.html` keeps deep links
(e.g. `/portfolios/new`) working. `proxy_pass http://backend:8080;` with **no** URI part preserves
the full `/api/...` path the backend expects.

**Alternatives considered**:
- *Runtime env-substitution of an API base URL* (`envsubst` on a config.js) — unnecessary because
  the proxy makes the API same-origin; also risks an env-specific hostname in the bundle (OD-5
  forbids).
- *`ng serve` in the container* — explicitly disallowed by the enabler (§7); dev server, not a
  production static server.
- *Caddy / static-web-server instead of nginx* — nginx is named by OD-2 and is the lowest-risk,
  best-documented choice.
- *nginx `resolver` + variable `proxy_pass`* to defer DNS resolution — not needed: `depends_on:
  backend: condition: service_healthy` guarantees `backend` is resolvable before nginx starts.

---

## D3 — Compose topology, health checks, dependency ordering (OD — enabler §8, §14)

**Decision**: `implementation/platform/infrastructure/local/compose.yaml` services:

| Service | Image / build | Ports (default) | Health check | depends_on |
|---|---|---|---|---|
| `postgres` | `postgres:16-alpine` | `${POSTGRES_PORT:-5432}:5432` | `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB` | — |
| `backend` | build `../../backend/core-service` | `8080:8080` | `curl -fsS http://localhost:8080/actuator/health/readiness` | `postgres: service_healthy` |
| `frontend` | build `../../frontend/web` | `4200:80` | `wget -q -O - http://localhost/ >/dev/null` (BusyBox `wget` in `nginx:alpine`) | `backend: service_healthy` |
| `e2e` | build `../../e2e` | — (none) | — | `frontend: service_healthy` |

- `e2e` carries `profiles: ["e2e"]` so a plain `docker compose up` never starts it.
- Backend env: `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB}`,
  `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (from `.env`).
- All health checks: `interval: 3s`, `timeout: 5s`, `retries: 20`, `start_period` tuned per service
  (backend ~40 s for JVM + Flyway).
- Top-level `name:` stays (`my-finai-manager-local`) so the default project name is stable;
  `e2e.sh` overrides it with `-p` (Compose precedence: `-p` > `COMPOSE_PROJECT_NAME` > `name:`).

**Rationale**: §14 requires explicit readiness (no primary reliance on fixed sleeps): `pg_isready`,
the Spring Boot health endpoint, an HTTP check for the frontend. `condition: service_healthy`
chaining gives correct start order (`postgres → backend → frontend → e2e`). Readiness endpoint
`/actuator/health/readiness` is more precise than `/health` for "ready to serve".

**Alternatives considered**:
- *`depends_on` without `condition`* — only waits for container start, not readiness; rejected.
- *A separate `wait-for-it`/`dockerize` entrypoint wrapper* — Compose native health conditions make
  it redundant here.
- *Backend health via a tiny Java/`jcmd` probe instead of installing `curl`* — more moving parts
  than one small package.

---

## D4 — Lifecycle scripts: `start.sh` / `stop.sh` become container-only (OD-9, enabler §10)

**Decision**:

`start.sh`:
1. preflight: `docker` present + daemon up; `docker compose` available; `.env` present;
   required host ports free (`5432`, `8080`, `4200`) — fail fast with a clear message + non-zero exit.
2. `docker compose --env-file .env -f compose.yaml up -d postgres backend frontend`
   (**no `--build`** — OD-9; images must already exist, else a clear "run with BUILD=1 / `docker
   compose build`" message).
3. poll `docker compose ps --format '{{.Name}} {{.Health}}'` until `postgres`, `backend`,
   `frontend` are `healthy`, bounded (~120 s total); on timeout report which service failed and
   `docker compose logs <svc>` hint, exit non-zero.
4. print frontend (`http://localhost:4200`), backend (`http://localhost:8080`), health URLs.

`start.sh` honours `BUILD=1` (or a `--build` flag) to run `docker compose build` first — the
deliberate, explicit rebuild path.

`stop.sh`:
1. `docker compose -f compose.yaml down` (keeps the named postgres volume so dev data persists).
2. safe no-op when nothing runs (`down` on a stopped project exits 0).
3. **no** `.run/` PID handling — remove that logic; delete `.run/` if present.

**Rationale**: enabler §10 — the complete platform starts/stops through Compose, no host Spring
Boot/Angular, no PID files, fail-fast on predictable errors, bounded readiness. OD-9 — normal start
does not rebuild.

**Alternatives considered**:
- *`docker compose up` (foreground)* — blocks the terminal; `-d` + explicit health polling gives a
  clean "platform is ready" UX and a usable exit code.
- *Keeping a thin PID fallback* — the enabler explicitly forbids relying on PID files after EN002.
- *`docker compose up --wait`* (Compose 2.17+ waits for health) — attractive and may be used, but
  explicit polling gives friendlier per-service failure messages; `--wait` is an acceptable
  simplification the implementer may choose.

---

## D5 — `e2e.sh`: isolated, disposable E2E environment (OD-6, OD-7)

**Decision**: `implementation/platform/e2e.sh`:

```text
PROJECT=finai-e2e
FILES="-f infrastructure/local/compose.yaml -f infrastructure/local/compose.e2e.yaml"

1. docker compose -p $PROJECT $FILES --profile e2e build
2. docker compose -p $PROJECT $FILES up -d postgres backend frontend
3. poll health (same helper as start.sh) — bounded; on failure: dump logs, down -v, exit 1
4. docker compose -p $PROJECT $FILES run --rm e2e   # runs `npx playwright test`; forwards args ("$@")
   → capture EXIT
5. docker compose -p $PROJECT $FILES --profile e2e down -v      # ALWAYS (trap), removes disposable volume
6. exit $EXIT
```

`compose.e2e.yaml` override:
- `postgres.volumes: []` → an **anonymous/disposable** volume (removed by `down -v`), NOT the
  persistent `my-finai-manager-local` named volume.
- shifted host ports (`15432` / `18080` / `14200`) so `e2e.sh` runs even while the normal platform
  is up (OD-EN002-4).
- `e2e.environment.E2E_BASE_URL: http://frontend` (Compose-internal DNS — the Playwright *browser*
  runs inside the `e2e` container and resolves service names fine).
- `e2e.volumes: - ./e2e/test-results:/work/test-results` so diagnostics land on the host.

**Rationale**: OD-6 — isolated Compose project (`-p finai-e2e`) + disposable volumes ⇒ normal dev
data is untouchable. OD-7 — `e2e.sh` builds, starts, waits, runs Playwright, propagates the exit
code, tears down with volumes. `docker compose run --rm` returns the command's exit code directly
(cleaner than parsing `up` results). A `trap` guarantees teardown even on Ctrl-C / failure.

**Alternatives considered**:
- *`compose.e2e.yaml` using `!override []` to drop published ports entirely* — cleaner surface, but
  `!reset`/`!override` tags are newer Compose syntax; shifting ports is more portable and lets a
  developer inspect the running E2E stack. Either is acceptable (OD-EN002-4).
- *Same project name as the default platform, relying only on a separate test DB name* — risks
  Compose reusing/removing the shared volume; rejected per OD-6.
- *`e2e.sh` requiring the platform to already be running* (allowed by §11) — rejected: a
  self-contained isolated run is more reliable and repeatable and can't clobber dev data.

---

## D6 — Playwright project & container (OD-3, OD-4, enabler §12–§13)

**Decision**:
- `implementation/platform/e2e/` npm project. `package.json` depends on `@playwright/test` pinned
  to `<X.Y.Z>` (OD-EN002-1).
- `e2e/Dockerfile`: `FROM mcr.microsoft.com/playwright:v<X.Y.Z>-noble`; `WORKDIR /work`;
  `COPY package*.json`; `npm ci`; `COPY . .`; default `CMD ["npx","playwright","test"]`. Browsers
  are already in the base image — **no** `npx playwright install` needed, and the developer needs
  no local browsers (VC-007).
- `playwright.config.ts`:
  - `testDir: './tests'`, `outputDir: './test-results'`
  - `use: { baseURL: process.env.E2E_BASE_URL ?? 'http://frontend', trace: 'retain-on-failure',
    screenshot: 'only-on-failure', video: 'off' }`
  - `projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]`
  - `reporter: [['list'], ['html', { open: 'never', outputFolder: 'test-results/html' }]]`
  - `fullyParallel: true`, `retries: 0`, `forbidOnly: true`, **no `webServer`** (the app is already
    running — `e2e.sh`/Compose own the lifecycle).
- `e2e/package.json` scripts: `"test": "playwright test"`, `"test:one": "playwright test -g"`.

**Rationale**: OD-3 (official pinned image), OD-4 (Chromium only), §12 (Node + Playwright + browsers
+ tests + config + support all in the image), §13 (tests target the running frontend; no `webServer`
management). `trace: retain-on-failure` + `screenshot: only-on-failure` + `video: off` == OD-8.

**Alternatives considered**:
- *Bind-mounting `tests/` into a stock Playwright image instead of building* — the enabler says the
  image *includes* test sources; `e2e.sh` rebuilds each run (OD-EN002-5) so edits are picked up
  anyway. A bind mount can be documented as an optional fast-iteration override.
- *`retries: 1`* to paper over flakiness — rejected initially; EN002 optimises for a *reliable*
  smoke test. Revisit only if real flakiness appears.
- *`@playwright/test` version drifting from the image tag* — forbidden; they are pinned together
  (a check can compare them).

---

## D7 — Platform smoke test (enabler §17, VC-009)

**Decision**: `e2e/tests/platform-smoke.spec.ts`, one test:

```text
test('the platform shell loads and is interactive', async ({ page }) => {
  const response = await page.goto('/');
  expect(response?.ok()).toBeTruthy();
  await expect(page.locator('app-shell')).toBeVisible();
  await expect(page.locator('app-sidebar')).toBeVisible();
  // a known nav entry exists and is clickable (proves routing + render, not business behaviour)
  const nav = page.getByRole('link', { name: 'Portfolios' });
  await expect(nav).toBeVisible();
  // no uncaught page error / failed document request blocked interaction
  // (page 'pageerror' listener asserted empty)
});
```

It asserts: the frontend responds, the app shell + sidebar render, a nav link is present and
visible, and no page-level runtime error fired. It does **not** open the Create Portfolio form,
submit anything, or touch the database as an assertion target (FR-023, VC-015).

**Rationale**: §17 — prove `Playwright → frontend → containerized platform` without any
Portfolio-specific behaviour. Checking a rendered nav link (added by FD001) is still
platform-level: it proves Angular booted, routing config loaded, and the shell composed — not that
portfolios work.

**Alternatives considered**:
- *Asserting only `<title>`* — too weak; wouldn't catch a blank-shell / failed-bootstrap regression.
- *Navigating to `/portfolios/new` and asserting the form renders* — borders on feature testing;
  left for the FD001 E2E spec (follow-up, not EN002). The smoke test may `goto('/portfolios/new')`
  and assert the route resolves *without* interacting, if the team wants slightly more coverage —
  noted as an allowed, still-non-business extension.

---

## D8 — E2E test data & isolation strategy (OD-6, enabler §15–§16)

**Decision**: **disposable database volume per E2E run** (D5) + **unique generated values** in
tests (`support/data.ts`: `portfolioName()` → `"E2E <ulid>"`). Flyway runs fresh on the disposable
DB each run, including the `V2` default-investor seed. **No** database-reset endpoint, **no**
test-only application behaviour.

**Rationale**: §15 asks for the *simplest reliable* strategy and forbids production-only behaviour.
A throwaway DB volume makes every run start clean; unique values make individual tests independent
even within a run. §16 is satisfied: the normal platform's named volume is never referenced by the
E2E project.

**Alternatives considered**:
- *`TRUNCATE`/SQL fixtures between tests* — more machinery than a disposable DB needs; keep as a
  future option if a feature suite needs partial resets.
- *A dedicated `/test-support/reset` endpoint* — explicitly gated ("only if explicitly approved");
  not approved, not needed.
- *Sharing the dev DB with a distinct schema* — violates OD-6 (risk to dev data).

---

## D9 — Diagnostics & artifact retention (OD-8, enabler §20, VC-013)

**Decision**: screenshots **on failure**, Playwright trace **retain-on-failure**, video **off**,
HTML report generated (not auto-opened). All under `implementation/platform/e2e/test-results/`,
bind-mounted to the host by `e2e.sh`. `.gitignore` gets `implementation/platform/e2e/test-results/`
and `implementation/platform/e2e/node_modules/`. `e2e.sh` may `docker compose logs` the app
services into `test-results/containers/` **only on failure**. Playwright config sets no secret
env into traces; `.env` values are DB-only synthetic creds.

**Rationale**: OD-8 verbatim. Passing runs keep essentially nothing (list reporter + an HTML report
with no trace/video). Failing runs give screenshot + trace + optional container logs — enough to
diagnose an integration break. Nothing committed (VC-013).

**Alternatives considered**:
- *`trace: 'on'` / `video: 'on'`* — heavy artifacts on every run; rejected by OD-8.
- *Uploading artifacts somewhere* — that's CI territory, out of scope.

---

## D10 — Container networking & the browser→backend path (OD-5, enabler §9)

**Decision**: one Compose bridge network (default). Server-to-server traffic uses service names
(`backend` reaches `postgres:5432`; nginx proxies to `backend:8080`; the `e2e` container reaches
`http://frontend`). **Browser** traffic (inside the `e2e` container's Chromium, and a developer's
host browser) only ever hits the **frontend origin** — `/api` is same-origin and nginx forwards it.
No Angular code references a Docker service name.

**Rationale**: §9 — browser code can't resolve Compose DNS; the reverse proxy is the sanctioned
route (OD-5). The `e2e` container's browser *can* resolve `frontend` because it runs inside the
Compose network, so `baseURL: http://frontend` is correct and needs no host port.

**Alternatives considered**:
- *Playwright hitting `http://localhost:4200` from inside the container* — wrong; `localhost` is the
  e2e container. Service DNS is right here.
- *Exposing the backend to the browser directly on a second origin + CORS* — more surface, needs
  CORS config, and OD-5 says the browser accesses the frontend only.

---

## D11 — What EN002 does **not** containerize

**Decision**: the existing **backend build/test** (`mvn verify`, Testcontainers ITs, ArchUnit,
contract test) and **frontend unit tests** (`ng test`) continue to run on the host toolchain. EN002
does not move them into containers.

**Rationale**: enabler §5 — "Local build tools may still be used for development tasks such as
compilation or unit testing." Only the **runtime** and **E2E** are containerized. Testcontainers
already gives those tests disposable infra; wrapping them in yet another container adds nothing.

**Alternatives considered**: a "build" container image for hermetic CI — that's a future CI enabler,
explicitly out of scope.

---

## D12 — `.env` / configuration model after containerization

**Decision**: `.env.example` keeps `POSTGRES_DB/USER/PASSWORD/PORT` and adds/keeps the backend
datasource pointing at the **container** host: `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/finai`
(used by the `backend` service; the host-facing `localhost` form is only relevant to the removed
host-process path). Image tags / build args (Playwright version, base-image tags) live as Compose
`build.args` / a small `versions` section, not as secrets. No secret ever enters these files.

**Rationale**: enabler §6/§7 (config via env, no embedded creds), FR-037, `technology-policy.md`
secrets rule.

**Alternatives considered**: a `.env.e2e` — unnecessary; `compose.e2e.yaml` carries the few E2E
overrides and can inherit the same `.env`.

---

## D13 — Documentation deliverables (VC-014, FR-031)

**Decision**: update `implementation/platform/README.md` (containerized start/stop, prerequisites =
Docker only for *running*), add `implementation/platform/e2e/README.md` (run all tests, run one
test with `-g`, debug a failure with the trace viewer, add a `FD00N-<slug>.spec.ts`), and a short
"Testing layers" note aligning Unit/Integration/Contract/Architecture/E2E (FR-032). The
`testing-strategy.md` / `definition-of-done.md` / `project-verify` touch-ups (FR-032/FR-033) are a
**separate maintainer-approved change** (see plan "Architecture Impact & ADR").

**Rationale**: VC-014 lists exactly these six documentation needs.

---

## D13a — Image build mechanism (environment note)

**Decision**: `start.sh` and `e2e.sh` build images with **`docker build -t <tag> <context>`**, then
run **`docker compose up --no-build`** / **`docker compose run`** against those tags. `compose.yaml`
declares **both** `image:` (stable local tag) **and** `build:` (context) for each buildable
service.

**Rationale**: `docker compose build` requires Buildx ≥ 0.17; the local environment ships Buildx
0.11.2, which errors on `docker compose build` but handles plain `docker build` fine. Building with
`docker build` and orchestrating everything else (up / down / health / networking / `run`) through
Compose keeps the design intent ("runtime orchestration delegated to Docker Compose") while working
on the current toolchain. Where a newer Buildx is installed, `docker compose build` also works
because the `build:` stanzas are present.

**Alternatives considered**: upgrading Buildx on the developer machine (out of scope for this
enabler; not something the scripts should force); pinning `platform:` and using `docker buildx
bake` (more machinery than three images need).

**`DOCKER_DEFAULT_PLATFORM`**: `start.sh` and `e2e.sh` `unset DOCKER_DEFAULT_PLATFORM` at the top.
A stray `linux/amd64` (common leftover on Apple-silicon machines) otherwise makes Compose demand
an image variant that was never built ("image … does not provide the specified platform") and
forces slow QEMU emulation. The local platform is always built and run for the host architecture;
`compose.yaml` pins no `platform:`. The built services also carry `pull_policy: never` so Compose
never tries a registry pull for the locally-built `finai/*:local` tags (removing the harmless but
noisy "pull access denied" warnings).

**Wrong-arch pulled base image**: even with the `unset`, a `postgres:16-alpine` that was *already*
pulled as `linux/amd64` stays cached and Compose then warns `The requested image's platform
(linux/amd64) does not match the detected host platform` and runs it emulated. `start.sh` /
`e2e.sh` include a small `pull_native()` step: it reads the postgres tag from `compose.yaml`,
compares `docker image inspect --format {{.Architecture}}` (which the colima/containerd image store
reports as **empty** for an emulated image) against `docker version --format {{.Server.Arch}}`, and
on any mismatch does `docker rmi -f` + `docker pull --platform linux/<host-arch>`. It runs only on
mismatch (no network on the happy path) and is best-effort (a network failure falls back to the
cached image).

---

## D14 — Verification approach (no CI)

**Decision**: validation is by running `start.sh`, `stop.sh`, `e2e.sh`, and the `quickstart.md`
scenarios locally, plus inspection for VC-015 (no product scope) and VC-013 (no secrets in
artifacts). `e2e.sh` exit code is the machine-checkable signal (VC-011).

**Rationale**: CI/CD is out of scope (enabler §3, FR-036); the EN001 precedent validates via
`quickstart.md` + local commands.

---

## Open questions (reserved for a maintainer — safe defaults recorded)

| ID | Question | Recommended default | Impact if changed later |
|----|----------|---------------------|--------------------------|
| **OD-EN002-1** | Exact Playwright version + `mcr.microsoft.com/playwright:v<X.Y.Z>-noble` tag | Latest stable at implementation (≥ 1.48); pinned identically in `e2e/package.json` and the image tag | Bump tag + dependency together; no design change |
| **OD-EN002-2** | Backend runtime base image | `eclipse-temurin:21-jre-jammy` + `curl` | `21-jre-alpine` (uses BusyBox `wget` for the probe) — swap the probe tool |
| **OD-EN002-3** | Frontend build Node tag / nginx tag | `node:22-alpine` / `nginx:1.27-alpine` (pinned) | Any Node ≥ 22.12 or ≥ 20.19; any current nginx |
| **OD-EN002-4** | E2E isolated-env host ports | Shift to `15432/18080/14200` in `compose.e2e.yaml` | Or drop published ports via `!override []` — behaviourally equivalent |
| **OD-EN002-5** | Does `e2e.sh` rebuild images every run | Yes (`build` step in `e2e.sh`) | Skip when unchanged for speed; correctness unaffected |

None of these affect enabler intent, verification criteria, or architecture. They are recorded so
implementation can proceed on the defaults if the maintainer does not object.
