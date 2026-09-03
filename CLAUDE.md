# CLAUDE.md

## Purpose

This file defines how Claude Code must operate inside the My-FinAI-Manager repository.

It is an execution adapter for the repository's human-governed product, architecture, engineering, and governance rules.

It is **not** the source of truth for product intent or architecture.

Claude must always treat the documents under `product/` as authoritative.

---

# 1. Repository Authority

Before performing specification, planning, implementation, refactoring, testing, or architecture work, read the relevant documents under:

```text
product/
├── definition/
├── architecture/
├── engineering/
└── governance/
```

Authority order:

```text
1. Human-approved product intent under product/
2. Human-approved architecture and ADRs
3. Human-approved Feature Definitions / Enabler Definitions
4. Formal SDD specifications
5. Implementation plans
6. Tasks
7. Existing code
```

If lower-level artifacts conflict with higher-level artifacts, stop and surface the conflict.

Do not silently resolve conflicts by changing product intent.

---

# 2. Human-Governed Source of Truth

`product/` is human-governed.

Claude may:

- analyze;
- challenge;
- identify ambiguity;
- propose alternatives;
- suggest architecture options;
- draft specifications;
- draft ADRs;
- draft plans;
- draft tasks;
- implement approved work;
- create tests;
- update documentation when required.

Claude must not silently:

- invent product requirements;
- introduce new business rules;
- change product scope;
- redefine terminology;
- alter approved acceptance criteria;
- change architecture decisions;
- introduce technologies;
- create new deployment boundaries;
- weaken testing requirements.

Material product or architecture decisions require explicit human approval.

---

# 3. Product Definitions

Product definitions live under:

```text
product/definition/
```

Relevant artifact types include:

```text
global/
features/
enablers/
epics/          # if introduced
```

## Feature Definitions

Feature Definitions use identifiers such as:

```text
FD001-create-investment-portfolio
```

They represent product capabilities delivered vertically.

A Feature Definition is authoritative for:

- purpose;
- scope;
- business rules;
- user behavior;
- acceptance criteria;
- information concepts;
- relevant business events;
- explicit product decisions.

Do not infer requirements that are not present.

If a requirement is ambiguous and materially affects behavior, ask for clarification or record it explicitly as an open question.

## Technical Enablers

Technical Enablers use identifiers such as:

```text
EN001-bootstrap-platform
```

They represent technical work that enables product delivery but is not itself a user-facing product capability.

Do not force artificial user stories or business behavior into Technical Enablers.

---

# 4. SDD Framework Role

Spec Kit or any future SDD framework is replaceable tooling.

The framework must consume human-defined intent; it must not become the source of product truth.

Derived SDD artifacts belong under:

```text
specs/
```

Examples:

```text
specs/FD001-create-investment-portfolio/
specs/EN001-bootstrap-platform/
```

Generated specifications, plans, tasks, checklists, and related artifacts must remain traceable to their source Feature Definition or Technical Enabler.

Do not move human-governed product definitions into framework-owned directories.

---

## Specification Context Rules

Before generating or formalizing any specification, including work initiated through Spec Kit commands such as `/speckit.specify`, Claude must automatically read and comply with:

```text
CLAUDE.md
.specify/memory/constitution.md

product/definition/global/
product/architecture/
product/engineering/
product/governance/
product/definition/global/ux/design-system.md
```

Claude must also read:

- the target Feature Definition or Technical Enabler;
- all applicable ADRs under `product/architecture/adrs/`;
- any feature-specific UX artifacts associated with the target Feature Definition.

For Product Feature specifications:

- Treat the target Feature Definition as the authoritative source of product intent.
- Preserve its purpose, scope, business rules, acceptance criteria, information objects, relevant business events, and explicit product decisions.
- Do not invent additional product behavior.
- Do not introduce technical behavior that is not required by the approved Feature Definition.
- Respect explicit out-of-scope items defined by the Feature Definition.
- Extend the existing executable platform under `implementation/platform/`.
- Respect the approved backend topology and applicable ADRs.
- Use the existing `core-service` unless an approved architecture decision explicitly introduces another deployable component.
- External REST behavior must follow contract-first development and be represented in OpenAPI.
- Persistence must follow the project's approved PostgreSQL and data-ownership policies.
- Backend business logic must follow Hexagonal Architecture.
- Applicable integration tests against PostgreSQL or other application-managed infrastructure must use Testcontainers according to project policy.
- Frontend behavior must follow the global design system and any approved feature-specific UX artifacts.
- If material ambiguity remains, surface it explicitly instead of making an assumption.

For Technical Enabler specifications:

- Treat the target Enabler Definition as the authoritative technical intent.
- Do not force artificial investor-facing user stories or business functionality into the enabler.
- Do not introduce product behavior, business APIs, schemas, technologies, infrastructure, or deployment boundaries beyond the approved enabler scope.
- Surface material technical decisions that require human approval rather than silently choosing them.

These rules are repository-wide defaults. They do not need to be repeated in every `/speckit.specify` prompt.

A normal Feature Definition invocation may therefore be concise, for example:

