---
description: "Task list for EN002 — Establish Containerized End-to-End Testing Foundation"
---

# Tasks: Establish Containerized End-to-End Testing Foundation (EN002)

**Input**: Design documents from `/specs/EN002-containerized-e2e-testing-foundation/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md` (D1–D14 + OD-EN002-1…5), `data-model.md`, `contracts/container-interfaces.md`, `quickstart.md`

**Governance**: `.specify/memory/constitution.md` v1.0.0 (principles I–VIII); `product/architecture/technology-policy.md` (Playwright = PREFERRED for browser E2E); `product/engineering/{testing-strategy,development-rules,definition-of-done}.md`

**Tests**: This is a **Technical Enabler**. There is no deterministic domain logic to TDD. The one
test artifact EN002 delivers is the **Playwright platform smoke test** (`FR-022`) — it *is* a
deliverable, not a test-of-code. Validation is by running `start.sh` / `stop.sh` / `e2e.sh` and the
`quickstart.md` scenarios (no CI — `FR-036`). The existing backend/frontend test suites are
**unchanged** and continue to run on the host toolchain (`research.md` D11).

> **How EN002 is sliced.** **Foundational** builds the three container images + the Compose
> topology (nothing works without them). **US1** (P1) makes `start.sh` / `stop.sh` container-only.
> **US2** (P1) delivers `e2e.sh` + the Playwright project + the smoke test. **US3** (P2) makes E2E
> runs isolated, repeatable, and safe for dev data. **US4** (P3) adds failure diagnostics and the
> documented workflow/conventions. **No application source changes** anywhere.

**Path conventions**
- Backend: `implementation/platform/backend/core-service/`
- Frontend: `implementation/platform/frontend/web/`
- Local infra: `implementation/platform/infrastructure/local/`
- E2E: `implementation/platform/e2e/`
- Lifecycle scripts: `implementation/platform/{start.sh,stop.sh,e2e.sh}`

**Version pins**: use the `research.md` recommended defaults (Playwright ≥ 1.48 pinned, `eclipse-temurin:21-jre-jammy`, `node:22-alpine`, `nginx:1.27-alpine`, `postgres:16-alpine`) unless a maintainer specifies otherwise (OD-EN002-1…5). Pin every tag; never `latest`.

---

## Phase 1: Setup

**Purpose**: Scaffolding and repo hygiene that everything else builds on.

- [X] T001 [P] Create the E2E project skeleton: `implementation/platform/e2e/` with subdirectories `tests/` and `support/`, and an empty placeholder `implementation/platform/e2e/README.md` (filled in T028).
- [X] T002 [P] Add `implementation/platform/e2e/package.json` (`"name": "my-finai-manager-e2e"`, `"private": true`, `devDependency` `@playwright/test` pinned per OD-EN002-1, scripts `"test": "playwright test"` and `"test:one": "playwright test -g"`) and `implementation/platform/e2e/tsconfig.json` (strict, `"types": ["@playwright/test"]`, target ES2022).
- [X] T003 [P] Run `npm install` in `implementation/platform/e2e/` to produce `implementation/platform/e2e/package-lock.json` (locks the exact Playwright version that MUST equal the base-image tag in T018).
- [X] T004 [P] Add `.dockerignore` files: `implementation/platform/backend/core-service/.dockerignore` (`target/`, `.idea/`, `*.iml`, `*.log`, `.git`) and `implementation/platform/frontend/web/.dockerignore` (`node_modules/`, `dist/`, `.angular/`, `.git`, `*.log`).
- [X] T005 Update the root `.gitignore`: add `implementation/platform/e2e/test-results/` and `implementation/platform/e2e/node_modules/`; remove the now-obsolete `implementation/platform/.run/` line (EN002 drops PID files — `research.md` D4).
- [X] T006 [P] Update `implementation/platform/infrastructure/local/.env.example` — point the backend datasource at the container host: `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB}` (and matching `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`); keep all values synthetic; add a comment that image/version tags are build configuration, never secrets (`data-model.md` §3, FR-037).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The three container images and the Compose topology. **No user story can proceed until this phase is done.**

