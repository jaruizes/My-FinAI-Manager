# EN005 — Establish Finnhub Market Data Integration

> **Status:** Approved  
> **Enabler ID:** EN005  
> **Enabler Name:** Establish Finnhub Market Data Integration  
> **Supports:** Future portfolio valuation and allocation features  
> **Last Updated:** 2026-09-03  

---

# 1. Purpose

Establish a provider-neutral technical capability that allows My-FinAI-Manager to obtain external market and company data from Finnhub using an API Key.

The capability must support future calculation of:

- latest/available market value of a Position;
- Portfolio value;
- sector classification;
- Portfolio allocation by sector;
- Portfolio valuation expressed in EUR;
- Portfolio valuation expressed in USD.

EN005 integrates Finnhub as the initial external provider while preserving provider independence in the domain and business layers.

EN005 does not itself define Portfolio valuation UI or Portfolio allocation UX.

---

# 2. Motivation

Existing Portfolio Positions contain deterministic information such as:

```text
ticker
market
quantity
currency
average purchase price
```

To value a Portfolio, the platform additionally needs:

```text
latest market price
company sector / industry
FX rate
```

Finnhub provides the initial source for those capabilities through:

```text
/quote
/stock/profile2
/forex/rates
```

The Finnhub API is accessed using an API Key configured outside source control.

---

# 3. Scope

## In Scope

- Configure Finnhub integration through an API Key.
- Keep the API Key outside source control.
- Provide provider-neutral domain ports for:
  - market prices;
  - instrument/company profile;
  - FX rates.
- Implement Finnhub infrastructure adapters for those ports.
- Use Finnhub `/quote` to obtain stock price information.
- Use Finnhub `/stock/profile2` to obtain company profile information including:
  - ticker;
  - company name;
  - currency;
  - exchange;
  - sector/industry classification when available.
- Use Finnhub `/forex/rates` to obtain exchange rates.
- Support at least USD → EUR and EUR → USD.
- Preserve timestamps/source information needed to understand data freshness.
- Provide explicit failure behavior when Finnhub data is unavailable or incomplete.
- Add deterministic automated tests without depending on the live Finnhub service.
- Preserve ADR-003 Spring architecture.
- Use Maven.
- Use OpenTelemetry-compatible observability patterns already established by the platform.

## Out of Scope

- Portfolio valuation business rules.
- Portfolio valuation UI.
- Sector allocation UI.
- Historical Portfolio valuation.
- Intraday charts.
- Historical stock prices.
- Historical FX series.
- Currency support beyond EUR and USD unless required by a later feature.
- AI-based valuation.
- AI-based sector classification.
- News.
- Recommendations.
- Stop-Loss calculation.
- Direct frontend calls to Finnhub.
- Replacing EN004 as the canonical Financial Instrument Catalog.
- Making Finnhub's exchange representation the canonical market identity.

---

# 4. Architectural Principle

Finnhub is an infrastructure provider, not a domain dependency.

The business/core architecture should consume provider-neutral ports:

```text
MarketDataPort
InstrumentProfilePort
FxRatePort
```

Conceptually:

```text
                    business
                       │
        ┌──────────────┼──────────────┐
        │              │              │
        ▼              ▼              ▼
 MarketDataPort  InstrumentProfilePort  FxRatePort
        ▲              ▲              ▲
        └──────────────┼──────────────┘
                       │
                FinnhubAdapter(s)
                       │
                       ▼
                  Finnhub API
```

The dependency direction remains:

```text
infrastructure → business → domain
```

Business/domain code must not depend on Finnhub SDK types, Finnhub HTTP response objects, Finnhub field names, or Finnhub authentication mechanisms.

---

# 5. Functional Module

The capability should live in an appropriate functional module without creating a new deployable service by default.

A possible structure:

