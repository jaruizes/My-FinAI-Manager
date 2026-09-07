# Feature Definition Template

> **Status:** Draft  
> **Feature ID:** FDNNN  
> **Feature Name:** Short descriptive name  
> **Owner:** TBD  
> **Created:** YYYY-MM-DD  
> **Last Updated:** YYYY-MM-DD  

---

# 1. Purpose

Describe why this feature exists and the product problem it addresses.

Focus on business/user intent rather than technical implementation.

Example questions:

- What problem does this feature solve?
- Why is it valuable?
- What outcome should be possible after implementation?

---

# 2. User Value

Describe the value delivered to the primary user or actor.

Example:

> As an Investor, I want to register my investment portfolio so that My-FinAI-Manager can analyse, value, monitor, and provide recommendations about it.

---

# 3. Primary Actor

Identify the main actor responsible for initiating or benefiting from the feature.

Examples:

- Investor
- Scheduled Platform Process
- External Information Source

---

# 4. Scope

Define what is included in this feature.

Be explicit about the product behavior that belongs to this Feature Definition.

## In Scope

- ...
- ...
- ...

## Out of Scope

- ...
- ...
- ...

Out-of-scope items should not be implemented implicitly.

---

# 5. Preconditions

Describe the relevant conditions that must be true before the feature can be used.

Examples:

- The Investor is authenticated.
- The Portfolio exists.
- Required market information is available.

Only include preconditions that are meaningful from a product perspective.

---

# 6. Main User Flow

Describe the expected happy-path behavior in business terms.

```text
Actor
  ↓
Action
  ↓
System Response
  ↓
Business Outcome
```

Example structure:

1. The Investor ...
2. The system ...
3. The Investor ...
4. The system ...
5. The resulting business state is ...

---

# 7. Alternative and Error Flows

Describe meaningful deviations from the main flow.

Focus on product-visible behavior.

Examples:

## Invalid Input

- Condition:
- Expected behavior:
- User-visible outcome:

## Duplicate Information

- Condition:
- Expected behavior:
- User-visible outcome:

## External Dependency Unavailable

- Condition:
- Expected behavior:
- User-visible outcome:

---

# 8. Business Rules

List explicit business rules introduced or relied upon by this feature.

Use stable identifiers where useful.

Example:

## BR-001 — Rule Name

**Rule**

Describe the business rule.

**Rationale**

Explain why the rule exists if useful.

---

# 9. Acceptance Criteria

Acceptance criteria must describe observable behavior.

Prefer scenario-oriented wording.

## AC-001 — Scenario Name

**Given**

...

**When**

...

**Then**

...

---

## AC-002 — Scenario Name

**Given**

...

**When**

...

**Then**

...

---

# 10. Affected Functional Domains

Reference the relevant domains defined in:

```text
product/definition/global/domains.md
```

Examples:

- Portfolio Management
- Financial Instruments
- Valuation
- Risk
- Market Intelligence
- News & Events
- Investment Thesis
- Recommendations
- Stop-Loss Management
- Portfolio Review

Do not interpret an affected domain as a required microservice or deployment unit.

---

# 11. Information Objects

Reference the business information objects affected by the feature.

Source:

```text
product/definition/global/information-model.md
```

Examples:

- Portfolio
- Position
- Financial Instrument
- Investment Thesis
- Risk
- Recommendation
- Evidence

For each relevant object, optionally describe the impact.

| Information Object | Impact |
|---|---|
| Portfolio | Created / read / updated / evaluated |
| Position | ... |

This section must remain at business-information level and must not define database tables, ORM entities, or storage schemas.

---

# 12. Relevant Business Events

Reference events from:

```text
product/definition/global/business-events.md
```

Examples:

- PortfolioCreated
- PositionAdded
- PortfolioUpdated
- PortfolioValued
- RiskDetected
- RecommendationGenerated

If the feature requires a new reusable business event that is not already defined globally, identify it explicitly.

A business event does not imply Kafka or any other technical messaging mechanism.

---

# 13. State Changes

Describe the business state that changes as a result of the feature.

Example:

```text
Before
Portfolio does not exist

        ↓

Feature Execution

        ↓

After
Portfolio exists with validated Positions
```

Include only meaningful business state transitions.

---

# 14. Data and Validation Requirements

Describe product-level validation rules.

Examples:

- Required information
- Allowed formats
- Uniqueness rules
- Value ranges
- Cross-field constraints
- Business consistency checks

Do not define implementation-specific validation libraries or database constraints here.

---

# 15. UX / Interaction Requirements

Describe relevant user interaction expectations.

This may include:

- required screens;
- user actions;
- information displayed;
- validation feedback;
- confirmation behavior;
- loading or progress expectations when product-relevant.

If a visual prototype exists, reference it.

Example:

```text
UX Reference:
product/definition/features/FDNNN-short-name/ux/prototype-reference.md
```

A visual prototype helps clarify the experience but does not override this Feature Definition.

If a prototype contains behavior not explicitly approved here, that behavior is not automatically a requirement.

---

# 16. Explainability Requirements

Describe what the user must be able to understand about the result.

Relevant questions may include:

- Why was this result produced?
- Which data contributed?
- Which Evidence was used?
- Was the outcome deterministic or AI-assisted?
- What uncertainty exists?

Use this section only where explainability is relevant to the feature.

---

# 17. AI / LLM Involvement

Indicate whether the feature requires AI-assisted behavior.

## AI Required?

- [ ] No
- [ ] Yes
- [ ] To be evaluated during architecture impact analysis

