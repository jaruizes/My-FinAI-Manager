# My-FinAI-Manager — AI Development Policy

## Purpose

This document defines how AI agents may participate in the development of My-FinAI-Manager.

AI is treated as an engineering accelerator operating inside a human-governed product, architecture, and development model.

AI agents may assist with:

- requirements formalization;
- clarification;
- architecture analysis;
- design proposals;
- planning;
- task decomposition;
- implementation;
- testing;
- code review;
- documentation;
- refactoring;
- verification.

AI agents do not own product intent or architectural authority.

---

# Core Principle

The governing principle is:

> AI may accelerate reasoning and implementation, but humans retain authority over product intent, architecture, and material engineering decisions.

AI should operate within explicit constraints rather than infer the project from source code alone.

---

# Sources of Authority

AI agents must respect the following hierarchy:

```text
1. Human-governed product definition under product/
2. Architecture policies and approved ADRs
3. Engineering and governance policies
4. Approved Feature Definition
5. Approved formal specification
6. Approved implementation plan
7. Approved tasks
8. Existing implementation
```

AI must not use lower-level artifacts to silently override higher-level intent.

---

# AI May Do

AI agents may:

- analyze Feature Definitions;
- identify ambiguity;
- ask clarifying questions;
- formalize requirements;
- propose acceptance scenarios;
- identify architecture impacts;
- propose alternatives;
- draft ADRs;
- generate implementation plans;
- decompose work into tasks;
- generate tests;
- generate production code;
- generate migrations;
- generate OpenAPI or AsyncAPI contracts;
- review architecture conformance;
- review security or quality issues;
- identify missing tests;
- propose refactoring;
- update documentation when explicitly instructed;
- generate evaluation datasets using synthetic or public data;
- explain trade-offs.

AI proposals remain subject to the approval requirements defined by project governance.

---

# AI Must Not Do

AI agents must not:

- silently invent business requirements;
- silently redefine product terminology;
- silently change Feature Definition scope;
- silently alter acceptance criteria;
- silently change architecture;
- silently introduce new technologies;
- bypass architecture rules;
- weaken tests to make CI pass;
- fabricate financial facts;
- fabricate source evidence;
- treat probabilistic output as verified fact;
- expose private portfolio data unnecessarily;
- commit secrets;
- make autonomous investment transactions.

---

# Human Approval Boundaries

Explicit human approval is required before AI-originated changes that materially affect:

- product behavior;
- Feature Definition scope;
- business rules;
- architecture topology;
- technology policy;
- security boundaries;
- persistence ownership;
- public API compatibility;
- financial recommendation policy;
- stop-loss policy;
- externally visible contracts;
- irreversible migrations.

Routine implementation details inside an already approved plan do not require separate architectural approval.

---

# AI and SDD

AI participation is independent from the SDD framework.

The project does not assume that Spec Kit, OpenSpec, or another framework is responsible for AI governance.

AI governance applies across the conceptual lifecycle:

```text
Feature Definition
      ↓
Specification
      ↓
Clarification
      ↓
Architecture Impact
      ↓
Plan
      ↓
Tasks
      ↓
Implementation
      ↓
Verification
```

The selected SDD framework may orchestrate or assist these steps, but this policy remains authoritative regardless of tooling.

---

# Requirement Formalization

When AI transforms a Feature Definition into a specification, it must:

- preserve stated intent;
- preserve terminology;
- distinguish explicit requirements from inferred assumptions;
- identify missing information;
- avoid adding plausible but unapproved behavior;
- propose clarifications when ambiguity matters.

A large, polished specification is not evidence that the underlying assumptions are correct.

---

# Assumption Policy

When AI encounters missing information, it must classify the situation.

## Safe Implementation Detail

AI may choose a reasonable implementation detail when:

- it does not change product behavior;
- it complies with architecture;
- it is reversible;
- it does not introduce significant operational consequences.

## Material Product or Architecture Assumption

AI must surface the assumption when it affects:

- user-visible behavior;
- business rules;
- financial behavior;
- data ownership;
- public contracts;
- security;
- architecture;
- technology selection;
- availability;
- scalability;
- privacy.

The preferred pattern is:

```text
Ambiguity
   ↓
Explicit Question or Proposed Assumption
   ↓
Human Approval
   ↓
Proceed
```

---

# Architecture Role of AI

AI may act as an architecture assistant.

It may:

- identify architecture impacts;
- compare design alternatives;
- propose module boundaries;
- propose sync vs async communication;
- propose service extraction;
- propose persistence technologies;
- propose ADRs;
- identify vendor lock-in;
- propose fitness functions.

AI must not treat architecture as fully delegated.

The responsible human remains the architectural authority.

---

# Technology Selection

Before proposing a technology, AI must consult:

```text
product/architecture/technology-policy.md
```

If the technology is:

- approved: AI may use it when justified;
- conditional: AI must explain why the condition applies;
- ADR required: AI must propose the required decision process;
- not approved: AI must not introduce it silently.

---

# Architecture Rules

AI-generated designs and code must comply with:

```text
product/architecture/architecture-rules.md
```

Examples include:

- Hexagonal Architecture;
- inward dependency direction;
- explicit module boundaries;
- no cross-module persistence access;
- provider abstraction;
- deterministic financial logic;
- no automatic mapping from business events to Kafka;
- no automatic mapping from domains to microservices.

---

# AI-Generated Code

AI-generated code is treated exactly like human-written code.

It must satisfy:

- coding rules;
- TDD requirements;
- testing strategy;
- architecture rules;
- security requirements;
- observability requirements;
- Definition of Done.

AI-generated code must not receive lower quality standards because it was generated faster.

---

# AI and TDD

Where TDD applies, AI should follow the same sequence expected from human development:

```text
Behavior / Acceptance Scenario
        ↓
Failing Test
        ↓
Minimal Implementation
        ↓
Refactor
```

AI should not generate extensive production code first and then add superficial tests purely to satisfy coverage.

---

# AI and Testcontainers

When generating integration tests against application-managed infrastructure, AI must follow the Testcontainers policy.

Where applicable, AI should use real disposable infrastructure for tests involving:

- PostgreSQL;
- Kafka;
- Neo4j;
- other containerizable application dependencies.

AI should not replace these integrations with mocks when the integration itself is what must be tested.

External SaaS providers may use deterministic stubs or mocks where appropriate.

---

# AI and External Providers

AI must preserve provider neutrality.

For example, generated application/domain code must not depend directly on:

- AWS Bedrock SDK models;
- OpenAI message structures;
- Anthropic response types;
- market-data-provider DTOs.

Provider-specific code belongs in adapters.

---

# AI and Financial Logic

Financial calculations that can be deterministic must remain deterministic.

AI must not be used as a calculator for:

- portfolio valuation;
- position weights;
- percentage calculations;
- currency conversion;
- deterministic thresholds;
- deterministic stop-loss formulas.

AI may reason about or explain deterministic outputs.

---

# AI and Investment Recommendations

AI may participate in investment recommendation workflows because recommendation generation is within product scope.

However:

- recommendations remain advisory;
- supporting Evidence must be preserved where relevant;
- facts, calculations, interpretations, and recommendations must remain distinguishable;
- unsupported claims must not be presented as facts;
- uncertainty must not be hidden;
- the Investor remains the final decision maker;
- AI must not execute investment transactions.

---

# AI Output Classification

Where materially relevant, AI-produced information should be classified conceptually as one of:

```text
Fact
Deterministic Calculation
External Opinion
Interpretation
Inference
Recommendation
```

AI must not collapse these categories into a single undifferentiated answer.

---

# Evidence and Provenance

AI-assisted conclusions should preserve provenance where practical.

For material conclusions, the system should be able to determine:

- which source information was used;
- which deterministic calculations contributed;
- which model/provider was used where relevant;
- when the conclusion was generated;
- what Evidence supports it;
- the associated confidence or uncertainty when applicable.

---

# Hallucination Handling

AI must not fabricate missing information.

When information is insufficient, preferred outcomes include:

- “cannot determine”;
- explicit uncertainty;
- request for additional information;
- partial result with limitations.

A plausible invented answer is not an acceptable fallback.

---

# AI Evaluation

Material AI capabilities require evaluation beyond traditional unit testing.

Changes to:

- prompts;
- models;
- providers;
- retrieval;
- agent topology;
- tool selection;

should trigger relevant evaluation.

AI-generated evaluation data should be synthetic, anonymized, or public unless explicitly approved otherwise.

---

# Privacy

AI agents must not unnecessarily expose private portfolio or user data to external systems.

When generating or modifying code that calls external AI providers, the design must consider:

- data minimization;
- prompt contents;
- provider retention policy;
- logging;
- telemetry;
- sensitive-data leakage.

---

# Security

AI must never place:

- passwords;
- API keys;
- access tokens;
- private certificates;
- real credentials;

into committed source files.

AI must not weaken authorization or validation logic for convenience.

---

# Refactoring Policy

AI may propose or perform refactoring when:

- behavior remains unchanged;
- relevant tests protect the change;
- architecture boundaries improve or remain intact;
- the refactor is within task scope.

AI should not mix large unrelated refactors into feature implementation.

---

# Documentation Changes

Files under `product/` are human-governed.

AI may draft modifications, but product intent and architecture policy changes require explicit human approval.

AI must not silently modify these files as a side effect of implementation.

Generated framework artifacts outside `product/` may be updated automatically according to the selected SDD workflow.

---

# Architecture Decision Records

AI may draft ADRs.

An AI-generated ADR should include:

- context;
- decision;
- alternatives;
- rationale;
- consequences.

The ADR is not considered approved merely because AI generated it.

Material architecture decisions require human approval.

---

# Repository and Implementation Placement

AI agents must respect the repository organization defined by the project.

Generated executable implementation belongs under:

```text
implementation/platform/
```

The expected structure is:

```text
implementation/platform/
├── backend/
├── contracts/
├── frontend/
├── infrastructure/
├── start.sh
└── stop.sh
```

AI agents must not invent alternative repository-root implementation trees such as `apps/`, `services/`, `src/`, `infrastructure/`, or `tests/` unless an approved architecture decision explicitly changes the repository model.

## Vertical Slice Rule

A Feature Definition may require coordinated changes across multiple platform areas.

```text
FD001-import-investment-portfolio
        ├── frontend changes
        ├── backend changes
        ├── contract changes
        ├── database changes
        └── test changes
```

AI must integrate those changes into the existing executable platform rather than create a feature-specific standalone application.

## Backend Placement

New backend deployable components must be created under:

```text
implementation/platform/backend/
```

AI must not infer that every functional domain requires its own service.

Initial backend service granularity may be coarse.

If AI determines that a new independently deployable service is justified, it must surface the architectural impact and follow the ADR process where required.

## Contracts and Infrastructure

AI-generated platform contracts belong under:

```text
implementation/platform/contracts/
```

AI-generated runtime or deployment infrastructure belongs under:

```text
implementation/platform/infrastructure/
```

## Lifecycle Scripts

AI must preserve:

```text
implementation/platform/start.sh
implementation/platform/stop.sh
```

as the canonical local lifecycle entry points.

When new components or dependencies are added, these scripts must be updated so the complete platform can still be started and stopped through the same stable interface.

# Pull Request Expectations

AI-generated changes should remain reviewable.

A change should make clear:

- which feature/task it implements;
- what files were changed;
- what behavior changed;
- what architecture boundaries are affected;
- what tests were added;
- whether an ADR is required.

Avoid massive unstructured changes that are difficult for a human to validate.

---

# AI Failure Modes to Avoid

The project explicitly guards against these patterns:

## Plausible Requirement Invention

AI adds behavior that sounds reasonable but was never approved.

## Architecture Drift

AI introduces infrastructure, dependencies, or service boundaries opportunistically.

## Test Decoration

AI adds low-value tests merely to satisfy coverage.

## Framework Leakage

AI lets Spring, FastAPI, Kafka, or provider models define the domain.

## Vendor Coupling

AI uses provider-specific APIs directly throughout application code.

## Documentation Backfill

AI changes requirements after implementation to make the implementation appear compliant.

## False Certainty

AI presents interpretations or incomplete information as verified fact.

---

# Escalation Rule

If AI cannot complete a task without violating or changing an existing rule, it must stop that part of the implementation and surface the conflict.

The expected options are:

```text
1. Change the implementation approach
2. Clarify the requirement
3. Propose an ADR
4. Propose a policy change
```

Silently ignoring the conflict is not allowed.

---

# AI Development Policy Summary

The intended operating model is:

```text
Human defines intent
        ↓
AI analyses and challenges
        ↓
Human resolves material decisions
        ↓
AI formalizes and plans
        ↓
Architecture constrains implementation
        ↓
AI and/or humans implement
        ↓
Automated verification
        ↓
Human review and acceptance
```

AI is an active engineering collaborator.

It is not the product owner, architect of record, or final decision authority.