```text
com.myfinaimanager.core.marketdata
│
├── domain/
│   ├── model/
│   ├── ports/
│   └── exceptions/
│
├── business/
│
└── infrastructure/
    └── finnhub/
        ├── client/
        ├── dto/
        ├── mapper/
        └── config/
```

If instrument profile enrichment belongs more naturally inside the existing `financialinstrument` module, the planning phase may place the corresponding port/use case there.

Module placement must preserve domain cohesion and ADR-003 dependency rules.

---

# 6. Provider Configuration

Finnhub access must be configured using an API Key.

Conceptually:

```text
FINNHUB_API_KEY
```

The API Key must:

- not be committed to Git;
- not be hard-coded in application source;
- not be hard-coded in Docker images;
- not be returned through APIs;
- not be written to logs;
- be injected through runtime configuration.

A Spring configuration may conceptually use:

```text
finnhub.api-key=${FINNHUB_API_KEY}
finnhub.base-url=https://finnhub.io/api/v1
```

Exact property names are implementation details.

Local development may use `.env` or equivalent local environment configuration provided the secret file is excluded from version control.

Deployment environments must use the project's approved secret-management mechanism.

---

# 7. Market Price Capability

The platform must provide a provider-neutral capability to obtain the latest available market price for a Financial Instrument.

Conceptual port:

```java
MarketPrice getLatestPrice(InstrumentIdentifier instrument);
```

The domain result should conceptually contain:

```text
MarketPrice
- ticker
- market?
- price
- currency
- observedAt
- source
```

Exact domain design belongs to specification/planning.

---

# 8. Finnhub Quote Endpoint

Finnhub `/quote` is the initial provider endpoint for stock price information.

Conceptually:

```text
GET /quote?symbol={symbol}
```

Relevant response information includes:

```text
c   current/latest price
d   change
dp  percentage change
h   high
l   low
o   open
pc  previous close
t   timestamp
```

EN005 requires only the information needed for current Portfolio valuation unless a later feature explicitly needs additional fields.

For valuation purposes, the initial candidate is:

```text
price = c
```

The implementation must not silently use zero or a missing price as a valid market value.

---

# 9. Instrument Profile Capability

The platform must provide a provider-neutral capability to obtain external profile/classification information for a Financial Instrument.

Conceptual port:

```java
InstrumentProfile getProfile(InstrumentIdentifier instrument);
```

The result may conceptually contain:

```text
InstrumentProfile
- ticker
- name
- sector
- industry?
- currency
- providerExchange?
- source
- observedAt?
```

Provider-specific field names must not leak outside infrastructure.

---

# 10. Finnhub Company Profile 2 Endpoint

Finnhub `/stock/profile2` is the initial endpoint used for company profile information.

Conceptually:

```text
GET /stock/profile2?symbol={symbol}
```

Relevant Finnhub data may include:

```text
ticker
name
currency
exchange
finnhubIndustry
```

The initial mapping is:

```text
Finnhub ticker           → provider ticker/reference
Finnhub name             → company/instrument name
Finnhub currency         → quote/profile currency metadata
Finnhub finnhubIndustry  → initial sector/industry classification
Finnhub exchange         → provider metadata only
```

The Finnhub `exchange` field must not replace EN004's canonical ISO 10383 MIC.

---

# 11. Relationship with EN004

EN004 remains the canonical source for Financial Instrument identity.

Example:

```text
EN004
FinancialInstrument
- ticker   = AAPL
- market   = XNAS
- currency = USD
```

Finnhub enrichment:

```text
/quote
→ price

/profile2
→ name
→ currency metadata
→ exchange description
→ finnhubIndustry
```

Canonical identity remains:

```text
ticker + market(MIC)
```

Finnhub data enriches that identity but does not redefine it.

---

# 12. Symbol Resolution

EN004 may contain:

```text
ticker
market
providerSymbol
```

The Finnhub adapter must use an explicitly resolved provider symbol appropriate for Finnhub.

The implementation must not assume that every canonical ticker can always be sent unchanged to Finnhub.

