# My-FinAI-Manager — Definition of Done

## Purpose

This document defines when a feature, change, or implementation task is considered complete in My-FinAI-Manager.

“Done” means more than:

- code compiles;
- code runs locally;
- an AI agent says implementation is complete;
- tests exist;
- a pull request has been opened.

A change is Done only when the applicable product, architecture, engineering, testing, documentation, and operational expectations have been satisfied.

---

# Definition of Done Checklist

## 1. Product and Specification

A change is not Done unless:

- [ ] It is traceable to an approved Feature Definition or approved technical work item.
- [ ] The formal specification is approved when the SDD lifecycle requires one.
- [ ] All implemented behavior is within approved scope.
- [ ] No new business requirement has been silently introduced.
- [ ] Acceptance scenarios are implemented.
- [ ] Out-of-scope behavior has not been added speculatively.

---

# 2. Architecture

- [ ] The implementation complies with `architecture.md`.
- [ ] The implementation complies with `architecture-rules.md`.
- [ ] Technology choices comply with `technology-policy.md`.
- [ ] Hexagonal Architecture boundaries are respected.
- [ ] Domain logic does not depend on infrastructure.
- [ ] Component/module ownership is clear.
- [ ] No module directly accesses another module's owned persistence.
- [ ] No unapproved technology has been introduced.
- [ ] Any significant architectural change has an approved ADR.
- [ ] Architecture diagrams are updated if the approved architecture changed.

---

# 3. Code Quality

- [ ] Code is readable and uses product/domain terminology consistently.
- [ ] Responsibilities are cohesive.
- [ ] No unnecessary abstraction or speculative infrastructure has been introduced.
- [ ] Precision-sensitive financial calculations use safe numeric representations.
- [ ] Optional and unknown states are represented intentionally.
- [ ] Errors are explicit and meaningful.
- [ ] Unused code and dependencies have been removed.
- [ ] No unrelated refactoring is mixed into the change without justification.

---

# 4. Testing

- [ ] Deterministic domain logic was developed according to the project's TDD expectations.
- [ ] Unit/domain tests cover the relevant business behavior.
- [ ] Integration tests exist where infrastructure behavior matters.
- [ ] Contract tests exist for externally visible interfaces where applicable.
- [ ] Architecture tests cover enforceable architecture rules where applicable.
- [ ] End-to-end tests cover critical journeys when warranted.
- [ ] AI evaluation exists for material probabilistic behavior where applicable.
- [ ] Tests cover important failure and edge cases.
- [ ] All required tests pass.

---

# 5. Coverage

- [ ] Overall code coverage is at least 90%.
- [ ] Critical deterministic business logic meets the stronger coverage expectations defined in `testing-strategy.md`.
- [ ] Coverage exclusions are justified.
- [ ] Tests provide meaningful assertions rather than executing code only to increase coverage.

---

# 6. APIs and Contracts

Where applicable:

- [ ] REST contracts are defined with OpenAPI.
- [ ] Implementation matches the approved contract.
- [ ] Required request validation is implemented.
- [ ] Response schemas are correct.
- [ ] Error contracts are stable and machine-readable.
- [ ] Provider-specific structures do not leak through business contracts.
- [ ] Breaking changes are explicit and approved.

For asynchronous interfaces:

- [ ] Event contracts are documented where required.
- [ ] Schema compatibility is considered.
- [ ] Idempotency and delivery assumptions are tested where applicable.

---

# 7. Persistence

Where applicable:

- [ ] Persistence ownership is explicit.
- [ ] Database migrations are included.
- [ ] Migrations are tested.
- [ ] Constraints necessary for correctness are present.
- [ ] Transaction boundaries are intentional.
- [ ] No accidental dual-write consistency problem has been introduced.
- [ ] Specialized stores such as Neo4j have an explicit ownership/projection strategy.

---

# 8. External Integrations

Where applicable:

- [ ] External integrations are behind ports/adapters.
- [ ] Provider-specific models stay inside adapters.
- [ ] Timeouts are configured.
- [ ] Retry behavior is deliberate.
- [ ] Rate-limit behavior is considered.
- [ ] External failures are translated safely.
- [ ] Integration tests use deterministic mocks/stubs/containers where appropriate.

---

# 9. AI / LLM Capabilities

Where applicable:

- [ ] LLM providers are accessed through provider-neutral ports.
- [ ] Deterministic calculations are not delegated to the LLM.
- [ ] Structured AI outputs are validated.
- [ ] AI interpretations are distinguishable from facts and deterministic calculations.
- [ ] Material conclusions preserve supporting Evidence.
- [ ] Relevant confidence/uncertainty is preserved.
- [ ] The capability handles “cannot determine” safely.
- [ ] Provider/model changes have been evaluated where relevant.
- [ ] Sensitive information is not unnecessarily exposed to external providers.
- [ ] AI calls are observable without leaking sensitive prompt contents.

---

# 10. Security and Privacy

