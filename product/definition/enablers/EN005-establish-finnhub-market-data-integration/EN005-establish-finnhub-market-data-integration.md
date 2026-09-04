# EN005 — Establish External Market Data Capabilities

> **Status:** Approved — Reopened for revision  
> **Enabler ID:** EN005  
> **Enabler Name:** Establish Finnhub Market Data Integration  
> **Supports:** Future portfolio valuation and allocation features  
> **Initial Providers:** Finnhub (price/profile), Frankfurter (FX)  
> **Last Updated:** 2026-09-04

---

# 1. Purpose

Establish provider-neutral market-data capabilities that allow My-FinAI-Manager core/business logic to request:

- latest available market price of a Financial Instrument;
- sector and company/instrument information;
- currency exchange rates.

The core must not know which external provider supplies any capability.

Finnhub is the initial provider for market-price and instrument-profile capabilities. Frankfurter is the initial provider for FX rates. Provider selection must remain an infrastructure concern so that each capability can change provider independently and multiple adapters can coexist without affecting core/business logic.

EN005 does not define Portfolio valuation calculations or Portfolio valuation UI.

---

# 2. Motivation

Portfolio valuation needs three independent capabilities:

```text
Get latest market price
Get instrument profile / sector
Get FX rate
```

The desired architecture is:

```text
Core / Business
      │
      ├── GetMarketPrice
      ├── GetInstrumentProfile
      └── GetFxRate
              │
              ▼
      provider-neutral ports
              │
      ┌───────┼────────┐
      ▼       ▼        ▼
 Price     Profile      FX
 Adapter   Adapter     Adapter
      │       │          │
      ▼       ▼          ▼
  Finnhub  Finnhub   Frankfurter
 initially initially   initially
```

A future configuration may be:

```text
MarketPriceProviderPort
→ AlphaVantageMarketPriceAdapter

InstrumentProfileProviderPort
→ FinnhubInstrumentProfileAdapter

FxRateProviderPort
→ EcbFxRateAdapter
```

with no change to Portfolio valuation business logic.

---

# 3. Architectural Decision

Provider identity belongs exclusively to infrastructure.

The initial design MUST NOT contain one monolithic `FinnhubAdapter` implementing every responsibility.

Each capability has its own port and its own adapter.

Required provider ports:

```text
MarketPriceProviderPort
InstrumentProfileProviderPort
FxRateProviderPort
```

Initial adapters:

```text
FinnhubMarketPriceAdapter
FinnhubInstrumentProfileAdapter
FrankfurterFxRateAdapter
```

Provider selection is independently configurable per capability.

---

# 4. Core-Facing Operations

The business layer must expose operations conceptually equivalent to:

```text
obtainMarketPrice(instrument)
obtainInstrumentProfile(instrument)
obtainFxRate(fromCurrency, toCurrency)
```

Possible Java use-case names:

```java
GetMarketPrice
    GetInstrumentProfile
GetFxRate
```

Core/business code must never call Finnhub clients or Finnhub-specific APIs directly.

---

# 5. Provider-Neutral Domain Models

## MarketPrice

```text
MarketPrice
- instrumentIdentifier
- price
- currency
- observedAt
- retrievedAt
- source
```

## InstrumentProfile

```text
InstrumentProfile
- instrumentIdentifier
- name
- sector
- industry?
- currency?
- lastUpdatedAt
- source?
```

## FxRate

```text
FxRate
- fromCurrency
- toCurrency
- rate
- observedAt
- retrievedAt
- source
```

Provider DTOs must never cross the infrastructure boundary.

---

# 6. Adapter per Capability

## 6.1 Market Price

Port:

```java
interface MarketPriceProviderPort {
    MarketPrice getLatestPrice(InstrumentIdentifier instrument);
}
```

Initial implementation:

```text
FinnhubMarketPriceAdapter
```

Initial endpoint:

```text
Finnhub /quote
```

The adapter owns provider-specific symbol resolution, authentication, HTTP invocation, DTO mapping, and provider-error translation.

---

## 6.2 Instrument Profile / Sector

External provider port:

```java
interface InstrumentProfileProviderPort {
    InstrumentProfile getProfile(InstrumentIdentifier instrument);
}
```

Initial implementation:

```text
FinnhubInstrumentProfileAdapter
```

