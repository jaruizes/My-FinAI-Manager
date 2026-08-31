# My-FinAI-Manager — Testing Strategy

## Purpose

This document defines the testing strategy for My-FinAI-Manager.

Testing exists to provide confidence that the product:

- behaves according to approved specifications;
- preserves business invariants;
- performs deterministic financial logic correctly;
- respects architecture boundaries;
- integrates safely with infrastructure and external providers;
- produces reliable AI-assisted behavior;
- can evolve without introducing unintended regressions.

Coverage is a quality gate, but coverage alone is not evidence of correctness.

---

# Testing Principles

1. Test behavior, not implementation details.
2. Use TDD for deterministic domain logic.
3. Prefer fast tests at lower levels.
4. Use real infrastructure in integration tests where feasible.
5. Mock external systems at explicit boundaries.
6. Contract-test externally visible interfaces.
7. Architecture rules should be tested automatically where practical.
8. AI behavior requires evaluation in addition to traditional unit tests.
9. Tests must be deterministic unless the behavior itself is probabilistic.
10. Tests must remain understandable and maintainable.
11. Use Testcontainers by default for integration tests against application-managed infrastructure when a suitable container exists.

---

# Testing Pyramid

The preferred balance is:

```text
                 ┌───────────────────┐
                 │   End-to-End      │
                 │      Tests        │
                 └─────────┬─────────┘
                           │
                 ┌─────────▼─────────┐
                 │  Contract / API   │
                 │      Tests        │
                 └─────────┬─────────┘
                           │
                 ┌─────────▼─────────┐
                 │   Integration     │
                 │      Tests        │
                 └─────────┬─────────┘
                           │
                 ┌─────────▼─────────┐
                 │ Unit / Domain     │
                 │      Tests        │
                 └───────────────────┘
```

The majority of deterministic business behavior should be covered at unit/domain level.

---

# 1. Unit Tests

## Purpose

Validate isolated behavior quickly.

Unit tests are appropriate for:

- value objects;
- business rules;
- validation;
- deterministic calculations;
- domain policies;
- state transitions;
- mapping logic where meaningful.

Examples:

- position quantity validation;
- duplicate instrument detection;
- portfolio weight calculation;
- concentration thresholds;
- stop-loss formula behavior;
- business error selection.

## Expectations

Unit tests should:

- run quickly;
- avoid network access;
- avoid real databases;
- be deterministic;
- assert meaningful outcomes;
- use domain terminology.

---

# 2. TDD for Deterministic Logic

Deterministic business logic must normally follow:

```text
RED
Write a failing behavioral test

GREEN
Implement the minimum code

REFACTOR
Improve the design without changing behavior
```

TDD applies particularly to:

- financial calculations;
- validation;
- domain policies;
- transformations with precise expected outcomes;
- business state transitions.

TDD is not measured through Git history.

The engineering expectation is that implementation design is driven by executable behavior rather than tests being added only at the end.

---

# 3. Integration Tests

## Purpose

Verify that application components integrate correctly with real infrastructure.

Examples include integration with:

- PostgreSQL;
- Kafka;
- Neo4j;
- authentication infrastructure;
- HTTP adapters.

## Testcontainers Policy

Testcontainers is the default and mandatory approach for integration tests against infrastructure owned or operated by the application whenever a suitable containerized dependency is available.

Integration tests should execute against real disposable infrastructure rather than mocked substitutes when the purpose of the test is to validate the infrastructure integration itself.

Typical examples include:

```text
Spring Boot
  + PostgreSQL Testcontainer

Python
  + PostgreSQL Testcontainer

Kafka producer/consumer
  + Kafka-compatible Testcontainer

Neo4j adapter
  + Neo4j Testcontainer
```

This policy applies to infrastructure such as:

- PostgreSQL;
- Kafka;
- Neo4j;
- identity providers when a suitable containerized test dependency exists;
- other application-managed infrastructure for which a reliable Testcontainers-compatible image is available.

Testcontainers-based integration tests must be:

- reproducible;
- isolated;
- self-contained;
- independent from infrastructure manually installed on the developer machine;
- safe to execute in CI.

Infrastructure behavior must not be mocked when the purpose of the test is to validate that infrastructure integration itself.

Mocks, stubs, fixtures, and mock servers remain appropriate for true external providers such as:

- market-data APIs;
- news APIs;
- LLM providers;
- SaaS services;

when calling the live provider would introduce cost, nondeterminism, rate limits, external instability, or data/privacy concerns.

