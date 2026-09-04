# Phase 1 — Data Model: EN005 Revision 2

**Plan**: [plan.md](./plan.md) · **Research**: [research.md](./research.md) · **Spec**: [spec.md](./spec.md)

Supersedes the 2026-09-03 data-model. Covers: the provider-neutral read models (`marketdata`), the
new **persisted** instrument-profile enrichment (`financialinstrument`, Q1), and the `V5` schema.
No change to `Portfolio` / `Position` / EN004 `FinancialInstrumentListing` identity.

All amounts / rates are **`BigDecimal`** (FR-011). A missing value is an explicit
absent/unavailable outcome — never `0` / `1` (FR-008, FR-010, BR-EN005-006).

---

## 1. `marketdata` module — after Revision 2

### 1.1 Read models (not persisted)

| Model | Fields | Notes |
|---|---|---|
| `MarketPrice` | `InstrumentIdentifier instrument`, `BigDecimal price` (>0), `SupportedCurrency currency`, `Instant observedAt`, `ObservedAtSource observedAtSource`, `DataSource source` | unchanged; `source = FINNHUB` |
| `FxRate` | `SupportedCurrency from`, `SupportedCurrency to` (≠ from), `BigDecimal rate` (>0), `Instant observedAt`, `ObservedAtSource observedAtSource`, `DataSource source` | `source` now **`FRANKFURTER`**; `observedAt` = Frankfurter `date` at start-of-day UTC; `observedAtSource = PROVIDER_TIMESTAMP` |
| `InstrumentIdentifier` | `String ticker`, `String market`, `SupportedCurrency currency` | unchanged (used by the price port) |
| `SupportedCurrency` | enum `EUR`, `USD` + `isSupported` / `parseOrNull` | unchanged |
| `ObservedAtSource` | enum `PROVIDER_TIMESTAMP`, `RETRIEVAL_TIME` | unchanged |
| `DataSource` | enum `FINNHUB`, **`FRANKFURTER`** | +1 |
| ~~`InstrumentProfile`~~, ~~`Sector`~~ | — | **removed** — moved to `financialinstrument` (§2) |

### 1.2 Ports (`marketdata.domain.ports`)

| Port | Signature | Notes |
|---|---|---|
| `MarketDataPort` | `MarketPrice getLatestPrice(InstrumentIdentifier)` | unchanged (name kept — Q2) |
| `FxRatePort` | `FxRate getRate(SupportedCurrency from, SupportedCurrency to)` | unchanged; now Frankfurter-backed |
| ~~`InstrumentProfilePort`~~ | — | **removed** from `marketdata` |

### 1.3 Neutral exceptions (`marketdata.domain.exceptions`) — kept (Q2)

`MarketDataException` (base) · `MarketDataUnavailableException` · `FxRateUnavailableException` ·
`MarketDataNotConfiguredException` · `ProviderRateLimitedException` ·
`ProviderAuthenticationFailedException` · `InstrumentNotResolvedException`.
~~`InstrumentProfileUnavailableException`~~ → moved to `financialinstrument`.

### 1.4 Infrastructure (provider packages)

| Package | Contents |
|---|---|
| `…infrastructure.finnhub` | `FinnhubMarketDataAdapter` (price; `@ConditionalOnProperty market-data.price.provider`), `client.FinnhubRestClient` (**`/quote` only**), `client.FinnhubOperation` (`QUOTE`), `dto.FinnhubQuoteResponse`, `mapper.FinnhubQuoteMapper`, `resolver.{FinnhubSymbolResolver,FinnhubSymbolRule}` (+ `finnhub-symbol-map.csv`) |
| `…infrastructure.frankfurter` **(new)** | `FrankfurterFxRateAdapter` (`implements FxRatePort`; `@ConditionalOnProperty market-data.fx.provider=frankfurter matchIfMissing`), `client.FrankfurterRestClient`, `dto.FrankfurterRatesResponse`, `mapper.FrankfurterFxRateMapper` |
| `…infrastructure.finnhub.cache` | `TtlCache`, `CachingMarketDataPort` (`@Primary`), `CachingFxRatePort` (`@Primary`; now decorates `FrankfurterFxRateAdapter`). ~~`CachingInstrumentProfilePort`~~ **removed** |
| `…infrastructure.config` | `FinnhubProperties` (`api-key`, `base-url`, timeouts, `cache.quote-ttl`), **`FrankfurterProperties`** (`base-url` `${FRANKFURTER_BASE_URL:https://api.frankfurter.dev}`, timeouts, `cache.fx-ttl`), `MarketDataModuleConfiguration` |

`FrankfurterRatesResponse` (Jackson, `@JsonIgnoreProperties(ignoreUnknown = true)`):
`BigDecimal amount`, `String base`, `String date`, `Map<String, BigDecimal> rates`.