- [X] T007 Create `implementation/platform/backend/core-service/Dockerfile` — multi-stage (D1, OD-1): build stage `maven:3.9-eclipse-temurin-21` (cache `pom.xml` deps, then `mvn -q -DskipTests package`); runtime stage `eclipse-temurin:21-jre-jammy` + `curl` (`apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*`), non-root user, `COPY --from=build .../core-service-*.jar /app/app.jar`, `EXPOSE 8080`, `ENTRYPOINT ["java","-jar","/app/app.jar"]`. Verify: `docker build` succeeds and `docker history` shows **no Maven** in the final image.
- [X] T008 [P] Create `implementation/platform/frontend/web/nginx.conf` (D2, OD-2, OD-5) — `listen 80`; `root /usr/share/nginx/html`; `location /api/ { proxy_pass http://backend:8080; proxy_set_header Host $host; proxy_set_header X-Real-IP $remote_addr; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; proxy_set_header X-Forwarded-Proto $scheme; }`; `location / { try_files $uri $uri/ /index.html; }` (SPA fallback).
- [X] T009 Create `implementation/platform/frontend/web/Dockerfile` — multi-stage (D2): build stage `node:22-alpine` (`npm ci`, `npm run build` — the default `production` config); runtime stage `nginx:1.27-alpine`, `COPY --from=build /app/dist/web/browser /usr/share/nginx/html`, `COPY nginx.conf /etc/nginx/conf.d/default.conf`, `EXPOSE 80`. Verify: `docker run` serves `/` and a deep link (`/portfolios/new`) both return the shell HTML. Depends on T008.
- [X] T010 Expand `implementation/platform/infrastructure/local/compose.yaml` (D3, `data-model.md` §2) — keep top-level `name: my-finai-manager-local`; `postgres` keeps the persistent named volume `postgres-data` and a `pg_isready` healthcheck; add `backend` (build `../../backend/core-service`, env `SPRING_DATASOURCE_*` → `postgres:5432`, `depends_on: { postgres: { condition: service_healthy } }`, healthcheck `curl -fsS http://localhost:8080/actuator/health/readiness`, `ports: ["8080:8080"]`, `start_period` ~40s); add `frontend` (build `../../frontend/web`, `depends_on: { backend: { condition: service_healthy } }`, healthcheck `wget -q -O - http://localhost/ >/dev/null`, `ports: ["4200:80"]`). All healthchecks bounded (`interval: 3s`, `timeout: 5s`, `retries: 20`). Depends on T007, T009.

**Checkpoint**: `docker compose --env-file infrastructure/local/.env -f infrastructure/local/compose.yaml up -d` → `postgres` + `backend` + `frontend` all reach `healthy`; `curl http://localhost:8080/actuator/health` → `UP` incl. `db`; `curl http://localhost:4200/` → app shell; `curl -X POST http://localhost:4200/api/portfolios -H 'Content-Type: application/json' -H 'Idempotency-Key: fnd-1' -d '{"name":"F","positions":[{"ticker":"ASML","market":"XAMS","quantity":"1","currency":"EUR"}]}'` → `201` (browser→nginx→backend→postgres path works).

---

## Phase 3: US1 — The whole platform runs as containers, one-command start/stop (Priority: P1) 🎯 MVP (part 1)

**Goal**: `./start.sh` brings `postgres` + `backend` + `frontend` up as containers only; `./stop.sh` tears the platform down; no host Spring Boot / Angular process; no PID files.

**Independent Test**: quickstart §B + §C — on a clean machine with only Docker, `./start.sh` reaches all-healthy and prints URLs; `ps aux` shows no `spring-boot:run` / `ng serve`; `./stop.sh` (twice) tears down safely and the `postgres-data` volume persists. Covers VC-001…VC-006.

