# Implementation Plan: Portfolio Valuation & Allocation (FD004)

**Branch**: `FD004-portfolio-valuation-and-allocation` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/FD004-portfolio-valuation-and-allocation/spec.md`

**Authoritative feature**: `product/definition/features/FD004-portfolio-valuation-and-allocation/FD004-portfolio-valuation-and-allocation.md`
(**Status: Approved** — §31 signed by jaruiz 2026-09-04; the eight §30 open questions were resolved
by the product owner on 2026-09-04 and recorded in FD004 §30).

**Consumed capabilities**: FD001 (`POST /api/portfolios` + persisted `portfolio` / `position` data +
`CreatePortfolioService`), FD002 (`INSTRUMENT_NOT_IN_CATALOG` — must not regress), FD003 (Portfolio
list + read-only detail + `GET /api/portfolios[/{id}]` contract + `portfolio-detail.page.ts`),
EN004 (canonical `ticker + market(MIC)` identity), **EN005 — the `marketdata` module's three
provider-neutral ports `MarketDataPort` / `InstrumentProfilePort` / `FxRatePort` and their
`MarketPrice` / `InstrumentProfile` / `FxRate` read models + neutral exception set**.

**Governing decisions**: ADR-001 (one `core-service`), ADR-002 (single seeded Default Investor),
ADR-003 (Standard Spring Backend Architecture), EN002 (containerized Playwright E2E),
`product/ux/design-system.md`, **AR-062** (inter-module reads via a published port, from the
consumer's `infrastructure` only). **A new Flyway migration is required** (`V4__portfolio_valuation.sql`);
**no new ADR is anticipated** — FD004 adds a valuation area *inside the existing `portfolio` module*
and one read endpoint, with no new deployable, broker, scheduler, or persistence technology. If
planning/implementation surfaces a material architecture decision it MUST be raised for human
approval (constitution IV).

> **Approval status — cleared 2026-09-04.** FD004 §31 signed; the §30 material decisions
> (synchronous after-commit valuation; dedicated `GET /api/portfolios/{portfolioId}/valuation`;
> `FAILED` only when no Position valuable or no total producible; 2-dp display; provider sector
> string shown directly; latest-only valuation schema) are recorded in FD004 §30 and drive FR-004 /
> FR-018 / FR-025 / FR-014 / FR-031 / FR-020. Spec has **zero `[NEEDS CLARIFICATION]`**. Nine
> technical Open Decisions (OD-FD004-1…9) below carry recommended positions.

## Summary

FD004 values a Portfolio **synchronously, right after FD001 persists it**, using EN005's external
data, with **deterministic** EUR/USD maths, and shows the result in the FD003 detail. A valuation
failure never touches the created Portfolio.

1. **Trigger** (`portfolio` module): `CreatePortfolioService`, after `repository.save(...)` returns
   and only on a genuine new creation (`!replayed`), publishes a Spring **`PortfolioCreatedEvent`**.
   A **synchronous** `@EventListener` (`PortfolioValuationOnCreationListener`, in
   `portfolio.infrastructure`) calls `ValuePortfolioUseCase.value(portfolioId)` **wrapped in a
   catch-all** — a valuation error is logged (`event=PortfolioValuationFailed`) and a `FAILED`
   snapshot is best-effort persisted; it **never** propagates into the create request, which still
   returns `201`. No `@Async`, no `@TransactionalEventListener`, no Kafka, no scheduler (the FD001
   save already committed via its `TransactionTemplate`, so the listener runs post-commit on the
   request thread — OD-FD004-1).
2. **ACL to EN005** (`portfolio` module): one published port
   `portfolio.domain.ports.MarketDataGateway` with `Optional`-returning methods
   (`latestPrice(ticker, market, currency)`, `sector(ticker, market, currency)`,
   `fxRate(from, to)`), implemented by `EnMarketDataGatewayAdapter` in
   `portfolio.infrastructure.marketdata` — the **only** class that imports `marketdata.*`. It calls
   EN005's three ports and translates every `MarketDataException` into `Optional.empty()` (a domain
   "unavailable" signal). `portfolio.domain` / `portfolio.business` never see a `marketdata` type
   (AR-062 — OD-FD004-2). **No EN005 change** (OD-FD004-9).
3. **Deterministic calculator** (`portfolio.domain`): `PortfolioValuationCalculator` — a **pure**
   function `(Portfolio, Map<PositionId, PositionInputs>, FxRates) -> PortfolioValuation`. No ports,
   no Spring, no I/O, **no LLM**. `BigDecimal` only: `nativeMarketValue = quantity × price`;
   USD/EUR cross-values via the FX rates; `totalEUR` / `totalUSD` = Σ; `weight = valueInEUR /
   totalEUR`; sector allocation on normalised EUR values; status per FR-015…FR-018.
4. **Valuation domain model** (`portfolio.domain.model`): `PortfolioValuation` (aggregate:
   `portfolioId`, `ValuationStatus`, `calculatedAt`, `totalValueEUR?`, `totalValueUSD?`,
   `marketDataAsOf?`, `fxDataAsOf?`, `List<PositionValuation>`, `List<SectorAllocation>`),
   `PositionValuation` (ticker, market, quantity, `valued` flag, `marketPrice?`, `nativeCurrency`,
   `nativeMarketValue?`, `valueInEUR?`, `valueInUSD?`, `portfolioWeight?`, `sector` (string or
   `"Unclassified"`), `priceObservedAt?`), `SectorAllocation` (`sector`, `sectorValueEUR`,
   `sectorWeight`), `ValuationStatus` enum `PENDING`/`COMPLETED`/`PARTIAL`/`FAILED`.
5. **Business** (`portfolio.business`): `ValuePortfolioUseCase` / `PortfolioValuationService`
   (`@Service`) — load the aggregate (`PortfolioRepository.findByIdForInvestor`), gather inputs via
   `MarketDataGateway` (per Position price + sector; the FX directions actually needed —
   OD-FD004-8), run the calculator, persist via `PortfolioValuationRepository.upsertLatest(...)`.
   Never calls `PortfolioRepository.save` — read-only on `portfolio`/`position` (SC-008). Idempotent
   (FR-021).
6. **Persistence** (`portfolio.infrastructure.persistence`): **`V4__portfolio_valuation.sql`** —
   `portfolio_valuation` (`portfolio_id` UNIQUE → **latest only**), `position_valuation` (FK,
   `ON DELETE CASCADE`), `sector_allocation` (FK, `ON DELETE CASCADE`). New JPA entities + a derived
   `PortfolioValuationJpaRepository` + `PortfolioValuationPersistenceAdapter` implementing the port
   — `upsertLatest` runs in one write transaction: delete the existing snapshot's children + parent
   for the `portfolio_id`, insert the new graph. FD001/EN004 tables untouched; `ddl-auto: none`
   (OD-FD004-4).
7. **API — contract-first** (`implementation/platform/contracts/openapi/openapi.yaml`, 3.0.3):
   **`GET /api/portfolios/{portfolioId}/valuation`** → a new `PortfolioValuation` schema
   (`status`, `calculatedAt`, `totalValueEUR`, `totalValueUSD`, `marketDataAsOf`, `fxDataAsOf`,
   `positions[]`, `sectors[]`). New `PortfolioValuationController` (`@RestController`, separate from
   the FD003 `PortfolioQueryController`); `{portfolioId}` typed `UUID` → framework `400`;
   unknown / other-investor id → `404` `/problems/portfolio-not-found` (reuse
   `PortfolioNotFoundException` + widen `PortfolioExceptionHandler`); a Portfolio with no snapshot
   yet → `200` with `status = "PENDING"` and null monetary fields (FR-025; OD-FD004-6). **FD003's
   `Portfolio` schema and `GET /api/portfolios[/{id}]` contract are unchanged.** New
   `PortfolioValuationResponse` DTO + `PortfolioValuationResponseMapper`. Contract test + an FD003
   contract-regression re-run.
8. **Frontend** (`implementation/platform/frontend/web/src/app/portfolio/`): `portfolio-valuation.service.ts`
   (`getValuation(id)` → `GET /api/portfolios/:id/valuation`, `404` → `'not-found'`, error → `null`);
   extend `portfolio-detail.page.ts` (FD003) with — a **totals card** (€ and $), the extended
   Position columns (`Market Price` / `Market Value` / `Value in EUR` / `Value in USD` / `Portfolio
   Weight` shown only when the Position is `valued`; `Sector` shown when present), a **sector
   allocation** list with 2-dp percentages, and a **valuation-state** line ("Valued at …" /
   "Partial valuation — market data unavailable for N Positions" / "Valuation pending" /
   "Valuation unavailable"). **No** control edits a value. New `PortfolioValuationView` model. Money
   / percentages formatted to **2 dp**.
9. **E2E — two mandatory scenarios** (`implementation/platform/e2e/`) with a **controlled Finnhub
   HTTP boundary**: a small **`finnhub-stub` service** in `compose.e2e.yaml` serving canned
   `/quote` · `/stock/profile2` · `/forex/rates` JSON keyed by `symbol` / `base`; the e2e backend
   gets `FINNHUB_API_KEY=e2e-stub` and `FINNHUB_BASE_URL=http://finnhub-stub:8080`. EN005's **real**
   adapter / mapper / cache / error-translation runs; frontend / backend / calculations /
   persistence / PostgreSQL are all real (FD004 §25). **E2E-001** (`fd004-valuation.spec.ts`) —
   create the deterministic AAPL(USD)+SAN(EUR) Portfolio → open the detail → assert totals
   `€2,100.00` / `$2,625.00`, per-Position valuations, sectors, weights, sector allocation
   (`Technology 76.19 %`, `Financial Services 23.81 %`) → Portfolio still in the Home list.
   **E2E-002** (`fd004-provider-failure.spec.ts`) — the stub returns `429` / empty → create a
   Portfolio → it is persisted and in the Home list → the detail shows a valuation-state message
   and **no** fabricated zeros (OD-FD004-7).
