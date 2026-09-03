# Specification Quality Checklist: Establish Financial Instrument Reference Data (EN004)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-03
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
- [x] Success criteria are technology-agnostic (no implementation details)
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

- **This is a Technical Enabler** whose subject *is* backend reference-data infrastructure. Per
  constitution V and `CLAUDE.md`, an enabler spec legitimately describes developer / operator /
  maintainer workflows and technical verification criteria (VC-001…VC-020) rather than investor
  journeys, and it does not invent business behavior. FD002 owns the investor-facing capability.
- **"No implementation details" is applied proportionately.** The authoritative enabler and its
  normalization decision (both human-authored) *name* the target module (`financialinstrument`),
  the technologies (Spring Data JPA, Maven, Flyway, Testcontainers, ArchUnit), ISO 10383 / 6166 /
  4217, and the exact mapping-driven normalization algorithm. The spec reflects those approved
  constraints (FR-001…FR-040) rather than inventing them; it defers class design, entity fields,
  schema DDL, mapper code, batch-transaction mechanics, and the import entry point to
  `plan.md` / `research.md` and flags them as EN004 §33 open technical decisions (A3–A15).
- **Success criteria are outcome-focused**: valid catalog rows, zero-duplicate re-import, every
  bad row quarantined and counted, no generic dot-strip rule, no provider types in domain/API,
  case-insensitive search hits, no outbound provider call on the request path, offline test + E2E,
  failure-safe import, no product change.
- **Enabler status**: **approved by the human 2026-09-03** — `product/…/EN004-….md` is `Status:
  Approved` with §34 signed (jaruiz), together with the two data decisions (OD-EN004-3 → curated
  `instruments.sample.csv`; OD-EN004-20 → keep the `product/` mapping-CSV copies). Decision record in
  `research.md` §"Decision taken (human)".
- **Both clarifications resolved (Session 2026-09-03)**: FR-027 — EN004 delivers only the
  instrument-search endpoint (`GET /api/financial-instruments?query=…`), contract-first;
  `GET /api/markets` is out of scope. FR-040 — initial catalog is equities + ETFs; `instrumentType`
  is optional descriptive metadata, not a filter. No `[NEEDS CLARIFICATION]` markers remain.
- `plan.md` / `research.md` must resolve EN004 §33's open **technical** decisions within ADR-003
  (source-file provisioning A3/A4, currency modeling A5, casing A6, name-search strategy A7,
  batch-transaction strategy A8, inactivation rule A9, provenance placement A10, import entry
  point A13, fixture identifiers A15).