```text
/speckit.specify

Create the formal SDD specification for:

FD001-create-investment-portfolio

Authoritative source:
product/definition/features/FD001-create-investment-portfolio/feature-definition.md
```

---


# 5. Implementation Location

All executable product implementation belongs under:

```text
implementation/platform/
```

Current expected structure:

```text
implementation/platform/
├── backend/
├── contracts/
├── frontend/
├── infrastructure/
├── start.sh
└── stop.sh
```

Do not create alternative root-level implementation trees such as:

```text
apps/
services/
src/
backend/
frontend/
infrastructure/
```

unless an approved architecture decision explicitly changes the repository model.

---

# 6. Vertical Delivery Rule

Each implemented Feature Definition must extend the existing executable platform.

A feature may modify:

- frontend;
- backend;
- contracts;
- persistence;
- infrastructure;
- tests.

A feature must not create an isolated feature-specific application.

Feature boundaries provide delivery traceability.

Physical code organization follows architecture and technical boundaries.

These concepts are related but are not identical.

---

# 7. Backend Architecture

All business backend components must follow Hexagonal Architecture.

Dependencies must point inward.

Business/domain code must not depend directly on:

- Spring;
- FastAPI;
- Kafka;
- PostgreSQL;
- Neo4j;
- AWS SDKs;
- OpenAI SDKs;
- Anthropic SDKs;
- infrastructure frameworks.

Keep business logic out of:

- controllers;
- REST adapters;
- database adapters;
- SQL;
- framework configuration;
- LLM prompts.

Use explicit ports and adapters for external dependencies.

---

# 8. Initial Backend Topology

Respect the currently approved backend topology ADR.

The initial backend is expected to be:

```text
implementation/platform/backend/core-service/
```

with one coarse-grained Spring Boot deployable unit.

It must preserve explicit internal boundaries.

Do not create one microservice per functional domain.

A new independently deployable service requires concrete justification and the architecture/ADR process defined by the project.

---

# 9. Technology Policy

Before selecting or introducing technology, read:

```text
product/architecture/technology-policy.md
```

Use only approved technologies according to their policy status.

Do not introduce major libraries, infrastructure products, frameworks, databases, messaging technologies, or cloud services because they are convenient.

Examples:

- PostgreSQL is the preferred relational database.
- Kafka is conditional and requires a concrete asynchronous/event-streaming need.
- Neo4j is conditional and requires a graph-semantic use case.
- Dedicated vector databases require architecture review.
- LLM providers must remain behind provider-neutral ports/adapters.

Avoid speculative infrastructure.

---

# 10. Deterministic Logic vs AI

Deterministic financial and business calculations must remain deterministic.

AI/LLM components may:

- explain;
- summarize;
- classify;
- reason;
- interpret;
- recommend where explicitly allowed.

AI/LLM components must not replace deterministic calculations when deterministic implementation is possible and required.

AI output must not automatically be treated as fact.

Important conclusions must preserve evidence, provenance, confidence, or uncertainty where required by product definitions.

---

# 11. Contracts

External REST APIs must follow contract-first development.

REST contracts belong under:

```text
implementation/platform/contracts/openapi/
```

OpenAPI is required for REST APIs.

Contracts must use business language rather than persistence or framework terminology.

Do not expose internal database models as API contracts.

Compatibility changes must be intentional.

Business events do not automatically imply Kafka, topics, or AsyncAPI.

---

# 12. Persistence

Data ownership must be explicit.

Do not access another module's owned persistence directly.

Shared physical PostgreSQL infrastructure does not imply shared domain ownership.

Avoid dual writes across independent persistence technologies.

Database schema changes must use the project's approved migration approach.

---

# 13. Testing

Read and follow:

```text
product/engineering/testing-strategy.md
product/engineering/development-rules.md
product/engineering/definition-of-done.md
```

TDD is required for deterministic domain/business logic:

```text
RED
GREEN
REFACTOR
```

Tests must validate behavior rather than exist only to increase coverage.

## Testcontainers

For integration tests against application-managed infrastructure, Testcontainers is required by default whenever a suitable containerized dependency exists.

Examples:

- PostgreSQL;
- Kafka;
- Neo4j;
- compatible identity infrastructure.

Do not require developers or CI to manually install infrastructure for integration tests.

Mocks/stubs remain appropriate for true external providers where live calls introduce:

- cost;
- nondeterminism;
- rate limits;
- privacy concerns;
- reliability issues.

Examples include:

- external market-data providers;
- news providers;
- LLM APIs;
- SaaS APIs.

---

# 14. Architecture Verification

Where practical, add architecture conformance tests / fitness functions.

Examples:

- dependency direction;
- forbidden framework dependencies in domain code;
- module boundary enforcement;
- package/import constraints.

For Java, ArchUnit may be used when consistent with the technology policy.

---

# 15. Errors and Resilience

Errors must be explicit.

Do not:

- swallow exceptions;
- expose stack traces to users;
- return infrastructure terminology in business APIs;
- silently ignore failed persistence or integration operations.

External adapters should apply appropriate timeout, retry, and failure-handling policies when justified.

Do not add resilience complexity without a concrete need.

---

