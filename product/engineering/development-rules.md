# My-FinAI-Manager — Development Rules

## Purpose

This document defines the development rules that all implementation work in My-FinAI-Manager must follow.

These rules apply to human-written and AI-generated code alike.

They complement:

- `product/architecture/architecture.md`
- `product/architecture/technology-policy.md`
- `product/architecture/architecture-rules.md`
- `product/engineering/testing-strategy.md`
- `product/engineering/definition-of-done.md`

The goal is to make implementation predictable, reviewable, maintainable, and aligned with the product and architecture definitions.

---

# 1. Source of Truth

Implementation work must respect the following order of authority:

1. Human-governed product definition under `product/`
2. Approved architecture rules and ADRs
3. Approved Feature Definition
4. Formal specification produced by the SDD framework
5. Approved implementation plan
6. Approved implementation tasks
7. Existing code

Existing code must not be treated as authoritative when it conflicts with an approved higher-level artifact.

---

# 2. Spec-Driven Development

## DR-001 — No Feature Without a Human Feature Definition

Every product feature must originate from a human-authored Feature Definition under:

```text
product/definition/features/
```

The Feature Definition represents deliberate product and domain intent.

---

## DR-002 — Do Not Invent Product Requirements

Developers and AI agents must not silently introduce new business requirements.

If implementation reveals ambiguity, contradiction, or missing product behavior, the issue must be raised before implementation proceeds.

---

## DR-003 — Separate WHAT from HOW

Product behavior belongs to Feature Definitions and specifications.

Technology and implementation decisions belong to architecture, plans, ADRs, and code.

A specification must not become a substitute for architecture design, and an implementation plan must not redefine product behavior.

---

# 3. Test-Driven Development

## DR-004 — TDD Is Required for Deterministic Business Logic

Deterministic domain logic must be developed using a Test-Driven Development workflow:

```text
RED
 ↓
Write a failing test

GREEN
 ↓
Write the minimum implementation needed to pass

REFACTOR
 ↓
Improve design while preserving behavior
```

Examples include:

- portfolio rules;
- validation;
- valuation;
- financial calculations;
- risk formulas;
- business policies;
- recommendation rules that are deterministic;
- stop-loss formulas that are deterministic.

---

## DR-005 — Tests Must Validate Behavior

Tests must exist to validate meaningful behavior.

Tests must not be created solely to:

- increase code coverage;
- satisfy a CI threshold;
- execute lines without asserting useful outcomes.

Coverage is a quality gate, not the objective of testing.

---

## DR-006 — AI-Generated Code Follows the Same TDD Rules

AI-generated production code does not bypass TDD requirements.

Where TDD applies, the expected sequence remains:

```text
Test
 ↓
Implementation
 ↓
Refactor
```

AI agents must not generate large amounts of implementation first and add superficial tests afterwards.

---

# 4. Code Quality

## DR-007 — Keep Code Simple

Prefer the simplest implementation that satisfies the approved specification and architecture.

Avoid:

- speculative abstractions;
- unnecessary indirection;
- premature generalization;
- unused extension points;
- infrastructure introduced for hypothetical future needs.

---

## DR-008 — Small, Cohesive Units

Classes, functions, modules, and components should have clear and cohesive responsibilities.

Large units should be split when they mix unrelated responsibilities or become difficult to test and understand.

---

## DR-009 — Domain Language Must Be Preserved

Code should use terminology from:

```text
product/definition/global/glossary.md
```

Do not introduce competing names for established business concepts without an explicit product-level decision.

---

## DR-010 — Avoid Primitive Obsession Where Domain Meaning Matters

Important domain concepts should be represented explicitly where that improves correctness.

Examples may include:

- Money
- Currency
- Percentage
- Quantity
- Ticker
- MIC
- Portfolio ID
- Position ID

The exact implementation depends on the runtime and feature, but domain meaning should not be lost unnecessarily.

---

## DR-011 — Financial Values Must Use Safe Numeric Representations

Financial amounts and other precision-sensitive values must not use binary floating-point types when exact decimal behavior is required.

Use decimal-safe representations appropriate to the selected runtime.

---

## DR-012 — Nullability and Optionality Must Be Explicit

Optional business information must be represented intentionally.

Do not use null-like values ambiguously to represent:

- missing;
- unknown;
- not applicable;
- not yet calculated;
- failed.

Where those states have different business meaning, model them explicitly.

---

# 5. Architecture Compliance

## DR-013 — Hexagonal Boundaries Must Be Preserved

Implementation must comply with the architecture rules.

In particular:

- domain logic must remain infrastructure-independent;
- inbound adapters must call application use cases;
- outbound dependencies must be represented through ports;
- provider SDK models must remain in adapters.

---

