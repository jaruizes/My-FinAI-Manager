# My-FinAI-Manager — Architecture Rules

## Purpose

This document defines the architectural rules that all My-FinAI-Manager components must follow.

These rules translate the high-level architecture and technology policy into concrete, reviewable constraints for design and implementation.

They apply regardless of whether a capability is implemented with:

- Spring Boot;
- Python;
- Angular;
- a Modular Monolith;
- an independently deployable service;
- synchronous APIs;
- asynchronous messaging;
- or AI-assisted components.

The rules are human-governed architectural constraints.

AI agents may propose changes to them, but must not silently override or bypass them.

---

# Rule Categories

Each rule is classified as one of:

- **MANDATORY** — must be followed unless an ADR explicitly approves an exception.
- **CONDITIONAL** — applies when the described architectural situation exists.
- **RECOMMENDED** — preferred practice; deviations should be justified when material.

---

# 1. Architecture Style

## AR-001 — Hexagonal Architecture

**MANDATORY**

Every backend business-capable component must follow Hexagonal Architecture principles.

Business logic must be isolated from infrastructure through explicit ports and adapters.

A component must conceptually separate the business core from infrastructure through explicit ports and adapters.

For Spring Boot components, this principle is implemented through the mandatory package model:

```text
domain
business
infrastructure
```

For a modular monolith, this structure exists inside each functional module.

The concrete dependency and package rules are defined by AR-053 through AR-061.

---

## AR-002 — Dependencies Point Inward

**MANDATORY**

Infrastructure must depend on the business core, not the other way around.

For Spring Boot components, the allowed dependency direction is:

```text
infrastructure → business → domain
```

`infrastructure` may also depend directly on `domain` when implementing or mapping domain ports and types.

`domain` must not depend on `business` or `infrastructure`.

`business` must not depend on `infrastructure`.

The Domain Layer must not depend directly on:

- Spring;
- FastAPI;
- Kafka;
- PostgreSQL;
- Neo4j;
- AWS SDKs;
- OpenAI SDKs;
- Anthropic SDKs;
- HTTP clients;
- persistence frameworks;
- serialization frameworks;
- UI concerns.

Provider-specific and framework-specific types must not become part of the canonical domain model.

---

## AR-003 — Domain Logic Must Be Explicit

**MANDATORY**

Core business rules and deterministic financial calculations must live in domain or application code rather than in:

- controllers;
- persistence adapters;
- frontend components;
- Kafka consumers/producers;
- SQL stored procedures;
- LLM prompts;
- provider-specific workflows.

Infrastructure must not become the hidden owner of business behavior.

---

# 2. Component Boundaries

## AR-004 — Functional Domains Do Not Automatically Become Services

**MANDATORY**

A functional domain defined in `product/definition/global/domains.md` must not automatically become:

- a microservice;
- a database;
- a Kafka topic;
- a deployment unit.

Technical boundaries are architectural decisions.

---

## AR-005 — Prefer Explicit Module Boundaries

**MANDATORY**

Whether the platform starts as a Modular Monolith or evolves into several services, business capabilities must have explicit boundaries.

A module or component must clearly define:

- what it owns;
- which use cases it exposes;
- which data it owns;
- which dependencies it requires;
- which other components it may call.

---

## AR-006 — No Cross-Module Persistence Access

**MANDATORY**

A component must not read or write another component's persistence representation directly.

Examples of forbidden coupling include:

- querying another module's relational tables directly;
- modifying another component's records;
- reading another module's Neo4j representation as if it were owned locally.

Cross-boundary access must occur through an explicit application interface, API, event, or approved shared contract.

---

## AR-007 — Shared Database Does Not Mean Shared Ownership

**MANDATORY**

If several modules initially use the same PostgreSQL instance, logical ownership boundaries must still be preserved.

A shared database instance must not be used as justification for bypassing module contracts.

---

## AR-008 — Service Extraction Requires Justification

**MANDATORY**

A module may become an independently deployable service only when there is a concrete reason such as:

- independent scaling;
- independent availability;
- distinct resource profile;
- different runtime needs;
- independent release cadence;
- operational isolation;
- security isolation;
- team ownership;
- materially different latency characteristics.

Extraction should normally be documented by an ADR.

---

# 3. External API Boundary

## AR-009 — External Business API Is the Platform Entry Point

**MANDATORY**

External clients must access platform capabilities through explicit external business contracts.

Clients must not depend directly on:

- internal module interfaces;
- internal service topology;
- database structures;
- Kafka topics;
- internal AI providers;
- internal workflow engines.

---

## AR-010 — External APIs Expose Business Capabilities

