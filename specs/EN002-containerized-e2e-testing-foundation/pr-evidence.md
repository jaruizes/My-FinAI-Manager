# EN002 — Establish Containerized End-to-End Testing Foundation · PR Evidence & Definition of Done

Assembled per `product/engineering/definition-of-done.md` → *Minimum Pull Request Evidence* and
*Architecture Change* checklist. All 35 tasks in `tasks.md` are `[X]`.

## What / why / trace

| Question | Answer |
|---|---|
| What does this implement? | EN002 — the local runtime becomes fully containerized (backend + frontend + db as Docker Compose services) and a containerized Playwright browser-E2E foundation is added, with one platform smoke test. |
| Trace | `specs/EN002-containerized-e2e-testing-foundation/{spec,plan,research,data-model,quickstart,tasks}.md`; `contracts/container-interfaces.md`. Covers `VC-001…VC-015`, `FR-001…FR-037`. |
| What changed? | **New**: `backend/core-service/Dockerfile` (+`.dockerignore`), `frontend/web/Dockerfile` + `nginx.conf` (+`.dockerignore`), `infrastructure/local/compose.e2e.yaml`, `e2e/` project (Dockerfile, package.json/-lock, `playwright.config.ts`, `tests/platform-smoke.spec.ts`, `support/{readiness,data}.ts`, README, `.gitignore`), `e2e.sh`. **Rewritten**: `start.sh`, `stop.sh` (container-only, no PID files), `infrastructure/local/compose.yaml` (backend/frontend/e2e services + health + depends_on), `infrastructure/local/.env.example`, `implementation/platform/README.md`, root `.gitignore`. **No application source changed.** |
| Why this design | `research.md` D1–D14 + the enabler's OD-1…OD-9: multi-stage Temurin-JRE backend image; nginx serving the Angular build + reverse-proxying `/api` (relative URLs, no service-DNS in the bundle); official pinned Playwright image, Chromium-only; `e2e.sh` owns an isolated Compose project (`-p finai-e2e`) with a disposable DB volume and shifted host ports; screenshot+trace on failure, video off; `start.sh` never rebuilds unconditionally. |
| Architecture boundaries | ADR-001 **intact** — `core-service` is still one deployable, now packaged as a container, not split. No new deployable, technology (Playwright already PREFERRED in `technology-policy.md`), messaging, or persistence tech. |
| ADRs required? | **None.** The host→container *local runtime* change is fully specified and human-approved in the enabler (§29 checklist complete) with resolved decisions OD-1…OD-9; a formal ADR-003 is optional (offered, not created). |

## Deviations / notes (surface for review)

1. **Image build mechanism** (`research.md` D13a): local Buildx is 0.11.2, below the ≥ 0.17 that
   `docker compose build` needs. `start.sh` / `e2e.sh` build with `docker build` and run
   `docker compose up --no-build`; `compose.yaml` keeps `build:` for newer-Buildx environments.
   Safe, reversible.
2. **`compose.e2e.yaml` uses `!override`** (Compose ≥ 2.24) to replace `ports:` / `volumes:` for
   isolation. Verified working with the installed Compose.
3. **Pinned tags** (OD-EN002-1…3, maintainer-confirmable): `@playwright/test` **1.62.1** ==
   `mcr.microsoft.com/playwright:v1.62.1-noble`; `eclipse-temurin:21-jre-jammy`; `maven:3.9-eclipse-temurin-21`;
   `node:22-alpine`; `nginx:1.27-alpine`; `postgres:16-alpine`.
4. **Governance-doc alignment** (`FR-032`/`FR-033`) is **prepared, not committed** — see
   `governance-alignment.md` (proposed edits to `testing-strategy.md`, `definition-of-done.md`, and
   the `project-verify` skill). Needs maintainer approval (constitution I). `technology-policy.md`
   was already updated (with approval) this session.
5. **No FD001 E2E test** — that is the EN002 follow-up (§26), owned by the FD001 Feature Definition.

