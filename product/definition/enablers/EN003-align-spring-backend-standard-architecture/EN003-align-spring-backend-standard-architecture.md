# EN003 — Align Spring Backend with Standard Architecture

> **Status:** Approved  
> **Enabler ID:** EN003  
> **Enabler Name:** Align Spring Backend with Standard Architecture  
> **Architecture Decision:** ADR-003 — Standard Spring Backend Architecture  
> **Last Updated:** 2026-09-02

---

# 1. Purpose

Align the existing My-FinAI-Manager Spring Boot backend with the standard Spring architecture approved by ADR-002.

The enabler restructures the backend implementation without intentionally changing product behavior.

It establishes the architecture that future Spring Feature Definitions and Technical Enablers must extend.

---

# 2. Motivation

The initial backend was implemented using a valid but more generic Hexagonal Architecture interpretation.

The desired Spring engineering model is now more specific:

```text
functional module
    ├── domain
    ├── business
    └── infrastructure
```

The current implementation also contains relational persistence implemented through direct JDBC/JdbcClient and uses a build setup that must be aligned to Maven.

Without a deliberate migration, future AI-assisted development may continue reproducing the old structure.

---

# 3. Scope

## In Scope

- Preserve the current Java/Spring runtime versions unless a separately approved decision changes them.
- Reorganize backend packages by functional module.
- Within each functional module introduce:
  - `domain`;
  - `business`;
  - `infrastructure`.
- Move domain classes, enums, value objects, and domain exceptions under `domain`.
- Move all business-facing ports under `domain.ports`.
- Move business operation/use-case implementation under `business`.
- Move all adapters under `infrastructure`.
- Standardize REST adapter packages.
- Standardize messaging adapter packages when present.
- Standardize persistence adapter packages.
- Replace ordinary JdbcClient/direct-SQL Portfolio persistence with Spring Data JPA.
- Introduce persistence-specific JPA entities.
- Keep domain models free from JPA annotations.
- Introduce explicit domain ↔ persistence mapping where necessary.
- Preserve Flyway migrations as schema ownership/evolution mechanism.
- Preserve existing API contracts.
- Preserve existing business behavior.
- Preserve existing idempotency semantics and database constraints.
- Update backend Dockerfile/build commands to Maven.
- Update local container build/orchestration if build-tool paths or commands change.
- Update unit, integration, contract, and architecture tests.
- Introduce or update ArchUnit rules for the new package architecture.
- Execute existing FD001 E2E regression tests against the containerized platform.
- Update implementation documentation affected by the migration.

## Out of Scope

- New Portfolio behavior.
- New Portfolio API operations.
- Instrument-catalog behavior.
- Authentication/authorization changes.
- Kafka introduction.
- New backend services.
- Service extraction.
- Database schema redesign unrelated to JPA mapping.
- New persistence technology.
- Java/Spring major-version upgrades unless strictly required by the migration.
- Frontend behavior changes other than changes strictly required to preserve existing integration.
- New CI/CD platform or deployment topology.

---

# 4. Target Backend Structure

The current `core-service` remains one deployable backend component.

Inside its Java base package, functional modules are the primary boundary.

Conceptually:

```text
com.myfinaimanager.core
│
├── portfolio/
│   ├── domain/
│   │   ├── model/
│   │   ├── ports/
│   │   └── exceptions/
│   │
│   ├── business/
│   │   └── ...
│   │
│   └── infrastructure/
│       ├── api/
│       │   ├── rest/
│       │   │   ├── dto/
│       │   │   └── ...
│       │   └── mapper/
│       │       └── ...
│       ├── persistence/
│       │   ├── entity/
│       │   ├── repository/
│       │   ├── mapper/
│       │   └── ...
│       └── messaging/
│
└── <future-functional-module>/
    ├── domain/
    ├── business/
    └── infrastructure/
```

The exact root Java package follows the current project package namespace.

---

# 5. Dependency Rules

The target dependency direction is:

```text
infrastructure → business → domain
```

Infrastructure may depend directly on domain where required to implement ports and mapping.

Mandatory restrictions:

```text
domain       -X-> business
domain       -X-> infrastructure
business     -X-> infrastructure
```

The migration must not introduce cycles between architecture areas.

---

# 6. Domain Migration

For each functional module, `domain` must contain:

```text
domain/
├── model/
├── ports/
└── exceptions/
```

Existing domain types must be relocated without changing their externally observable business semantics.

Ports currently located under application/outbound packages must move to `domain.ports`.

Framework-specific types must not appear in port signatures.

---

# 7. Business Migration

Business operations and use-case orchestration must move under:

```text
<module>.business
```

Business classes may depend on:

- domain models;
- domain ports;
- other approved business abstractions within the same module.

They must not import infrastructure implementation.

Where Spring provides transaction management or DI, Spring annotations may remain in business classes where appropriate.

---

# 8. REST Adapter Migration

REST adapter classes must use:

```text
infrastructure.api.rest
```

REST DTOs must use:

```text
infrastructure.api.rest.dto
```

REST mapping must use:

```text
infrastructure.api.rest.mapper
```

> Updated 2026-09-02 (was `infrastructure.api.mapper`) — see the ADR-003 amendment "REST mapper
> placement". A REST mapper is part of the REST adapter.

Controllers must:

- map transport input to business/domain input;
- delegate business behavior;
- map result to REST response;
- preserve current OpenAPI contract.

Controllers must not own Portfolio business rules.

---

# 9. Persistence Migration

The Portfolio persistence implementation must migrate from direct JdbcClient/embedded SQL to Spring Data JPA.

Target structure:

```text
portfolio/
└── infrastructure/
    └── persistence/
        ├── entity/
        │   ├── PortfolioEntity.java
        │   └── PositionEntity.java
        ├── repository/
        │   └── PortfolioJpaRepository.java
        ├── mapper/
        │   └── PortfolioPersistenceMapper.java
        └── PortfolioPersistenceAdapter.java
```

The exact class names may vary if equivalent naming is clearer.

The domain persistence port belongs in:

```text
portfolio.domain.ports
```

The adapter implements that port.

The Spring Data repository extends an appropriate Spring Data JPA repository type.

Domain models must not become JPA entities.

---

# 10. JPA Mapping Requirements

JPA mapping must preserve current business and persistence behavior.

At minimum it must preserve:

- Portfolio identity;
- Investor identity;
- Portfolio name;
- Portfolio status;
- creation timestamp;
- idempotency key behavior;
- Position identity;
- `ticker + market`;
- Quantity;
- Currency;
- optional Initial Purchase Date;
- optional Average Purchase Price;
- aggregate persistence semantics;
- current relational constraints.

The migration must not weaken domain validation or database constraints.

Aggregate persistence should use JPA relationship/cascade behavior deliberately rather than relying on accidental defaults.

Fetching strategy must be explicit where it affects aggregate reconstruction or performance.

---

# 11. SQL Policy

Ordinary application persistence must no longer embed general-purpose SQL through JdbcClient/JdbcTemplate.

Spring Data JPA should use:

- derived repository methods;
- standard persistence operations;
- specifications/criteria where appropriate;
- JPQL/repository queries only when justified.

Native SQL or direct JDBC remains available only for explicitly justified exceptional cases under the architecture policy.

EN003 itself should not introduce a direct-SQL exception unless required to preserve an existing behavior that cannot reasonably be implemented with JPA.

---

# 12. Flyway

Flyway remains the owner of schema migration.

Hibernate/JPA schema auto-generation must not replace Flyway as the governed schema-evolution mechanism.

The existing schema should be reused where practical.

EN003 should not redesign tables merely to simplify JPA unless a concrete mapping incompatibility requires a schema change and that change is explicitly documented.

---

# 14. Containerization Impact

The platform remains fully containerized according to EN002.

Backend Docker build instructions must use Maven.

Existing:

```text
start.sh
stop.sh
e2e.sh
compose.yaml
```

must remain the canonical runtime/test entry points.

The migration must not restore host-based Spring execution as the canonical platform runtime.

---

# 15. Testing and Regression Safety

EN003 is a structural/technical migration.

Existing tests are regression evidence and must continue to pass.

Verification includes:

- unit tests;
- Spring integration tests;
- PostgreSQL Testcontainers integration tests;
- OpenAPI/contract verification where applicable;
- ArchUnit;
- container build;
- platform startup;
- FD001 Playwright E2E.

Tests must be adapted to package/class changes without weakening their assertions.

---

# 16. Architecture Tests