---

## 2. `financialinstrument` module — instrument-profile enrichment (Q1, NEW)

### 2.1 Domain model (`financialinstrument.domain.model`)

| Model | Fields | Rules |
|---|---|---|
| `Sector` | final class: `UNCLASSIFIED` singleton, `of(String)`, `isClassified()`, `classification()` | a missing/blank provider value ⇒ `UNCLASSIFIED`; never inferred (BR-EN005-006) |
| `ProfileSource` | enum `FINNHUB` | module-local (no `marketdata` import) |
| `InstrumentProfile` | `InstrumentIdentity instrument` (reuse — `Ticker` + `Mic`), `String name?`, `Sector sector` (non-null), `String industry?`, `SupportedCurrency currency?`, `Instant lastUpdatedAt` (non-null), `ProfileSource source?` | `withLastUpdatedAt(Instant)` copy for the use case |

### 2.2 Ports (`financialinstrument.domain.ports`)

| Port | Signature | Role |
|---|---|---|
| `InstrumentProfileLookup` | `InstrumentProfile get(InstrumentIdentity)` — throws `InstrumentProfileUnavailableException` | **inbound** capability port; consumed by `financialinstrument` itself and by FD004's `portfolio.infrastructure` (AR-062 — same pattern as `FinancialInstrumentCatalog`). Implemented by `GetInstrumentProfile`. |
| `InstrumentProfilePort` | `InstrumentProfile getProfile(InstrumentIdentity)` — throws neutral provider exceptions | **outbound** external-provider port (name kept — Q2). Implemented by `FinnhubInstrumentProfileAdapter`. |
| `InstrumentProfileRepositoryPort` | `Optional<InstrumentProfile> findByInstrument(InstrumentIdentity)` · `InstrumentProfile save(InstrumentProfile)` (**upsert**) | **outbound** persistence port. Implemented by `JpaInstrumentProfileRepositoryAdapter`. |

### 2.3 Neutral exceptions (`financialinstrument.domain.exceptions`)

`ExternalProviderException` (base) · `ExternalProviderUnavailableException` ·
`ExternalProviderRateLimitedException` · `ExternalProviderAuthenticationFailedException` ·
`ProviderNotConfiguredException` · `InstrumentProfileUnavailableException`. Minimal set — only what
`FinnhubInstrumentProfileAdapter` throws + what `GetInstrumentProfile` rethrows.

### 2.4 Business (`financialinstrument.business`)

`GetInstrumentProfileUseCase` = `InstrumentProfileLookup` (or `GetInstrumentProfile implements
InstrumentProfileLookup`). `GetInstrumentProfile` (`@Service`): deps `InstrumentProfileRepositoryPort`,
`InstrumentProfilePort`, `Clock`. Algorithm — [research.md](./research.md) D6:
local hit → return (no provider call) · miss → provider → `withLastUpdatedAt(now)` → **upsert** →
return · provider failure on a miss → `InstrumentProfileUnavailableException`, **persist nothing**.

### 2.5 Persistence (`financialinstrument.infrastructure.persistence`)

| Class | Notes |
|---|---|
| `InstrumentProfileEntity` | `@Entity @Table("instrument_profile")`; columns per §3; under `…persistence.entity.**` (JaCoCo-excluded) |
| `InstrumentProfileJpaRepository` | `extends JpaRepository<…, UUID>`; `Optional<…> findByTickerAndMarketMic(String, String)`; `@Modifying @Query(nativeQuery = true, value = "INSERT … ON CONFLICT (ticker, market_mic) DO UPDATE SET …")` `int upsert(...)` |
| `InstrumentProfilePersistenceMapper` | domain ⇄ entity; `Sector` ⇄ `sector` string (`UNCLASSIFIED` ⇄ `'Unclassified'`); `ProfileSource` ⇄ `source` string |
| `JpaInstrumentProfileRepositoryAdapter` | `@Repository implements InstrumentProfileRepositoryPort`; own `TransactionTemplate` (read-only for `findByInstrument`, write for `save`→`upsert`) |

### 2.6 Provider adapter (`financialinstrument.infrastructure.finnhub`)

