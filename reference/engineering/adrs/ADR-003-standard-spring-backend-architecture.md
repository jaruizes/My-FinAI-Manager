# ADR-003 — Standard Spring Backend Architecture

> **Status:** Approved  
> **Date:** 2026-09-02  
> **Decision Owner:** Human Architecture Governance  
> **Scope:** Spring Boot backend components

---

## Context

My-FinAI-Manager already requires backend business-capable components to follow Hexagonal Architecture and preserve dependency direction toward the business core.

The initial architecture intentionally allowed implementation freedom around exact package organization and persistence mechanisms.

Early implementation of FD001 demonstrated that this freedom can lead AI-assisted development to select structures and technologies that are valid in isolation but do not match the team's preferred way of building Spring services, including:

- application/adapter-oriented package structures that differ from the team's standard;
- direct `JdbcClient` repositories containing hand-written SQL;
- Gradle-based Spring builds where Maven is the desired standard.

The project needs a concrete Spring architecture convention that is explicit enough to guide humans, AI agents, planning, implementation, and automated verification.

---

## Decision

Spring Boot components will use a standard three-area architecture:

```text
domain
business
infrastructure
```

The dependency direction is:

```text
infrastructure → business → domain
```

`infrastructure` may also depend directly on `domain` where required to implement ports or map domain types.

`domain` must not depend on `business` or `infrastructure`.

`business` must not depend on `infrastructure`.

---

## Domain

`domain` owns the business-facing model and ports.

Standard structure:

```text
domain/
├── model/
├── ports/
└── exceptions/
```

`domain.model` contains:

- domain classes;
- aggregates;
- entities in the domain sense;
- value objects;
- enums;
- deterministic domain behavior.

`domain.ports` contains interfaces used by business operations to reach external capabilities such as:

- persistence;
- remote services;
- market-data providers;
- messaging;
- AI providers;
- storage;
- other infrastructure boundaries.

`domain.exceptions` contains domain/business exceptions.

The domain must remain framework-independent.

It must not contain Spring, JPA/Hibernate, HTTP, Kafka, database-client, serialization, or provider-SDK dependencies.

---

## Business

`business` owns the implementation of business operations/use cases.

It:

- depends on `domain`;
- orchestrates domain behavior;
- coordinates business operations;
- uses ports from `domain`;
- owns application transaction boundaries where appropriate.

`business` must not depend on `infrastructure`.

Spring annotations may be used for runtime/application behavior where useful, provided they do not introduce infrastructure-specific coupling.

---

## Infrastructure

`infrastructure` owns adapters and framework/provider-specific implementation.

Each adapter has an explicit package boundary.

Standard packages include:

```text
infrastructure/
├── api/
│   └── rest/
│       ├── dto/
│       └── mapper/
├── persistence/
│   ├── entity/
│   ├── repository/
│   └── mapper/
├── messaging/
├── client/
└── <other-adapter>/
```

REST controllers:

```text
infrastructure.api.rest
```

REST DTOs:

```text
infrastructure.api.rest.dto
```

REST mappers:

```text
infrastructure.api.rest.mapper
```

Messaging:

```text
infrastructure.messaging
```

Persistence:

```text
infrastructure.persistence
```

---

## Modular Monolith Structure

When a Spring Boot deployable contains multiple functional modules, the code is organized module-first and architecture-second.

Example:

```text
com.myfinaimanager.core
│
├── portfolio/
│   ├── domain/
│   ├── business/
│   └── infrastructure/
│
├── financialinstrument/
│   ├── domain/
│   ├── business/
│   └── infrastructure/
│
└── valuation/
    ├── domain/
    ├── business/
    └── infrastructure/
```

A global layer-first package structure is not the standard for a modular monolith.

Each module preserves its own business and infrastructure boundary.

---

## Relational Persistence

Spring Data JPA is the standard persistence abstraction for Spring relational persistence.

The expected pattern is:

```text
domain.port
     ↑
PersistenceAdapter
     ↓
Spring Data JpaRepository
     ↓
JPA Entity
     ↓
PostgreSQL
```

JPA entities are infrastructure models.

