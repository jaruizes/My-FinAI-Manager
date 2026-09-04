# Contract — market-data ports (EN005)

> **Revision 2 (2026-09-04).** `marketdata` now owns **two** provider ports —
> `MarketDataPort` (price, Finnhub) and `FxRatePort` (FX, **Frankfurter** — see
> [frankfurter-provider-contract.md](./frankfurter-provider-contract.md)). The **instrument-profile
> capability moved to the `financialinstrument` module** (Q1): the outbound
> `financialinstrument.domain.ports.InstrumentProfilePort` (external provider), the
> `InstrumentProfileRepositoryPort` (persistence), and the inbound
> `financialinstrument.domain.ports.InstrumentProfileLookup` (`InstrumentProfile get(InstrumentIdentity)`,
> **database-first**) — consumed by FD004 from `portfolio.infrastructure` exactly as
> `FinancialInstrumentCatalog` is (AR-062). Port + neutral-exception **names are unchanged** (Q2).
> `CachingInstrumentProfilePort` is removed; `CachingMarketDataPort` / `CachingFxRatePort` stay.

**In-process** interfaces for the EN005 capability. There is **no HTTP contract** exposed by EN005
(FR-024). No `openapi.yaml` change.

```text
future valuation feature (business)
        │  depends on
        ▼
marketdata.domain.ports.{MarketDataPort, InstrumentProfilePort, FxRatePort}
        ▲  @Primary implementation
marketdata.infrastructure.finnhub.cache.{CachingMarketDataPort, CachingInstrumentProfilePort, CachingFxRatePort}
        │  delegates to
marketdata.infrastructure.finnhub.{FinnhubMarketDataAdapter, FinnhubInstrumentProfileAdapter, FinnhubFxRateAdapter}
        │  uses
marketdata.infrastructure.finnhub.client.FinnhubRestClient  ──►  Finnhub REST API  (X-Finnhub-Token header)
        │  and
marketdata.infrastructure.finnhub.resolver.FinnhubSymbolResolver
```

---

## C1 — `MarketDataPort`

```java
MarketPrice getLatestPrice(InstrumentIdentifier instrument);
```

| # | Invariant |
|---|---|
| P1 | Returns a `MarketPrice` with `price > 0` (decimal-safe `BigDecimal`), the instrument's `currency`, `source = FINNHUB`, and freshness metadata (`observedAt`, `observedAtSource`). |
| P2 | A Finnhub `/quote` with `c = 0` / absent / null price → **`MarketDataUnavailableException`** — never a `MarketPrice` with price `0`. |
| P3 | `429` → `ProviderRateLimitedException`; `401`/`403` → `ProviderAuthenticationFailedException`; other `4xx`/`5xx`/malformed/timeout/network → `MarketDataUnavailableException`. |
| P4 | An instrument the `FinnhubSymbolResolver` cannot map → **`InstrumentNotResolvedException`**, with **no** outbound call. |
| P5 | Blank `finnhub.api-key` → **`MarketDataNotConfiguredException`**, with no outbound call. |
| P6 | Pure read — no persistence, no business event, no mutation of any state. Deterministic given the provider response + `Clock`. |
| P7 | Repeated identical calls within `finnhub.cache.quote-ttl` → **one** outbound Finnhub call (caching decorator); the cached result keeps its original `observedAt`. |
| P8 | The API key never appears in the return value, the exception message, or any log line produced on this path. |

**Consumers**: a future valuation feature (`positionValue = quantity × MarketPrice.price`).

---

## C2 — `InstrumentProfilePort`

```java
InstrumentProfile getProfile(InstrumentIdentifier instrument);
```