- [X] T011 [US1] Rewrite `implementation/platform/start.sh` (FR-011, FR-013, FR-014, D4): preflight (docker daemon reachable; `docker compose` available; `infrastructure/local/.env` present; host ports `5432`/`8080`/`4200` free) with clear non-technical messages + non-zero exit on failure; `docker compose --env-file infrastructure/local/.env -f infrastructure/local/compose.yaml up -d postgres backend frontend` (**no `--build`**); bounded health-poll loop over `docker compose ps` until the 3 services are `healthy` (~120s cap) with a per-service failure message + `docker compose logs <svc>` hint; print frontend/backend/health URLs; support `BUILD=1` (or `--build`) to run `docker compose build` first (the deliberate rebuild path, OD-9). Must **not** run `mvn spring-boot:run` or `npm start`.
- [X] T012 [US1] Rewrite `implementation/platform/stop.sh` (FR-012, D4): `docker compose -f infrastructure/local/compose.yaml down` (keeps `postgres-data`); safe successful no-op when nothing is running; **delete all `.run/` PID-file logic**; `rmdir implementation/platform/.run 2>/dev/null || true`.
- [X] T013 [US1] Sweep for residual host-process assumptions: confirm no script, README snippet, or doc under `implementation/platform/` instructs starting the backend/frontend as host processes; remove `implementation/platform/.run/` from the working tree if present (FR-012, FR-015).
- [X] T014 [US1] Run quickstart §A (`BUILD=1 ./start.sh` builds all images; `docker history` shows no Maven/Node in runtime layers), §B (containerized start: 3 healthy, URLs printed, no host app process, health `UP`, routing `201`), and §C (`./stop.sh` ×2 safe; volume persists). Record results as VC-001…VC-006 evidence in `quickstart.md`.

**Checkpoint**: the platform runs entirely as containers through `start.sh` / `stop.sh`.

---

## Phase 4: US2 — One-command containerized E2E smoke test (Priority: P1) 🎯 MVP (part 2)

**Goal**: `./e2e.sh` runs the Playwright smoke test (in its own container, Chromium, no host browser) against the running containerized frontend and returns Playwright's exit code.

**Independent Test**: quickstart §D + §E — `./e2e.sh` builds + starts an isolated stack, waits for readiness, runs `platform-smoke.spec.ts` (passes), exits 0; breaking the routing makes it exit non-zero while still tearing down. Covers VC-007…VC-009, VC-011, VC-012.

- [X] T015 [P] [US2] Add `implementation/platform/e2e/playwright.config.ts` (D6, OD-3, OD-4, OD-8): `testDir: './tests'`, `outputDir: './test-results'`, `use.baseURL: process.env.E2E_BASE_URL ?? 'http://frontend'`, `use.trace: 'retain-on-failure'`, `use.screenshot: 'only-on-failure'`, `use.video: 'off'`, `projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]`, `reporter: [['list'], ['html', { open: 'never', outputFolder: 'test-results/html' }]]`, `retries: 0`, `forbidOnly: true`, **no `webServer`**.
- [X] T016 [P] [US2] Add `implementation/platform/e2e/tests/platform-smoke.spec.ts` (D7, FR-022, FR-023): attach a `page.on('pageerror')` collector; `const res = await page.goto('/')`; assert `res?.ok()`; assert `app-shell` and `app-sidebar` are visible; assert the `Portfolios` nav link is visible; assert the pageerror collector is empty. **No** form interaction, **no** DB assertion, **no** Portfolio business behaviour.
- [X] T017 [P] [US2] Add `implementation/platform/e2e/support/readiness.ts` — exported `waitForFrontend(baseURL, timeoutMs)` that polls for HTTP 200 with a bounded timeout; usable as belt-and-braces (Compose health is the primary gate — FR-021 secondary support only).
- [X] T018 [US2] Add `implementation/platform/e2e/Dockerfile` (D6, OD-3): `FROM mcr.microsoft.com/playwright:v<pinned>-noble` (tag **must equal** the `@playwright/test` version from T003), `WORKDIR /work`, `COPY package*.json ./`, `RUN npm ci`, `COPY . .`, `CMD ["npx","playwright","test"]`. No `playwright install` (browsers are in the base image). Depends on T015–T017.
- [X] T019 [US2] Add the `e2e` service to `implementation/platform/infrastructure/local/compose.yaml` (enabler §8): `build: ../../e2e`, `profiles: ["e2e"]`, `depends_on: { frontend: { condition: service_healthy } }`, `environment: { E2E_BASE_URL: "http://frontend" }`, `volumes: ["../../e2e/test-results:/work/test-results"]`, **no published ports**. Depends on T018, T010.
- [X] T020 [US2] Create `implementation/platform/e2e.sh` (FR-020, OD-7, D5): project name `-p finai-e2e`; compose files `-f infrastructure/local/compose.yaml -f infrastructure/local/compose.e2e.yaml`; steps: (1) `docker compose … --profile e2e build`; (2) `docker compose … up -d postgres backend frontend`; (3) bounded health poll (reuse the loop from `start.sh`); (4) `docker compose … run --rm e2e "$@"` and capture the exit code; (5) teardown; (6) `exit` with the captured code. Must delegate to Compose and **not** start host processes. Also create a minimal `implementation/platform/infrastructure/local/compose.e2e.yaml` (hardened in T022).
- [X] T021 [US2] Run quickstart §D (`./e2e.sh` → isolated `finai-e2e` project, health wait, `platform-smoke.spec.ts` passes in Chromium, exit `0`, no host browser required) and §E (break the `/api` proxy → `./e2e.sh` exits non-zero, env still torn down). Record as VC-007…VC-009, VC-011, VC-012 evidence.