10. **Architecture**: `StandardArchitectureRulesTest` — **+2 rules** (18 → 20):
    `portfolio.domain` / `portfolio.business` free of `..core.marketdata..`; `..core.marketdata..`
    referenced only from `..core.portfolio.infrastructure.marketdata..`. `PortfolioValuationCalculator`
    stays framework-free; ArchUnit `domain_has_no_framework_dependencies` already forbids
    `double`/`float`? — no; SC-006 adds a code-review + a targeted "no `double`/`float` field in the
    valuation model" check.
11. **Regression**: FD001 create + FD002 selection + FD003 list/detail + EN004 + EN005 suites and
    E2Es stay green; `./mvnw verify` (≥ 90 % line & branch) + `ng test` + `./e2e.sh` green.

## Technical Context

**Language / Runtime**: Backend — Java 21, Spring Boot 3.5.6 (unchanged). Frontend — Angular 20,
TypeScript 5.8, standalone components + `HttpClient` (unchanged). E2E — `@playwright/test` (EN002).

**Primary Dependencies**: **all reused** — Spring Data JPA + Flyway (new migration), Spring
`ApplicationEventPublisher` / `@EventListener` (in-framework), the EN005 `marketdata` ports,
`swagger-request-validator` (contract test), `BigDecimal`. **No new Maven / npm dependency.** The
E2E `finnhub-stub` is a tiny committed HTTP server (a ~40-line script or a WireMock image with
committed mappings — OD-FD004-7); it is **e2e infrastructure**, not an application dependency.

**Storage**: PostgreSQL 16. **One new Flyway forward migration** `V4__portfolio_valuation.sql`
adds `portfolio_valuation` / `position_valuation` / `sector_allocation` (latest-only), owned by the
`portfolio` module. FD001 (`V2`) and EN004 (`V3`) schemas **unchanged**. `ddl-auto: none`.

**Testing**: `./mvnw -B clean verify` (Surefire unit + Failsafe/Testcontainers + JaCoCo `check` +
ArchUnit); `ng test`; `./e2e.sh`. FD004 unit tests (calculator, status rules, mappers) are
**RED-first**. Persistence + trigger ITs use Testcontainers PostgreSQL with a **fake
`MarketDataGateway`** (deterministic values — the EN005 boundary is a true external provider, so it
is stubbed, not Testcontainers). Contract test via `@WebMvcTest` + `swagger-request-validator`.

**Target Platform**: the existing `core-service` container (ADR-001) + the EN002 containerized
platform. `compose.yaml` gains **no** service for local `./start.sh`; `compose.e2e.yaml` gains the
`finnhub-stub` service **for E2E only**.

**Performance Goals**: SC-011 — the valued detail renders within 2 s for ≤ ~50 Positions. Valuation
itself makes ≤ (2 × positions + 2 FX) EN005 calls, each served by EN005's short-lived cache and
bounded by EN005's 2 s / 5 s timeouts; the synchronous trigger adds this to the `POST /api/portfolios`
latency (acceptable — a create is already a deliberate action; the catch-all keeps it from failing).