| # | Invariant |
|---|---|
| P1 | Returns an `InstrumentProfile` with `ticker`, `name`, `sector` (**never null**), and `source = FINNHUB`. |
| P2 | Finnhub `finnhubIndustry` present → `sector = Sector.of(finnhubIndustry)` (classified). Absent/blank → `sector = Sector.UNCLASSIFIED`. **No** LLM / heuristic inference. |
| P3 | Finnhub `exchange` → `providerExchange` **string metadata only**. It is never used as, mapped to, or allowed to override an ISO 10383 MIC. EN004 stays canonical for identity. |
| P4 | Finnhub `currency` → `currency` **metadata** (nullable); it does not redefine the Position currency. |
| P5 | Empty/malformed profile payload → **`InstrumentProfileUnavailableException`**. `429`/`401`/`403` → the rate-limit / auth exception. Unresolved symbol → `InstrumentNotResolvedException`. Blank key → `MarketDataNotConfiguredException`. |
| P6 | Pure read; deterministic; no persistence. |
| P7 | Cached for `finnhub.cache.profile-ttl` (long — a profile is not time-critical). |

**Consumers**: a future sector-allocation feature (`sectorWeight = sectorValue / portfolioValue`).

---

## C3 — `FxRatePort`

```java
FxRate getRate(SupportedCurrency from, SupportedCurrency to);   // from != to
```

| # | Invariant |
|---|---|
| P1 | `getRate(USD, EUR)` and `getRate(EUR, USD)` each return an `FxRate` with `rate > 0` (decimal-safe), `from` / `to` as requested, `source = FINNHUB`, `observedAt` set, `observedAtSource = RETRIEVAL_TIME` (`/forex/rates` carries no provider timestamp). |
| P2 | Each direction is fetched **independently** (`base=USD` for `USD→EUR`, `base=EUR` for `EUR→USD`) — no inversion in v1 (OD-EN005-7). |
| P3 | `/forex/rates` response missing the requested target currency → **`FxRateUnavailableException`** — the rate is never defaulted to `1`, `0`, or a guess. |
| P4 | `429`/`401`/`403`/`5xx`/malformed/timeout → the corresponding neutral exception. Blank key → `MarketDataNotConfiguredException`. |
| P5 | The adapter returns rates **only** — it contains no `convertedValue = value × rate` or any Portfolio-total arithmetic (that is deterministic business logic outside EN005). |
| P6 | Cached for `finnhub.cache.fx-ttl`. |

**Consumers**: a future valuation feature (`normalizedPositionValue = positionValue × FxRate.rate`).

---

## C4 — Exception → caller semantics

| Exception | Meaning to the caller (future feature) | Typical reaction |
|---|---|---|
| `MarketDataUnavailableException` | no usable price right now | skip valuation for this Position, or mark it stale |
| `InstrumentProfileUnavailableException` | no profile / sector right now | treat sector as unknown for this Position |
| `FxRateUnavailableException` | no conversion rate right now | cannot produce the converted total; show the base-currency total only |
| `ProviderRateLimitedException` | provider throttled | back off; retry later; serve last-known values with their age |
| `ProviderAuthenticationFailedException` | key invalid / rejected | operator action needed; capability effectively down |
| `InstrumentNotResolvedException` | this instrument has no Finnhub symbol | exclude from provider-backed enrichment; not a transient error |
| `MarketDataNotConfiguredException` | no API key in this environment | market-data features are not available here; unrelated features unaffected |

All messages are **key-free** and safe to log or surface in an internal diagnostic. None carries a
Finnhub HTTP status object or DTO (FR-012, FR-016).

---

## C5 — Tests that guard this contract

- `Finnhub{Quote,Profile,ForexRates}MapperTest` — P2 (each C), freshness (C1/C3 P1), `Sector`
  (C2 P2), `exchange` non-MIC (C2 P3).
- `FinnhubSymbolResolverTest` — C1/C2 P4.
- `FinnhubRestClientTest` (`MockRestServiceServer`) — the full status→exception table (C1 P3,
  C2 P5, C3 P4), the `X-Finnhub-Token` header, and **no key in any message/log** (C1 P8).
- `Finnhub{MarketData,InstrumentProfile,FxRate}AdapterTest` — end-to-end port behavior against the
  stub.
- `TtlCacheTest` + `CachingPortsTest` — C1 P7, C2 P7, C3 P6; `observedAt` preserved; failures not
  cached.
- `FinnhubConfigurationTest` — C*/P5 (blank key), timeouts configured.
