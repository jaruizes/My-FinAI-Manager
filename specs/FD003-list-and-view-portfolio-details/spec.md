# Feature Specification: List and View Portfolio Details (FD003)

**Feature Branch**: `FD003-list-and-view-portfolio-details`

**Created**: 2026-09-03

**Status**: Draft

**Input**: Feature Definition: "Allow an Investor to see their saved Portfolios directly from the
application Home page and access the detail of a selected Portfolio. The Home page displays existing
Portfolios in a table; selecting a row navigates to a Portfolio detail view showing that Portfolio's
persisted Positions. If no Portfolios exist, an explicit empty-state message is shown. FD003 is
entirely read-only."

**Authoritative Source**:
`product/definition/features/FD003-list-and-view-portfolio-details/FD003-list-and-view-portfolio-details.md`
(**Status: Approved** — §19 all boxes checked, signed by jaruiz 2026-09-03).

**Governing decisions**: ADR-001 (single `core-service` deployable — unchanged); ADR-003 (Standard
Spring Backend Architecture); ADR-002 (interim unauthenticated write — the "current Investor" is the
single seeded Default Investor); the FD001 Create Portfolio capability and its persisted
`portfolio` / `position` data; EN002 containerized browser-E2E; the global design system
`product/ux/design-system.md`. **No new ADR is anticipated** — FD003 adds two read endpoints and
two frontend views inside the existing `portfolio` module and topology.

> **Governance note.** The Feature Definition is **Approved** (§19 signed 2026-09-03) and §18
> records "no known product-blocking questions remain". §17 (Explicit Product Decisions 1–15) and
> §16 (mandatory E2E-001 + E2E-002) are the authoritative product intent. This spec derives from
> them and adds no product behavior. It does not modify any `product/` document.

---

## Overview *(mandatory)*

FD003 makes the Investor's **already-persisted** Portfolios visible and inspectable — the natural
follow-up to FD001 (which explicitly deferred "listing Portfolios" and "viewing a Portfolio after
creation") and FD002 (controlled Position selection).

- The **Home page** loads and shows the Investor's saved Portfolios in a table — one row per
  Portfolio, showing at least the **Portfolio name** and its **number of Positions**. When the
  Investor has none, an explicit **empty-state message** is shown instead of an empty table.
- Selecting a Portfolio row navigates to a **Portfolio detail view** that shows the Portfolio name
  and a table of that Portfolio's **persisted Positions** (ticker, market, quantity, currency, and
  the optional initial purchase date / average purchase price when the persisted Position has them).
- FD003 is **entirely read-only**: it reads `Portfolio` and `Position` data, modifies nothing, and
  adds no business event.

**Explicitly out of scope** (FD003 §3 "Out of Scope" + §12 + §18):
editing / deleting a Portfolio; adding / removing Positions; portfolio valuation, performance, risk,
recommendations, stop-loss, current market price, profit/loss; sorting, filtering, searching,
pagination; row actions or any Portfolio/Position action from the list or the detail; currency
totals; last-review information; any list column beyond name + Position count; any additional
navigation beyond opening the detail. Authentication / multi-investor identity remains out of scope
(carried from ADR-002).

---

## User Scenarios & Testing *(mandatory)*

The actor is the **Investor** (glossary: the person who owns and manages one or more portfolios).
Because there is no authentication yet, "the Investor" is the single seeded **Default Investor**
(ADR-002) — FD003 still scopes every query to that Investor so multi-investor support is a drop-in
later.

Each story is an independently demonstrable slice of the existing platform. **US1 alone is a viable
MVP** (an Investor can see that their Portfolios exist and how many Positions each has).

### User Story 1 - See my saved Portfolios on the Home page (Priority: P1)

An Investor opens My-FinAI-Manager and the Home page shows their saved Portfolios in a table — one
row per Portfolio, each row showing the Portfolio name and the number of Positions it contains.

