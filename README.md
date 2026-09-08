# My-FinAI-Manager

**My-FinAI-Manager** is a personal investment portfolio intelligence platform and an experiment in **human-governed, AI-assisted software delivery**.

The product is designed to help an investor understand, evaluate, and continuously manage one or more investment portfolios by combining deterministic financial analysis with market data, external information, semantic context, and AI-assisted reasoning.

The engineering model is equally important: **human-defined intent is authoritative**, while specifications, plans, tasks, implementation, and AI-assisted delivery are derived from that intent and validated before human acceptance.

---

## Product Vision

My-FinAI-Manager is intended to evolve from a portfolio-analysis tool into a proactive portfolio-management assistant.

It should help an investor:

- maintain one or more portfolios;
- understand portfolio composition and valuation;
- evaluate position and portfolio-level risk;
- receive evidence-backed recommendations;
- assess and periodically revise stop-loss levels;
- identify relevant market, company, macroeconomic, regulatory, and geopolitical information;
- understand why new information matters to the portfolio;
- periodically reassess portfolios and react to relevant events.

The platform is a **decision-support system**.

It may eventually recommend actions such as:

```text
Maintain
Increase
Reduce
Exit
Rebalance
Adjust Stop-Loss
Consider a new position
```

but it does **not** autonomously execute investment transactions.

The investor always remains responsible for the final investment decision.

---

## Core Product Principle

Deterministic financial calculations and deterministic business rules must remain deterministic.

AI may reason about, classify, correlate, summarize, or explain deterministic information, but it must not replace calculations that can be implemented reliably through deterministic logic.

Conceptually:

```text
Verified Data
     ↓
Deterministic Calculations
     ↓
Semantic / External Context
     ↓
AI-Assisted Reasoning
     ↓
Explainable Insights
     ↓
Human Decision
```

This boundary is fundamental to the project.

---

# Human-Governed Delivery Model

My-FinAI-Manager separates **human-owned intent** from the mechanisms used to design and implement software.

The delivery mechanism is intentionally replaceable.

A Feature or Enabler may be designed and implemented using:

- OpenSpec;
- Spec-Kit;
- another Spec-Driven Development framework;
- direct AI-assisted workflows;
- specialized agents;
- traditional human development;
- or a combination of these.

The methodology remains the same:

```text
Human Definition
      ↓
Solution Design
Spec → Plan → Tasks
      ↓
Implementation
      ↓
Independent Validation
      ↓
READY
      ↓
Human Validation
      ↓
DONE
```

The framework or AI model is a delivery mechanism, not the authority over product intent.

---

## Sources of Truth

The authoritative sources of intent are the **human-governed Feature Definitions and Enabler Definitions** under:

```text
product/definition/features/
product/definition/enablers/
```

These definitions describe what must be delivered and include the acceptance criteria used to determine whether the implementation is correct.

The shared product model under:

```text
product/model/
```

provides product-wide context such as:

- vision;
- business context;
- glossary;
- domains;
- information model;
- business events.

AI may assist humans in creating and challenging these documents, but final responsibility remains human.

Derived artifacts such as:

```text
Specification
Plan
Tasks
Implementation
Derived tests
```

must remain consistent with the approved human definition.

---

## Project Guardrails

Reusable constraints live under:

```text
reference/
```

They define the rules within which a Feature or Enabler may be implemented.

The main areas are:

```text
reference/
├── engineering/
├── governance/
├── templates/
└── ux/
```

### Engineering

`reference/engineering/` contains the technical guardrails for the platform:

- `architecture.md` — architectural model, boundaries, interaction style, persistence direction, AI boundaries, observability, and evolution principles;
- `architecture-rules.md` — concise architecture conformance criteria;
- `technology-policy.md` — approved technology stack and technology-introduction guardrails;
- `development-rules.md` — implementation discipline;
- `testing.md` — testing and evidence-producing strategy;
- `adrs/` — Architecture Decision Records;
- `diagrams/` — architecture diagrams.

### Governance

`reference/governance/delivery-policy.md` defines the delivery lifecycle, actors, Definition of Ready, Definition of Done, validation model, Fix / Change Request handling, CI/CD expectations, and Pull Request policy.

### Templates

`reference/templates/` contains reusable templates for:

- Feature / Enabler Definitions;
- Change Requests;
- Fixes.

### UX

`reference/ux/` contains reusable UX and design guardrails.

---

# Delivery Actors

The delivery model defines four logical actors.

## Product Manager / Owner

The **Product Manager / Owner** is the human authority over a Feature or Enabler.

This role may be performed by a business Product Manager, Product Owner, architect, technical lead, or another responsible human depending on the nature of the work.

Responsibilities include:

- creating or owning the Feature / Enabler Definition;
- defining scope, requirements, constraints, and acceptance criteria;
- resolving ambiguities;
- approving the Definition before implementation;
- validating the Ready implementation;
- approving the final Pull Request;
- deciding when the Feature / Enabler is Done.

AI may support this role, but responsibility remains human.

## Solution Designer