Domain models must not contain JPA annotations.

Spring Data repository interfaces belong under infrastructure.

Hand-written SQL, `JdbcClient`, `JdbcTemplate`, direct JDBC, or native queries are conditional.

They may be used only when a concrete technical need justifies bypassing normal Spring Data JPA usage, for example:

- measured performance requirements;
- specialized bulk operations;
- database-specific features;
- complex read models where ORM produces a materially worse design.

Any exception must remain isolated in infrastructure and should be visible during architecture review.

Flyway remains the schema-evolution mechanism.

---

## Build Tool

Maven is the standard and required build tool for Spring Boot components.

Expected project files include:

```text
pom.xml
mvnw
mvnw.cmd
.mvn/
```

Gradle is not the default approved build tool for Spring components after this ADR.

Existing Gradle Spring components may be migrated when an approved enabler includes that migration.

---

## Architecture Verification

The architecture should be verified automatically using ArchUnit.

At minimum:

```text
domain          must not depend on business
domain          must not depend on infrastructure
business        must not depend on infrastructure
```

Additional package-placement rules should verify adapter conventions where practical.

---

## Alternatives Considered

### Keep the generic Hexagonal package structure

Rejected because it leaves too much implementation freedom and repeatedly requires humans to correct AI-generated structure.

### Use Spring/JPA annotations directly in domain models

Rejected because it couples the canonical domain model to persistence infrastructure.

### Use direct JDBC as the default persistence mechanism

Rejected because it is not the desired Spring development standard and creates unnecessary hand-written persistence code for ordinary CRUD/aggregate persistence.

### Continue using Gradle

Rejected for Spring components because Maven is the team's desired standard and improves consistency across generated services.

---

## Consequences

### Positive

- consistent Spring service structure;
- easier navigation between services and modules;
- architecture becomes easier for AI agents to follow;
- stronger separation of domain/business/infrastructure;
- Spring Data JPA removes unnecessary embedded SQL for common persistence operations;
- Maven becomes predictable across Spring components;
- ArchUnit can enforce key rules automatically;
- modular monolith modules remain independently understandable and extractable.

### Negative / Trade-offs

- existing Spring code may require significant package refactoring;
- migrating Gradle to Maven has short-term cost;
- JPA introduces ORM behavior that must be understood and tested;
- explicit mapping between domain and persistence models adds code;
- direct SQL remains necessary for some specialized workloads and therefore cannot be completely forbidden.

---

## Migration

The current My-FinAI-Manager Spring backend will be aligned through:

```text
EN003 — Align Spring Backend with Standard Architecture
```

EN003 will migrate the existing backend structure without intentionally changing product behavior.

Existing feature acceptance and E2E tests remain mandatory regression gates.

---

## Supersedes / Updates

This ADR makes the exact Spring package structure prescriptive where the previous architecture only required conceptual Hexagonal Architecture.

It updates the implementation interpretation of:

- `architecture.md`;
- `architecture-rules.md`;
- `technology-policy.md`.

---

## Amendment — 2026-09-02 (REST mapper placement)

**Change:** REST request/response mapping code moves from `infrastructure.api.mapper` to
`infrastructure.api.rest.mapper`.

**Rationale:** A transport mapper that maps REST DTOs to/from business/domain types is part of the
**REST adapter**, so it belongs under `infrastructure.api.rest` alongside the controller and
`dto/` — not in a separate `api`-level sibling package. `api/` in this structure is REST-scoped:
messaging adapters already live at `infrastructure.messaging`, not `infrastructure.api.messaging`.
A future non-REST API adapter would introduce its own `infrastructure.api.<style>` subtree with its
own mapper package.

**Effect:** `architecture.md` and `architecture-rules.md` (AR-057, AR-058) are updated to match.
The standard `api/` subtree is now:

```text
infrastructure/
└── api/
    └── rest/
        ├── dto/
        └── mapper/
```

Implemented by the `portfolio` module (EN003); enforced by
`StandardArchitectureRulesTest.rest_mappers_live_in_infrastructure_api_rest_mapper`.

**Approved by:** jaruiz · 2026-09-02.