**Why this priority**: This is the feature's core purpose (FD003 §1–§2, §17.1–§17.4). Without it the
Investor has no way to see what Portfolios exist after creating them.

**Independent Test**: With the platform running and (via the FD001 API) two Portfolios persisted —
one with 1 Position, one with 3 — open the Home page and confirm a table with exactly those two
Portfolios, each showing its correct name and Position count.

**Acceptance Scenarios**:

1. **Given** the Investor has one saved Portfolio, **When** they open the Home page, **Then** the
   Portfolios table shows that Portfolio. *(FD003 AC-001)*
2. **Given** the Investor has several saved Portfolios, **When** they open the Home page, **Then**
   the table shows exactly one row per saved Portfolio. *(FD003 AC-002, BR-004)*
3. **Given** a saved Portfolio contains Positions, **When** it is shown in the table, **Then** its
   row displays the Portfolio name and the number of Positions currently persisted for it. *(FD003
   AC-003, BR-005)*
4. **Given** the Investor created a Portfolio through FD001, **When** they subsequently open the Home
   page, **Then** that newly persisted Portfolio appears in the list. *(FD003 AC-005)*
5. **Given** a Portfolio-creation draft that was never saved, **When** the Home page loads, **Then**
   it does **not** appear in the list — only successfully persisted Portfolios are shown. *(FD003
   BR-002)*
6. **Given** the Home page is loading the list, **When** results have not yet returned, **Then** a
   loading state is shown; if the load fails, a recoverable error state is shown (no technical
   detail) with a way to retry. *(design system §"Loading States" / §"Error States")*

---

### User Story 2 - Clear empty state when I have no Portfolios (Priority: P1)

An Investor with no saved Portfolios opens the Home page and sees an explicit "no portfolios"
message — not a blank or empty table.

**Why this priority**: FD003 makes this a distinct business rule (BR-003), acceptance criterion
(AC-004), and a **mandatory** E2E gate (E2E-002). An unexplained empty table is called out as
insufficient.

**Independent Test**: With no Portfolios persisted for the Investor, open the Home page and confirm
the empty-state message is shown and **no** Portfolio row (and no misleading placeholder row) is
displayed.

**Acceptance Scenarios**:

1. **Given** the Investor has no saved Portfolios, **When** they open the Home page, **Then** the
   application shows a message indicating that no Portfolios exist. *(FD003 AC-004, BR-003)*
2. **Given** the empty state is shown, **When** the Investor looks at the Portfolios area, **Then**
   there is no Portfolio row and no misleading empty/placeholder row. *(FD003 AC-004)*
3. **Given** the empty state is shown, **When** the Investor then creates a Portfolio (FD001) and
   returns to the Home page, **Then** the list now shows that Portfolio and the empty state is gone.
   *(FD003 AC-005)*

---

### User Story 3 - Open a Portfolio and see its Positions (Priority: P1)

From the Home list, an Investor selects a Portfolio row and is taken to that Portfolio's detail
view, which shows the Portfolio name and a table of its persisted Positions.

**Why this priority**: This is the second half of the feature's purpose (FD003 §1, §6, §17.6–§17.9)
and a **mandatory** E2E gate (E2E-001 detail assertions).

**Independent Test**: With three Portfolios persisted (Positions 1 / 2 / 3), open the Home page,
select the 3-Position Portfolio, and confirm the detail view shows that Portfolio's name and exactly
its three Positions with their persisted values — and none of the other two Portfolios' Positions.

**Acceptance Scenarios**:

1. **Given** the Investor has several saved Portfolios and selects one from the Home table, **When**
   the selection is made, **Then** the application navigates to that Portfolio's detail view. *(FD003
   AC-006, BR-006)*
2. **Given** the detail view has loaded, **When** it renders, **Then** it displays the selected
   Portfolio's name and one table row per persisted Position of that Portfolio. *(FD003 AC-006,
   AC-007, BR-007, BR-008)*