The **Solution Designer** transforms the approved Definition into:

```text
Specification
Plan
Tasks
```

This role may be implemented by an SDD framework, an AI model or agent, a human, or a hybrid process.

If the Definition is ambiguous, the Solution Designer must request clarification rather than invent product intent.

## Builder

The **Builder** implements the approved Tasks and their associated tests while following all applicable project guardrails.

The Builder may be a human developer, AI coding agent, SDD implementation workflow, or combination of these.

The Builder does not decide that its own implementation is Ready.

## Solution Validator

The **Solution Validator** independently determines whether the implementation is objectively ready for human validation.

It verifies the complete chain:

```text
Definition ↔ Specification
Specification ↔ Plan / Tasks
Definition ↔ Implementation
Acceptance Criteria ↔ Tests
Architecture Rules ↔ Implementation
Technology Policy ↔ Dependencies
Engineering Guardrails ↔ Implementation
```

It also executes the applicable deterministic tests locally.

The validator returns:

```text
OK
KO
BLOCKED
```

Only `OK` may move the implementation to **Ready** and open the Pull Request.

---

# Definition of Ready

A Feature or Enabler is **Ready** when implementation has finished and all mandatory technical and semantic validation has passed.

At minimum:

```text
Definition ↔ Specification      PASS
Specification ↔ Plan / Tasks    PASS
Definition ↔ Implementation     PASS
Acceptance Tests                PASS
Architecture Tests              PASS
Other Mandatory Tests           PASS
Solution Validation             OK
                              ─────
                              READY
```

`Ready` means:

> **The implementation is objectively ready for the responsible human to evaluate.**

It does not mean that the Feature or Enabler is finished.

---

# Human Validation: Fix, Change Request, or Accept

Once an implementation is Ready, the Product Manager / Owner evaluates the actual result.

Three outcomes are possible:

```text
READY
  │
  ├── implementation is incorrect
  │       ↓
  │      FIX
  │
  ├── implementation is correct,
  │   but desired behavior changes
  │       ↓
  │   CHANGE REQUEST
  │
  └── implementation is accepted
          ↓
         DONE
```

## Fix

A **Fix** means the implementation does not correctly satisfy the already-approved Definition.

The approved intent does not change.

The implementation returns to the delivery cycle, is corrected, revalidated, and must become Ready again.

A Fix may also reveal that an acceptance test or the validator itself needs to be strengthened.

## Change Request

A **Change Request** means the implementation correctly satisfies the current Definition, but the human decides that the desired result should change.

This is new human intent.

The Feature / Enabler Definition is updated and approved again, and the affected Specification, Plan, Tasks, tests, and implementation are regenerated or adjusted before the implementation can become Ready again.

Change Requests remain as traceability records.

---

# Definition of Done

A Feature or Enabler is **Done** only when:

```text
READY
  ↓
Human Validation
  ↓
Human Acceptance
  ↓
Pull Request Approval / Merge
  ↓
DONE
```

`Done` is final for that Definition.

Once Done, the Feature / Enabler lifecycle is closed.

If new behavior is desired later, a **new Feature Definition or Enabler Definition** must be created rather than reopening the completed one.

---

# Testing Strategy

Every Feature / Enabler defines explicit **Acceptance Criteria**.

Where behavior is deterministic, those criteria should be translated into executable tests wherever practical.

For deterministic business behavior, TDD is preferred:

```text
RED
  ↓
Failing behavioral test

GREEN
  ↓
Minimum correct implementation

REFACTOR
  ↓
Improve design without changing behavior
```

The project also uses technical tests that do not originate directly from product acceptance criteria, including:

- architecture tests;
- integration tests;
- contract tests;
- persistence tests;
- browser-based E2E tests;
- security and resilience tests where applicable.

The testing strategy favors meaningful evidence over arbitrary test quantity.

---

# CI/CD and Pull Requests

The Pull Request is the human review boundary.

The normal flow is:

```text
Dedicated branch
      ↓
Implementation
      ↓
Solution Validator = OK
      ↓
READY
      ↓
Pull Request created
      ↓
CI executes deterministic verification
      ↓
Human Review
      ↓
Merge
      ↓
DONE
```

CI/CD executes applicable deterministic checks such as:

- build;
- unit/domain tests;
- integration tests;
- contract tests;
- architecture tests;
- E2E / acceptance tests;
- static analysis;
- dependency/security checks where configured.

**No AI/LLM execution is required in normal CI/CD.**

AI-based semantic validation belongs to the Solution Validator stage before the Pull Request.

Automated actors must not merge the Pull Request.

Final merge remains a human decision.

---

# Architecture Direction

The architecture is designed for incremental evolution.

Important principles include:

- frontend/backend decoupling through explicit business contracts;
- stable external business APIs;
- Hexagonal Architecture for business-capable backend components;
- explicit domain, business, and infrastructure boundaries;
- provider-neutral interfaces for external systems and AI providers;
- deterministic logic isolated from AI-assisted reasoning;
- synchronous and asynchronous interaction chosen according to actual requirements;
- PostgreSQL as the preferred relational persistence technology;
- specialized infrastructure only when demonstrated value justifies the complexity;
- vendor-neutral observability through OpenTelemetry;
- explicit ADRs for significant architecture changes.

