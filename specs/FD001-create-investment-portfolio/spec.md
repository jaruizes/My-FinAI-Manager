# Feature Specification: Create Investment Portfolio (FD001)

**Feature Branch**: `FD001-create-investment-portfolio`

**Created**: 2026-09-01

**Status**: Draft

**Input**: Feature Definition: "Allow an Investor to create a new investment Portfolio and define its initial Positions. This feature only covers creation and persistence of the Portfolio."

**Authoritative Source**: `product/definition/features/FD001_Create_portfolio/FD001_Create_portfolio.md` (Status: Approved — jaruiz, 2026-09-01)

**Governing decisions**: `product/architecture/adrs/ADR-001-initial-backend-topology.md`; the executable platform established by `EN001 — Bootstrap Executable Platform`; the containerized browser-E2E foundation established by `EN002 — Establish Containerized End-to-End Testing Foundation`.

> **Revision 2026-09-01 (E2E gate)**: the authoritative Feature Definition §13 was updated to make
> an automated End-to-End test of the critical Create Portfolio journey (**E2E-001**) **mandatory**,
> now that `EN002` provides the containerized Playwright foundation. This spec is updated to carry
> that requirement: see US1 acceptance scenario 4, **FR-036**, **SC-013**, and **A13**. E2E-001
> passing against the containerized platform is a **closure gate** — FD001 cannot be accepted,
> closed, or marked Completed while E2E-001 is missing or failing (FD001 §13, §16).

---

## Overview *(mandatory)*

FD001 lets an Investor create one new investment **Portfolio**, give it a name, add one or more
initial **Positions**, have the Portfolio validated, and have it persisted so later capabilities
can use it. It is the first vertical product feature and extends the platform delivered by EN001
(frontend, `core-service` backend, PostgreSQL, contracts) — it does not create a new application.

**Explicitly out of scope** (deferred to later Feature Definitions — FD001 §3):
listing Portfolios, viewing a Portfolio after creation, editing an existing Portfolio, adding /
reducing / removing Positions after persistence, transaction or purchase-lot history, valuation,
market-price enrichment, risk, recommendations, stop-loss, automatic currency inference, and
recalculating an average purchase price after future purchases.

---

## Clarifications

### Session 2026-09-01

- Q: How should FD001 determine the Investor who owns a newly created Portfolio, given that EN001 introduced no authentication? → A: A single default Investor is seeded by the platform; every Portfolio FD001 creates is owned by it. Real identity + authentication arrive in a later Feature Definition / Technical Enabler.
- Q: When the Portfolio passes validation but the Save itself fails (transient persistence error), what should the Investor experience and what state should result? → A: Show a non-technical "couldn't save, please try again" message; keep the entered Portfolio + Positions so the Investor can retry; guarantee nothing partial is persisted (no Portfolio, no orphan Positions). No automatic retry in FD001.
- Q: If the Investor accidentally triggers Save twice for the same in-progress Portfolio, should the system create two Portfolios or one? → A: Prevent the accidental duplicate — Save is disabled while a submission is in flight, and a repeated identical submission of the same draft returns/keeps the already-created Portfolio instead of creating a second one. Portfolio names remain non-unique (no new uniqueness business rule).

---

## User Scenarios & Testing *(mandatory)*

The actor is the **Investor** (glossary: the person who owns and manages one or more portfolios).
Each story below is an independently demonstrable slice; US1 alone is a viable MVP.

### User Story 1 - Create a portfolio with one position (Priority: P1)

An Investor starts creating a new Portfolio, provides a name, adds one valid Position (ticker,
market, quantity, currency), and saves. The system validates the Portfolio, persists it with its
Position, and confirms success.

**Why this priority**: This is the core purpose of the feature (FD001 §1–§2). Without it, no
Portfolio exists for any future capability to act on. Shipping only this story already delivers
the feature's user value.

**Independent Test**: From the Create Portfolio entry point, enter a name, add one valid Position,
save; confirm a success message and that the Portfolio and its Position are persisted with exactly
the values entered. The same journey is additionally proven by an automated browser end-to-end
test (**E2E-001**) run against the fully containerized platform.

**Acceptance Scenarios**:

