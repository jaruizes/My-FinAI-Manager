# Phase 0 — Research: EN005 Revision 2

**Feature dir**: `specs/EN005-establish-finnhub-market-data-integration/` · **Plan**: [plan.md](./plan.md) · **Spec**: [spec.md](./spec.md)

Supersedes the 2026-09-03 research. Q1–Q3 resolved (spec Clarifications). This document resolves the
remaining technical decisions; no `NEEDS CLARIFICATION` remains.

---

## D1 — Frankfurter FX adapter

**Decision.** New package `marketdata.infrastructure.frankfurter`:

- `FrankfurterProperties` — `@ConfigurationProperties("frankfurter")` record: `baseUrl` (URI,
  default `https://api.frankfurter.dev`), `connectTimeout` (default 2s), `readTimeout` (default 5s).
- `FrankfurterRestClient` — `@Component`; builds its **own** `RestClient` (`ClientHttpRequestFactoryBuilder.detect().build(settings)` with the timeouts, matching `FinnhubRestClient`). One method:
  `FrankfurterRatesResponse latest(String base, String symbols)` → `GET {baseUrl}/v1/latest?base={base}&symbols={symbols}`, **no** auth header. Translates transport/status failures: `4xx`/`5xx`/timeout/`IOException` → `ExternalProviderUnavailable` (reuse `MarketDataUnavailableException` — Q2 keeps names) or, on a clearly malformed body, `FxRateUnavailableException`. Structured log `event=ProviderCall provider=frankfurter capability=fx-rate operation=LATEST outcome=… httpStatusCategory=… latencyMs=…` — no secret (there is none).
- `FrankfurterRatesResponse` — Jackson record `@JsonIgnoreProperties(ignoreUnknown = true)`:
  `BigDecimal amount`, `String base`, `String date`, `Map<String, BigDecimal> rates`.
