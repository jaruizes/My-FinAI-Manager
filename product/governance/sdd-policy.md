# My-FinAI-Manager — Spec-Driven Development Policy

## Purpose

This document defines how Spec-Driven Development (SDD) is applied in My-FinAI-Manager.

The project intentionally does **not** depend conceptually on a specific SDD framework.

SDD is treated as a set of engineering principles and lifecycle concepts that help transform human product intent into implementation in a controlled, traceable, and reviewable way.

Frameworks such as Spec Kit, OpenSpec, or future alternatives may support this lifecycle, but they are considered replaceable tooling.

The project must remain understandable and governable even if the selected SDD framework changes.

---

# Core Principle

The governing principle is:

> SDD is a development approach, not a product dependency.

My-FinAI-Manager applies the concepts of:

- explicit intent;
- specification before implementation;
- clarification of ambiguity;
- traceability;
- architecture impact analysis;
- implementation planning;
- task decomposition;
- verification against acceptance criteria;
- controlled evolution.

A framework may automate or formalize some of these steps, but it does not own the product definition.

---

# Human-Governed Source of Truth

The authoritative project definition lives under:

```text
product/
```

This includes:

```text
product/
├── definition/
│   ├── global/
│   └── features/
├── architecture/
├── engineering/
└── governance/
```

These files are human-governed and framework-independent.

An SDD framework may consume them, derive artifacts from them, or help formalize them, but it must not silently replace them as the source of truth.

---

# SDD Lifecycle

The project follows this conceptual lifecycle:

```text
Human Product Intent
        ↓
Feature Definition
        ↓
Formal Specification
        ↓
Clarification
        ↓
Architecture Impact Analysis
        ↓
Implementation Plan
        ↓
Task Decomposition
        ↓
Implementation
        ↓
Verification
        ↓
Acceptance
```

The exact commands, filenames, generated folders, or automation mechanisms may differ depending on the selected SDD framework.

---

# 1. Feature Definition

Every product capability should originate from a human-authored Feature Definition.

Feature Definitions live under:

```text
product/definition/features/
```

A Feature Definition should capture deliberate product intent such as:

- objective;
- user value;
- scope;
- main behavior;
- business rules;
- acceptance expectations;
- affected domains;
- relevant information objects;
- relevant business events;
- known constraints;
- out-of-scope behavior.

The Feature Definition is not a technical implementation plan.

---

# 2. Formal Specification

The Feature Definition may be transformed into a more formal specification.

The purpose of the specification is to make behavior precise enough to implement and verify.

A specification should:

- preserve the Feature Definition's intent;
- remove ambiguity;
- define acceptance scenarios;
- identify edge cases;
- make assumptions explicit;
- remain technology-independent unless technology is part of the requirement.

AI may assist in generating the specification.

The resulting specification is not automatically authoritative until reviewed and accepted.

---

# 3. Clarification

Ambiguity must be resolved before implementation when it materially affects behavior.

Clarification should identify:

- missing requirements;
- contradictory statements;
- undefined terminology;
- ambiguous business rules;
- unsupported assumptions;
- unclear edge cases.

The correct behavior is not to let AI infer a plausible answer silently.

The correct behavior is:

```text
Ambiguity Detected
       ↓
Question / Explicit Assumption
       ↓
Human Decision
       ↓
Specification Updated
```

---

# 4. Architecture Impact Analysis

Every significant feature must be evaluated against the current architecture.

Questions include:

- Does the current architecture already support the feature?
- Does the feature introduce a new external dependency?
- Does it require Kafka?
- Does it require Neo4j?
- Does it require a new service?
- Does it require a new runtime?
- Does it require changes to external APIs?
- Does it require new persistence?
- Does it affect security, privacy, observability, or scalability?
- Does it require an ADR?

The architecture must not be silently changed as a side effect of implementation.

---

# 5. Implementation Plan

The implementation plan describes how the approved specification will be implemented within the approved architecture.

It may define:

- affected modules/components;
- ports and adapters;
- API changes;
- persistence changes;
- event interactions;
- test strategy;
- migration strategy;
- observability requirements;
- implementation sequence.

The plan may propose architectural changes, but such changes must follow the ADR and architecture governance process where applicable.

---

# 6. Task Decomposition

Implementation should be decomposed into reviewable tasks.

Tasks should:

- have a clear objective;
- trace back to the specification or plan;
- avoid mixing unrelated work;
- include relevant testing work;
- preserve dependency order where required.

Tasks are implementation artifacts and may be generated by the selected SDD framework.

---

# 7. Implementation

Implementation must comply with:

```text
product/architecture/
product/engineering/
product/governance/
```

Code generation by AI does not alter these constraints.

Implementation must not:

- reinterpret product intent;
- bypass architecture rules;
- introduce new technologies without approval;
- weaken test requirements;
- silently expand scope.

---

# 8. Verification

Implementation must be verified against the approved specification.

Verification includes, as applicable:

- acceptance scenarios;
- unit tests;
- integration tests;
- contract tests;
- architecture tests;
- AI evaluations;
- security checks;
- coverage gates;
- Definition of Done.

The question is not merely:

> Does the code work?

It is:

> Does the implementation demonstrably satisfy the approved product intent within the approved architecture?

