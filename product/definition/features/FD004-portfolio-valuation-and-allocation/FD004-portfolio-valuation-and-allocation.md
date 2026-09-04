# FD004 — Portfolio Valuation & Allocation

> **Status:** Approved  
> **Feature ID:** FD004  
> **Feature Name:** Portfolio Valuation & Allocation  
> **Depends on:** FD001, FD003, EN004, EN005  
> **Last Updated:** 2026-09-04

---

# 1. Purpose

Allow My-FinAI-Manager to automatically value a Portfolio after it is successfully created and classify its value by sector using the market-data, company-profile, and FX capabilities established by EN005.

After a Portfolio is created, the platform must initiate a Portfolio valuation operation that:

- obtains the latest available market price for each Position;
- obtains the sector/classification of each Financial Instrument;
- calculates the market value of each Position;
- calculates total Portfolio value in EUR;
- calculates total Portfolio value in USD;
- calculates each Position's percentage weight;
- calculates Portfolio allocation by sector.

The valuation calculations themselves must be deterministic.

EN005 supplies external data. FD004 owns the Portfolio valuation and allocation behavior.

---

# 2. User Value

As an Investor, I want a newly created Portfolio to be valued automatically so that I can immediately understand:

- how much the Portfolio is currently worth;
- how much each Position is worth;
- how much each Position contributes to the Portfolio;
- how the Portfolio is distributed across sectors;
- the Portfolio's total value in both EUR and USD.

---

# 3. Trigger

FD004 is triggered after a Portfolio has been successfully persisted.

```text
Create Portfolio
      ↓
Portfolio persisted
      ↓
Portfolio Valuation requested
      ↓
EN005 market/profile/FX data
      ↓
deterministic calculations
      ↓
Portfolio Valuation stored
```

A failure in external market-data retrieval must not invalidate or roll back an already successfully created Portfolio.

---

# 4. Scope

## In Scope

- Automatically initiate valuation after successful Portfolio creation.
- Obtain market-price information through EN005 `MarketDataPort`.
- Obtain sector/profile information through EN005 `InstrumentProfilePort`.
- Obtain EUR/USD FX rates through EN005 `FxRatePort`.
- Calculate each Position's market value.
- Calculate Position value in native currency, EUR, and USD.
- Calculate total Portfolio value in EUR and USD.
- Calculate Position weights.
- Group Positions by sector.
- Calculate sector allocation percentages.
- Persist the latest Portfolio valuation.
- Persist enough freshness/source information to understand when valuation was calculated.
- Represent incomplete/partial valuation explicitly.
- Expose valuation/allocation data in the Portfolio detail established by FD003.
- Display a mandatory ticker allocation circular/pie chart in Portfolio detail.
- Display a mandatory sector allocation circular/pie chart in Portfolio detail.
- Use EN004 canonical Financial Instrument identity.

## Out of Scope

- Historical valuation.
- Historical performance.
- Profit/loss against purchase price.
- Benchmark comparison.
- Dividends, taxes, fees.
- Stop-Loss calculation.
- Risk scoring.
- AI recommendations.
- News analysis.
- Portfolio rebalancing.
- Automatic trading.
- Continuous/intraday refresh.
- Scheduled periodic revaluation.
- Currencies other than EUR and USD.
- LLM-based sector inference.
- Manual sector editing.

---

# 5. Main User Flow

1. Investor creates a Portfolio through FD001.
2. Portfolio is successfully persisted.
3. Platform initiates valuation.
4. For each Position:
   - resolve canonical instrument through EN004;
   - obtain latest available market price through EN005;
   - obtain sector/profile through EN005.
5. Platform obtains required EUR/USD FX information through EN005.
6. Platform deterministically calculates Position values, Portfolio totals, Position weights, and sector allocation.
7. Valuation result is persisted.
8. Portfolio detail displays the latest available valuation and allocation.

---

# 6. Valuation Status

A Portfolio valuation must have an explicit status:

```text
PENDING
COMPLETED
PARTIAL
FAILED
```

- **PENDING**: valuation requested but not completed.
- **COMPLETED**: all required monetary inputs were available.
- **PARTIAL**: some Positions or conversions could not be fully valued.
- **FAILED**: no meaningful valuation result could be produced.

A Portfolio remains valid regardless of valuation status.

---

# 7. Position Valuation

