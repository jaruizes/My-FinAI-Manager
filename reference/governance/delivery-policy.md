# Delivery Policy

## Purpose

This document defines the delivery governance model.

Its goal is to ensure that software implementation remains faithful to **human-defined intent**, that the implementation is objectively verified before human review, and that final acceptance of a Feature or Enabler remains a human responsibility.

The delivery model is independent from the implementation mechanism. Specification, planning, implementation, and validation may be performed using an SDD framework such as OpenSpec or Spec-Kit, AI-assisted workflows or direct prompts, specialized agents, traditional human development, or a combination of these.

> **Humans define and accept intent. Delivery mechanisms derive and implement it. Validation proves conformance before human acceptance.**

---

# 1. Sources of Truth

The authoritative sources of product and technical intent are the human-governed definitions:

```text
product/definition/features/
product/definition/enablers/
```

A Feature Definition (FD) or Enabler Definition (EN) describes what must be delivered and how its acceptance will be evaluated.

The Product Manager / Owner may use AI to clarify requirements, challenge assumptions, identify missing cases, improve wording, propose acceptance criteria, and structure the document. However, the human remains responsible for the final content and approval.

Derived artifacts are not sources of truth:

```text
Specification
Plan
Tasks
Implementation
Tests derived from the Definition
```

If a derived artifact contradicts the approved FD / EN, the approved human definition takes precedence.

---

# 2. Guardrails

All implementation must comply with the project-wide guardrails under:

```text
product/reference/
```

These include, where applicable:

```text
engineering/
  architecture.md
  architecture-rules.md
  technology-policy.md
  development-rules.md
  testing.md

ux/
  ...

governance/
  delivery-policy.md
```

Relevant ADRs are also authoritative where they define an approved architectural decision.

Guardrails constrain **how** the approved definition may be implemented. They must not silently change **what** the Feature / Enabler is intended to deliver.

If implementation cannot comply with an applicable guardrail, the issue must be surfaced before proceeding.

---

# 3. TDD and Verification

Every Feature / Enabler must define explicit **Acceptance Criteria**.

Acceptance Criteria represent observable evidence that the approved definition has been implemented correctly.

Where behavior is deterministic, Acceptance Criteria should be translated into automated tests wherever practical.

Conceptually:

```text
Human Definition
      ↓
Acceptance Criteria
      ↓
Executable Tests
      ↓
Implementation
```

For deterministic business behavior, a TDD workflow is preferred:

```text
RED
  ↓
write the failing behavioral test

GREEN
  ↓
implement the minimum correct behavior

REFACTOR
  ↓
improve the implementation without changing behavior
```

Not every required test originates directly from a Feature / Enabler Acceptance Criterion.

The project also contains **Architecture Tests** and other technical verification derived from project guardrails, such as architecture dependency checks, contract validation, integration tests, persistence integration tests, browser-based acceptance tests, and security or resilience tests where applicable.

Therefore, readiness requires both:

```text
Feature / Enabler acceptance evidence
+
Project guardrail verification
```

Passing tests alone is not sufficient if those tests do not represent the approved definition correctly.

---

# 4. Definition of Ready

A Feature / Enabler implementation is **Ready** when it is ready for human validation.

A Feature / Enabler may become Ready only when:

1. the approved FD / EN exists;
2. the derived Specification faithfully represents the FD / EN;
3. the Plan and Tasks remain consistent with the Specification and FD / EN;
4. implementation is complete for the approved scope;
5. all mandatory Acceptance Criteria are represented by appropriate verification;
6. all mandatory automated tests pass;
7. applicable Architecture Rules pass;
8. applicable technology and engineering guardrails are satisfied;
9. the Solution Validator concludes that implementation conforms to the approved Definition;
10. no unresolved blocking ambiguity remains.

Conceptually:

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

`Ready` does **not** mean that the Feature / Enabler is complete.

It means:

> **The implementation is objectively ready for the responsible human to evaluate.**

---

# 5. Definition of Done

A Feature / Enabler is **Done** only when the responsible human has reviewed the Ready implementation and explicitly accepted it.

Conceptually:

```text
READY
  ↓
Human Validation
  ↓
Human Acceptance
  ↓
DONE
```

`Done` is the final state of that Feature / Enabler Definition.

Once a Feature / Enabler is Done:

- its implementation has been accepted;
- its delivery lifecycle is closed;
- it must not be reopened to add new desired behavior.

If the product must evolve after Done, a **new Feature Definition or Enabler Definition** must be created.

This keeps completed definitions immutable as historical evidence of what was approved and delivered.

---

# 6. Human Review Outcomes

When a Feature / Enabler reaches `Ready`, the Product Manager / Owner reviews and tests the implementation.

There are three possible outcomes:

```text
READY
  │
  ├── defect found              → FIX
  ├── correct but change desired → CHANGE REQUEST
  └── accepted                  → DONE
```

## Fix

A **Fix** is created when the implementation does not correctly satisfy the already-approved Definition.

This can happen even if all automated tests passed or the Solution Validator returned `OK`.

Examples include an incomplete acceptance test, a validator missing an explicit requirement, the Specification and implementation encoding the same incorrect interpretation, or an edge case from the Definition being missed.

A Fix does **not** introduce new intent.

Flow:

```text
READY
  ↓
Human detects defect
  ↓
FIX
  ↓
Reimplementation / correction
  ↓
Tests
  ↓
Solution Validation
  ↓
READY
  ↓
Human Validation
```

A Fix should also improve the verification mechanism where appropriate so the same defect is less likely to escape again.

## Change Request

A **Change Request (CR)** is created when:

```text
implementation == approved definition
```

but the human decides, after seeing or using it, that the desired behavior should change.

A Change Request represents **new human intent**.

Flow:

```text
READY
  ↓
Human decides to change intent
  ↓
CHANGE REQUEST
  ↓
Update FD / EN
  ↓
Human Approval
  ↓
Update Specification / Plan / Tasks / Tests
  ↓
Reimplementation
  ↓
Solution Validation
  ↓
READY
  ↓
Human Validation
```

The Change Request remains as historical traceability, while the FD / EN is updated to represent the new approved current intent.

---

# 7. Actors

## Product Manager / Owner

The Product Manager / Owner is the human authority over the Feature / Enabler.

This role may be performed by a business Product Manager, Product Owner, architect, technical lead, or another responsible human depending on the nature of the Definition.

Responsibilities:

- create or own the FD / EN;
- define intent, scope, requirements, constraints, and Acceptance Criteria;
- resolve ambiguities;
- approve the Definition before delivery starts;
- answer clarification requests;
- validate the Ready implementation;
- create or approve Fixes and Change Requests;
- approve the final Pull Request;
- move the Feature / Enabler to Done.

AI may assist this role, but responsibility remains human.

## Solution Designer

The Solution Designer transforms the approved Definition into:

```text
Specification
Plan
Tasks
```

This role may be performed by an SDD framework such as OpenSpec or Spec-Kit, an AI model or agent, a human, or a hybrid workflow.

Responsibilities:

- preserve the approved Definition;
- derive a complete Specification;
- create an implementation Plan;
- decompose the Plan into actionable Tasks;
- associate Tasks with relevant Acceptance Criteria and tests;
- identify ambiguity rather than invent missing intent;
- request clarification from the Product Manager / Owner when required.

## Builder

The Builder implements the approved Tasks.

This role may be an AI coding agent, a human developer, an SDD/agentic implementation workflow, or a combination of these.

Responsibilities:

- implement only the approved scope;
- follow project guardrails;
- create or update the tests associated with the Tasks;
- ensure relevant tests pass during implementation;
- avoid changing the approved Definition;
- surface blockers or missing decisions.

The Builder does not decide that its own implementation is Ready.

## Solution Validator

The **Solution Validator** determines whether implementation is objectively ready for human validation.

This role should be independent from the Builder where practical. It may be another AI model, a dedicated validation agent or skill, a human reviewer, or a hybrid approach.

It verifies:

```text
Definition ↔ Specification
Specification ↔ Plan / Tasks
Definition ↔ Implementation
Acceptance Criteria ↔ Tests
Architecture Rules ↔ Implementation
Technology Policy ↔ Dependencies
Engineering Guardrails ↔ Implementation
```

The Solution Validator also:

- executes applicable local automated tests;
- verifies that all mandatory evidence passes;
- identifies missing or insufficient tests;
- detects unapproved behavior;
- identifies architecture or technology violations;
- returns `OK`, `KO`, or `BLOCKED`.

If validation is not `OK`, the implementation returns for correction.

When validation is `OK`, the Solution Validator:

1. marks the implementation as `Ready`;
2. creates the Pull Request;
3. provides validation evidence for human review.

The Solution Validator does not merge the Pull Request.

---

# 8. Delivery Lifecycle

The lifecycle is:

```text
HUMAN
Product Manager / Owner
  ↓
Create FD / EN
Define Acceptance Criteria
Approve Definition
  ↓
DERIVED DELIVERY
Solution Designer
  ↓
Specification
  ↓
Plan
  ↓
Tasks
  ↓
IMPLEMENTATION
Builder
  ↓
Tests + Implementation
  ↓
READY VALIDATION
Solution Validator
  ↓
Definition ↔ Spec
Spec ↔ Plan / Tasks
Definition ↔ Implementation
Acceptance Tests
Architecture Tests
Other Mandatory Verification
  ↓
   ┌───────────────┬───────────────┐
   │               │               │
  KO            BLOCKED            OK
   │               │               │
Reimplement     Human clarify       ↓
   │               │             READY
   └───────┬───────┘               ↓
           └── Validate again   Pull Request
                                  ↓
                              CI Verification
                                  ↓
HUMAN VALIDATION
Product Manager / Owner
  ↓
   ┌───────────┬───────────────┬───────────┐
   │           │               │           │
  FIX          CR            ACCEPT
   │           │               │
Correct      Update intent     Approve PR
   │           │               │
   └──────┬────┘               ↓
          │                   Merge
          └── Delivery cycle    ↓
               again          DONE
```

The phases before human validation may be executed manually, with an SDD framework, with direct AI prompting, with specialized agents, or with any combination of these.

The governance model remains the same regardless of implementation mechanism.

---

# 9. Solution Validator Workflow

The Solution Validator operates after the Builder considers implementation complete.

Validation sequence:

```text
1. Read approved FD / EN
2. Read Specification
3. Read Plan / Tasks
4. Read applicable reference guardrails
5. Inspect implementation
6. Verify Definition ↔ Specification conformance
7. Verify Specification / Tasks ↔ Implementation conformance
8. Verify Acceptance Criteria coverage
9. Execute mandatory local tests
10. Verify Architecture Rules
11. Verify technology / engineering compliance
12. Produce validation result
```

## OK

```text
All mandatory checks pass
        ↓
Mark READY
        ↓
Create Pull Request
```

## KO

Use when the problem is objectively correctable without new human intent.

Examples:

- Acceptance Test fails;
- required behavior is missing;
- Architecture Rule is violated;
- an unapproved technology was introduced;
- Specification and implementation differ;
- required test coverage is missing.

Flow:

```text
KO
 ↓
Return findings to Builder
 ↓
Reimplement
 ↓
Validate again
```

No Pull Request is created while validation remains `KO`.

## BLOCKED

Use when a human decision is required.

Examples:

- Definition is ambiguous;
- Definition and guardrails conflict;
- Acceptance Criteria are insufficient;
- a new architecture decision is required;
- Solution Designer introduced an unapproved assumption.

Flow:

```text
BLOCKED
   ↓
Product Manager / Owner clarification
   ↓
Update authoritative artifacts if required
   ↓
Resume delivery
```

The validator must never invent the missing decision.

---

# 10. CI/CD

The project provides a CI/CD mechanism for Pull Requests.

When the Solution Validator creates a Pull Request, CI executes all applicable **deterministic automated verification**.

This may include:

- build;
- unit/domain tests;
- integration tests;
- contract tests;
- Architecture Tests;
- browser-based Acceptance / E2E tests;
- static analysis;
- security/dependency checks where configured.

CI provides an additional independent execution of deterministic evidence before human acceptance.

No AI/LLM execution is required as part of normal CI/CD.

AI-based semantic validation belongs to the Solution Validator stage before the Pull Request.

A Pull Request with failing mandatory CI checks cannot be accepted or merged.

---

# 11. Branch and Pull Request Policy

Each Feature / Enabler is implemented in a dedicated branch.

Recommended naming:

```text
feature/FDxxx-short-name
enabler/ENxxx-short-name
```

The same branch is retained throughout implementation, Fixes, and Change Requests until the Feature / Enabler reaches Done.

The Solution Validator creates the Pull Request only after the implementation reaches `Ready`.

The Pull Request is the human review boundary.

The Product Manager / Owner may:

```text
approve
request FIX
request CHANGE REQUEST
```

Automated actors must not merge the Pull Request.

Final merge is performed by the responsible human after acceptance.

The default branch should reject direct implementation changes outside the Pull Request process.

---

# 12. Final State Model

```text
DRAFT
  ↓
APPROVED
  ↓
DESIGNING
  ↓
IMPLEMENTING
  ↓
VALIDATING
  ├── KO → IMPLEMENTING
  ├── BLOCKED → HUMAN CLARIFICATION
  └── OK
       ↓
     READY
       ↓
   PULL REQUEST
       ↓
      CI
       ↓
HUMAN VALIDATION
  ├── FIX → IMPLEMENTING → VALIDATING → READY
  ├── CR  → DEFINITION UPDATE → DELIVERY CYCLE → READY
  └── ACCEPT
       ↓
      MERGE
       ↓
      DONE
```

`Ready` means:

> **Implementation and automated/agentic verification are complete; human validation may begin.**

`Done` means:

> **The responsible human has accepted the implementation and the Feature / Enabler lifecycle is closed.**

Any later desired behavior starts a new Feature Definition or Enabler Definition.

---

# Governing Principle

> **Definition is human-owned. Design and implementation may be automated. Ready is evidence-based. Done is human-approved.**