1. **Given** the Investor is creating a new Portfolio, **When** they provide a non-empty name, add one Position with a ticker, market, quantity greater than zero, and currency, and select Save, **Then** the Portfolio and its Position are persisted and the Investor is told the Portfolio was created successfully. *(FD001 AC-001)*
2. **Given** a successfully created Portfolio, **When** its stored data is inspected, **Then** it contains exactly the name and the one Position the Investor entered, with no values the Investor did not provide. *(FD001 BR-009, §14.9)*
3. **Given** the Investor has entered a valid name and one valid Position, **When** they save, **Then** the business outcome "a Portfolio has been created" and "a Position has been added" is recorded (PortfolioCreated, PositionAdded — as semantic events, no messaging mechanism implied). *(FD001 §10)*
4. **Given** the whole platform is running as containers and the Investor opens the Create Portfolio screen in a real browser, **When** they enter a valid name, add one valid Position, and save, **Then** the Portfolio is created and the success confirmation is shown — verified end to end through the real browser → frontend → REST API → `core-service` → PostgreSQL stack, with **no** mocked frontend-to-backend communication and **no** mocked persistence. *(FD001 §13 E2E-001 — mandatory closure gate)*

---

### User Story 2 - Create a portfolio with several positions (Priority: P2)

An Investor adds multiple Positions for different instruments before saving, and the whole
Portfolio is persisted with all of them in one action.

**Why this priority**: Real portfolios hold several instruments; a single-position-only feature
would force awkward workarounds. It builds directly on US1.

**Independent Test**: Add three Positions with distinct `ticker + market` identities, save, and
confirm all three are persisted under the one Portfolio.

**Acceptance Scenarios**:

1. **Given** the Investor is creating a new Portfolio, **When** they add several valid Positions with different `ticker + market` identities and select Save, **Then** the Portfolio is persisted with all entered Positions. *(FD001 AC-002)*
2. **Given** the Investor has added two Positions with the same ticker but different markets, **When** they save, **Then** both Positions are accepted as distinct holdings. *(FD001 BR-003)*
3. **Given** a save fails validation for any one Position, **When** the Investor is notified, **Then** no Portfolio and no Position is persisted (all-or-nothing). *(FD001 §4.11–4.12)*

---

### User Story 3 - Validation prevents invalid portfolios (Priority: P2)

The Investor is stopped from saving a Portfolio that breaks a business rule, and is told exactly
what to fix, with nothing persisted.

**Why this priority**: The Feature Definition's business rules (BR-001…BR-005) and half its
acceptance criteria (AC-003…AC-005) are about rejecting invalid input. Data integrity for every
downstream capability depends on it.

**Independent Test**: Attempt to save with (a) an empty name, (b) a Position with quantity 0 or
negative, and (c) two Positions sharing the same `ticker + market`; confirm each attempt is
rejected with a specific message and no Portfolio is created.

**Acceptance Scenarios**:

1. **Given** the Portfolio name is empty or only whitespace, **When** the Investor tries to save, **Then** the Portfolio is not saved and the Investor is told the name is required. *(FD001 AC-003, BR-001)*
2. **Given** the Investor has provided a name but added no Position, **When** they try to save, **Then** the Portfolio is not saved and the Investor is told at least one Position is required. *(FD001 BR-002)*
3. **Given** the Investor is adding a Position, **When** Quantity is zero, negative, or not a valid number, **Then** the Position is not accepted and the Investor is told a valid Quantity is required. *(FD001 AC-004, BR-005)*
4. **Given** the Investor is adding a Position, **When** ticker, market, or currency is missing, **Then** the Position is not accepted and the Investor is told which field is required. *(FD001 §5)*
5. **Given** the Investor has already added a Position for a specific `ticker + market`, **When** they attempt to add another Position with the same `ticker + market`, **Then** the duplicate is rejected and the Investor is told the Position already exists in this Portfolio. *(FD001 AC-005, BR-004)*
6. **Given** any validation error, **When** it is shown, **Then** the message is presented near the affected field / Position and no partial data is persisted. *(FD001 §11; design-system "Validation")*

---

### User Story 4 - Optional acquisition information (Priority: P3)

An Investor who does not know the initial purchase date or the average purchase price can still
add the Position and save the Portfolio; the system never invents those values.

