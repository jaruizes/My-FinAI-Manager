<!--
Sync Impact Report
- Version change: (unpopulated template) → 1.0.0
- Rationale: Initial ratification of a minimal, framework-facing constitution. MAJOR set to 1.
- Principles defined (8):
  1. Human-Governed Source of Truth
  2. Definitions and Enablers Are Authoritative Intent
  3. Derived Artifacts and Repository Layout
  4. No Invention; Surface Material Ambiguity
  5. Technical Enablers Stay Technical
  6. Hexagonal Architecture and Deterministic Logic
  7. Test-First for Deterministic Logic; Testcontainers by Default
  8. Contract-First External APIs
- Added sections:
  * Authoritative Governance Sources
  * Development Workflow and Compliance
  * Governance
- Removed sections: none (template slots [SECTION_2], [SECTION_3] repurposed)
- Templates requiring updates:
  * .specify/templates/plan-template.md ✅ already reads "Constitution Check" at runtime; no change required
  * .specify/templates/spec-template.md ✅ no constitution coupling
  * .specify/templates/tasks-template.md ✅ no constitution coupling
- Deferred TODOs: none
-->

# My-FinAI-Manager Constitution

This constitution is the **framework-facing operating agreement** for Spec-Driven Development
tooling (Spec Kit) and coding agents working in this repository. It is intentionally minimal.

It does not restate project governance. The detailed, authoritative rules live under `product/`
(`definition/`, `architecture/`, `engineering/`, `governance/`). Where this document and any
document under `product/` appear to conflict, the `product/` document prevails and the conflict
MUST be surfaced for human resolution.

`CLAUDE.md` at the repository root is the **Claude Code execution adapter**: it translates these
principles and the `product/` rules into concrete agent behavior. `CLAUDE.md` MUST NOT weaken this
constitution or `product/`; if it drifts, `product/` and this constitution win.

## Core Principles

### I. Human-Governed Source of Truth

`product/` is the human-governed source of truth for product intent, architecture, engineering
standards, and governance. Agents and SDD tooling MAY analyze, challenge, and draft changes to it,
but MUST NOT silently modify it. Approved ADRs under `product/architecture/adrs/` and
`product/architecture/technology-policy.md` are binding and MUST be respected in every
specification, plan, task, and implementation.

**Rationale**: A single, stable, human-owned definition prevents tooling or generated artifacts
from becoming an accidental product authority.

### II. Definitions and Enablers Are Authoritative Intent

Feature Definitions (`FDNNN`) under `product/definition/features/` and Technical Enablers
(`ENNNN`) under `product/definition/enablers/` are the authoritative statements of product and
technical intent respectively. Specifications, plans, and tasks MUST trace to exactly one
Feature Definition or Technical Enabler and MUST NOT expand, narrow, or reinterpret its scope,
business rules, or acceptance criteria without human approval recorded in the source artifact.

**Rationale**: Traceability to a human-approved intent is what makes generated work reviewable.

### III. Derived Artifacts and Repository Layout

SDD artifacts (specifications, research, plans, data models, contracts, tasks, checklists) are
**derived** and MUST live under `specs/<FEATURE-OR-ENABLER-ID>/`. They MUST NOT be placed under
`product/`. Executable implementation MUST live under `implementation/platform/` and MUST NOT
introduce alternative repository-root implementation trees (e.g. `apps/`, `services/`, `src/`,
root `backend/`). The framework is replaceable; regenerating `specs/` MUST NOT lose traceability
to the source Definition or Enabler.

**Rationale**: Separating human-governed intent, derived artifacts, and executable code keeps each
layer independently reviewable and keeps the project understandable without the tooling.

### IV. No Invention; Surface Material Ambiguity

Agents MUST NOT invent requirements, business rules, terminology, technologies, infrastructure, or
architecture decisions. When a decision materially affects user-visible behavior, business or data
semantics, architecture, persistence ownership, service boundaries, technology, security, or public
contracts, the agent MUST stop, state the ambiguity and its impact, propose options, and obtain
human approval before proceeding. Only safe, reversible, non-material implementation details may be
chosen without escalation, and they MUST be recorded (e.g. in `research.md` or an Assumptions
section).

**Rationale**: The highest-risk failure mode is a plausible assumption hardening into an apparent
official requirement.

### V. Technical Enablers Stay Technical

A Technical Enabler MUST NOT be converted into an artificial user-facing feature. Enabler
specifications MUST NOT fabricate investor user stories or business behavior; their acceptance is
expressed through the enabler's own verification criteria. Feature implementations MUST extend the
shared platform under `implementation/platform/` as vertical slices and MUST NOT create isolated,
feature-specific applications. After any change the platform MUST remain coherent and executable
through `implementation/platform/start.sh` and `stop.sh`.

