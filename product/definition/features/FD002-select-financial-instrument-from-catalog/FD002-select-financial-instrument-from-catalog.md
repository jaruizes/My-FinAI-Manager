# FD002 — Select Financial Instrument from Catalog

> **Status:** Draft  
> **Feature ID:** FD002  
> **Feature Name:** Select Financial Instrument from Catalog  
> **Last Updated:** 2026-09-02  

---

# 1. Purpose

Allow an Investor to select the financial instrument, market, and currency of a Portfolio Position from controlled values instead of entering those values as unrestricted free text.

The purpose is to reduce data-entry errors and ensure that Position identifiers use known, normalized, and valid reference data.

FD002 evolves the Position creation interaction introduced by FD001. It does not change the meaning of a Position or the existing Portfolio creation capability.

---

# 2. User Value

As an Investor, I want to select a known financial instrument when adding a Position so that I do not have to know or manually enter ticker, market, and currency codes and so that invalid combinations cannot be introduced into my Portfolio.

---

# 3. Scope

## In Scope

- Replace free-text Ticker entry in the Add Position flow with a controlled selector/search.
- Replace free-text Market entry with a controlled selector or a value derived from the selected instrument/listing.
- Replace free-text Currency entry with a controlled selector or a value derived from the selected instrument/listing.
- Support EUR and USD as the initial allowed Position currencies.
- Maintain a local Market Catalog.
- Use ISO 10383 Market Identifier Codes (MIC) as the preferred representation for Markets.
- Maintain a local Financial Instrument Catalog.
- Associate each catalogued instrument/listing with:
  - ticker;
  - instrument name;
  - market/MIC;
  - currency;
  - optional ISIN;
  - optional external/reference identifier;
  - active/inactive status.
- Allow the Investor to search Financial Instruments by at least ticker or instrument name.
- Allow only valid instrument/listing combinations present in the Financial Instrument Catalog.
- Integrate controlled instrument selection into the existing FD001 Add Position flow.
- Prevent arbitrary `ticker + market + currency` combinations.
- Provide appropriate UI states when no matching instrument exists.

## Out of Scope

- Live market prices.
- Portfolio valuation.
- Automatic foreign-exchange conversion.
- Position currencies other than EUR and USD.
- User-created Financial Instruments.
- User-created Markets.
- Real-time synchronization with exchanges.
- Guaranteed complete global instrument coverage.
- Crypto assets.
- Derivatives unless explicitly loaded into the reference catalog.
- Market-data enrichment.
- News enrichment.
- Automatic Portfolio recommendations.
- Direct dependency of the user-facing form on an external reference-data API.

---

# 4. Main User Flow

1. The Investor starts or continues creation of a Portfolio.
2. The Investor selects **Add Position**.
3. The application displays the Position form.
4. The Investor searches for a Financial Instrument using ticker or instrument name.
5. The application displays matching known instruments/listings from the Financial Instrument Catalog.
6. Each result provides enough information to distinguish the listing, including at least instrument name, ticker, market/MIC, and currency.
7. The Investor selects one result.
8. The application populates or constrains Ticker, Market, and Currency from the selected catalog entry.
9. The Investor enters Quantity, Initial Purchase Date if known, and Average Purchase Price if known.
10. The Investor confirms the Position.
11. The Position is added using the normalized Financial Instrument reference.
12. The existing FD001 Portfolio creation flow continues.

---

# 5. Financial Instrument Selection

The preferred interaction is to select a Financial Instrument or listing as one coherent reference instead of allowing three unrelated free-text values.

Conceptually:

```text
Instrument
[ Search by ticker or name... ]

Search results:

Apple Inc.
AAPL · XNAS · USD

Microsoft Corporation
MSFT · XNAS · USD
```

After selection:

```text
Instrument   Apple Inc.
Ticker       AAPL
Market       XNAS
Currency     USD
```

Ticker, Market, and Currency are controlled by the selected catalog entry.

