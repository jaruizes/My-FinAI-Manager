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
| Jaeger (AI traces — EN006) | http://localhost:16686 |
| Prometheus (AI metrics — EN006) | http://localhost:9090 |
| Grafana (AI dashboard — EN006) | http://localhost:3000 |

**Optional — real market data for FD004 valuation.** Portfolio valuation needs Finnhub. With no key
the platform runs fine but valuations resolve to `FAILED`. To use live Finnhub, set a **real** key
(free at https://finnhub.io) as an environment variable — in the git-ignored `infrastructure/local/.env`
(`FINNHUB_API_KEY=...`) or exported before the script:

```bash
FINNHUB_API_KEY=xxxxxxxx ./start.sh
```

`compose.yaml` forwards `FINNHUB_API_KEY` (and the optional `FINNHUB_BASE_URL`) from the shell / `.env`
to the backend container. Never put the key in `.env.example` or commit it — it is sent to Finnhub
only as the `X-Finnhub-Token` header and is never logged.

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
| Select Financial Instrument from catalog (Add Position) | frontend `/portfolios/new` Add Position — search + select · `GET /api/financial-instruments?query=` | `specs/FD002-select-financial-instrument-from-catalog/quickstart.md` |
| List and view portfolio details | frontend **Home (`/`)** lists saved portfolios; a row opens `/portfolios/:id` · `GET /api/portfolios` · `GET /api/portfolios/{portfolioId}` | `specs/FD003-list-and-view-portfolio-details/quickstart.md` |
| Portfolio valuation & allocation | automatic on create; shown in `/portfolios/:id` · `GET /api/portfolios/{portfolioId}/valuation` | `specs/FD004-portfolio-valuation-and-allocation/quickstart.md` |
| Search the Financial Instrument catalog | `GET /api/financial-instruments?query=` | `specs/EN004-establish-financial-instrument-reference-data/quickstart.md` |

The external REST contract is `contracts/openapi/openapi.yaml` (OpenAPI 3.0.3). Portfolio data
lives in the `investor` / `portfolio` / `position` tables created by Flyway migration
`V2__portfolio.sql` (a single "Default Investor" is seeded — FD001 has no authentication yet; see
`product/architecture/adrs/ADR-002-interim-unauthenticated-write-access.md`).

**FD002** replaced the free-text ticker/market/currency inputs in Add Position with a catalog
search-and-select. A Position may only reference an active EUR/USD catalogued listing — the frontend
constrains the choice and the backend re-validates every Position on `POST /api/portfolios`
(`ValidationProblem` code `INSTRUMENT_NOT_IN_CATALOG`). No schema change; the FD001 request body and
Position identity are unchanged.

**FD003** made the saved portfolios visible: **Home (`/`) now renders the portfolio list** (name +
position count, newest first) with an empty state and a "Create portfolio" action; a row opens the
read-only detail at `/portfolios/:id`. Two new **read-only** endpoints — `GET /api/portfolios`
(returns the lean `PortfolioSummary`) and `GET /api/portfolios/{portfolioId}` (returns the existing
`Portfolio` schema; `404` `/problems/portfolio-not-found` for an unknown id, `400` for a non-UUID
id). No schema migration, no write path, no new business event.

**FD004** values a portfolio **automatically and synchronously** right after it is created (a
Spring in-process event → a catch-all listener, so a valuation failure never rolls back or hides the
portfolio), using EN005's market data. Deterministic `BigDecimal` maths computes each Position's
market value in **EUR and USD**, the portfolio totals, each Position's weight, and the sector
allocation (canonical EUR basis). The latest snapshot only is persisted (Flyway
`V4__portfolio_valuation.sql` — `portfolio_valuation` / `position_valuation` / `sector_allocation`,
owned by the `portfolio` module; FD001/EN004 tables untouched) and read through the dedicated
`GET /api/portfolios/{portfolioId}/valuation` (status `PENDING` / `COMPLETED` / `PARTIAL` /
`FAILED`; a Portfolio with no snapshot yet → an explicit `PENDING` body). The `/portfolios/:id`
detail is extended additively with the totals, per-Position valuation columns — market price (shown
**with its native currency**, e.g. `200.00 USD`) / EUR value / USD value / weight / sector, each
shown only when the Position could be valued, a missing price is **never** `0` — an explicit
valuation-state line, and **two mandatory allocation pie charts** — *Allocation by Ticker* and
*Allocation by Sector* — rendered as self-contained inline SVG (no charting library) and driven
**only** by the deterministic valuation weights the API returns (the frontend never recomputes
allocation). The sector percentages are read straight off the *Allocation by Sector* chart's legend
(there is no separate sector list). The charts appear only when there is a valued snapshot with a
positive EUR basis; otherwise the valuation-state line stands alone. FD003's `Portfolio` contract
and the `PortfolioValuation` response are unchanged.

### Provider integrations

**EN005** established a **backend-only** Finnhub market-data integration (`marketdata` module in
`core-service`): three provider-neutral in-process ports — latest price, company profile/sector,
USD↔EUR FX rate — behind adapters. **FD004** consumes them (via the `portfolio` module's own
`MarketDataGateway` ACL port — AR-062). EN005 itself is still not a user-facing capability. The
Finnhub API key is supplied per environment via **`FINNHUB_API_KEY`** (never committed; sent as the
`X-Finnhub-Token` header) — `compose.yaml` forwards it from the shell / `infrastructure/local/.env`
to the backend, so `./start.sh` picks it up (see "Run the platform" above); when unset, the
integration is disabled and every other capability keeps working. The base URL is overridable via
**`FINNHUB_BASE_URL`** (the containerized E2E uses it to point EN005's real adapter at a local
Finnhub stub). Automated tests never call the live provider.

**EN006** established a **provider-neutral AI model integration** capability (`ai` module in
`core-service`): a generic `AiModelPort` behind an invocation policy that centralizes prompt
composition/versioning, rule-based input/output guardrails, token/context/cost budget enforcement,
provider-neutral error handling with bounded timeout/retry, and OpenTelemetry-based observability.
EN006 ships **no live AI provider** — only a deterministic, network-free local/stub adapter, so it
needs no API key and defines no business AI feature; a real provider (Anthropic/OpenAI/Bedrock/
Vertex) is deferred to whichever future feature first needs one. Every AI invocation is traced and
metriced through a local Docker Compose observability stack (`ADR-004`) — an OpenTelemetry
Collector, Jaeger, Prometheus, and an auto-provisioned Grafana dashboard, all started/stopped by
`./start.sh` / `./stop.sh` alongside the rest of the platform. The internal
`POST /actuator/aidiagnostic` endpoint (not a business API, not in `openapi.yaml`) triggers one
deterministic invocation so the whole chain can be inspected end to end. No product/portfolio
behavior is affected; EN006 is purely additive infrastructure for future AI-assisted features.

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