# 16. Observability

Use structured logging.

Never log:

- secrets;
- passwords;
- tokens;
- private credentials;
- sensitive portfolio data unnecessarily.

OpenTelemetry is the preferred observability direction.

Do not introduce observability infrastructure beyond what the current feature/enabler requires.

---

# 17. Security and Secrets

Never hard-code secrets.

Configuration must be externalized.

Production/deployed environments must use approved external secret management.

Do not commit:

- API keys;
- passwords;
- private certificates;
- tokens;
- personal portfolio information used as real test data.

Use synthetic test data.

---

# 18. UX and Design System

Before implementing frontend UI, read:

```text
product/definition/global/ux/design-system.md
```

Feature-specific UX artifacts, when available, live with the Feature Definition:

```text
product/definition/features/<feature-id>/ux/
```

Global design rules govern:

- visual language;
- colors;
- typography;
- layout;
- navigation;
- reusable visual patterns.

Feature UX governs the concrete interaction.

Visual references do not introduce business functionality.

If a prototype conflicts with the Feature Definition, the Feature Definition wins and the conflict must be surfaced.

---

# 19. Business Events

Business events defined under:

```text
product/definition/global/business-events.md
```

are semantic business concepts.

They do not automatically require:

- Kafka;
- message brokers;
- topics;
- AsyncAPI;
- event sourcing.

The technical communication mechanism is an architecture decision.

---

# 20. ADRs

Architecture decisions live under:

```text
product/architecture/adrs/
```

Read applicable ADRs before planning or implementing affected work.

Create or propose an ADR when a change materially affects:

- backend topology;
- service extraction;
- data ownership;
- persistence technology;
- messaging architecture;
- external API architecture;
- security model;
- major technology adoption;
- deployment/runtime architecture;
- significant operational characteristics.

Do not create ADRs for trivial implementation details.

Do not silently contradict an approved ADR.

---

# 21. Planning Rules

A plan describes HOW approved intent will be implemented.

Before creating a plan:

1. Read the source Feature Definition or Technical Enabler.
2. Read applicable global product definitions.
3. Read architecture documents and ADRs.
4. Read engineering rules.
5. Identify architecture impact.
6. Surface unresolved material decisions.

Do not use planning to invent product behavior.

Prefer the simplest architecture that satisfies current requirements.

---

# 22. Task Rules

Tasks must:

- be concrete;
- be implementable;
- be reviewable;
- be traceable to the plan;
- avoid hidden scope;
- include required tests;
- include contract/documentation changes when applicable.

Do not create speculative tasks for future capabilities.

---

# 23. Implementation Rules

During implementation:

- work only within approved scope;
- preserve repository structure;
- keep the platform executable;
- extend the existing platform;
- follow TDD;
- update tests with behavior changes;
- update contracts when APIs change;
- update relevant documentation when implementation changes architecture or behavior;
- keep changes cohesive.

Do not perform unrelated broad refactoring during feature implementation unless explicitly approved.

---

# 24. Platform Lifecycle

These files are canonical local lifecycle entry points:

```text
implementation/platform/start.sh
implementation/platform/stop.sh
```

When implementation changes the executable platform, ensure these scripts remain valid or update them accordingly.

Do not introduce a separate undocumented way of starting the platform that supersedes these entry points.

---

# 25. Current Initial Work

The first technical enabler is:

```text
product/definition/enablers/EN001-bootstrap-platform/enabler-definition.md
```

The applicable architecture decision is:

```text
product/architecture/adrs/ADR-001-initial-backend-topology.md
```

The first product feature is:

```text
product/definition/features/FD001-create-investment-portfolio/feature-definition.md
```

EN001 must bootstrap the executable platform before FD001 is implemented.

Do not implement FD001 business behavior as part of EN001.

---

# 26. Ambiguity Policy

The highest-risk failure mode is turning a plausible assumption into an apparently official requirement.

Therefore:

If a decision materially affects:

- user-visible behavior;
- business rules;
- data semantics;
- architecture;
- persistence ownership;
- service boundaries;
- technology;
- security;
- public contracts;

do not guess.

Instead:

1. identify the ambiguity;
2. explain its impact;
3. propose options if useful;
4. request human approval;
5. update the authoritative artifact after approval.

Safe, reversible, non-material implementation details may be chosen without unnecessary escalation.

---

# 27. Completion

A task is not complete merely because code compiles or tests pass.

Before declaring work complete, verify applicable requirements from:

```text
product/engineering/definition-of-done.md
```

At minimum ensure:

- approved scope is satisfied;
- architecture rules are respected;
- required tests pass;
- contracts are consistent;
- documentation is current;
- no unapproved technology was introduced;
- no product requirement was invented;
- platform lifecycle remains functional.

---

# 28. Claude Working Principle

Use this operating model:

```text
Human defines intent
        ↓
Claude reads authoritative context
        ↓
Claude challenges / clarifies
        ↓
Human approves material decisions
        ↓
Claude specifies / plans
        ↓
Claude implements with tests
        ↓
Claude verifies against source intent
```

Claude is an engineering assistant.

It is not the product owner and it is not the architecture authority.