3. **Given** a persisted Position, **When** its row is shown, **Then** it displays the ticker,
   market (MIC), quantity, and currency, plus the initial purchase date and average purchase price
   **when the persisted Position has them**. *(FD003 §6, §17.9)*
4. **Given** a Portfolio's detail is open, **When** the Position table is inspected, **Then** it
   contains **only** Positions belonging to that Portfolio — Positions of any other Portfolio are
   not shown. *(FD003 AC-007)*
5. **Given** the Investor is on a Portfolio detail view, **When** they look for edit / add-Position
   / remove-Position controls, **Then** there are none — the detail is read-only. *(FD003 BR-009,
   §17.10)*
6. **Given** a Portfolio id that does not exist (or is not the Investor's), **When** its detail is
   requested, **Then** the application shows a clear "not found" state — no other Portfolio's data
   is shown and nothing is created. *(FD003 BR-001, BR-007; RFC 9457)*
7. **Given** the detail view is loading, **When** data has not yet returned, **Then** a loading
   state is shown; a failed load shows a recoverable error state. *(design system)*

---

### User Story 4 - Both journeys proven end to end; FD001/FD002 unaffected (Priority: P2)

The list-and-empty-state journey and the list→detail journey each have a mandatory Playwright E2E
against the real containerized platform, and FD001 Create Portfolio + FD002 instrument selection
still work.

**Why this priority**: FD003 §16 makes **E2E-001 and E2E-002 mandatory closure gates**. P2 because
it verifies US1–US3 rather than adding new user value.

**Independent Test**: `./e2e.sh` runs E2E-001 (create three Portfolios via the FD001 API with 1 / 2
/ 3 Positions → Home shows all three with exact counts → open one → detail shows exactly its
Positions) and E2E-002 (no Portfolios → empty-state message, no rows), plus the existing FD001 /
FD002 specs — all green.

**Acceptance Scenarios**:

1. **Given** the containerized platform, **When** **E2E-001** runs, **Then** it creates exactly
   three Portfolios (via the FD001 creation API, not direct DB inserts) with different Position
   counts, opens the Home page, asserts each Portfolio's **identity and exact Position count** (not
   merely "three rows"), selects one, and asserts the detail shows that Portfolio's name and
   **exactly** its persisted Positions (correct count, correct values, none from the other two). It
   passes with exit 0. *(FD003 §16 E2E-001, §17.12–§17.13)*
2. **Given** the containerized platform with no Portfolios for the Investor, **When** **E2E-002**
   runs, **Then** the Home page shows the empty-state message and no Portfolio row, exit 0. *(FD003
   §16 E2E-002, §17.14)*
3. **Given** E2E-001 or E2E-002 is missing or failing, **When** closure is considered, **Then**
   FD003 **cannot** be accepted, closed, or marked Completed. *(FD003 §16, §17.15)*
4. **Given** FD003 is delivered, **When** the FD001 Create Portfolio and FD002 instrument-selection
   journeys are run, **Then** they behave exactly as before (creation, validation, idempotency,
   catalog check, the FD002 E2E). *(FD003 §15 "regression of Portfolio creation")*

---

### Edge Cases