Initial endpoint:

```text
Finnhub /stock/profile2
```

The adapter only retrieves and normalizes external profile data.

It must not decide whether local persistence should be queried first.

---

## 6.3 FX Rate

Port:

```java
interface FxRateProviderPort {
    FxRate getRate(Currency from, Currency to);
}
```

Initial implementation:

```text
FrankfurterFxRateAdapter
```

Initial endpoint:

```text
GET https://api.frankfurter.dev/v1/latest?base={fromCurrency}&symbols={toCurrency}
```

Examples:

```text
USD → EUR
GET https://api.frankfurter.dev/v1/latest?base=USD&symbols=EUR

EUR → USD
GET https://api.frankfurter.dev/v1/latest?base=EUR&symbols=USD
```

Frankfurter does not require an API Key for the public API.

The adapter must map the provider response into the provider-neutral `FxRate` model and must not leak Frankfurter-specific payloads into core/business code.

A future provider can replace this adapter independently.

---

# 7. Independent Provider Configuration

Provider selection must be configurable independently.

Conceptually:

```text
market-data.price.provider=finnhub
market-data.profile.provider=finnhub
market-data.fx.provider=frankfurter
```

Future configuration may be:

```text
market-data.price.provider=alpha-vantage
market-data.profile.provider=finnhub
market-data.fx.provider=ecb
```

Changing one provider must not require changes to Portfolio domain, Portfolio valuation business logic, API contracts, or the other adapters.

The initial provider assignment is therefore:

```text
price   → Finnhub
profile → Finnhub
FX      → Frankfurter
```

---

# 8. Finnhub Configuration

Finnhub authentication uses an externally supplied API Key:

```text
FINNHUB_API_KEY
```

The API Key must not be committed, hard-coded, exposed to frontend code, returned by APIs, logged, or traced.

Finnhub remains an infrastructure concern.

---

# 8A. Frankfurter Configuration

Frankfurter is the initial FX-rate provider.

Public API base URL:

```text
https://api.frankfurter.dev
```

Initial endpoint contract:

```text
GET /v1/latest?base={fromCurrency}&symbols={toCurrency}
```

For the initial EUR/USD scope:

```text
GET /v1/latest?base=USD&symbols=EUR
GET /v1/latest?base=EUR&symbols=USD
```

Example response shape:

```json
{
  "amount": 1.0,
  "base": "USD",
  "date": "2026-08-21",
  "rates": {
    "EUR": 0.85477
  }
}
```

The adapter maps:

```text
base            → fromCurrency
rates[target]   → rate
date            → observedAt/date
retrieval time  → retrievedAt
source          → FRANKFURTER
```

The public API requires no API Key.

The application must not treat Frankfurter as a domain dependency.

---

# 9. Relationship with EN004

EN004 remains authoritative for canonical Financial Instrument identity:

```text
FinancialInstrument
- ticker
- market (MIC)
- currency
- providerSymbol?
```

Provider adapters may use provider-specific symbol resolution but must not redefine the canonical identity.

Finnhub exchange descriptions must not replace EN004 MIC values.

---

# 10. Database-First Instrument Profile Strategy

Sector and instrument/company information are relatively stable and must use a database-first lookup strategy.

```text
GetInstrumentProfile
        │
        ▼
InstrumentProfileRepositoryPort
        │
        ├── FOUND
        │      ↓
        │   return local profile
        │
        └── NOT FOUND
               ↓
       InstrumentProfileProviderPort
               ↓
          external adapter
               ↓
          normalize profile
               ↓
       persist in PostgreSQL
               ↓
            return
```

PostgreSQL is the local persistence mechanism for retrieved profile enrichment.

---

# 11. Instrument Profile Repository Port

Business must use a provider-neutral persistence port conceptually equivalent to:

```java
interface InstrumentProfileRepositoryPort {

    Optional<InstrumentProfile> findByInstrument(
        InstrumentIdentifier instrument
    );

    InstrumentProfile save(
        InstrumentProfile profile
    );
}
```

Infrastructure implementation:

```text
InstrumentProfileRepositoryPort
        ↓
JpaInstrumentProfileRepositoryAdapter
        ↓
Spring Data JPA
        ↓
PostgreSQL
```

Business code must not use Spring Data repositories directly.