If a suitable containerized dependency does not exist or cannot reasonably be used, the exception must be explicit and the alternative integration-test strategy documented.

## What Integration Tests Should Validate

- persistence mappings;
- transaction behavior;
- migrations;
- repository adapters;
- serialization;
- Kafka publication/consumption;
- idempotency;
- infrastructure failure translation;
- query behavior;
- database constraints.

---

# 4. Contract Tests

## REST Contracts

Externally exposed REST APIs must be validated against OpenAPI contracts.

Contract tests should verify:

- required fields;
- response shapes;
- status codes;
- error payloads;
- compatibility;
- request validation.

The contract must not drift silently from implementation.

---

## Event Contracts

If asynchronous contracts are externally exposed, tests should verify:

- schema;
- required fields;
- version compatibility;
- serialization/deserialization;
- producer expectations;
- consumer expectations.

AsyncAPI should be used where defined by architecture policy.

---

# 5. End-to-End Tests

## Purpose

Validate critical business journeys through realistic system boundaries.

End-to-end tests should be limited to high-value scenarios.

Possible examples:

- register a Portfolio;
- add Positions;
- run an initial Portfolio Review;
- update Portfolio composition;
- verify an updated assessment;
- process relevant external information and produce an Alert.

End-to-end tests must not replace lower-level tests.

---

# 6. Architecture Tests

Architecture rules should be automatically validated where practical.

## Java / Spring

Possible architecture checks include:

- domain packages do not depend on infrastructure;
- adapters depend inward;
- modules do not access forbidden packages;
- external-provider SDKs remain inside adapters.

Tools such as ArchUnit may be used.

---

## Python

Equivalent checks may validate:

- import boundaries;
- dependency direction;
- module ownership;
- forbidden infrastructure imports in domain packages.

The exact tool may vary.

---

## General Architecture Tests

CI may verify:

- unauthorized dependencies;
- unapproved technology introduction;
- OpenAPI consistency;
- forbidden direct module access;
- architecture metadata where used.

---

# 7. Database Testing

Database integration tests should use Testcontainers by default so they validate behavior against the real database engine used by the application.

Database tests should validate:

- schema migrations;
- constraints;
- indexes where behavior depends on them;
- transaction boundaries;
- data ownership rules;
- rollback behavior.

Database behavior should not be assumed correct solely because repository unit tests pass.

---

# 8. Kafka and Asynchronous Testing

When Kafka is introduced, integration tests should use a Kafka-compatible Testcontainer by default.

Tests should cover relevant concerns such as:

- event production;
- event consumption;
- serialization;
- schema compatibility;
- duplicate delivery;
- idempotency;
- ordering assumptions;
- retry behavior;
- poison-message handling;
- transactional outbox if used.

Tests must reflect the actual delivery guarantees chosen by architecture.

---

# 9. External Provider Testing

External providers must be tested at adapter boundaries.

## Local / CI Tests

Use deterministic stubs, fixtures, or mock servers for providers such as:

- market-data APIs;
- news APIs;
- LLM providers.

Tests must not depend routinely on live external services.

---

## Provider Compatibility Tests

A smaller set of optional or scheduled tests may verify real provider compatibility when justified.

These tests should be separated from the normal deterministic CI suite because they may be:

- slow;
- costly;
- rate-limited;
- externally unstable.

---

# 10. AI and LLM Testing

Traditional unit tests are not sufficient for probabilistic AI behavior.

AI-enabled capabilities require an evaluation strategy.

## Deterministic Boundary Tests

Always test deterministic logic around AI components normally.

Examples:

- prompt/input assembly;
- provider routing;
- structured-output validation;
- retry/failure behavior;
- evidence propagation;
- safety constraints;
- parsing;
- fallback behavior.

---

## AI Evaluation Datasets

Material AI capabilities should have a curated evaluation dataset.

Examples:

- news relevance classification;
- company/entity extraction;
- portfolio-impact classification;
- investment-thesis assessment;
- recommendation rationale quality.

Evaluation datasets should use:

- synthetic data;
- public data;
- anonymized data.

Private portfolio data must not be committed.

---

## Evaluation Dimensions

Depending on the capability, evaluation may include:

- correctness;
- relevance;
- groundedness;
- evidence use;
- hallucination rate;
- classification precision/recall;
- consistency;
- completeness;
- format compliance.

---

## Regression Evaluation

Changes to:

- prompts;
- models;
- providers;
- retrieval strategy;
- agent workflows;

must not be treated as safe solely because code tests pass.

Relevant evaluation suites should be rerun.

---

# 11. Recommendation Testing

Investment recommendations are a high-impact product capability.

Testing should distinguish:

```text
Facts
Calculations
Interpretations
Recommendations
```

Recommendation tests should verify, where applicable:

- required deterministic inputs were calculated correctly;
- evidence is attached;
- unsupported claims are not introduced;
- the recommendation is represented as advisory;
- confidence/uncertainty is preserved;
- failure to determine is supported.

Exact financial recommendation policy belongs to Feature Definitions.

---

# 12. Stop-Loss Testing

Deterministic Stop-Loss algorithms must be covered thoroughly.

Tests should include:

- normal conditions;
- boundary values;
- extreme volatility;
- missing data;
- invalid prices;
- currency handling;
- rounding;
- historical-data insufficiency.

When an AI component contributes interpretation, deterministic calculation and probabilistic reasoning must be tested separately.

---

# 13. Security Testing

Testing should cover relevant security behavior, including:

- authentication;
- authorization;
- Investor resource isolation;
- invalid/expired credentials;
- unauthorized access;
- secret leakage prevention;
- input validation.

Security-critical backend behavior must not rely solely on frontend testing.

---

# 14. Resilience Testing

Integration tests should cover important failure scenarios where applicable:

- provider timeout;
- provider unavailable;
- database unavailable;
- Kafka unavailable;
- malformed external response;
- rate limiting;
- transient errors.

Tests should verify that failures do not corrupt authoritative business state.

---

# 15. Code Coverage

## Global Coverage Gate

The project target is:

```text
>= 90% overall code coverage
```

This is a minimum engineering quality gate, not the testing objective.

---

## Critical Domain Logic

Critical deterministic financial and business logic should target stronger coverage.

Recommended target:

```text
>= 95% branch coverage
```

for areas such as:

- valuation;
- risk formulas;
- stop-loss calculations;
- portfolio business invariants;
- deterministic recommendation policies.

Particularly critical calculations may require effectively complete branch coverage.

---

## Coverage Exclusions

Coverage exclusions must be limited and justified.

Typical candidates may include:

- generated code;
- trivial framework bootstrap code;
- pure configuration wiring.

Business logic must not be excluded merely to satisfy the coverage threshold.

---

# 16. Mutation Testing

Mutation testing is recommended for critical deterministic domain logic.

It is particularly valuable for detecting tests that execute code without verifying behavior.

Potential targets include:

- financial formulas;
- validation;
- risk rules;
- stop-loss rules.

Mutation testing may initially be introduced selectively due to execution cost.

---

# 17. Test Data

Test data must be:

- deterministic;
- understandable;
- minimal for the scenario;
- synthetic, anonymized, or public.

Do not commit real private portfolio information.

Fixtures should use realistic financial concepts where useful but must not accidentally become hidden product requirements.

---

# 18. Test Naming

Tests should describe behavior.

Preferred style:

```text
givenValidPortfolio_whenImported_thenPortfolioIsCreated
```

or:

```text
test_import_rejects_duplicate_financial_instrument
```

depending on language conventions.

Names should communicate:

- scenario;
- action;
- expected result.

---

# 19. CI Quality Gates

The normal CI pipeline should eventually enforce, as applicable:

```text
Compile / Build
      ↓
Unit Tests
      ↓
Integration Tests
      ↓
Architecture Tests
      ↓
Contract Validation
      ↓
Coverage Gate
      ↓
Static Analysis
      ↓
Security / Dependency Checks
```

AI evaluation suites may run:

- in normal CI for fast deterministic evaluations;
- in dedicated workflows for slower/costly evaluations.

---

# 20. Test Failure Policy

A failing required test blocks completion of the change.

Tests must not be:

- disabled;
- ignored;
- weakened;
- deleted;

solely to make CI pass.

If an approved requirement changes, update the tests and specification together.

---

# Testing Strategy Summary

```text
Feature Requirement
       ↓
Acceptance Scenarios
       ↓
Domain / Unit Tests
       ↓
Integration Tests
       ↓
Contract Tests
       ↓
Architecture Tests
       ↓
Selected End-to-End Tests
       ↓
AI Evaluations where applicable
       ↓
Coverage + Quality Gates
```

The central principle is:

> Confidence comes from meaningful behavioral verification, not from coverage numbers alone.
