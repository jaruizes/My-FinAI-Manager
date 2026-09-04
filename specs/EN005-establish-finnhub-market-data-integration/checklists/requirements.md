# Specification Quality Checklist: Establish External Market Data Capabilities (EN005 — Revision 2)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-04
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — provider names (Finnhub, Frankfurter)
  and endpoint paths are mandated by the approved enabler and are part of the enabler's intent, not
  incidental tech choices; kept as in the enabler.
- [x] Focused on user value and business needs — as an enabler, "user" = backend developer /
  operator / maintainer; every story ties to a capability the platform needs.
- [x] Written for non-technical stakeholders — the enabler audience is technical; the spec stays at
  capability/behavior level, not code.
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — Q1/Q2/Q3 answered 2026-09-04 (financialinstrument
  owns profile enrichment; keep current port/exception names; keep price+FX TtlCache, drop profile
  cache).
- [x] Requirements are testable and unambiguous — each FR has an associated deterministic test in
  its user story; the 3 open items are explicitly scoped.
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic — outcomes are verifiable results; the few tool
  references (`./mvnw verify`, `ng test`, `./e2e.sh`, ArchUnit) are the project's established gates,
  matching FD002–FD004 / EN004.
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded — Revision note + Out of Scope + scope-guardrail FRs.
- [x] Dependencies and assumptions identified — incl. the FD004 consumer + E2E ripple and the new
  Frankfurter external dependency.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — FR-004 / FR-025 / FR-031 resolved
  by Q2 / Q1 / Q3.
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification — beyond the enabler-mandated provider/endpoint
  facts.

## Notes

- **Enabler is Approved (reopened for revision)** — §29 signed by jaruiz 2026-09-04, all boxes
  checked (provider-neutral architecture, one adapter per capability, Finnhub for price/profile,
  Frankfurter for FX, US-only initial coverage, database-first profile, independent provider config,
  multiple adapters, no fallback).
- **Clarifications resolved 2026-09-04** — Q1: `financialinstrument` owns the DB-first profile
  enrichment (repo port + JPA + Flyway table + `GetInstrumentProfile` + `FinnhubInstrumentProfileAdapter`
  all move there; `marketdata` = price + FX only). Q2: keep the current port/exception names. Q3:
  keep `TtlCache` + `CachingMarketDataPort`/`CachingFxRatePort`, remove `CachingInstrumentProfilePort`.
- **Cross-feature impact recorded, not asked**: FD004's `EnMarketDataGatewayAdapter` + tests + E2E
  stub are updated by this work (FR-038, FR-039); FD004's domain/business/calculator/API/UI are not.
- The previous (2026-09-03) EN005 spec/plan/tasks/closure are **superseded** by this revision (§28 —
  revise in place, prior EN005 not formally closed).

**Ready for `/speckit-plan`.**