- **No Portfolios** — empty-state message, no rows (US2).
- **A Portfolio with zero Positions** — the persisted schema requires ≥ 1 Position at creation
  (FD001), so a zero-Position Portfolio should not occur; if one is encountered, the list row shows
  count `0` and the detail shows the name with an empty Positions table (with its own "no
  positions" note), never a crash.
- **Unknown / other-Investor Portfolio id in the detail URL** — "not found" state; no data leak
  (US3 AS6).
- **Large number of Portfolios or Positions** — no pagination in FD003 (§3); the tables render all
  rows. A performance target is set in Success Criteria; a pagination decision is explicitly a
  future, separately-approved change.
- **Position with an average purchase price but no date (or vice versa)** — each optional field is
  shown independently only when present (FD001 persists them independently).
- **Decimal precision** — quantity and average purchase price are displayed exactly as persisted
  (no rounding / reformatting that loses precision), consistent with FD001.
- **Navigating directly to a detail URL (deep link / refresh)** — the detail view loads the
  Portfolio by id from the API; it does not depend on having come from the Home list.
- **List / detail API temporarily unavailable** — recoverable error state with retry; nothing is
  created or changed.

---

## Requirements *(mandatory)*

### Portfolio list

- **FR-001**: The Home page MUST, on load, retrieve and display the current Investor's **persisted**
  Portfolios as a table with **one row per Portfolio**. *(FD003 §1, §3, §4, BR-002, BR-004; AC-001,
  AC-002)*
- **FR-002**: Each Portfolio row MUST display at least the **Portfolio name** and the **number of
  Positions** currently persisted for that Portfolio. *(FD003 §5, BR-005; AC-003)*
- **FR-003**: The list MUST include only Portfolios belonging to the current Investor and MUST NOT
  include unsaved creation drafts. *(FD003 BR-001, BR-002)*
- **FR-004**: When the Investor has no persisted Portfolios, the Home page MUST show an explicit
  empty-state message and MUST NOT show a Portfolio row or a misleading empty/placeholder row.
  *(FD003 §3, BR-003; AC-004)*
- **FR-005**: A Portfolio created through FD001 MUST appear in the list the next time the Home page
  is loaded. *(FD003 AC-005)*
- **FR-006**: The list MUST NOT provide sorting, filtering, searching, pagination, or any
  row/Portfolio action other than opening the detail. The row order is a fixed, deterministic
  server-side order (see A3). *(FD003 §3, §12, §17.11, §18)*
- **FR-007**: The Home page MUST show a loading state while the list is being retrieved and a
  recoverable, non-technical error state (with retry) if retrieval fails. *(design system
  §"Loading States", §"Error States")*

### Portfolio detail

- **FR-008**: Selecting a Portfolio row MUST navigate to that Portfolio's detail view. *(FD003 §4,
  BR-006; AC-006)*
- **FR-009**: The detail view MUST display the selected Portfolio's **name** and a table with **one
  row per persisted Position** of that Portfolio. *(FD003 §6, BR-007, BR-008; AC-006, AC-007)*
- **FR-010**: Each Position row MUST display the **ticker**, **market (MIC)**, **quantity**, and
  **currency**, and MUST display the **initial purchase date** and **average purchase price**
  **when the persisted Position has them** (and omit them cleanly otherwise). Numeric values MUST be
  shown exactly as persisted (no precision loss). *(FD003 §6, §17.9)*
- **FR-011**: The detail view MUST show **only** the selected Portfolio's Positions — no Position of
  any other Portfolio. *(FD003 AC-007)*
- **FR-012**: The detail view MUST be **read-only** — no control to edit the Portfolio, add a
  Position, remove a Position, or change any value. *(FD003 BR-009, §17.10)*
- **FR-013**: Requesting the detail of a Portfolio id that does not exist for the current Investor
  MUST result in a clear "not found" state — no other Portfolio's data is shown, nothing is
  created. *(FD003 BR-001, BR-007)*
- **FR-014**: The detail view MUST load its data by Portfolio id from the API (deep-link / refresh
  safe) and MUST show a loading state and a recoverable error state as in FR-007. *(design system)*

### Read-only & data reuse

- **FR-015**: FD003 MUST NOT modify Portfolio or Position state, MUST NOT create or persist
  anything, and MUST NOT introduce a business event. It reads the `Portfolio` / `Position` data
  already persisted by FD001. *(FD003 §10, §11, BR-009)*
