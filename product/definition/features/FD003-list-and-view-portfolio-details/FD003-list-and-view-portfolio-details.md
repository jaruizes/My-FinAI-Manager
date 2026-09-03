# FD003 — List and View Portfolio Details

> **Status:** Approved  
> **Feature ID:** FD003  
> **Feature Name:** List and View Portfolio Details  
> **Last Updated:** 2026-09-03  

---

# 1. Purpose

Allow an Investor to see their saved Portfolios directly from the application Home page and access the detail of a selected Portfolio.

The Home page must display the existing Portfolios in a table.

When the Investor selects a Portfolio row, the application must navigate to a Portfolio detail view showing the Positions currently stored in that Portfolio.

If no Portfolios exist, the application must display an explicit empty-state message instead of an empty table.

---

# 2. User Value

As an Investor, I want to see my saved Portfolios when I enter the application and inspect the Positions contained in a selected Portfolio so that I can immediately understand which Portfolios exist and what they contain.

---

# 3. Scope

## In Scope

- Load the Investor's existing Portfolios when the Home page is accessed.
- Display the saved Portfolios in a table.
- Display one row per Portfolio.
- Display at least:
  - Portfolio name;
  - number of Positions.
- Allow the Investor to select a Portfolio row.
- Navigate to a Portfolio detail view.
- Load the selected Portfolio and its persisted Positions.
- Display the Portfolio Positions in a table.
- Display one row per Position.
- Provide a clear empty state when no Portfolios exist.
- Reuse the Portfolio and Position data already persisted by FD001.
- Preserve the existing application visual design conventions.

## Out of Scope

- Editing a Portfolio.
- Deleting a Portfolio.
- Adding Positions to an existing Portfolio.
- Removing Positions.
- Portfolio valuation.
- Portfolio performance.
- Risk analysis.
- Recommendations.
- Stop-Loss information.
- Sorting.
- Filtering.
- Searching.
- Pagination.
- Portfolio actions from the list or detail unless separately defined by another Feature Definition.

---

# 4. Main User Flow

1. The Investor enters My-FinAI-Manager.
2. The application displays the Home page.
3. The application loads the Investor's saved Portfolios.
4. If one or more Portfolios exist:
   - the application displays a table;
   - each Portfolio is shown as one row;
   - each row displays at least Portfolio name and Position count.
5. If no Portfolios exist:
   - the application displays an explicit message indicating that no Portfolios exist.
6. The Investor selects one Portfolio row.
7. The application navigates to the selected Portfolio detail.
8. The application loads the selected Portfolio and its persisted Positions.
9. The detail view displays the Portfolio name and one table row per Position.

---

# 5. Portfolio List Information

For FD003, each Home table row must display at least:

| Field | Required | Description |
|---|---:|---|
| Portfolio Name | Yes | Name assigned to the Portfolio |
| Number of Positions | Yes | Number of current Positions in the Portfolio |

FD003 does not require valuation, profit/loss, performance, currency totals, risk level, recommendation status, or last review information.

---

# 6. Portfolio Detail Information

The Portfolio detail view must display:

- Portfolio name;
- a table containing the Portfolio's persisted Positions.

Each Position row must display at least:

| Field | Required | Description |
|---|---:|---|
| Ticker | Yes | Financial Instrument ticker |
| Market | Yes | Market/MIC associated with the Position |
| Quantity | Yes | Current aggregated quantity |
| Currency | Yes | Position currency |
| Initial Purchase Date | When available | Initial acquisition date |
| Average Purchase Price | When available | Average acquisition price |

FD003 does not calculate or display current market price, valuation, profit/loss, performance, risk, recommendations, or Stop-Loss information.

---

# 7. Business Rules

## BR-001 — Investor Portfolios Only

The Home page must display only Portfolios belonging to the current Investor.

## BR-002 — Persisted Portfolios Only

Only successfully persisted Portfolios are displayed.

Draft Portfolio creation state that has not been saved must not appear in the list.

## BR-003 — Empty State

If the Investor has no saved Portfolios, the Home page must display a clear empty-state message.

An empty table without explanation is not sufficient.

## BR-004 — One Row per Portfolio

Each saved Portfolio must be represented by one row in the Home table.

## BR-005 — Position Count

The Position count displayed for a Portfolio represents the number of persisted Positions currently associated with that Portfolio.

## BR-006 — Portfolio Row Selection

A displayed Portfolio row must allow the Investor to access that Portfolio's detail.

## BR-007 — Portfolio Detail

The detail view must represent the currently persisted state of the selected Portfolio.

## BR-008 — One Row per Position

Each persisted Position of the selected Portfolio must be represented by one row in the Position table.

## BR-009 — Read-Only Detail

FD003 is read-only.

The detail view must not allow editing, adding, or removing Positions.

---

# 8. Acceptance Criteria

## AC-001 — List One Portfolio