---

# SDD Artifact Hierarchy

The project uses the following authority hierarchy:

```text
1. Human-governed product definition
2. Architecture and engineering policies
3. Approved Feature Definition
4. Approved formal specification
5. Approved implementation plan
6. Approved tasks
7. Generated code
```

Lower-level artifacts must not silently contradict higher-level artifacts.

If a conflict is discovered, it must be surfaced explicitly.

---

# Framework Independence

The SDD framework is replaceable.

The project may initially use tools such as:

- Spec Kit;
- OpenSpec;
- another SDD tool;
- custom scripts;
- AI-agent workflows.

The project must not require a specific framework in order to understand:

- what the product is;
- what a feature means;
- what architecture is approved;
- what engineering rules apply;
- how AI may participate.

---

# Framework Adapter Principle

Framework-specific artifacts should be considered adapters around the human-governed development model.

Conceptually:

```text
Human-Governed Project Definition
              │
              ▼
       SDD Conceptual Model
              │
      ┌───────┼────────┐
      ▼       ▼        ▼
  Spec Kit  OpenSpec  Future Tool
```

The framework adapts to the project.

The project must not be restructured conceptually around limitations of a specific framework unless there is an explicit decision to do so.

---

# Framework Migration

If the SDD framework is replaced:

1. human-governed files under `product/` remain unchanged unless product or architecture decisions change;
2. generated framework-specific artifacts may be regenerated;
3. traceability between Feature Definitions and generated artifacts must be preserved;
4. no product requirement should be lost during migration;
5. no architecture rule should be reinterpreted merely because a new tool uses different terminology.

---

# Repository Mapping for SDD Artifacts

The repository separates product definition, derived SDD artifacts, and executable implementation:

```text
product/                 Human-governed source of truth
specs/                   SDD-derived artifacts
implementation/platform/ Current cumulative executable platform
```

## `product/`

Contains framework-independent human intent, architecture, engineering standards, and governance.

## `specs/`

Contains SDD artifacts derived from Feature Definitions.

A feature may produce artifacts such as:

```text
specs/FD001-import-investment-portfolio/
├── specification.md
├── plan.md
├── tasks.md
└── ...
```

Exact filenames remain framework-dependent.

The `specs/` directory is intentionally separate from `product/` so generated or derived SDD artifacts do not become the human-governed source of truth.

## `implementation/platform/`

Contains the accumulated executable realization of all implemented features.

A vertical slice may change multiple areas:

```text
implementation/platform/
├── frontend/
├── backend/
├── contracts/
└── infrastructure/
```

The feature does not own a separate executable tree.

```text
FD001 → extends platform
FD002 → extends the same platform
FD003 → extends the same platform
```

Vertical slicing defines how product value is specified and delivered; it does not require the physical source tree to be organized by Feature Definition.

Every completed feature must leave the shared platform coherent and executable.

# Generated Artifacts

Framework-generated artifacts may include:

- formal specifications;
- clarification records;
- implementation plans;
- task lists;
- checklists;
- generated metadata.

Generated artifacts should live outside the authoritative human-governed definition unless explicitly promoted through human review.

They may be regenerated.

The human product definition must not depend on generated files to remain understandable.

---

# Naming and Traceability

Feature identifiers should remain stable across the development lifecycle.

Recommended pattern:

```text
FDNNN-short-name
```

Example:

```text
FD001-import-investment-portfolio
```

The same identifier should be reused where practical across:

- Feature Definition;
- specification;
- implementation plan;
- tasks;
- branch;
- pull request;
- tests or acceptance evidence.

---

# Change Management

When implementation reveals the need to change the product behavior:

```text
Implementation Discovery
        ↓
Update Feature Definition
        ↓
Human Review
        ↓
Update Specification
        ↓
Re-evaluate Architecture Impact
        ↓
Continue Implementation
```

Do not patch the code first and retroactively rewrite the specification to match it.

---

# Architecture Changes During SDD

When a feature requires a significant architecture change:

```text
Feature Requirement
        ↓
Architecture Impact Analysis
        ↓
Architecture Decision
        ↓
ADR
        ↓
Architecture / Policy Update
        ↓
Implementation Plan
```

The SDD workflow must respect the architecture governance process.

---

# SDD and Brownfield Development

The same principles apply to existing code.

Existing implementation may reveal current behavior, but it is not automatically the desired product definition.

For brownfield changes:

```text
Existing Behavior
       +
Human Intent
       ↓
Feature / Change Definition
       ↓
Specification
       ↓
Impact Analysis
       ↓
Implementation
```

---

# What SDD Must Not Become

SDD must not become:

- a document-generation exercise;
- a rigid ceremony with no engineering value;
- an excuse for excessive upfront design;
- a mechanism for AI to invent requirements;
- a replacement for architecture;
- a replacement for engineering judgment;
- a framework dependency.

The goal is controlled transformation from intent to verified implementation.

---

# SDD Policy Summary

The project follows this principle:

```text
Humans define intent
        ↓
AI may challenge and formalize
        ↓
Humans approve
        ↓
Architecture constrains the solution
        ↓
SDD structures implementation
        ↓
AI may implement
        ↓
Tests and review verify the result
```

The framework is replaceable.

The concepts are not.