If the same Financial Instrument is available through several supported listings, the application may present those listings as separate selectable results.

---

# 6. Reference Information

## Financial Instrument

| Field | Required | Description |
|---|---:|---|
| Name | Yes | Human-readable instrument name |
| Ticker | Yes | Trading ticker/symbol |
| Market | Yes | Market where the listing is identified/traded |
| Currency | Yes | Supported trading/Position currency |
| ISIN | No | International Securities Identification Number when available |
| External Identifier | No | Provider/reference identifier when available |
| Active | Yes | Whether the instrument/listing may currently be selected |

## Market

| Field | Required | Description |
|---|---:|---|
| MIC | Yes | ISO 10383 Market Identifier Code when available |
| Name | Yes | Human-readable market name |
| Country | No | Country associated with the market |
| Active | Yes | Whether the market may be selected |

## Currency

The initial supported currencies are:

```text
EUR
USD
```

Currency codes use ISO 4217 representation.

---

# 7. Business Rules

## BR-001 — Controlled Instrument Selection

A Position must reference a Financial Instrument/listing available in the Financial Instrument Catalog.

The Investor must not provide an unrestricted ticker value.

## BR-002 — Controlled Market Selection

A Position Market must reference a known active Market.

Markets should use ISO 10383 MIC codes where available.

The Investor must not introduce arbitrary Market codes.

## BR-003 — Supported Currencies

The initial supported Position currencies are EUR and USD.

No other currency may be selected in FD002.

## BR-004 — Valid Instrument Listing

Ticker, Market, and Currency must represent a valid catalogued instrument/listing combination.

The application must not allow arbitrary combinations of those values.

## BR-005 — Catalog Entry Is the Source of Selection

Once an instrument/listing is selected, Ticker, Market, and Currency must be populated or constrained using the selected catalog entry.

The UI must not permit these values to drift into a combination that does not exist in the catalog.

## BR-006 — Active Instruments Only

Inactive Financial Instruments/listings must not be selectable for creation of a new Position.

## BR-007 — Catalog Source Independence

The product's Financial Instrument representation must not depend on the schema of a specific external data provider.

External source records must be normalized before becoming Financial Instrument Catalog entries.

## BR-008 — Local Catalog Is the Runtime Source

The user-facing Position flow must query the My-FinAI-Manager Financial Instrument Catalog through the platform backend.

The frontend must not call an external market/reference-data provider directly.

The local catalog may itself be populated or synchronized from files or external providers through separately governed technical capabilities.

## BR-009 — Existing Position Identity Remains Valid

FD002 does not change the FD001 Position identity rule.

Within a Portfolio, a Position remains uniquely identified by:

```text
ticker + market
```

FD002 improves how those values are selected and validated.

---

# 8. Acceptance Criteria

## AC-001 — Select Known Instrument

**Given** the Investor is adding a Position  
**And** the Financial Instrument Catalog contains Apple Inc. listed as `AAPL` on `XNAS` in `USD`  
**When** the Investor searches for `AAPL`  
**And** selects Apple Inc.  
**Then** the Position uses ticker `AAPL`, market `XNAS`, and currency `USD`  
**And** the Investor cannot replace those values with an arbitrary invalid combination.

## AC-002 — Search by Instrument Name

**Given** the Financial Instrument Catalog contains a known Financial Instrument  
**When** the Investor searches using part of its instrument name  
**Then** the application displays matching selectable results.

## AC-003 — Search by Ticker

**Given** the Financial Instrument Catalog contains a known ticker  
**When** the Investor searches using that ticker  
**Then** the application displays the matching known instrument/listing.

## AC-004 — Controlled Market

**Given** the Investor has selected a Financial Instrument/listing  
**When** the Position information is displayed  
**Then** the Market is selected from or derived from the catalog  
**And** arbitrary Market text cannot be entered.

## AC-005 — Controlled Currency