**MANDATORY**

External APIs must model product capabilities, not infrastructure.

API resources and operations should use domain language from the product glossary.

Provider payloads and persistence structures must not leak through public contracts.

---

## AR-011 — REST Contracts Must Be OpenAPI-Defined

**MANDATORY**

Externally exposed REST APIs must be defined through OpenAPI.

The API contract should be established before implementation when practical.

Contract changes must preserve compatibility intentionally.

---

## AR-012 — Stable Error Contracts

**MANDATORY**

External APIs must provide stable machine-readable error identification.

Human-readable messages may evolve or be localized independently from error codes.

The external error model must not expose raw framework exceptions, SQL errors, provider responses, or stack traces.

---

# 4. Frontend Rules

## AR-013 — Frontend Is Decoupled

**MANDATORY**

The Angular frontend must interact only through approved frontend-facing contracts.

It must not:

- query databases;
- consume Kafka directly;
- invoke LLM providers directly;
- depend on backend persistence models;
- replicate critical backend business rules.

---

## AR-014 — BFF Is Optional, Not Default

**MANDATORY**

A BFF may be introduced only when frontend-specific orchestration or adaptation provides concrete value.

The BFF must not become the owner of core business logic.

It may:

- aggregate responses;
- adapt payloads for frontend consumption;
- coordinate frontend-specific calls;
- handle frontend session concerns.

It must not own portfolio rules, risk rules, valuation logic, or recommendation policy.

---

# 5. Synchronous and Asynchronous Communication

## AR-015 — Choose Interaction Style Deliberately

**MANDATORY**

Synchronous or asynchronous communication must be selected based on the needs of the use case.

Do not use asynchronous messaging merely because Kafka is available.

Do not use synchronous chaining when temporal decoupling or independent processing is clearly required.

---

## AR-016 — Kafka Requires a Concrete Reason

**MANDATORY**

Kafka may be introduced only when it provides one or more concrete benefits such as:

- temporal decoupling;
- durable event distribution;
- multiple independent consumers;
- buffering;
- independent scaling;
- event streaming;
- integration with external event sources.

The existence of a business event in `business-events.md` does not imply Kafka publication.

---

## AR-017 — Business Events and Technical Events Are Different

**MANDATORY**

A business event represents a meaningful fact in the domain.

A technical integration event is an architectural representation used for communication.

The two must not be treated as automatically equivalent.

When a business event is exposed technically, the architecture must define:

- producer;
- consumers;
- contract;
- schema;
- versioning;
- compatibility;
- delivery semantics;
- observability.

---

## AR-018 — Event Consumers Must Be Idempotent Where Required

**CONDITIONAL**

Consumers of durable asynchronous events must support safe reprocessing when delivery semantics can produce duplicates.

Idempotency strategy must be explicit for business-critical operations.

---

## AR-019 — Avoid Distributed Transactions

**MANDATORY**

The architecture should not rely on distributed ACID transactions across independently deployable components.

When workflows span boundaries, use appropriate patterns such as:

- local transactions;
- idempotency;
- eventual consistency;
- transactional outbox;
- compensating actions;
- durable workflows.

The exact pattern must be justified by the use case.

---

# 6. Data Ownership and Persistence

## AR-020 — Data Ownership Must Be Explicit

**MANDATORY**

Every persistent business information set must have a clear owning component.

Ownership includes responsibility for:

- business invariants;
- authoritative updates;
- lifecycle;
- schema evolution;
- external access.

---

## AR-021 — PostgreSQL Is the Default Relational Store

**RECOMMENDED**

Relational business state should use PostgreSQL unless another storage model provides a clear advantage.

Introducing another relational database requires an architectural decision.

---

## AR-022 — Specialized Databases Need Specialized Value

**MANDATORY**

Neo4j, search engines, vector databases, caches, or other specialized persistence technologies must only be introduced when their workload provides concrete value.

They must not be added preemptively.

---

## AR-023 — Neo4j Ownership Mode Must Be Explicit

**CONDITIONAL**

When Neo4j is introduced, the architecture must explicitly state whether the graph is:

- authoritative;
- a derived projection;
- an analytical representation.

The same business data must not have ambiguous ownership across PostgreSQL and Neo4j.

---

## AR-024 — Avoid Accidental Dual Writes

**MANDATORY**

Business operations must not independently update multiple persistence technologies without an explicit consistency strategy.

If the same business change must be reflected in PostgreSQL and another store, the architecture must define how consistency is maintained.

---

## AR-025 — External Provider Data Is Not the Domain Model

**MANDATORY**

Market-data, news, AI, or other provider schemas must be translated at adapter boundaries.

