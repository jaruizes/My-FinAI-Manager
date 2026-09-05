# My-FinAI-Manager

**My-FinAI-Manager** is an AI-native personal portfolio intelligence platform.

Its goal is to explore how deterministic financial analysis, semantic knowledge, software architecture, and agentic AI can work together to help an investor better understand their portfolio, its risks, investment theses, market events, and relevant information.

The project is also an experiment in **AI-assisted software engineering**, where human-defined product intent, architecture, and engineering rules guide AI agents through a Spec-Driven Development lifecycle.

## Goals

My-FinAI-Manager aims to explore capabilities such as:

* Investment portfolio modelling
* Financial instrument modelling
* Portfolio valuation and exposure analysis
* Portfolio risk analysis
* Investment thesis tracking
* Market event and news relevance
* Semantic relationships between companies, sectors, geographies, and risks
* Knowledge Graphs and semantic retrieval
* GraphRAG and other retrieval strategies
* Agentic workflows
* Explainable AI-assisted analysis
* AI evaluation and observability
* Model Context Protocol (MCP)
* Event-Driven Architecture where justified

The long-term goal is not to build an *AI stock picker*, but a system capable of answering questions such as:

> Why is my portfolio exposed to a particular risk?

> What happened today that is actually relevant to my portfolio?

> Which positions share common sector, geographical, or business dependencies?

> Has new information weakened one of my original investment theses?

> Why did my portfolio move?

## Current Capabilities

My-FinAI-Manager is currently in the early stages of development. These are the main capabilities it currently supports:

### Portfolio Home

```
![Home](docs/img/portfoliios_home)
```

### Portfolio Detail

```
![Home](docs/img/portfoliio_detail)
```

## Core Principle

Financial calculations and deterministic business rules must remain deterministic.

AI models may reason about, explain, correlate, or summarize deterministic information, but they must not replace deterministic financial calculations.

In simplified form:

```text
Deterministic Data & Calculations
              ↓
      Semantic Knowledge
              ↓
        AI Reasoning
              ↓
     Explainable Insights
              ↓
        Human Decision
```

The investor always remains responsible for investment decisions.

## Human-Governed Development

My-FinAI-Manager follows a human-governed development model.

The project's product intent, architecture, engineering standards, and development governance are defined independently from any particular AI coding agent or Spec-Driven Development framework.

```text
Human-Governed Product Definition
        │
        └── product/
            ├── definition/
            │   ├── global/
            │   └── features/
            ├── architecture/
            ├── engineering/
            └── governance/
        │
        ▼
Spec-Driven Development
        │
        ▼
AI-assisted Planning
        │
        ▼
Implementation
        │
        ▼
Verification
```

This separation allows the project to evolve independently from the tooling used to implement it.

## Repository Structure

```text
.
├── implementation/
│   └── platform/
│       ├── backend/
│       ├── contracts/
│       ├── frontend/
│       ├── infrastructure/
│       ├── start.sh
│       └── stop.sh
│
├── product/
│   ├── architecture/
│   ├── definition/
│   │   ├── features/
│   │   └── global/
│   │       ├── vision.md
│   │       ├── context.md
│   │       ├── glossary.md
│   │       ├── domains.md
│   │       ├── information-model.md
│   │       └── business-events.md
│   ├── engineering/
│   └── governance/
│
└── specs/
```

The repository deliberately separates three concerns:

```text
product/                 Human-governed product, architecture, engineering and governance definition
specs/                   SDD-derived artifacts for vertical product changes
implementation/platform/ Current cumulative executable realization of My-FinAI-Manager
```

### Implementation

`implementation/platform/` contains the executable platform produced incrementally by implemented Feature Definitions.

- `frontend/` — user-facing frontend applications.
- `backend/` — backend deployable components or bounded services. Initial service granularity may be coarse and may evolve only when scaling, maintainability, ownership, runtime, availability, or operational needs justify separation.
- `contracts/` — externally visible API and event contracts such as OpenAPI and AsyncAPI definitions.
- `infrastructure/` — local runtime and deployment infrastructure required by the platform.
- `start.sh` — canonical local entry point for starting the complete platform or development environment.
- `stop.sh` — canonical local entry point for stopping the complete platform or development environment.

Every implemented Feature Definition extends the existing platform. A feature may modify several implementation areas at once, including frontend, backend, contracts, persistence, infrastructure, and tests.

Feature boundaries provide vertical delivery traceability; implementation folders follow architectural and technical boundaries. They are related, but are not required to be identical.

### Product Definition

`product/definition/` contains the human-authored functional definition of My-FinAI-Manager.

The `global/` directory contains product-wide information that applies across features:

- `vision.md` — product vision, objectives, and Version 1.0.0 direction.
- `context.md` — actors, system boundaries, external information sources, and functional context.
- `glossary.md` — shared business vocabulary and relevant standards.
- `domains.md` — main functional domains, responsibilities, concepts, and information handled.
- `information-model.md` — business information objects, their main attributes, and relationships.
- `business-events.md` — significant business events, their effects, and the main business processes they participate in.

The `features/` directory contains human-authored Feature Definitions. Each Feature Definition represents deliberate product and domain intent for a concrete capability before it is formalized through the selected SDD framework.

Global product definitions provide the shared context for all features; Feature Definitions refine that context without silently redefining it.

### Architecture

Contains the architectural vision, approved technology policies, architecture rules, diagrams, and Architecture Decision Records.

Architecture documentation evolves together with the system.

### Engineering

Defines how the software must be developed and verified, including testing, code quality, development practices, and the Definition of Done.

### Governance

Defines how human intent, AI agents, architecture, and the Spec-Driven Development lifecycle interact.

### Specifications

`specs/` contains framework-generated or framework-assisted SDD artifacts associated with Feature Definitions.

These artifacts may include specifications, clarification records, implementation plans, tasks, and checklists.

They are derived from the human-governed definition under `product/` and must remain traceable to the corresponding Feature Definition.

## Architecture Evolution

The architecture is expected to evolve incrementally as new capabilities are introduced.

Architectural changes should be intentional and traceable.

When a feature requires a significant architectural decision:

```text
Feature Requirement
       ↓
Architecture Impact Analysis
       ↓
Architecture Decision Record
       ↓
Architecture / Diagram Update
       ↓
Implementation Plan
```

The project deliberately avoids introducing infrastructure or architectural complexity before there is a justified need for it.

## Spec-Driven Development

The project uses Spec-Driven Development to transform human intent into executable software.

The intended lifecycle is:

```text
Human Feature Definition
        ↓
Formal Specification
        ↓
Clarification
        ↓
Architecture Impact Analysis
        ↓
Implementation Plan
        ↓
Tasks
        ↓
AI-assisted Implementation
        ↓
Testing & Verification
```

The SDD framework itself is considered replaceable tooling.

The initial implementation uses **Spec Kit**, but the product, architecture, engineering, and governance definitions are intentionally framework-independent.

## Status

🚧 **Experimental project — under active development**

The project is being built incrementally, with architecture and functionality evolving as new features are introduced.

## Disclaimer

My-FinAI-Manager is an experimental software engineering and artificial intelligence project.

It is an advisory decision-support system. It may generate portfolio and investment recommendations, including maintain, increase, reduce, exit, rebalance, or stop-loss suggestions, but it does not autonomously execute investment transactions.

The Investor remains responsible for all final investment decisions and for any external execution of those decisions.