**Checkpoint**: `./e2e.sh` runs the browser smoke test against the containerized stack with a correct exit code. **MVP complete** (containerized platform + working E2E harness).

---

## Phase 5: US3 — E2E runs are isolated, repeatable, and never destroy dev data (Priority: P2)

**Goal**: `./e2e.sh` uses an isolated Compose project with disposable volumes and shifted ports; repeated runs are clean; the normal platform's data is never touched.

**Independent Test**: quickstart §F + §G — three consecutive `./e2e.sh` runs all pass with no manual cleanup; creating dev data in the running default platform and then running `./e2e.sh` leaves that data and its volume unchanged. Covers VC-010.

- [X] T022 [US3] Expand `implementation/platform/infrastructure/local/compose.e2e.yaml` (D5, OD-6, OD-EN002-4): override `postgres` to use a **disposable/anonymous** volume (never the named `postgres-data`); shift published host ports to `15432` / `18080` / `14200` so `./e2e.sh` runs while the default platform is also up.
- [X] T023 [US3] Harden `implementation/platform/e2e.sh` teardown (OD-7, VC-010): `trap 'docker compose -p finai-e2e … --profile e2e down -v' EXIT` so the isolated environment **and its disposable volumes** are always removed (pass, fail, or Ctrl-C); assert in a comment + code that the default `postgres-data` volume is never referenced by the `finai-e2e` project.
- [X] T024 [P] [US3] Add `implementation/platform/e2e/support/data.ts` (D8, FR-026, FR-027): `uniquePortfolioName()` and `uniqueIdempotencyKey()` returning collision-free synthetic values (e.g. `E2E <timestamp>-<rand>`); documented as the pattern for future feature specs — **no real portfolio data, no test-only backend endpoint**.
- [X] T025 [US3] Run quickstart §F (`./e2e.sh && ./e2e.sh && ./e2e.sh` → all pass, no cleanup) and §G (create a portfolio via `http://localhost:4200/api/portfolios` on the default platform, run `./e2e.sh`, verify the row count + `postgres-data` volume are unchanged). Record as VC-010 evidence.

**Checkpoint**: E2E is isolated, repeatable, and safe for developer data.

---

## Phase 6: US4 — Failing E2E tests are diagnosable and the workflow is documented (Priority: P3)

**Goal**: a failed E2E run yields a screenshot + trace (+ optional container logs); passing runs keep almost nothing; the repository documents build/start/stop, run all/one, debug, and add-a-feature-test.

**Independent Test**: quickstart §H + §I — force a failure and confirm a screenshot, a `trace.zip`, and an HTML report appear under `e2e/test-results/` (git-ignored, secret-free), while a passing run keeps only the HTML report; follow the docs to run all/one test, debug, and locate where an `FD00N-*.spec.ts` goes. Covers VC-013, VC-014.