The project deliberately avoids introducing distributed-system complexity before a demonstrated need exists.

---

# Technology Direction

The approved solution space is defined in `reference/engineering/technology-policy.md`.

Current principal choices include:

| Concern | Direction |
|---|---|
| Web Frontend | Angular + TypeScript |
| Java Backend | Spring Boot |
| Spring Build | Maven |
| Python Backend / AI / Data | Python, FastAPI when an HTTP API is needed |
| External API | REST + OpenAPI |
| Relational Persistence | PostgreSQL |
| Spring Persistence | Spring Data JPA / Hibernate |
| Schema Migration | Flyway |
| Async Messaging | Kafka when justified |
| Vector Search | PostgreSQL + pgvector first |
| Graph | Neo4j when justified |
| AI Providers | AWS Bedrock, OpenAI, Anthropic, Vertex AI behind neutral boundaries |
| Observability | OpenTelemetry |
| Containers | OCI-compatible containers |
| Identity | OAuth 2.0 / OpenID Connect |
| Integration Testing | Testcontainers |
| Architecture Testing | ArchUnit for Java where applicable |
| Browser E2E | Playwright |

An approved technology is not automatically required.

Specialized infrastructure must earn its complexity.

---

# Repository Structure

The current human-governed repository baseline is organized as:

```text
.
├── product/
│   ├── definition/
│   │   ├── features/
│   │   └── enablers/
│   │
│   └── model/
│       ├── vision.md
│       ├── context.md
│       ├── glossary.md
│       ├── domains.md
│       ├── information-model.md
│       └── business-events.md
│
├── reference/
│   ├── engineering/
│   │   ├── adrs/
│   │   ├── diagrams/
│   │   ├── architecture.md
│   │   ├── architecture-rules.md
│   │   ├── technology-policy.md
│   │   ├── development-rules.md
│   │   └── testing.md
│   │
│   ├── governance/
│   │   └── delivery-policy.md
│   │
│   ├── templates/
│   │   ├── feature-definition-template.md
│   │   ├── feature-enabler-definition-template.md
│   │   ├── change-request-template.md
│   │   └── fix-template.md
│   │
│   └── ux/
│       └── design-system.md
│
├── implementation/
│   └── platform/                 executable platform (EN001)
│       ├── backend/core-service/  Spring Boot service
│       ├── frontend/web/          Angular application
│       ├── contracts/             platform OpenAPI contract
│       ├── infrastructure/        Docker Compose runtime + observability
│       ├── e2e/                   containerized Playwright browser E2E
│       ├── start.sh / stop.sh / e2e.sh
│       └── README.md
│
└── README.md
```

The separation is intentional:

```text
product/
→ human-owned product intent

reference/
→ reusable architecture, engineering, governance, and UX guardrails

derived SDD artifacts
→ replaceable delivery artifacts

implementation
→ executable realization
```

When executable product code is present, the architecture defines `implementation/platform/` as the cumulative implementation boundary. The executable platform foundation delivered by **EN001** now lives there — see [`implementation/platform/README.md`](implementation/platform/README.md) for how to run and verify it (`./implementation/platform/start.sh`).

---

# Defined Product Capabilities

The current definitions in the repository cover areas including:

- portfolio creation;
- controlled financial-instrument selection;
- portfolio listing and detail;
- deterministic valuation and allocation;
- AI-assisted portfolio analysis;
- platform bootstrap and testing foundations;
- financial-instrument reference data;
- market-data integration;
- AI model integration.

These Definitions represent approved or evolving human intent. Their existence does not by itself imply that the corresponding capability is present in the current branch executable.

---

# Architecture Evolution

Architecture evolves when product requirements justify it.

Conceptually:

```text
Feature / Enabler Requirement
          ↓
Architecture Impact Analysis
          ↓
Can current architecture support it?
       │
       ├── Yes → continue delivery
       │
       └── No
             ↓
      Architecture Decision
             ↓
            ADR
             ↓
      Update Architecture
             ↓
      Continue Delivery
```

Architecture should enable future evolution without predicting every future requirement.

---

# Project Status

🚧 **Experimental project — under active development**

This branch carries the human-governed product definition, the reusable reference guardrails, the delivery methodology, and the first executable slice: the **EN001** platform foundation under `implementation/platform/` (Angular + Spring Boot + PostgreSQL + containerized observability, proven end to end by a containerized Playwright test).

The project intentionally treats implementation tooling — including SDD frameworks and AI coding agents — as replaceable.

---

# Disclaimer

My-FinAI-Manager is an experimental software engineering and artificial intelligence project.

It is intended as an advisory decision-support system.

Any analysis, recommendation, risk assessment, stop-loss suggestion, or other generated insight must be evaluated by the investor.

The system does not autonomously execute investment transactions.

The investor remains responsible for all final investment decisions.