**Given** the Investor has one saved Portfolio  
**When** the Investor enters the application Home page  
**Then** the application displays the Portfolio in the Portfolios table.

## AC-002 — List Multiple Portfolios

**Given** the Investor has multiple saved Portfolios  
**When** the Investor enters the application Home page  
**Then** the application displays one row for each saved Portfolio.

## AC-003 — Portfolio Information

**Given** a saved Portfolio contains Positions  
**When** the Portfolio is displayed in the Home table  
**Then** the row displays:
- the Portfolio name;
- the number of Positions contained in the Portfolio.

## AC-004 — No Portfolios

**Given** the Investor has no saved Portfolios  
**When** the Investor enters the application Home page  
**Then** the application displays a message indicating that no Portfolios exist  
**And** it does not display a misleading empty Portfolio row.

## AC-005 — Newly Created Portfolio Is Listed

**Given** the Investor successfully created a Portfolio through FD001  
**When** the Investor subsequently accesses the Home page  
**Then** the newly persisted Portfolio appears in the Portfolio list.

## AC-006 — Open Portfolio Detail

**Given** the Investor has multiple saved Portfolios  
**And** one Portfolio contains known Positions  
**When** the Investor selects that Portfolio from the Home table  
**Then** the application navigates to its detail view  
**And** displays the selected Portfolio name  
**And** displays its persisted Positions.

## AC-007 — Correct Position Detail

**Given** a Portfolio contains several persisted Positions  
**When** the Investor opens that Portfolio  
**Then** one row is displayed for each Position  
**And** the Position data corresponds to the persisted Portfolio  
**And** Positions belonging to another Portfolio are not displayed.

---

# 9. Affected Functional Domains

- Portfolio Management

A functional domain does not imply a technical service or deployment boundary.

---

# 10. Information Objects

| Information Object | Impact |
|---|---|
| Portfolio | Read |
| Position | Read |

FD003 does not modify Portfolio or Position state.

---

# 11. Relevant Business Events

FD003 does not introduce new business events.

Listing or viewing Portfolio data is a query operation and does not represent a new business fact by itself.

---

# 12. UX / Interaction Requirements

The Home page must contain a clear Portfolio listing area.

When Portfolios exist:

```text
My Portfolios

┌───────────────────────┬───────────┐
│ Portfolio             │ Positions │
├───────────────────────┼───────────┤
│ Long Term Investment  │     8     │
│ Technology            │     4     │
└───────────────────────┴───────────┘
```

The Portfolio row must be selectable.

When no Portfolios exist:

```text
My Portfolios

No portfolios available.
```

The Portfolio detail view must show:

```text
Portfolio: Long Term Investment

┌────────┬────────┬──────────┬──────────┬────────────────┬──────────────────────┐
│ Ticker │ Market │ Quantity │ Currency │ Purchase Date  │ Average Purchase Price│
├────────┼────────┼──────────┼──────────┼────────────────┼──────────────────────┤
│ AAPL   │ XNAS   │ 10       │ USD      │ 2025-01-10     │ 185.00               │
│ SAN    │ XMAD   │ 50       │ EUR      │ 2024-03-15     │ 4.10                 │
└────────┴────────┴──────────┴──────────┴────────────────┴──────────────────────┘
```

The table and detail view must follow the global My-FinAI-Manager design system.

FD003 does not require row actions, sorting controls, filters, pagination, or edit controls.

---

# 13. Expected Implementation Areas

```text
implementation/platform/
├── frontend/       Yes
├── backend/        Yes
├── contracts/      Yes
└── infrastructure/ Yes — existing persistence reused
```

FD003 extends the existing `portfolio` functional module.

It must comply with ADR-003:

```text
portfolio/
├── domain/
├── business/
└── infrastructure/
```

---

# 14. API Expectation

FD003 requires read capabilities for the current Investor's Portfolios and for a selected Portfolio detail.

Conceptually:

```text
GET /portfolios
GET /portfolios/{portfolioId}
```

The exact paths and API contracts must follow the project's REST/OpenAPI conventions.

The list response must contain only the information required by FD003 unless an existing approved contract already provides an equivalent representation.

The detail response must return the selected Portfolio and its Positions.

Provider or persistence-specific models must not leak into the API.

---

# 15. Testing Expectations

At minimum, verification must cover:

- zero Portfolios;
- one Portfolio;
- multiple Portfolios;
- correct Portfolio name;
- correct Position count;
- only persisted Portfolios are returned;
- selected Portfolio detail;
- correct Position rows;
- exclusion of Positions belonging to another Portfolio;
- API contract behavior;
- PostgreSQL integration;
- Home page table rendering;
- Home page empty state;
- navigation from list to detail;
- Portfolio detail table rendering;
- regression of Portfolio creation.

Integration tests against PostgreSQL must follow the project's Testcontainers policy.

---

# 16. E2E Testing

FD003 introduces a critical browser journey.