Symbol-resolution rules must be provider-specific infrastructure behavior.

If a Financial Instrument cannot be resolved to a valid Finnhub symbol, the adapter must return an explicit unavailable/unresolved result rather than guessing.

---

# 13. FX Rate Capability

The platform must provide a provider-neutral capability for currency conversion.

Conceptual port:

```java
FxRate getRate(Currency from, Currency to);
```

The domain result should conceptually contain:

```text
FxRate
- fromCurrency
- toCurrency
- rate
- observedAt
- source
```

---

# 14. Finnhub Forex Rates Endpoint

Finnhub `/forex/rates` is the initial endpoint for FX rates.

Conceptually:

```text
GET /forex/rates?base=USD
GET /forex/rates?base=EUR
```

The integration must support, at minimum:

```text
USD → EUR
EUR → USD
```

The provider response must be mapped into a provider-neutral `FxRate`.

---

# 15. Currency Conversion Rules

The FX adapter provides rates.

It does not own Portfolio valuation arithmetic.

A future valuation capability may use:

```text
convertedValue = originalValue × fxRate
```

The business layer must use decimal-safe numeric types appropriate for financial calculations.

Floating-point binary arithmetic must not be used for monetary calculations where deterministic decimal arithmetic is required.

---

# 16. Future Portfolio Valuation Support

EN005 must provide enough information for a later feature to calculate:

```text
positionValue = quantity × latestPrice
normalizedPositionValue = positionValue × fxRate   # when conversion is required
portfolioValue = Σ normalizedPositionValue
```

A future feature may expose totals such as:

```text
Portfolio Value
€23,421.76
$27,114.43
```

EN005 supplies price/profile/FX data only.

The calculation itself remains deterministic business logic.

---

# 17. Future Sector Allocation Support

EN005 must provide sector/classification information sufficient for a future feature to calculate sector weights.

Conceptually:

```text
AAPL → Technology
MSFT → Technology
SAN  → Financial Services
IBE  → Utilities
```

A future feature can calculate:

```text
sectorValue = Σ normalized position value for positions in sector
sectorWeight = sectorValue / portfolioValue
```

EN005 must not use an LLM to infer a sector when Finnhub does not provide one.

Missing sector data must remain explicitly unknown/unclassified unless another approved deterministic source is introduced.

---

# 18. Data Freshness

Market price and FX information are time-sensitive.

Every returned price or exchange rate should retain enough information to understand when it was observed.

At minimum:

```text
source
observedAt
```

where supported by the provider.

For data without an explicit provider timestamp, the adapter may record retrieval time separately from provider observation time.

---

# 19. Caching

EN005 may introduce short-lived caching to reduce Finnhub API calls, rate-limit pressure, latency, and repeated requests.

Suggested initial behavior:

```text
company profile → longer-lived cache
market price    → short-lived cache
FX rate         → short-lived cache
```

Exact TTL values must be defined during specification/planning based on Finnhub plan limitations and product freshness requirements.

Caching must not hide data freshness.

---

# 20. Rate Limits

The integration must tolerate provider rate limits.

The adapter must distinguish rate-limit failures from authentication failures, symbol-not-found, malformed responses, network failures, and provider server failures.

The implementation should avoid one external request per UI-rendered field when data can be fetched/reused efficiently.

Rate-limit handling must not fabricate market data.

---

# 21. Error Model

Provider failures must be translated into provider-neutral failures.

Conceptually:

```text
MarketDataUnavailable
InstrumentProfileUnavailable
FxRateUnavailable
ProviderRateLimited
ProviderAuthenticationFailed
InstrumentNotResolved
```

Finnhub-specific HTTP codes or DTOs must not propagate into domain/business APIs.

---

# 22. Partial Data

The system must support partial availability.

Examples:

```text
price available
sector unavailable
FX available
```

or:

```text
profile available
price unavailable
```