**Given** the Investor has selected a Financial Instrument/listing  
**When** the Position information is displayed  
**Then** Currency is selected from or derived from the catalog  
**And** only EUR or USD are accepted in FD002.

## AC-006 — Invalid Combination Cannot Be Created

**Given** an instrument/listing exists as `AAPL + XNAS + USD`  
**When** the Investor creates the Position  
**Then** the application must not allow an unrelated combination such as `AAPL + XMAD + EUR`.

## AC-007 — Inactive Instrument

**Given** a Financial Instrument/listing is marked inactive  
**When** the Investor searches for instruments to add  
**Then** that entry cannot be selected for a new Position.

## AC-008 — No Search Results

**Given** the Investor is searching the Financial Instrument Catalog  
**When** no known Financial Instrument matches the search criteria  
**Then** the application informs the Investor that no matching instrument was found  
**And** it does not silently accept the entered text as a new instrument.

## AC-009 — Existing FD001 Flow Continues

**Given** the Investor has selected a valid catalogued instrument  
**When** the Investor provides the remaining valid Position information and confirms it  
**Then** the Position can be added to the Portfolio  
**And** the existing FD001 Portfolio creation flow continues without changing its business semantics.

---

# 9. Affected Functional Domains

- Portfolio Management
- Financial Instruments

A functional domain does not imply a technical service or deployment boundary.

---

# 10. Information Objects

| Information Object | Impact |
|---|---|
| Financial Instrument | Introduced/managed as reference data |
| Market | Introduced/managed as reference data |
| Currency | Restricted to approved values |
| Position | References controlled instrument/listing information |
| Portfolio | Existing behavior reused |

---

# 11. Relevant Business Events

FD002 does not require introducing a new business event by default.

Selecting a Financial Instrument is an interaction used to construct a Position and does not automatically imply publication of a technical or business event.

---

# 12. UX / Interaction Requirements

The Add Position interaction must no longer use unrestricted free-text controls for Ticker, Market, or Currency.

The preferred interaction is:

```text
Instrument
[ Search by ticker or company name... ]

Selected:
Apple Inc.
AAPL · XNAS · USD

Quantity
[               ]

Initial Purchase Date
[               ]

Average Purchase Price
[               ] USD
```

If a separate Market selector is displayed, it must contain only valid Market/listing alternatives applicable to the selected instrument.

If a separate Currency selector is displayed, it must contain only supported values and must not enable an invalid instrument/listing combination.

The UI must provide search/loading state, no-results state, selection state, clear validation feedback, keyboard-accessible selection controls, and enough information to distinguish similarly named instruments/listings.

Exact visual styling remains governed by the global UX/design system.

---

# 13. Expected Implementation Areas

```text
implementation/platform/
├── frontend/        Yes
├── backend/         Yes
├── contracts/       Yes
└── infrastructure/  Yes — reference-data persistence required
```

This Feature extends the same executable platform.

The exact mechanism used to populate or synchronize reference data is not owned by this Feature Definition unless explicitly approved as part of its implementation plan.

---

# 14. Testing Expectations

At minimum, verification must cover:

- search by ticker;
- search by instrument name;
- selection of a known Financial Instrument;
- valid Market resolution;
- valid Currency resolution;
- rejection/prevention of invalid `ticker + market + currency` combinations;
- EUR support;
- USD support;
- rejection of unsupported currencies;
- inactive instrument behavior;
- no-results behavior;
- integration with FD001 Position creation;
- API contract behavior for instrument search/selection;
- persistence/querying of reference data;
- frontend-to-backend Financial Instrument search;
- regression of FD001 Portfolio creation.

Integration tests against application-managed persistence must follow the project's Testcontainers policy.

A critical browser-based E2E journey must verify the controlled-selection flow through the real containerized platform.

```text
Playwright
    ↓
Create Portfolio
    ↓
Add Position
    ↓
Search/select Financial Instrument
    ↓
Ticker/Market/Currency controlled
    ↓
Save Portfolio
    ↓
REST API
    ↓
core-service
    ↓
PostgreSQL
```

