# ADR-001 — Initial Backend Topology

> **Status:** Approved  
> **Decision ID:** ADR-001  
> **Date:** 2026-08-31  

---

# Context

My-FinAI-Manager is starting implementation with a repository structure that separates:

```text
product/
specs/
implementation/platform/
```

The platform architecture allows both:

- Modular Monolith approaches;
- independently deployable backend services.

The exact long-term deployment topology is intentionally not fixed.

The first technical enabler, `EN001 — Bootstrap Executable Platform`, requires an initial backend topology so that the executable platform can be created consistently.

The platform is expected to grow incrementally through vertical Feature Definitions.

Future features may eventually introduce capabilities with different:

- scalability needs;
- runtime requirements;
- maintenance characteristics;
- availability needs;
- release cadence;
- operational isolation;
- ownership boundaries.

However, none of those needs have yet been demonstrated.

Creating multiple independently deployable backend services at project bootstrap would therefore introduce distributed-system complexity before there is a concrete requirement.

---

# Decision

My-FinAI-Manager will initially use **one coarse-grained Spring Boot backend deployable component** under:

```text
implementation/platform/backend/core-service/
```

The initial backend will behave as a **modular monolith / coarse-grained backend service**.

It may implement several functional domains within the same deployable unit.

Internal functional capabilities must still preserve explicit boundaries and follow Hexagonal Architecture.

Conceptually:

```text
core-service
│
├── capability/module A
│   ├── domain
│   ├── application
│   └── adapters
│
├── capability/module B
│   ├── domain
│   ├── application
│   └── adapters
│
└── ...
```

A functional domain does not automatically become an independently deployable service.

---

# Rationale

This decision is based on the following considerations.

## Lower Initial Operational Complexity

A single backend deployable unit avoids introducing:

- service discovery;
- distributed tracing complexity;
- inter-service network failure modes;
- distributed transactions;
- multiple deployment pipelines;
- additional container orchestration;
- unnecessary API boundaries.

---

## Faster Domain Evolution

The product domain is still evolving.

Keeping related capabilities in one deployable unit makes it easier to:

- refine boundaries;
- refactor concepts;
- evolve the information model;
- move responsibilities between modules;
- discover the correct service boundaries.

---

## Architecture Boundaries Are Still Preserved

A modular monolith does not imply an unstructured monolith.

The backend must still preserve:

- Hexagonal Architecture;
- clear capability boundaries;
- explicit ownership;
- no direct cross-module persistence access;
- inward dependency direction.

These boundaries make future extraction possible.

---

## Avoid Premature Distribution

Independent services should be introduced because a concrete quality attribute requires them, not because microservices are considered the default architecture style.

---

# Consequences

## Positive Consequences

- Simpler initial development environment.
- Easier local execution.
- Lower deployment complexity.
- Easier integration testing.
- Faster refactoring while domain boundaries evolve.
- Lower infrastructure cost.
- Fewer distributed-system failure modes.
- Easier implementation of the first vertical Feature Definitions.

---

## Negative Consequences

- All initial backend capabilities share one deployment lifecycle.
- Independent scaling is not initially available.
- A defect in the backend process may affect several capabilities.
- Runtime choice is initially shared by capabilities implemented inside `core-service`.

These trade-offs are accepted for the initial stage of the project.

---

# Service Extraction Criteria

A capability may later be extracted from `core-service` into an independently deployable backend service when one or more concrete needs arise.

Examples include:

- independent scalability;
- significantly different CPU or memory profile;
- different runtime or technology requirements;
- independent deployment cadence;
- availability isolation;
- security isolation;
- long-running or asynchronous workloads;
- operational ownership;
- maintainability benefits that outweigh distribution cost.

Service extraction requires explicit architecture impact analysis.

A significant extraction should normally be documented through a new ADR.

---

# Relationship with Functional Domains

Functional domains defined in:

```text
product/definition/global/domains.md
```

represent business responsibility.

They do not define deployment boundaries.

Several functional domains may initially coexist inside `core-service`.

Conversely, a future service may support more than one functional domain if architecture requirements justify that grouping.

---

# Relationship with `implementation/platform/backend/`

The initial structure will be:

```text
implementation/platform/backend/
└── core-service/
```

Future evolution may produce:

```text
implementation/platform/backend/
├── core-service/
├── market-intelligence-service/
├── risk-service/
└── ...
```

only when justified.

The existence of the `backend/` parent directory should therefore not be interpreted as a requirement to create many services immediately.

---

# Alternatives Considered

## Alternative A — Multiple Microservices from the Beginning

Example:

```text
portfolio-service
valuation-service
risk-service
recommendation-service
market-intelligence-service
```

### Rejected for Initial Implementation

Reasons:

- no demonstrated independent scaling requirements;
- domain boundaries are still evolving;
- higher operational complexity;
- greater local development complexity;
- unnecessary synchronous/asynchronous integration decisions;
- increased testing and deployment overhead.

---

## Alternative B — Single Unstructured Backend Application

One Spring Boot application without enforced internal modular boundaries.

### Rejected

Reasons:

- would make future service extraction harder;
- encourages cross-domain coupling;
- conflicts with architecture rules;
- weakens domain ownership;
- would turn a simple initial topology into an architectural dead end.

---

## Alternative C — Coarse-Grained Modular Backend

One Spring Boot deployable unit with explicit internal modular and Hexagonal Architecture boundaries.

### Selected

This provides the lowest operational complexity while preserving future architectural evolution.

---

# Reversibility

This decision is intentionally reversible.

The architecture is designed so that a bounded capability may later be extracted into a separate service.

Extraction should not require redesigning the product model.

Ports, adapters, contracts, and clear data ownership should reduce the migration cost.

---

# Validation

The decision is considered successfully applied when:

- the initial backend is deployed as one Spring Boot component;
- internal capabilities preserve explicit boundaries;
- Hexagonal Architecture is respected;
- no independent service is created without a concrete architectural justification;
- EN001 can bootstrap the platform without distributed-system infrastructure.

---

# Decision Summary

```text
Initial State

implementation/platform/backend/
└── core-service/
        ↓
coarse-grained
modular
hexagonal
single deployable unit

Future

Observed Quality Attribute Need
        ↓
Architecture Impact Analysis
        ↓
New ADR if significant
        ↓
Extract Capability
        ↓
Independent Service
```

The project therefore starts simple without sacrificing the ability to evolve.