**Constraints**:
- ADR-003 layout; ArchUnit-enforced. New classes: valuation model + `MarketDataGateway` +
  `PortfolioValuationRepository` in `portfolio.domain`; `PortfolioValuationCalculator` in
  `portfolio.domain` (pure); `ValuePortfolioUseCase` / `PortfolioValuationService` in
  `portfolio.business`; `EnMarketDataGatewayAdapter` + `PortfolioValuationOnCreationListener` +
  persistence adapter/entities/repository + `PortfolioValuationController` + DTO + mapper in
  `portfolio.infrastructure.*`.
- **Deterministic + decimal-safe**: `BigDecimal` for every price / value / rate / weight; **no**
  `double`/`float`; **no LLM** on the valuation or sector path (FR-008, FR-010; closure gate).
- **Creation independence**: the listener catches everything; valuation opens **no** write on
  `portfolio` / `position` and cannot roll back the create (FR-002, SC-008; closure gate).
- **Missing data**: a Position with no price is `valued = false` with **null** monetary fields
  (never `0`); status `PARTIAL` (FR-017, FR-019; closure gate).
- **Provider-neutral**: no Finnhub type / field / URL / key in FD004 code or the valuation API
  (FR-026, FR-027); EN005 accessed only via `MarketDataGateway` → its ports.
- Contract-first for the new endpoint; **FD003's contract unchanged**.
- FD001/FD002/FD003/EN004/EN005 behavior, contracts, tests, E2Es unchanged (FR-034).
- `./start.sh` / `./stop.sh` / `./e2e.sh` interface unchanged (EN002).

**Scale/Scope**: ~1 event + 1 listener; 1 ACL port + 1 adapter; 1 pure calculator; ~5 valuation
domain types; 1 use case + 1 service; 1 Flyway migration + 3 entities + 1 JPA repo + 1 persistence
adapter; 1 OpenAPI operation + 1 schema; 1 controller + 1 DTO + 1 mapper + 1 exception-handler
method; 1 frontend service + a detail-page extension + 1 view model; 2 E2E specs + 1 e2e stub
service; ArchUnit +2 rules. No new module, no new deployable.

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Human-Governed Source of Truth | **PASS** | Implements the human-approved FD004 (§31 signed 2026-09-04). FD004 §30/§31 were updated as a **human-directed decision record** (the product owner supplied the §30 answers and directed progression). Tech unchanged — no new dependency. |
| II | Definitions/Enablers Are Authoritative Intent | **PASS** | Traces to exactly one Feature Definition (FD004). Every FR maps to an FD004 §/BR/AC or a resolved §30 decision (spec Traceability + Clarifications). Consumes FD001/FD003/EN004/EN005; expands none. |
| III | Derived Artifacts & Repository Layout | **PASS** | Artifacts under `specs/FD004-…`; implementation under `implementation/platform/`; no root `src/` / `apps/`. |
| IV | No Invention; Surface Material Ambiguity | **PASS** | Material product decisions (§30) are human-approved in FD004. Nine **technical** ODs (below) carry recommended positions; the schema shape, trigger primitive, and E2E stub mechanism are surfaced, not silently chosen. |
| V | Features Extend the Platform | **PASS** | Vertical slice: the `portfolio` module gains a valuation area, the FD003 detail is extended, EN005's ports are consumed. No isolated app; `./start.sh` / `./e2e.sh` stay coherent. |
| VI | Hexagonal Architecture & Deterministic Logic | **PASS** | Deterministic valuation is a **pure** `PortfolioValuationCalculator` in `domain`; the service (business) orchestrates behind ports; `domain`/`business` framework-free and free of `marketdata` types (ArchUnit). **No LLM** anywhere (FR-008, FR-010). All money is `BigDecimal`. |
| VII | Test-First for Deterministic Logic; Testcontainers by Default | **PASS** | The calculator + status rules + mappers are TDD'd RED-first. Persistence + the create→value trigger are covered by Testcontainers ITs (with a **fake `MarketDataGateway`** — EN005 is a true external provider, correctly stubbed per constitution VII). No manually-installed infra. |
| VIII | Contract-First External APIs | **PASS** | The new `GET /api/portfolios/{portfolioId}/valuation` is added to `openapi.yaml` (3.0.3) first, with a contract test; business language; RFC 9457 `404`; no Finnhub / persistence leakage. FD003's contract is left byte-identical (additive only). |

**Repository-structure / technology-policy quick check:**

| Check | Status | Evidence |
|---|---|---|
| One `core-service` deployable (ADR-001) | PASS | no new service; `compose.yaml` unchanged (E2E-only `finnhub-stub` in `compose.e2e.yaml`) |
| ADR-003 module layout | PASS | valuation area inside the `portfolio` module; new classes placed per AR-055…AR-058 |
| AR-062 (inter-module read via published port, from infrastructure only) | PASS | `portfolio.business` → `portfolio.domain.ports.MarketDataGateway`; only `portfolio.infrastructure.marketdata` imports `marketdata.*`; ArchUnit +2 rules |
| No new technology / dependency | PASS | Spring events + JPA + Flyway + `BigDecimal` + EN005 ports; `pom.xml` / `package.json` untouched |
| No new deployable / broker / scheduler / cache / search engine / new persistence tech | PASS | synchronous in-process Spring event (FR-003); the EN005 short-lived cache already exists |
| Schema change uses the approved migration mechanism | PASS | `V4__portfolio_valuation.sql` (Flyway forward migration); FD001/EN004 migrations untouched |
| No write path on `portfolio` / `position`; no rollback of creation | PASS | FR-002, SC-008 — valuation is read-only on FD001 data; the listener catches all |
| No LLM; deterministic monetary maths | PASS | FR-008, FR-010; pure calculator; SC-006 |
| No historical valuation / scheduler / third currency / manual revalue | PASS | FR-040; `portfolio_valuation.portfolio_id` UNIQUE (latest only) |

**Result: PASS.** The ODs are technical and change no FD004 product decision.

## Open Decisions (technical) — recommended positions, for confirmation

