# FD001 — Create Investment Portfolio

> **Status:** Draft  
> **Feature ID:** FD001  
> **Feature Name:** Create Portfolio  
> **Last Updated:** 2026-08-31  

---

# 1. Purpose

Allow an Investor to create a new investment Portfolio and define its initial Positions.

This feature only covers creation and persistence of the Portfolio.

Displaying, listing, analysing, valuing, editing, or monitoring existing Portfolios is outside the scope of this feature.

---

# 2. User Value

As an Investor, I want to create an investment Portfolio and register its initial Positions so that My-FinAI-Manager can use that Portfolio in later capabilities.

---

# 3. Scope

## In Scope

- Start the creation of a new Portfolio.
- Provide a Portfolio name.
- Add one or more initial Positions before saving.
- For each Position, provide:
  - ticker;
  - market;
  - quantity;
  - currency;
  - initial purchase date, if known;
  - average purchase price, if known.
- Validate the Portfolio before saving.
- Persist the Portfolio and its Positions.

## Out of Scope

- Listing existing Portfolios.
- Viewing Portfolio details after creation.
- Editing an existing Portfolio.
- Adding securities to an already persisted Portfolio.
- Reducing or removing securities from an existing Portfolio.
- Maintaining transaction or movement history.
- Maintaining purchase lots.
- Portfolio valuation.
- Market-price enrichment.
- Risk analysis.
- Recommendations.
- Stop-Loss analysis.
- Automatic currency inference.
- Automatic calculation of a new average purchase price after future purchases.

Those capabilities may be introduced by later Feature Definitions.

---

# 4. Main User Flow

1. The Investor accesses My-FinAI-Manager.
2. The Investor selects **Create Portfolio**.
3. The application requests a name for the Portfolio.
4. The Investor enters the Portfolio name.
5. The Investor selects **Add Position**.
6. The application displays a form or equivalent interaction for entering Position information.
7. The Investor enters the Position information.
8. The Investor confirms the Position.
9. The Investor may repeat steps 5–8 to add additional Positions.
10. The Investor selects **Save**.
11. The system validates the Portfolio and all entered Positions.
12. If validation succeeds, the system persists the Portfolio and its Positions.
13. The system confirms that the Portfolio was created successfully.

---

# 5. Position Information

Each Position contains the following business information.

| Field | Required | Description |
|---|---:|---|
| Ticker | Yes | Instrument ticker symbol |
| Market | Yes | Market where the instrument is identified/traded |
| Quantity | Yes | Number of securities held |
| Currency | Yes | Currency used for acquisition information |
| Initial Purchase Date | No | Initial acquisition date when known by the Investor |
| Average Purchase Price | No | Average acquisition price when known by the Investor |

The Position represents the Investor's current aggregated holding for the identified instrument.

This feature does not model the individual transactions or purchase lots that produced that Position.

---

# 6. Business Rules

## BR-001 — Portfolio Name

A Portfolio must have a non-empty name.

---

## BR-002 — Initial Positions

A Portfolio must contain at least one valid Position before it can be saved.

---

## BR-003 — Financial Instrument Identity

Within a Portfolio, a Position is uniquely identified by:

```text
ticker + market
```

The market should use the Market Identifier Code (MIC) defined by ISO 10383 where available.

---

## BR-004 — Unique Position

A Portfolio cannot contain more than one Position with the same `ticker + market` combination.

Duplicate Positions must be rejected during Portfolio creation.

Future capabilities for adding securities to an existing Position will be defined separately.

---

## BR-005 — Position Quantity

Position Quantity must be greater than zero.

---

## BR-006 — Position Currency

Each Position must define a Currency.

The Currency is entered explicitly by the Investor in this feature.

Currency should be represented using ISO 4217 currency codes.

Automatic currency inference from ticker or market is outside the scope of FD001.

---

## BR-007 — Purchase Price Currency

When Average Purchase Price is provided, it is expressed in the Currency defined for the Position.

---

## BR-008 — Optional Acquisition Information

Initial Purchase Date and Average Purchase Price are optional.

The Portfolio may be created when the Investor does not know either value.

---

## BR-009 — Unknown Acquisition Cost

My-FinAI-Manager must not fabricate or infer an acquisition price when the Investor has not provided one.

Values that depend on acquisition cost may remain unknown until sufficient information becomes available.

---

## BR-010 — Aggregated Position Model

FD001 stores the current aggregated Position.

It does not store the historical transactions, movements, or purchase lots that resulted in that Position.

The decision to maintain a movement history is deferred to a future Feature Definition.

---

# 7. Acceptance Criteria

## AC-001 — Create Portfolio with One Position

**Given** the Investor is creating a new Portfolio  
**When** the Investor provides a valid Portfolio name, adds one valid Position, and selects Save  
**Then** the Portfolio and its Position are persisted successfully.

---

## AC-002 — Create Portfolio with Multiple Positions

