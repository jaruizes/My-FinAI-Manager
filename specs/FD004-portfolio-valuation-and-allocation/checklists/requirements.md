# Specification Quality Checklist: Portfolio Valuation & Allocation (FD004)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-04 · **Re-validated**: 2026-09-05 (two-mandatory-charts addendum)
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (verifiable outcomes; the few tool references —
  `./mvnw verify`, `ng test`, `./e2e.sh`, ArchUnit — are the project's established gates, matching
  FD002/FD003)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- **FD004 Feature Definition is Approved** — `product/…/FD004-portfolio-valuation-and-allocation.md`
  §31 signed by jaruiz 2026-09-04; the eight §30 open questions were resolved by the product owner
  on 2026-09-04 (recorded in FD004 §30). The three material decisions (execution model = synchronous
  after-commit; API shape = dedicated `GET /api/portfolios/{portfolioId}/valuation`; `PARTIAL` vs
  `FAILED` = `FAILED` only when no Position valuable or no total producible) are captured in
  `spec.md` § Clarifications and drive FR-004 / FR-025 / FR-018.
- The remaining open items are **planning-level technical decisions** (post-commit trigger
  primitive; valuation schema columns; calculation rounding; E2E EN005 stub mechanism) — recorded
  in `spec.md` § Resolved Product Decisions for `research.md` / `plan.md`. They do not change FD004
  intent.
- Two mandatory closure gates carried from FD004 §26/§27: **E2E-001** (create → value → display,
  deterministic stub) and **E2E-002** (provider failure → Portfolio survives, no fabricated zeros).

### 2026-09-05 re-validation — two mandatory allocation charts

- FD004 Feature Definition updated (§4, §17.1–§17.4, §19, BR-014…BR-017, AC-013…AC-015,
  §26 checks 11–15, §28, §29.16–§29.22) and **§31 re-signed by jaruiz** — the Portfolio detail MUST
  show an **Allocation by Ticker** pie chart and an **Allocation by Sector** pie chart when
  valuation data is available; both are mandatory + a closure gate.
- Spec updated: new **US7**; `FR-028` "chart optional" → mandatory; new `FR-044…FR-049`; `AC-013…
  AC-015` + `SC-014`/`SC-015` added to Traceability / Success Criteria; E2E-001 (`FR-038`) and the
  closure gate (`FR-039`) extended with the chart checks; `FR-024` clarifies the response already
  carries the chart data (**no API/contract change**); `FR-041` / Out-of-Scope forbid a new
  charting dependency; **A12** (self-contained SVG rendering, no new dep), **A13** (frontend-only
  change), **A14** (E2E must also stub the **Frankfurter** FX endpoint — EN005 Rev 2 swapped FX off
  Finnhub).
- **Zero `[NEEDS CLARIFICATION]`** — the Feature Definition fully specifies the charts; the only
  technical choice (rendering approach) has a governed default (no new dependency ⇒ self-contained).
- **Not a blocker, recorded**: EN005 Revision 2 is mid-implementation (checkpoints C1+C2 done). Its
  FX-provider swap means `./e2e.sh` currently can't produce a `COMPLETED` FD004 valuation until the
  E2E stub also serves Frankfurter `/v1/latest` — planning folds that prerequisite in (A14).

Ready for `/speckit-plan`.