- `FrankfurterFxRateMapper` — `(FrankfurterRatesResponse dto, SupportedCurrency from, SupportedCurrency to, Instant retrievedAt) → FxRate`. Reads `dto.rates().get(to.name())`; **absent key ⇒ `FxRateUnavailableException`** (never a null/1/0 rate); `date` → `observedAt` (parsed as `LocalDate` at start-of-day UTC → `Instant`, or kept as the provider's date — see D-note); `retrievedAt = now`; `observedAtSource = PROVIDER_TIMESTAMP` (Frankfurter dates its rates); `source = DataSource.FRANKFURTER`.
- `FrankfurterFxRateAdapter` — `@Component`, `implements FxRatePort`,
  `@ConditionalOnProperty(name = "market-data.fx.provider", havingValue = "frankfurter", matchIfMissing = true)`.
  `getRate(SupportedCurrency from, SupportedCurrency to)`: reject `from == to` (`IllegalArgumentException`,
  per `FxRatePort` javadoc); call `client.latest(from.name(), to.name())`; map; return.

**`DataSource`** gains `FRANKFURTER`. `FxRate.source` for FX results is now `FRANKFURTER`.

**Rationale.** Enabler §6.3, §8A, §15, §25.19; the FX capability the free Finnhub plan cannot serve
(`/forex/rates` → `403`). Frankfurter is ECB-sourced, keyless, no rate limit. Each adapter owning
its own HTTP (§6.1) keeps providers independent (§16, VC-013).

**D-note (observedAt).** Frankfurter returns a `date` (the ECB publication date), not a timestamp.
Store it as `LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant()` for `observedAt` and
mark `observedAtSource = PROVIDER_TIMESTAMP`; `retrievedAt` is the call instant. Consumers (FD004)
already surface `fxDataAsOf` from `observedAt`.

**Alternatives rejected.** Reuse `FinnhubRestClient` — couples two providers (§16 forbids). Derive
`EUR→USD = 1 / (USD→EUR)` — the enabler wants an independent call per direction; also Frankfurter
serves both directly. A dedicated FX library — no new dependency (technology policy).

---

## D2 — Remove the Finnhub FX integration

**Decision.** Delete: `marketdata.infrastructure.finnhub.FinnhubFxRateAdapter`,
`…finnhub.dto.FinnhubForexRatesResponse`, `…finnhub.mapper.FinnhubForexRatesMapper`,
`FinnhubRestClient.getForexRates(...)` (+ its private `call` wiring for that path),
`FinnhubOperation.FOREX_RATES`, `FinnhubProperties.cache.fxTtl`? — **keep** `fxTtl` (the
`CachingFxRatePort` still uses a TTL; rename the config to `frankfurter`-side or keep under
`finnhub.cache` — **decision: move the FX TTL to `frankfurter.cache.fx-ttl`** so all Finnhub config
is quote-only). Update `FinnhubProviderSmokeTest` — drop the forex probe. Update every test that
referenced these types.

**Rationale.** FR-019, §27. No code may reference the removed Finnhub FX path.

**Alternatives rejected.** Keep `FinnhubFxRateAdapter` as a dormant second provider — dead code,
and it never worked on the plan; VC-013 is satisfied structurally by the `@ConditionalOnProperty`
pattern, not by keeping a broken adapter.

---

## D3 — Per-capability provider configuration & selection

**Decision.** `application.yml`:

```yaml
market-data:
  price:   { provider: finnhub }
  profile: { provider: finnhub }
  fx:      { provider: frankfurter }
```

Selection = `@ConditionalOnProperty(name = "market-data.<cap>.provider", havingValue = "<name>",
matchIfMissing = true)` on each provider-adapter bean:
- `FinnhubMarketDataAdapter` → `market-data.price.provider = finnhub`
- `FinnhubInstrumentProfileAdapter` (now in `financialinstrument`) → `market-data.profile.provider = finnhub`
- `FrankfurterFxRateAdapter` → `market-data.fx.provider = frankfurter`

The `@Primary` caching decorators (`CachingMarketDataPort`, `CachingFxRatePort`) inject the single
active provider bean **by port type**. `domain`/`business` contain **no** provider string and **no**
`if`/`switch` on provider — ArchUnit + review (FR-006, VC-004).

**Rationale.** Enabler §7, §16, §25.9. `matchIfMissing = true` = zero-config default; a future
second provider adds its own `@ConditionalOnProperty` bean and flipping the property switches it
with no other change (VC-013).

**Alternatives rejected.** A `Map<String, Port>` registry + selector — over-engineered for
one-per-capability; revisit at the second provider. Spring profiles — ties provider choice to the
deployment profile, not a per-capability knob.

---

## D4 — `financialinstrument` instrument-profile domain model

**Decision.** New in `financialinstrument.domain.model`:
- `Sector` — final class mirroring the (deleted) `marketdata` one: `Sector.UNCLASSIFIED` singleton,
  `Sector.of(String)`, `isClassified()`, `classification()`. (A missing sector is explicit, never
  inferred — BR-EN005-006.)
- `InstrumentProfile` — record: `InstrumentIdentity instrument` (reuse the existing
  `financialinstrument.domain.model.InstrumentIdentity(Ticker, Mic)` — OD-4), `String name`
  (nullable), `Sector sector` (non-null), `String industry` (nullable),
  `SupportedCurrency currency` (nullable — `financialinstrument.domain.model.SupportedCurrency`
  exists), `Instant lastUpdatedAt` (non-null), `DataSource source` (nullable). Add a
  `withLastUpdatedAt(Instant)` copy method for the use case.

`DataSource` — the profile model needs a source enum. `financialinstrument` has none. **Decision:**
add a minimal `financialinstrument.domain.model.ProfileSource { FINNHUB }` (own to the module — do
not import `marketdata.domain.model.DataSource`).

**Rationale.** Q1 — the capability lives in `financialinstrument`; its domain must be self-contained
(no `marketdata` import). `InstrumentIdentity` already is the canonical `ticker + MIC` key (EN004
§7). `SupportedCurrency` already exists in the module.

**Alternatives rejected.** Import `marketdata`'s `Sector`/`DataSource`/`InstrumentIdentifier` —
cross-module domain coupling, and ArchUnit would (correctly) forbid `financialinstrument.domain` →
`marketdata`.

---

## D5 — Profile ports in `financialinstrument`

**Decision.** `financialinstrument.domain.ports`:

```java
public interface InstrumentProfilePort {           // external provider port (name kept — Q2)
    InstrumentProfile getProfile(InstrumentIdentity instrument);   // throws InstrumentProfileUnavailableException, ExternalProvider*
}

public interface InstrumentProfileRepositoryPort { // provider-neutral persistence port
    Optional<InstrumentProfile> findByInstrument(InstrumentIdentity instrument);
    InstrumentProfile save(InstrumentProfile profile);             // upsert on (ticker, MIC)
}
```

`financialinstrument.domain.exceptions.InstrumentProfileUnavailableException` (+ reuse of the
provider-neutral rate-limit / auth / not-configured concepts — **decision:** add
`ExternalProviderException` base + `ExternalProviderUnavailableException` /
`ExternalProviderRateLimitedException` / `ExternalProviderAuthenticationFailedException` /
`MarketDataNotConfiguredException`-equivalent in `financialinstrument.domain.exceptions`; the
`marketdata` copies stay for price/FX). Minimal set — only what the Finnhub profile adapter throws.

**Rationale.** Enabler §11, §19; §18 dependency rules. Business must use the repository *port*, not
Spring Data directly (FR-026).

**Alternatives rejected.** One shared `externalprovider` exceptions package used by both modules —
tempting, but creates a third shared module/namespace; the enabler keeps modules self-contained.
The duplication is ~6 tiny final classes.

---

## D6 — `GetInstrumentProfile` (DB-first orchestration)

**Decision.** `financialinstrument.business`:

```java
public interface GetInstrumentProfileUseCase {
    /** @throws InstrumentProfileUnavailableException when neither local nor provider can supply one */
    InstrumentProfile get(InstrumentIdentity instrument);
}
```

`GetInstrumentProfile` (`@Service`) deps: `InstrumentProfileRepositoryPort`, `InstrumentProfilePort`,
`Clock`.

```
Optional<InstrumentProfile> local = repository.findByInstrument(instrument);
if (local.isPresent()) return local.get();                       // BR-002 — no provider call
InstrumentProfile fetched;
try {
    fetched = provider.getProfile(instrument);                   // BR-003
} catch (ExternalProviderException e) {                          // provider miss / failure
    throw new InstrumentProfileUnavailableException(...);        // BR-006 — persist nothing, fabricate nothing
}
return repository.save(fetched.withLastUpdatedAt(clock.instant())); // BR-004/005 — upsert, provider-neutral
```

**Outcome shape (OD-5):** returns `InstrumentProfile`; throws the neutral
`InstrumentProfileUnavailableException` only on total unavailability. A local hit always returns.
This matches FD004's `EnMarketDataGatewayAdapter.sector(...)` which already `try/catch`es a neutral
exception → `Optional.empty()`.

**RED-first tests** (`GetInstrumentProfileTest`, Mockito): local hit ⇒ `provider` **never**
invoked; local miss + provider ok ⇒ `provider` invoked once, `repository.save` invoked once with
`lastUpdatedAt` set; local miss + provider throws ⇒ `InstrumentProfileUnavailableException`,
`repository.save` **never** invoked; the returned profile on a miss is the **saved** one.

**Rationale.** Enabler §10–§13, BR-EN005-001…006; VC-005…VC-009. Deterministic business logic ⇒
constitution VII RED-first.

---

## D7 — `V5__instrument_profile.sql`

**Decision.** New Flyway forward migration (V1 baseline, V2 FD001, V3 EN004, V4 FD004 → **V5**),
owned by the `financialinstrument` module. **No change** to `financial_instrument` / `market` / any
FD001/FD004 table (FR-042, VC-010).

```sql
CREATE TABLE instrument_profile (
    id               UUID PRIMARY KEY,
    ticker           TEXT NOT NULL,
    market_mic       TEXT NOT NULL,
    currency         CHAR(3),
    name             TEXT,
    sector           TEXT NOT NULL,          -- 'Unclassified' when the provider gave none
    industry         TEXT,
    last_updated_at  TIMESTAMPTZ NOT NULL,
    source           TEXT,
    CONSTRAINT instrument_profile_identity_uk UNIQUE (ticker, market_mic)
);
```

`save` = upsert: `INSERT INTO instrument_profile (...) VALUES (...) ON CONFLICT (ticker, market_mic)
DO UPDATE SET name = EXCLUDED.name, sector = EXCLUDED.sector, industry = EXCLUDED.industry,
currency = EXCLUDED.currency, last_updated_at = EXCLUDED.last_updated_at, source = EXCLUDED.source`.
Implemented in `JpaInstrumentProfileRepositoryAdapter` via a derived `findByTickerAndMarketMic` +
an explicit upsert (native `@Modifying` query or a find-then-save inside one write transaction —
**decision:** `INSERT … ON CONFLICT` as a native query on the JPA repository, keeping the adapter's
own `TransactionTemplate` pattern like `PortfolioValuationPersistenceAdapter`).

**Rationale.** OD-6 — standalone identity columns, no FK (decoupled per enabler §20A). `NUMERIC`
not needed (no money). `UNIQUE(ticker, market_mic)` matches EN004's catalog key and makes the
upsert deterministic (idempotent concurrent miss).

**Alternatives rejected.** FK to `financial_instrument(id)` — forces a join per lookup and couples
lifecycles. New columns on `financial_instrument` — mutates EN004's schema (FR-042).

---

## D8 — Profile persistence stack (`financialinstrument.infrastructure.persistence`)

**Decision.** `InstrumentProfileEntity` (`@Entity @Table("instrument_profile")`, id + the columns
above, getters, protected ctor, `equals/hashCode` on id) — under
`financialinstrument/infrastructure/persistence/entity/**` (already a JaCoCo-excluded package).
`InstrumentProfileJpaRepository extends JpaRepository<InstrumentProfileEntity, UUID>` — derived
`Optional<…> findByTickerAndMarketMic(String, String)` + `@Modifying @Query(nativeQuery = true)`
upsert. `InstrumentProfilePersistenceMapper` — domain `InstrumentProfile` ⇄ entity (`Sector` ⇄
`sector` string, `Sector.UNCLASSIFIED` ⇄ `'Unclassified'`; `ProfileSource` ⇄ `source` string).
`JpaInstrumentProfileRepositoryAdapter` (`@Repository`) — implements
`InstrumentProfileRepositoryPort`; read on a `readOnly` template, `save` on a write template.

**Testcontainers IT** (`JpaInstrumentProfileRepositoryAdapterIT`): round-trip incl. `Sector.UNCLASSIFIED`;
two `save`s for the same identity ⇒ **one** row (upsert); `findByInstrument` miss ⇒ empty.

**Rationale.** Enabler §11; constitution VII (Testcontainers). Mirrors the FD001/FD004 persistence
adapter pattern already in the codebase.

---

## D9 — Move the Finnhub profile adapter into `financialinstrument`

**Decision.** New `financialinstrument.infrastructure.finnhub`:
- `config.FinnhubProfileProperties` — `@ConfigurationProperties("finnhub")` **read-only view** of
  `api-key` + `base-url` (bind the same env vars `FINNHUB_API_KEY` / `FINNHUB_BASE_URL` the
  `marketdata` `FinnhubProperties` binds — Spring allows two `@ConfigurationProperties` on the same
  prefix in different modules; both are read-only). Plus its own timeouts default.
- `client.FinnhubProfileClient` — `@Component`; own `RestClient`; `GET {baseUrl}/stock/profile2?symbol={symbol}`
  with the `X-Finnhub-Token` header; status translation (`401/403` → auth, `429` → rate-limited,
  `5xx`/timeout → unavailable, empty `{}` → `InstrumentProfileUnavailableException`).
- `dto.FinnhubCompanyProfileResponse` — moved verbatim (`ticker`, `name`, `currency`, `exchange`,
  `finnhubIndustry`, `isEmpty()`).
- `mapper.FinnhubProfileMapper` — `(dto, InstrumentIdentity, Instant retrievedAt) → InstrumentProfile`
  (`finnhubIndustry` → `Sector.of(...)` verbatim — A6, no taxonomy; `exchange` **not** used as a
  MIC — VC-010).
- `FinnhubInstrumentProfileAdapter` — `@Component`, `implements InstrumentProfilePort`,
  `@ConditionalOnProperty(name = "market-data.profile.provider", havingValue = "finnhub", matchIfMissing = true)`.
  Resolves the Finnhub symbol from `InstrumentIdentity` — **decision:** the symbol resolver
  (`FinnhubSymbolResolver` + `finnhub-symbol-map.csv`) also moves to `financialinstrument.infrastructure.finnhub.resolver`
  **or** is duplicated. **Decision: move it** (the price adapter in `marketdata` keeps its own copy?
  — no: the price adapter also needs symbol resolution). → **Keep one `FinnhubSymbolResolver` and
  its CSV in a shared spot.** The cleanest: the resolver is *reference data + rules*, not
  provider-transport; **decision:** move it to `financialinstrument.infrastructure.finnhub.resolver`
  and have `marketdata`'s `FinnhubMarketDataAdapter` call `financialinstrument`'s resolver via a
  published port. That is a new cross-module read → **rejected** (over-coupling for a CSV lookup).
  **Final decision:** **duplicate** `FinnhubSymbolResolver` + `FinnhubSymbolRule` + the
  `finnhub-symbol-map.csv` resource into `financialinstrument.infrastructure.finnhub.resolver`
  (the CSV is ~9 rows; a provider adapter owning its symbol resolution is exactly enabler §6.1/§6.2).
  `marketdata` keeps its copy for the price adapter.

**Rationale.** Q1 + enabler §17 (shows `FinnhubInstrumentProfileAdapter` under
`financialinstrument/infrastructure/provider/finnhub/`). Each adapter owning its HTTP + symbol
resolution is §6.1/§6.2. The small CSV duplication beats a cross-module port for reference data.

**Alternatives rejected.** Keep the profile adapter in `marketdata` and have `financialinstrument`
call `marketdata` — violates Q1 and adds `financialinstrument → marketdata` coupling. A shared
`finnhub-common` module — new module for two adapters is premature.

---

## D10 — ArchUnit rule delta (`StandardArchitectureRulesTest`)

**Remove/rewrite** (`marketdata` no longer holds profile; now holds `frankfurter`):
- `finnhub_dto_and_client_types_are_confined_to_the_finnhub_adapter` — keep, still valid (price
  dto/client only now).
- `only_the_finnhub_client_package_uses_restclient` → **rename** to
  `only_provider_client_packages_use_restclient`: `RestClient` allowed in
  `..marketdata.infrastructure.finnhub.client..`, `..marketdata.infrastructure.frankfurter.client..`,
  `..financialinstrument.infrastructure.finnhub.client..`.
- `marketdata_domain_is_free_of_http_json_and_provider_types` — keep.
- `marketdata_does_not_depend_on_other_business_modules` — keep (marketdata must not depend on
  `financialinstrument` or `portfolio`).

**Add**:
- `frankfurter_dto_and_client_types_are_confined_to_the_frankfurter_adapter`.
- `financialinstrument_domain_and_business_are_free_of_provider_http_json_types` — no
  `..finnhub..` / `org.springframework.web..` / `com.fasterxml.jackson..` in
  `..financialinstrument.domain..` / `..financialinstrument.business..`.
- `financialinstrument_does_not_depend_on_marketdata` — no
  `..core.financialinstrument..` → `..core.marketdata..`.
- `finnhub_profile_types_are_confined_to_the_financialinstrument_finnhub_adapter`.

FD004 rules (`portfolio_valuation_core_is_free_of_marketdata`,
`marketdata_is_accessed_only_from_the_portfolio_marketdata_adapter`,
`portfolio_domain_uses_no_binary_floating_point_fields`) — **keep**. `portfolio_core_is_free_of_financialinstrument`
(existing) — **keep** (FD004's `sector(...)` call is from `portfolio.infrastructure.marketdata`, which
is `portfolio.infrastructure`, so it's allowed; the rule targets `portfolio.domain`/`business`).
Wait — verify: FD004's `EnMarketDataGatewayAdapter` is in `..portfolio.infrastructure.marketdata..`;
the existing rule `portfolio_touches_financialinstrument_only_via_its_domain_ports` forbids
`..core.portfolio..` → `..financialinstrument.infrastructure..`/`..business..`. The `sector(...)`
call goes to `financialinstrument.business.GetInstrumentProfileUseCase` → **this rule would fail.**
→ **Rule update:** allow `..portfolio.infrastructure.marketdata..` to reference
`financialinstrument.business` (the published use-case interface) — narrow the exclusion, or change
it to target only `financialinstrument.infrastructure` + non-published business. **Decision:** the
FD004 consumer should call a **`financialinstrument.domain.ports`** interface, not the business
`@Service` — so add a thin published inbound port... actually `GetInstrumentProfileUseCase` **is**
the published interface (in `business`). AR-062 precedent (FD002): `portfolio` reads
`financialinstrument` via `financialinstrument.domain.ports.FinancialInstrumentCatalog` from
`portfolio.infrastructure`. **Follow that exactly:** FD004 calls
`financialinstrument.domain.ports` — so `GetInstrumentProfile` should expose its capability through
a **domain port** the same way `FinancialInstrumentCatalog` does. **Final: put the inbound
capability interface in `financialinstrument.domain.ports` (e.g. `InstrumentProfileLookup`) —
implemented by the `GetInstrumentProfile` `@Service` — and FD004's adapter depends on that port.**
Then the existing ArchUnit rule passes unchanged.

Each new/changed rule verified **non-vacuous** (temp violating import → fail → revert).

---

## D11 — FD004 consumer: `EnMarketDataGatewayAdapter.sector(...)`

**Decision.** `portfolio.infrastructure.marketdata.EnMarketDataGatewayAdapter`:
- Add dependency `financialinstrument.domain.ports.InstrumentProfileLookup` (D10).
- `sector(String ticker, String market, String currencyCode)`:
  ```java
  try {
      InstrumentProfile p = instrumentProfileLookup.get(new InstrumentIdentity(new Ticker(ticker), new Mic(market)));
      return p.sector().isClassified() ? Optional.of(p.sector().classification()) : Optional.empty();
  } catch (RuntimeException e) {   // InstrumentProfileUnavailableException / bad identity
      logUnavailable("profile", ticker, market, e);
      return Optional.empty();
  }
  ```
- Drop imports of `marketdata` `InstrumentProfilePort` / `InstrumentProfile` / `Sector`.
- `latestPrice(...)` and `fxRate(...)` **unchanged**.

`EnMarketDataGatewayAdapterTest` — replace the `InstrumentProfilePort` mock with an
`InstrumentProfileLookup` mock; keep the same behavior assertions (classified → value, unclassified
→ empty, exception → empty). `portfolio.domain` / `portfolio.business` / calculator / persistence /
API / UI: **no change** (FR-038).

**Rationale.** FD004's `MarketDataGateway` ACL is exactly for absorbing this. The `Ticker`/`Mic`
value objects are `financialinstrument.domain.model` — `portfolio.infrastructure` importing them is
allowed (infrastructure → another module's domain port + its argument types, AR-062 precedent).

---

## D12 — E2E: combined `market-data-stub` + `FRANKFURTER_BASE_URL`

**Decision.**
- Rename `implementation/platform/e2e/finnhub-stub/` → `market-data-stub/`. `server.js` serves:
  `/quote?symbol=` (unchanged), `/stock/profile2?symbol=` (unchanged), **`/v1/latest?base=&symbols=`**
  (new — Frankfurter shape: `{ "amount":1.0, "base":"USD", "date":"2026-09-03", "rates": { "EUR": 0.80 } }`
  / `base=EUR` → `{ "rates": { "USD": 1.25 } }`). **Remove** `/forex/rates`.
- `compose.e2e.yaml`: service `finnhub-stub` → `market-data-stub` (image `finai/market-data-stub:local`);
  backend env `FINNHUB_BASE_URL: http://market-data-stub:8080` **and**
  `FRANKFURTER_BASE_URL: http://market-data-stub:8080`; `FINNHUB_API_KEY: e2e-stub`.
- `e2e.sh`: build step + health wait renamed to `market-data-stub`.
- `application.yml`: add `frankfurter.base-url: ${FRANKFURTER_BASE_URL:https://api.frankfurter.dev}`.
- `compose.yaml` (local `./start.sh`): add `FRANKFURTER_BASE_URL: ${FRANKFURTER_BASE_URL:-https://api.frankfurter.dev}`
  passthrough (parity with `FINNHUB_BASE_URL`; default works with no `.env` entry).
- **FD004 E2E specs unchanged (A10)**: `fd004-valuation.spec.ts` still creates AAPL + SAN; the stub
  still fakes the SAN quote; FX now comes from the stub's `/v1/latest` with the same 0.80 / 1.25 →
  the E2E-001 assertions (`€2,100.00` / `$2,625.00`, `76.19 %` / `23.81 %`) are unchanged.
  `fd004-provider-failure.spec.ts` still uses MSFT (stub returns `{}` for its quote) → FAILED.
  Profile is now DB-first: first request is a miss → stub `/stock/profile2` → persisted; unchanged
  from the test's point of view.

**Rationale.** OD-3 — one stub, minimal compose churn; `./start.sh` unaffected (E2E-only override).
FD004's approved E2E numbers stay put.

---

## D13 — Telemetry, JaCoCo, test matrix, sequencing

**Telemetry (FR-033).** Each provider adapter's structured log gains `capability=` alongside the
existing `provider=` / `operation=` / `outcome=` / `httpStatusCategory=` / `latencyMs=`:
`capability=market-price` (Finnhub quote), `capability=instrument-profile` (Finnhub profile),
`capability=fx-rate` (Frankfurter). Event name unified to `event=ProviderCall`.

**JaCoCo.** `pom.xml` excludes += `financialinstrument/infrastructure/finnhub/dto/**`,
`financialinstrument/infrastructure/finnhub/config/**`,
`marketdata/infrastructure/frankfurter/dto/**`. `financialinstrument/infrastructure/persistence/entity/**`
already excluded. Bundle gate stays ≥ 90 % line & branch — the new deterministic code
(`GetInstrumentProfile`, `FrankfurterFxRateMapper`, `FinnhubProfileMapper`,
`InstrumentProfilePersistenceMapper`, `JpaInstrumentProfileRepositoryAdapter`,
`FrankfurterFxRateAdapter` error paths) is covered by the tests below.

**Test matrix:**

| Test | Kind | Covers |
|---|---|---|
| `GetInstrumentProfileTest` | unit, **RED-first** (Mockito) | D6 — local hit no-call; miss calls + persists; provider fail → neutral exception, no persist; returned = saved |
| `FrankfurterFxRateAdapterTest` | unit (`MockRestServiceServer`) | D1 — `GET /v1/latest?base=USD&symbols=EUR` no auth; happy USD→EUR + EUR→USD; missing `rates` key → `FxRateUnavailable`; malformed body → `FxRateUnavailable`; `5xx`/timeout → unavailable; `from == to` → `IllegalArgumentException` |
| `FrankfurterFxRateMapperTest` | unit | rate/observedAt/source mapping; absent target key |
| `FrankfurterRestClientTest` + logging test | unit | request shape, no secret, `provider=frankfurter capability=fx-rate` structured log |
| `FinnhubInstrumentProfileAdapterTest` | unit (`MockRestServiceServer`) | moved — `/stock/profile2` request + `X-Finnhub-Token`; sector/industry mapping; `{}` → unavailable; `401/403` → auth; `429` → rate-limited; `exchange` not used as MIC |
| `FinnhubProfileMapperTest` | unit | moved — `finnhubIndustry` verbatim → `Sector.of`; unclassified |
| `InstrumentProfilePersistenceMapperTest` | unit | domain ⇄ entity, `Sector.UNCLASSIFIED` ⇄ `'Unclassified'` |
| `JpaInstrumentProfileRepositoryAdapterIT` | **Testcontainers** PG | round-trip; upsert (2 saves → 1 row); miss → empty; `V5` applied |
| `GetInstrumentProfileIT` | **Testcontainers** PG (+ stubbed provider bean) | full DB-first: miss → provider → persisted → 2nd call is a local hit (provider bean not called again) |
| `ProviderSelectionTest` | `@SpringBootTest` slice | `market-data.fx.provider=frankfurter` ⇒ FX bean is Frankfurter; `market-data.price.provider=finnhub` ⇒ price bean is Finnhub; profile likewise; no provider-identity branch in core (ArchUnit) |
| `MarketDataModelTest` / existing marketdata tests | unit | updated — no Finnhub FX / profile refs; `DataSource.FRANKFURTER` |
| `FinnhubIntegrationIT` | `@SpringBootTest extends PostgresContainerSupport` | updated — price + (FX via Frankfurter, no key needed); profile via `GetInstrumentProfile`; blank Finnhub key ⇒ price/profile unavailable, FX still works |
| `FinnhubProviderSmokeTest` | opt-in (`FINNHUB_SMOKE=1`) | updated — drop forex probe; + optional Frankfurter live probe |
| `EnMarketDataGatewayAdapterTest` (FD004) | unit | D11 — `InstrumentProfileLookup` mock replaces `InstrumentProfilePort`; same assertions |
| `StandardArchitectureRulesTest` | ArchUnit | D10 rule delta; all non-vacuous |
| `fd004-valuation.spec.ts` / `fd004-provider-failure.spec.ts` | Playwright E2E | unchanged; run against `market-data-stub` |
| full FD001/FD002/FD003/FD004/EN004 suites | Surefire + Failsafe + `ng test` + `e2e.sh` | non-regression (FR-040) |

**Sequencing (OD-8) — 5 green checkpoints:** C1 FX swap · C2 config + telemetry · C3 DB-first
profile + FD004 rewrite + `marketdata` profile deletion · C4 E2E stub merge + FD004 E2E · C5
ArchUnit/JaCoCo/docs/runtime smoke.

---

## Research summary

| # | Topic | Outcome |
|---|---|---|
| D1 | Frankfurter FX adapter | new `frankfurter` package: props + own `RestClient` + DTO + mapper + adapter; `DataSource.FRANKFURTER` |
| D2 | Remove Finnhub FX | delete adapter/DTO/mapper/client-method/enum; FX TTL → `frankfurter.cache` |
| D3 | Provider selection | `market-data.{price,profile,fx}.provider` + `@ConditionalOnProperty(matchIfMissing=true)`; no core branch |
| D4 | FI profile domain model | `InstrumentProfile` + `Sector` + `ProfileSource` in `financialinstrument.domain.model`; reuse `InstrumentIdentity` |
| D5 | FI profile ports | `InstrumentProfilePort` (provider) + `InstrumentProfileRepositoryPort` + `InstrumentProfileLookup` (inbound domain port for consumers) + neutral exceptions |
| D6 | `GetInstrumentProfile` | DB-first; return profile / throw neutral on total-unavailable; RED-first |
| D7 | `V5__instrument_profile.sql` | dedicated table, `UNIQUE(ticker, market_mic)`, upsert; EN004 untouched |
| D8 | Profile persistence stack | entity + JPA repo (native upsert) + mapper + adapter; Testcontainers IT |
| D9 | Move Finnhub profile adapter | to `financialinstrument.infrastructure.finnhub`, own `RestClient`; duplicate the symbol resolver + CSV |
| D10 | ArchUnit delta | rewrite `restclient` rule; +4 rules (frankfurter confinement, FI domain/business provider-free, FI ⊥ marketdata, finnhub-profile confinement); FD004 consumer uses `InstrumentProfileLookup` (domain port) so existing `portfolio ⊥ financialinstrument.infrastructure/business` rule stays green |
| D11 | FD004 consumer | `EnMarketDataGatewayAdapter.sector(...)` → `InstrumentProfileLookup`; adapter test updated; FD004 core unchanged |
| D12 | E2E | `finnhub-stub` → `market-data-stub` (+ `/v1/latest`, − `/forex/rates`); `FRANKFURTER_BASE_URL`; FD004 E2E numbers unchanged |
| D13 | Telemetry / JaCoCo / tests / sequencing | `capability=` log key; 3 new JaCoCo dto/config excludes; full matrix; 5 checkpoints |

No `NEEDS CLARIFICATION`. No new deployable / broker / scheduler / dependency / `product/` edit.
