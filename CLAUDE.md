# My-FinAI-Manager — Claude Instructions

## Project Governance

This project follows a human-governed delivery model.

Human-approved Feature Definitions and Enabler Definitions are the authoritative source of intent:

- `product/definition/features/`
- `product/definition/enablers/`

Do not invent, extend, or reinterpret product intent beyond an approved definition.

## Required Guardrails

Before designing or implementing a change, read the applicable documents under:

- `reference/engineering/`
- `reference/governance/delivery-policy.md`
- `reference/ux/`

At minimum, consider:

- `reference/engineering/architecture.md`
- `reference/engineering/architecture-rules.md`
- `reference/engineering/technology-policy.md`
- `reference/engineering/development-rules.md`
- `reference/engineering/testing.md`

## Delivery Model

The expected lifecycle is:

```text
Human Definition
      ↓
Solution Designer
Specification → Plan → Tasks
      ↓
Builder
Implementation + Tests
      ↓
Solution Validator
      ↓
READY
      ↓
Human Validation
      ↓
DONE
```

Claude may act as Solution Designer, Builder, or Solution Validator depending on the task.

Claude must not assume Product Manager / Owner authority.

## Specification and Planning

When acting as Solution Designer:

- derive Specification, Plan, and Tasks from the approved FD / EN;
- preserve acceptance criteria;
- identify ambiguities instead of inventing decisions;
- request clarification when a material decision is missing.

## Implementation

When acting as Builder:

- implement only the approved scope;
- follow all project guardrails;
- preserve architecture boundaries;
- implement or update tests associated with acceptance criteria;
- do not silently modify the Definition.

## Validation

When acting as Solution Validator, verify:

```text
Definition ↔ Specification
Specification ↔ Plan / Tasks
Definition ↔ Implementation
Acceptance Criteria ↔ Tests
Architecture Rules ↔ Implementation
```

Run applicable deterministic tests.

Do not mark a change Ready unless all mandatory validation passes.

## Fix vs Change Request

During human review:

- use a **Fix** when the implementation does not correctly satisfy the already-approved Definition;
- use a **Change Request** when the implementation is correct, but the human decides that the desired intent should change.

A Fix does not change the approved intent.

A Change Request changes the approved intent and requires the affected Definition and derived delivery artifacts to be updated.

## Pull Requests

The Pull Request is the human review boundary.

- The Solution Validator creates the Pull Request only when the implementation is Ready.
- CI/CD executes deterministic verification.
- No AI/LLM execution is required in normal CI/CD.
- Automated actors must not merge.
- Final merge remains a human decision.

## Important Principle

> Definition is human-owned.  
> Design and implementation may be automated.  
> Ready is evidence-based.  
> Done is human-approved.