EN005 must not treat unrelated missing enrichment data as valid zero values.

A later Portfolio valuation feature must decide which missing inputs block valuation and which only reduce enrichment quality.

---

# 23. Security

The Finnhub API Key is a secret.

The implementation must ensure:

- no API Key in Git;
- no API Key in frontend bundles;
- no API Key in OpenAPI examples;
- no API Key in logs;
- no API Key in error responses;
- no API Key in traces;
- no direct browser-to-Finnhub communication.

All Finnhub calls are backend-to-provider calls.

---

# 24. Observability

Outbound Finnhub calls should be observable.

At minimum, capture:

```text
provider = finnhub
operation
success/failure
latency
HTTP status category
```

Do not include the API Key in logs, spans, or metrics labels.

Where OpenTelemetry instrumentation already exists, outbound HTTP calls should participate in platform tracing.

---

# 25. HTTP Client

The Finnhub integration must use the project's approved Java/Spring HTTP-client approach.

A third-party Finnhub SDK is not required.

Direct HTTP integration is preferred when it keeps the API contract explicit, dependency footprint small, provider DTOs contained in infrastructure, and testability straightforward.

The exact Spring HTTP client is decided during planning according to the current platform conventions.

---

# 26. Testing Strategy

Automated tests must not depend on live Finnhub.

At minimum:

## Unit Tests

- quote DTO → domain MarketPrice mapping;
- profile DTO → domain InstrumentProfile mapping;
- FX response → domain FxRate mapping;
- null/missing price handling;
- missing sector handling;
- unsupported symbol handling;
- provider-specific exchange does not replace canonical MIC.

## Adapter / Integration Tests

Use WireMock or equivalent deterministic HTTP stubbing to verify:

- correct Finnhub endpoint;
- correct query parameters;
- API Key sent correctly;
- successful quote response;
- successful profile response;
- successful FX response;
- authentication failure;
- rate limit;
- provider failure;
- malformed payload;
- timeout/network behavior.

Live Finnhub calls must not be required for CI.

---

# 27. Required Ports

The formal design should preserve three independently replaceable capabilities:

```text
MarketDataPort
InstrumentProfilePort
FxRatePort
```

Even if all three are initially implemented through Finnhub.

This must allow future combinations such as:

```text
MarketDataPort       → Finnhub
InstrumentProfilePort → Finnhub
FxRatePort           → ECB adapter
```

without changing Portfolio valuation business logic.

---

# 28. Verification Criteria

## VC-001 — API Key Configuration
Finnhub can be configured through an external API Key.

## VC-002 — Secret Protection
The API Key is not committed, returned, traced, or logged.

## VC-003 — Quote
The backend can obtain and normalize a Finnhub quote for a supported instrument.

## VC-004 — Profile
The backend can obtain and normalize Finnhub company profile data.

## VC-005 — Sector
The normalized profile exposes Finnhub sector/industry classification when available.

## VC-006 — Exchange Independence
Finnhub's exchange description does not replace EN004's canonical MIC.

## VC-007 — USD/EUR FX
The backend can obtain a USD → EUR rate.

## VC-008 — EUR/USD FX
The backend can obtain a EUR → USD rate.

## VC-009 — Provider-Neutral Ports
Business/domain code depends on provider-neutral ports rather than Finnhub DTOs.

## VC-010 — Deterministic Calculation Inputs
Price and FX values are exposed using decimal-safe values suitable for deterministic calculation.

## VC-011 — Timestamp/Freshness
Price and FX results include adequate freshness/source metadata.

## VC-012 — Missing Data
Missing prices, sectors, or FX values are not silently converted to zero or fabricated values.

## VC-013 — Rate Limits
Finnhub rate-limit responses are recognized and handled explicitly.

## VC-014 — Authentication Failure
Invalid API Key behavior is represented explicitly.

## VC-015 — No Frontend Finnhub Calls
The frontend does not call Finnhub directly.