| Class | Notes |
|---|---|
| `config.FinnhubProfileProperties` | `@ConfigurationProperties("finnhub")` read-only view of `api-key` / `base-url` (+ own timeouts); binds the same env vars as `marketdata`'s `FinnhubProperties` |
| `client.FinnhubProfileClient` | `@Component`; own `RestClient`; `GET {base}/stock/profile2?symbol={symbol}` + `X-Finnhub-Token` header; status→neutral-exception translation |
| `dto.FinnhubCompanyProfileResponse` | moved verbatim (`ticker`, `name`, `currency`, `exchange`, `finnhubIndustry`, `isEmpty()`) |
| `mapper.FinnhubProfileMapper` | `(dto, InstrumentIdentity, Instant retrievedAt) → InstrumentProfile`; `finnhubIndustry` → `Sector.of(...)` verbatim (A6); `exchange` **not** a MIC (VC-010) |
| `resolver.{FinnhubSymbolResolver,FinnhubSymbolRule}` + `finnhub-symbol-map.csv` | **duplicated** from `marketdata` (research D9) — a provider adapter owns its symbol resolution |
| `FinnhubInstrumentProfileAdapter` | `@Component implements InstrumentProfilePort`; `@ConditionalOnProperty(name = "market-data.profile.provider", havingValue = "finnhub", matchIfMissing = true)` |

---

## 3. Schema — `V5__instrument_profile.sql`

Flyway forward migration (V1…V4 exist → **V5**). Owned by `financialinstrument`. **No** change to
`financial_instrument` / `market` / FD001 / FD004 tables (FR-042, VC-010). `ddl-auto: none`.

| Column | Type | Constraints |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `ticker` | `TEXT` | `NOT NULL` |
| `market_mic` | `TEXT` | `NOT NULL` |
| `currency` | `CHAR(3)` | nullable (display only) |
| `name` | `TEXT` | nullable |
| `sector` | `TEXT` | `NOT NULL` (`'Unclassified'` when the provider gave none) |
| `industry` | `TEXT` | nullable |
| `last_updated_at` | `TIMESTAMPTZ` | `NOT NULL` |
| `source` | `TEXT` | nullable |
| constraint | `instrument_profile_identity_uk` | `UNIQUE (ticker, market_mic)` |

`save` = `INSERT … ON CONFLICT (ticker, market_mic) DO UPDATE SET name, sector, industry, currency,
last_updated_at, source` — idempotent under a concurrent miss.

---

## 4. Configuration (`application.yml`)

```yaml
market-data:
  price:   { provider: finnhub }
  profile: { provider: finnhub }
  fx:      { provider: frankfurter }

finnhub:
  api-key: ${FINNHUB_API_KEY:}
  base-url: ${FINNHUB_BASE_URL:https://finnhub.io/api/v1}
  connect-timeout: 2s
  read-timeout: 5s
  cache:
    quote-ttl: 45s          # profile-ttl removed (DB-first); fx-ttl moved to frankfurter

frankfurter:
  base-url: ${FRANKFURTER_BASE_URL:https://api.frankfurter.dev}
  connect-timeout: 2s
  read-timeout: 5s
  cache:
    fx-ttl: 10m
```

`compose.yaml` (`./start.sh`): + `FRANKFURTER_BASE_URL: ${FRANKFURTER_BASE_URL:-https://api.frankfurter.dev}`.
`compose.e2e.yaml`: `FINNHUB_BASE_URL` **and** `FRANKFURTER_BASE_URL` → `http://market-data-stub:8080`.

---

## 5. Consumer mapping — FD004 `EnMarketDataGatewayAdapter`

| FD004 gateway method | Before | After |
|---|---|---|
| `latestPrice(ticker, market, ccy)` | `marketdata` `MarketDataPort.getLatestPrice` | **unchanged** |
| `fxRate(from, to)` | `marketdata` `FxRatePort.getRate` (Finnhub) | **unchanged signature**; now Frankfurter-backed |
| `sector(ticker, market, ccy)` | `marketdata` `InstrumentProfilePort.getProfile` → `Sector` | `financialinstrument` `InstrumentProfileLookup.get(new InstrumentIdentity(Ticker, Mic))` → `InstrumentProfile.sector()`; catch `RuntimeException` → `Optional.empty()` |

`portfolio.domain` / `portfolio.business` / `PortfolioValuationCalculator` / persistence / API / UI:
**no change**.

---

## 6. Traceability

| Element | Requirement / VC |
|---|---|
| 3 independent ports, no monolith adapter | FR-001, FR-002; VC-002, VC-003 |
| `FrankfurterFxRateAdapter`, no key | FR-016, FR-017; VC-003, VC-011 |
| Finnhub FX removed | FR-019; §27 |
| `GetInstrumentProfile` DB-first | FR-020, FR-021, FR-023; VC-005…VC-009 |
| `V5` migration, EN004 untouched | FR-024, FR-042; VC-010 |
| provider-neutral persisted fields | FR-022; VC-009 |
| per-capability config, no core branch | FR-028, FR-029, FR-006; VC-004 |
| keep price/FX cache, drop profile cache | FR-031 (Q3) |
| `capability=` telemetry, no secret | FR-033, FR-034; VC-011 |
| FD004 consumer via a `domain.ports` port | FR-007, FR-038; AR-062 |
| both providers stubbed in CI | FR-035, FR-039; VC-014 |