The product owns its canonical information model.

Provider changes must not require redesigning the core domain model.

---

# 7. AI and LLM Rules

## AR-026 — LLM Providers Must Be Behind Ports

**MANDATORY**

Application and domain logic must interact with LLM capabilities through provider-neutral outbound ports.

Provider SDKs must remain inside adapters.

---

## AR-027 — No LLM Vendor Lock-In in Business Logic

**MANDATORY**

Business logic must not depend on:

- AWS Bedrock-specific model structures;
- OpenAI-specific response structures;
- Anthropic-specific message formats;
- Vertex AI-specific orchestration constructs.

Provider-specific optimizations may exist only inside provider adapters or clearly isolated infrastructure components.

---

## AR-028 — Deterministic Logic Stays Deterministic

**MANDATORY**

LLMs must not perform calculations that can be implemented deterministically and reliably.

Examples that should remain deterministic include:

- portfolio valuation;
- position weight;
- concentration percentage;
- exchange-rate calculation;
- deterministic validation;
- deterministic risk formulas;
- stop-loss formulas when explicitly algorithmic.

LLMs may explain or reason over deterministic results.

---

## AR-029 — AI Output Is Not Automatically a Fact

**MANDATORY**

AI-generated output must be distinguishable from:

- verified facts;
- observed market data;
- deterministic calculations.

AI-generated interpretations, classifications, or recommendations must preserve their semantic nature.

---

## AR-030 — Important AI Conclusions Require Evidence

**MANDATORY**

Material AI-assisted conclusions must be traceable to supporting Evidence where practical.

Examples include:

- investment recommendations;
- risk interpretations;
- thesis assessments;
- news relevance assessments;
- portfolio impact conclusions.

---

## AR-031 — AI Provider Calls Must Be Observable

**MANDATORY**

LLM interactions must provide enough telemetry to understand:

- provider;
- model;
- latency;
- failures;
- token/cost information when available and useful;
- correlation with the originating business operation.

Sensitive prompt content must not be exposed through observability data.

---

# 8. Vendor Lock-In

## AR-032 — External Systems Are Replaceable Boundaries

**MANDATORY**

External systems and managed providers must be isolated behind explicit boundaries.

This principle applies to:

- LLM providers;
- market-data providers;
- news providers;
- identity providers;
- cloud services;
- storage services;
- notification providers.

---

## AR-033 — Prefer Open Standards

**RECOMMENDED**

Where applicable, prefer standards such as:

- OpenAPI;
- AsyncAPI;
- OAuth 2.0;
- OpenID Connect;
- OpenTelemetry;
- OCI;
- ISO financial/reference standards;
- MCP where agent interoperability provides value.

---

## AR-034 — Vendor-Neutrality Must Not Create Fake Abstractions

**MANDATORY**

Avoiding vendor lock-in does not justify speculative abstraction layers for every library or framework.

Create replaceable boundaries primarily at external or architecturally significant dependencies.

Do not create meaningless interfaces solely to wrap stable internal libraries.

---

# 9. Security and Privacy

## AR-035 — Private Portfolio Data Must Be Protected

**MANDATORY**

Portfolio and Investor data must be treated as private information.

Components must only access the information required for their responsibility.

---

## AR-036 — External Data Sharing Must Be Minimized

**MANDATORY**

When sending information to an external AI or data provider, only the minimum required information should be transmitted.

Private portfolio details must not be included automatically when they are not needed.

---

## AR-037 — Secrets Stay Outside Source Code

**MANDATORY**

Credentials, tokens, certificates, and secrets must not be committed to source control.

They must be supplied through approved secret-management mechanisms.

---

## AR-038 — Authorization Is Enforced Backend-Side

**MANDATORY**

Authorization decisions concerning Investor-owned resources must be enforced by backend components.

Frontend checks may improve usability but are not a security boundary.

---

# 10. Observability

## AR-039 — Deployable Components Must Be Observable

**MANDATORY**

Deployable backend components must provide appropriate:

- structured logs;
- metrics;
- traces where useful.

OpenTelemetry is the preferred instrumentation standard.

---

## AR-040 — Correlation Must Cross Boundaries

**MANDATORY**

Business operations should preserve correlation across:

- external API requests;
- internal synchronous calls;
- Kafka flows;
- external provider calls;
- AI/LLM calls.

---

## AR-041 — Business-Critical Decisions Must Be Auditable

**MANDATORY**

Important business outcomes should retain enough information to reconstruct why they were produced.

This is especially relevant for:

- Recommendations;
- Stop-Loss Recommendations;
- Risk Assessments;
- Portfolio Reviews;
- AI-assisted interpretations.