---

# 12. Instrument Profile Retrieval Rules

## BR-EN005-001 — Database First
When instrument profile/sector information is requested, local persistence must be queried first.

## BR-EN005-002 — Local Hit
If the profile exists locally, return it without calling the external provider.

## BR-EN005-003 — Local Miss
If the profile does not exist locally, call the configured `InstrumentProfileProviderPort`.

## BR-EN005-004 — Persist External Result
A successfully retrieved and normalized external profile must be persisted.

## BR-EN005-005 — Provider-Neutral Persistence
Persisted profile data must use provider-neutral fields. Finnhub payloads must not become the canonical persistence model.

## BR-EN005-006 — Missing External Profile
If neither local data nor the provider can supply a profile, return an explicit unavailable result. No sector may be fabricated.

---

# 13. Profile Freshness

The first version requires database-first reuse.

Persisted profile information should retain:

```text
lastUpdatedAt
source?
```

Initial behavior:

```text
profile exists in DB
→ use it

profile absent
→ fetch externally and persist
```

No automatic TTL-based refresh is required by this revision.

---

# 14. Price Retrieval Strategy

Market prices are time-sensitive.

```text
GetMarketPrice
        ↓
MarketPriceProviderPort
        ↓
configured price adapter
```

EN005 does not require market price to follow the database-first profile strategy.

A future cache or persistence mechanism may be added without changing the port.

---

# 15. FX Retrieval Strategy

FX retrieval is independent:

```text
GetFxRate
      ↓
FxRateProviderPort
      ↓
configured FX adapter
```

A future `EcbFxRateAdapter`, `AlphaVantageFxRateAdapter`, or other adapter may replace `FrankfurterFxRateAdapter` without changing core logic.

---

# 16. Multi-Adapter Architecture

Several adapters may coexist:

```text
MarketPriceProviderPort
├── FinnhubMarketPriceAdapter
└── AlphaVantageMarketPriceAdapter

InstrumentProfileProviderPort
├── FinnhubInstrumentProfileAdapter
└── AnotherProfileAdapter

FxRateProviderPort
├── FrankfurterFxRateAdapter
└── EcbFxRateAdapter
```

Provider selection must be resolved through infrastructure configuration/wiring, not through provider-specific `if/else` logic in core/business code.

Automatic fallback chains are not required by this revision.

---

# 17. Functional Module Structure

Possible ADR-003-compliant structure:

```text
marketdata/
├── domain/
│   ├── model/
│   ├── ports/
│   │   ├── MarketPriceProviderPort.java
│   │   └── FxRateProviderPort.java
│   └── exceptions/
├── business/
│   ├── GetMarketPrice.java
│   └── GetFxRate.java
└── infrastructure/
    └── provider/
        ├── finnhub/
        │   ├── price/
        │   ├── client/
        │   └── config/
        └── frankfurter/
            └── fx/
```

Profile enrichment may remain in `financialinstrument`:

```text
financialinstrument/
├── domain/
│   ├── model/
│   └── ports/
│       ├── InstrumentProfileProviderPort.java
│       └── InstrumentProfileRepositoryPort.java
├── business/
│   └── GetInstrumentProfile.java
└── infrastructure/
    ├── persistence/
    │   ├── entity/
    │   ├── repository/
    │   ├── mapper/
    │   └── JpaInstrumentProfileRepositoryAdapter.java
    └── provider/
        └── finnhub/
            └── FinnhubInstrumentProfileAdapter.java
```

Exact package names may be refined during planning, but capability separation is mandatory.

---

# 18. Dependency Rules

ADR-003 remains mandatory:

```text
infrastructure → business → domain
```

Additionally:

```text
business -X-> Finnhub
domain   -X-> Finnhub
business -X-> Frankfurter
domain   -X-> Frankfurter
business -X-> Spring Data JPA
domain   -X-> Spring Data JPA
```

Only infrastructure knows provider technology.

---

# 19. Error Model

Core/business errors must be provider-neutral:

```text
MarketPriceUnavailable
InstrumentProfileUnavailable
FxRateUnavailable
ExternalProviderUnavailable
ExternalProviderRateLimited
ExternalProviderAuthenticationFailed
InstrumentNotResolved
```

Provider-specific HTTP status, DTO, or error codes from Finnhub or Frankfurter must be translated inside each adapter.

