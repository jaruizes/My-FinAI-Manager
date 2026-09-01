# My-FinAI-Manager — Executable Platform

This directory is the single cumulative executable realization of My-FinAI-Manager. Every Feature
Definition extends what is here; features do not create isolated applications.

Established by **EN001 — Bootstrap Executable Platform**. EN001 provides only the runnable
foundation — there is **no product behaviour** yet (no portfolios, valuation, risk, etc.).

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Docker + Docker Compose | any recent | daemon must be running |
| JDK | **21** (LTS) | backend build/run |
| Maven | 3.9+ | backend build tool |
| Node.js | **22 LTS** (≥ 22.12) or ≥ 20.19 | Angular 20 CLI requirement |
| npm | bundled with Node | frontend deps |

Version decisions are recorded in `specs/EN001-bootstrap-platform/research.md`
(OD-1…OD-6): Java 21 · Spring Boot 3.5.x · Maven · Angular 20 + Node 22 LTS · PostgreSQL 16 · Flyway.

> First run downloads container images, Maven dependencies and npm packages and can take several
> minutes. Subsequent starts are fast.

## Run the platform

```bash
cd implementation/platform
cp infrastructure/local/.env.example infrastructure/local/.env   # one-time (synthetic creds)
./start.sh          # PostgreSQL + backend + frontend
./stop.sh           # tears everything down (safe to run anytime)
```

| Component | URL |
|-----------|-----|
| Backend | http://localhost:8080 |
| Backend health | http://localhost:8080/actuator/health |
| Frontend | http://localhost:4200 |
| PostgreSQL | localhost:5432 (db/user/pass from `infrastructure/local/.env`) |

`start.sh` / `stop.sh` are the **canonical** lifecycle entry points. Their internals may change;
the interface must not. Background process PIDs live in `.run/` (git-ignored).

## Layout

```text
implementation/platform/
├── start.sh / stop.sh        canonical lifecycle scripts
├── backend/core-service/     single coarse-grained Spring Boot backend (ADR-001)
├── contracts/openapi/        contract-first OpenAPI location (skeleton — FD001 adds operations)
├── frontend/web/             Angular application shell
└── infrastructure/local/     Docker Compose (PostgreSQL only)
```

### Backend — Hexagonal Architecture convention

`backend/core-service/src/main/java/com/myfinaimanager/core/`

```text
CoreServiceApplication.java   Spring Boot entry point (only production class in EN001)
platform/                     documented layer convention — NO production classes yet
├── domain/                   business concepts, rules, deterministic calculations
├── application/
│   ├── port/in/              inbound ports (use cases exposed)
│   └── port/out/             outbound ports (dependencies required)
└── adapter/
    ├── in/web/               REST controllers implementing the OpenAPI contract
    └── out/persistence/      PostgreSQL adapters
bootstrap/                    the only place Spring wiring / @Configuration may live
```

Dependency direction points inward: `adapter → application → domain`. `domain` and `application`
must never import Spring, JDBC, HTTP, or serialization frameworks — enforced by
`HexagonalArchitectureRulesTest` (ArchUnit). In EN001 that test is a guardrail (no modules yet);
it becomes substantive with FD001.

### How FD001 (and later features) extend this platform

- Add a capability package as a sibling of `platform/` (e.g. `com.myfinaimanager.core.portfolio`)
  using the same `domain / application / adapter` layout.
- Add schema as `backend/core-service/src/main/resources/db/migration/V2__*.sql` (Flyway).
- Add operations + schemas to `contracts/openapi/openapi.yaml` (contract-first) and a contract test.
- Add frontend routes/components under `frontend/web/src/app/`; register nav entries in the
  sidebar only for capabilities that now exist.
- Extend `start.sh` / `stop.sh` only if a new runtime dependency is introduced — keep the
  interface stable.

## Tests

```bash
# Backend — unit tests (incl. ArchUnit) + Testcontainers integration test.
cd implementation/platform/backend/core-service
mvn verify            # needs a running Docker daemon for the Testcontainers integration test

# Frontend — unit tests (headless Chrome).
cd implementation/platform/frontend/web
npm test -- --watch=false --browsers=ChromeHeadless
```

The backend integration test (`PlatformIntegrationIT`) starts a disposable `postgres:16` container
via Testcontainers — no locally installed database is required.

## Troubleshooting

- **`mvn verify` — "client version 1.32 is too old. Minimum supported API version is 1.44"**:
  handled — `core-service/pom.xml` sets `api.version=1.44` for the Testcontainers test JVM
  (`docker-java` ignores the `DOCKER_API_VERSION` env var). Bump the
  `testcontainers.docker.api.version` property if a future engine raises its minimum.
- **`start.sh` — "image postgres:16-alpine ... does not provide the specified platform (linux/amd64)"**:
  you have a global `DOCKER_DEFAULT_PLATFORM=linux/amd64` and a previously-cached native-arch
  image. Fix: `docker rmi postgres:16-alpine` then re-run `start.sh` (Compose re-pulls the right
  platform).
- **Angular CLI — "requires a minimum Node.js version of v20.19 or v22.12"**: your Node is older;
  switch to a supported version (`nvm use 22`).

## Out of scope for EN001

No authentication / Spring Security, no CI/CD, no Kafka / Neo4j / Redis / Kubernetes, no business
schema or business API endpoints, no LLM/AI. These arrive with later Feature Definitions or
Technical Enablers when justified.
