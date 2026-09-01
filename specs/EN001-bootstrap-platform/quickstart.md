# Quickstart & Validation: Bootstrap Executable Platform (EN001)

This guide proves EN001 end-to-end. Each scenario maps to one or more enabler Verification
Criteria (VC-001 … VC-009). It is a **validation guide**, not an implementation guide — code
belongs in `tasks.md` / the implementation phase.

Resolved decisions (OD-1…OD-6, see `research.md`): Java 21 · Spring Boot 3.5.6 · **Maven** ·
Angular 20 + Node 22 LTS · PostgreSQL 16 · Flyway.

> **Status:** Scenarios 1–6 were executed on 2026-09-01 and pass. `mvn clean verify` = BUILD
> SUCCESS (ArchUnit 4/4, `PlatformIntegrationIT` 3/3). See the VC coverage table at the bottom.

## Prerequisites

Installed on the workstation (documented, not installed by EN001):

- Docker / Docker Compose with a running daemon — Docker Desktop, OrbStack, colima, or Rancher
  Desktop all work (`mvn verify` sets the Docker API version for Testcontainers itself).
- JDK 21
- Node.js ≥ 20.19 or ≥ 22.12 (Angular 20 CLI requirement) + npm
- `curl`

First run downloads container images and build dependencies and may exceed the 15-minute
Success-Criteria budget; SC-006 measures subsequent runs.

## One-time setup

```bash
cd implementation/platform
cp infrastructure/local/.env.example infrastructure/local/.env   # synthetic local credentials
```

---

## Scenario 1 — Start the whole platform with one command · VC-001, VC-002, VC-003, VC-007

```bash
cd implementation/platform
./start.sh
```

Expected:

- Script verifies Docker is running; if not, it exits non-zero with a single clear message.
- PostgreSQL container starts and reaches a healthy state.
- Backend (`core-service`) starts and logs a structured "started" line.
- Frontend dev server starts.
- Script prints the backend URL, the frontend URL, the `/actuator/health` URL, and readiness
  hints, then returns.

Checks:

```bash
docker compose -f infrastructure/local/compose.yaml ps                     # postgres = healthy   (VC-003)
curl -fs http://localhost:8080/actuator/health | grep -q '"status":"UP"'   # backend running      (VC-002)
curl -fsI http://localhost:4200 | head -n 1                                # frontend serves HTML (VC-001)
```

---

## Scenario 2 — Backend health reflects database connectivity · VC-004, VC-005

```bash
curl -fs http://localhost:8080/actuator/health | json_pp
```

Expected: `status: UP` and a `components.db.status: UP` entry — the backend has an open connection
to PostgreSQL (VC-004, VC-005). This is the standard Spring Boot Actuator mechanism; EN001 adds no
custom endpoint.

### Degradation check (edge case: database down)

```bash
docker compose -f infrastructure/local/compose.yaml stop postgres
curl -s http://localhost:8080/actuator/health          # status: OUT_OF_SERVICE / DOWN, no stack trace
docker compose -f infrastructure/local/compose.yaml start postgres
# within a few seconds, health recovers with no backend restart   (spec edge case, research.md D2)
```

---

## Scenario 3 — Stop the whole platform with one command · VC-008

```bash
cd implementation/platform
./stop.sh
```

Expected:

- Backend and frontend processes are stopped (PID files removed).
- PostgreSQL container is stopped and removed.

Checks:

```bash
docker compose -f infrastructure/local/compose.yaml ps        # no running services
curl -s http://localhost:8080/actuator/health || echo "backend down (expected)"
./stop.sh                                                      # second run = safe no-op, exit 0
```

---

## Scenario 4 — Integration test against disposable PostgreSQL · VC-006

With **no** PostgreSQL installed on the host and the platform **not** running:

```bash
cd implementation/platform/backend/core-service
mvn verify
```

Expected:

- The integration test (`PlatformIntegrationIT`) starts a disposable PostgreSQL container via
  Testcontainers, Flyway applies `V1__baseline.sql`, a `SELECT 1` via the `DataSource` succeeds,
  and the booted context reports `/actuator/health` = `UP` with `db: UP` (VC-006).
- The ArchUnit guardrail (`HexagonalArchitectureRulesTest`) passes.
- The container is removed after the run.
- No step installs or provisions a database.

---

## Scenario 5 — Architecture conformance guardrail · FR-027

```bash
cd implementation/platform/backend/core-service
mvn -Dtest=HexagonalArchitectureRulesTest test
```

Expected: `HexagonalArchitectureRulesTest` passes. The rules are vacuously satisfied today (no
production modules yet) but are active: adding a class in `..platform..domain..` that imports
Spring or `java.sql` would fail the build. The test becomes substantive when FD001 adds real
modules.

---

## Scenario 6 — Structural baseline & "no business functionality" · VC-009, FR-002, FR-007, FR-018

Inspection checklist:

- [x] `implementation/platform/` contains `backend/core-service/`, `contracts/openapi/`,
      `frontend/web/`, `infrastructure/local/compose.yaml`, `start.sh`, `stop.sh`, `README.md`.
- [x] Backend: the Spring Boot entry point plus documented empty packages `platform/domain`,
      `platform/application/port/{in,out}`, `platform/adapter/{in/web,out/persistence}`,
      `bootstrap` — **no production business classes**.
- [x] `contracts/openapi/openapi.yaml` has `paths: {}` — no portfolio, position, valuation, risk,
      or other product endpoints; `README.md` explains the convention.
- [x] `db/migration/V1__baseline.sql` creates no business tables.
- [x] Frontend shows an app shell (sidebar + top bar + empty content) and no product screens; the
      sidebar exposes no navigation for capabilities that do not exist yet.
- [x] No Spring Security dependency; no `.github/` CI workflow (both deferred by EN001).
- [x] `grep -riE 'portfolio|position|valuation|risk|recommendation|stop-loss'` across
      `implementation/platform/` returns only incidental matches (comments/docs), no behavior.

---

## Verification Criteria coverage

Executed 2026-09-01 — all pass.

| VC | Scenario(s) | Evidence |
|---|---|---|
| VC-001 Frontend starts | 1 | `start.sh` → `curl -I http://localhost:4200` = `HTTP 200` |
| VC-002 Backend starts | 1 | `start.sh` → `GET /actuator/health` responds |
| VC-003 PostgreSQL starts | 1 | `docker compose ps` → `postgres` healthy |
| VC-004 Backend ↔ PostgreSQL connectivity | 2, 4 | `/actuator/health` `components.db.status = UP`; `PlatformIntegrationIT` `SELECT 1` |
| VC-005 Health endpoint responds | 2 | `/actuator/health` → `{"status":"UP",...}` |
| VC-006 Testcontainers integration test | 4 | `mvn verify` → `PlatformIntegrationIT` 3/3 against `postgres:16-alpine`, container disposed |
| VC-007 `start.sh` starts the platform | 1 | all three components reached running state from one command |
| VC-008 `stop.sh` stops the platform | 3 | backend/frontend killed, postgres container + network removed; 2nd run = no-op exit 0 |
| VC-009 No business functionality invented | 5, 6 | ArchUnit 4/4; `paths: {}`; empty baseline; grep finds only doc references |

Also verified (spec edge cases): DB stopped → `/actuator/health` = `503 DOWN` with a clean error
(no stack trace, no crash) → DB restarted → auto-recovers to `200 UP` without an app restart;
`start.sh` with the Docker daemon down → fail-fast, one clear stderr line, exit 1.