## DR-014 — No Direct Cross-Module Data Access

One module must not directly query or mutate another module's owned persistence.

Interaction must occur through approved boundaries.

---

## DR-015 — Do Not Introduce Unapproved Technologies

Before adding a significant dependency or infrastructure technology, verify it against:

```text
product/architecture/technology-policy.md
```

If it is not approved, follow the architecture decision process.

AI agents must flag the need rather than silently add the dependency.

---

## DR-016 — Significant Architecture Changes Require Review

If implementation requires:

- a new service;
- a new database;
- Kafka;
- Neo4j;
- a BFF;
- a new runtime;
- a new external provider category;
- a new deployment pattern;

the architecture impact must be reviewed before implementation.

An ADR may be required.

---

# 6. API and Contract Development

## DR-017 — Contract-First for External Interfaces

Externally exposed interfaces must be defined before or alongside implementation.

For REST APIs:

```text
OpenAPI Contract
      ↓
Contract Review
      ↓
Implementation
      ↓
Contract Tests
```

---

## DR-018 — Contracts Must Use Business Language

Public API contracts must use product terminology and must not expose:

- database columns;
- ORM entities;
- provider payloads;
- framework exception structures;
- internal implementation details.

---

## DR-019 — Compatibility Must Be Intentional

Breaking contract changes must be explicit.

Do not accidentally break:

- REST consumers;
- event consumers;
- serialized persistent data;
- external integrations.

---

# 7. Error Handling

## DR-020 — Errors Must Be Explicit and Stable

Business and API errors should use stable machine-readable identifiers when exposed externally.

Error messages must explain the issue without leaking technical internals.

---

## DR-021 — Do Not Swallow Errors

Failures must not be silently ignored.

Errors should be:

- handled;
- propagated;
- translated;
- retried;
- or recorded;

according to their responsibility and boundary.

---

## DR-022 — Fail Safely

If the system cannot establish a reliable result, it should prefer an explicit failure or “cannot determine” outcome over fabricated or misleading information.

---

# 8. External Integration Development

## DR-023 — External Providers Stay Behind Adapters

External systems such as:

- market-data providers;
- news providers;
- LLM providers;
- identity providers;

must be integrated through explicit ports and adapters.

---

## DR-024 — Provider Contracts Must Be Translated

Provider-specific structures must be translated into canonical product/domain representations at the adapter boundary.

---

## DR-025 — Remote Calls Need Explicit Resilience

Remote integration code must define, as appropriate:

- timeout;
- retry;
- backoff;
- rate-limit handling;
- failure translation;
- idempotency considerations.

---

# 9. AI-Assisted Development

## DR-026 — AI Is an Implementation Assistant, Not Product Authority

AI agents may:

- formalize requirements;
- propose technical designs;
- create tests;
- generate code;
- review code;
- identify architecture violations;
- propose ADRs.

AI agents must not:

- silently change product intent;
- silently change architecture rules;
- invent business behavior;
- introduce new technology without approval;
- reinterpret ambiguous financial rules as facts.

---

## DR-027 — AI-Generated Changes Must Be Reviewable

AI-generated changes should be:

- scoped;
- traceable to an approved task;
- accompanied by meaningful tests;
- small enough to review where practical.

Avoid unrelated refactoring during feature work.

---

## DR-028 — Do Not Trust Generated Code by Default

Generated code must be verified through:

- tests;
- static analysis;
- contract validation;
- architecture checks;
- human review for material decisions.

---

# 10. Logging and Observability

## DR-029 — Use Structured Logging

Application logs must be structured and machine-readable.

Logs should include relevant correlation identifiers where available.

---

## DR-030 — Never Log Secrets or Sensitive Data

Logs must not expose:

- passwords;
- API keys;
- access tokens;
- private credentials;
- unnecessary private portfolio information;
- sensitive prompt contents.

---

## DR-031 — Trace Important Boundaries

Where observability is applicable, tracing should cover:

- inbound API calls;
- cross-component calls;
- Kafka flows;
- external providers;
- LLM interactions.

---

# 11. Configuration

## DR-032 — Configuration Must Be Externalized

Environment-dependent configuration must not be hard-coded into production code.

Examples include:

- endpoints;
- credentials;
- model identifiers;
- database connections;
- provider settings;
- feature switches.

---

## DR-033 — Secrets Must Not Be Stored in Repository Files

Secrets must not appear in:

- source code;
- committed `.env` files;
- test fixtures;
- documentation examples containing real credentials.

---

# 12. Dependencies

## DR-034 — Keep Dependencies Intentional

Every significant dependency should have a clear purpose.

Avoid adding libraries for trivial functionality that can be implemented safely and simply without them.

---

## DR-035 — Keep Dependency Direction Clean