**Why this priority**: FD001 explicitly allows incomplete acquisition data (BR-008, BR-009,
AC-006, AC-007) and forbids fabricating a price. It refines US1/US2 rather than gating them.

**Independent Test**: Add a valid Position leaving Initial Purchase Date and Average Purchase
Price blank; save; confirm the Portfolio is created and both values remain unset (not zero, not
inferred).

**Acceptance Scenarios**:

1. **Given** the Investor adds a valid Position without an Initial Purchase Date, **When** they save, **Then** the Position and Portfolio are saved and the date is recorded as "not provided". *(FD001 AC-006, BR-008)*
2. **Given** the Investor adds a valid Position without an Average Purchase Price, **When** they save, **Then** the Position and Portfolio are saved, the price is recorded as "not provided", and the system does not infer or default a price. *(FD001 AC-007, BR-009)*
3. **Given** the Investor provides an Average Purchase Price, **When** the Position is saved, **Then** that price is interpreted in the Currency selected for that Position. *(FD001 AC-008, BR-006, BR-007)*
4. **Given** the Investor provides an Average Purchase Price that is zero or negative, **When** they try to add the Position, **Then** it is rejected with a message that the price must be greater than zero (a price is only recorded when it is a positive amount). *(derived — see Assumptions)*

---

### User Story 5 - Review and correct the draft before saving (Priority: P3)

While building the Portfolio, the Investor sees the list of Positions added so far and can remove
or correct a Position before persisting.

**Why this priority**: FD001 §11 requires a visible list of Positions being added and an action to
remove or correct one before the Portfolio is persisted. It improves the creation flow but the
Portfolio can be created without it.

**Independent Test**: Add three Positions, remove the second, correct the quantity on the first,
then save; confirm the persisted Portfolio reflects the corrected set (two Positions, corrected
quantity).

**Acceptance Scenarios**:

1. **Given** the Investor has added one or more Positions, **When** they view the creation screen, **Then** each Position added so far is listed with its key details (ticker, market, quantity, currency). *(FD001 §11)*
2. **Given** a Position is in the draft list, **When** the Investor removes it, **Then** it is no longer part of the Portfolio and is not persisted on Save. *(FD001 §11)*
3. **Given** a Position is in the draft list, **When** the Investor corrects one of its values and re-confirms, **Then** the corrected values are what get validated and persisted. *(FD001 §11)*
4. **Given** the Investor removes the only remaining Position, **When** they try to save, **Then** the save is blocked because at least one Position is required. *(FD001 BR-002)*

---

### Edge Cases

- **Whitespace-only name** — treated as empty; save is blocked (BR-001).
- **No positions** — save is blocked with "at least one Position required" (BR-002).
- **Non-numeric / zero / negative quantity** — Position rejected (BR-005, AC-004).
- **Same ticker, different market** — accepted as two distinct Positions (BR-003).
- **Same `ticker + market` added twice** — second is rejected as a duplicate (BR-004, AC-005).
- **Average Purchase Price provided, currency not chosen** — cannot occur: Currency is mandatory for every Position (BR-006); the price is always interpreted in that Currency (BR-007).
- **Only one of {purchase date, purchase price} provided** — allowed; the other stays "not provided" (BR-008).
- **Future Initial Purchase Date** — rejected as invalid (see Assumptions).
- **Validation fails for one Position among many** — the entire Save is rejected; nothing is persisted (all-or-nothing).
- **Save fails on persistence (not validation)** — the Investor sees a non-technical "could not save, try again" message, the draft (name + Positions) is kept for retry, and nothing partial is persisted; no automatic retry (Clarification 2026-09-01).
- **Investor navigates away / abandons the draft before Save** — no Portfolio is persisted.
- **Duplicate Portfolio name** (the default Investor already has a Portfolio with the same name) — allowed; Portfolio names need not be unique (see Assumptions).
- **Accidental double Save of the same draft** (double-click, client retry) — only one Portfolio is created; the Save control is disabled during submission and a repeated identical submission resolves to the already-created Portfolio (Clarification 2026-09-01).
- **Unrecognised currency or market code** — see Assumptions (format-level check only in FD001; no external reference-data validation).

---

## Requirements *(mandatory)*

### Functional Requirements