## How it was validated (no CI — local, `quickstart.md`)

| Check | Result |
|---|---|
| `BUILD=1 ./start.sh` | builds `finai/core-service:local` + `finai/web:local`; postgres+backend+frontend `healthy`; URLs printed; exit 0 |
| host-process check | `ps aux` → **no** `spring-boot:run` / `ng serve` (VC-004) |
| backend health via `:8080` | `{"status":"UP", …, "db":{"status":"UP"}}` (VC-006) |
| routing `POST http://localhost:4200/api/portfolios` | `201` — browser→nginx→backend→postgres (VC-003) |
| SPA deep link `/portfolios/new` | `200` (nginx `try_files` fallback) |
| `./stop.sh` ×2 | exit 0 both; `postgres-data` volume retained (VC-005) |
| `./e2e.sh` | isolated `finai-e2e`, `platform-smoke.spec.ts` → **1 passed** (Chromium), exit 0, full `down -v` teardown (VC-007/008/009/011) |
| `./e2e.sh` ×3 consecutive | all pass, no manual cleanup (VC-010) |
| `./e2e.sh` while `./start.sh` platform is up | smoke passes; default platform `portfolio` rows **2 → 2**; `my-finai-manager-postgres-data` intact (VC-010, OD-6) |
| forced failure (`E2E_BASE_URL=http://frontend:9999 ./e2e.sh`) | exit **1**; `test-failed-1.png` + `trace.zip` + `containers/*.log` + HTML report under `e2e/test-results/`; teardown still ran (VC-011, VC-013) |
| secrets in artifacts | `grep finai_local_dev e2e/test-results/` → **none** (VC-013) |
| passing-run artifacts | HTML report + `.last-run.json` only — no trace, no video (OD-8) |
| `git add -n e2e/` | 0 `test-results/` files staged — git-ignored (VC-013) |
| backend regression `mvn -B clean verify` | **BUILD SUCCESS** — 84 tests, JaCoCo gate met (unchanged) |
| frontend regression `ng test` | **26/26 SUCCESS** (unchanged) |
| backend runtime image | 115 MB; `docker history` → **0** Maven references (VC-001) |
| frontend runtime image | 21 MB, `nginx:1.27-alpine`, no Node (VC-002) |

## DoD checklist

| Item | Status | Evidence |
|---|---|---|
| Approved scope | PASS | EN002 §3; only the smoke test, no feature E2E, no CI, no auth, no business behaviour (VC-015) |
| Architecture compliance | PASS | ADR-001 intact; single `core-service`; hexagonal rules unaffected (source untouched); ArchUnit green in regression |
| Unit / domain tests | PASS (unchanged) | `mvn verify` 84 tests green |
| Integration tests (Testcontainers) | PASS (unchanged) | backend ITs green; EN002 adds no infra-integration test |
| Contract tests | PASS (unchanged) | `CreatePortfolioControllerContractTest` green; nginx forwards `/api` verbatim, contract not modified |
| Architecture tests | PASS (unchanged) | ArchUnit green |
| **End-to-end tests** | **PASS** | `platform-smoke.spec.ts` green via `./e2e.sh`; harness + conventions established (FR-024, FR-033) |
| All required tests pass | PASS | backend `mvn verify`, frontend `ng test`, `./e2e.sh` |
| OpenAPI / contract | N/A | no external API change |
| Persistence / migration | N/A | no schema change |
| Documentation current | PASS | `implementation/platform/README.md`, `e2e/README.md`; governance-doc edits prepared for approval |
| Secrets / hygiene | PASS | no credentials in any Dockerfile/Compose/nginx/artifact; `.env` synthetic + git-ignored; `e2e/test-results/` + `e2e/node_modules/` git-ignored |
| Observability | N/A | backend logging unchanged |
| Platform lifecycle | PASS | `start.sh` / `stop.sh` / `e2e.sh` all functional; no alternative entry point; no PID files |
| ADR | PASS | none required (reasoned); optional ADR-003 offered |
