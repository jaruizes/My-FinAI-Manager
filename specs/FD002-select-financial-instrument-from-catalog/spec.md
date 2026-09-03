# Feature Specification: Select Financial Instrument from Catalog (FD002)

**Feature Branch**: `FD002-select-financial-instrument-from-catalog`

**Created**: 2026-09-03

**Status**: Approved

**Input**: Feature Definition: "Allow an Investor to select the financial instrument, market, and
currency of a Portfolio Position from controlled values instead of entering those values as
unrestricted free text. Reduce data-entry errors and ensure Position identifiers use known,
normalized, valid reference data. FD002 evolves the FD001 Position-creation interaction; it does not
change the meaning of a Position or the existing Portfolio-creation capability."

**Authoritative Source**:
`product/definition/features/FD002-select-financial-instrument-from-catalog/FD002-select-financial-instrument-from-catalog.md`
*(the invoking prompt referenced `feature-definition.md`; the actual file in the repository is
`FD002-select-financial-instrument-from-catalog.md` — no `feature-definition.md` exists).*

**Technical capability already available**:
`EN004 — Establish Financial Instrument Reference Data` (Approved 2026-09-03). EN004 delivers the
local PostgreSQL Market + Financial Instrument catalog, the mapping-driven ingestion that populates
it on backend start, and the read-only search operation **`GET /api/financial-instruments?query=…`**
(active EUR/USD listings only; exact-ticker matches first, then name-contains; provider-neutral
response `{ id, name, ticker, market (MIC), currency, active, isin }`; blank query →
`400 application/problem+json`; no match → `200 []`). FD002 consumes this capability — it does not
re-implement the catalog.

**Governing decisions**: ADR-001 (single `core-service` deployable — unchanged); ADR-003 (Standard
Spring Backend Architecture); the containerized platform + browser-E2E foundation from EN002; the
FD001 Create Portfolio capability and its `POST /api/portfolios` contract; the global design system
`product/ux/design-system.md`. **No new ADR is anticipated** — FD002 is a frontend change plus, at
most, one added server-side validation rule and its contract update within the existing topology.
Any material architectural decision surfaced during planning MUST be raised for human approval.

> **Governance note.** The Feature Definition
> `product/definition/features/FD002-select-financial-instrument-from-catalog.md` was **approved by
> the human on 2026-09-03** (§19 signed by jaruiz; `Status: Approved`), together with the resolution
> of all six §18 Open Questions (recorded in the enabler as "Open Question resolutions (2026-09-03)"
> and here as Clarifications + Assumptions A5–A9). OQ1 and the enforcement question — which
> materially affect UX and the public contract — were resolved with the user on 2026-09-03 and are
> reflected in FR-007 / FR-011 / FR-020. All prerequisite gates for `/speckit-plan` are cleared.
> The only human-directed `product/` edits for FD002 are: the §18/§19 approval sync on the Feature
> Definition, and **`architecture-rules.md` AR-062** (inter-module reads via a published port —
> OD-FD002-4, confirmed 2026-09-03). Both were raised for and given human approval (FR-030).

---

## Overview *(mandatory)*

FD002 replaces the three free-text fields in the FD001 **Add Position** interaction — Ticker,
Market, Currency — with **controlled selection of a Financial Instrument listing** from the local
catalog delivered by EN004. The Investor searches by ticker or instrument name, picks one listing,
and the Position's ticker, market (MIC), and currency are taken from that catalog entry. The
platform prevents a Position from being created with a `ticker + market + currency` combination that
is not an active, selectable catalogued listing.

FD002 changes **how** Position reference values are chosen and validated. It does **not** change:

- the meaning of a Position or a Portfolio;
- the FD001 `ticker + market` Position identity (FD002 §7 BR-009);
- the FD001 Create Portfolio flow, its validation rules, its idempotency behavior, or its
  persistence semantics;
- the set of things a Position records (quantity, optional initial purchase date, optional average
  purchase price).

**Explicitly out of scope** (FD002 §3 "Out of Scope" plus derived boundaries):
live market prices, portfolio valuation, automatic FX conversion, Position currencies other than
EUR/USD, user-created instruments or markets, real-time exchange synchronization, guaranteed global
instrument coverage, crypto, derivatives beyond what the catalog already contains, market-data or
news enrichment, automatic recommendations, any direct dependency of the user-facing form on an
external reference-data provider, and the **reference-data acquisition / refresh mechanics** (owned
by EN004). Listing, viewing, or editing a Portfolio or its Positions after creation remains out of
scope (no such capability exists yet — carried from FD001).

---

## Clarifications

### Session 2026-09-03

The following were resolved from EN004's delivered capability and the FD002 Feature Definition, and
are recorded as **Assumptions** (A-series) below:

- FD002 §18 OQ2 (multiple listings) → each supported listing of the same economic instrument is a
  **separate selectable search result** — this is exactly what EN004's search returns (one row per
  `ticker + market`). *(A5)*
- FD002 §18 OQ3 (instrument types) → the selectable catalog contains **equities and ETFs**; type is
  descriptive metadata, never a search filter (settled by EN004 spec FR-040 / EN004 Clarifications
  2026-09-03). *(A6)*
- FD002 §18 OQ4 (market universe) → whatever EN004's committed catalog contains; FD002 mandates no
  specific geographic coverage. *(A7)*
- FD002 §18 OQ5 (ISIN) → ISIN is stored by EN004 when the source provides it and is shown in search
  results when present (aids disambiguation). *(A8)*
- FD002 §18 OQ6 (pre-FD002 Positions whose `ticker + market` is not in the catalog) → **not
  applicable** in FD002 — there is no capability to list or edit an existing Position, so no
  Position is ever re-validated against the catalog. FD002 governs **new Position creation only**.
  *(A9)*

**Resolved 2026-09-03 (user):**

- Q: FD002 §18 OQ1 — after an instrument is selected, are Market and Currency read-only derived
  values or still-adjustable selectors? → A: **Constrained selectors after picking the instrument.**
  The Investor first selects a Financial Instrument (by name or ticker). If that instrument has more
  than one supported catalogued listing, the Investor then selects the specific listing via
  **Market / Currency selectors that offer only that instrument's real catalogued listing
  combinations**. If it has exactly one supported listing, that listing's Market and Currency are
  applied directly. In every case the resulting `ticker + market + currency` is a combination that
  exists in the catalog; a combination that does not exist can never be assembled. *(→ FR-007)*
- Q: Is the "invalid combination cannot be created" guarantee (BR-004 / AC-006) enforced
  server-side or frontend-only? → A: **The backend validates each Position on
  `POST /api/portfolios`** against the catalog and rejects a Position unless the catalog holds **one
  active listing whose `ticker + market + currency` all match** (wrong market, inactive listing, or a
  submitted currency ≠ the listing's currency are all rejected — currency-match pinned 2026-09-03),
  with a new stable `ValidationProblem` code **`INSTRUMENT_NOT_IN_CATALOG`**. This is a
  contract-first change to the `POST /api/portfolios` OpenAPI operation plus a contract test; the
  frontend still constrains selection, but the
  guarantee no longer depends on the client. *(→ FR-011, FR-020)*

---

## User Scenarios & Testing *(mandatory)*

The actor is the **Investor** (glossary: the person who owns and manages one or more portfolios).
Each story is an independently demonstrable slice of the FD001 Create Portfolio journey; **US1 alone
is a viable MVP** (an Investor can add a Position by picking a real instrument instead of typing
codes).

### User Story 1 - Add a Position by selecting a known instrument (Priority: P1)

An Investor creating a Portfolio opens **Add Position**, searches for an instrument by ticker or by
company/instrument name, sees matching catalogued results (each showing enough to tell them apart —
instrument name, ticker, market/MIC, currency, and ISIN when available), and selects one. If that
instrument has a single supported listing, the Position's Ticker, Market, and Currency are filled
from it; if it has several, the Investor picks the specific listing from constrained Market/Currency
selectors. The Investor then enters Quantity (and optionally initial purchase date / average
purchase price) and confirms the Position, and the FD001 flow continues unchanged.

**Why this priority**: This is the feature's purpose (FD002 §1–§2, §4). Without it, the Investor
still types ticker/market/currency by hand.

**Independent Test**: With EN004's catalog populated, open Add Position, search `AAPL` → select
"Apple Inc." → the form shows ticker `AAPL`, market `XNAS`, currency `USD`; add quantity and
confirm → the Position appears in the draft list and the Portfolio saves through the existing
`POST /api/portfolios`.

**Acceptance Scenarios**:

1. **Given** the catalog contains Apple Inc. listed as `AAPL · XNAS · USD` (active) as its only
   supported listing, **When** the Investor searches `AAPL` (any case) in Add Position **and**
   selects Apple Inc., **Then** the Position uses ticker `AAPL`, market `XNAS`, currency `USD`,
   taken from that catalog entry. *(FD002 AC-001, AC-003)*
2. **Given** the catalog contains an instrument whose name is "Banco Santander, S.A.", **When** the
   Investor types part of the name (`santander`, any case), **Then** the matching instrument(s) are
   shown as selectable results. *(FD002 AC-002)*
3. **Given** search results are shown, **When** the Investor inspects one result, **Then** it
   displays at least the instrument name, ticker, market (MIC), and currency — and ISIN when the
   catalog has one — so two similarly named instruments can be told apart. *(FD002 §4.6, §12)*
4. **Given** the selected instrument has **more than one** supported catalogued listing, **When**
   the Investor has selected the instrument, **Then** they choose the specific listing via
   Market / Currency selectors that offer **only** that instrument's real catalogued listing
   combinations; the Position's ticker, market, and currency are taken from the chosen listing.
   *(FD002 §5, §17.7, AC-004, AC-005; resolved OQ1)*
5. **Given** an instrument and (where applicable) a listing are selected, **When** the Investor
   completes Quantity and confirms, **Then** the Position is added to the draft Portfolio and the
   existing FD001 Create Portfolio flow (validation, idempotent Save, persistence) continues without
   any change to its business semantics. *(FD002 AC-009; BR-009)*
6. **Given** the Investor has not selected an instrument (and, where required, a listing), **When**
   they try to confirm the Position, **Then** confirmation is blocked with clear feedback about what
   must be selected — the typed search text is never accepted as an instrument. *(FD002 AC-008;
   BR-001)*

---

### User Story 2 - Invalid instrument/market/currency combinations cannot be created (Priority: P1)

The Investor cannot end up with a Position whose `ticker + market + currency` is not an active,
selectable catalogued listing — not by editing the populated fields, not by selecting an inactive
listing, and not by choosing a currency the catalog does not support for that listing.

**Why this priority**: This is the safety guarantee that makes controlled selection meaningful
(FD002 BR-001, BR-004, BR-005, BR-006; AC-006, AC-007). A selector that can still be overridden into
an invalid state delivers little over free text.

**Independent Test**: After selecting an instrument, the Market/Currency selectors offer only that
instrument's real catalogued listings — `AAPL + XMAD + EUR` is never an option; a Position submitted
directly to `POST /api/portfolios` with `AAPL + XMAD + EUR` (wrong market) **or** `AAPL + XNAS + EUR`
(wrong currency) is rejected with `INSTRUMENT_NOT_IN_CATALOG`; an instrument whose only listing is
inactive is not offered.

**Acceptance Scenarios**:

1. **Given** the catalog has `AAPL · XNAS · USD` and no `AAPL · XMAD · EUR` listing, **When** the
   Investor selects Apple Inc., **Then** the Market/Currency selectors do not offer `XMAD` / `EUR`
   for that instrument — an invalid combination cannot be assembled in the interface. *(FD002
   AC-006; BR-004, BR-005)*
2. **Given** any client submits `POST /api/portfolios` with a Position whose
   `ticker + market + currency` is not one **active catalogued listing** — wrong market, inactive
   listing, unknown instrument, **or** a currency that differs from the listing's (`AAPL + XNAS + EUR`
   vs the USD listing) — **When** the request is processed, **Then** the platform rejects it with a
   `ValidationProblem` carrying `errors[].code = INSTRUMENT_NOT_IN_CATALOG` for that position, and
   **nothing is persisted**. *(FD002 AC-006; BR-004; resolved enforcement Clarification)*