#### Portfolio creation and naming

- **FR-001**: The Investor MUST have a clear entry point to start creating a new Portfolio.
- **FR-002**: The Investor MUST be able to provide a name for the Portfolio being created.
- **FR-003**: The system MUST reject a Portfolio whose name is empty or contains only whitespace, and MUST tell the Investor the name is required. *(BR-001, AC-003)*
- **FR-004**: The system MUST NOT require Portfolio names to be unique across the Investor's Portfolios.

#### Adding positions

- **FR-005**: The Investor MUST be able to add one or more Positions to the Portfolio before saving.
- **FR-006**: For each Position the Investor MUST provide: a ticker, a market, a quantity, and a currency. *(FD001 §5)*
- **FR-007**: For each Position the Investor MAY optionally provide an Initial Purchase Date and an Average Purchase Price. *(FD001 §5, BR-008)*
- **FR-008**: The system MUST reject a Position that is missing any required field (ticker, market, quantity, currency) and MUST indicate which field is required. *(FD001 §5)*
- **FR-009**: The system MUST reject a Position whose Quantity is not a number greater than zero, and MUST tell the Investor a valid Quantity is required. *(BR-005, AC-004)*
- **FR-010**: The system MUST reject an Average Purchase Price that is not a positive amount when one is provided. *(derived — Assumptions)*
- **FR-011**: The system MUST reject an Initial Purchase Date that is in the future when one is provided. *(derived — Assumptions)*

#### Financial instrument identity

- **FR-012**: Within a Portfolio, a Position MUST be uniquely identified by the combination `ticker + market`. *(BR-003, §14.1)*
- **FR-013**: The system MUST reject a second Position in the same Portfolio that has the same `ticker + market` as an existing Position, and MUST tell the Investor the Position already exists in this Portfolio. *(BR-004, AC-005)*
- **FR-014**: Two Positions with the same ticker but different markets MUST be accepted as distinct holdings. *(BR-003)*
- **FR-015**: The market SHOULD be captured as an ISO 10383 Market Identifier Code (MIC) where the Investor supplies one; the system MUST record the market value the Investor provides. *(BR-003, §14.2)*
- **FR-016**: The currency MUST be captured using ISO 4217 currency codes. *(BR-006, §14.5)*
- **FR-017**: The system MUST NOT infer a Position's currency from its ticker or market. *(BR-006, §14.4)*

#### Acquisition information semantics

- **FR-018**: The system MUST allow a Portfolio to be created when the Investor does not provide the Initial Purchase Date and/or the Average Purchase Price for any Position. *(BR-008, AC-006, AC-007)*
- **FR-019**: The system MUST NOT fabricate, infer, or default an Average Purchase Price or Initial Purchase Date that the Investor did not provide; a missing value MUST be represented explicitly as "not provided", distinct from zero. *(BR-009, §14; development-rules DR-012)*
- **FR-020**: When an Average Purchase Price is provided, the system MUST interpret and store it in the Currency selected for that Position. *(BR-007, AC-008, §14.6)*
- **FR-021**: The system MUST store each Position as a single current aggregated holding and MUST NOT record individual transactions or purchase lots. *(BR-010, §14.9–14.10)*

#### Validation and save

- **FR-022**: The system MUST require at least one valid Position before a Portfolio can be saved. *(BR-002)*
- **FR-023**: On Save, the system MUST validate the Portfolio and every entered Position, and MUST persist the Portfolio and all its Positions atomically — either the whole Portfolio with every Position is persisted, or nothing is (no partial Portfolio, no orphan Positions), whether the failure is a validation failure or a persistence failure. *(FD001 §4.11–4.12; Clarification 2026-09-01)*
- **FR-023a**: If Save fails because of a persistence error (not a validation error), the system MUST show the Investor a non-technical "could not save, please try again" message, MUST retain the entered Portfolio name and Positions so the Investor can retry, and MUST NOT automatically retry. *(Clarification 2026-09-01; design-system "recoverable operation errors")*
- **FR-024**: Validation feedback MUST be specific (identify the field or Position at fault) and MUST be shown close to the affected input. *(FD001 §11; design-system "Validation")*
- **FR-025**: Financial and monetary values (quantity, average purchase price) MUST be handled with exact decimal precision — no rounding or binary floating-point drift in stored values. *(development-rules DR-011)*

