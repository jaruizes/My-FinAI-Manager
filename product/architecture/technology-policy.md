# My-FinAI-Manager — Technology Policy

## Purpose

This document defines the technology policy for My-FinAI-Manager.

Its purpose is to constrain the solution space used by humans and AI agents when designing and implementing product capabilities.

The policy defines:

- technologies that are preferred;
- technologies that are allowed;
- technologies that are conditional;
- technologies that require an Architecture Decision Record (ADR);
- technologies that are not approved by default;
- general rules for introducing or replacing technologies.

This document complements `architecture.md`.

It does not define feature behavior.

---

# Policy Categories

Each technology is classified using one of the following statuses.

## REQUIRED

The technology or standard must be used for the applicable concern unless an ADR explicitly approves an exception.

## PREFERRED

The technology is the default choice for the applicable concern.

A different approved option may be used when there is a concrete justification.

## ALLOWED

The technology is approved and may be selected when it fits the capability.

Its use must still be justified by the feature and architecture.

## CONDITIONAL

The technology may only be introduced when a concrete technical or product need justifies it.

Its presence in the approved stack does not mean it should be used by default.

## ADR REQUIRED

The technology or capability may be introduced, but its use requires an explicit Architecture Decision Record.

## NOT APPROVED

The technology must not be introduced without first changing this policy through an explicit architectural decision.

---

# Technology Selection Principles

Technology choices must follow these principles:

1. Prefer the simplest technology that satisfies current requirements.
2. Do not introduce infrastructure solely because it belongs to the approved enterprise stack.
3. Avoid duplicate technologies that solve the same concern without a strong reason.
4. Avoid provider-specific concepts leaking into the business domain.
5. Prefer open standards and portable contracts.
6. Minimize vendor lock-in.
7. Prefer technologies already approved by this policy.
8. Introducing a technology outside this policy requires architectural review.
9. Introducing a second runtime, database, broker, or major infrastructure component requires justification.
10. Technology choices must preserve Hexagonal Architecture boundaries.
11. Technology decisions should be reversible where practical.
12. Operational complexity is an architectural cost and must be considered explicitly.

---

# Approved Technology Matrix

| Concern | Technology / Standard | Status | Notes |
|---|---|---|---|
| Web Frontend | Angular | PREFERRED | Default web frontend framework |
| Frontend Language | TypeScript | REQUIRED | Applies to Angular frontend code |
| Backend Runtime | Java | ALLOWED | Primary runtime for Spring-based capabilities |
| Backend Framework | Spring Boot | ALLOWED | Preferred Java backend framework |
| Backend Runtime | Python | ALLOWED | Approved for AI, data, analytics, and backend capabilities |
| Python API Framework | FastAPI | ALLOWED | Preferred option when Python exposes HTTP APIs |
| External Business API | REST | PREFERRED | Default interaction style for external business capabilities |
| API Contract | OpenAPI | REQUIRED for REST APIs | REST interfaces must be contract-defined |
| Asynchronous Messaging | Kafka | CONDITIONAL | Use only when asynchronous decoupling is justified |
| Event Contract | AsyncAPI | PREFERRED when events are externally exposed | Recommended for published asynchronous contracts |
| Relational Database | PostgreSQL | PREFERRED | Default relational persistence |
| Graph Database | Neo4j | CONDITIONAL | Use when graph traversal or semantic relationships justify it |
| Vector Search | PostgreSQL + pgvector | PREFERRED initial option | Avoid separate vector infrastructure unless justified |
| Dedicated Vector Database | Provider-specific / specialized | ADR REQUIRED | Introduce only when PostgreSQL/pgvector is insufficient |
| Full-Text Search | PostgreSQL | PREFERRED initial option | Dedicated search engine requires justification |
| Search Engine | OpenSearch / Elasticsearch | ADR REQUIRED | Use for advanced search workloads when justified |
| Cache | Redis-compatible technology | CONDITIONAL | Introduce only for demonstrated latency/load needs |
| LLM Provider | AWS Bedrock | ALLOWED | May be initial provider but must be abstracted |
| LLM Provider | OpenAI | ALLOWED | Must be accessed through provider abstraction |
| LLM Provider | Anthropic | ALLOWED | Must be accessed through provider abstraction |
| LLM Provider | Google Vertex AI | ALLOWED | Must be accessed through provider abstraction |
| Agent / LLM Integration | Provider-neutral application ports | REQUIRED | Domain/application logic must not depend directly on provider SDKs |
| MCP | Model Context Protocol | CONDITIONAL | Use when exposing or consuming agent tools provides value |
| Observability | OpenTelemetry | PREFERRED | Vendor-neutral telemetry standard |
| Distributed Tracing | OpenTelemetry | PREFERRED | Applies when cross-component traces exist |
| Metrics | OpenTelemetry-compatible | PREFERRED | Backend vendor remains replaceable |
| Logging | Structured logging | REQUIRED | Machine-readable structured logs |
| Containers | OCI-compatible containers | PREFERRED | Docker-compatible tooling acceptable |
| Authentication | OAuth 2.0 / OpenID Connect | PREFERRED | Default identity standards |
| Authorization | Application/domain authorization policies | REQUIRED | Exact framework may vary |
| Infrastructure as Code | Terraform | ALLOWED | Preferred when cloud infrastructure is required |
| Deployment | Kubernetes | CONDITIONAL | Introduce when deployment/operational needs justify it |
| CI/CD | GitHub Actions | ALLOWED | Default for the repository unless context changes |
| Secrets | External secret management | REQUIRED for deployed environments | Secrets must not be committed |
| Test Containers | Testcontainers | REQUIRED for applicable integration tests | Mandatory by default for integration tests against application-managed infrastructure when a suitable container exists |
| Schema Migration | Flyway / equivalent | PREFERRED for relational DBs | Exact tool follows runtime choice |