Conceptually:

```text
PositionValuation
- ticker
- market
- quantity
- marketPrice
- nativeCurrency
- nativeMarketValue
- valueInEUR
- valueInUSD
- portfolioWeight
- sector
- priceObservedAt
```

Exact API/persistence modeling belongs to specification/planning.

---

# 8. Deterministic Calculations

For each Position:

```text
nativeMarketValue =
quantity × marketPrice
```

For a USD Position:

```text
valueInUSD = nativeMarketValue
valueInEUR = nativeMarketValue × USD→EUR
```

For an EUR Position:

```text
valueInEUR = nativeMarketValue
valueInUSD = nativeMarketValue × EUR→USD
```

Portfolio totals:

```text
portfolioValueEUR = Σ position.valueInEUR
portfolioValueUSD = Σ position.valueInUSD
```

All calculations must use decimal-safe numeric types.

No LLM may calculate or infer monetary values.

---

# 9. Position Weight

EUR is the canonical calculation currency for allocation percentages.

```text
positionWeight =
position.valueInEUR / portfolioValueEUR
```

```text
positionWeightPercentage =
positionWeight × 100
```

Using one canonical currency avoids mixing native currencies.

---

# 10. Sector Classification

Sector/classification comes through EN005 `InstrumentProfilePort`.

Example:

```text
AAPL → Technology
MSFT → Technology
SAN  → Financial Services
IBE  → Utilities
```

If sector information is missing but monetary valuation is available, the Position is classified as:

```text
Unclassified
```

No LLM may infer a missing sector.

---

# 11. Sector Allocation

For each sector:

```text
sectorValueEUR =
Σ position.valueInEUR
for positions belonging to sector
```

Then:

```text
sectorWeight =
sectorValueEUR / portfolioValueEUR
```

Example:

```text
Technology              42.3 %
Financial Services      31.1 %
Utilities               18.4 %
Unclassified             8.2 %
```

For a completed valuation, Position and sector weights should sum to approximately 100%, allowing only display-rounding differences.

---

# 12. Valuation Snapshot

Conceptually:

```text
PortfolioValuation
- portfolioId
- status
- calculatedAt
- totalValueEUR
- totalValueUSD
- marketDataAsOf
- fxDataAsOf
- positions[]
- sectors[]
```

FD004 requires persistence of the latest valuation snapshot only.

Historical valuation retention is not required.

---

# 13. Creation Independence

Portfolio creation and valuation are independent outcomes.

If Portfolio creation succeeds but valuation fails:

```text
Portfolio creation = SUCCESS
Portfolio valuation = FAILED
```

The Portfolio must remain stored and visible through FD003.

The user must never lose a successfully created Portfolio because Finnhub or FX data is unavailable.

---

# 14. Trigger Execution Semantics

FD004 requires valuation to start after successful Portfolio persistence.

The exact mechanism is technical and may be:

```text
application orchestration
post-commit invocation
internal event
background execution
```

FD004 does not require Kafka or another external broker.

The implementation must preserve:

```text
Portfolio creation must not be rolled back
because valuation fails.
```

---

# 15. Idempotency

Valuation must be safely repeatable.

Re-running valuation must not duplicate Position or sector allocation state.

A new valuation may replace/update the current latest valuation snapshot.

---

# 16. Partial Data Rules

## Missing Sector Only

If price and FX are available but sector is missing:

```text
Position is valued
Sector = Unclassified
```

The valuation may still be `COMPLETED`.

## Missing Price

If a Position has no valid market price:

```text
Position cannot be valued
Portfolio valuation = PARTIAL
```

The missing Position must not be assigned value zero.

## Missing FX

If required cross-currency conversion is unavailable:

```text
Portfolio valuation = PARTIAL or FAILED
```

depending on whether a meaningful partial result remains.

Exact status transition rules should be formalized during specification.

---

# 17. Portfolio Detail UX

FD004 extends FD003 Portfolio detail.

At minimum:

```text
Portfolio: Long Term Investment

Total Value
€24,350.21
$28,314.20
```

The Position table may be extended with:

| Field | Required |
|---|---:|
| Ticker | Yes |
| Market | Yes |
| Quantity | Yes |
| Currency | Yes |
| Market Price | When valuation available |
| Market Value | When valuation available |
| Value in EUR | When valuation available |
| Value in USD | When valuation available |
| Portfolio Weight | When valuation available |
| Sector | When profile available |