- **FR-016**: FD003 MUST NOT change the FD001 Create Portfolio behavior (creation, validation,
  idempotency, atomic persistence) or the FD002 instrument-selection behavior (the constrained
  Add Position dialog, the `INSTRUMENT_NOT_IN_CATALOG` check). *(FD003 §15; FD001; FD002)*
- **FR-017**: FD003 MUST NOT change the FD001 `ticker + market` Position identity or any Portfolio /
  Position business rule. *(FD003 §10)*

### API & contract

- **FR-018**: FD003 MUST expose, **contract-first** (OpenAPI under
  `implementation/platform/contracts/openapi/`, OpenAPI 3.0.3, RFC 9457 errors), read operations
  for: (a) the current Investor's Portfolio list, and (b) a selected Portfolio's detail with its
  Positions. Conceptually `GET /api/portfolios` and `GET /api/portfolios/{portfolioId}`; exact paths
  follow the project REST conventions. *(FD003 §14; ADR-011, ADR-012; constitution VIII)*
- **FR-019**: The **list** response MUST carry only what FD003 needs — per Portfolio: an identifier
  (for navigation), the name, and the Position count. It MUST NOT embed the full Position list.
  *(FD003 §14 "only the information required by FD003")* → **Resolved in planning:** a
  `PortfolioSummary` representation `{ id, name, positionCount }` (research Dx).
- **FR-020**: The **detail** response MUST return the selected Portfolio and its Positions. The
  existing `Portfolio` schema (`id`, `name`, `status`, `positions[]`, `createdAt`) and `Position`
  schema already provide an equivalent representation and SHOULD be reused for the detail operation
  rather than defining a new one. *(FD003 §14; FD001 contract)*
- **FR-021**: No response introduced or changed by FD003 may expose provider-specific or
  persistence structures (entity shapes, JPA/column names, internal ids beyond the public Portfolio
  / Position ids). *(FD003 §14; constitution VIII; FD002 provider-neutrality)*