---

# Frontend Policy

## Angular

Angular is the preferred framework for the web frontend.

Frontend implementation must:

- use TypeScript;
- consume explicit business API contracts;
- avoid direct knowledge of backend persistence;
- avoid direct communication with databases or infrastructure;
- avoid direct communication with Kafka;
- avoid direct dependency on external AI providers;
- avoid embedding core business rules that belong to backend domains.

A BFF may be introduced if justified by frontend-specific needs.

---

# Backend Policy

## Spring Boot

Spring Boot is approved for backend capabilities.

It is particularly appropriate for:

- transactional business capabilities;
- API-heavy services;
- domain-centric applications;
- integration-heavy backend components;
- workloads requiring mature Java ecosystem support.

Spring-based components must still follow Hexagonal Architecture.

Framework annotations and infrastructure concerns must not invade the domain layer unnecessarily.

---

## Python

Python is approved for backend and analytical capabilities.

It is particularly appropriate for:

- AI integration;
- agent workflows;
- data processing;
- quantitative analytics;
- experimentation;
- financial or machine-learning libraries.

Python components must follow the same Hexagonal Architecture and testing rules as Spring components.

Python must not automatically be selected merely because a capability uses AI.

---

## Runtime Selection

A capability may use Spring Boot or Python.

The choice should consider:

- domain complexity;
- ecosystem fit;
- operational characteristics;
- performance needs;
- team familiarity;
- dependency requirements;
- AI/data library requirements;
- maintenance cost.

The project should avoid introducing both runtimes unless there is concrete value.

If both runtimes are introduced, their boundaries must be explicit.

---

# API Policy

## External Business APIs

REST is the preferred default for externally exposed business APIs.

REST APIs must:

- be described with OpenAPI;
- use business-oriented resource and operation semantics;
- expose stable error contracts;
- avoid leaking persistence models;
- avoid leaking external-provider payloads;
- support explicit versioning when compatibility requires it.

The external API should remain stable even if internal topology changes.

---

## Internal APIs

Internal interfaces may use:

- in-process application interfaces;
- HTTP APIs;
- asynchronous messaging;
- scheduled workflows;
- other approved mechanisms.

Internal APIs must not be introduced merely to simulate service boundaries inside a Modular Monolith.

---

# Messaging and Eventing Policy

## Kafka

Kafka is approved but conditional.

Kafka should be introduced when one or more of the following are true:

- temporal decoupling provides clear value;
- multiple independent consumers react to the same business event;
- workloads require buffering;
- processing is long-running or asynchronous;
- event streaming is a first-class requirement;
- independent scaling is required;
- integration with external event producers or consumers is required.

Kafka should not be introduced:

- only because business events exist;
- only because Kafka is part of the approved stack;
- to replace simple in-process communication without benefit;
- to create artificial microservice boundaries.

When externally meaningful event contracts are published, AsyncAPI should be considered.

Schema versioning and compatibility rules must be explicit.

---

# Persistence Policy

## PostgreSQL

PostgreSQL is the preferred default persistence technology.

Use PostgreSQL for:

- transactional business state;
- portfolios;
- positions;
- financial instrument references;
- reviews;
- recommendations;
- configuration;
- relational analytical data where appropriate.

PostgreSQL should be considered before introducing another persistence technology.

---

## pgvector

If semantic vector retrieval is required, PostgreSQL with pgvector should be evaluated before introducing a dedicated vector database.

A dedicated vector database requires an ADR when:

- scale exceeds reasonable PostgreSQL capabilities;
- retrieval latency requires specialization;
- operational isolation provides concrete value;
- provider-specific functionality materially improves the product.

---

## Neo4j

Neo4j is approved conditionally.

It should be considered for capabilities involving:

- multi-hop graph traversal;
- relationship-heavy semantic models;
- company/sector/geography/risk relationships;
- dependency graphs;
- portfolio exposure graphs;
- GraphRAG-oriented retrieval where graph structure adds measurable value.

Neo4j must not duplicate relational state without an explicit ownership or projection strategy.

The architecture must define whether Neo4j is:

- authoritative;
- derived/projection-based;
- or analytical-only.

That choice requires an ADR when Neo4j is introduced.

---

## Search Technologies

PostgreSQL should be evaluated first for simple full-text search.

OpenSearch or Elasticsearch may be introduced only when justified by:

- advanced search requirements;
- large document volumes;
- complex ranking;
- log/search workloads;
- semantic/hybrid retrieval needs that exceed PostgreSQL capabilities.

Their introduction requires an ADR.

---

# AI and LLM Policy

## Provider Independence

No domain or application capability may depend directly on a specific LLM provider.

Provider access must occur through explicit outbound ports.

Example:

```text
Application
    │
    ▼
LLM Port
    │
    ├── AWS Bedrock Adapter
    ├── OpenAI Adapter
    ├── Anthropic Adapter
    └── Vertex AI Adapter
```

Provider-specific SDKs belong in adapters.

---

## Allowed Providers

Initially approved providers include:

- AWS Bedrock;
- OpenAI;
- Anthropic;
- Google Vertex AI.

Other providers require architectural review before introduction.

---

## AWS Bedrock

AWS Bedrock may be used as an initial provider where appropriate.

Its use must not result in core business logic depending directly on:

- Bedrock-specific request models;
- Bedrock-specific identifiers;
- proprietary workflow constructs;
- AWS-only orchestration concepts.

Provider-specific optimizations may exist inside adapters.

---

## Model Selection

The architecture must not assume that one model is always used.

Model choice may vary by capability based on:

- quality;
- cost;
- latency;
- context length;
- availability;
- data handling requirements.

Model routing may be introduced when justified.

---

## Deterministic Boundary

LLMs must not replace deterministic calculations where a deterministic implementation is available.

Examples:

```text
Portfolio valuation       → deterministic
Position weight            → deterministic
Concentration calculation  → deterministic
Currency conversion        → deterministic

News summarization         → AI allowed
Entity extraction          → AI allowed
Semantic relevance         → AI allowed
Recommendation explanation → AI allowed
```

AI-generated recommendations may depend on deterministic results, but must not fabricate those calculations.

---

# MCP Policy

Model Context Protocol is allowed conditionally.

MCP may be used when it provides a useful interoperable boundary for agents to:

- query external capabilities;
- access portfolio capabilities;
- retrieve market information;
- retrieve news;
- expose reusable tools.

MCP must not be used as a replacement for ordinary internal domain interfaces without a concrete reason.

Internal deterministic business logic does not need to become an MCP server.

---

# Vendor Lock-In Policy

Avoiding unnecessary vendor lock-in is mandatory.

The following practices are required where applicable:

- domain models remain provider-neutral;
- external providers are isolated through ports and adapters;
- contracts prefer open standards;
- persistence ownership remains explicit;
- provider-specific SDK types do not cross adapter boundaries;
- cloud-specific services should have a documented replacement strategy when they become architecturally significant.

Vendor-neutrality does not mean avoiding useful managed services.

It means ensuring that a provider can be replaced without redesigning the business domain.

---

# Observability Policy

OpenTelemetry is the preferred observability standard.

Deployable backend components should support:

- structured logs;
- metrics;
- traces where meaningful.

Trace context should propagate across:

- synchronous calls;
- asynchronous Kafka flows;
- external provider calls;
- AI/LLM calls where possible.

Observability backends may change without changing application instrumentation.

---

# Security and Identity Policy

OAuth 2.0 and OpenID Connect are the preferred identity standards.

Security implementation should support:

- authenticated users;
- authorization of Investor-owned resources;
- least privilege;
- secure secret management;
- TLS for network communication;
- external provider credential isolation.

Security-sensitive business logic must not be delegated solely to the frontend.

---

# Container and Deployment Policy

## Containers

OCI-compatible containers are preferred for deployable backend components.

Container images must:

- be reproducible;
- avoid embedded secrets;
- use supported runtime versions;
- minimize unnecessary packages.

---

## Kubernetes

Kubernetes is conditional.

It should be introduced when justified by needs such as:

- multiple independently deployable components;
- scaling;
- workload scheduling;
- resilience;
- operational standardization.

A Modular Monolith does not require Kubernetes merely because Kubernetes is available.

---

# Infrastructure as Code

Terraform is approved for infrastructure provisioning.

Infrastructure as Code should be introduced when infrastructure becomes non-trivial or needs reproducibility across environments.

Cloud-specific Terraform modules are allowed, but provider lock-in implications should be visible.

---

# CI/CD Policy

GitHub Actions is approved as the default CI/CD platform for this repository.

CI pipelines should eventually validate:

- build;
- unit tests;
- integration tests;
- architecture tests;
- code coverage;
- static analysis;
- dependency checks;
- contract validation;
- container build where applicable.

Detailed engineering quality gates belong in `product/engineering/`.

---

# Testing Technology Policy

Test technology must support the engineering testing strategy.

## Testcontainers

Testcontainers is required by default for integration tests against infrastructure owned or operated by the application whenever a suitable containerized dependency is available.

Typical examples include:

- PostgreSQL;
- Kafka;
- Neo4j;
- identity infrastructure;
- other application-managed dependencies with reliable container images.

Integration tests must not depend on infrastructure manually installed on the developer workstation or CI runner.

When the purpose of a test is to verify infrastructure integration, that infrastructure must not be replaced by a mock merely to simplify the test.

Mocks and stubs remain appropriate for external providers such as market-data APIs, news APIs, and LLM providers when live calls would introduce cost, instability, rate limits, nondeterminism, or privacy concerns.

If Testcontainers cannot reasonably be used for an applicable dependency, the exception must be documented in the implementation plan or relevant engineering decision.

Preferred testing approaches include:

## Spring / Java

- JUnit 5
- AssertJ
- Mockito where isolation is appropriate
- Testcontainers for infrastructure integration (required by default when applicable)
- architecture testing tools such as ArchUnit where useful

## Python

- pytest
- Testcontainers for infrastructure integration (required by default when applicable)
- appropriate mocking tools for external boundaries

## Frontend

- Angular-compatible unit testing tools
- component testing
- end-to-end testing when required

Tool choice must not weaken the project's TDD and coverage requirements.

---

# Technology Introduction Process

When a feature appears to need a technology not already approved:

```text
Feature Requirement
      ↓
Can approved stack satisfy it?
      │
      ├── Yes ─────► Use approved technology
      │
      └── No
            ↓
      Evaluate alternatives
            ↓
      Architecture Decision
            ↓
           ADR
            ↓
      Update technology-policy.md
            ↓
      Implementation
```

An AI agent must not introduce an unapproved technology silently.

---

# Technology Replacement Process

Replacing a significant technology requires evaluation of:

- business impact;
- migration impact;
- data migration;
- operational impact;
- contract compatibility;
- observability;
- security;
- rollback strategy.

Significant replacements should be captured in an ADR.

---

# Version Policy

Major technology versions should be pinned or constrained explicitly in implementation repositories.

This document does not define exact versions unless a version becomes an architectural constraint.

Version selection should favor:

- actively supported releases;
- security support;
- ecosystem compatibility;
- operational stability.

---

# Current Technology Position Summary

```text
Frontend
  Angular + TypeScript

External API
  REST + OpenAPI

Backend
  Spring Boot
  or
  Python / FastAPI

Persistence
  PostgreSQL
      │
      ├── pgvector if semantic vectors are needed
      └── Neo4j when graph use cases justify it

Messaging
  Kafka when asynchronous decoupling is justified

AI / LLM
  Provider-neutral port
      ├── AWS Bedrock
      ├── OpenAI
      ├── Anthropic
      └── Vertex AI

Observability
  OpenTelemetry

Identity
  OAuth 2.0 / OpenID Connect

Deployment
  OCI containers
  Kubernetes only when justified

Infrastructure
  Terraform when required
```

This policy intentionally defines an approved solution space rather than prescribing that every listed technology must appear in the implementation.