3. **Given** a listing is marked **inactive** in the catalog, **When** the Investor searches for
   instruments to add, **Then** it is not selectable for a new Position, and a direct submission
   referencing it is rejected as in AS2. *(FD002 AC-007; BR-006)* — EN004's search already excludes
   inactive and non-EUR/USD listings.
4. **Given** the Investor selected a valid instrument + listing, **When** the Position is displayed,
   **Then** Ticker, Market, and Currency are controlled by that catalogued listing and cannot drift
   into a combination that does not exist in the catalog. *(FD002 AC-004, AC-005; BR-005)*

---

### User Story 3 - Clear handling of "no match" and unavailable instruments (Priority: P2)

When the Investor's search matches nothing, or the only candidate is not selectable, the interface
says so plainly and never turns the typed text into an instrument.

**Why this priority**: Prevents silent bad data and dead ends (FD002 AC-008, §12 "no-results
state"). Important, but the core value (US1/US2) can be demonstrated first.

**Independent Test**: Search a string that matches no catalogued instrument → a clear "no matching
instrument found" state is shown, with no option to proceed using the typed text; the search box
shows a loading state while a query is in flight.

**Acceptance Scenarios**:

1. **Given** the Investor searches the catalog, **When** no catalogued instrument matches, **Then**
   the interface informs the Investor that nothing matched **and** does not accept the entered text
   as a new instrument. *(FD002 AC-008)*
2. **Given** a search request is in progress, **When** results have not yet returned, **Then** a
   search/loading state is shown (design system §"Loading States"); the rest of the page is not
   blocked. *(FD002 §12)*
3. **Given** the catalog/search capability is temporarily unavailable, **When** the Investor
   searches, **Then** a recoverable-error state is shown (not a technical stack trace) and the
   Investor can retry; no Position is created from unvalidated input. *(FD002 §12; design system
   §"Error States")*

---

### User Story 4 - The FD001 Create Portfolio journey still works, proven end to end (Priority: P2)

Every FD001 Create Portfolio behavior — one or many Positions, the duplicate-instrument rule,
optional acquisition data, idempotent Save, the transient-failure message — continues to work with
the new selection interaction, and a browser E2E covers the full journey through the real
containerized platform.

**Why this priority**: FD002 explicitly must not regress FD001 (FD002 §1, AC-009) and its own
closure gate is a passing E2E (FD002 §14, §17.11). P2 because it is verification of US1–US3 rather
than new user value.

**Independent Test**: Run the FD001 suites unchanged (adjusted only where the Add Position input
mechanism changed) → green; run the FD002 E2E: Create Portfolio → Add Position → search/select an
instrument → confirm the controlled ticker/market/currency → Save → Portfolio persisted.

**Acceptance Scenarios**:

1. **Given** the FD001 Create Portfolio capability, **When** FD002 is delivered, **Then** creating a
   portfolio with one Position, with several Positions, with optional acquisition data, the
   duplicate `ticker + market` rejection, the idempotent re-Save, and the transient-failure
   experience all still behave as specified by FD001. *(FD002 AC-009)*
2. **Given** the containerized platform with EN004's catalog populated, **When** the FD002
   Playwright critical journey runs (`./e2e.sh`), **Then** it drives Create Portfolio → Add Position
   → search + select a Financial Instrument → the controlled ticker/market/currency → Save → the
   Portfolio is persisted, and it passes with **exit 0**. *(FD002 §14; SC — closure gate)*
3. **Given** FD002's E2E is missing or failing, **When** closure is considered, **Then** FD002
   **cannot** be accepted, closed, or marked Completed. *(FD002 §14, §17.11)*

---

### Edge Cases

- **Catalog empty / not yet imported** — EN004's startup import populates the catalog; if it is
  empty, every search returns "no match" and no Position can be added. The platform still starts
  (EN004 fail-safe). FD002 does not add its own catalog bootstrap.
- **Query shorter than the minimum / blank** — EN004's search treats a blank/whitespace query as
  `400`; the interface should not issue a search for an empty box and should show its idle/hint
  state instead.
- **Very common name substring** — a name search may return many listings; results are ordered
  exact-ticker-first then by ticker (EN004). The interface must keep results scannable (design
  system §"compact information density"); pagination / result-cap behavior is a planning-level UX
  detail, not a product rule in this spec.
- **Same ticker on multiple markets** (e.g. a dual listing) — shown as separate results
  distinguished by market/MIC and currency (A5).
- **Instrument active but priced in an unsupported currency** — excluded by EN004's search filter
  (`currency ∈ {EUR, USD}`); never selectable in FD002. *(BR-003)*
- **Listing becomes inactive between search and Save** — the backend re-checks the catalog on
  `POST /api/portfolios` (FR-011), so a Position for a now-inactive listing is rejected with
  `INSTRUMENT_NOT_IN_CATALOG` and nothing is persisted.
- **Investor changes the Market/Currency selector** — the selector only ever offers the selected
  instrument's real catalogued listings (FR-007), so an invalid combination cannot be produced;
  there is no free-text field to override.
- **Keyboard-only Investor** — search, result navigation, and selection must be operable by
  keyboard with visible focus (design system §"Accessibility"; FD002 §12).
- **Network failure during search** — recoverable-error state, retry available, no partial Position
  created (US3 AS3).

---

## Requirements *(mandatory)*

> Requirements trace to the FD002 Feature Definition (approved 2026-09-03). The two 2026-09-03
> Clarifications are resolved (FR-007, FR-011, FR-020).

### Controlled Add Position interaction

- **FR-001**: The Add Position interaction MUST NOT present unrestricted free-text controls for
  **Ticker**, **Market**, or **Currency**. Those values MUST come from a selected Financial
  Instrument listing in the catalog. *(FD002 §3, §12, §17.1–§17.3; BR-001, BR-002, BR-003)*
- **FR-002**: The Investor MUST be able to search the Financial Instrument catalog from within the
  Add Position interaction by **ticker** and by **instrument name**, case-insensitively. *(FD002 §3,
  §4.4, AC-002, AC-003)*
- **FR-003**: Search MUST run against the platform's own catalog through the backend
  (`GET /api/financial-instruments`); the frontend MUST NOT call any external reference-data
  provider directly. *(FD002 BR-007, BR-008, §15; constitution VIII)*
- **FR-004**: Each search result MUST show at least the **instrument name, ticker, market (MIC), and
  currency**, and MUST show **ISIN** when the catalog provides one, so that similar listings can be
  distinguished. *(FD002 §4.6, §12; A8)*
- **FR-005**: Only **active** listings priced in **EUR or USD** MUST be offered for selection.
  Inactive listings and listings in other currencies MUST NOT be selectable for a new Position.
  *(FD002 BR-003, BR-006, AC-007; A6)* — EN004's search already enforces this filter.
- **FR-006**: When the Investor selects a Financial Instrument, the Position's **Ticker** MUST be
  taken from the catalog. When that instrument has exactly one supported catalogued listing, its
  **Market** and **Currency** MUST be applied directly from that listing. *(FD002 §4.8, §5, AC-001,
  BR-005)*
- **FR-007** *(resolved — OQ1)*: When the selected instrument has **more than one** supported
  catalogued listing, the Investor MUST choose the specific listing through **Market / Currency
  selectors that offer only that instrument's real catalogued listing combinations**. The Position's
  Ticker, Market, and Currency are then taken from the chosen listing. In no case may the interface
  let the Investor assemble a `ticker + market + currency` combination that is not a catalogued
  listing. *(FD002 §5, §12, §17.7, BR-005, AC-004, AC-005)*
- **FR-008**: The search MUST let the Investor tell apart the supported listings of one economic
  instrument (by market/MIC and currency), whether shown as separate results or grouped under the
  instrument with a listing choice. *(FD002 §5; A5)*
- **FR-009**: A Position MUST NOT be confirmable until a catalogued instrument — and, where the
  instrument has multiple supported listings, a specific listing — has been selected; the typed
  search text MUST NOT be usable as an instrument. *(FD002 BR-001, AC-008)*

### Validity enforcement

- **FR-010**: The **frontend** MUST make it impossible to assemble a Position whose
  `ticker + market + currency` is not an active, EUR/USD, catalogued listing — the Market/Currency
  choices are constrained to the selected instrument's real listings (FR-007), and there is no
  free-text override. *(FD002 BR-004, BR-005, AC-006)*
- **FR-011** *(resolved — enforcement Clarification; currency-match pinned 2026-09-03)*: The
  **backend** MUST validate each Position on `POST /api/portfolios` against the catalog and MUST
  reject a Position unless the platform catalog holds **one active listing** whose
  `ticker + market + currency` **all** match the submitted values (so: unknown instrument, listing
  on a different market, inactive listing, **or a submitted currency that differs from the listing's
  currency** — e.g. `AAPL + XNAS + EUR` when the listing is USD — are all rejected). The rejection
  MUST be a `ValidationProblem` with a stable `errors[].code` of **`INSTRUMENT_NOT_IN_CATALOG`**
  scoped to the offending `positions[i]`, and **nothing MUST be persisted**. This makes the
  guarantee independent of the client and testable below the UI (`testing-strategy.md` —
  security-critical behavior must not rely solely on the frontend). A position missing a required
  `ticker` / `market` / `currency` is handled by the FD001 structural checks and is not
  catalog-checked. *(FD002 BR-004, BR-005, AC-006)*
- **FR-012**: FD002 MUST NOT change the FD001 **Position identity** rule — within a Portfolio a
  Position remains uniquely identified by `ticker + market`, and the FD001 duplicate-instrument
  rejection is unchanged. The catalog check of FR-011 is **in addition to**, not a replacement for,
  the existing FD001 position validations. *(FD002 BR-009, AC-009; FD001)*

### No-results, loading, and error states

- **FR-013**: When a search matches no catalogued instrument, the interface MUST clearly say so and
  MUST NOT accept the entered text as a new instrument. *(FD002 AC-008, §12)*
- **FR-014**: The interface MUST show a **search / loading** state while a query is in flight and a
  distinct **idle** state for an empty search box; it MUST NOT issue a search for a blank query.
  *(FD002 §12; design system §"Loading States"; EN004 blank-query `400`)*
- **FR-015**: A failure of the search capability MUST be shown as a recoverable error in plain
  language (no stack trace, no infrastructure detail), with a retry path, and MUST NOT result in a
  Position built from unvalidated input. *(FD002 §12; design system §"Error States"; constitution)*

### FD001 integration & regression

- **FR-016**: FD002 MUST integrate the controlled selection into the **existing** FD001 Add Position
  flow and Create Portfolio journey; it MUST NOT create a separate application or a parallel
  Position-creation path. *(FD002 §4, §13; constitution V)*
- **FR-017**: All FD001 Create Portfolio behaviors MUST continue to pass — single/multiple
  Positions, optional acquisition data, `ticker + market` duplicate rejection, idempotent Save,
  transient-failure ("couldn't save, please try again") experience, atomic persistence. *(FD002
  AC-009; FD001 spec)*
- **FR-018**: Quantity, Initial Purchase Date (optional), and Average Purchase Price (optional)
  MUST remain investor-entered exactly as in FD001 — FD002 does not add reference control to those
  fields. The "average purchase price is expressed in the Position currency" hint MUST reflect the
  **selected** currency. *(FD001; FD002 §12)*

### Contract

- **FR-019**: FD002 MUST use the existing **`GET /api/financial-instruments`** operation for search
  (delivered by EN004, OpenAPI 3.0.3, RFC 9457 errors). FD002 MUST NOT introduce a second search
  endpoint or a standalone markets endpoint. *(EN004 FR-027; FD002 §16)*
- **FR-020** *(resolved — FR-011)*: The `POST /api/portfolios` OpenAPI operation MUST be updated
  **contract-first**: **`INSTRUMENT_NOT_IN_CATALOG`** added to the canonical `ValidationProblem`
  `errors[].code` enum with its meaning documented, an example added, and a contract test covering
  the rejection. The request contract is otherwise unchanged (same fields, same
  `Idempotency-Key`). No provider/source fields appear in any request or response. *(constitution
  VIII; EN004 VC-010)*
- **FR-021**: No REST payload introduced or changed by FD002 may expose provider-specific or
  persistence structures (`providerSymbol`, source exchange code, `operatingMic`, `instrumentType`,
  `source*`, entity shapes). *(FD002 BR-007; EN004 VC-010; constitution VIII)*

### Provider neutrality & data strategy

- **FR-022**: The product's Financial Instrument representation surfaced to the Investor MUST remain
  **provider-neutral** (instrument name, ticker, market/MIC, currency, ISIN, active). FD002 MUST NOT
  introduce a provider-specific integration into the product contract. *(FD002 BR-007, §16, §17.9)*
- **FR-023**: FD002 MUST NOT own or change reference-data acquisition, normalization, or refresh —
  that is EN004's. FD002 depends on whatever catalog EN004 has populated. *(FD002 §15, §16; A7)*

### UX & accessibility

- **FR-024**: The Add Position interaction MUST follow the global design system
  (`product/ux/design-system.md`): dark data-oriented visual language, focused dialog/drawer for the
  action, labels and validation feedback near the control, distinct enabled/disabled/focused/
  invalid/read-only states, compact result density, keyboard-operable search and selection with
  visible focus, and accessible error feedback not conveyed by color alone. *(FD002 §12;
  design-system §"Forms", §"Dialogs and Drawers", §"Accessibility")*
- **FR-025**: The interface MUST provide, as distinct states: idle, searching/loading, results,
  no-results, selected, and validation error. *(FD002 §12)*

### Testing & closure

- **FR-026**: FD002 MUST provide automated tests covering: search by ticker; search by name;
  selection of a known instrument; controlled Market resolution; controlled Currency resolution;
  prevention of an invalid `ticker + market + currency` combination; EUR support; USD support;
  rejection of unsupported currencies; inactive-instrument behavior; no-results behavior;
  integration with FD001 Position creation; the search API contract; frontend→backend search; and
  **regression of the FD001 Create Portfolio journey**. *(FD002 §14)*
- **FR-027**: Integration tests against application-managed persistence MUST use Testcontainers per
  project policy; deterministic committed fixtures only (EN004 provides them). *(FD002 §14;
  constitution VII)*
- **FR-028**: A **mandatory** browser-based E2E critical journey MUST verify the controlled-selection
  flow through the real containerized platform (Create Portfolio → Add Position → search/select
  instrument → controlled ticker/market/currency → Save → persisted). FD002 MUST NOT be accepted,
  closed, or marked Completed while this E2E is missing or failing. *(FD002 §14, §17.11)*

### Scope guardrails

- **FR-029**: FD002 MUST NOT change the meaning of a Position or Portfolio, MUST NOT add valuation /
  prices / FX / news / recommendations, MUST NOT add Position currencies beyond EUR/USD, MUST NOT
  add user-created instruments or markets, crypto, or derivatives beyond the catalog's contents,
  MUST NOT add a new deployable service / messaging / scheduler / search engine / persistence
  technology, and MUST NOT let the frontend depend directly on an external reference-data provider.
  *(FD002 §3 Out of Scope; §17)*
- **FR-030**: FD002 MUST NOT silently edit a human-governed `product/` document. A needed change
  (e.g. promoting "Market"/"Currency" to first-class Information-Model objects, or resolving an
  Open Question) MUST be raised for human approval and applied to the authoritative artifact after
  approval. *(constitution I, IV)*

---

### Key Entities

- **Financial Instrument listing** *(reference data — owned and delivered by EN004; consumed by
  FD002)*: a selectable listing identified by normalized `ticker + market (MIC)`, carrying
  instrument `name`, `currency` (EUR/USD), optional `isin`, `active`. A single economic instrument
  may have several listings. FD002 shows these in search results and uses the selected one to
  populate a Position.
- **Market** *(reference data — owned by EN004)*: a trading venue identified by an ISO 10383 MIC,
  with a human-readable name; `active`. In FD002 the Market value of a Position is the selected
  listing's MIC.
- **Currency** *(constrained value set)*: ISO 4217, restricted to `{EUR, USD}` for FD002. Derived
  from the selected listing.
- **Position** *(existing — FD001)*: unchanged in meaning and identity (`ticker + market`). FD002
  changes only how its ticker/market/currency are chosen and validated; quantity and optional
  acquisition data are unchanged.
- **Portfolio** *(existing — FD001)*: unchanged. Created through the same `POST /api/portfolios`
  flow.

*No new Portfolio/Position entities, no new business event (FD002 §11), and no change to the
Information-Model relationships.*

---

### Traceability to Feature-Definition Acceptance Criteria

| FD002 AC | Description | Covered by |
|---|---|---|
| AC-001 | Select a known instrument → controlled ticker/market/currency | US1 (AS1); FR-001, FR-002, FR-006 |
| AC-002 | Search by instrument name | US1 (AS2); FR-002 |
| AC-003 | Search by ticker | US1 (AS1); FR-002 |
| AC-004 | Controlled Market (no arbitrary text) | US1 (AS4), US2 (AS4); FR-001, FR-007 |
| AC-005 | Controlled Currency (EUR/USD only) | US1 (AS4), US2 (AS4); FR-001, FR-005, FR-007 |
| AC-006 | Invalid combination cannot be created | US2 (AS1, AS2, AS3); FR-010, FR-011 |
| AC-007 | Inactive instrument not selectable | US2 (AS2); FR-005 |
| AC-008 | No search results → informed, text not accepted | US1 (AS5), US3 (AS1); FR-009, FR-013 |
| AC-009 | Existing FD001 flow continues unchanged | US1 (AS4), US4 (AS1); FR-012, FR-016, FR-017 |
| §14 E2E critical journey (closure gate) | mandatory browser E2E | US4 (AS2, AS3); FR-028 |

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In the Add Position interaction, **0** free-text inputs exist for ticker, market, or
  currency — all three are set only by selecting a catalogued listing. Verified by UI test and
  inspection.
- **SC-002**: For a catalogued active `AAPL · XNAS · USD` listing, a case-insensitive ticker search
  and a partial-name search each surface it, and selecting it yields exactly ticker `AAPL`, market
  `XNAS`, currency `USD` on the Position — 100 % of the defined search/select scenarios pass.
- **SC-003**: **0** Positions with a `ticker + market + currency` combination that is not an active
  EUR/USD catalogued listing can be created through the platform — verified by a backend/contract
  test that submits such a Position directly to `POST /api/portfolios` and asserts a
  `INSTRUMENT_NOT_IN_CATALOG` `ValidationProblem` with nothing persisted (guarantee holds without
  the UI).
- **SC-004**: An inactive listing and a non-EUR/USD listing are **never** offered as selectable
  results — verified by integration test against EN004's search.
- **SC-005**: A search that matches nothing produces a clear "no matching instrument" state with
  **no** path to proceed on the typed text — verified by UI test (AC-008).
- **SC-006**: **100 %** of the FD001 Create Portfolio automated scenarios still pass after FD002
  (single/multi Position, duplicate rejection, optional data, idempotent Save, transient-failure) —
  verified by the FD001 suites.
- **SC-007**: The frontend performs **0** direct calls to any external reference-data provider; all
  instrument data reaches the UI via `GET /api/financial-instruments` — verified by inspection and
  network-call assertion in the E2E.
- **SC-008**: **0** provider-specific or persistence fields appear in any FD002 request/response
  payload — verified by contract test and OpenAPI inspection.
- **SC-009**: `./mvnw verify` (backend, incl. any new contract test) and the frontend unit suite are
  green; the ≥ 90 % line-and-branch coverage gate holds; ArchUnit stays green.
- **SC-010**: The mandatory FD002 Playwright critical journey passes against the containerized
  platform (`./e2e.sh`, exit 0), driving Create Portfolio → Add Position → search + select
  instrument → controlled ticker/market/currency → Save → persisted. FD002 is not closable without
  it.
- **SC-011**: The change set contains **no** modification to Portfolio/Position business meaning,
  **no** change to the FD001 `ticker + market` identity, **no** new deployable/messaging/scheduler/
  search-engine/persistence technology, and **no** unapproved edit to a human-governed `product/`
  document — verified by `git diff` review.
- **SC-012**: An Investor can add a Position by finding an instrument and selecting it in **under 30
  seconds** for a known ticker, without needing to know the market or currency code — verified by a
  task-completion walkthrough.

---

## Assumptions

- **A1 — Feature Definition approved 2026-09-03**: FD002 is `Status: Approved` (§19 signed by
  jaruiz), all §18 Open Questions resolved, and the two spec Clarifications settled. Prerequisite
  gates for `/speckit-plan` are cleared. The only `product/` edit is the human-directed §18/§19
  approval sync.
- **A2 — EN004 is the catalog**: the Market + Financial Instrument catalog, its ingestion, and the
  `GET /api/financial-instruments` search operation already exist (EN004, Approved 2026-09-03) and
  are populated on backend start from committed deterministic fixtures. FD002 consumes them.
- **A3 — Same platform, same deployable**: FD002 extends `implementation/platform/` (frontend +
  backend + contracts) within the single `core-service` (ADR-001) and the ADR-003 architecture.
- **A4 — Search semantics come from EN004**: active + EUR/USD filter, exact-ticker-first ordering,
  blank → `400`, no-match → `200 []`, case-insensitive — FD002 does not redefine them.
- **A5 — Multiple listings** *(FD002 OQ2)*: each supported listing is its own selectable result,
  distinguished by market/MIC and currency (matches EN004's per-listing search rows).
- **A6 — Instrument types** *(FD002 OQ3)*: equities + ETFs; `instrumentType` is descriptive metadata
  and never a search filter (settled in EN004).
- **A7 — Market universe** *(FD002 OQ4)*: whatever EN004's committed catalog contains; FD002
  mandates no geographic coverage. The FD002 E2E uses EN004's deterministic fixtures.
- **A8 — ISIN** *(FD002 OQ5)*: shown in results when the catalog has it; not required, not entered
  by the Investor.
- **A9 — Legacy Positions** *(FD002 OQ6)*: not applicable — no capability lists or edits an existing
  Position, so none is re-validated against the catalog. FD002 governs new Position creation only.
- **A10 — Idempotency-Key** and the rest of the `POST /api/portfolios` request contract are reused
  from FD001 unchanged; only the `ValidationProblem` response gains the `INSTRUMENT_NOT_IN_CATALOG`
  code (FR-020).
- **A11 — OpenAPI dialect**: any contract change stays **OpenAPI 3.0.3** to match the existing
  `swagger-request-validator` contract tests.
- **A12 — No authentication yet**: Positions are created for the single seeded default Investor, as
  in FD001 (ADR-002). FD002 adds no identity behavior.
- **A13 — Result volume**: the committed catalog is small-to-moderate; result capping / pagination,
  debounce interval, and minimum-query-length are UX/planning details, not product rules — to be
  fixed in planning within the design-system guidance.

## Dependencies

- **EN004 — Establish Financial Instrument Reference Data** *(Approved)*: provides the catalog,
  ingestion, `GET /api/financial-instruments`, and the deterministic offline fixtures the FD002 E2E
  needs.
- **FD001 — Create Investment Portfolio** *(Approved)*: provides the Create Portfolio journey, the
  Add Position dialog, the `POST /api/portfolios` contract, and the `ticker + market` Position
  identity FD002 preserves.
- **EN001 / EN002 / EN003**: the executable platform, the containerized browser-E2E foundation
  (`e2e.sh`), and the ADR-003 backend architecture.
- **Governance**: `product/definition/global/` (domains, glossary, information model — ISO 10383 /
  ISO 6166 / ISO 4217), `product/ux/design-system.md`,
  `product/architecture/{architecture,architecture-rules,technology-policy}.md`,
  `product/engineering/{development-rules,testing-strategy,definition-of-done}.md`,
  `.specify/memory/constitution.md`.
- **Human approval** of the FD002 Feature Definition — **granted 2026-09-03** (§19 signed by jaruiz;
  `Status: Approved`).

## Out of Scope

Carried from FD002 §3 "Out of Scope" plus derived boundaries:

- Live market prices, portfolio valuation, automatic FX conversion, market-data or news enrichment,
  automatic recommendations.
- Position currencies other than EUR and USD.
- User-created Financial Instruments or Markets; crypto assets; derivatives beyond what EN004's
  catalog already contains; guaranteed complete global instrument coverage; real-time exchange
  synchronization.
- Reference-data **acquisition, normalization, and refresh** mechanics (owned by EN004).
- Any direct dependency of the user-facing form on an external reference-data provider.
- Listing, viewing, or editing a Portfolio or its Positions after creation; retroactive validation
  of Positions created before FD002.
- A new deployable service, messaging, a scheduler, a dedicated search engine, a new persistence
  technology, or a Java/Angular major-version change.
- Editing or reinterpreting human-governed architecture / product documents.
