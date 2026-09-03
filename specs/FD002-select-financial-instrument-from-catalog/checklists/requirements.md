# Specification Quality Checklist: Select Financial Instrument from Catalog (FD002)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-03
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — *see Note 1*
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification — *see Note 1*

## Notes

1. **Contract references, not implementation choices.** The spec names `GET /api/financial-instruments`
   (delivered by EN004) and `POST /api/portfolios` (delivered by FD001), and the validation code
   `INSTRUMENT_NOT_IN_CATALOG`. These are the **already-agreed business contracts** this feature
   extends, referenced so requirements are testable and traceable — consistent with the project's
   contract-first governance (constitution VIII). No language/framework/schema/component design is
   specified; the *how* remains for `/speckit-plan`.
2. **Clarifications resolved 2026-09-03** (spec §Clarifications):
   - FD002 §18 OQ1 → constrained Market/Currency selectors after the Investor picks the instrument
     (only that instrument's real catalogued listings). → FR-007, AC-004, AC-005.
   - Enforcement of BR-004 / AC-006 → **backend** validates each Position on `POST /api/portfolios`
     with a new `INSTRUMENT_NOT_IN_CATALOG` `ValidationProblem` code. → FR-011, FR-020, SC-003.
   - §18 OQ2–OQ6 resolved to safe defaults from EN004's capability → Assumptions A5–A9.
3. **Process gate:** the FD002 Feature Definition
   (`product/definition/features/FD002-select-financial-instrument-from-catalog.md`) was **approved
   by jaruiz on 2026-09-03** — §19 all boxes checked, `Status: Approved`, and the §18 Open Questions
   resolved in a new "Open Question resolutions (2026-09-03)" subsection. `product/` changes for
   FD002 (both human-directed): the approval sync, and `architecture-rules.md` **AR-062**.
4. **`/speckit.analyze` (2026-09-03):** 0 CRITICAL/HIGH. Remediation applied — A1: the catalog check
   now includes **currency** (`isSelectable(ticker, market, currency)`; `AAPL+XNAS+EUR` vs a USD
   listing is rejected) — spec FR-011, research D1/D2/D3, data-model, contracts, tasks T012/T014/T016–T018
   updated. A2: EUR selection/validation cases added to T007/T014. A3/A4/A5/A7: minor task-note fixes.

**Result:** all checklist items pass and the Feature Definition is human-approved. **Ready for
`/speckit-plan`** (`/speckit-clarify` optional — the two material questions are already resolved).