Auditability does not require storing every transient technical detail.

---

# 11. Resilience

## AR-042 — External Failures Must Not Corrupt Domain State

**MANDATORY**

Failures from external providers must be handled at integration boundaries.

An LLM, market-data, news, or other provider failure must not leave authoritative business state partially corrupted.

---

## AR-043 — Timeouts Must Be Explicit

**MANDATORY**

Remote calls must have explicit timeout behavior.

Components must not rely indefinitely on external dependencies.

---

## AR-044 — Retry Policy Must Be Deliberate

**MANDATORY**

Retries must be applied only when the operation is safe to retry.

Retry behavior must consider:

- idempotency;
- provider rate limits;
- backoff;
- failure amplification.

---

## AR-045 — Graceful Degradation Should Be Considered

**RECOMMENDED**

When practical, a temporary failure in a non-critical external capability should not make unrelated platform capabilities unavailable.

Example:

A temporary news-provider outage should not necessarily prevent the Investor from viewing their Portfolio.

---

# 12. Architecture Evolution

## AR-046 — No Speculative Infrastructure

**MANDATORY**

Do not introduce:

- Kafka;
- Kubernetes;
- Neo4j;
- Redis;
- OpenSearch;
- multiple services;
- workflow engines;
- additional databases;

until a concrete requirement or quality attribute justifies them.

---

## AR-047 — Prefer Evolution Over Prediction

**MANDATORY**

The architecture should optimize for safe evolution rather than attempting to predict every future requirement.

Intentional boundaries should allow components to be extracted or replaced later.

---

## AR-048 — Significant Architecture Changes Require ADRs

**MANDATORY**

Significant decisions must be recorded through ADRs.

Examples include:

- Modular Monolith vs distributed services;
- selecting the initial backend runtime;
- introducing Kafka;
- introducing Neo4j;
- introducing a BFF;
- extracting a service;
- introducing a new database;
- adopting a significant cloud-specific capability.

---

## AR-049 — Architecture Documentation Must Reflect Current Reality

**MANDATORY**

Architecture diagrams and documents must be updated when approved architecture changes.

Documentation must not describe speculative future components as if they already existed.

---

# 13. Architecture Conformance

## AR-050 — Architecture Rules Should Be Automatable Where Practical

**RECOMMENDED**

Rules should be enforced automatically when suitable mechanisms exist.

Examples:

- ArchUnit for Java dependency rules;
- Python import/dependency checks;
- OpenAPI validation;
- AsyncAPI validation;
- architecture tests;
- CI dependency checks;
- forbidden dependency scanning.

Manual review should not be the only protection against detectable architecture violations.

---

## AR-051 — AI-Generated Code Must Follow the Same Rules

**MANDATORY**

Code generated by AI agents is subject to exactly the same architectural constraints as human-written code.

AI generation is not an exception mechanism.

---

## AR-052 — Architecture Violations Must Be Explicit

**MANDATORY**

If a Feature Definition or implementation plan cannot comply with an existing architecture rule, the conflict must be surfaced before implementation.

The valid options are:

```text
Change the design
       or
Create an explicit ADR / approved rule change
```

Silently violating an architecture rule is not acceptable.

---

# 14. Spring Backend Architecture

## AR-053 — Standard Spring Package Structure

**MANDATORY**

Every Spring Boot business-capable component must use three primary architectural package areas:

```text
domain
business
infrastructure
```

For an independently deployable service, these packages exist directly below the service base package.

For a modular monolith, they exist below each functional-module package.

An ADR is required to adopt a materially different package architecture.

---

## AR-054 — Modular Monolith Is Module-First

**MANDATORY**

When one Spring Boot deployable contains multiple functional modules, package organization must be:

```text
<base-package>.<functional-module>.domain
<base-package>.<functional-module>.business
<base-package>.<functional-module>.infrastructure
```

and not a global layer-first structure such as:

```text
<base-package>.domain.<all-modules>
<base-package>.business.<all-modules>
<base-package>.infrastructure.<all-modules>
```

Functional modules remain the primary code ownership boundary.

---

## AR-055 — Domain Package Owns Model and Ports

**MANDATORY**

A Spring functional module's `domain` package contains:

- `model` for domain classes, value objects, enums, aggregates, and related domain concepts;
- `ports` for interfaces required by the business core to interact with external capabilities;
- `exceptions` for domain/business exceptions.

Ports must use domain-oriented types.

Ports must not expose:

- REST DTOs;
- JPA entities;
- Kafka records;
- database-specific types;
- provider SDK types;
- framework-specific request/response types.