The Portfolio detail must also display **two mandatory circular/pie charts** when valuation data is available.

## 17.1 Allocation by Ticker

The first pie chart represents Portfolio allocation by Financial Instrument / ticker.

Each slice corresponds to one valued Position, for example:

```text
AAPL
IBM
MSFT
KO
```

The slice size must use the existing deterministic Position weight calculated from normalized EUR value:

```text
tickerWeight =
position.valueInEUR / portfolioValueEUR
```

The chart must expose at least:

- ticker;
- percentage weight.

The chart must not use quantity, purchase price, or number of shares as its allocation measure.

## 17.2 Allocation by Sector

The second pie chart represents Portfolio allocation by sector.

Each slice corresponds to one sector, for example:

```text
Technology
Financial Services
Retail
Healthcare
Unclassified
```

The slice size must use the existing deterministic sector allocation:

```text
sectorWeight =
sectorValueEUR / portfolioValueEUR
```

The chart must expose at least:

- sector;
- percentage weight.

Positions without sector information must contribute to the `Unclassified` slice.

## 17.3 Chart Consistency

Both charts are visual representations of valuation/allocation data calculated by FD004.

The frontend must not independently calculate financial allocation from raw external-provider payloads.

For a `COMPLETED` valuation:

```text
Σ ticker chart slices ≈ 100 %
Σ sector chart slices ≈ 100 %
```

Only display-rounding differences are acceptable.

For a `PARTIAL` valuation, charts may represent only the successfully valued portion, but the UI must clearly indicate that the valuation is partial.

## 17.4 Suggested Layout

```text
Portfolio: Long Term Investment

Total Value
€24,350.21
$28,314.20

┌─────────────────────────┐   ┌─────────────────────────┐
│ Allocation by Ticker    │   │ Allocation by Sector    │
│                         │   │                         │
│       PIE CHART         │   │       PIE CHART         │
│                         │   │                         │
└─────────────────────────┘   └─────────────────────────┘

Positions
┌────────┬────────┬──────────┬──────────┬──────────┐
│ Ticker │ Market │ Quantity │ Value    │ Sector   │
└────────┴────────┴──────────┴──────────┴──────────┘
```

Exact responsive placement follows the global design system. On narrow screens, the charts may stack vertically.

The two pie charts are **mandatory FD004 functionality**, not optional visual enhancements.

---

# 18. Valuation State UX

The Portfolio detail must communicate valuation state, for example:

```text
Valuation pending
```

```text
Valued at 2026-09-03 20:15
```

```text
Partial valuation — market data unavailable for 1 Position
```

```text
Valuation unavailable
```

The UI must not display fabricated zero values when valuation inputs are missing.

---

# 19. API Expectations

FD004 requires an application-facing way to obtain Portfolio valuation.

Conceptually:

```text
GET /portfolios/{portfolioId}/valuation
```

or equivalent integration into the existing Portfolio detail contract.

The response must expose enough information for:

- valuation status;
- calculated timestamp;
- total EUR value;
- total USD value;
- Position valuation;
- Position weights;
- Position sectors;
- ticker allocation / Position weights required by the ticker pie chart;
- sector allocation required by the sector pie chart.

Finnhub DTOs must not leak into the public API.

---

# 20. Relationship with EN005

FD004 consumes EN005 only through provider-neutral capabilities:

```text
Portfolio Valuation
       │
       ├── MarketDataPort
       ├── InstrumentProfilePort
       └── FxRatePort
```

FD004 must not know:

- Finnhub endpoint URLs;
- Finnhub API Key;
- Finnhub response field names;
- Finnhub authentication details.

---

# 21. Relationship with EN004

EN004 remains authoritative for canonical Financial Instrument identity:

```text
ticker + market(MIC)
```

Finnhub enriches/prices the Position but does not redefine its canonical Market.

---

# 22. Business Rules

## BR-001 — Automatic Trigger
A successful Portfolio creation must initiate Portfolio valuation.

## BR-002 — Creation Independence
Valuation failure must not roll back or invalidate an already created Portfolio.

## BR-003 — Deterministic Monetary Calculation
All Position, Portfolio, currency-conversion, and percentage calculations must be deterministic.

## BR-004 — EN005 Data Access
External market, profile, and FX data must be obtained through EN005 provider-neutral ports.