| ID | Decision point | Recommended position | Alternatives rejected |
|---|---|---|---|
| OD-FD004-1 | Post-commit trigger primitive | `CreatePortfolioService` publishes a Spring **`PortfolioCreatedEvent`** after `repository.save` (only `!replayed`); a **synchronous `@EventListener`** in `portfolio.infrastructure` runs `ValuePortfolioUseCase.value(...)` in a **catch-all** (log `event=PortfolioValuationFailed`, best-effort persist a `FAILED` snapshot, never rethrow). The FD001 save already committed via its own `TransactionTemplate`, so the listener runs post-commit on the request thread. | *Direct call in `CreatePortfolioController`* — puts orchestration in the transport adapter; the event keeps FD001 minimally coupled (1 line). *`@TransactionalEventListener(AFTER_COMMIT)`* — there is no ambient transaction at publish time, so it would silently not fire. *`@Async` / a background worker / Kafka* — violates FD004 §30.1 (synchronous) and FR-003. |
| OD-FD004-2 | `portfolio` → EN005 access | **One published ACL port `portfolio.domain.ports.MarketDataGateway`** (`Optional<PositionPricing> latestPrice(...)`, `Optional<String> sector(...)`, `Optional<FxQuote> fxRate(from, to)`) + `EnMarketDataGatewayAdapter` in `portfolio.infrastructure.marketdata` (the only importer of `marketdata.*`), translating every `MarketDataException` → `Optional.empty()`. | *Three separate ports* — more interfaces, one seam is enough. *`portfolio.business` importing `marketdata.domain.ports` directly* — violates AR-062. *A new shared `marketdata` façade* — not `portfolio`'s to define. |
| OD-FD004-3 | Valuation code placement | **Inside the existing `portfolio` module** (valuation is a Portfolio concern; FD004 keeps the Portfolio visible via FD003). `domain/{model,ports}` + a pure `domain` calculator + `business` service + `infrastructure/{marketdata, persistence, api/rest}`. | *A new `valuation` module* — the `architecture.md` illustrative tree shows one, but FD004 is a small Portfolio-centric slice; a new module adds ceremony and a second owner of Portfolio identity. Revisit if valuation grows (history, benchmarks). |
| OD-FD004-4 | Persistence schema (latest-only) | `portfolio_valuation(portfolio_id UUID **UNIQUE** REFERENCES portfolio(id) ON DELETE CASCADE, status TEXT CHECK, calculated_at TIMESTAMPTZ, total_value_eur NUMERIC NULL, total_value_usd NUMERIC NULL, market_data_as_of TIMESTAMPTZ NULL, fx_data_as_of TIMESTAMPTZ NULL)`; `position_valuation(id UUID PK, portfolio_valuation_id FK ON DELETE CASCADE, ticker, market, quantity NUMERIC, valued BOOLEAN, market_price NUMERIC NULL, native_currency CHAR(3), native_market_value NUMERIC NULL, value_eur NUMERIC NULL, value_usd NUMERIC NULL, portfolio_weight NUMERIC NULL, sector TEXT, price_observed_at TIMESTAMPTZ NULL)`; `sector_allocation(id UUID PK, portfolio_valuation_id FK ON DELETE CASCADE, sector TEXT, sector_value_eur NUMERIC, sector_weight NUMERIC)`. `NUMERIC` with **no** precision/scale (exact round-trip, matches FD001). Re-valuation: `DELETE FROM portfolio_valuation WHERE portfolio_id = ?` (cascades) then insert — one transaction. | *An append-only history table* — FD004 §12 forbids history. *A `valuation_json` column* — loses queryability and the provider-neutral shape guarantee. *Reusing `V2`* — FD001 schema must not be edited. |
| OD-FD004-5 | Weight precision | Store `portfolio_weight` / `sector_weight` as an **exact fraction** computed `valueInEUR.divide(totalEUR, 12, HALF_UP)` (12 dp headroom); totals / values stored **unscaled**. The API returns the stored values; **the frontend formats money and percentages to 2 dp** (FD004 §30.3–§30.4). E2E compares stored totals exactly and displayed values to 2 dp. | *Round weights to 2 dp in storage* — then Σ drifts from 100 % beyond display rounding (FR-013). *No division scale* — `ArithmeticException` on non-terminating decimals. |
| OD-FD004-6 | Valuation controller & 404 | A **new `PortfolioValuationController`** (`GET /api/portfolios/{portfolioId}/valuation`), `{portfolioId}` typed `UUID` → framework `400`. Reuse `PortfolioNotFoundException` → widen `PortfolioExceptionHandler.assignableTypes` to include it → `404` `/problems/portfolio-not-found`. A Portfolio with **no snapshot yet** → `200` with `{ "status": "PENDING", ... nulls }`. | *Add to `PortfolioQueryController`* — mixes the FD003 read concern with FD004. *`404` for "no snapshot"* — conflates "portfolio absent" with "not valued yet". *A `202`/polling model* — valuation is synchronous, so a snapshot exists by the time the detail loads. |
| OD-FD004-7 | E2E EN005 boundary | A **`finnhub-stub` HTTP service in `compose.e2e.yaml`** (committed canned JSON for `/quote` · `/stock/profile2` · `/forex/rates`, keyed by `symbol` / `base`); e2e backend `FINNHUB_BASE_URL` → the stub, `FINNHUB_API_KEY=e2e-stub`. EN005's **real** adapter / mapper / cache / status-translation runs. E2E-002 uses a stub variant returning `429` / empty bodies. Requires `finnhub.base-url` to be env-overridable (it already binds `${FINNHUB_BASE_URL:https://finnhub.io/api/v1}` — add the env var). | *Profile-guarded fake `@Primary` `MarketDataPort` beans in `src/main`* — simpler but puts test doubles in production code and skips EN005's real path. *Live Finnhub* — forbidden by the closure gate. *A Playwright network intercept* — the calls are backend→provider, not browser. |
| OD-FD004-8 | Which FX rates to fetch | The service inspects the Portfolio's Position currencies and requests **only the directions needed**: any USD Position ⇒ `USD→EUR`; any EUR Position ⇒ `EUR→USD`; a single-currency Portfolio still needs the one *other-direction* rate for its second total. Missing a needed rate → the affected cross-values are absent → `PARTIAL` (FR-018). | *Always fetch both* — one wasted call for a single-currency Portfolio; harmless but avoidable. *Derive one rate as `1/other`* — EN005 OD-EN005-7 kept directions independent; FD004 does not re-introduce inversion. |
| OD-FD004-9 | EN005 API sufficiency | **No EN005 change.** `MarketDataPort.getLatestPrice`, `InstrumentProfilePort.getProfile` take `InstrumentIdentifier(ticker, market, currency)` — buildable from a `Position`; `FxRatePort.getRate(SupportedCurrency, SupportedCurrency)` covers EUR/USD; `MarketPrice`/`FxRate` carry `observedAt`/`source`; `InstrumentProfile.sector` is a classification string or `UNCLASSIFIED`. | *Adding a batch `getPrices(list)` to EN005* — an optimisation, not needed at FD004 scale; if raised it is an EN005 change with its own approval. |