ArchUnit must verify at least:

```text
..domain..          must not depend on ..business..
..domain..          must not depend on ..infrastructure..
..business..        must not depend on ..infrastructure..
```

Where practical, tests should verify:

- Spring Data repository interfaces remain under infrastructure persistence;
- JPA entities remain under infrastructure persistence;
- REST controllers remain under infrastructure API REST;
- REST DTOs do not live in domain/business;
- domain does not depend on Spring Data or JPA;
- messaging framework classes remain under infrastructure messaging.

---

# 17. FD001 Regression Gate

FD001's required E2E Create Portfolio test is a mandatory regression gate for EN003.

Conceptually:

```text
Playwright
    ↓
Frontend
    ↓
REST
    ↓
refactored core-service
    ↓
Spring Data JPA
    ↓
PostgreSQL
```

EN003 must not be accepted or marked Completed if the FD001 Create Portfolio E2E test fails.

A passing lower-level test suite does not override this requirement.

---

# 18. Verification Criteria

## VC-001 — Maven

The Spring backend builds and tests successfully using Maven.

## VC-002 — Functional Module Structure

Existing backend business code is organized under functional-module boundaries.

## VC-003 — Standard Package Structure

Each migrated functional module uses `domain`, `business`, and `infrastructure`.

## VC-004 — Domain Independence

Domain code has no Spring Data, JPA, REST, Kafka, JDBC, or infrastructure dependencies.

## VC-005 — Ports in Domain

Business-facing external dependency ports are located under `domain.ports`.

## VC-006 — Business Dependency Direction

Business code depends on domain and does not depend on infrastructure.

## VC-007 — Adapter Placement

REST, persistence, messaging, and other adapters are placed under the standard infrastructure packages.

## VC-008 — Spring Data JPA

Portfolio relational persistence uses Spring Data JPA rather than the current JdbcClient-based repository implementation.

## VC-009 — No JPA Domain Leakage

JPA annotations and JPA entities remain in infrastructure persistence.

## VC-010 — Persistence Semantics Preserved

Current Portfolio persistence, transaction, constraints, and idempotency behavior remains functionally equivalent.

## VC-011 — Flyway Preserved

Schema evolution continues to use Flyway.

## VC-012 — ArchUnit

Architecture tests enforce the mandatory dependency direction and package constraints.

## VC-013 — Integration Tests

Applicable PostgreSQL integration tests pass using Testcontainers.

## VC-014 — Contracts

Existing REST/OpenAPI behavior remains compatible.

## VC-015 — Containers

Backend container builds successfully using the Maven-based project.

## VC-016 — Platform Lifecycle

`start.sh` and `stop.sh` continue to operate the fully containerized platform.

## VC-017 — FD001 E2E

The FD001 Create Portfolio Playwright E2E test passes against the fully containerized platform.

## VC-018 — No Product Change

No new Portfolio business behavior is introduced by EN003.

---

# 19. Documentation Impact

Implementation must update references affected by this migration, including where applicable:

- backend README;
- build instructions;
- architecture diagrams;
- developer commands;
- package examples;
- test instructions.

Human-governed architecture documents are already updated by ADR-003 and must not be silently reinterpreted during implementation.

---

# 20. Open Technical Decisions

The following implementation details may be resolved during planning as long as they remain within ADR-003:

- exact base package names after migration;
- exact JPA entity naming;
- mapper implementation style;
- precise JPA aggregate relationship mapping;
- fetch strategy;
- cascade/orphan-removal strategy;
- transaction annotation placement;
- Maven plugin configuration;
- Maven wrapper version.

These are technical implementation decisions, not permission to change the approved architecture.

Any material deviation from ADR-003 requires human approval.

---

# 21. Human Approval

Before formal specification:

- [X] Standard Spring architecture migration is approved.
- [X] Module-first package structure is approved.
- [X] `domain / business / infrastructure` structure is approved.
- [X] Ports under `domain` are approved.
- [X] Spring Data JPA migration is approved.
- [X] Domain/JPA separation is approved.
- [X] ArchUnit enforcement is approved.
- [X] Existing API/product behavior must remain unchanged.
- [X] FD001 E2E is mandatory for EN003 closure.

**Approved by:*jaruiz*  
**Date:*2026-09-02*  
**Status:** Approved
