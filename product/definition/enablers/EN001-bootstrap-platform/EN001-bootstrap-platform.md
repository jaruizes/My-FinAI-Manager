# EN001 — Bootstrap Executable Platform

> **Status:** Completed  
> **Enabler ID:** EN001  
> **Enabler Name:** Bootstrap Executable Platform  
> **Last Updated:** 2026-09-01

---

# 1. Purpose

Create the minimum executable technical foundation required to implement and run My-FinAI-Manager product features.

This enabler establishes the first runnable platform baseline.

It does not introduce business functionality.

---

# 2. Scope

## In Scope

- Create the Angular frontend base.
- Create the initial Spring Boot backend deployable component.
- Establish the base Hexagonal Architecture structure in the backend.
- Configure PostgreSQL as the initial relational persistence technology.
- Create the local Docker Compose environment required by the platform.
- Create the initial contracts structure.
- Add a base OpenAPI location for external business contracts.
- Provide basic backend health endpoints.
- Configure the integration-test foundation using Testcontainers.
- Provide canonical platform lifecycle scripts:
    - `start.sh`
    - `stop.sh`
- Ensure the complete local platform can be started and stopped through those scripts.
- Establish a minimal project structure that future vertical Feature Definitions can extend.

## Out of Scope

- Portfolio business functionality.
- Kafka.
- Neo4j.
- Python backend services.
- LLM integration.
- MCP.
- Kubernetes.
- Production cloud infrastructure.
- Production CI/CD.
- Market-data integration.
- News-provider integration.
- Authentication and authorization beyond what is strictly necessary to bootstrap the platform.
- Advanced observability backends.

These capabilities may be introduced later when justified by Feature Definitions or additional Technical Enablers.

---

# 3. Expected Repository Result

After EN001, the executable platform should contain at least:

```text
implementation/
└── platform/
    ├── backend/
    │   └── core-service/
    │
    ├── contracts/
    │   └── openapi/
    │
    ├── frontend/
    │   └── web/
    │
    ├── infrastructure/
    │   └── local/
    │       └── compose.yaml
    │
    ├── start.sh
    └── stop.sh
```

Exact framework-generated internal folders may differ.

---

# 4. Initial Technology Constraints

The enabler must use technologies already approved by the project technology policy.

Initial implementation:

- Angular
- TypeScript
- Java
- Spring Boot
- PostgreSQL
- Docker / Docker Compose
- OpenAPI
- Testcontainers
- Hexagonal Architecture

No additional major technology should be introduced without architecture review.

---

# 5. Backend Baseline

The initial backend must be created under:

```text
implementation/platform/backend/core-service/
```

The backend must follow Hexagonal Architecture principles.

Its internal structure should preserve separation between:

- domain;
- application;
- inbound adapters;
- outbound adapters.

The bootstrap implementation should avoid inventing business modules that are not yet required.

A minimal technical structure is sufficient.

---

# 6. Frontend Baseline

The initial Angular application must be created under:

```text
implementation/platform/frontend/web/
```

The frontend should establish:

- application bootstrap;
- base application shell;
- routing foundation;
- global styling integration point;
- location for shared design tokens/components when they emerge.

The frontend does not need to implement product-specific screens as part of EN001.

---

# 7. Contract Baseline

External business contracts must live under:

```text
implementation/platform/contracts/
```

For REST APIs, the initial location should include:

```text
implementation/platform/contracts/openapi/
```

The enabler may include a minimal OpenAPI document or placeholder structure required to validate the contract-first workflow.

It must not invent product endpoints that are not yet defined by a Feature Definition.

---

# 8. Persistence Baseline

PostgreSQL is the initial relational database.

The local environment must provide a PostgreSQL instance suitable for:

- local execution;
- backend connectivity;
- future migrations;
- integration testing.

No business schema needs to be introduced beyond what is technically required to prove connectivity.

---

# 9. Local Infrastructure

Local infrastructure must live under:

```text
implementation/platform/infrastructure/local/
```

Docker Compose should be used to orchestrate required local infrastructure.

For EN001, PostgreSQL is the only mandatory infrastructure dependency.

The environment must remain intentionally minimal.

---

# 10. Platform Lifecycle

The repository must provide:

```text
implementation/platform/start.sh
implementation/platform/stop.sh
```

## `start.sh`

Must act as the canonical local entry point for starting the executable platform or development environment.

It should start, directly or indirectly:

- required local infrastructure;
- backend;
- frontend.

## `stop.sh`

Must act as the canonical local entry point for stopping the complete local environment.

The internal implementation of these scripts may evolve, but their role as stable developer entry points should remain.

---

# 11. Integration Testing Baseline

The backend must include at least one integration test using Testcontainers.

The test must validate integration with PostgreSQL using a real disposable PostgreSQL container.

The test must not depend on a manually installed local PostgreSQL instance.

This establishes the baseline required by the project's testing strategy.

---

# 12. Health Verification

The backend must expose a simple health endpoint or equivalent framework health mechanism.

The verification should prove that:

- the backend process starts;
- the application runtime is healthy;
- PostgreSQL connectivity can be established where applicable.

No complex readiness or liveness policy is required yet.

---

# 13. Architecture Constraints

EN001 must comply with:

```text
product/architecture/architecture.md
product/architecture/technology-policy.md
product/architecture/architecture-rules.md
```

In particular:

- backend code follows Hexagonal Architecture;
- infrastructure does not define the domain;
- no speculative distributed architecture is introduced;
- no Kafka or Neo4j is added without a concrete requirement;
- external contracts remain separate from implementation details;
- the platform remains vendor-neutral where applicable.

---

# 14. Verification Criteria

EN001 is complete when all applicable conditions below are satisfied.

## VC-001 — Frontend Starts

The Angular frontend starts successfully in the local development environment.

## VC-002 — Backend Starts

The Spring Boot backend starts successfully.

## VC-003 — PostgreSQL Starts

PostgreSQL starts successfully through the local platform infrastructure.

## VC-004 — Backend Connectivity

The backend can establish connectivity with PostgreSQL.

## VC-005 — Health Endpoint

The backend health endpoint responds successfully.

## VC-006 — Testcontainers

At least one backend integration test executes successfully against PostgreSQL using Testcontainers.

## VC-007 — Platform Start

Running:

```bash
./implementation/platform/start.sh
```

starts the complete local platform or development environment.

## VC-008 — Platform Stop

Running:

```bash
./implementation/platform/stop.sh
```

stops the complete local platform or development environment.

## VC-009 — No Business Functionality Invented

The bootstrap does not introduce unapproved Portfolio or financial behavior.

---

# 15. Expected Follow-Up

After EN001 is complete, the platform should be ready to implement the first vertical product feature:

```text
FD001 — Create Investment Portfolio
```

FD001 should extend the existing executable platform rather than recreate technical foundations.

---

# 16. Architecture Decision Dependency

EN001 depends on the approval of:

```text
ADR-001 — Initial Backend Topology
```

This ADR defines the initial backend deployment strategy and service granularity.

---

# 17. Human Approval

Before implementation:

- [X] Scope is correct.
- [X] Out-of-scope items are correct.
- [X] Initial technology choices are approved.
- [X] Verification criteria are sufficient.
- [X] ADR-001 is approved.
- [X] No speculative infrastructure has been introduced.

**Approved by:*jaruiz*  
**Date:*2026-09-01*  
**Status:** Approved
