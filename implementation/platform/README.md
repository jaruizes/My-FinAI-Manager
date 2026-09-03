# My-FinAI-Manager — Executable Platform

This directory is the single cumulative executable realization of My-FinAI-Manager. Every Feature
Definition extends what is here; features do not create isolated applications.

Since `EN002`, the **complete local platform runs as containers** through Docker Compose — there is
no host Spring Boot process and no host Angular dev server.

## Prerequisites

| To… | You need |
|-----|----------|
| **Run** the platform + E2E tests | **Docker + Docker Compose v2** (daemon running). Nothing else. |
| **Build/test** application code locally | JDK 21 · the bundled `./mvnw` wrapper (backend — no host Maven needed) · Node ≥ 22.12 / ≥ 20.19 (frontend) |

Version decisions: `specs/EN001-bootstrap-platform/research.md` (Java 21 · Spring Boot 3.5.x ·
Maven · Angular 20 · PostgreSQL 16 · Flyway) and
`specs/EN002-containerized-e2e-testing-foundation/research.md` (Temurin 21 JRE image · nginx ·
Playwright — pinned tags).

> First run pulls base images and builds the platform images — allow a few minutes. Later starts
> reuse the images and are fast.

## Run the platform

```bash
cd implementation/platform
cp infrastructure/local/.env.example infrastructure/local/.env   # one-time (synthetic creds)

./start.sh            # build (if needed) + start postgres + backend + frontend as containers
BUILD=1 ./start.sh    # force-rebuild the images first
./stop.sh             # docker compose down (keeps the PostgreSQL data volume; safe anytime)
```

| Component | URL |
|-----------|-----|
| Frontend | http://localhost:4200 |
| Backend | http://localhost:8080 |
| Backend health | http://localhost:8080/actuator/health |
| PostgreSQL | localhost:5432 (db/user/pass from `infrastructure/local/.env`) |

`start.sh` / `stop.sh` / `e2e.sh` are the **canonical** entry points. Their internals may change;
the interface must not. The frontend container (nginx) serves the Angular build and reverse-proxies
`/api/*` to the backend container — the browser only ever talks to the frontend origin.

## Browser E2E tests

```bash
./e2e.sh                    # run the Playwright smoke suite against an isolated containerized stack
./e2e.sh -g "shell loads"   # filter to matching tests
```

`e2e.sh` spins up a throwaway, isolated copy of the platform (separate Compose project, disposable
DB volume, shifted host ports), runs Playwright in its own container (Chromium — no host browser
needed), propagates the exit code, and tears everything down. Your normal `./start.sh` data is
never touched. See `e2e/README.md` for debugging and for adding a feature-specific E2E test.

## Testing layers

| Layer | Runs where | Validates |
|-------|-----------|-----------|
| Unit / component | host (`./mvnw test`, `ng test`) | isolated deterministic logic |
| Integration | host + Testcontainers | components against real application-managed infrastructure (PostgreSQL) |
| Contract | host (`./mvnw verify`) | live payloads conform to `contracts/openapi/openapi.yaml` |
| Architecture | host (ArchUnit — `StandardArchitectureRulesTest`) | ADR-003 `domain → business → infrastructure` dependency direction |
| **E2E** | **containers (`./e2e.sh`, Playwright)** | **critical user journeys through the real containerized stack** |

The layers are complementary — E2E does **not** replace lower-level tests, and the E2E suite stays
deliberately small and journey-focused (`product/engineering/testing-strategy.md`).

## Capabilities

| Capability | Entry point | Reference |
|------------|-------------|-----------|
| Create investment portfolio | frontend `/portfolios/new` · `POST /api/portfolios` | `specs/FD001-create-investment-portfolio/quickstart.md` |
| Search the Financial Instrument catalog | `GET /api/financial-instruments?query=` | `specs/EN004-establish-financial-instrument-reference-data/quickstart.md` |

The external REST contract is `contracts/openapi/openapi.yaml` (OpenAPI 3.0.3). Portfolio data
lives in the `investor` / `portfolio` / `position` tables created by Flyway migration
`V2__portfolio.sql` (a single "Default Investor" is seeded — FD001 has no authentication yet; see
`product/architecture/adrs/ADR-002-interim-unauthenticated-write-access.md`).

The Financial Instrument catalog (`market` / `financial_instrument` tables — Flyway
`V3__financial_instrument.sql`) is populated on backend start from committed reference data under
`backend/core-service/src/main/resources/reference-data/` — a curated markets subset and a
deterministic Yahoo-shape instrument sample, normalized to canonical `(ticker, MIC, currency)`
purely through the two mapping CSVs. The import is idempotent, offline (no external provider is
contacted), and flag-guarded by `app.reference-data.import-on-startup`. See EN004 and
`backend/core-service/README.md`.

## Backend architecture

`backend/core-service` is the single Spring Boot deployable (ADR-001). It follows the **standard
Spring backend architecture** of `product/architecture/adrs/ADR-003-standard-spring-backend-architecture.md`:
each functional module is `domain / business / infrastructure` with dependencies pointing inward
(`infrastructure → business → domain`). The `portfolio` module is the reference implementation;
`financialinstrument` (EN004) is a second module following the same layout. Relational persistence
uses **Spring Data JPA** (`domain.ports` port → persistence adapter → Spring Data repository →
`@Entity` → PostgreSQL), with **Flyway owning the schema** (`spring.jpa.hibernate.ddl-auto: none`).
Build with `./mvnw` — see `backend/core-service/README.md`.