- [X] T026 [US4] Harden the `implementation/platform/e2e.sh` failure path (FR-028, FR-030, VC-013): on a non-zero Playwright exit, before teardown, write `docker compose -p finai-e2e … logs --no-color postgres backend frontend` to `implementation/platform/e2e/test-results/containers/`; ensure no `.env` value is echoed to stdout/logs.
- [X] T027 [P] [US4] Confirm `.gitignore` (T005) ignores `implementation/platform/e2e/test-results/`; verify a **passing** `./e2e.sh` retains only `test-results/html/` (no `trace.zip`, no `*.webm`); verify a **failing** run's artifacts contain no credential (grep for the DB password) (FR-029, FR-030, OD-8).
- [X] T028 [P] [US4] Write `implementation/platform/e2e/README.md` (FR-024, FR-031, VC-014): run all tests (`./e2e.sh`), run one (`./e2e.sh -g "shell loads"`), debug a failure (`npx playwright show-trace implementation/platform/e2e/test-results/**/trace.zip`), and add a feature E2E test — naming `FD00N-<slug>.spec.ts`, location `implementation/platform/e2e/tests/`, rule "start from the frontend, do not call the backend API directly" (FR-021), use `support/data.ts` for synthetic values.
- [X] T029 [US4] Update `implementation/platform/README.md` (FR-031, FR-032): replace host-process run instructions with the containerized model (prerequisite to *run* = Docker only; `./start.sh` / `./stop.sh` / `./e2e.sh`); add a "Testing layers" note — Unit / Integration (Testcontainers) / Contract / Architecture / E2E (Playwright, containerized) are complementary and the E2E suite stays deliberately small and journey-focused.
- [X] T030 [US4] Run quickstart §H (forced failure → screenshot + `trace.zip` + HTML report under `e2e/test-results/`, git-ignored, no secrets; passing run keeps no trace/video) and §I (documentation review against the six VC-014 items). Record as VC-013, VC-014 evidence.

**Checkpoint**: a failing E2E test is diagnosable and the full workflow is documented.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T031 [P] **Prepare (do NOT commit without maintainer approval — constitution I)** the governance-doc alignment for FR-032/FR-033: a short addition to `product/engineering/testing-strategy.md` §5 (containerized Playwright E2E; suite stays small/critical-journeys) and to `product/engineering/definition-of-done.md` (a feature that introduces or materially changes a `frontend → backend → persistence` journey needs at least one passing E2E test; the closure verifier checks this), plus a note that `.claude/skills/project-verify` should check for a required E2E test. Deliver as a reviewable diff/summary; flag it explicitly in the PR.
- [X] T032 Run the **full** `quickstart.md` (§A–§J) from a clean state; complete the "Verification Criteria coverage" table with concrete evidence for VC-001…VC-015 (incl. VC-015: `git diff --stat` shows only infra/scripts/docs/specs — no domain class, no `openapi.yaml` operation, no migration, no auth, no `.github/`).
- [X] T033 [P] Assemble PR evidence per `product/engineering/definition-of-done.md` "Minimum Pull Request Evidence": what/why/trace to EN002 VC-001…VC-015, the container-interface change (`contracts/container-interfaces.md`), how it was validated (local `start.sh`/`stop.sh`/`e2e.sh` + quickstart — no CI), architecture boundaries (ADR-001 intact; no new ADR — optional ADR-003 noted), and the flagged governance-doc change (T031).
- [X] T034 Run the `product/engineering/definition-of-done.md` checklist against the change and record status in the PR: scope (VC-015), architecture (topology unchanged), tests (existing host suites green + new smoke test green), documentation current, secrets/hygiene (no credentials in Dockerfiles/Compose/nginx/artifacts; `.gitignore` covers `e2e/test-results/`), platform lifecycle (`start.sh`/`stop.sh`/`e2e.sh` functional).
- [X] T035 Regression check — run `mvn -q clean verify` in `implementation/platform/backend/core-service/` and `npm test -- --watch=false --browsers=ChromeHeadless` in `implementation/platform/frontend/web/` on the host to confirm EN002 changed **no** application behaviour (only Dockerfiles / Compose / nginx / scripts / docs).

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies — T001–T006 all `[P]` (different files).
- **Foundational (Phase 2)**: after Setup. Order: T007 ‖ T008 → T009 (needs T008) → T010 (needs T007 + T009). **Blocks all user stories.**
- **US1 (Phase 3, P1)**: after Foundational. T011 ‖ T012 → T013 → T014 (validation).
- **US2 (Phase 4, P1)**: after Foundational. T015 ‖ T016 ‖ T017 → T018 → T019 (needs T018 + T010) → T020 → T021 (validation). Independent of US1 (uses its own `-p finai-e2e` project), though in practice US1's rewritten scripts land first.
- **US3 (Phase 5, P2)**: after US2 (extends `compose.e2e.yaml` + `e2e.sh`). T022 ‖ T024 → T023 → T025.
- **US4 (Phase 6, P3)**: after US2 (diagnostics live in `e2e.sh` / `playwright.config.ts`); T028/T029 doc tasks can start once US1+US2 behaviour is settled. T026 → T027 ‖ T028 ‖ T029 → T030.
- **Polish (Phase 7)**: after the targeted stories. T032 needs everything; T031/T033/T034/T035 mostly `[P]`.