## Project Structure

### Documentation (this feature)

```text
specs/FD004-portfolio-valuation-and-allocation/
├── plan.md              # this file
├── research.md          # Phase 0 — D1…D11
├── data-model.md        # Phase 1 — valuation domain model + schema (V4) + response shapes
├── contracts/
│   ├── openapi/portfolio-valuation.read.yaml   # mirror fragment: GET /api/portfolios/{id}/valuation + PortfolioValuation schema
│   ├── market-data-gateway.md                  # portfolio.domain.ports.MarketDataGateway (ACL to EN005) contract
│   └── valuation-calculation.md                # the deterministic calculator + status-rule contract (worked examples)
├── quickstart.md        # Phase 1 — scenarios A–H ⇒ AC-001…AC-012 + SC + E2E-001/002
├── checklists/requirements.md   # 16/16 (already passing)
└── tasks.md             # Phase 2 — /speckit-tasks (NOT this command)
```

### Source Code (repository)

```text
implementation/platform/
├── contracts/openapi/openapi.yaml       # + GET /api/portfolios/{portfolioId}/valuation, + PortfolioValuation / PositionValuation / SectorAllocation schemas
│
├── backend/core-service/src/
│   ├── main/resources/db/migration/
│   │   └── V4__portfolio_valuation.sql   # NEW — portfolio_valuation (portfolio_id UNIQUE), position_valuation, sector_allocation
│   ├── main/java/com/myfinaimanager/core/portfolio/
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── PortfolioValuation.java          # NEW — aggregate (latest snapshot)
│   │   │   │   ├── PositionValuation.java           # NEW
│   │   │   │   ├── SectorAllocation.java            # NEW
│   │   │   │   ├── ValuationStatus.java             # NEW — PENDING/COMPLETED/PARTIAL/FAILED
│   │   │   │   └── PortfolioValuationCalculator.java # NEW — PURE deterministic function (no ports, no Spring)
│   │   │   ├── ports/
│   │   │   │   ├── MarketDataGateway.java           # NEW — ACL port over EN005 (Optional-returning)
│   │   │   │   └── PortfolioValuationRepository.java # NEW — upsertLatest / findByPortfolioId
│   │   │   └── events/PortfolioCreatedEvent.java     # NEW — record(PortfolioId)
│   │   ├── business/
│   │   │   ├── ValuePortfolioUseCase.java           # NEW — value(PortfolioId)
│   │   │   └── PortfolioValuationService.java       # NEW — @Service; gather inputs → calculator → persist
│   │   ├── business/CreatePortfolioService.java     # + publish PortfolioCreatedEvent after save (only !replayed)
│   │   └── infrastructure/
│   │       ├── marketdata/
│   │       │   ├── EnMarketDataGatewayAdapter.java  # NEW — the ONLY importer of marketdata.* ; translates exceptions → Optional.empty()
│   │       │   └── package-info.java
│   │       ├── valuation/
│   │       │   └── PortfolioValuationOnCreationListener.java  # NEW — synchronous @EventListener; catch-all
│   │       ├── persistence/
│   │       │   ├── entity/{PortfolioValuationEntity,PositionValuationEntity,SectorAllocationEntity}.java  # NEW
│   │       │   ├── repository/PortfolioValuationJpaRepository.java  # NEW — derived queries
│   │       │   ├── PortfolioValuationPersistenceAdapter.java        # NEW — implements PortfolioValuationRepository (upsert-latest, one tx)
│   │       │   └── mapper/PortfolioValuationPersistenceMapper.java  # NEW — domain ⇄ entity
│   │       └── api/rest/
│   │           ├── PortfolioValuationController.java   # NEW — GET /api/portfolios/{portfolioId}/valuation
│   │           ├── PortfolioExceptionHandler.java      # + assignableTypes += PortfolioValuationController
│   │           ├── dto/PortfolioValuationResponse.java # NEW
│   │           └── mapper/PortfolioValuationResponseMapper.java # NEW
│   └── test/java/com/myfinaimanager/core/portfolio/
│       ├── domain/model/PortfolioValuationCalculatorTest.java        # NEW — RED first — the §8/§9/§11/§16 worked examples + status rules
│       ├── business/PortfolioValuationServiceTest.java               # NEW — RED first — mocked gateway + repo; per-Position failure → PARTIAL; save never called
│       ├── infrastructure/marketdata/EnMarketDataGatewayAdapterTest.java  # NEW — EN005 exception → Optional.empty()
│       ├── infrastructure/api/rest/PortfolioValuationControllerContractTest.java  # NEW — @WebMvcTest + swagger-request-validator
│       ├── infrastructure/persistence/PortfolioValuationPersistenceAdapterIT.java # NEW — Testcontainers — upsert-latest, cascade, 0 duplicates
│       └── PortfolioValuationOnCreationIT.java                       # NEW — Testcontainers — create → snapshot exists; gateway failure → Portfolio persisted + FAILED snapshot; 0 rollback
│
├── frontend/web/src/app/portfolio/
│   ├── portfolio-valuation.service.ts (+ spec)   # NEW — GET /api/portfolios/:id/valuation
│   ├── portfolio.models.ts                       # + PortfolioValuationView / PositionValuationView / SectorAllocationView / ValuationStatus
│   ├── portfolio-detail.page.ts (+ spec)         # EXTEND — totals card + extra Position columns + sector allocation + state line
│   └── valuation-format.ts (+ spec)              # NEW — 2-dp money / percentage formatting helpers
│
└── e2e/
    ├── infrastructure … compose.e2e.yaml         # + finnhub-stub service (E2E only)
    ├── finnhub-stub/                             # NEW — canned /quote /stock/profile2 /forex/rates JSON + a tiny server (or WireMock mappings)
    ├── support/valuation.ts                      # NEW — helpers for the FD004 detail assertions
    └── tests/
        ├── fd004-valuation.spec.ts               # NEW — E2E-001
        └── fd004-provider-failure.spec.ts        # NEW — E2E-002

implementation/platform/backend/core-service/src/test/java/com/myfinaimanager/core/architecture/
└── StandardArchitectureRulesTest.java   # + 2 rules: portfolio.domain/business free of marketdata; marketdata used only from portfolio.infrastructure.marketdata → 20 rules

implementation/platform/backend/core-service/src/main/resources/application.yml   # finnhub.base-url already ${FINNHUB_BASE_URL:…}? — confirm/add the env indirection (D8)
implementation/platform/backend/core-service/pom.xml   # + JaCoCo excludes for portfolio/infrastructure/persistence/entity (already excluded by the existing pattern) — confirm the valuation entities are covered by the wildcard
implementation/platform/backend/core-service/README.md · implementation/platform/README.md   # + the FD004 valuation area / endpoint / capability row
```