## BR-005 — Dual Currency Total
A fully valued Portfolio must expose total value in EUR and USD.

## BR-006 — Position Market Value
A Position's native market value is quantity multiplied by latest available market price.

## BR-007 — Canonical Allocation Currency
Position and sector allocation percentages are calculated using normalized EUR values.

## BR-008 — Sector Classification
Sector information must come from an approved deterministic source through EN005.

## BR-009 — Missing Sector
A valued Position without sector information is assigned to `Unclassified`.

## BR-010 — Missing Price Is Not Zero
A Position without valid market-price data must not be treated as zero value.

## BR-011 — Latest Valuation
FD004 must persist and expose the latest valuation snapshot.

## BR-012 — Read-Only Valuation
The Investor does not manually edit calculated valuation values.

## BR-013 — Data Freshness
The valuation must retain calculation time and enough market/FX freshness information to understand when the result was produced.

## BR-014 — Ticker Allocation Pie Chart
Portfolio detail must display a circular/pie chart representing valued Positions by ticker using normalized EUR Position weights.

## BR-015 — Sector Allocation Pie Chart
Portfolio detail must display a circular/pie chart representing sector allocation using normalized EUR sector weights.

## BR-016 — Chart Data Consistency
Both charts must use the same deterministic valuation/allocation results returned by FD004.

## BR-017 — Partial Valuation Visualization
When valuation is `PARTIAL`, the UI must clearly communicate that allocation charts represent incomplete valuation data.

---

# 23. Acceptance Criteria

## AC-001 — Automatic Valuation
**Given** the Investor creates a valid Portfolio  
**And** it is successfully persisted  
**When** creation completes  
**Then** the application initiates valuation.

## AC-002 — Position Market Value
**Given** quantity `10` and market price `230 USD`  
**When** the Position is valued  
**Then** native market value is `2300 USD`.

## AC-003 — USD to EUR
**Given** a USD Position and a valid USD→EUR rate  
**When** valuation runs  
**Then** its EUR value is calculated.

## AC-004 — EUR to USD
**Given** an EUR Position and a valid EUR→USD rate  
**When** valuation runs  
**Then** its USD value is calculated.

## AC-005 — Total EUR
**Given** all Positions can be valued in EUR  
**Then** total EUR equals the sum of all Position EUR values.

## AC-006 — Total USD
**Given** all Positions can be valued in USD  
**Then** total USD equals the sum of all Position USD values.

## AC-007 — Position Weight
Each Position weight is based on its EUR value relative to total Portfolio EUR value.

## AC-008 — Sector Allocation
The application groups normalized Position values by sector and calculates sector percentages.

## AC-009 — Missing Sector
A Position with valid monetary data but no sector is valued and allocated to `Unclassified`.

## AC-010 — Missing Price
A missing price never becomes a fabricated zero value and produces an incomplete valuation.

## AC-011 — Valuation Failure Does Not Delete Portfolio
If EN005 fails after successful Portfolio creation, the Portfolio remains persisted and visible.

## AC-012 — Portfolio Detail
A completed valuation displays totals in EUR/USD, Position valuation, weights, sector data, and sector allocation.

## AC-013 — Ticker Allocation Pie Chart

**Given** a Portfolio has a completed valuation with multiple valued Positions  
**When** the Investor opens Portfolio detail  
**Then** a circular/pie chart is displayed with one slice per ticker  
**And** each slice percentage corresponds to that Position's normalized EUR Portfolio weight.

## AC-014 — Sector Allocation Pie Chart

**Given** a Portfolio has a completed valuation with Positions belonging to multiple sectors  
**When** the Investor opens Portfolio detail  
**Then** a circular/pie chart is displayed with one slice per sector  
**And** each slice percentage corresponds to the deterministic sector allocation  
**And** Positions without sector classification contribute to `Unclassified`.

## AC-015 — Chart Consistency

**Given** the Portfolio detail displays valuation data  
**When** the charts are rendered  
**Then** their percentages are consistent with backend Position weights and sector allocation.

---

# 24. Testing Expectations

At minimum, verify:

- post-creation valuation trigger;
- deterministic Position values;
- USD→EUR conversion;
- EUR→USD conversion;
- total EUR/USD;
- Position weights;
- sector grouping/percentages;
- `Unclassified`;
- missing price;
- missing FX;
- EN005 failure;
- Portfolio creation surviving valuation failure;
- idempotent revaluation;
- latest valuation persistence;
- Portfolio detail API/UI;
- ticker allocation pie-chart rendering;
- sector allocation pie-chart rendering;
- chart percentages matching backend valuation/allocation data;
- `Unclassified` sector represented correctly in the sector chart.

Automated business tests must use deterministic market/profile/FX values and must not depend on changing live Finnhub responses.

---

# 25. E2E Testing

FD004 requires deterministic browser-based E2E verification.

The live Finnhub service must not be required by CI.

The Finnhub boundary may be stubbed/controlled while frontend, backend, business calculations, application persistence, and PostgreSQL remain real.

```text
Playwright
    ↓
Create Portfolio
    ↓
Portfolio persisted
    ↓
valuation operation
    ↓
EN005 boundary with deterministic responses
    ↓
valuation persisted
    ↓
Portfolio Detail
    ↓
PostgreSQL
```

---

# 26. E2E-001 — Create, Value and Display Portfolio

Create a deterministic Portfolio containing at least one EUR Position and one USD Position, preferably in different sectors.

Example:

```text
Portfolio Valuation Test

AAPL
quantity: 10
market: XNAS
currency: USD
price: 200 USD
sector: Technology

SAN
quantity: 100
market: XMAD
currency: EUR
price: 5 EUR
sector: Financial Services

FX
USD → EUR = 0.80
EUR → USD = 1.25
```

Expected:

```text
AAPL native value
10 × 200 = 2,000 USD

AAPL EUR value
2,000 × 0.80 = 1,600 EUR

SAN native value
100 × 5 = 500 EUR

SAN USD value
500 × 1.25 = 625 USD

Portfolio total EUR
1,600 + 500 = 2,100 EUR

Portfolio total USD
2,000 + 625 = 2,625 USD
```

Sector allocation:

```text
Technology
1,600 / 2,100 = 76.190476... %

Financial Services
500 / 2,100 = 23.809523... %
```

The UI may round according to approved display precision.

The E2E must verify:

1. Portfolio creation succeeds.
2. Valuation is initiated.
3. Total EUR is correct.
4. Total USD is correct.
5. AAPL valuation is correct.
6. SAN valuation is correct.
7. AAPL sector is Technology.
8. SAN sector is Financial Services.
9. Position weights are correct within approved rounding.
10. Sector allocations are correct.
11. The ticker allocation pie chart is visible.
12. The ticker chart contains the expected ticker slices and percentages.
13. The sector allocation pie chart is visible.
14. The sector chart contains the expected sector slices and percentages.
15. Chart values are consistent with the deterministic valuation shown in the detail.
16. Portfolio remains persisted.

---

# 27. E2E-002 — Provider Failure Does Not Break Creation

**Given** the controlled EN005 provider boundary returns unavailable/error responses  
**When** the Investor successfully creates a Portfolio  
**Then** the Portfolio remains created  
**And** remains visible in the Home list  
**And** its detail does not show fabricated valuation values  
**And** valuation state indicates pending, partial, failed, or unavailable according to implemented status rules.

---

# 28. Closure Gate

FD004 must not be accepted, closed, or marked Completed if:

- calculations are not deterministic;
- live Finnhub is required by CI E2E;
- E2E-001 is missing or failing;
- E2E-002 is missing or failing;
- Portfolio creation is rolled back because valuation fails;
- missing price/FX data is represented as fabricated zero.
- the ticker allocation pie chart is missing or inconsistent with Position weights;
- the sector allocation pie chart is missing or inconsistent with sector allocation.

---

# 29. Explicit Product Decisions