---

## AR-056 — Business Package Owns Business Operations

**MANDATORY**

The `business` package contains business-operation and use-case implementation.

It:

- depends on `domain`;
- invokes domain behavior;
- coordinates operations;
- uses ports defined in `domain` to access persistence, remote services, messaging, AI providers, and other external capabilities.

`business` must not depend on `infrastructure`.

Infrastructure implementation details must not be embedded in business services.

---

## AR-057 — Infrastructure Contains Adapters

**MANDATORY**

All adapters and provider/framework-specific implementation belong under `infrastructure`.

Each adapter must have an explicit package.

Standard Spring adapter package conventions are:

```text
infrastructure.api.rest
infrastructure.api.rest.dto
infrastructure.api.rest.mapper
infrastructure.persistence
infrastructure.messaging
```

Additional adapter types may use dedicated packages such as:

```text
infrastructure.client
infrastructure.templating
infrastructure.storage
infrastructure.ai
```

when required.

---

## AR-058 — REST Adapter Package Convention

**MANDATORY**

REST controllers belong under:

```text
infrastructure.api.rest
```

REST request/response DTOs belong under:

```text
infrastructure.api.rest.dto
```

REST-specific mapping code (DTO ⇄ business command / domain type) belongs under:

```text
infrastructure.api.rest.mapper
```

A REST mapper is part of the REST adapter, so it lives inside `infrastructure.api.rest` — not in a
separate `infrastructure.api`-level sibling package. `api/` is REST-scoped in this convention;
messaging adapters use `infrastructure.messaging`, not `infrastructure.api.messaging`. (Amended
2026-09-02 alongside ADR-003.)

REST DTOs must not become domain models.

Controllers must delegate business behavior to the `business` layer.

---

## AR-059 — Messaging Adapter Package Convention

**MANDATORY**

Messaging producers, consumers, message DTOs, serialization concerns, and broker-specific implementation belong under:

```text
infrastructure.messaging
```

or its child packages.

Kafka or other broker-specific types must not cross into `domain`.

Message consumers must delegate business behavior rather than own it.

---

## AR-060 — Spring Data JPA Is the Standard Relational Persistence Adapter

**MANDATORY for Spring relational persistence**

Spring Boot components using relational business persistence must use Spring Data JPA as the default persistence abstraction.

Persistence code belongs under:

```text
infrastructure.persistence
```

Recommended child packages are:

```text
entity
repository
mapper
```

JPA entities are infrastructure models.

Domain model classes must not be annotated as JPA entities.

A persistence adapter may implement a port from `domain` and delegate storage operations to one or more Spring Data `JpaRepository` interfaces.

Hand-written SQL, `JdbcClient`, `JdbcTemplate`, native queries, or direct JDBC are CONDITIONAL and require an explicit technical justification.

Flyway or the approved schema-migration mechanism remains responsible for schema evolution.

---

## AR-061 — Spring Architecture Must Be Automatically Checked

**MANDATORY where ArchUnit can enforce the rule**

Spring components must include architecture tests that verify at least:

```text
domain          does not depend on business
domain          does not depend on infrastructure
business        does not depend on infrastructure
```

Architecture tests should additionally verify the standard package placement for REST, messaging, and persistence adapters where practical.

Architecture violations must fail automated verification unless an approved ADR documents an exception.

---

# Architecture Rule Summary

The architecture can be summarized through the following constraints:

```text
                     External Clients
                            │
                            ▼
                   Business API Contract
                            │
                     Optional BFF
                            │
                            ▼
              ┌────────────────────────┐
              │    Inbound Adapter     │
              └────────────┬───────────┘
                           ▼
              ┌────────────────────────┐
              │   Application Layer    │
              └────────────┬───────────┘
                           ▼
              ┌────────────────────────┐
              │      Domain Layer      │
              │                        │
              │ Deterministic business │
              │ logic and rules        │
              └────────────┬───────────┘
                           │
                    Outbound Ports
                           │
        ┌──────────────────┼───────────────────┐
        ▼                  ▼                   ▼
   PostgreSQL          Kafka*           External Providers
      Adapter          Adapter             Adapters
        │                                     │
        │                               Market / News / LLM
        │
      Neo4j*

* only when justified
```

Core constraints:

```text
Business logic does not depend on infrastructure.

Modules do not bypass each other's boundaries.

External providers do not define the domain model.

LLMs do not replace deterministic calculations.

Business events do not automatically imply Kafka.

Functional domains do not automatically imply microservices.

Specialized infrastructure requires demonstrated value.

Architecture changes are explicit, traceable, and preferably testable.
```