### Story dependency summary

```text
Setup → Foundational ┬→ US1 (P1)  ─┐
                     ├→ US2 (P1) → US3 (P2)  ─┼→ Polish
                     │            └→ US4 (P3) ─┘
                     └───────────────────────────
```

### Parallel opportunities

- Setup: T001, T002, T004, T006 in parallel (T003 after T002).
- Foundational: T007 and T008 in parallel.
- US2: T015, T016, T017 in parallel (all new files under `e2e/`).
- US3: T022 and T024 in parallel.
- US4: T027, T028, T029 in parallel after T026.
- After Foundational, US1 and US2 can be taken by different people (disjoint files apart from `compose.yaml`, which US1 does not touch and US2 edits once in T019).

---

## Parallel Example: Foundational + US2 file creation

```bash
# Foundational — two independent files:
Task: "T007 backend Dockerfile in implementation/platform/backend/core-service/Dockerfile"
Task: "T008 nginx.conf in implementation/platform/frontend/web/nginx.conf"

# US2 — three independent new files under implementation/platform/e2e/:
Task: "T015 playwright.config.ts"
Task: "T016 tests/platform-smoke.spec.ts"
Task: "T017 support/readiness.ts"
```

---

## Implementation Strategy

### MVP (both P1 stories)

1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 US1 → 4. Phase 4 US2 →
   **STOP & VALIDATE**: `./start.sh` runs the platform as containers; `./e2e.sh` runs the smoke
   test green and returns a correct exit code (quickstart §B, §C, §D, §E).

### Incremental delivery

1. Setup + Foundational → three images + a working Compose topology.
2. + US1 → containerized `start.sh` / `stop.sh` (no host processes).
3. + US2 → `e2e.sh` + Playwright smoke test (MVP — the enabler's core value).
4. + US3 → isolated, repeatable, dev-data-safe E2E.
5. + US4 → diagnostics + documented workflow + feature-test conventions.
6. Polish → full quickstart, PR evidence, DoD, flagged governance-doc prep, regression check.

### Constitution / DoD checkpoints

- No application source changes — Dockerfiles/Compose/nginx/scripts/docs only (T035 regression check).
- ADR-001 intact; no new ADR (optional ADR-003 noted in `plan.md`).
- Playwright is approved (`technology-policy.md`, PREFERRED); no other new technology.
- No secrets in any image/Compose/nginx/artifact; `e2e/test-results/` git-ignored (T005, T027).
- Governance-doc touch-ups (T031) are **prepared for maintainer approval**, not committed silently.

---

## Notes

- `[P]` = different files, no dependency on an incomplete task.
- `[US#]` labels map tasks to the spec's user stories for traceability.
- Pin every image tag (Playwright, Temurin, Node, nginx, Postgres) — never `latest` (OD-EN002-1…3).
- The Playwright `@playwright/test` version and the `mcr.microsoft.com/playwright:v…-noble` image tag MUST match (T003 ↔ T018).
- Commit after each task or logical group; keep the platform runnable via `start.sh` / `stop.sh` at every checkpoint.
- If any task seems to need: an FD001 (or other feature) E2E scenario, authentication for E2E, a CI workflow, multi-browser execution, a test-only backend endpoint, or a technology not already approved — **stop and surface it** (constitution IV; enabler §3).
- Total: 35 tasks — Setup 6, Foundational 4, US1 4, US2 7, US3 4, US4 5, Polish 5.