**FD002 must not be accepted, closed, or marked Completed if its mandatory E2E critical journey is missing or failing.**

---

# 15. Reference Data Strategy

FD002 defines the product need for a controlled Financial Instrument Catalog.

It does not mandate a specific provider.

The preferred product behavior is:

```text
Frontend
   ↓
My-FinAI-Manager API
   ↓
Financial Instrument Catalog
   ↓
normalized local reference data
```

The frontend must not depend directly on external catalog providers.

Reference data may initially be loaded from controlled files and later synchronized through external providers.

Potential source categories include:

```text
Markets
  → ISO 10383 MIC reference data

Currencies
  → controlled internal catalog (EUR / USD initially)

Financial Instruments
  → curated/imported source files
  → optional external provider synchronization later
```

Provider/source selection and synchronization mechanics should be governed separately when they introduce technical architecture decisions.

---

# 16. Relationship with Technical Enablers

A separate Technical Enabler should establish reusable reference-data ingestion/synchronization infrastructure when required.

Recommended future enabler:

```text
EN004 — Establish Financial Instrument Reference Data
```

Its responsibilities may include:

- loading ISO 10383 Market data;
- loading or synchronizing Financial Instrument data;
- normalizing provider/source records;
- scheduled refresh;
- source provenance;
- failure handling;
- catalog bootstrap;
- deterministic test fixtures.

FD002 owns the user/business capability.

EN004 owns the technical mechanism for acquiring and maintaining reference data.

FD002 must not silently introduce a provider-specific integration that becomes part of the product contract.

---

# 17. Explicit Product Decisions

The following decisions are proposed for approval in FD002:

1. Ticker must no longer be unrestricted free text in the Add Position flow.
2. Market must no longer be unrestricted free text.
3. Currency must no longer be unrestricted free text.
4. Financial Instruments are selected from a controlled catalog.
5. Markets use ISO 10383 MIC where available.
6. Initial supported currencies are EUR and USD.
7. Ticker, Market, and Currency must form a valid catalogued listing combination.
8. The frontend queries the My-FinAI-Manager backend/catalog and does not call external reference providers directly.
9. The catalog representation is provider-neutral.
10. The existing FD001 `ticker + market` Position identity remains unchanged.
11. A successful E2E critical journey is mandatory before FD002 can be accepted or completed.

---

# 18. Open Questions

1. Should Market and Currency remain visible selectors after selecting an instrument, or should they be read-only values derived from the selected listing?
2. If one Financial Instrument has multiple supported listings, should the UI show each listing as a separate search result or select the instrument first and then request the listing?
3. Which Financial Instrument types are included initially: equities only, equities + ETFs, or another explicitly approved set?
4. What geographic/market universe must be present in the first reference-data load?
5. Must ISIN be stored when the source provides it?
6. How should existing FD001 Positions created before FD002 be treated if their `ticker + market` combination is not found in the new catalog?

Reference-data acquisition and refresh mechanics should preferably be decided in EN004 rather than embedded as product behavior in FD002.

---

# 19. Human Approval

Before formal specification:

- [ ] Purpose is correct.
- [ ] Scope is correct.
- [ ] Controlled Financial Instrument selection is approved.
- [ ] Ticker is no longer unrestricted free text.
- [ ] Market is no longer unrestricted free text.
- [ ] Currency is no longer unrestricted free text.
- [ ] EUR and USD initial scope is approved.
- [ ] Valid catalogued `ticker + market + currency` combinations are required.
- [ ] Existing FD001 Position identity remains `ticker + market`.
- [ ] Frontend must not call external reference-data providers directly.
- [ ] Provider-neutral catalog representation is approved.
- [ ] Mandatory E2E closure gate is approved.
- [ ] No unapproved behavior has been added.

**Approved by:**  
**Date:**  
**Status:** Draft / Approved / Completed / Superseded