1. Successful Portfolio creation automatically initiates valuation.
2. Valuation occurs only after Portfolio persistence.
3. Valuation failure never rolls back Portfolio creation.
4. EN005 provides price, profile/sector, and FX data through provider-neutral capabilities.
5. FD004 owns deterministic valuation/allocation calculations.
6. Portfolio value is shown in EUR and USD.
7. EUR is the canonical allocation currency.
8. Each Position receives market value when data is available.
9. Each Position receives a Portfolio weight when sufficient data exists.
10. Sector allocation is based on normalized EUR Position values.
11. Missing sector becomes `Unclassified`.
12. Missing price never becomes zero.
13. Latest valuation snapshot is persisted.
14. Historical valuation is out of scope.
15. Portfolio detail is extended with valuation and sector allocation.
16. Portfolio detail must include a circular/pie chart for allocation by ticker.
17. The ticker chart uses normalized EUR Position weights.
18. Portfolio detail must include a circular/pie chart for allocation by sector.
19. The sector chart uses normalized EUR sector weights.
20. Both pie charts are mandatory.
21. Charts consume deterministic FD004 valuation/allocation results.
22. `Unclassified` appears as a sector slice when applicable.
23. EN004 remains canonical for Financial Instrument identity.
24. External providers remain hidden behind EN005 provider-neutral ports.
25. CI E2E uses deterministic controlled external-provider responses.
26. E2E must verify both pie charts and their expected allocations.
27. Successful E2E valuation is mandatory for closure.
28. Provider-failure E2E is mandatory for closure.

---

# 30. Open Questions — resolved 2026-09-04 (jaruiz)

The open questions were resolved by the product owner before formal specification:

1. **Valuation execution — SYNCHRONOUS after the create transaction commits.** Valuation runs in
   the same `POST /api/portfolios` request, immediately after the Portfolio is persisted; a
   valuation error is caught and the Portfolio still returns successfully. No background execution,
   no Kafka, no scheduler. *(→ FR-004)*
2. **`PARTIAL` vs `FAILED` — `FAILED` only when NO Position can be valued, OR when neither total
   (EUR nor USD) can be produced at all; otherwise `PARTIAL`.** Missing FX for the cross-currency
   total → `PARTIAL` (the native-currency total still stands). *(→ FR-018)*
3. **Monetary display precision — 2 decimal places for display; full precision retained in storage
   and calculation** (adopts the recommended default).
4. **Percentage display precision — 2 decimal places for display; weights stored as exact
   fractions** (adopts the recommended default).
5. **API shape — a DEDICATED operation `GET /api/portfolios/{portfolioId}/valuation`.** FD003's
   existing `Portfolio` detail schema and contract are left unchanged. *(→ FR-025)*
6. **Valuation persistence schema — a new Flyway forward migration adds a latest-only valuation
   structure (`portfolio_valuation` + `position_valuation` + `sector_allocation`) owned by the
   `portfolio` module; FD001/EN004 tables untouched.** Exact columns are a planning detail.
7. **Sector representation — the EN005 provider classification string is displayed directly;
   `Unclassified` when absent. No canonical sector taxonomy in FD004** (deferred to a possible
   future feature).
8. **A manual refresh / revalue action is OUT OF SCOPE for FD004** (deferred to a future feature).

These decisions introduce no scheduled valuation, no history, no AI inference, and no trading.

---

# 31. Human Approval

Before formal specification:

- [X] Purpose is correct.
- [X] Automatic post-creation valuation is approved.
- [X] Portfolio creation remains independent from valuation success.
- [X] EN005 usage is approved.
- [X] EUR and USD totals are approved.
- [X] EUR canonical allocation currency is approved.
- [X] Position market-value calculation is approved.
- [X] Position weight calculation is approved.
- [X] Sector allocation is approved.
- [X] `Unclassified` behavior is approved.
- [X] Latest valuation persistence is approved.
- [X] Historical valuation remains out of scope.
- [X] Portfolio detail extension is approved.
- [X] Ticker allocation pie chart is approved.
- [X] Sector allocation pie chart is approved.
- [X] Chart allocation based on normalized EUR values is approved.
- [X] Mandatory E2E validation of both charts is approved.
- [X] Deterministic calculation requirement is approved.
- [X] Controlled/stubbed Finnhub boundary in CI E2E is approved.
- [X] E2E-001 is approved.
- [X] E2E-002 is approved.
- [X] Mandatory E2E closure gate is approved.
- [X] No unapproved behavior has been added.

The §30 open questions were resolved by the product owner on 2026-09-04 (execution model =
synchronous after-commit; `PARTIAL`/`FAILED` rule; 2-dp monetary + percentage display; dedicated
`GET /api/portfolios/{portfolioId}/valuation`; latest-only valuation schema owned by the `portfolio`
module; provider sector string shown directly; no manual revalue action).

**Approved by:** jaruiz  
**Date:** 2026-09-04  
**Status:** Approved
