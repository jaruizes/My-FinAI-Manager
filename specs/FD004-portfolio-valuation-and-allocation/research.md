# Phase 0 — Research: Portfolio Valuation & Allocation (FD004)

**Feature dir**: `specs/FD004-portfolio-valuation-and-allocation/` · **Plan**: [plan.md](./plan.md) · **Spec**: [spec.md](./spec.md)

All FD004 §30 material decisions are **human-approved** (FD004 §31 signed 2026-09-04). This document
resolves the **technical** planning decisions the spec left to planning (spec §"Resolved Product
Decisions" tail + Assumptions A4/A6/A7/A8/A10) and records the design rationale per decision. No
`NEEDS CLARIFICATION` remains.

---

## D1 — Post-commit valuation trigger

**Decision.** `CreatePortfolioService.create(...)`, after `repository.save(candidate, key)` returns
**and only when `!replayed`** (a genuine first creation, mirroring the existing
`recordBusinessOutcomes` guard), publishes a Spring **`PortfolioCreatedEvent(PortfolioId)`** via an
injected `ApplicationEventPublisher`. A **synchronous** `@EventListener` —
`PortfolioValuationOnCreationListener` in `portfolio.infrastructure.valuation` — calls
`ValuePortfolioUseCase.value(portfolioId)` inside a **catch-all** `try/catch (Exception)` that logs
`event=PortfolioValuationFailed portfolioId=… reason=…` (structured, no secrets) and returns
normally. `POST /api/portfolios` still returns `201` with the FD001 body.

**Rationale.**
- FR-004 requires the valuation to run **synchronously, after the create transaction commits, in
  the same request**. `CreatePortfolioService.create` is **not** `@Transactional`; `repository.save`
  commits through its own `TransactionTemplate` before returning. So by the time the service
  publishes, the Portfolio is durably committed — a plain synchronous `@EventListener` on the
  request thread satisfies "after commit" without `@TransactionalEventListener`.
- Spring's default `@EventListener` invocation is **synchronous** and **in the caller's thread** —
  no `@Async`, no `TaskExecutor`, no broker, no scheduler (FR-003).
- The event keeps FD001 coupling to one line (publish) + one dependency (`ApplicationEventPublisher`)
  and puts the valuation orchestration in `portfolio.infrastructure`, not in the controller and not
  in the create domain/business path. The listener depends on `business`; the publisher depends only
  on a Spring core interface (allowed in `business` — it is not a persistence/HTTP/provider type;
  ArchUnit `business_does_not_depend_on_spring_web_or_data` still passes because
  `org.springframework.context.ApplicationEventPublisher` is context, not web/data. **Confirm the
  existing ArchUnit rule wording during implementation**; if it forbids all `org.springframework..`
  in `business`, fall back to publishing from a thin `portfolio.infrastructure` wrapper around the
  create controller — see Alternative 2).
- The catch-all is the FR-002 / SC-005 / SC-008 guarantee: a valuation exception cannot reach the
  create request, cannot roll back the (already-committed) create, and the valuation service itself
  opens no write on `portfolio`/`position`.

**Alternatives rejected.**
1. `@TransactionalEventListener(phase = AFTER_COMMIT)` — there is **no ambient transaction** around
   `create()` at publish time (the save's template already committed), so the listener would be
   bound to no transaction and (with default `fallbackExecution = false`) **silently not fire**.
2. An explicit call from a new `portfolio.infrastructure.api.rest` wrapper / an interceptor after
   the controller returns — viable and keeps `business` Spring-free, but spreads creation knowledge
   into the transport layer; kept as the fallback if ArchUnit forbids the publisher in `business`.
3. `@Async` / `ApplicationRunner` queue / Kafka / `@Scheduled` sweep — all violate FR-003 / FR-004
   (must be synchronous, in-request, no broker, no scheduler).

**Trigger contract:** on `PortfolioCreatedEvent` → `value(portfolioId)` runs to completion (writing
a `COMPLETED` / `PARTIAL` / `FAILED` snapshot) **or** throws, in which case the listener logs and
best-effort writes a `FAILED` snapshot (a second, independent `try/catch`), then returns. Either
way the HTTP response is the unmodified FD001 `201`.

---

## D2 — `portfolio` → EN005 access: one ACL port (AR-062)

**Decision.** A single **published port** in the consumer module:

```text
portfolio.domain.ports.MarketDataGateway
  Optional<PositionPricing>  latestPrice(String ticker, String market, String currencyCode)
  Optional<String>           sector(String ticker, String market, String currencyCode)
  Optional<FxConversion>     fxRate(String fromCurrencyCode, String toCurrencyCode)
```

with small `portfolio.domain.model` read records `PositionPricing(BigDecimal price, Instant
observedAt)` and `FxConversion(BigDecimal rate, Instant observedAt)`. Implemented by
`EnMarketDataGatewayAdapter` in **`portfolio.infrastructure.marketdata`** — the **only** class in
the `portfolio` module that imports `com.myfinaimanager.core.marketdata.*`. It:
- builds EN005's `InstrumentIdentifier` / `SupportedCurrency` from the primitive args,
- calls `MarketDataPort` / `InstrumentProfilePort` / `FxRatePort`,
- maps `MarketPrice`→`PositionPricing`, `InstrumentProfile.sector`→`Optional<String>`
  (`Sector.UNCLASSIFIED` → `Optional.empty()`), `FxRate`→`FxConversion`,
- **catches every `MarketDataException` subtype** (and `SupportedCurrency` parse failure) and
  returns `Optional.empty()` — an unavailable-data signal the domain understands. It logs each
  unavailable outcome at DEBUG with a reason code; it never rethrows a provider type.

**Rationale.** AR-062 — inter-module reads go through the **owning consumer's** published port,
invoked **only from `infrastructure`**. `portfolio.business` / `portfolio.domain` never see a
`marketdata` type (FR-026, FR-035, SC-007), so Finnhub cannot leak into the valuation domain or the
API. `Optional.empty()` collapses EN005's 7-member exception hierarchy into the one distinction
FD004's status rules care about: *was this input available?* Primitive `String` args keep
`marketdata` enums out of `portfolio.domain`.

**Alternatives rejected.** Three separate ports (`PricingGateway` / `SectorGateway` / `FxGateway`) —
one seam is enough and they are always used together. `portfolio.business` importing
`marketdata.domain.ports` directly — breaks AR-062. A new shared `marketdata` façade module —
not `portfolio`'s to define; also an EN005 change (FR-033).

---

## D3 — Valuation code placement: inside the `portfolio` module

**Decision.** Add a **valuation area to the existing `portfolio` module** (not a new module):

```text
portfolio/domain/model/       PortfolioValuation, PositionValuation, SectorAllocation, ValuationStatus, ValuationFreshness
portfolio/domain/model/       PortfolioValuationCalculator          (PURE — no ports, no Spring)
portfolio/domain/ports/       MarketDataGateway, PortfolioValuationRepository
portfolio/domain/events/      PortfolioCreatedEvent
portfolio/business/           ValuePortfolioUseCase, PortfolioValuationService
portfolio/infrastructure/marketdata/    EnMarketDataGatewayAdapter
portfolio/infrastructure/valuation/     PortfolioValuationOnCreationListener
portfolio/infrastructure/persistence/   *ValuationEntity, PortfolioValuationJpaRepository, PortfolioValuationPersistenceAdapter, mapper
portfolio/infrastructure/api/rest/      PortfolioValuationController, dto/PortfolioValuationResponse, mapper/…
```

**Rationale.** A valuation *is* a Portfolio fact; FD004's payoff is shown in the FD003 Portfolio
detail; the valuation reads `Portfolio`/`Position` aggregates the module already owns (FR-023). ADR-003
is module-first with `domain / business / infrastructure/{api,persistence,client,<other-adapter>}` —
the new packages slot into that. `architecture.md` shows an *illustrative* `valuation` module, but
FD004 is a small Portfolio-centric slice; a second module would create a second owner of Portfolio
identity and cross-module reads for data that is already in-module. FR-041 forbids no *module* — it
forbids a new *deployable* — so this is compliant. Revisit a dedicated module if valuation later
grows history / benchmarks / scheduled runs (all currently out of scope, FR-040).

**Alternatives rejected.** New `valuation` module — ceremony + a cross-module read of
`Portfolio`/`Position` (would itself need an AR-062 port back into `portfolio`). Putting the
calculator in `business` — it is pure and deterministic; `domain` is the correct home and keeps it
trivially unit-testable (constitution VI).

---

## D4 — Deterministic calculator: formulas, types, rounding

**Decision.** `PortfolioValuationCalculator` is a **pure function**:

```text
PortfolioValuation calculate(
    PortfolioId portfolioId,
    List<PositionInput> positions,     // ticker, market, quantity(BigDecimal), nativeCurrency("EUR"|"USD"),
                                       // Optional<BigDecimal> price, Optional<Instant> priceObservedAt, Optional<String> sector
    FxContext fx,                      // Optional<BigDecimal> usdToEur, Optional<BigDecimal> eurToUsd, Optional<Instant> fxObservedAt
    Instant calculatedAt)
```

Everything is **`BigDecimal`**; **no `double`/`float`** anywhere on the type (SC-006).

Per Position, when `price` is present:
- `nativeMarketValue = quantity.multiply(price)` — exact, no rounding.
- USD Position: `valueInUSD = nativeMarketValue`; `valueInEUR = nativeMarketValue.multiply(usdToEur)`
  **iff `usdToEur` present**, else absent.
- EUR Position: `valueInEUR = nativeMarketValue`; `valueInUSD = nativeMarketValue.multiply(eurToUsd)`
  **iff `eurToUsd` present**, else absent.
- `sector` = the provided string, or the literal `"Unclassified"` when absent (FR-011, FR-014).
When `price` is absent → `PositionValuation` with `valued = false` and **all** monetary fields
`null`/absent (never `BigDecimal.ZERO` — FR-017).

Totals (over valued Positions only, FR-007):
- `totalValueEUR` = Σ `valueInEUR` **iff every valued Position has a `valueInEUR`**, else the EUR
  total is absent (a valued USD Position with no FX ⇒ no EUR total).
- `totalValueUSD` = Σ `valueInUSD` under the mirror condition.
- Multiplication/addition are exact; **no scale is imposed on stored money** (matches FD001 `NUMERIC`
  round-trip). The API returns the stored value; **display rounding to 2 dp is the frontend's job**
  (FR-031, spec Edge Cases "Display precision").

Weights (FR-009, FR-012), only when `totalValueEUR` is present **and > 0**:
- `position.portfolioWeight = valueInEUR.divide(totalValueEUR, 12, RoundingMode.HALF_UP)` — a
  fraction with 12-dp headroom (an explicit scale is mandatory: the quotient is generally
  non-terminating).
- Per sector: `sectorValueEUR = Σ valueInEUR` of that sector's valued Positions (exact);
  `sectorWeight = sectorValueEUR.divide(totalValueEUR, 12, HALF_UP)`.
- A valued Position with no `valueInEUR` (USD, no FX) is **excluded from weights and sector
  allocation** (weights need the canonical EUR basis) but still counts toward `totalValueUSD`; its
  presence forces status `PARTIAL`.
- If `totalValueEUR` is absent or `0` → **no** weights, **no** sector allocation rows are emitted
  (spec Edge Case "Zero total EUR"); never `0 %`/`NaN` (FR-019).

**Rationale.** `BigDecimal` end-to-end is the FD001/EN005 convention and the only decimal-safe
option (FR-008). Keeping stored money **unscaled** and rounding only on display honours FD004 §11
("only display-rounding differences") and §30.3–§30.4, and lets SC-001 assert **exact** stored
totals (`2100.00` = `2100`, compared with `compareTo`). 12-dp weight fractions make Σ-weights match
100 % to far beyond the 2-dp display tolerance (SC-003, FR-013). `HALF_UP` is the common financial
half-rounding and is only ever applied to the non-exact division.

**Worked example (E2E-001 / SC-001 / SC-003).** `AAPL` qty 10 @ 200 USD (Technology); `SAN` qty 100
@ 5 EUR (Financial Services); `usdToEur = 0.80`, `eurToUsd = 1.25`.
- AAPL: native `2000` USD → `valueInUSD 2000`, `valueInEUR 2000×0.80 = 1600`.
- SAN: native `500` EUR → `valueInEUR 500`, `valueInUSD 500×1.25 = 625`.
- `totalValueEUR = 1600 + 500 = 2100.00`; `totalValueUSD = 2000 + 625 = 2625.00`.
- AAPL weight `1600/2100 = 0.761904761905`; SAN `500/2100 = 0.238095238095`.
- Sectors: `Technology 1600 / 2100 → 76.19 %`; `Financial Services 500 / 2100 → 23.81 %`.
- Displayed (2 dp): totals `€2,100.00` / `$2,625.00`; weights `76.19 %` / `23.81 %` (Σ `100.00 %`).

**Alternatives rejected.** Rounding money to 2 dp in storage — Σ then drifts from the exact total and
SC-001's "exactly, no precision loss" fails. Deriving `eurToUsd = 1/usdToEur` — EN005 kept the two
directions independent (OD-EN005-7); FD004 does not reintroduce inversion (it would also make
`0.80`↔`1.25` a lucky coincidence rather than two fetched rates). `MathContext` instead of an
explicit divide-scale — less predictable digit count across JVMs.

---

## D5 — `ValuationStatus` transition rules (FR-015…FR-018)

**Decision.** Terminal status from one `calculate(...)` run, given `Vp` = # Positions, `Vv` = #
valued (price present), and the totals:

| Condition | Status |
|---|---|
| `Vv == 0` (no Position could be priced) | **`FAILED`** |
| `Vv > 0` **and** neither `totalValueEUR` nor `totalValueUSD` producible | **`FAILED`** |
| `Vv == Vp` **and** both totals producible **and** every valued Position has both `valueInEUR` and `valueInUSD` (sector may be missing) | **`COMPLETED`** |
| otherwise (some Position unvalued, or a needed FX rate missing, or only one total producible) | **`PARTIAL`** |

`PENDING` is **never produced by the calculator** — it is only the stored state for the window
before the synchronous listener writes a result, and a valid `GET …/valuation` response when no
snapshot row exists yet (D7). A missing **sector only** never lowers `COMPLETED` (FR-016).

**Rationale.** Directly encodes FD004 §30.2 / FR-018: `FAILED` is reserved for "nothing usable";
everything partial-but-useful is `PARTIAL`, including the same-currency-Portfolio case where the
cross-currency total's FX rate is missing (spec Edge Case "FX for a same-currency Portfolio").

**Alternatives rejected.** `FAILED` when *any* Position is unvalued — contradicts FR-018 (that is
`PARTIAL`). A distinct `NO_DATA` status — FD004 §6 fixes the set at exactly four values.

---

## D6 — Persistence: latest-only schema + one-transaction upsert

**Decision.** New Flyway forward migration **`V4__portfolio_valuation.sql`** (V1 baseline, V2 FD001,
V3 EN004 exist → V4 is next), owned by the `portfolio` module, **no change to `portfolio` /
`position` / EN004 tables** (FR-022, FR-023, SC-012):

```sql
CREATE TABLE portfolio_valuation (
    id                  UUID PRIMARY KEY,
    portfolio_id        UUID NOT NULL UNIQUE REFERENCES portfolio(id) ON DELETE CASCADE,
    status              TEXT NOT NULL CHECK (status IN ('PENDING','COMPLETED','PARTIAL','FAILED')),
    calculated_at       TIMESTAMPTZ NOT NULL,
    total_value_eur     NUMERIC,            -- nullable: absent when not producible
    total_value_usd     NUMERIC,
    market_data_as_of   TIMESTAMPTZ,
    fx_data_as_of        TIMESTAMPTZ
);
CREATE TABLE position_valuation (
    id                    UUID PRIMARY KEY,
    portfolio_valuation_id UUID NOT NULL REFERENCES portfolio_valuation(id) ON DELETE CASCADE,
    ticker                TEXT NOT NULL,
    market                TEXT NOT NULL,
    quantity              NUMERIC NOT NULL,
    valued                BOOLEAN NOT NULL,
    native_currency       CHAR(3) NOT NULL,
    market_price          NUMERIC,          -- all nullable: absent for an unvalued Position (never 0)
    native_market_value   NUMERIC,
    value_eur             NUMERIC,
    value_usd             NUMERIC,
    portfolio_weight      NUMERIC,          -- exact fraction (scale 12); absent when no EUR basis
    sector                TEXT NOT NULL,    -- classification string or 'Unclassified'
    price_observed_at     TIMESTAMPTZ
);
CREATE TABLE sector_allocation (
    id                    UUID PRIMARY KEY,
    portfolio_valuation_id UUID NOT NULL REFERENCES portfolio_valuation(id) ON DELETE CASCADE,
    sector                TEXT NOT NULL,
    sector_value_eur      NUMERIC NOT NULL,
    sector_weight         NUMERIC NOT NULL  -- exact fraction (scale 12)
);
CREATE INDEX ix_position_valuation_parent ON position_valuation(portfolio_valuation_id);
CREATE INDEX ix_sector_allocation_parent  ON sector_allocation(portfolio_valuation_id);
```

`portfolio_id UNIQUE` **is** the latest-only guarantee (FR-020). `PortfolioValuationPersistenceAdapter.upsertLatest(PortfolioValuation)`
runs in **one** write transaction (its own `TransactionTemplate`, matching the FD001 adapter
pattern): `DELETE FROM portfolio_valuation WHERE portfolio_id = ?` (children cascade), then insert
the new parent + children. This is on the **valuation** tables only — zero writes to
`portfolio`/`position` (SC-008). JPA entities live under
`portfolio/infrastructure/persistence/entity/**` (already a JaCoCo-excluded package — confirm the
new entities match the existing wildcard; add `portfolio/infrastructure/persistence/entity/**` if
the current exclude is narrower). `spring.jpa.hibernate.ddl-auto` stays `none`.

**Rationale.** FD004 §12 / §30.6 mandate latest-only, no history (FR-020, FR-040). A `UNIQUE`
column + delete-then-insert is the simplest correct idempotent replace (FR-021, SC-002) and avoids
partial-update bugs across the parent/child graph. `ON DELETE CASCADE` from `portfolio(id)` also
means deleting a Portfolio (not in FD004 scope, but future-safe) cleans its valuation. `NUMERIC`
with no precision/scale = exact round-trip, identical to FD001's money columns.

**Alternatives rejected.** History table + `is_latest` flag — FD004 §12 forbids retention. A single
JSON column — loses queryability and the provider-neutral shape guarantee (FR-027). `MERGE`/upsert
SQL — Flyway/Postgres supports it, but delete-then-insert is clearer for a parent+2-children graph
and the volume is tiny.

---

## D7 — Valuation read endpoint: controller, 404, "no snapshot → PENDING"

**Decision.** **Contract-first** — add to `implementation/platform/contracts/openapi/openapi.yaml`
(OpenAPI **3.0.3**, matching `swagger-request-validator`):

```text
GET /api/portfolios/{portfolioId}/valuation
  200 → PortfolioValuation
  400 → Problem   (portfolioId not a UUID — framework-generated)
  404 → Problem   (unknown / not-current-investor portfolio; type=/problems/portfolio-not-found)
```

A **new** `PortfolioValuationController` (`@RestController`, separate from the FD003
`PortfolioQueryController`); `{portfolioId}` typed `UUID` ⇒ Spring returns `400` for a non-UUID
(same mechanism FD003 uses). Flow: resolve the current Investor (`DefaultInvestorProvider`) →
`PortfolioRepository.findByIdForInvestor(portfolioId, investorId)` (FD003's method); **absent ⇒
throw `PortfolioNotFoundException`** (FD003's exception) → `404` `/problems/portfolio-not-found` via
`PortfolioExceptionHandler` (widen its `assignableTypes` to include
`PortfolioValuationController.class`). Portfolio exists → `PortfolioValuationRepository.findByPortfolioId(portfolioId)`:
- **present** ⇒ map to `PortfolioValuationResponse` (the stored snapshot, any of the four statuses).
- **absent** ⇒ return a synthetic `200` body `{ "portfolioId": …, "status": "PENDING", "positions":
  [], "sectors": [], all monetary fields null }` (FR-025, A7). Not `404` (the Portfolio exists), not
  a fabricated `COMPLETED`.

New `PortfolioValuationResponse` DTO (record) + `PortfolioValuationResponseMapper` (domain →
DTO). **FD003's `Portfolio` schema and `GET /api/portfolios[/{id}]` are byte-unchanged** — the
diff is purely additive (SC-012).

**Rationale.** FD004 §30.5 fixes the endpoint shape. Reusing `PortfolioNotFoundException` +
`findByIdForInvestor` keeps the `404` body identical to FD003's and requires no new persistence
method. Because valuation is synchronous in the create request, a snapshot almost always exists by
the time the detail page loads; the `PENDING` synthetic body covers the narrow race and a future
manual/async revalue without a special error code.

**Alternatives rejected.** Adding the operation to `PortfolioQueryController` — mixes the FD003 read
concern with FD004; a separate controller keeps `assignableTypes` and tests clean. `404` for "no
snapshot" — conflates "no such Portfolio" with "not valued yet" (FR-025 explicitly wants an
explicit `PENDING`). `202 Accepted` + polling — there is no async job to poll.

---

## D8 — Which FX rates to fetch; EN005 base-url override for E2E

**Decision — FX directions.** `PortfolioValuationService` inspects the Portfolio's Position
currencies and asks `MarketDataGateway.fxRate(...)` only for what the totals need:
- any **USD** Position present ⇒ fetch `USD→EUR` (for those Positions' EUR value + the EUR total),
- any **EUR** Position present ⇒ fetch `EUR→USD`,
- a **single-currency** Portfolio still fetches the **one** other-direction rate so the second total
  can be produced; if it is unavailable, that total is absent and status is `PARTIAL` (spec Edge
  Case "FX for a same-currency Portfolio").

`fxDataAsOf` = the **earliest** `observedAt` among the FX rates actually used (most conservative
freshness). `marketDataAsOf` = the **earliest** `priceObservedAt` among valued Positions.

**Decision — E2E base URL.** EN005's `FinnhubProperties.baseUrl` binds
`${FINNHUB_BASE_URL:https://finnhub.io/api/v1}` in `application.yml` (confirm the exact default
during implementation; EN005 added the `finnhub:` block). `compose.e2e.yaml` sets, on the backend
service, `FINNHUB_API_KEY=e2e-stub` and `FINNHUB_BASE_URL=http://finnhub-stub:8080` so EN005's
**real** `FinnhubRestClient` → adapters → mappers → cache → status-translation all execute against
the stub. **No production default changes** (SC-012); `compose.yaml` (local `./start.sh`) is
untouched — local dev still starts with a blank key and logs `FinnhubIntegrationDisabled`.

**Rationale.** Fetching only needed directions avoids one wasted call for single-currency
Portfolios without affecting correctness. Driving the stub through `FINNHUB_BASE_URL` exercises the
entire EN005 code path (FD004 §25: "backend, business calculations, application persistence, and
PostgreSQL are all real; only the Finnhub boundary is controlled") rather than replacing EN005 with
a fake bean.

**Alternatives rejected.** Always fetch both directions — one harmless wasted call; avoidable.
Profile-guarded `@Primary` fake `MarketDataPort` beans in `src/main` — puts test doubles in
production code and bypasses EN005's real adapter/cache/error paths (weaker E2E).

---

## D9 — Frontend: FD003 detail extension, 2-dp formatting, state line

**Decision.** In `implementation/platform/frontend/web/src/app/portfolio/`:
- **`portfolio-valuation.service.ts`** — `getValuation(id: string): Observable<PortfolioValuationView
  | 'not-found' | null>`; `GET /api/portfolios/:id/valuation`; `404` → `'not-found'`; other error →
  `null` (the detail then shows "Valuation unavailable"). Mirrors `portfolio-query.service.ts` (FD003).
- **`portfolio.models.ts`** — add `ValuationStatus`, `PortfolioValuationView`, `PositionValuationView`,
  `SectorAllocationView` (fields nullable exactly as the API).
- **`portfolio-detail.page.ts`** (FD003) extended, additively:
  - a **totals card**: `Total value` `€ x,xxx.xx` and `$ x,xxx.xx` (omitted / dashed when the
    respective total is null),
  - the Position table gains `Market Price`, `Market Value`, `Value in EUR`, `Value in USD`,
    `Portfolio Weight` columns — each cell renders the 2-dp value when the row is `valued` and the
    field is present, else `—`; a `Sector` column showing the string or `Unclassified`,
  - a **Sector allocation** block: one row per `SectorAllocationView` — `sector` + `xx.xx %`
    (a bar/chart is optional, FR-028),
  - a **valuation-state line** driven by `status` + counts:
    `PENDING` → "Valuation pending"; `COMPLETED` → "Valued at {calculatedAt|date}"; `PARTIAL` →
    "Partial valuation — market data unavailable for {N} position(s)" (N = Positions with `valued =
    false`); `FAILED` / `null` → "Valuation unavailable".
  - **no** input, button, or editable control on any valuation figure (FR-032).
- **`valuation-format.ts`** — `money(amount: string | null, currency: 'EUR'|'USD')` and
  `percent(fraction: string | null)` → 2-dp strings using `Intl.NumberFormat`; `null` → `'—'`.
- Design system: dark compact table, right-aligned numeric columns, existing typography (FR-031).

**Rationale.** FD004 §17–§18 + AC-012. Keeping the FD003 always-on columns (ticker/market/quantity/
currency) and adding valuation columns as "present-only" is exactly FR-029 and prevents fabricated
zeros (FR-019, FR-030). 2-dp formatting lives in one helper so E2E can assert display values
precisely while the API/stored values stay full-precision.

**Alternatives rejected.** A separate `/portfolios/:id/valuation` route/page — FD004 §17 says
*extend the detail*. Formatting in each template binding — duplication; a helper is testable.

---

## D10 — E2E: `finnhub-stub` service + the two mandatory specs

**Decision.** Add a **`finnhub-stub`** service to `implementation/platform/e2e/…/compose.e2e.yaml`
(the EN002 `!override`): a tiny committed HTTP server (≈ a Node/Express or `http`-module script, or
a `wiremock/wiremock` image with committed `mappings/`) that serves canned JSON for the three
Finnhub endpoints EN005 calls:
- `GET /quote?symbol=…` → `{ "c": <price>, "t": <epoch> }` keyed by symbol (`AAPL`, `SAN.MC`, …),
- `GET /stock/profile2?symbol=…` → `{ "finnhubIndustry": "<sector>" }`,
- `GET /forex/rates?base=USD` / `?base=EUR` → `{ "quote": { "EUR": 0.80 } }` / `{ "quote": { "USD":
  1.25 } }`.
The canned bodies are copied from `specs/EN005-…/contracts/finnhub-provider-contract.md` §5 (the
same fixture shapes EN005's own mapper tests use) so the stub cannot drift from the real provider
shape. Two stub "modes" (via env var or two mapping sets): **normal** (E2E-001 values) and
**degraded** (`429` + empty bodies for E2E-002).

Backend e2e env: `FINNHUB_API_KEY=e2e-stub`, `FINNHUB_BASE_URL=http://finnhub-stub:8080`.

Specs in `implementation/platform/e2e/tests/`:
- **`fd004-valuation.spec.ts` (E2E-001)** — depends on a fresh DB; create the Portfolio
  `AAPL`(NASDAQ/`XNAS`, USD, qty 10) + `SAN`(BME/`XMAD`, EUR, qty 100) via the FD001 UI/API helper
  (`support/portfolios.ts` from FD003 + a new `support/valuation.ts`); open the FD003 detail; assert:
  creation succeeded and the Portfolio is in the Home list (FD003); totals card shows `€2,100.00`
  and `$2,625.00`; AAPL row → price `200.00`, market value `2,000.00`, value EUR `1,600.00`, value
  USD `2,000.00`, weight `76.19 %`, sector `Technology`; SAN row → `5.00` / `500.00` / `500.00` /
  `625.00` / `23.81 %` / `Financial Services`; sector allocation `Technology 76.19 %`,
  `Financial Services 23.81 %`; state line "Valued at …".
- **`fd004-provider-failure.spec.ts` (E2E-002)** — stub in degraded mode; create a one-Position
  Portfolio; assert the Portfolio is persisted and in the Home list; the detail shows a
  valuation-state message (`PARTIAL`/`FAILED`/unavailable per the rules) and **no** `0.00` / `0 %`
  in any valuation cell or total.

The existing FD001/FD002/FD003 + `platform-smoke` specs stay green; no spec reaches `finnhub.io`
(SC-010).

**Rationale.** FD004 §25–§27 + FR-038 make both specs mandatory closure gates and require the real
containerized stack with only the Finnhub boundary controlled. A stub *service* (not a bean
override) keeps EN005's adapter/mapper/cache/error path in the assertion.

**Alternatives rejected.** Live Finnhub in CI — forbidden (FR-039, SC-010). Playwright
`page.route(...)` intercept — the Finnhub calls are backend→provider, invisible to the browser.
A fake `MarketDataPort` bean — see D8.

---

## D11 — ArchUnit rules, JaCoCo, and the test plan

**Decision — ArchUnit (`StandardArchitectureRulesTest`, 18 → 20 rules).** Add:
1. `portfolio_valuation_domain_and_business_do_not_depend_on_marketdata` — no class in
   `..core.portfolio.domain..` or `..core.portfolio.business..` may depend on `..core.marketdata..`.
2. `marketdata_is_accessed_only_from_the_portfolio_marketdata_adapter_package` — classes in
   `..core.marketdata..` may be referenced from `..core.portfolio..` **only** by
   `..core.portfolio.infrastructure.marketdata..`.
Both are **non-vacuous** (verified by a throwaway violating import during implementation, then
reverted). Existing rules already cover: `domain` has no Spring/JPA/HTTP; `business` no web/data;
the `PortfolioValuationCalculator` being in `domain` is automatically framework-fenced.
Plus a lightweight check (ArchUnit `fields()` or a review checklist item) that **no field in
`..portfolio.domain.model.(PortfolioValuation|PositionValuation|SectorAllocation)..` is `double` or
`float`** (SC-006).

**Decision — JaCoCo.** Bundle gate stays **≥ 90 % line AND branch**. New entities under
`portfolio/infrastructure/persistence/entity/**` are already excluded (confirm wildcard). The
controller/DTO/mapper/adapter/listener/calculator/service are **in scope** and must be covered by
the tests below. No new exclude beyond the entity package.

**Decision — test plan (TDD where deterministic — FR-036/FR-037).**

| Test | Kind | Covers |
|---|---|---|
| `PortfolioValuationCalculatorTest` | unit, **RED-first** | FR-005…FR-013, FR-016…FR-018; the D4 worked example to the digit; USD-only / EUR-only / mixed; missing price → unvalued not 0; missing FX → PARTIAL + native total stands; `totalEUR = 0` → no weights/NaN; sector missing → `Unclassified` + still `COMPLETED`; idempotent (pure) |
| `ValuationStatusRulesTest` (or table cases in the above) | unit | D5 table — every row |
| `PortfolioValuationServiceTest` | unit (Mockito: `MarketDataGateway`, `PortfolioValuationRepository`, `PortfolioRepository`) | gathers only needed FX directions (D8); per-Position gateway failure → that Position unvalued → `PARTIAL`; **`PortfolioRepository.save` never invoked** (SC-008); persists via `upsertLatest` once |
| `EnMarketDataGatewayAdapterTest` | unit (Mockito: EN005 ports) | each `MarketDataException` subtype → `Optional.empty()`; `Sector.UNCLASSIFIED` → `Optional.empty()`; happy path maps `MarketPrice`/`FxRate`/`sector`; bad currency string → `Optional.empty()` |
| `PortfolioValuationResponseMapperTest` | unit | domain snapshot (all 4 statuses; unvalued Position; absent totals) → DTO with nulls, never 0 |
| `PortfolioValuationControllerContractTest` | `@WebMvcTest` + `swagger-request-validator` | `200` COMPLETED; `200` synthetic PENDING (no snapshot); `404` unknown id (`/problems/portfolio-not-found`); `400` non-UUID; response validates against `openapi.yaml`; **FD003 contract tests re-run green** |
| `PortfolioValuationPersistenceAdapterIT` | **Testcontainers** PG | `upsertLatest` inserts graph; second `upsertLatest` for same `portfolio_id` → 1 parent row, children replaced, **0 duplicates** (SC-002); cascade on parent delete; `NUMERIC` exact round-trip (SC-001) |
| `PortfolioValuationOnCreationIT` | **Testcontainers** PG (fake `MarketDataGateway` bean) | create Portfolio → snapshot row exists with expected status; **gateway throws for all Positions → Portfolio + Positions rows unchanged, `FAILED` snapshot written, request still 201** (FR-002, SC-005, SC-008); replay create (idempotency key) → no second valuation |
| `SchemaIntegrityIT` (if the pattern exists) | Testcontainers | Hibernate does not `alter`/`drop` `V4` tables (`ddl-auto: none`) |
| `portfolio-valuation.service.spec.ts` | `ng test` | `getValuation` maps body; `404` → `'not-found'`; error → `null` |
| `portfolio-detail.page.spec.ts` (extended) | `ng test` | COMPLETED → totals card + columns + sector list + "Valued at"; PARTIAL → blank cells for unvalued row + "Partial valuation — … N position(s)"; absent/FAILED → "Valuation unavailable", **no `0.00`/`0 %`**; no edit control |
| `valuation-format.spec.ts` | `ng test` | 2-dp money/percent; `null` → `—` |
| `fd004-valuation.spec.ts` | Playwright E2E-001 | SC-001, SC-003, AC-001…AC-012 end-to-end |
| `fd004-provider-failure.spec.ts` | Playwright E2E-002 | SC-005 — Portfolio survives, no fabricated zeros |
| `StandardArchitectureRulesTest` +2 | ArchUnit | SC-006, SC-007, FR-035 |

**Regression:** the full FD001/FD002/FD003/EN004/EN005 Surefire + Failsafe + `ng test` + existing
E2E specs must stay green (FR-034, SC-009).

**Rationale.** Every SC and AC has an owning executable test (SC-013). Deterministic pieces
(calculator, status rules) are pure and RED-first (constitution VII); PostgreSQL touchpoints use
Testcontainers (FR-037); the EN005 boundary is stubbed because it is a true external provider
(constitution VII carve-out). No coverage exclude is added for FD004 logic.

---

## Research summary — all planning decisions resolved

| # | Topic | Outcome | Traces |
|---|---|---|---|
| D1 | Trigger | Spring `PortfolioCreatedEvent` + synchronous `@EventListener` + catch-all, published on `!replayed` after `save` | FR-002/003/004; OD-FD004-1 |
| D2 | EN005 access | one `MarketDataGateway` ACL port + `EnMarketDataGatewayAdapter`; exceptions → `Optional.empty()` | FR-026/035; AR-062; OD-FD004-2 |
| D3 | Placement | valuation area inside the `portfolio` module | FR-023/041; OD-FD004-3 |
| D4 | Calculator | pure `BigDecimal`; exact money (unscaled), weights `divide(…,12,HALF_UP)`; display-only 2 dp | FR-005…013; SC-001/003/006; OD-FD004-5 |
| D5 | Status rules | `FAILED` only when nothing usable; else `PARTIAL`; `PENDING` not calculator-produced | FR-015…018; OD-FD004-… |
| D6 | Persistence | `V4__portfolio_valuation.sql`; `portfolio_id UNIQUE` (latest-only); delete-then-insert in one tx | FR-020/021/022; SC-002; OD-FD004-4 |
| D7 | Read endpoint | contract-first `GET /api/portfolios/{portfolioId}/valuation`; new controller; reuse `PortfolioNotFoundException`; no snapshot → synthetic `PENDING` `200` | FR-024/025/027; SC-012; OD-FD004-6 |
| D8 | FX + E2E wiring | fetch only needed FX directions; drive the stub via `FINNHUB_BASE_URL`, no prod default change | FR-006/018; SC-010/012; OD-FD004-8 |
| D9 | Frontend | extend the FD003 detail additively; one 2-dp format helper; explicit state line; read-only | FR-028…032; AC-012 |
| D10 | E2E | `finnhub-stub` service in `compose.e2e.yaml`; `fd004-valuation` + `fd004-provider-failure` specs | FR-038; SC-005/010/013; OD-FD004-7 |
| D11 | Tests / ArchUnit / JaCoCo | +2 ArchUnit rules, no-`double` check; full test matrix; ≥ 90 % gate; no new exclude | FR-035/036/037; SC-006/007/009/013 |

No EN005 change (D2/D8, FR-033). No new deployable / broker / scheduler / persistence tech / provider
(FR-041, SC-012). No `product/` edit (FR-042).

---

# Revision 2 — Allocation charts (2026-09-05)

Frontend-only research for the two mandatory pie charts. No `NEEDS CLARIFICATION`.

## D-chart-1 — Inline-SVG pie geometry

**Decision.** `PieChartComponent` (standalone, `frontend/web/src/app/portfolio/pie-chart.component.ts`)
renders a `<svg viewBox="0 0 100 100">` with one `<path>` per slice. For slice *i* with cumulative
start fraction `a0` and end fraction `a1` (`a1 - a0 = slice.fraction`, all fractions normalised so
`Σ = 1`):

```
θ0 = 2π·a0 − π/2      θ1 = 2π·a1 − π/2        (−π/2 ⇒ start at 12 o'clock, clockwise)
(x0,y0) = (50 + r·cosθ0, 50 + r·sinθ0)
(x1,y1) = (50 + r·cosθ1, 50 + r·sinθ1)
largeArc = (a1 − a0) > 0.5 ? 1 : 0
d = `M 50 50 L x0 y0 A r r 0 largeArc 1 x1 y1 Z`
```

**Single 100 % slice** (one valued Position, or one sector) ⇒ `a1 − a0 = 1` ⇒ `θ0 == θ1` ⇒ a
degenerate arc. Handle as a special case: render a full `<circle cx=50 cy=50 r=r>` instead.
**Zero slices** ⇒ the component renders nothing (its parent already guards with `showCharts()`).

`r = 48` (2 px margin for the stroke). Slice stroke: `1` px `--color-surface` for separation.

**Rationale.** Deterministic, dependency-free, unit-testable geometry (constitution VII). `viewBox`
scaling keeps it responsive. `M 50 50 L … Z` (pie, not donut — OD-R2-3).

**Alternatives rejected.** `conic-gradient(...)` — a CSS one-liner but no per-slice DOM node ⇒ no
per-slice label/`<title>`/hit-target, and the geometry can't be asserted in `ng test`. A charting
library — new npm dependency, forbidden (FR-041, FR-049).

## D-chart-2 — Slice sources + the `showCharts` predicate

**Decision.** In `portfolio-detail.page.ts`, from the fetched `PortfolioValuationView v`:

```ts
tickerSlices() = v.positions
  .filter(p => p.valued && p.portfolioWeight != null)
  .map(p => ({ label: p.ticker, fraction: Number(p.portfolioWeight) }))
  .sort((a,b) => b.fraction - a.fraction);            // OD-R2-7

sectorSlices() = v.sectors
  .map(s => ({ label: s.sector, fraction: Number(s.sectorWeight) }))
  .sort((a,b) => b.fraction - a.fraction);

showCharts() = v != null
  && (v.status === 'COMPLETED' || v.status === 'PARTIAL')
  && v.totalValueEUR != null && Number(v.totalValueEUR) > 0
  && tickerSlices().length > 0;
```

When `showCharts()` is false → render **neither** `<app-pie-chart>` (FR-048); the valuation-state
line is the only allocation signal. For `PARTIAL`, the valued-set weights already sum to ~1, so the
pie is full and the partial-state message carries the "incomplete" meaning (OD-R2-8, §17.3).

**BR-016 / FR-047.** The component is handed the **backend** `fraction`; it never divides,
re-weights, or reads the Position table. The legend renders `percent(String(fraction))` — the exact
helper the totals/weights use — so the chart % and the table % are the same number.

**Rationale.** The valuation API already returns `portfolioWeight` (per valued Position) and
`sectorWeight` (per sector). No API change (FR-024, A13).

## D-chart-3 — Categorical colour palette

**Decision.** Add to `frontend/web/src/styles/_tokens.scss`:

```scss
--chart-1: #3b82f6;  --chart-2: #22c55e;  --chart-3: #f59e0b;  --chart-4: #a855f7;
--chart-5: #ef4444;  --chart-6: #14b8a6;  --chart-7: #ec4899;  --chart-8: #eab308;
```

(dark-theme categorical ramp, aligned with the existing accent tokens). Assigned by slice **index**
(`--chart-${(i % 8) + 1}`), stable within a chart; the two charts index independently. The legend
always shows the text label + % — colour is never the only signal (design-system rule).

**Rationale.** Stable, legible on `--color-background`, no collisions. The tokens file explicitly
anticipates being "refined as real UI is built".

## D-chart-4 — Accessibility & responsive layout

**Decision.** `<svg role="img" [attr.aria-label]="ariaLabel()">` where `ariaLabel()` summarises the
title + the top 3 slices (e.g. *"Allocation by Ticker: AAPL 76.19%, SAN 23.81%"*). Each `<path>`
gets a `<title>` (`${label} ${percent}`). The legend is a real `<ul>` of `label` + `%`.
Layout: a CSS grid `grid-template-columns: repeat(auto-fit, minmax(240px, 1fr))` holding the two
charts → side-by-side on wide, stacked on narrow (§17.4). Consistent with the design-system dark
card style (`--color-surface-elevated`, `--radius-md`).

## D-chart-5 — E2E stub `/v1/latest` prerequisite (A14)

**Decision.** EN005 Revision 2 (C1) moved FX to **Frankfurter**; the e2e `finnhub-stub` only serves
`/quote` + `/stock/profile2` + `/forex/rates`. Add to `e2e/finnhub-stub/server.js`:

```
GET /v1/latest?base=USD&symbols=EUR  →  { "amount":1.0, "base":"USD", "date":"<today>", "rates": { "EUR": 0.80 } }
GET /v1/latest?base=EUR&symbols=USD  →  { "amount":1.0, "base":"EUR", "date":"<today>", "rates": { "USD": 1.25 } }
```

and on the e2e `backend` service in `compose.e2e.yaml`:
`FRANKFURTER_BASE_URL: http://finnhub-stub:8080`. Same deterministic 0.80 / 1.25 the FD004 E2E-001
numbers assume ⇒ totals `€2,100.00` / `$2,625.00`, weights `76.19 %` / `23.81 %` unchanged.

This is a **Phase-1 prerequisite** — done + `./e2e.sh` green **before** the chart assertions land.
It also completes most of EN005-R2 checkpoint C4 (which then only needs the `finnhub-stub` →
`market-data-stub` rename).

**Rejected.** Doing the full EN005-R2 C4 rename here (mixes workstreams); a fake `@Primary` FxRate
bean in the e2e image (test double in production code).

## D-chart-6 — Test matrix (Revision 2)

| Test | Kind | Covers |
|---|---|---|
| `pie-chart.component.spec.ts` | `ng test`, **RED-first** | slice `<path>` count == slice count; 1-slice → `<circle>`; 2 slices → two paths, `largeArcFlag` per rule; a > 50 % slice → `largeArcFlag=1`; legend text = `label` + `xx.xx %`; `role="img"` + `aria-label`; 0 slices → empty render; total fraction not exactly 1 (rounding) → still closes the circle |
| `portfolio-detail.page.spec.ts` (extended) | `ng test` | `COMPLETED` (E2E-001 numbers) → ticker chart legend `AAPL 76.19%` / `SAN 23.81%`; sector chart `Technology 76.19%` / `Financial Services 23.81%`; chart % === the weight/allocation shown in the table/list (SC-014); `PARTIAL` → charts render valued portion + partial message present (SC-015); `FAILED` / `PENDING` / no-EUR-basis / valuation `null` → **no** `app-pie-chart` element in the DOM (SC-015); no `0 %` slice anywhere |
| `valuation-format.spec.ts` | `ng test` | (unchanged — `percent()` reused) |
| `e2e/tests/fd004-valuation.spec.ts` (extended) | Playwright E2E-001 | after opening the detail: Allocation-by-Ticker chart visible, slices `AAPL` ≈ `76.19 %` + `SAN` ≈ `23.81 %`; Allocation-by-Sector chart visible, `Technology` ≈ `76.19 %` + `Financial Services` ≈ `23.81 %`; chart %s consistent with the detail's numbers (§26 checks 11–15); Portfolio still persisted |
| `e2e` stub route | manual + `./e2e.sh` | `/v1/latest` returns the deterministic rates; `./e2e.sh` reaches a `COMPLETED` FD004 valuation again |
| full `ng test` + `./e2e.sh` + FD001/2/3 + EN004/EN005 suites | regression | no regression (FR-034, FR-040, SC-009, SC-010) |

---

## Revision 2.1 — Portfolio-detail table trim (2026-09-04)

Owner request, within FR-029's "MAY be extended" latitude (display refinement, not scope):

- **Drop the native `Market Value` column** — `quantity × price` duplicates `Value (EUR)` /
  `Value (USD)`. `PositionValuationView.nativeMarketValue` stays in the model (the API still
  returns it); `portfolio-detail.page.ts` just stops rendering it and `marketValueOf()` is deleted.
- **`Market Price` renders with the native currency** — `priceOf()` returns
  `"<decimal2(marketPrice)> <nativeCurrency>"` when the row is `valued` and a price is present,
  `—` otherwise. No new formatting helper (string interpolation over the existing `decimal2`).
- **Drop the standalone `Sector allocation` list** — supersedes D-chart earlier note "(a bar/chart
  is optional, FR-028)". The mandatory Allocation-by-Sector chart legend (`sectorSlices()`) already
  renders `sector` + `xx.xx %` per sector. The `sectors()` accessor is kept (it feeds
  `sectorSlices()`); the `<section class="sectors">` block, its styles and `pct()` are removed.

**Test-matrix delta**: `portfolio-detail.page.spec.ts` — cross-check SC-014 by comparing the two
chart legends' percentage sets (the sector list it used before is gone); assert the `Market value`
header is absent and the `Market price` cell carries the currency. `fd004-valuation.spec.ts` —
`.sectors` locator count `0`, no `Market value` `<th>`, `200.00 USD` / `5.00 EUR` in the rows.
No backend / API / dependency / migration / `product/` change.
