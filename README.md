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
Human Project Definition
        │
        ├── Product
        ├── Architecture
        ├── Engineering
        └── Governance
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
├── product/
│   ├── vision.md
│   ├── context.md
│   ├── glossary.md
│   └── feature-definitions/
│
├── architecture/
│   ├── architecture.md
│   ├── technology-policy.md
│   ├── architecture-rules.md
│   ├── diagrams/
│   └── adrs/
│
├── engineering/
│   ├── development-rules.md
│   ├── testing-strategy.md
│   └── definition-of-done.md
│
└── governance/
    ├── sdd-policy.md
    └── ai-development-policy.md
```

### Product

Contains the human-authored definition of what My-FinAI-Manager is and what it should do.

Feature definitions represent deliberate human product and domain decisions before they are formalized through the selected SDD framework.

### Architecture

Contains the architectural vision, approved technology policies, architecture rules, diagrams, and Architecture Decision Records.

Architecture documentation evolves together with the system.

### Engineering

Defines how the software must be developed and verified, including testing, code quality, development practices, and the Definition of Done.

### Governance

Defines how human intent, AI agents, architecture, and the Spec-Driven Development lifecycle interact.

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

It does not provide personalized financial advice, does not issue buy/sell/hold recommendations, and does not execute investment transactions.

Any financial analysis produced by the system is intended for informational and educational purposes only.