---

# 20A. Initial Market Coverage

The initial EN005 implementation supports **US equities only** for market-price and instrument-profile retrieval through Finnhub.

This is an explicit initial scope decision, not a core-domain limitation.

Conceptually:

```text
Initial supported market-data universe
→ United States equities
```

The core/business APIs remain provider-neutral and must not encode Finnhub-specific market restrictions.

A future adapter or provider change may extend coverage to European or other markets without changing the core use cases or public business contracts.

EN004 may continue to contain canonical instruments from other markets even when EN005 cannot currently value or enrich them.

---

# 20. Provider Limitations

Core must not contain provider-plan rules such as:

```text
Finnhub supports only US equities on the current plan
Frankfurter is used for FX instead of relying on Finnhub FX capabilities
```

Those are infrastructure/provider limitations.

The active adapter may return a provider-neutral unsupported/unavailable result.

A future adapter may extend coverage without changing business logic.

---

# 21. Observability

Each external adapter must provide provider-aware infrastructure telemetry:

```text
provider
capability
operation
latency
success/failure
HTTP status category
```

Example:

```text
provider=finnhub
capability=market-price
```

```text
provider=frankfurter
capability=fx-rate
```

API keys must never appear in logs, metrics, or traces.

---

# 22. Testing Strategy

Automated tests must not depend on live providers.

## Business tests

Verify:

- core operations are provider-independent;
- `GetInstrumentProfile` checks repository first;
- local profile hit does not call provider;
- local profile miss calls provider;
- external profile is persisted;
- provider failure does not fabricate data.

## Adapter tests

Each adapter is tested independently with WireMock or equivalent.

### FinnhubMarketPriceAdapter
- correct quote request;
- API Key;
- response mapping;
- missing price;
- unsupported symbol;
- rate limit;
- authentication failure;
- provider failure.

### FinnhubInstrumentProfileAdapter
- correct profile request;
- profile/sector mapping;
- missing classification;
- provider failure.

### FrankfurterFxRateAdapter
- correct `/v1/latest` request;
- correct `base` query parameter;
- correct `symbols` query parameter;
- successful USD → EUR mapping;
- successful EUR → USD mapping;
- malformed response;
- unsupported currency;
- provider failure.

---

# 23. Architecture Tests

ArchUnit should verify:

- domain does not depend on infrastructure;
- business does not depend on infrastructure;
- Finnhub and Frankfurter packages remain under infrastructure;
- provider DTOs do not appear in domain/business;
- JPA entities/repositories remain in infrastructure.

---

# 24. Verification Criteria

## VC-001 — Provider-Neutral Core
Core/business obtains price, profile, and FX without referencing Finnhub.

## VC-002 — Separate Provider Ports
Price, profile, and FX use independent provider ports.

## VC-003 — Separate Provider Adapters
Finnhub price/profile and Frankfurter FX integrations are implemented as independent adapters.

## VC-004 — Independent Provider Selection
Changing one provider does not require changing the other capabilities.

## VC-005 — Database-First Profile
Instrument profile retrieval checks PostgreSQL before external access.

## VC-006 — No External Call on Local Hit
Existing profile data is returned locally without provider invocation.

## VC-007 — External Fetch on Miss
Missing profile data invokes the configured external profile provider.

## VC-008 — Profile Persistence
External profile data is normalized and persisted.

## VC-009 — Provider-Neutral Persistence
Stored profile data contains no Finnhub DTO dependency.

## VC-010 — Canonical Identity
EN004 remains authoritative for `ticker + market(MIC)`.

## VC-011 — Secret Protection
Finnhub API Key remains exclusively in infrastructure/runtime configuration.

Frankfurter public API access requires no API Key and must not introduce a fake/shared application secret.

## VC-012 — Provider Limitations Hidden
Finnhub subscription/coverage limitations do not leak into business rules.

## VC-013 — Multiple Adapters Possible
Several adapter implementations may coexist.

## VC-014 — Deterministic Tests
CI does not require live Finnhub.

## VC-015 — ADR-003 Compliance
Implementation follows the standard Spring architecture.

---

# 25. Explicit Technical Decisions