**Structure Decision**: extend the existing `portfolio` backend module with a valuation area
(pure `domain` calculator + `business` service behind ports + `infrastructure` adapters), add one
read endpoint, extend the FD003 frontend detail, consume EN005 through one `portfolio` ACL port.
One new Flyway migration. No new module, no new deployable, no schema change to FD001/EN004.

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| A valuation error breaks the `POST /api/portfolios` request (rolls back / 500s creation) | Med | **High** (closure gate) | The listener wraps `value(...)` in a catch-all; the valuation service opens **no** write on `portfolio`/`position`; `PortfolioValuationOnCreationIT` asserts the Portfolio persists and a `FAILED` snapshot is written across a gateway outage; SC-005/SC-008. |
| Missing price/FX shown as a fabricated `0` | Med | **High** (closure gate) | `PositionValuation.valued` flag; monetary fields are `Optional`/nullable in domain, DTO, and DB; the frontend renders `—`/omits the column when `valued = false`; `PortfolioValuationCalculatorTest` + `fd004-provider-failure.spec.ts` assert no `0`. |
| Non-deterministic maths (float drift, division `ArithmeticException`) | Low | **High** (closure gate) | `BigDecimal` only (ArchUnit + review — SC-006); division always with an explicit scale + `RoundingMode` (OD-FD004-5); calculator is pure and unit-tested with the FD004 §26 worked numbers to the digit. |
| `portfolio.business` accidentally imports a `marketdata` type | Med | Med | The `MarketDataGateway` ACL port + adapter; **+2 ArchUnit rules**, non-vacuous, with a deliberate-violation check (temp `marketdata` import in `PortfolioValuationService` → rule fails → revert). |
| The synchronous trigger noticeably slows `POST /api/portfolios` | Med | Med | ≤ (2·N + 2) EN005 calls, each cache-served and timeout-bounded (2 s/5 s); the create still returns on failure. If a realistic Portfolio makes create too slow, the async model is a *future* FD004 revision (needs FD approval), not this slice. |
| Widening `PortfolioExceptionHandler` `assignableTypes` regresses FD001/FD003 error bodies | Low | Med | Additive `assignableTypes` entry only; the existing `400`/`404`/`503` handlers unchanged; re-run `CreatePortfolioControllerContractTest` + `PortfolioQueryControllerContractTest`. |
| E2E `finnhub-stub` drifts from the real Finnhub shape | Low | Med | The stub JSON is copied from `specs/EN005-…/contracts/finnhub-provider-contract.md` §5 (the same fixtures EN005's own tests use); EN005's contract test still guards the real shape. |
| New `V4` migration conflicts with a parallel schema change | Low | Med | `V4` is the next free version; only new tables; FK to `portfolio(id)` with `ON DELETE CASCADE`; `SchemaIntegrityIT`-style check that Hibernate does not alter it. |
| Weight/percentage rounding makes Σ ≠ 100 % beyond tolerance | Low | Med | Weights stored as exact 12-dp fractions; `PortfolioValuationCalculatorTest` asserts Σ within the display tolerance (FR-013); the E2E tolerates ±0.01 % on displayed values. |
| Detail page fetches valuation on every open (extra call) | Low | Low | One `GET …/valuation` alongside the FD003 detail call; EN005-side data is cached; SC-011 (< 2 s) is the guard. |

## Phase 0 — Research

See [research.md](./research.md). Decisions **D1–D11** cover: the post-commit event + synchronous
listener + catch-all (D1, OD-FD004-1); the `MarketDataGateway` ACL port + adapter + exception
translation (D2, OD-FD004-2); valuation code placement in the `portfolio` module (D3, OD-FD004-3);
the pure deterministic calculator — formulas, `BigDecimal` scale/rounding, the worked §26 numbers
(D4); the `ValuationStatus` transition rules per FR-015…FR-018 (D5); the latest-only persistence
schema + upsert-in-one-transaction (D6, OD-FD004-4); the valuation read endpoint + controller + 404
+ "no snapshot → PENDING" (D7, OD-FD004-6); the FX-directions-needed logic (D8, OD-FD004-8); the
frontend detail extension + 2-dp formatting + state line (D9); the E2E `finnhub-stub` service and
the two mandatory specs (D10, OD-FD004-7); the ArchUnit rules + deliberate-violation check + the
test plan (D11). No `NEEDS CLARIFICATION`.

## Phase 1 — Design & Contracts

Outputs: [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md).

**Post-Design Constitution re-check: PASS** — the design adds no framework dependency to `domain`
(the calculator is pure), no `marketdata` coupling in `portfolio` core, one Flyway migration in the
approved mechanism, a contract-first read endpoint that leaves FD003 untouched, TDD for every
deterministic piece, Testcontainers for persistence + the trigger, a stubbed EN005 boundary for
both mandatory E2E scenarios, and no LLM, no scheduler, no broker, no new deployable, no history.

---

# Revision 2 — Two mandatory allocation pie charts (2026-09-05)

**Trigger**: the FD004 Feature Definition was updated + re-signed by jaruiz (§4, §17.1–§17.4, §19,
BR-014…BR-017, AC-013…AC-015, §26 checks 11–15, §28, §29.16–§29.22) to make the Portfolio detail
display **two mandatory circular / pie charts** — *Allocation by Ticker* and *Allocation by Sector*.
Spec delta: new **US7**, `FR-044…FR-049`, `AC-013…AC-015`, `SC-014`/`SC-015`; `FR-028` "chart
optional" → mandatory; `FR-038`/`FR-039` (E2E-001 + closure gate) extended; `FR-024` clarified;
`FR-041` / Out-of-Scope forbid a new charting dependency; assumptions **A12–A14**.

## R2 Summary

**Frontend + E2E only. No backend / domain / business / persistence / API change.** The valuation
API response the FD003 detail already fetches (`GET /api/portfolios/{portfolioId}/valuation`)
already carries everything the charts need:

- **Ticker chart** — one slice per entry of `positions[]` where `valued == true` and
  `portfolioWeight != null`; slice magnitude = `Number(portfolioWeight)` (the deterministic
  `valueInEUR / totalValueEUR`); label = `ticker`.
- **Sector chart** — one slice per entry of `sectors[]`; slice magnitude = `Number(sectorWeight)`;
  label = `sector` (`Unclassified` is already its own entry when present).

Rendering: a **self-contained standalone Angular `PieChartComponent`** drawing inline **SVG** arc
paths + a legend (`label` + `xx.xx %`). **No new npm dependency** (FR-041, FR-049, A12). Both charts
appear only when valuation data with a positive EUR basis exists; otherwise the existing
valuation-state message stands in (FR-048).

**EN005 Revision 2 interaction (A14)**: EN005-R2 checkpoints C1+C2 are done → the FX provider is now
**Frankfurter**, so the E2E `finnhub-stub` (which only serves Finnhub endpoints) can no longer
produce a `COMPLETED` FD004 valuation. This plan **folds in the prerequisite**: add a
`/v1/latest` route to the E2E stub + a `FRANKFURTER_BASE_URL` env on the e2e backend. This is the
minimum for E2E-001 to pass; it also advances EN005-R2 checkpoint C4 (whose remaining work becomes
just the `finnhub-stub` → `market-data-stub` rename).

## R2 Technical Context (delta)

**Language / Runtime**: Angular 20, TypeScript 5.8, standalone components + signals (unchanged). No
backend change.

**Dependencies**: none added — `package.json` untouched (SC-012). Charts are inline SVG + existing
`valuation-format.ts` helpers. E2E: `@playwright/test` (unchanged); the stub gains one route.

**Storage / API / Contract**: **no change** — `openapi.yaml`, all backend modules, all migrations
untouched. The `PortfolioValuation` response is consumed as-is.

**Testing**: `ng test` (new `pie-chart.component.spec.ts`; extended `portfolio-detail.page.spec.ts`);
`./e2e.sh` (extended `fd004-valuation.spec.ts` + the stub route). `PieChartComponent`'s slice
geometry is deterministic ⇒ **TDD RED-first** (constitution VII). No Testcontainers (no persistence).

**Performance**: two SVGs with ≤ ~50 `<path>` elements each; well within SC-011's 2 s.

## R2 Constitution Check

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Human-Governed Source of Truth | **PASS** | Implements the re-signed FD004 §17/§29 chart requirements; the `product/` edit is the owner's (§31 re-signed). No `product/` change by this work. |
| II | Definitions Are Authoritative Intent | **PASS** | Every new FR (FR-044…FR-049) maps to an FD004 §/BR/AC. No invented behavior. |
| III | Derived Artifacts & Layout | **PASS** | Under `specs/FD004-…`; code under `implementation/platform/frontend/`. |
| IV | No Invention; Surface Material Ambiguity | **PASS** | The Feature Definition fully specifies the charts. The one technical choice (rendering) has a governed default (no new dependency ⇒ self-contained SVG). ODs below carry recommended positions. |
| V | Technical Enablers Stay Technical | **N/A** | FD004 is a Feature, not an enabler. |
| VI | Hexagonal Architecture & Deterministic Logic | **PASS** | No backend touched. The chart component is a pure view: given `slices[]` it renders deterministically; it performs **no** financial calculation (BR-016 — it consumes the backend weights). No LLM. |
| VII | Test-First for Deterministic Logic; Testcontainers by Default | **PASS** | `PieChartComponent` arc geometry + legend % are TDD'd RED-first. No new persistence ⇒ no Testcontainers need. |
| VIII | Contract-First External APIs | **PASS (N/A)** | **No API change** — `openapi.yaml` untouched; the charts read the existing `PortfolioValuation` response. |

**Repo / technology-policy quick check**: no new deployable / broker / scheduler / persistence tech
/ external provider / **npm dependency**; `openapi.yaml` + `pom.xml` + all backend code untouched;
`package.json` untouched; the E2E stub route + `FRANKFURTER_BASE_URL` are E2E-infra only (they also
unblock EN005-R2). `_tokens.scss` gains a categorical chart palette (`--chart-1…--chart-8`) — a
design-system token addition, not a dependency (the tokens file is explicitly "refined as real UI
is built"). **Result: PASS.**

## R2 Open Decisions (technical) — recommended positions

| ID | Decision | Recommended | Rejected |
|---|---|---|---|
| OD-R2-1 | Chart rendering tech | **Standalone `PieChartComponent`, inline SVG arc `<path>` slices + HTML legend.** Full control of labels, a11y (`role="img"` + `aria-label`), per-slice title; zero dependency. | `conic-gradient` CSS — no per-slice label/hit target, hard to test geometry. `ngx-charts` / `chart.js` / `d3` — a new npm dependency, forbidden by FR-041/FR-049. |
| OD-R2-2 | Component location | **`frontend/web/src/app/portfolio/pie-chart.component.ts`** (feature-local — only FD004 uses it today). | `src/app/shared/` — premature; promote when a 2nd feature needs it. |
| OD-R2-3 | Pie vs donut | **Solid pie** with a thin `--color-surface` stroke between slices (the Feature Definition says "pie chart"; no donut precedent in the design system). | Donut — cosmetic, adds a hole-radius knob with no requirement. |
| OD-R2-4 | Slice colours | **A fixed ordered palette `--chart-1…--chart-8` added to `_tokens.scss`** (dark-theme categorical ramp), assigned by slice index, cycling if > 8. Same palette used independently by each chart. Colour is never the only signal — the legend carries label + %. | Hashing the label to a colour — unstable, can collide to near-identical hues. |
| OD-R2-5 | "Show charts" predicate | **Show both iff** `valuation` present **and** `status ∈ {COMPLETED, PARTIAL}` **and** `totalValueEUR` present & `> 0` (⇔ ≥ 1 ticker slice with `fraction > 0`). Else render **neither** — the valuation-state line stands (FR-048). | Always render (empty circle for FAILED/PENDING) — violates FR-048 "no empty/fabricated chart". |
| OD-R2-6 | E2E FX stub | **Extend the existing `e2e/finnhub-stub/server.js` with `/v1/latest`** + set `FRANKFURTER_BASE_URL` on the e2e backend. Minimal; keeps the `finnhub-stub` name for now. | Do the full EN005-R2 C4 rename (`finnhub-stub` → `market-data-stub`) here — mixes two workstreams; EN005-R2 C4 keeps the rename. |
| OD-R2-7 | Slice ordering | **Descending fraction** for both charts (largest slice first, clockwise from 12 o'clock). The sector list from the API is already descending by EUR value; the ticker list gets sorted client-side for the chart (does not reorder the Position table). | API order for tickers — Position table order is by creation, visually noisy in a pie. |
| OD-R2-8 | PARTIAL rendering | The valued Positions' `portfolioWeight` values already sum to ~1 over the valued set (they are `valueInEUR / totalValueEUR`), so the PARTIAL pie is "full" but represents only the valued portion; the **partial-state message** (already present) is the "incomplete" signal (FR-048, §17.3). No extra "unvalued" slice. | A grey "unvalued / N positions" slice — the feature def says charts show "only the successfully valued portion". |

## R2 Project Structure (delta)

```text
specs/FD004-portfolio-valuation-and-allocation/
├── plan.md              # this addendum
├── research.md          # + "Revision 2 — allocation charts" section (D-chart-1…6)
├── data-model.md        # + "Allocation chart view-models" section (frontend-only, no persistence/API)
├── quickstart.md        # + chart validation steps (H) + the E2E stub prerequisite
└── tasks.md             # Phase 10 — /speckit-tasks (NOT this command)

implementation/platform/frontend/web/src/
├── styles/_tokens.scss                         # + --chart-1 … --chart-8 (dark categorical palette)
└── app/portfolio/
    ├── pie-chart.component.ts (+ .spec.ts)     # NEW — standalone; @Input slices[] + title; inline SVG + legend; a11y
    ├── portfolio.models.ts                     # + AllocationSlice { label; fraction } (view-model)
    ├── valuation-format.ts                     # reuse percent(); + a slice-builder helper if useful
    ├── portfolio-detail.page.ts (+ .spec.ts)   # EXTEND — two <app-pie-chart>; tickerSlices()/sectorSlices()/showCharts(); layout (grid, stacks on narrow)

implementation/platform/e2e/
├── finnhub-stub/server.js                      # + GET /v1/latest?base=&symbols=  (Frankfurter shape; USD→EUR 0.80, EUR→USD 1.25) — prerequisite (A14)
├── support/valuation.ts                        # + helpers: pie chart present? slice labels+percentages?
└── tests/fd004-valuation.spec.ts               # EXTEND — assert both charts visible, slice %s, consistency (E2E-001 checks 11–15)

implementation/platform/infrastructure/local/compose.e2e.yaml   # + FRANKFURTER_BASE_URL: http://finnhub-stub:8080 on backend
implementation/platform/README.md · backend/core-service/README.md   # note the two mandatory charts
```

**Structure Decision**: one new feature-local standalone component + an extension of the existing
`portfolio-detail.page.ts`, both driven entirely by the valuation response already fetched. The E2E
stub gains one route so E2E-001 can reach `COMPLETED` after the EN005-R2 FX-provider swap.

## R2 Risk Register (delta)

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Chart % drifts from the table numbers (independent rounding) | Med | High (BR-016, closure gate) | The component receives the **backend fraction** and formats with the same `percent()` helper the table uses; `portfolio-detail.page.spec.ts` asserts chart legend % === table weight %; E2E-001 asserts both. |
| SVG arc math wrong for the 1-slice / ~100 % case (`largeArcFlag`) | Med | Med | `pie-chart.component.spec.ts` covers 1 slice (full circle), 2 slices, > 2, and a slice > 50 % (`largeArcFlag = 1`); RED-first. |
| Charts rendered for FAILED/PENDING/no-EUR-basis (empty circle) | Med | Med (FR-048) | `showCharts()` predicate (OD-R2-5); spec asserts the chart element is **absent** from the DOM for those states (SC-015). |
| `./e2e.sh` still can't produce `COMPLETED` (Frankfurter not stubbed) | High (already true) | High (E2E-001 gate) | The stub `/v1/latest` route + `FRANKFURTER_BASE_URL` are **Phase-1 prerequisite tasks**, done and verified (`./e2e.sh` green) before the chart E2E assertions are added. |
| New `--chart-*` tokens clash with the design system | Low | Low | Added to `_tokens.scss` (the sanctioned token file); dark categorical ramp; legend always carries text so colour is not the only signal (design-system rule). |
| Accessibility regression (SVG not announced) | Low | Med | `role="img"` + `aria-label` summarising the top slices; legend is real text; `ng test` asserts the `aria-label`. |

## R2 Phase 0 / Phase 1 pointers

- **Phase 0 — Research**: [research.md](./research.md) → "Revision 2 — allocation charts"
  (D-chart-1 SVG pie geometry; D-chart-2 the two slice sources + `showCharts` predicate; D-chart-3
  colour palette tokens; D-chart-4 a11y + responsive layout; D-chart-5 E2E stub `/v1/latest`
  prerequisite; D-chart-6 test matrix). No `NEEDS CLARIFICATION`.
- **Phase 1 — Design**: [data-model.md](./data-model.md) "Allocation chart view-models" (the
  `AllocationSlice` view-model + the two derivations — no persistence, no API); [quickstart.md](./quickstart.md)
  section H (chart validation) + the E2E prerequisite.

**Post-Design Constitution re-check: PASS** — frontend-only, deterministic view component (TDD),
no dependency, no API/contract/schema change, no `product/` edit, both mandatory E2E gates covered
(E2E-001 extended, E2E-002 unchanged), and the EN005-R2 E2E prerequisite folded in explicitly.

---

## Revision 2.1 — Portfolio-detail table trim (2026-09-04)

Owner request, within the FR-029 "MAY be extended" latitude — a display refinement, **not** a scope
change. In the `/portfolios/:id` detail:

1. **Remove the native "Market value" column** — `quantity × price` is redundant with the
   `Value (EUR)` / `Value (USD)` columns; the useful native figure is the price.
2. **"Market price" now shows the Position's native currency** — `priceOf()` returns
   `"<decimal2(price)> <nativeCurrency>"` (e.g. `200.00 USD`) when the Position is valued, `—`
   otherwise. Lets the reader tell EUR from USD prices without another column.
3. **Remove the standalone "Sector allocation" list** — the mandatory **Allocation by Sector** pie
   chart's legend already lists every sector + its percentage (FR-028). The `sectors()` accessor
   stays (it feeds `sectorSlices()` → the chart); only the `<section class="sectors">` template
   block, its styles, and the `pct()` helper are removed.

**Scope**: `portfolio-detail.page.ts` (+ its spec), `e2e/tests/fd004-valuation.spec.ts`,
`e2e/support/valuation.ts`, the two READMEs, and these `specs/FD004-…` docs. No backend Java, no
`openapi.yaml`, no `pom.xml`, no `package.json`, no migration, no `product/` / `.specify/` edit.
The `PortfolioValuation` API response is unchanged — it still carries `nativeMarketValue`; the
frontend simply stops rendering it.

**Constitution re-check: PASS** — same rationale as Revision 2; deterministic weights still the
sole allocation source, TDD preserved (`portfolio-detail.page.spec.ts` updated RED→GREEN), both
E2E gates still covered.