- **FR-022**: An unknown Portfolio id (well-formed but not the current Investor's) MUST return
  `404 application/problem+json` with a stable machine-readable `type`. A **malformed** Portfolio id
  (a path segment that is not a valid identifier) MUST be rejected with `400` (a client error — the
  request is not a valid resource reference); the `400` body is not required to carry a specific
  problem `type`. The list of an Investor with no Portfolios MUST return `200` with an empty
  collection (not `404`). *(FD003 BR-003, BR-007; RFC 9457)*

### Persistence & architecture

- **FR-023**: FD003 MUST reuse the existing PostgreSQL `portfolio` / `position` data and the
  `portfolio` module (ADR-003 `domain` / `business` / `infrastructure`). It MUST add **read-only**
  query capability (e.g. new `domain.ports` read methods + Spring Data queries) and MUST NOT add a
  schema migration. Hibernate MUST NOT own the schema (`ddl-auto: none`). *(FD003 §13; ADR-003;
  constitution VI, VII)*
- **FR-024**: The list query MUST compute the Position count from the persisted Positions of each
  Portfolio (not a stored/denormalised counter unless planning introduces one deliberately). *(FD003
  BR-005)*
- **FR-025**: Backend business/domain code MUST remain free of framework types in `domain`, and the
  read path MUST NOT contact an external provider. *(ADR-003; constitution VI)*

### UX & accessibility

- **FR-026**: The Home Portfolios table, the empty state, and the Portfolio detail (name + Positions
  table) MUST follow the global design system (`product/ux/design-system.md`): dark data-oriented
  visual language, compact table density, clear empty state ("what's missing / why / what to do
  next"), semantic table markup, keyboard-operable row selection with visible focus, and error text
  not conveyed by color alone. *(FD003 §12; design system §"Forms"/"Empty States"/"Loading
  States"/"Error States"/"Accessibility")*
- **FR-027**: A Portfolio row MUST be operable by both pointer and keyboard to open its detail
  (e.g. an accessible link/button per row), with a visible focus indicator. *(FD003 §12
  "selectable"; design system §"Accessibility")*
- **FR-028**: The application's primary navigation MUST make the Portfolio list reachable (the Home
  page is where Portfolios live — FD003 §17.1); the navigation MUST NOT expose capabilities that do
  not exist. *(design system §"Sidebar"; FD003 §1)*

### Testing & closure

- **FR-029**: FD003 MUST provide automated tests covering: zero / one / multiple Portfolios;
  correct name and Position count; only-persisted Portfolios returned; selected-Portfolio detail;
  correct Position rows; exclusion of another Portfolio's Positions; the list & detail API
  contracts; PostgreSQL integration (Testcontainers per policy); Home table rendering; Home empty
  state; navigation from list to detail; detail table rendering; and **regression of Portfolio
  creation** (FD001) and instrument selection (FD002). *(FD003 §15)*
- **FR-030**: FD003 MUST provide **two mandatory** Playwright E2E scenarios against the real
  containerized platform (frontend ↔ backend ↔ PostgreSQL not mocked): **E2E-001** (create three
  Portfolios via the FD001 creation API with 1 / 2 / 3 Positions → Home shows all three with exact
  identities and counts → open one → detail shows exactly that Portfolio's persisted Positions and
  none from the others) and **E2E-002** (no Portfolios → empty-state message, no rows). FD003 MUST
  NOT be accepted, closed, or marked Completed while either is missing or failing. *(FD003 §16,
  §17.12–§17.15)*
- **FR-031**: E2E setup MUST create Portfolio state through the existing Portfolio creation API, not
  by inserting `portfolio` / `position` rows directly into PostgreSQL as the primary mechanism.
  *(FD003 §16 "Test Preparation")*

### Scope guardrails

- **FR-032**: FD003 MUST NOT add editing / deletion / Position mutation, valuation / performance /
  risk / recommendations / stop-loss / market price / P&L, sorting / filtering / searching /
  pagination, row or Portfolio actions beyond opening the detail, extra list columns, currency
  totals, or any new deployable / messaging / scheduler / persistence technology. Any such addition
  MUST be a separately approved Feature Definition. *(FD003 §3, §12, §17.10–§17.11, §18)*
- **FR-033**: FD003 MUST NOT silently edit a human-governed `product/` document. A needed change
  (e.g. promoting a list representation to the Information Model) MUST be raised for human approval
  and applied to the authoritative artifact after approval. *(constitution I, IV)*

---

### Key Entities

- **Portfolio** *(existing — FD001; read-only in FD003)*: `id`, `name`, `status` (`ACTIVE`),
  `positions[]`, `createdAt`. Owned by one Investor. FD003 reads it for the list (name +
  Position count) and the detail (name + Positions).
- **Position** *(existing — FD001; read-only in FD003)*: `id`, `ticker`, `market` (MIC),
  `quantity`, `currency`, optional `initialPurchaseDate`, optional `averagePurchasePrice`. Belongs
  to exactly one Portfolio. Identity within a Portfolio: `ticker + market` (unchanged).
- **Portfolio summary** *(new — API representation only, not a new domain concept)*: the lean list
  item `{ id, name, positionCount }` returned by the list operation (FR-019). Carries no Position
  detail.
- **Investor** *(existing)*: the single seeded Default Investor (ADR-002). Every FD003 query is
  scoped to it.

*No new persisted entity, no schema change, no new business event (FD003 §10, §11).*

---

### Traceability to Feature-Definition Acceptance Criteria

| FD003 AC | Description | Covered by |
|---|---|---|
| AC-001 | List one Portfolio | US1 (AS1); FR-001 |
| AC-002 | List multiple Portfolios (one row each) | US1 (AS2); FR-001, FR-006 |
| AC-003 | Row shows name + Position count | US1 (AS3); FR-002, FR-024 |
| AC-004 | No Portfolios → empty-state message, no misleading row | US2 (AS1, AS2); FR-004 |
| AC-005 | Newly created Portfolio is listed | US1 (AS4), US2 (AS3); FR-005 |
| AC-006 | Open Portfolio detail (name + Positions) | US3 (AS1, AS2); FR-008, FR-009 |
| AC-007 | Correct Position detail; other Portfolios' Positions excluded | US3 (AS2, AS4); FR-009, FR-010, FR-011 |
| §16 E2E-001 (mandatory) | list 3 with exact counts + detail | US4 (AS1); FR-030, FR-031 |
| §16 E2E-002 (mandatory) | empty state | US4 (AS2); FR-030 |
| §15 regression of Portfolio creation | FD001/FD002 unaffected | US4 (AS4); FR-016 |

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With exactly N persisted Portfolios (N = 1, 2, 3), the Home page shows exactly N
  rows, each with the correct Portfolio name and the exact count of that Portfolio's persisted
  Positions — verified for each N by integration and E2E tests. **100 %** identity/count accuracy.
- **SC-002**: With 0 persisted Portfolios, the Home page shows the empty-state message and **0**
  Portfolio rows — verified by test.
- **SC-003**: Opening a Portfolio's detail shows exactly that Portfolio's persisted Positions —
  **0** Positions from any other Portfolio appear, and the displayed count equals the persisted
  count — verified by integration and E2E tests.
- **SC-004**: The displayed Position values (ticker, market, quantity, currency, and the optional
  date / average price when present) match the persisted values **exactly**, with no precision loss
  — verified by test against known created data.
- **SC-005**: FD003 performs **0** writes — no `INSERT` / `UPDATE` / `DELETE` on `portfolio` or
  `position`, no new row, no business event — verified by test and code review.
- **SC-006**: A request for a non-existent Portfolio id returns a `404` problem response and leaks
  **0** data about other Portfolios — verified by contract/integration test.
- **SC-007**: **100 %** of the FD001 Create Portfolio and FD002 instrument-selection automated
  scenarios still pass after FD003 — verified by the FD001/FD002 suites and E2E.
- **SC-008**: **0** provider-specific or persistence-shaped fields appear in any FD003 response —
  verified by contract test and OpenAPI inspection.
- **SC-009**: `./mvnw verify` (backend, incl. the new contract tests) and the frontend unit suite
  are green; the ≥ 90 % line-and-branch coverage gate holds; ArchUnit stays green.
- **SC-010**: Both mandatory Playwright scenarios (**E2E-001**, **E2E-002**) pass against the
  containerized platform (`./e2e.sh`, exit 0). FD003 is not closable without them.
- **SC-011**: The change set contains **no** schema migration, **no** write path, **no** new
  deployable / persistence technology / messaging / scheduler, **no** sorting/filtering/pagination,
  and **no** unapproved edit to a human-governed `product/` document — verified by `git diff`
  review.
- **SC-012**: On the containerized platform with a realistic small dataset (≤ ~20 Portfolios, ≤ ~50
  Positions each), the Home list and a Portfolio detail each render within **2 seconds** of
  navigation under local conditions — verified by an E2E/manual walkthrough.

---

## Assumptions

- **A1 — Feature Definition approved 2026-09-03**: FD003 is `Status: Approved` (§19 signed by
  jaruiz), §18 records no product-blocking questions. This spec adds no product behavior and edits
  no `product/` document.
- **A2 — Single Investor (ADR-002)**: "the current Investor" is the seeded Default Investor. FD003
  scopes every query to it so multi-investor auth drops in later without a contract change.
- **A3 — List order** *(technical default)*: the list is returned in a **fixed, deterministic
  server-side order** — most-recently-created first (by `createdAt` descending, id as tiebreak).
  User-controlled sorting is out of scope (FD003 §3); a fixed order is required only so the table
  and E2E assertions are deterministic. Planning may choose ascending instead; either is acceptable
  as long as it is deterministic and documented.
- **A4 — List representation** *(→ FR-019)*: a new lean `PortfolioSummary` `{ id, name,
  positionCount }`. The full Position list is **not** embedded in the list response.
- **A5 — Detail representation** *(→ FR-020)*: reuse the existing `Portfolio` + `Position` OpenAPI
  schemas for `GET /api/portfolios/{portfolioId}`; no new detail schema.
- **A6 — Position count** *(→ FR-024)*: computed from persisted Positions (e.g. a `COUNT` query or
  the size of the loaded aggregate), not a denormalised counter — unless planning adds one
  deliberately for performance.
- **A7 — Not-found semantics**: a well-formed but unknown Portfolio id → `404`
  `application/problem+json` with a stable `type` (e.g. `/problems/portfolio-not-found`); a malformed
  id (not a valid identifier) → `400` (typing the path variable as `UUID` yields the framework's
  default `400`, no custom handler). Empty list → `200 []`.
- **A8 — Routes**: the Portfolio list lives on the Home route; the detail on a per-Portfolio route
  (e.g. `/portfolios/{id}`). Exact paths are a planning detail; the FD001 create route
  (`/portfolios/new`) is unchanged.
- **A9 — OpenAPI dialect**: new operations are authored in **OpenAPI 3.0.3** to match the existing
  `swagger-request-validator` contract tests.
- **A10 — Zero-Position Portfolio**: FD001 requires ≥ 1 Position at creation, so this should not
  occur; if it does, the row shows count `0` and the detail shows an empty Positions table with its
  own note — never an error.
- **A11 — No pagination**: all rows render (FD003 §3). SC-012 sets a performance expectation for a
  realistic small dataset; a pagination/virtualisation decision is a future separately-approved
  change.
- **A12 — Sidebar navigation**: the primary nav should point the Investor at the Home page (where
  Portfolios live); the current deep-link-only entry to `/portfolios/new` is adjusted so the list
  is reachable. This is a small UX wiring detail, not new product behavior.

## Dependencies

- **FD001 — Create Investment Portfolio** *(Approved, implemented)*: persists the `Portfolio` /
  `Position` data FD003 reads; provides the creation API E2E-001 uses for setup; defines the
  `Portfolio` / `Position` OpenAPI schemas FD003 reuses for the detail.
- **FD002 — Select Financial Instrument from Catalog** *(Approved, implemented)*: FD003 must not
  regress the constrained Add Position dialog or the `INSTRUMENT_NOT_IN_CATALOG` backend check.
- **EN001 / EN002 / EN003**: the executable platform, the containerized browser-E2E foundation
  (`e2e.sh`, deterministic synthetic data, disposable DB per run), and the ADR-003 backend
  architecture.
- **ADR-001 / ADR-002 / ADR-003**: unchanged; FD003 implements within them.
- **Governance**: `product/definition/global/` (domains, glossary, information model),
  `product/ux/design-system.md`,
  `product/architecture/{architecture,architecture-rules,technology-policy}.md`,
  `product/engineering/{development-rules,testing-strategy,definition-of-done}.md`,
  `.specify/memory/constitution.md`.

## Out of Scope

Carried from FD003 §3 "Out of Scope", §12, §17.10–§17.11, §18:

- Editing or deleting a Portfolio; adding or removing Positions; any Portfolio/Position mutation.
- Portfolio valuation, performance, profit/loss, current market price, risk analysis,
  recommendations, stop-loss, last-review information, currency totals.
- Sorting, filtering, searching, pagination; row actions or any action from the list/detail beyond
  opening the detail; any list column beyond Portfolio name + Position count.
- Navigation behavior beyond opening the Portfolio detail.
- Authentication / multi-investor identity (carried from ADR-002).
- A new deployable service, messaging, a scheduler, a new persistence technology, or a Java/Angular
  major-version change; any schema migration.
- Editing or reinterpreting human-governed architecture / product documents.