## VC-016 — Tests
Automated tests execute without live Finnhub access.

## VC-017 — ADR-003
The implementation complies with the standard Spring package/dependency architecture.

## VC-018 — Future Portfolio Valuation Ready
The resulting ports provide enough data for a future feature to calculate Portfolio value in EUR and USD.

## VC-019 — Future Sector Allocation Ready
The resulting profile capability provides enough classification data for a future feature to calculate sector allocation.

---

# 29. E2E / External Provider Testing

EN005 does not require normal application E2E tests to call the live Finnhub service.

The provider integration should be tested deterministically at adapter/integration level.

A manually executable provider smoke test may be provided to validate a real API Key, but:

- it must be opt-in;
- it must not run by default in CI;
- it must not expose the API Key;
- failure due to external rate limits/network must remain distinguishable from application test failures.

Future Portfolio valuation E2E tests should stub or control the Finnhub boundary while exercising the real application stack unless a specifically approved external-provider E2E policy says otherwise.

---

# 30. Explicit Technical Decisions

1. Finnhub is the initial external provider.
2. Finnhub authentication uses an externally configured API Key.
3. `/quote` provides the initial market price source.
4. `/stock/profile2` provides initial profile and sector classification.
5. `/forex/rates` provides initial EUR/USD exchange-rate information.
6. Three provider-neutral ports are preserved:
   - `MarketDataPort`;
   - `InstrumentProfilePort`;
   - `FxRatePort`.
7. Finnhub does not replace EN004's canonical instrument identity.
8. Finnhub exchange descriptions are provider metadata only.
9. Valuation arithmetic remains deterministic and outside the provider adapter.
10. Monetary and FX calculations use decimal-safe numeric representations.
11. Missing provider data is never fabricated.
12. The frontend never accesses Finnhub directly.
13. Automated tests do not require live Finnhub.
14. Provider data freshness remains visible.
15. Caching is allowed but must preserve freshness semantics.

---

# 31. Open Technical Decisions

The following should be resolved during specification/planning:

1. Exact Spring HTTP client implementation.
2. Exact timeout configuration.
3. Retry policy, if any.
4. Exact caching mechanism.
5. Cache TTL for quote data.
6. Cache TTL for company profile data.
7. Cache TTL for FX data.
8. Exact Finnhub symbol-resolution strategy for non-US instruments.
9. Whether company profile enrichment is persisted locally or cached only.
10. Whether price data is cached only or persisted as a snapshot.
11. Whether FX rates are cached only or persisted as a snapshot.
12. Whether `finnhubIndustry` is mapped directly to `sector` initially or represented as provider classification pending a canonical taxonomy.
13. Exact decimal precision/scale for FX rates.
14. Exact decimal precision/scale for prices.
15. Exact behavior when a quote returns `c = 0` or missing data.
16. Whether the future valuation feature requests both EUR and USD rates independently or derives inverse rates when mathematically safe and freshness-equivalent.

These decisions must not introduce Portfolio business behavior into EN005.

---

# 32. Human Approval

Before formal specification:

- [X] Purpose is correct.
- [X] Finnhub is approved as the initial provider.
- [X] API Key configuration is approved.
- [X] `/quote` usage is approved.
- [X] `/stock/profile2` usage is approved.
- [X] `/forex/rates` usage is approved.
- [X] EUR/USD conversion support is approved.
- [X] Three independent provider-neutral ports are approved.
- [X] EN004 remains canonical for instrument identity.
- [X] Finnhub exchange metadata does not replace MIC.
- [X] Sector enrichment from Finnhub is approved.
- [X] Deterministic valuation arithmetic remains outside the adapter.
- [X] Live Finnhub access is not required for automated CI tests.
- [X] No Portfolio valuation UI/business behavior is introduced.
- [X] No unapproved behavior has been added.

**Approved by:*jaruiz*  
**Date:*2026-09-03*  
**Status:** Approved