1. EN005 remains named **Establish Finnhub Market Data Integration**.
2. The architecture is provider-neutral despite Finnhub remaining the initial market-data/profile provider.
3. Each external capability has an independent port.
4. Finnhub provides the initial `MarketPriceProviderPort` adapter.
5. Finnhub provides the initial `InstrumentProfileProviderPort` adapter.
6. Frankfurter provides the initial `FxRateProviderPort` adapter.
7. There is no single all-purpose provider adapter.
8. Core/business never branches on provider identity.
9. Provider selection is infrastructure configuration.
10. Several adapters may coexist.
11. EN004 remains canonical for instrument identity.
12. Initial price/profile coverage is limited to US equities because of the current Finnhub plan.
13. That US-only limitation must not become a core-domain restriction.
14. Instrument profile/sector lookup is database-first.
15. A local profile hit avoids an external API call.
16. A local profile miss invokes the configured profile provider.
17. Successfully retrieved profile data is persisted locally.
18. Market price remains time-sensitive and uses its provider port.
19. FX rates are retrieved from Frankfurter using its public API.
20. Initial FX scope is EUR ↔ USD.
21. Provider subscription/coverage limitations are infrastructure concerns.
22. Provider errors are translated into provider-neutral failures.
23. Automated tests use controlled provider boundaries.

---

# 26. Open Technical Decisions

1. Exact Spring HTTP client.
2. Exact adapter-selection mechanism.
3. Spring profiles vs conditional beans vs explicit provider factories.
4. Exact database schema for profile enrichment.
5. Store profile data in existing EN004 tables vs dedicated enrichment tables.
6. Exact normalization of Finnhub `finnhubIndustry`.
7. Future profile refresh/TTL policy.
8. Price caching strategy.
9. FX caching strategy.
10. Exact symbol resolution rules per provider.
11. Whether automatic fallback chains should be introduced later.

Automatic multi-provider fallback is not required now.

---

# 27. Migration from Previous EN005

Previous EN005 already defined provider-neutral capabilities and Finnhub as the initial provider.

This revision strengthens the design by making adapter separation mandatory and introducing database-first profile enrichment.

```text
BEFORE

MarketDataPort
InstrumentProfilePort
FxRatePort
       ↓
FinnhubAdapter(s)
```

```text
AFTER

MarketPriceProviderPort
       ↓
FinnhubMarketPriceAdapter

InstrumentProfileProviderPort
       ↓
FinnhubInstrumentProfileAdapter

FxRateProviderPort
       ↓
FrankfurterFxRateAdapter
```

plus:

```text
GetInstrumentProfile
       ↓
InstrumentProfileRepositoryPort
       ├── found → return
       └── missing
              ↓
InstrumentProfileProviderPort
              ↓
external adapter
              ↓
persist
```

---

# 28. Governance of This Revision

Because EN005 had already been approved, this is a material revision of the same technical capability.

Recommended lifecycle:

```text
Approved EN005
      ↓
reopen as Draft / Under Revision
      ↓
apply this revision
      ↓
review
      ↓
human re-approval
```

A new Enabler ID is not required unless the previous EN005 has already been implemented and formally closed.

If EN005 has already been implemented/closed, create a new technical enabler to evolve the architecture instead of rewriting completed history.

---

# 29. Human Approval

- [X] EN005 name remains unchanged.
- [X] Provider-neutral core/business architecture is approved.
- [X] Separate adapter per capability is approved.
- [X] `MarketPriceProviderPort` is approved.
- [X] `InstrumentProfileProviderPort` is approved.
- [X] `FxRateProviderPort` is approved.
- [X] Finnhub is approved for market price.
- [X] Finnhub is approved for instrument profile/sector.
- [X] Frankfurter is approved for EUR/USD FX rates.
- [X] Initial market-data scope is US equities only.
- [X] US-only coverage is not encoded as a core-domain restriction.
- [X] Independent provider configuration is approved.
- [X] Database-first instrument-profile retrieval is approved.
- [X] Local profile hit avoids external provider access.
- [X] External profile result is persisted.
- [X] EN004 remains canonical for instrument identity.
- [X] Multiple provider adapters may coexist.
- [X] No automatic provider fallback is introduced yet.
- [X] No Portfolio valuation business behavior is introduced.

**Approved by:*jaruiz*  
**Date:*2026-09-04*  
**Status:** Approved