#### Draft management

- **FR-026**: While creating the Portfolio, the system MUST show the Investor the list of Positions added so far with their key details. *(FD001 §11)*
- **FR-027**: The Investor MUST be able to remove a Position from the draft before the Portfolio is persisted. *(FD001 §11)*
- **FR-028**: The Investor MUST be able to correct a Position's values before the Portfolio is persisted. *(FD001 §11)*

#### Persistence and confirmation

- **FR-029**: On successful validation, the system MUST persist the Portfolio and all its Positions durably. *(BR-002, AC-001, §4.12)*
- **FR-030**: A created Portfolio MUST be associated with exactly one owning Investor. FD001 uses a single **default Investor** seeded by the platform; every Portfolio FD001 creates is owned by that Investor. The Investor is not chosen, entered, or authenticated in this feature. Ownership MUST be recorded (non-null) on every created Portfolio. Real multi-investor identity and authentication are deferred to a later Feature Definition / Technical Enabler. *(information-model; FD001 §9; Clarification 2026-09-01)*
- **FR-031**: After a successful save, the system MUST confirm to the Investor that the Portfolio was created successfully. *(AC-001, §4.13)*
- **FR-031a**: The system MUST prevent an accidental duplicate creation from a single creation flow: while a Save submission is in flight the Save action MUST be unavailable, and a repeated identical submission of the same draft MUST NOT create a second Portfolio (it resolves to the Portfolio already created for that submission). This does not introduce a Portfolio-name uniqueness rule (FR-004 still holds). *(Clarification 2026-09-01)*
- **FR-032**: The system MUST record the creation of the Portfolio and the addition of each Position as business outcomes (PortfolioCreated, PositionAdded). No messaging technology, topic, or event schema is implied or required. *(FD001 §10; architecture-rules AR-017)*

#### Scope guardrails

- **FR-033**: The feature MUST NOT provide any capability to list, view-after-creation, edit, or delete an existing Portfolio, nor to add/reduce/remove Positions after persistence. *(FD001 §3 Out of Scope)*
- **FR-034**: The feature MUST NOT compute valuation, market prices, weights, risk, recommendations, or stop-loss information. *(FD001 §3 Out of Scope)*
- **FR-035**: The feature MUST extend the existing executable platform under `implementation/platform/` (frontend, `core-service`, contracts, persistence) and MUST NOT create an isolated application or a new deployable component. *(CLAUDE.md §5–§8; ADR-001)*

#### End-to-end verification