**Given** the Investor is creating a new Portfolio  
**When** the Investor adds several valid Positions with different `ticker + market` identities and selects Save  
**Then** the Portfolio is persisted with all entered Positions.

---

## AC-003 — Missing Portfolio Name

**Given** the Investor is creating a new Portfolio  
**When** the Portfolio name is empty  
**Then** the Portfolio cannot be saved  
**And** the application informs the Investor that the name is required.

---

## AC-004 — Invalid Quantity

**Given** the Investor is adding a Position  
**When** Quantity is zero, negative, or otherwise invalid  
**Then** the Position cannot be accepted  
**And** the application informs the Investor that a valid Quantity is required.

---

## AC-005 — Duplicate Financial Instrument

**Given** the Investor has already added a Position identified by a specific `ticker + market`  
**When** the Investor attempts to add another Position using the same `ticker + market`  
**Then** the duplicate Position is rejected  
**And** the application informs the Investor that the Position already exists in the Portfolio.

---

## AC-006 — Unknown Purchase Date

**Given** the Investor is adding a valid Position  
**When** the Investor does not provide Initial Purchase Date  
**Then** the Position may still be added and the Portfolio may be saved.

---

## AC-007 — Unknown Average Purchase Price

**Given** the Investor is adding a valid Position  
**When** the Investor does not provide Average Purchase Price  
**Then** the Position may still be added and the Portfolio may be saved  
**And** the system does not infer a purchase price.

---

## AC-008 — Position Currency

**Given** the Investor provides an Average Purchase Price  
**When** the Position is saved  
**Then** that price is interpreted in the Currency explicitly selected for the Position.

---

# 8. Affected Functional Domains

- Portfolio Management
- Financial Instruments

A functional domain does not imply a technical service or deployment boundary.

---

# 9. Information Objects

| Information Object | Impact |
|---|---|
| Portfolio | Created |
| Position | Created as part of the Portfolio |
| Financial Instrument | Referenced through ticker + market |

---

# 10. Relevant Business Events

- PortfolioCreated
- PositionAdded

These are business events and do not imply Kafka or any other technical messaging mechanism.

---

# 11. UX / Interaction Requirements

The feature requires, at minimum:

- an entry point for **Create Portfolio**;
- a field for the Portfolio name;
- an **Add Position** action;
- a form, dialog, drawer, or equivalent interaction for entering Position information;
- a visible list or summary of Positions currently being added before saving;
- an action to remove or correct a Position before the Portfolio is persisted;
- a **Save** action;
- clear validation feedback;
- successful creation feedback.

Exact layout, component style, colors, typography, spacing, and interaction patterns are governed by global UX/design definitions and feature-specific prototypes when available.

Visual prototypes clarify intended interaction but do not override the business rules or acceptance criteria defined in this Feature Definition.

---

# 12. Expected Implementation Areas

```text
implementation/platform/
├── frontend/        Yes
├── backend/         Yes
├── contracts/       Yes
└── infrastructure/  Yes — persistence required
```

This is a vertical feature and may modify all of these implementation areas while extending the same executable platform.

---

# 13. Testing Expectations

At minimum, verification should cover:

- successful creation with one Position;
- successful creation with multiple Positions;
- missing Portfolio name;
- invalid Position Quantity;
- duplicate `ticker + market`;
- optional purchase date;
- optional purchase price;
- currency handling;
- persistence of Portfolio and Positions;
- API contract behavior;
- frontend-to-backend creation flow.

Integration tests against application-managed persistence must follow the project's Testcontainers policy.

---

# 14. Explicit Product Decisions

The following decisions are approved for FD001:

1. A Financial Instrument is identified within a Portfolio by `ticker + market`.
2. Market should use ISO 10383 MIC where available.
3. Duplicate `ticker + market` Positions are not allowed.
4. Currency is entered manually by the Investor.
5. Currency uses ISO 4217 representation.
6. Average Purchase Price, when provided, uses the Position Currency.
7. Initial Purchase Date is optional.
8. Average Purchase Price is optional.
9. FD001 stores only the aggregated Position.
10. Transaction/movement history is explicitly deferred to a future feature.
11. Adding securities to an already persisted Portfolio is explicitly deferred to a future feature.
12. Listing existing Portfolios is explicitly deferred to the next Feature Definition.

---

# 15. Open Questions

No known product-blocking questions remain for the initial specification of FD001.

UX details may still be refined through visual design artifacts without changing the business intent of this Feature Definition.

---

# 16. Human Approval

Before formal specification:

- [ ] Purpose is correct.
- [ ] Scope is correct.
- [ ] Business rules are approved.
- [ ] Acceptance criteria reflect intended behavior.
- [ ] Explicit product decisions are approved.
- [ ] No unapproved behavior has been added.

**Approved by:**  
**Date:**  
**Status:** Draft / Approved / Superseded
