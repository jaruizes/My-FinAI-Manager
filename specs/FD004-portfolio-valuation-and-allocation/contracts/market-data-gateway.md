# Contract — `MarketDataGateway` (portfolio → EN005 ACL port)

**Package**: `com.myfinaimanager.core.portfolio.domain.ports` · **Adapter**:
`com.myfinaimanager.core.portfolio.infrastructure.marketdata.EnMarketDataGatewayAdapter`

Rationale + alternatives: [research.md](../research.md) D2. Enforces **AR-062** (inter-module read
via the consumer's published port, invoked only from `infrastructure`) and **FR-026 / FR-035 /
SC-007** (no `marketdata` or Finnhub type in `portfolio.domain` / `portfolio.business`).

---

## Port interface

```java
public interface MarketDataGateway {

    /** Latest market price for the instrument, in its native currency. Empty when unavailable
     *  for ANY reason (not resolved, provider error/timeout, not configured, malformed currency). */
    Optional<PositionPricing> latestPrice(String ticker, String market, String currencyCode);

    /** Provider sector/industry classification string for the instrument.
     *  Empty when the profile is unavailable OR the provider returns an unclassified profile. */
    Optional<String> sector(String ticker, String market, String currencyCode);

    /** FX conversion rate `fromCurrencyCode -> toCurrencyCode` (multiply a `from` amount by it).
     *  Empty when the rate is unavailable for any reason. */
    Optional<FxConversion> fxRate(String fromCurrencyCode, String toCurrencyCode);
}
```

`domain.model` records (both: value `> 0`, `observedAt` non-null):

```java
public record PositionPricing(BigDecimal price, Instant observedAt) {}
public record FxConversion(BigDecimal rate, Instant observedAt) {}
```

Arguments are **primitives** (`String`) so no EN005 enum (`SupportedCurrency`) or value object
(`InstrumentIdentifier`) reaches `portfolio.domain`. `currencyCode` / `fromCurrencyCode` /
`toCurrencyCode` are ISO-4217 3-letter strings (`"EUR"`, `"USD"`); an unrecognized value ⇒ the
method returns `Optional.empty()` (no exception).

---

## Adapter behavior (`EnMarketDataGatewayAdapter`)

The **only** `portfolio`-module class permitted to import `com.myfinaimanager.core.marketdata.*`.

| Call | EN005 delegate | Mapping |
|---|---|---|
| `latestPrice(t, m, c)` | `MarketDataPort.getLatestPrice(new InstrumentIdentifier(t, m, SupportedCurrency.valueOf(c)))` | `MarketPrice{amount, observedAt}` → `PositionPricing(amount, observedAt)` |
| `sector(t, m, c)` | `InstrumentProfilePort.getProfile(identifier)` | `InstrumentProfile.sector()` → `Sector.UNCLASSIFIED` ⇒ `Optional.empty()`; classified ⇒ `Optional.of(sector.classification())` |
| `fxRate(from, to)` | `FxRatePort.getRate(SupportedCurrency.valueOf(from), SupportedCurrency.valueOf(to))` | `FxRate{rate, observedAt}` → `FxConversion(rate, observedAt)` |

**Exception translation — every failure becomes `Optional.empty()`:**

| EN005 signal | Adapter result |
|---|---|
| `InstrumentNotResolvedException` | `Optional.empty()` |
| `MarketDataUnavailableException` / provider `5xx` / timeout | `Optional.empty()` |
| `MarketDataRateLimitedException` (`429`) | `Optional.empty()` |
| `MarketDataNotConfiguredException` (blank Finnhub key) | `Optional.empty()` |
| any other `MarketDataException` subtype | `Optional.empty()` |
| `IllegalArgumentException` from `SupportedCurrency.valueOf` (bad `currencyCode`) | `Optional.empty()` |
| happy path, classified profile | `Optional.of(...)` |
| happy path, unclassified profile | `Optional.empty()` (from `sector(...)` only) |

Each empty outcome is logged **once** at `DEBUG` with a structured reason code
(`event=MarketDataUnavailable reason=<code> ticker=<t> market=<m>`) — no stack trace to callers, no
secret, no Finnhub URL/key (FR-026, constitution XVI). The adapter never rethrows an EN005 type and
never returns `null`.

---

## Consumer usage (`PortfolioValuationService`, `portfolio.business`)

Per Position: `latestPrice(ticker, market, nativeCurrency)` and `sector(ticker, market,
nativeCurrency)`. FX (research D8): if any USD Position ⇒ `fxRate("USD", "EUR")`; if any EUR
Position ⇒ `fxRate("EUR", "USD")`. Results feed `PortfolioValuationCalculator` as
`Optional`-valued `PositionInput` / `FxContext` fields — the calculator, not the gateway, decides
`valued` / status.

---

## Tests

`EnMarketDataGatewayAdapterTest` (Mockito mocks of the three EN005 ports) — one case per row of the
translation table above, plus the happy path for each method. ArchUnit rules
`portfolio_valuation_domain_and_business_do_not_depend_on_marketdata` and
`marketdata_is_accessed_only_from_the_portfolio_marketdata_adapter_package` (research D11) guard the
boundary; both verified non-vacuous.