- **FR-036**: The critical Create Portfolio journey MUST be covered by an automated end-to-end test (**E2E-001**) that drives the running frontend in a real browser and exercises the full stack — browser → frontend → REST API → `core-service` → PostgreSQL — with **no** mocked frontend-to-backend communication and **no** mocked persistence. The test starts from the Create Portfolio screen, enters a valid name, adds one valid Position, saves, and asserts the success confirmation. It runs against the fully containerized platform using the foundation established by `EN002` (canonical entry point `implementation/platform/e2e.sh`; expected location `implementation/platform/e2e/tests/FD001-create-portfolio.spec.ts`). **E2E-001 passing is a mandatory closure gate**: FD001 MUST NOT be accepted, closed, or marked Completed while E2E-001 is missing or failing. *(FD001 §13 "E2E Testing" / E2E-001; §16; EN002)*
- **FR-037**: E2E-001 MUST use synthetic test data and MUST NOT depend on artifacts left by earlier test runs (it runs against the disposable database `EN002`'s `e2e.sh` provisions per run). It MUST NOT introduce a test-only backend endpoint or otherwise add behaviour that exists only for testing. *(EN002 §15; development-rules)*

### Key Entities

- **Portfolio** *(created)* — a named collection of Positions owned by one Investor. Attributes used by FD001: identity, name, owning Investor, creation time, status. Other information-model attributes (description, valuations, reviews, risks) are out of scope.
- **Position** *(created as part of the Portfolio)* — the Investor's current aggregated holding of one Financial Instrument within the Portfolio. Attributes used by FD001: identity, quantity, currency, optional initial purchase date, optional average purchase price (in the Position currency), and the `ticker + market` reference to the instrument. No transactions or purchase lots.
- **Financial Instrument** *(referenced, not managed here)* — identified within the Portfolio by `ticker + market` (MIC where available). FD001 does not create or enrich a canonical Financial Instrument record; it records the identifying values the Investor supplies.
- **Investor** *(owner)* — the person who owns the Portfolio. FD001 does not create, select, or authenticate Investors; it uses one platform-seeded default Investor as the owner of every created Portfolio (Clarification 2026-09-01).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An Investor can create a Portfolio with one Position — from the entry point to the success confirmation — in under 2 minutes, entering each required value exactly once with no repeated steps or workarounds.
- **SC-002**: An Investor can create a Portfolio containing at least 10 distinct Positions in a single Save.
- **SC-003**: 100% of Save attempts that violate a business rule (empty name, no Position, non-positive quantity, missing required Position field, duplicate `ticker + market`) are rejected with a field- or Position-specific message, and **zero** Portfolios or Positions are persisted for those attempts.
- **SC-004**: 100% of Positions created without an Average Purchase Price and/or Initial Purchase Date are stored with those values explicitly absent (never 0, never a system-chosen value).
- **SC-005**: For 100% of created Positions that have an Average Purchase Price, the stored price currency equals the Position's currency.
- **SC-006**: A persisted Portfolio, when its stored data is inspected, contains exactly the name and Positions the Investor entered — no added, altered, or inferred values — for 100% of successful creations.
- **SC-007**: Stored quantity and price values reproduce the Investor's input exactly (no precision loss) for 100% of created Positions.
- **SC-008**: A duplicate `ticker + market` within one Portfolio draft is detected and blocked before persistence in 100% of attempts.
- **SC-009**: 100% of the Feature Definition's acceptance criteria (AC-001…AC-008) have automated test evidence, and the critical creation journey (FD001 §13 E2E-001) has automated end-to-end test evidence.
- **SC-010**: When a Save fails after validation passes (persistence error), the Investor sees a non-technical retry message and their entered data is preserved; **zero** partial Portfolios and **zero** orphan Positions exist afterwards, in 100% of such failures.
- **SC-011**: A repeated identical submission of the same creation draft results in exactly one persisted Portfolio, in 100% of double-submission attempts.
- **SC-012**: Every persisted Portfolio has a non-null owning Investor (the default Investor), for 100% of created Portfolios.
- **SC-013**: The critical Create Portfolio journey — open the screen, enter a valid name, add one valid Position, Save → success confirmation, Portfolio persisted — passes an automated end-to-end test run **through a real browser against the fully containerized platform** (`implementation/platform/e2e.sh` exits 0), with no mocked frontend-to-backend communication and no mocked persistence. This test passing is a **mandatory closure gate** for FD001 — the feature is not "done" while it is missing or failing. *(FD001 §13 E2E-001, §16)*

## Assumptions

- **A1 — Delivery shape**: FD001 is delivered as one vertical slice across the existing platform — a frontend creation flow, a backend capability, an external contract for the create operation, and durable persistence — per FD001 §12 and CLAUDE.md. The specification stays behaviour-focused; interaction, contract, and technical design belong to the plan.
- **A2 — Owning Investor** *(resolved — Clarification 2026-09-01, see FR-030)*: each created Portfolio is owned by a single platform-seeded default Investor. The default Investor record must exist before FD001 can create a Portfolio (seeded via the platform's migration mechanism). Real multi-investor identity and authentication are deferred to a future Feature Definition / Technical Enabler.
- **A3 — Non-positive average purchase price**: when the Investor provides an Average Purchase Price, it must be a positive amount; zero or negative is a rejected input error (an acquisition price cannot be ≤ 0). A blank price remains valid (FR-018).
- **A4 — Future purchase date**: when provided, the Initial Purchase Date must not be in the future; a future date is a rejected input error. A blank date remains valid.
- **A5 — Currency / market validation depth**: FD001 validates that currency is an ISO 4217 code and records the market/MIC as provided. It does **not** verify the code against an external reference-data source or confirm that the instrument actually trades on that market — instrument enrichment and reference-data validation are out of scope (FD001 §3).
- **A6 — Quantity type**: Quantity is a positive numeric amount; whether fractional quantities are permitted follows ordinary investment practice (fractional shares/units allowed) unless a later Feature Definition restricts it.
- **A7 — Name constraints**: the Portfolio name has a reasonable maximum length (e.g. a single-line label, on the order of 120 characters) and is stored as entered (trimmed of surrounding whitespace). Names are not required to be unique (FR-004).
- **A8 — Draft lifetime**: an unsaved Portfolio draft is not persisted; there is no requirement to recover a draft after the Investor leaves the creation flow.
- **A9 — Confirmation content**: the success confirmation states that the Portfolio was created; navigating to a view of the created Portfolio is out of scope (FD001 §3), so the confirmation does not link to a Portfolio detail screen.
- **A10 — Single Portfolio per invocation**: one creation flow creates exactly one Portfolio; bulk import of multiple Portfolios is not part of FD001.
- **A11 — Access posture** *(accepted temporary state)*: because there is a single default Investor and no authentication (A2), the create-Portfolio capability is reachable without a login, consistent with the EN001 platform. Portfolio data is nonetheless private (architecture-rules AR-035/AR-038); the plan should surface this as security debt to be closed by the future identity/auth feature, and FD001 must not weaken any protection that later exists.
- **A12 — Portfolio status on creation**: a newly created Portfolio is recorded with a single active status (the information model's default). FD001 defines no other Portfolio lifecycle states or transitions.
- **A13 — E2E scope**: end-to-end coverage for FD001 is exactly **one** test — the critical happy-path creation journey (FD001 §13 E2E-001). The full acceptance-criteria matrix (AC-001…AC-008, validation, idempotency, optional data, draft editing) is verified by the existing unit / domain / integration / contract tests, not re-verified through the browser. This keeps the E2E suite deliberately small and journey-focused (`product/engineering/testing-strategy.md`; EN002 §22). E2E-001 asserts the success confirmation; it does not need a database assertion (the persistence guarantees are already covered by the integration tests), though a lightweight post-check is permitted as secondary evidence.
- **A14 — E2E prerequisite**: E2E-001 requires `EN002` (the containerized platform + `e2e.sh` + the Playwright runner) to be in place. FD001's implementation adds the `FD001-create-portfolio.spec.ts` test into that existing foundation; it does not modify the E2E infrastructure.

## Dependencies

- **EN001 — Bootstrap Executable Platform** (READY TO CLOSE) provides the frontend shell, `core-service`, PostgreSQL + Flyway, and the contracts location this feature extends.
- **EN002 — Establish Containerized End-to-End Testing Foundation** provides the containerized platform, `implementation/platform/e2e.sh`, and the Playwright runner that **E2E-001** (FR-036, SC-013) runs in. EN002 must be in place before FD001's E2E gate can be satisfied.
- **ADR-001 — Initial Backend Topology**: the feature is implemented inside the single `core-service` deployable.
- **ADR-002 — Interim Unauthenticated Write Access** (Status: Proposed): records the accepted security posture for FD001's unauthenticated `POST /api/portfolios` (A11); MUST be human-approved before FD001 merges.
- Authoritative Feature Definition: `product/definition/features/FD001_Create_portfolio/FD001_Create_portfolio.md`.
- Global definitions: `product/definition/global/{glossary,information-model,domains,business-events}.md`.
- Governance: `product/architecture/*`, `product/engineering/*`, `product/governance/*`, `product/ux/design-system.md`.
- Affected functional domains: Portfolio Management, Financial Instruments (FD001 §8) — no service or deployment boundary implied.

## Out of Scope

Carried verbatim from FD001 §3 — deferred to later Feature Definitions:

- Listing existing Portfolios; viewing a Portfolio after creation; editing an existing Portfolio.
- Adding, reducing, or removing Positions after a Portfolio is persisted.
- Transaction / movement history; purchase lots.
- Portfolio valuation; market-price enrichment; Position weights.
- Risk analysis; recommendations; stop-loss analysis.
- Automatic currency inference; automatic recalculation of average purchase price after future purchases.
- Authentication, authorization, and multi-investor identity management (not in FD001; see FR-030 / A2 / A11).
- Any messaging/broker infrastructure for the PortfolioCreated / PositionAdded business events.
