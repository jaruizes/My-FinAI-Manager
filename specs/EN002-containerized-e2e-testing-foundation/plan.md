# Implementation Plan: Establish Containerized End-to-End Testing Foundation (EN002)

**Branch**: `EN002-containerized-e2e-testing-foundation` | **Date**: 2026-09-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/EN002-containerized-e2e-testing-foundation/spec.md`

**Authoritative enabler**: `product/definition/enablers/EN002-containerized-e2e-testing-foundation/EN002-containerized-e2e-testing-foundation.md` (Status: **Approved**, incl. resolved decisions OD-1…OD-9)
**Governing ADR**: `product/architecture/adrs/ADR-001-initial-backend-topology.md` (unchanged — `core-service` stays the single backend deployable)

## Summary

EN002 changes the **local runtime topology** established by EN001 (backend + frontend move from
host processes into containers) and adds a **containerized browser E2E testing foundation** with
Playwright.

Concretely it delivers:

- a multi-stage **backend Dockerfile** (`core-service` → Temurin 21 JRE runtime, no build tooling);
- a multi-stage **frontend Dockerfile** (Angular production build → **nginx** serving the static
  app and reverse-proxying `/api/*` to `backend:8080`);
- an expanded **`compose.yaml`** orchestrating `postgres` + `backend` + `frontend`, with an opt-in
  `e2e` service behind a Compose profile;
- rewritten **`start.sh` / `stop.sh`** that run the whole platform through Docker Compose (no host
  Spring Boot / `ng serve`, no PID files);
- a **Playwright project** under `implementation/platform/e2e/` (own Dockerfile from the official
  pinned Playwright image, Chromium-only, config targeting the already-running frontend);
- **`e2e.sh`** — the canonical E2E entry point that builds images, starts an **isolated** Compose
  project with **disposable volumes**, waits for readiness, runs Playwright, propagates the exit
  code, and tears the environment down (`down -v`);
- one **`platform-smoke.spec.ts`** proving `Playwright → frontend → containerized stack`;
- **documentation** (build/start/stop, run all/one E2E test, debug a failure, add a feature E2E
  test) and the naming/layering conventions for future feature E2E tests.

**No** product behavior, **no** authentication, **no** CI/CD, **no** multi-browser, **no** change
to the backend service topology (ADR-001 intact), **no** FD001 (or other feature) E2E scenario.

## Technical Context

**Languages**: Bash (lifecycle scripts, `e2e.sh`), Dockerfile, YAML (Compose), nginx conf,
TypeScript (Playwright tests). Backend Java / frontend TypeScript source is **unchanged** — EN002
only packages it.

**Primary Dependencies / Images**:
- Backend image: build stage `maven:3.9-eclipse-temurin-21` (matches pinned Maven/JDK); runtime
  stage `eclipse-temurin:21-jre-jammy` + `curl` (for the health probe). No Maven in the runtime layer.
- Frontend image: build stage `node:22-alpine` (Angular 20 needs Node ≥ 22.12 / ≥ 20.19 — the
  `22` alpine tag currently satisfies this); runtime stage `nginx:1.27-alpine`.
- E2E image: `mcr.microsoft.com/playwright:v<pinned>-noble` (official; version identical to
  `e2e/package.json`).
- Orchestration: Docker Compose (`docker compose` v2). Engine present in the environment is 29.x.

**Storage**: PostgreSQL (`postgres:16-alpine`, unchanged from EN001) as a Compose service. The
normal platform keeps a **persistent named volume**; each **E2E run uses a disposable volume in an
isolated Compose project** and never touches the normal volume.

**Testing**:
- Existing backend unit/integration/contract/ArchUnit tests and frontend Karma tests are
  **unchanged** and still run via the local build (`mvn verify`, `ng test`) — EN002 does not move
  them into containers.
- New: Playwright `platform-smoke.spec.ts` (Chromium) run by `e2e.sh` against the containerized
  platform.

**Target Platform**: Local developer workstation (macOS / Linux) with Docker. No cloud, no CI, no
image publishing.

**Project Type**: Cumulative executable platform under `implementation/platform/` — decoupled
Angular frontend + single Spring Boot backend + local infrastructure + an E2E runner, all
containerized.

**Performance Goals**: none beyond the spec's Success Criteria (containerized start healthy within
90 s; a new contributor reaches a first `start.sh` + first `e2e.sh` in < 20 min excluding image
pulls; smoke test stable across 3 consecutive runs).

**Constraints**:
- `core-service` remains the single backend deployable (ADR-001); containerizing it does not split it.
- Browser code reaches the backend **only** through the frontend's nginx `/api` proxy; the Angular
  source uses **relative** `/api` URLs and no Docker service DNS names (OD-2, OD-5).
- `start.sh` / `stop.sh` / `e2e.sh` are the only supported runtime entry points; no host
  application processes; no PID files after EN002.
- `start.sh` uses existing images and does **not** rebuild unconditionally (OD-9).
- Only approved technologies: Playwright (now PREFERRED in `technology-policy.md`), Docker Compose,
  nginx, Temurin JRE, official Playwright image.
- No secrets in Dockerfiles / Compose / E2E config / artifacts; config externalized; synthetic
  local credentials only.

**Unresolved decisions (reserved for a maintainer — see research.md "Open questions"; consistent
with the EN001 version-deferral precedent):**

| ID | Decision | Recommended default |
|----|----------|---------------------|
| OD-EN002-1 | Exact Playwright version + matching `mcr.microsoft.com/playwright:v<X.Y.Z>-noble` tag | Latest stable at implementation (≥ 1.48), pinned identically in `e2e/package.json` |
| OD-EN002-2 | Backend runtime base image tag | `eclipse-temurin:21-jre-jammy` (glibc, predictable) + `curl` for the probe; `21-jre-alpine` acceptable |
| OD-EN002-3 | Frontend build Node tag / runtime nginx tag | `node:22-alpine` / `nginx:1.27-alpine` (both pinned) |
| OD-EN002-4 | E2E isolated-environment host-port strategy | Shift published ports in a `compose.e2e.yaml` override (e.g. 15432/18080/14200) so `e2e.sh` never collides with a running default platform |
| OD-EN002-5 | Whether `e2e.sh` rebuilds images every run | Yes — `docker compose … build` (or `up --build`) each run; correctness over speed for E2E |

All are safe, reversible packaging details; recommended defaults are recorded so implementation is
unblocked if the maintainer does not object.

**Scale/Scope**: 3 Dockerfiles, 1 expanded Compose file + 1 E2E override, 3 lifecycle scripts
(2 rewritten + 1 new), 1 nginx config, 1 Playwright project with 1 smoke spec + support helpers,
documentation. No application source changes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.0.0**, principles I–VIII.

| # | Principle | Status | Evidence |
|---|---|---|---|
| I | Human-Governed Source of Truth (respect ADRs + technology-policy; don't silently modify `product/`) | PASS (1 flagged item) | ADR-001 honored — service topology unchanged. Playwright is already recorded in `product/architecture/technology-policy.md` (human-updated, status PREFERRED). The host→container **local runtime** change is fully specified and **human-approved in the enabler** (§29 checklist complete) with resolved decisions OD-1…OD-9 — no new ADR is required (see "Architecture Impact & ADR" below); a formal ADR-003 can be added on maintainer request. **Flagged**: FR-032/FR-033 imply small touch-ups to `product/engineering/testing-strategy.md` and `definition-of-done.md` (and the `project-verify` skill) — these will be **prepared** and require **maintainer approval before commit** (handled like FD001's `containers.md`), not applied silently. |
| II | Definitions/Enablers Are Authoritative Intent | PASS | Every plan element traces to EN002 `FR-001…FR-037` / `VC-001…VC-015`. Scope is the enabler minimum — only the platform smoke test; feature-specific E2E (incl. FD001) explicitly deferred. |
| III | Derived Artifacts & Repository Layout | PASS | Design artifacts under `specs/EN002-containerized-e2e-testing-foundation/`; all executable assets under `implementation/platform/` (`backend/`, `frontend/`, `e2e/`, `infrastructure/local/`, `*.sh`). No `apps/`/`services/`/`src/` root trees; no `.github/` CI tree. |
| IV | No Invention; Surface Material Ambiguity | PASS | OD-1…OD-9 (enabler) are authoritative and used as-is. The only open choices are exact image/version tags — surfaced as OD-EN002-1…5 for a maintainer with recommended defaults recorded, not silently fixed. |
| V | Technical Enablers Stay Technical | PASS | No investor user stories; US1–US4 are developer/operator workflows. No Portfolio/valuation/risk behavior. The platform stays coherent and executable via `start.sh` / `stop.sh` (now containerized) plus the new `e2e.sh`. |
| VI | Hexagonal Architecture & Deterministic Logic | PASS | Backend Java source is untouched — the Dockerfile only packages the existing artifact. No domain/application/adapter changes, no LLM. ArchUnit rules unaffected. |
| VII | Test-First for Deterministic Logic; Testcontainers by Default | PASS | No new deterministic domain logic ⇒ nothing to develop test-first. Existing Testcontainers integration tests are unchanged and still run via `mvn verify`. E2E is an additive, complementary layer (FR-032) and runs against a **real disposable containerized PostgreSQL** — consistent with the "real disposable infrastructure, never a mock" principle. |
| VIII | Contract-First External APIs | PASS | EN002 adds **no** external REST operation. The nginx `/api` reverse proxy forwards the existing contract (`implementation/platform/contracts/openapi/openapi.yaml`) unchanged; the frontend already uses relative `/api` URLs. Contract tests are unaffected. |

### Development-Workflow / Compliance gates

| Gate | Status | Notes |
|---|---|---|
| Plan includes a Constitution Check | PASS | This section. |
| Completion measured by `definition-of-done.md` | PASS | Carried into `/speckit-tasks`. DoD line 69 ("End-to-end tests cover critical journeys when warranted") — EN002 delivers the harness + smoke test; feature journeys stay per-Feature-Definition (FR-033). |
| ADR required for material architecture change | PASS (reasoned) | Backend **service topology** unchanged (ADR-001 intact). The **local runtime packaging** change (host→container) and the **Playwright adoption** are already human-approved (enabler §29; `technology-policy.md` update). No deployment/cloud/runtime-architecture change beyond local. A formal **ADR-003 (Containerized Local Runtime)** is *optional* and can be drafted on maintainer request. |
| Structured logging; no secrets; externalized config | PASS | Backend logging unchanged (ECS JSON). No credentials in any Dockerfile/Compose/nginx/E2E file; DB config via env; `.env.example` synthetic; E2E artifacts git-ignored and screened for secrets (FR-030). |
| No speculative infrastructure | PASS | Adds only nginx (frontend static server + `/api` proxy) and the Playwright runner — both mandated by the enabler. No registry, K8s, CI, or messaging. |

**Result: PASS.** One flagged governance-doc item (maintainer approval before commit, not a
violation). Complexity Tracking not required.

## Architecture Impact & ADR

- **ADR-001 (backend topology)**: unaffected — `core-service` remains one coarse-grained
  deployable; it is now *packaged* as a container, not *split*.
- **New ADR?** Not required. The runtime change is **local-only** (developer workstation), does not
  alter deployment/cloud architecture, and is **explicitly approved in the enabler** with its
  rationale and resolved decisions (OD-1…OD-9). Playwright adoption is recorded in
  `technology-policy.md`. If the maintainer wants the containerized-local-runtime decision captured
  as `ADR-003` for durability, it is a ~1-page ADR the assistant can draft on request (Status:
  Proposed → human approval), mirroring ADR-002 for FD001.
- **Governance docs** (`testing-strategy.md` §5, `definition-of-done.md`, `project-verify` skill):
  small alignment edits are in scope of FR-032/FR-033 but are **human-governed** — prepared as a
  separate reviewable change, not committed without maintainer sign-off.

## Project Structure

### Documentation (this feature)

```text
specs/EN002-containerized-e2e-testing-foundation/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output (D1…D14 + Open questions OD-EN002-1…5)
├── data-model.md        # Phase 1 output — configuration & container-interface model (no business entities)
├── quickstart.md        # Phase 1 output — validation scenarios mapped to VC-001…VC-015
├── contracts/
│   └── container-interfaces.md   # service / network / env / health "contract" between Compose services
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks output (NOT created here)
```

### Source Code (repository root)

```text
implementation/platform/
├── start.sh                       # REWRITTEN — docker compose up postgres+backend+frontend; wait health; print URLs
├── stop.sh                        # REWRITTEN — docker compose down; safe no-op; no PID files
├── e2e.sh                         # NEW — isolated E2E env: build → up → wait → playwright → exit code → down -v
├── README.md                      # UPDATED — containerized platform + E2E usage
│
├── backend/
│   └── core-service/
│       ├── Dockerfile             # NEW — multi-stage: maven build → temurin-21-jre runtime (+curl), non-root, EXPOSE 8080
│       ├── .dockerignore          # NEW — target/, .idea/, *.iml, etc.
│       └── … (unchanged Java source, pom.xml)
│
├── frontend/
│   └── web/
│       ├── Dockerfile             # NEW — multi-stage: node build (npm ci && ng build) → nginx:alpine serving dist/web/browser
│       ├── nginx.conf             # NEW — SPA fallback + location /api/ → proxy_pass http://backend:8080
│       ├── .dockerignore          # NEW — node_modules/, dist/, .angular/
│       └── … (unchanged Angular source, angular.json, package.json)
│
├── contracts/
│   └── openapi/openapi.yaml       # UNCHANGED — proxied verbatim by nginx
│
├── e2e/                           # NEW
│   ├── Dockerfile                 # FROM mcr.microsoft.com/playwright:v<pinned>-noble; COPY project; npm ci
│   ├── package.json               # @playwright/test pinned == image tag; scripts: "test", "test:one"
│   ├── package-lock.json
│   ├── playwright.config.ts       # testDir ./tests; baseURL from E2E_BASE_URL (default http://frontend);
│   │                              #   chromium only; trace retain-on-failure; screenshot only-on-failure; video off;
│   │                              #   outputDir test-results; reporter list + html(open:never); no webServer
│   ├── tsconfig.json
│   ├── tests/
│   │   └── platform-smoke.spec.ts # the ONLY spec EN002 ships — shell renders, no runtime error blocks interaction
│   ├── support/
│   │   ├── readiness.ts           # optional in-test guard (frontend reachable) — belt-and-braces vs compose health
│   │   └── data.ts                # unique synthetic value helpers (no real portfolio data) for future feature specs
│   ├── test-results/              # git-ignored — screenshots/traces/html on failure
│   └── README.md                  # how to run all/one test, debug, add a FD00N-*.spec.ts
│
└── infrastructure/
    └── local/
        ├── compose.yaml           # EXPANDED — services: postgres (persistent vol), backend, frontend, e2e (profile: e2e)
        ├── compose.e2e.yaml       # NEW — override for e2e.sh: disposable DB volume + shifted host ports
        ├── .env.example           # UPDATED — container-network datasource + image tags/build args as needed
        └── nginx/ (optional)      # if nginx.conf is templated per-env; default keeps it in frontend/web/
```

**Structure Decision**: keep the single cumulative platform under `implementation/platform/`,
exactly matching the enabler's §4 "Expected Repository Result". Three Dockerfiles live next to the
component they package; the Playwright project is a self-contained `e2e/` area; `compose.yaml`
stays the canonical orchestration file with `e2e` behind a profile, and a small `compose.e2e.yaml`
override gives `e2e.sh` an isolated, port-shifted, disposable environment. `start.sh` / `stop.sh` /
`e2e.sh` are the only supported entry points.

## Complexity Tracking

No Constitution Check violations. Section intentionally empty.

## Phase 0 — Research

See [research.md](./research.md). Decisions D1–D14 resolve the enabler's §28 open questions using
OD-1…OD-9 plus best practice for: multi-stage image builds, nginx SPA + `/api` proxy, Compose
health/`depends_on` ordering, isolated-project + disposable-volume E2E, `docker compose run` for
exit-code propagation, Playwright config against an already-running app, and diagnostics retention.
Remaining exact version/tag pins are recorded as OD-EN002-1…5 for maintainer confirmation (safe
defaults noted). No `NEEDS CLARIFICATION` on enabler intent remain.

## Phase 1 — Design & Contracts

- [data-model.md](./data-model.md) — no business entities. Documents the externalized
  configuration model (env vars per service), the container-image model, the volume model
  (persistent vs disposable), and the E2E artifact model.
- [contracts/container-interfaces.md](./contracts/container-interfaces.md) — the interface contract
  between Compose services: exposed ports, required env vars, health/readiness endpoints, the
  browser→nginx→backend routing rule, and the E2E service's inputs/outputs. EN002 introduces **no**
  new external REST contract.
- [quickstart.md](./quickstart.md) — validation scenarios for `start.sh`, `stop.sh`, `e2e.sh`, a
  forced-failure diagnostics check, a repeatability check, and a dev-data-safety check, each mapped
  to `VC-001…VC-015`.

### Post-Design Constitution Re-Check

Re-evaluated after Phase 1: still **PASS**. The design adds no application code, no external API
operation, no authentication, no CI, and no technology beyond the approved set; exact image tags
remain maintainer-confirmable; ADR-001 is intact and no new ADR is triggered (optional ADR-003
noted). The one flagged item (governance-doc touch-ups) remains a human-approval step, not a
silent change.