Dependencies must not violate architecture boundaries.

For example, a domain package must not import an infrastructure package because a helper function happens to exist there.

---

## DR-036 — Remove Unused Dependencies

Unused dependencies should be removed.

They increase:

- attack surface;
- maintenance cost;
- build complexity;
- supply-chain risk.

---

# 13. Refactoring

## DR-037 — Refactoring Must Preserve Behavior

Refactoring should not silently alter product behavior.

Existing relevant tests must remain green.

---

## DR-038 — Avoid Unrelated Refactoring During Feature Work

A feature implementation should not include broad unrelated changes unless they are required to complete the feature safely.

Large refactors should be separated into their own work when practical.

---

# 14. Documentation

## DR-039 — Code Should Explain HOW, Product Docs Explain WHY/WHAT

Do not duplicate product definitions extensively inside source code.

Code documentation should focus on implementation-specific reasoning.

---

## DR-040 — Significant Decisions Must Be Documented

Use ADRs for significant architectural decisions.

Use code comments only when the reasoning cannot be expressed clearly through structure and naming.

---

# 15. Repository Hygiene

## DR-041 — Generated and Local Artifacts Must Stay Out of Git

Do not commit:

- compiled binaries;
- local environments;
- IDE state;
- local datasets;
- temporary files;
- runtime logs;
- secrets.

---

## DR-042 — Private Portfolio Data Must Not Be Used as Shared Test Data

Shared fixtures must use:

- synthetic;
- anonymized;
- or deliberately public data.

---

# 16. Repository and Vertical Slice Organization

## DR-043 — Implementation Lives Under the Platform Directory

**MANDATORY**

Executable product implementation must live under:

```text
implementation/platform/
```

Developers and AI agents must not create alternative top-level implementation trees such as `apps/`, `services/`, `src/`, `infrastructure/`, or `tests/` at repository root unless an explicit architecture decision changes the repository model.

---

## DR-044 — Every Feature Extends the Existing Platform

**MANDATORY**

Every implemented Feature Definition must extend the current executable realization of My-FinAI-Manager under `implementation/platform/`.

A feature must not create an isolated feature-specific application unless explicitly required by architecture.

After implementation, the platform must remain coherent and executable as a whole.

---

## DR-045 — Vertical Feature Boundaries and Physical Code Boundaries Are Different

**MANDATORY**

Feature Definitions provide vertical product-delivery traceability.

Physical source-code organization follows architectural and technical boundaries.

A single Feature Definition may modify frontend, backend, contracts, persistence, infrastructure, integration tests, and end-to-end tests.

Code must not be duplicated into feature-specific runtime trees merely to mirror the specification structure.

---

## DR-046 — Backend Components Live Under `backend/`

**MANDATORY**

Backend deployable components or bounded services must live under:

```text
implementation/platform/backend/
```

Initial service granularity may be coarse.

Service decomposition must evolve only when justified by scaling, maintainability, ownership, runtime, availability, security, release cadence, or operational isolation.

Each backend component must preserve Hexagonal Architecture boundaries.

---

## DR-047 — Contracts Represent the Current Platform

**MANDATORY**

Platform contracts must live under:

```text
implementation/platform/contracts/
```

Feature work may evolve these contracts, but must not create disconnected copies of the same platform contract solely inside feature-specific implementation folders.

---

## DR-048 — Infrastructure Evolves with the Platform

**MANDATORY**

Runtime and deployment infrastructure must live under:

```text
implementation/platform/infrastructure/
```

Infrastructure must be added incrementally as product capabilities require it. Speculative infrastructure remains prohibited.

---

## DR-049 — Platform Start and Stop Scripts Are the Local Entry Points

**MANDATORY**

The executable platform must expose:

```text
implementation/platform/start.sh
implementation/platform/stop.sh
```

These scripts are the canonical local interface for starting and stopping the complete platform or development environment.

They may delegate internally to other tools, but developers and AI agents must preserve these stable entry points as the implementation evolves.

---

# 17. Definition of Complete Development Work

Implementation work is not complete merely because code compiles or runs locally.

It must satisfy the applicable:

- specification;
- architecture rules;
- testing strategy;
- code-quality gates;
- contract requirements;
- observability requirements;
- documentation requirements;
- Definition of Done.

See:

```text
product/engineering/definition-of-done.md
```

---

# 18. Summary

The development model is:

```text
Human Product Intent
        ↓
Approved Specification
        ↓
Approved Architecture / Plan
        ↓
Tests First where deterministic
        ↓
Implementation
        ↓
Architecture + Contract + Quality Verification
        ↓
Human Review
```

The governing principle is:

> AI may accelerate implementation, but it does not reduce the engineering standards applied to the resulting software.