If Yes, describe the business capability expected from AI.

Examples:

- summarization;
- classification;
- semantic relevance;
- entity extraction;
- recommendation rationale;
- natural-language interaction.

Do not select a specific provider or model here unless it is a product requirement.

AI must not replace deterministic calculations when deterministic logic is available.

---

# 18. External Information or Dependencies

Describe external information needed from a product perspective.

Examples:

- Market prices
- Company information
- News
- Economic indicators
- Exchange rates

Do not select a technical provider here unless the provider itself is part of the requirement.

---

# 19. Temporal Requirements

Describe relevant time-related behavior.

Examples:

- Effective time of information
- Observation time
- Review period
- Historical state
- Expiration
- Scheduled behavior
- Staleness requirements

Distinguish where relevant between:

- when something happened;
- when My-FinAI-Manager observed it;
- when analysis was performed;
- when a recommendation was generated.

---

# 20. Evidence and Provenance Requirements

If the feature creates analytical or advisory conclusions, describe the provenance expectations.

Examples:

- supporting Evidence must be retained;
- external source must be identifiable;
- deterministic calculation inputs must be reconstructable;
- AI-assisted conclusions must preserve relevant provenance.

---

# 21. Security and Privacy Considerations

Describe product-relevant security or privacy requirements.

Examples:

- Only the owning Investor can access the Portfolio.
- Private portfolio information must not be exposed to another user.
- Only required information may be sent to an external provider.

Technical security implementation belongs to architecture and implementation planning.

---

# 22. Non-Functional Requirements

Include only non-functional requirements that are genuinely required by this feature.

Possible categories:

## Performance

- ...

## Scalability

- ...

## Availability

- ...

## Reliability

- ...

## Auditability

- ...

## Accessibility

- ...

Do not invent numerical targets without an explicit requirement.

---

# 23. Architecture Impact Expectations

This section identifies known architectural implications without prescribing the final solution.

Examples:

- New external API capability may be required.
- Existing backend component may need to be extended.
- New persistence may be required.
- Asynchronous processing may need evaluation.
- New external provider integration may be required.

Use:

```text
None known
```

when no architecture impact has yet been identified.

The formal Architecture Impact Analysis happens later in the SDD lifecycle.

---

# 24. Expected Implementation Areas

Identify implementation areas that are likely to be affected.

This is informative and may be refined during planning.

```text
implementation/platform/
├── frontend/        [Yes / No / TBD]
├── backend/         [Yes / No / TBD]
├── contracts/       [Yes / No / TBD]
└── infrastructure/  [Yes / No / TBD]
```

A vertical feature may modify several implementation areas.

This does not mean the feature owns a separate executable application.

---

# 25. Testing Expectations

Identify important behavior that must be verified.

Examples:

- business-rule tests;
- validation scenarios;
- integration with persistence;
- contract verification;
- external-provider boundary behavior;
- AI evaluation scenarios.

Detailed test design belongs to the implementation plan and testing strategy.

---

# 26. Dependencies on Other Features

List known Feature Definition dependencies.

Examples:

```text
Depends on:
- FD001-...

Enables:
- FD003-...
```

Use:

```text
None
```

when there are no known dependencies.

---

# 27. Open Questions

List unresolved questions that materially affect the feature.

These must be clarified before implementation when they affect product behavior.

| ID | Question | Status | Decision |
|---|---|---|---|
| Q-001 | ... | Open | |

Do not silently convert open questions into implementation assumptions.

---

# 28. Explicit Assumptions

List assumptions that have been deliberately accepted.

| ID | Assumption | Approved By |
|---|---|---|
| A-001 | ... | ... |

This section should not contain assumptions invented by an AI agent without human approval.

---

# 29. Examples

Provide examples when they materially reduce ambiguity.

Examples may include:

- sample Portfolio;
- sample Position;
- example Recommendation;
- example user flow;
- example error scenario.

Examples are illustrative unless explicitly identified as acceptance criteria or business rules.

---

# 30. Success Criteria

Describe how product success for this feature will be recognized.

Examples:

- The Investor can successfully complete the intended workflow.
- The resulting business state is correct.
- Required analysis becomes possible.
- The feature integrates into the current executable platform.

Avoid implementation metrics unless they represent actual product success.

---

# 31. Traceability

## Product Sources

Relevant global definitions:

- `product/definition/global/vision.md`
- `product/definition/global/context.md`
- `product/definition/global/glossary.md`
- `product/definition/global/domains.md`
- `product/definition/global/information-model.md`
- `product/definition/global/business-events.md`

## Architecture Sources

- `product/architecture/architecture.md`
- `product/architecture/technology-policy.md`
- `product/architecture/architecture-rules.md`

## Engineering Sources

- `product/engineering/development-rules.md`
- `product/engineering/testing-strategy.md`
- `product/engineering/definition-of-done.md`

## Governance Sources

- `product/governance/sdd-policy.md`
- `product/governance/ai-development-policy.md`

---

# 32. Human Approval

Feature Definitions are human-governed artifacts.

Before entering formal specification, the responsible human should confirm:

- [ ] Purpose is correct.
- [ ] Scope is explicit.
- [ ] Business rules are deliberate.
- [ ] Acceptance criteria reflect intended behavior.
- [ ] Open product questions have been identified.
- [ ] No AI-generated assumption has silently become a requirement.
- [ ] Affected domains, information objects, and business events are consistent with global definitions.

**Approved by:**  
**Date:**  
**Status:** Draft / Approved / Superseded