**Rationale**: Enablers exist to make delivery possible; forcing product framing onto them
corrupts both the enabler and the product backlog.

### VI. Hexagonal Architecture and Deterministic Logic

All backend business-capable components MUST follow Hexagonal Architecture with dependencies
pointing inward: domain and application code MUST NOT depend on frameworks, persistence engines,
messaging, cloud SDKs, or LLM SDKs, which belong in adapters behind explicit ports. Deterministic
business and financial logic MUST remain deterministic and MUST NOT be delegated to an LLM; AI/LLM
components may explain, classify, summarize, interpret, or recommend where a Definition allows, but
their output is never automatically treated as fact.

**Rationale**: Isolating business logic preserves testability, portability, and the integrity of
financial calculations.

### VII. Test-First for Deterministic Logic; Testcontainers by Default

Deterministic domain and business logic MUST be developed test-first (RED → GREEN → REFACTOR), with
tests that assert meaningful behavior rather than only raise coverage. Integration tests against
application-managed infrastructure (e.g. PostgreSQL, and later Kafka or Neo4j) MUST use
Testcontainers by default when a suitable container image exists and MUST NOT depend on manually
installed local or CI infrastructure. Mocks/stubs remain appropriate for true external providers
(market data, news, LLM, SaaS).

**Rationale**: Behavior-first tests drive design; real disposable infrastructure catches
integration defects that mocks hide.

### VIII. Contract-First External APIs

Externally exposed REST APIs MUST be contract-first and defined with OpenAPI under
`implementation/platform/contracts/openapi/`. Contracts MUST use business language, MUST NOT expose
persistence or framework structures, MUST provide stable machine-readable errors, and MUST NOT
drift silently from the implementation (contract tests required). Business events do not
automatically imply Kafka, topics, or AsyncAPI.

**Rationale**: A stable, business-oriented contract decouples clients from internal topology and
makes compatibility changes deliberate.

## Authoritative Governance Sources

These repository areas are authoritative and binding; this constitution defers to them for detail:

- `product/definition/` — vision, domains, glossary, information model, business events,
  Feature Definitions, Technical Enablers.
- `product/architecture/` — `architecture.md`, `architecture-rules.md`, `technology-policy.md`,
  and all approved ADRs under `adrs/`.
- `product/engineering/` — `development-rules.md`, `testing-strategy.md`, `definition-of-done.md`.
- `product/governance/` — `sdd-policy.md`, `ai-development-policy.md`.
- `CLAUDE.md` — Claude Code execution adapter (subordinate to the above).

Agents MUST read the relevant documents in these areas before specifying, planning, generating
tasks, or implementing work that they govern.

## Development Workflow and Compliance

- **Lifecycle**: Human intent → Feature Definition / Technical Enabler → specification →
  clarification → architecture impact analysis → plan → tasks → implementation → verification.
  Spec Kit commands orchestrate the derived steps; the conceptual lifecycle in
  `product/governance/sdd-policy.md` is authoritative.
- **Plan gate**: Every implementation plan MUST include a Constitution Check evaluating these
  principles and MUST resolve or explicitly justify any violation before design proceeds.
  Unjustified violations block the plan.
- **Completion gate**: Work is "done" only when it satisfies the applicable items in
  `product/engineering/definition-of-done.md`. Passing CI or compiling is necessary but not
  sufficient.
- **Architecture changes**: Any change that materially affects backend topology, service
  extraction, data ownership, persistence or messaging technology, external API architecture, the
  security model, major technology adoption, or runtime architecture requires an ADR under
  `product/architecture/adrs/` before implementation.
- **AI-generated work** is held to the same standards as human-written work and MUST remain
  scoped, traceable, and reviewable.

## Governance

- **Authority**: This constitution governs Spec Kit usage and agent behavior. It is subordinate to
  `product/` where they overlap; it supplements, and never overrides, human-governed governance.
- **Amendments**: Changes are proposed via pull request modifying `.specify/memory/constitution.md`,
  with a Sync Impact Report and a version bump. A human maintainer MUST approve. Amendments that
  would alter product intent, architecture, or engineering standards MUST instead be made in the
  relevant `product/` document.
- **Versioning**: Semantic versioning of this document —
  MAJOR for backward-incompatible principle removals or redefinitions;
  MINOR for a new principle or materially expanded guidance;
  PATCH for clarifications and non-semantic refinements.
- **Compliance review**: Plans and pull requests are reviewed against these principles. Detected
  violations are either corrected or recorded as an approved exception (ADR or explicit human
  sign-off referenced from the artifact). Silent deviation is not permitted.

**Version**: 1.0.0 | **Ratified**: 2026-08-31 | **Last Amended**: 2026-08-31