- [ ] Authentication requirements are implemented where applicable.
- [ ] Authorization is enforced backend-side.
- [ ] Investor-owned resources are properly isolated.
- [ ] No secrets exist in source control.
- [ ] Logs do not expose secrets or unnecessary sensitive data.
- [ ] External providers receive only the information required for the use case.
- [ ] Inputs are validated at trust boundaries.

---

# 11. Observability

For deployable backend components:

- [ ] Structured logs are implemented.
- [ ] Relevant metrics exist.
- [ ] Relevant traces exist.
- [ ] Correlation is preserved across required boundaries.
- [ ] Important external calls are observable.
- [ ] Business-critical analytical decisions preserve enough information for later explanation/audit.

---

# 12. Resilience

Where applicable:

- [ ] External call timeouts are explicit.
- [ ] Retry policies are safe.
- [ ] Idempotency is implemented where required.
- [ ] Failure does not leave authoritative domain state partially corrupted.
- [ ] Graceful degradation has been considered for non-critical dependencies.
- [ ] Critical failure scenarios are tested.

---

# 13. Documentation

- [ ] Relevant code documentation is present where implementation reasoning is non-obvious.
- [ ] Public contracts are documented.
- [ ] Feature documentation reflects approved behavior.
- [ ] ADRs have been added or updated for significant architecture decisions.
- [ ] Architecture diagrams reflect current approved architecture.
- [ ] Product-level documents are updated when a feature intentionally changes global definitions.
- [ ] No generated document silently contradicts human-governed product documentation.

---

# 14. Repository Hygiene

- [ ] No build artifacts are committed.
- [ ] No local IDE or environment files are committed unless intentionally shared.
- [ ] No private datasets are committed.
- [ ] No real credentials or tokens are committed.
- [ ] Test data is synthetic, anonymized, or public.
- [ ] Dependency changes are intentional.

---

# 15. CI/CD

- [ ] Build succeeds in CI.
- [ ] Required tests pass in CI.
- [ ] Coverage gates pass.
- [ ] Static analysis passes.
- [ ] Architecture checks pass.
- [ ] Contract validation passes where applicable.
- [ ] Dependency/security checks pass when configured.
- [ ] Container build passes when the component is containerized.

---

# 16. Review

- [ ] The implementation has been reviewed against the approved specification.
- [ ] The implementation has been reviewed against architecture rules.
- [ ] Significant AI-generated code has been critically reviewed rather than accepted blindly.
- [ ] Known limitations and deferred work are explicit.
- [ ] No unresolved blocker remains hidden in comments or TODOs.

---

# 17. Product Acceptance

For a complete Feature Definition:

- [ ] Every required acceptance scenario has evidence of successful execution.
- [ ] The resulting behavior matches human product intent.
- [ ] The product owner / responsible human can understand what was implemented.
- [ ] The implementation does not rely on undocumented assumptions.

---

# Minimum Pull Request Evidence

A pull request implementing a feature should make it possible to answer:

```text
What requirement does this implement?
Which specification/task does it trace to?
What changed?
Why was this design chosen?
How was it tested?
What architecture boundaries are affected?
Were any ADRs required?
What evidence shows acceptance criteria pass?
```

Where possible, these should be visible directly from:

- the PR description;
- commits;
- linked specification/tasks;
- tests;
- ADRs.

---

# Definition of Done by Change Type

Not every checklist item applies equally to every change.

## Documentation-Only Change

Minimum expectations:

- relevant documentation updated;
- internal consistency checked;
- no broken references;
- architecture/product governance respected.

---

## Deterministic Domain Change

Minimum expectations:

- approved requirement;
- TDD;
- unit/domain tests;
- >= 90% overall project coverage gate;
- stronger critical-domain coverage where applicable;
- architecture compliance;
- integration tests when persistence is involved.

---

## API Change

Additionally requires:

- OpenAPI update;
- contract validation;
- error model validation;
- compatibility review.

---

## Persistence Change

Additionally requires:

- migration;
- migration test;
- data ownership review;
- rollback/evolution consideration.

---

## Kafka / Async Change

Additionally requires:

- architectural justification;
- contract/schema;
- idempotency review;
- delivery-semantics review;
- integration tests;
- observability.

---

## AI / LLM Change

Additionally requires:

- provider abstraction;
- deterministic boundary verification;
- evaluation dataset or evaluation scenarios;
- groundedness/evidence checks;
- regression evaluation;
- observability;
- privacy review.

---

## Architecture Change

Additionally requires:

- ADR when significant;
- architecture documentation update;
- architecture diagram update;
- conformance rules/tests updated where practical.

---

# Completion Rule

A change is Done only when:

```text
Product Intent
    +
Architecture Compliance
    +
Meaningful Tests
    +
Quality Gates
    +
Security / Privacy
    +
Observability where applicable
    +
Required Documentation
    +
Human Review
    =
DONE
```

Passing CI is necessary, but not sufficient.

The final criterion is that the resulting software is demonstrably aligned with the human-governed product definition.