Automated Playwright End-to-End verification is mandatory.

## E2E-001 — List Portfolios and View Portfolio Detail

The E2E test must create its own deterministic test data before verifying the UI.

### Test Preparation

The test must create exactly three Portfolios with different Position counts.

A valid deterministic setup is:

```text
Portfolio Alpha
  Positions: 1

Portfolio Beta
  Positions: 2

Portfolio Gamma
  Positions: 3
```

The exact Portfolio names and Financial Instruments are test data and are not product requirements.

The three Portfolios should be created through an approved application-facing mechanism.

The existing Portfolio creation API should be preferred for E2E setup over direct database insertion.

The test must not insert Portfolio or Position records directly into PostgreSQL as its primary setup mechanism when the existing Portfolio creation API can create the required state.

### Portfolio List Verification

**Given** the three Portfolios have been created successfully  
**When** the Investor opens the application Home page  
**Then** all three Portfolios are displayed  
**And** each Portfolio displays the correct number of Positions.

The test must verify the identity and count of each expected Portfolio rather than only verifying that three rows exist.

Conceptually:

```text
Portfolio Alpha     1
Portfolio Beta      2
Portfolio Gamma     3
```

### Portfolio Detail Verification

After the list assertions succeed:

**When** the test selects one of the three Portfolio rows  
**Then** the application navigates to the selected Portfolio detail  
**And** the selected Portfolio name is displayed  
**And** the detail displays exactly the Positions belonging to that Portfolio  
**And** the number of displayed Positions is correct  
**And** each displayed Position corresponds to the Position data used when the Portfolio was created  
**And** Positions belonging to either of the other two Portfolios are not displayed.

The E2E journey must exercise:

```text
Playwright
    ↓
Portfolio creation through application API
    ↓
Home
    ↓
Portfolio list API
    ↓
Portfolio table
    ↓
click Portfolio row
    ↓
Portfolio detail API
    ↓
Position table
    ↓
PostgreSQL
```

The UI verification must use the real containerized frontend/backend/PostgreSQL platform.

Frontend-to-backend communication and PostgreSQL must not be mocked.

Using the application API only to prepare deterministic test state is allowed and does not replace the browser verification of FD003 behavior.

**FD003 must not be accepted, closed, or marked Completed if E2E-001 is missing or failing.**

## E2E-002 — Empty Portfolio List

A separate E2E scenario must verify the empty state.

**Given** the test environment contains no Portfolios for the Investor  
**When** the Investor opens the application Home page  
**Then** the application displays the empty-state message  
**And** no Portfolio row is displayed.

**FD003 must not be accepted, closed, or marked Completed if E2E-002 is missing or failing.**

---

# 17. Explicit Product Decisions

The following decisions are proposed for approval:

1. Portfolios are displayed directly on the Home page.
2. The Portfolio list is represented as a table.
3. Each saved Portfolio is represented by one row.
4. The list displays Portfolio name and number of Positions.
5. If no Portfolios exist, a clear empty-state message is shown.
6. Clicking/selecting a Portfolio row opens its detail.
7. Portfolio detail displays the Portfolio name and persisted Positions.
8. Each Position is represented by one row in the detail table.
9. Position detail displays Ticker, Market, Quantity, Currency, Initial Purchase Date when available, and Average Purchase Price when available.
10. FD003 is entirely read-only.
11. FD003 does not include sorting, filtering, searching, or pagination.
12. E2E-001 must create three Portfolios with different Position counts and verify their exact counts.
13. E2E-001 must select one Portfolio and verify its exact persisted Positions.
14. E2E-002 verifies the no-Portfolios empty state.
15. Successful E2E verification is mandatory for Feature closure.

---

# 18. Open Questions

No known product-blocking questions remain for the initial FD003 definition.

Any additional list columns, Portfolio actions, Position actions, sorting, filtering, searching, pagination, valuation information, or navigation behavior beyond opening the detail must be explicitly approved rather than inferred during specification or implementation.

---

# 19. Human Approval

Before formal specification:

- [X] Purpose is correct.
- [X] Scope is correct.
- [X] Home-page placement is approved.
- [X] Portfolio table presentation is approved.
- [X] Portfolio Name column is approved.
- [X] Position Count column is approved.
- [X] Empty-state behavior is approved.
- [X] Row selection/navigation to Portfolio detail is approved.
- [X] Position detail table is approved.
- [X] Position detail fields are approved.
- [X] FD003 remains read-only.
- [X] No sorting/filtering/pagination is included.
- [X] Three-Portfolio E2E setup is approved.
- [X] Exact Position-count E2E assertions are approved.
- [X] Portfolio detail E2E assertions are approved.
- [X] Empty-state E2E is approved.
- [X] Mandatory E2E closure gate is approved.
- [X] No unapproved behavior has been added.

**Approved by:*jaruiz*  
**Date:*2026-09-03*  
**Status:** Approved
