# EN006 — Establish AI Model Integration

> **Status:** Approved  
> **Enabler ID:** EN006  
> **Enabler Name:** Establish AI Model Integration  
> **Supports:** Future AI-assisted portfolio analysis, explanations, risk insights, recommendations, news intelligence, conversational portfolio interaction, and agentic capabilities  
> **Last Updated:** 2026-09-04

---

# 1. Purpose

Establish a provider-agnostic AI integration capability for My-FinAI-Manager.

The objective is to allow business capabilities to use LLMs and future AI models without coupling the core/business layers to a specific vendor, SDK, API contract, model family, or deployment platform.

EN006 must provide a governed foundation for:

- provider-neutral AI invocation;
- configurable system prompts;
- task-specific prompts;
- prompt versioning;
- guardrails;
- structured output;
- token control;
- context-window control;
- cost visibility;
- observability without exposing sensitive data;
- timeout and failure management;
- model/provider replacement without affecting business logic;
- future support for multiple providers and model-routing policies.

EN006 does not define any specific Portfolio AI feature.

---

# 2. Motivation

Future features may need capabilities such as:

```text
Portfolio Analysis
Risk Explanation
Natural-Language Portfolio Summary
News Interpretation
Recommendation Explanation
Ask My Portfolio
Scenario Explanation
Agentic Workflows
```

Those capabilities must not directly depend on:

```text
OpenAI
Anthropic
AWS Bedrock
Google Gemini
Azure OpenAI
local models
```

Desired dependency model:

```text
Business capability
       ↓
AI capability port
       ↓
AI orchestration / policy
       ↓
provider-neutral model port
       ↓
provider adapter
       ↓
external or local model
```

The provider must be replaceable without changing domain/business logic.

---

# 3. Architectural Principles

## 3.1 AI Is an External Capability

Core/domain logic must never depend on:

- provider SDKs;
- provider request/response DTOs;
- model-specific message types;
- provider-specific token counters;
- provider authentication;
- provider-specific tool-calling schemas.

## 3.2 Deterministic Financial Logic Remains Outside AI

AI must not replace deterministic calculations already owned by the core.

Examples:

```text
Portfolio valuation
FX conversion
Position weights
Sector percentages
Stop-loss arithmetic
Financial ratios
Scenario arithmetic
```

AI may interpret, summarize, classify, explain, identify patterns, or generate insights over supplied facts.

## 3.3 Inputs Must Be Explicit

AI features must receive explicitly prepared context.

```text
Domain data
   ↓
Context Builder
   ↓
AI Request
   ↓
Model
```

The model must not be expected to know the current Portfolio, valuation, sector allocation, or market state unless those facts are supplied.

## 3.4 Provider Independence

Provider/model selection is an infrastructure concern.

Core/business logic must not contain provider-specific branching.

---

# 4. Scope

## In Scope

- Provider-neutral AI model port.
- AI request/response models.
- System prompt support.
- Task-specific prompts.
- Prompt templates.
- Prompt versioning.
- Guardrail extension points.
- Input guardrails.
- Output guardrails.
- Structured output.
- JSON schema / typed response validation.
- Token counting/estimation abstraction.
- Input token limits.
- Output token limits.
- Context-size limits.
- Cost metadata and controls.
- Provider/model metadata collection.
- Latency tracking.
- Timeout handling.
- Bounded retry policies.
- Provider-neutral error model.
- OpenTelemetry-compatible observability.
- Docker Compose observability stack for local development.
- OpenTelemetry Collector.
- Jaeger trace visualization.
- Prometheus metrics storage/scraping.
- Grafana dashboard for AI observability.
- Sensitive-data-safe logging/tracing.
- Correlation IDs.
- Model/provider configuration.
- Common generation parameters.
- Future multi-provider support.
- Testability without live providers.
- Prompt/model traceability.
- AI invocation policy.
- Optional input redaction and output sanitization.

## Out of Scope

- Portfolio Analysis itself.
- AI-generated recommendations.
- News retrieval.
- RAG.
- Vector databases.
- Embeddings.
- Agent orchestration.
- Tool calling as a business capability.
- MCP.
- Human-in-the-loop workflows.
- Autonomous trading.
- Model fine-tuning/training.
- AI conversation memory.

These may be introduced by later features/enablers.

---

# 5. Core-Facing AI Capability

Conceptually:

```java
interface AiModelPort {
    AiResponse generate(AiRequest request);
}
```

Business features should preferably depend on higher-level task-specific ports.

Example:

```text
PortfolioAnalysisUseCase
        ↓
PortfolioAnalysisAiPort
        ↓
AiModelPort
        ↓
provider adapter
```

This keeps business intent separate from generic model invocation.

---

# 6. Provider Adapter Architecture

```text
Business / AI Use Case
          ↓
      AiModelPort
          ↓
  AiProviderRouter
          ↓
      Provider Adapter
```

Possible adapters:

```text
OpenAiModelAdapter
AnthropicModelAdapter
BedrockModelAdapter
GeminiModelAdapter
LocalModelAdapter
```

EN006 does not require all of them initially.

At least one concrete adapter is required when the first AI feature is implemented.

---

# 7. Provider Routing

Provider/model selection must be configuration-driven.

Conceptually:

```yaml
ai:
  default-provider: openai
  default-model: <configured-model>
```

Future task-specific routing:

```yaml
ai:
  tasks:
    portfolio-analysis:
      provider: anthropic
      model: <model-a>

    news-summary:
      provider: openai
      model: <model-b>
```

A future router may route by:

```text
task
cost
latency
context size
capability
availability
region
```

Automatic fallback is not required initially.

---

# 8. Provider-Neutral Request

Conceptually:

```text
AiRequest
- taskType
- systemPrompt
- userPrompt
- context
- outputSchema?
- maxOutputTokens
- temperature?
- metadata
- correlationId
```

Provider-specific message classes must not leak outside infrastructure.

---

# 9. Provider-Neutral Response

Conceptually:

```text
AiResponse
- content
- structuredContent?
- provider
- model
- inputTokens?
- outputTokens?
- totalTokens?
- estimatedCost?
- latencyMs
- finishReason?
- requestId?
- generatedAt
```

Only normalized provider metadata may escape the adapter.

---

# 10. System Prompt

EN006 must support a governed, configurable, versioned system prompt.

It should establish principles such as:

```text
- Do not invent financial facts.
- Use only supplied facts unless external retrieval is explicitly enabled by the task.
- Do not replace deterministic financial calculations.
- Explicitly state uncertainty.
- Do not fabricate missing prices, sectors, currencies, or Portfolio data.
- Do not claim actions were executed unless they actually were.
- Treat output as decision support, not autonomous execution.
```

The exact text remains a governance/configuration artifact.

---

# 11. Prompt Layering

Prompt composition must support:

```text
Global System Prompt
        +
Task Instructions
        +
Business Context
        +
User / Task Prompt
```

Prompt composition must be centralized rather than duplicated across controllers or adapters.

---

# 12. Prompt Templates and Versioning

Prompts must not be scattered as arbitrary strings throughout the codebase.

Suggested initial organization:

```text
prompts/
├── global-system/
│   └── v1
├── portfolio-analysis/
│   ├── v1
│   └── v2
└── portfolio-explanation/
    └── v1
```

Every invocation must identify:

```text
promptId
promptVersion
```

Persisted AI-generated artifacts should be able to record:

```text
provider
model
promptId
promptVersion
generatedAt
```

---

# 13. Structured Output

Business features should prefer structured output when results are consumed programmatically.

Example:

```json
{
  "summary": "...",
  "risks": [
    {
      "type": "SECTOR_CONCENTRATION",
      "severity": "HIGH",
      "explanation": "..."
    }
  ]
}
```

EN006 must support:

```text
output schema
typed/JSON response
schema validation
```

Invalid structured output must be rejected explicitly.

---

# 14. Guardrail Architecture

Guardrails must be provider-neutral at EN006 level.

```text
AI Request
   ↓
Input Guardrails
   ↓
Model Provider
   ↓
Output Guardrails
   ↓
Validated AI Response
```

Provider-native guardrails may complement but not replace application-level policy.

---

# 15. Input Guardrails

Potential input guardrails include:

- prompt injection detection;
- system-prompt override detection;
- sensitive-data detection/redaction;
- input-size enforcement;
- unsafe content filtering;
- unsupported task detection;
- malformed context rejection;
- unexpected action/tool instruction detection.

The initial implementation may support a subset, but the extension point is mandatory.

---

# 16. Output Guardrails

Potential output guardrails include:

- structured-schema validation;
- required-field validation;
- sensitive-data leakage detection;
- unsupported factual-claim checks;
- unsafe content detection;
- prohibited financial-action language;
- malformed structured response detection.

Guardrail rejection must produce a controlled provider-neutral failure.

---

# 17. Sensitive Data Policy

Data minimization is mandatory.

EN006 must support an optional sanitization/redaction stage:

```text
Business Context
      ↓
SensitiveDataSanitizer
      ↓
AI Request
```

Do not log or trace by default:

```text
user identity
email
account identifiers
complete raw Portfolio payloads
free text containing personal data
authentication tokens
API keys
provider credentials
raw prompts
raw completions
```

Only the minimum information required by an AI task should be sent to the provider.

---

# 18. Observability

AI calls must be observable without recording sensitive prompt/response content by default.

Capture at minimum:

```text
ai.provider
ai.model
ai.task
ai.prompt.id
ai.prompt.version
ai.input.tokens
ai.output.tokens
ai.total.tokens
ai.latency.ms
ai.success
ai.finish.reason
ai.guardrail.result
ai.correlation.id
```

Where available:

```text
ai.estimated.cost
ai.provider.request.id
```

---

# 19. OpenTelemetry and Local Observability Platform

AI invocations must participate in the platform OpenTelemetry strategy.

Conceptually:

```text
Business operation
      ↓
AI use-case span
      ↓
AI invocation span
      ↓
provider HTTP span
```

Where compatible with the project's OpenTelemetry version, use semantic attributes such as:

```text
gen_ai.system
gen_ai.request.model
gen_ai.operation.name
gen_ai.usage.input_tokens
gen_ai.usage.output_tokens
```

Additional application-level attributes may include:

```text
ai.task
ai.prompt.id
ai.prompt.version
ai.guardrail.result
ai.success
ai.estimated.cost
```

Raw prompts, completions, Portfolio payloads, user identifiers, credentials, and other sensitive content must not be stored in production spans by default.

## 19.1 Docker Compose Requirement

EN006 must update the local `implementation/platform` Docker Compose environment to include the infrastructure required to receive, store, inspect, and visualize AI-related telemetry.

The local observability topology should be:

```text
Spring Boot core-service
        │
        │ OTLP traces / metrics
        ▼
OpenTelemetry Collector
        │
        ├── traces  → Jaeger
        │
        └── metrics → Prometheus
                         │
                         ▼
                      Grafana
```

The exact ports and image versions are planning/implementation details, but the following components are required:

```text
OpenTelemetry Collector
Jaeger
Prometheus
Grafana
```

## 19.2 Expected Repository Impact

Conceptually:

```text
implementation/platform/
├── docker-compose.yml
└── observability/
    ├── otel-collector-config.yaml
    ├── prometheus.yml
    └── grafana/
        ├── provisioning/
        │   ├── datasources/
        │   └── dashboards/
        └── dashboards/
            └── ai-observability.json
```

Exact paths may be adapted to the existing repository conventions.

## 19.3 OpenTelemetry Collector

The OpenTelemetry Collector must be the central telemetry ingestion point for application-generated traces and metrics.

Conceptually:

```text
core-service
   ↓ OTLP
otel-collector
```

The Collector should expose OTLP endpoints required by the current platform instrumentation.

It must forward:

```text
traces  → Jaeger
metrics → Prometheus-compatible path
```

The Collector configuration must not export raw prompt/completion bodies.

## 19.4 Core Service Configuration

The Spring Boot application must be configurable to export telemetry to the Collector using environment/runtime configuration.

Conceptually:

```text
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318
OTEL_SERVICE_NAME=my-finai-manager-core
```

Exact property names depend on the current OpenTelemetry integration.

No observability configuration may contain AI provider credentials.

## 19.5 Jaeger

Jaeger must be included in the local Compose environment for trace inspection.

A developer must be able to inspect a trace conceptually similar to:

```text
PortfolioAnalysisUseCase
        ↓
AiInvocationService
        ↓
AiModelPort
        ↓
ProviderAdapter
        ↓
external AI provider
```

The trace must expose operational metadata while excluding sensitive content.

## 19.6 Prometheus

Prometheus must be included in the local Compose environment to make AI-related metrics queryable over time.

At minimum, EN006 should expose or derive metrics equivalent to:

```text
ai_requests_total
ai_request_duration
ai_input_tokens_total
ai_output_tokens_total
ai_errors_total
ai_guardrail_rejections_total
ai_estimated_cost_total
```

Metric dimensions must remain low-cardinality.

The following must not be metric labels:

```text
user ID
portfolio ID
raw prompt
raw completion
free-text model response
```

## 19.7 Grafana

Grafana must be included in the local Compose environment and provisioned automatically.

The local environment must provide a preconfigured Prometheus data source and an AI observability dashboard.

Manual dashboard setup after every environment start is not acceptable.

The dashboard should be provisioned from version-controlled configuration.

## 19.8 AI Observability Dashboard

The initial Grafana dashboard should contain, at minimum:

### Request Volume

```text
AI requests over time
requests grouped by task
requests grouped by provider/model
```

### Latency

```text
average / p95 AI invocation latency
latency grouped by provider/model
```

### Token Usage

```text
input tokens
output tokens
total tokens
token usage over time
token usage by task
token usage by model
```

### Estimated Cost

```text
estimated AI cost over time
cost by task
cost by provider/model
```

If cost information is unavailable for the active provider/model, the dashboard must tolerate missing cost metrics without failing.

### Errors

```text
AI provider errors
timeouts
rate-limit failures
invalid structured output
```

### Guardrails

```text
input guardrail rejections
output guardrail rejections
guardrail rejection rate
```

### Model Usage

```text
requests by provider
requests by model
```

## 19.9 Dashboard Privacy Requirements

Grafana dashboards must operate only on safe telemetry metadata.

The dashboard must not contain or expose:

```text
raw prompts
raw responses
Portfolio position details
user names
user identifiers
account data
API keys
provider credentials
```

## 19.10 Local Lifecycle Integration

The observability services must participate in the canonical platform lifecycle.

Existing platform commands such as:

```text
start.sh
stop.sh
```

must start/stop the required observability services consistently with the rest of the platform.

EN006 must not introduce an unrelated manual observability startup procedure.

## 19.11 E2E / Verification of Observability

EN006 verification must demonstrate that:

```text
AI invocation
    ↓
OTel Collector receives telemetry
    ↓
trace visible in Jaeger
    ↓
metrics queryable in Prometheus
    ↓
AI dashboard visible in Grafana
```

This verification may use a deterministic/stubbed AI provider invocation.

A live external AI provider must not be required to validate the observability stack.

---

# 20. Token Control

EN006 must actively control token consumption.

Support configurable:

```text
maximum input/context tokens
maximum output tokens
maximum total tokens
```

Conceptually:

```yaml
ai:
  limits:
    max-input-tokens: ...
    max-output-tokens: ...
    max-total-tokens: ...
```

Exact values depend on the active model.

---

# 21. Context Budget

AI use cases must not blindly serialize arbitrary domain objects.

Use a context-building layer:

```text
Domain model
    ↓
AiContextBuilder
    ↓
compact provider-neutral context
    ↓
LLM
```

The context builder should:

- remove irrelevant data;
- reduce repetition;
- avoid provider metadata;
- avoid sensitive data;
- preserve required deterministic facts;
- stay inside the configured token budget.

---

# 22. Token Counting

The architecture must support pre-invocation token counting or estimation.

Conceptual abstraction:

```java
interface TokenCounterPort {
    TokenUsage estimate(AiRequest request);
}
```

Provider-specific tokenizer adapters may be introduced when useful.

If exact pre-counting is unavailable, conservative estimation is acceptable.

Actual provider-reported usage must be collected after invocation when available.

---

# 23. Token Budget Enforcement

Before calling a model:

```text
request
   ↓
estimate tokens
   ↓
within budget?
   ├── yes → invoke
   └── no  → controlled rejection / explicit compaction
```

Context must not be silently truncated in a way that changes business meaning.

---

# 24. Cost Visibility and Limits

Conceptually:

```text
AiUsage
- inputTokens
- outputTokens
- totalTokens
- provider
- model
- estimatedCost
```

The architecture must support future reporting by:

```text
task
model
provider
Portfolio analysis
day/month
```

EN006 should also support request-level guardrails such as:

```text
max tokens per request
max output tokens
max estimated cost per request
```

Future daily/monthly/user/feature budgets are allowed but not mandatory initially.

---

# 25. Generation Parameters

Provider-neutral configuration may expose common parameters such as:

```text
temperature
maxOutputTokens
topP?
```

Business features must not depend on provider-only generation parameters.

Task configuration should centrally define generation behavior.

---

# 26. Error Model

Provider-specific errors must be translated into provider-neutral failures:

```text
AiProviderUnavailable
AiProviderRateLimited
AiProviderAuthenticationFailed
AiRequestTooLarge
AiTokenBudgetExceeded
AiCostBudgetExceeded
AiTimeout
AiInvalidResponse
AiGuardrailRejected
AiStructuredOutputInvalid
AiConfigurationError
```

Provider error payloads must not leak through public business APIs.

---

# 27. Timeout and Retry

Every AI call must have a bounded timeout.

Retries are allowed only for transient failures such as:

```text
temporary provider failure
connection reset
rate-limit retry-after
```

Do not blindly retry:

```text
invalid prompt
guardrail rejection
schema validation failure
authentication failure
token-budget violation
cost-budget violation
```

Retry behavior must be bounded.

---

# 28. Invocation Identity

AI generation is non-deterministic.

Every invocation should carry:

```text
correlationId
invocationId
```

This allows transport retries and new business generations to be distinguished.

---

# 29. AI Invocation Policy

Cross-cutting AI policy should be centralized.

Conceptually:

```text
AiInvocationPolicy
- validate task
- resolve provider/model
- compose/version prompt
- sanitize context
- enforce token budget
- enforce cost budget
- run input guardrails
- invoke provider
- run output guardrails
- validate structured output
- collect usage metadata
```

Business features must not reimplement these concerns independently.

---

# 30. Suggested Module Structure

ADR-003-compliant example:

```text
ai/
├── domain/
│   ├── model/
│   │   ├── AiRequest.java
│   │   ├── AiResponse.java
│   │   ├── AiUsage.java
│   │   └── PromptReference.java
│   ├── ports/
│   │   ├── AiModelPort.java
│   │   ├── TokenCounterPort.java
│   │   ├── InputGuardrailPort.java
│   │   └── OutputGuardrailPort.java
│   └── exceptions/
│
├── business/
│   ├── AiInvocationService.java
│   ├── PromptService.java
│   ├── AiInvocationPolicy.java
│   └── ContextBudgetService.java
│
└── infrastructure/
    ├── provider/
    │   ├── openai/
    │   ├── anthropic/
    │   ├── bedrock/
    │   └── local/
    ├── prompt/
    ├── guardrails/
    ├── observability/
    └── config/
```

Exact names may be refined during planning.

---

# 31. Prompt Storage

Initial prompt storage may use classpath resources.

Example:

```text
src/main/resources/prompts/
├── global-system-v1.txt
├── portfolio-analysis-v1.txt
└── portfolio-explanation-v1.txt
```

Each prompt should have:

```text
prompt ID
version
purpose
owner
status
```

A later enabler may introduce external prompt management.

---

# 32. AI Result Traceability

Persisted AI-generated business artifacts should record enough metadata for traceability:

```text
provider
model
promptId
promptVersion
generatedAt
inputTokens?
outputTokens?
correlationId
sourceSnapshotId?
```

Raw prompts/responses do not need to be persisted.

---

# 33. Data Lineage

AI results should reference deterministic source snapshots where applicable.

Examples:

```text
valuationId
portfolioSnapshotId
newsSnapshotId
```

This allows later review of which deterministic facts an AI result was based on.

---

# 34. Hallucination Policy

The model must never be treated as the source of truth for missing financial facts.

```text
missing fact
      ↓
unavailable / insufficient information
```

not:

```text
missing fact
      ↓
invent plausible value
```

System prompts and guardrails must reinforce this behavior.

---

# 35. Prompt Injection Defense

Future user/external content must be treated as untrusted data.

```text
System Instructions
      ↓ higher priority

Retrieved/User Content
      ↓ untrusted data
```

External content must not be allowed to redefine system policy.

---

# 36. Logging Policy

Production logs may contain:

```text
task
provider
model
prompt version
token usage
latency
result status
guardrail status
correlation ID
```

Production logs must not contain by default:

```text
system prompt body
user prompt body
full context
full completion
API keys
personal data
raw Portfolio payload
```

Prompt-content debug logging, if ever supported, must be local-only, explicit, and disabled by default.

---

# 37. Metrics

Suggested metrics:

```text
ai_requests_total
ai_request_duration
ai_input_tokens_total
ai_output_tokens_total
ai_errors_total
ai_guardrail_rejections_total
ai_estimated_cost_total
```

Metric labels must remain low-cardinality.

Do not use user IDs, Portfolio IDs, or raw prompts as labels.

These metrics must be consumable by the provisioned Grafana dashboard.

---

# 38. Provider Configuration

Provider secrets/configuration belong to infrastructure.

Conceptually:

```text
AI_PROVIDER
AI_MODEL
AI_API_KEY
```

Specific adapters may use provider-specific environment variables.

Secrets must never be committed, returned, logged, traced, or exposed to frontend code.

---

# 39. Multi-Provider Support

Several provider adapters may coexist:

```text
AiModelPort
├── OpenAiModelAdapter
├── AnthropicModelAdapter
└── BedrockModelAdapter
```

Provider/model routing must remain an infrastructure concern.

Automatic provider fallback is deferred.

---

# 40. Testing Strategy

Automated tests must not depend on live AI providers.

## Unit Tests

At minimum:

- prompt composition;
- prompt version selection;
- context-budget enforcement;
- token-budget enforcement;
- cost-budget enforcement;
- input guardrail rejection;
- output guardrail rejection;
- structured-output validation;
- sensitive-data sanitization;
- provider-neutral error mapping;
- AI usage aggregation.

## Observability Infrastructure Tests

Verification must cover:

- `docker-compose` includes OTel Collector, Jaeger, Prometheus, and Grafana;
- Collector configuration is valid;
- Prometheus can scrape/query the configured AI metrics;
- Grafana starts with the Prometheus data source provisioned;
- the AI observability dashboard is provisioned automatically;
- a deterministic AI invocation produces a trace visible through Jaeger;
- AI metrics are available to Grafana;
- no raw prompts, responses, credentials, or Portfolio payloads appear in telemetry.

## Adapter Tests

Use WireMock or equivalent deterministic stubs to verify:

- authentication;
- request mapping;
- system prompt mapping;
- model selection;
- max-token mapping;
- response mapping;
- usage mapping;
- provider errors;
- rate limits;
- timeout;
- malformed responses.

## Business Tests

Use fake `AiModelPort` implementations.

Do not assert exact natural-language model output unless it is a controlled fixture.

---

# 41. Integration / Smoke Testing

A real-provider smoke test may exist but must be:

- opt-in;
- excluded from normal CI;
- safe for credentials;
- bounded by token/cost limits.

---

# 42. E2E Policy

EN006 itself does not require a user-facing browser E2E.

Future AI features using EN006 must use deterministic E2E tests with the AI provider boundary stubbed or controlled.

Normal CI must not depend on live model output.

---

# 43. Architecture Tests

ArchUnit should enforce where practical:

```text
domain -X-> provider packages
business -X-> provider packages
domain -X-> provider SDKs
business -X-> provider SDKs
```

Provider SDKs and HTTP DTOs remain in infrastructure.

---

# 44. Verification Criteria

## VC-001 — Provider-Neutral Core
Core/business AI usage does not reference provider SDKs/types.

## VC-002 — Provider Adapter
At least one provider adapter can implement `AiModelPort`.

## VC-003 — Provider Replaceability
Changing configured provider does not require modifying consuming business use cases.

## VC-004 — System Prompt
A governed system prompt can be configured and versioned.

## VC-005 — Prompt Versioning
Every invocation identifies prompt ID/version.

## VC-006 — Structured Output
Typed/structured output can be requested and validated.

## VC-007 — Input Guardrails
Input can be rejected before provider invocation.

## VC-008 — Output Guardrails
Invalid/unsafe output can be rejected after provider invocation.

## VC-009 — Sensitive Data
Raw sensitive data is not logged/traced by default.

## VC-010 — Token Usage
Input/output/total token usage is recorded when available.

## VC-011 — Token Budget
Requests can be rejected when token limits are exceeded.

## VC-012 — Output Limit
Maximum output tokens can be configured.

## VC-013 — Context Control
Business context is explicitly selected rather than blindly serialized.

## VC-014 — Cost Metadata
The architecture supports estimated/recorded request cost.

## VC-015 — Cost Guardrail
A request can be rejected if it exceeds configured cost limits.

## VC-016 — Observability
Provider, model, task, prompt version, token usage, latency, and result status are observable.

## VC-017 — OpenTelemetry
AI invocation participates in tracing without exposing raw prompts by default.

## VC-018 — Timeout
Every provider call has a bounded timeout.

## VC-019 — Provider-Neutral Errors
Provider errors are translated into EN006 failures.

## VC-020 — Deterministic Tests
CI does not require live AI providers.

## VC-021 — Financial Determinism
AI does not replace deterministic financial calculations.

## VC-022 — Hallucination Constraint
Missing financial facts must not be fabricated.

## VC-023 — ADR-003 Compliance
Implementation follows the standard Spring architecture.

## VC-024 — Local Observability Stack
The platform Docker Compose includes OpenTelemetry Collector, Jaeger, Prometheus, and Grafana.

## VC-025 — Collector Integration
The core service exports AI telemetry through the OpenTelemetry Collector.

## VC-026 — Trace Visualization
A deterministic AI invocation can be inspected in Jaeger without exposing sensitive content.

## VC-027 — Metrics Availability
AI request, latency, token, error, guardrail, and available cost metrics are queryable by Prometheus.

## VC-028 — Grafana Provisioning
Grafana starts with its Prometheus data source and AI observability dashboard automatically provisioned.

## VC-029 — AI Dashboard
The Grafana dashboard displays request volume, latency, token usage, errors, guardrail activity, provider/model usage, and estimated cost where available.

## VC-030 — Observability Privacy
Raw prompts, completions, Portfolio data, user identifiers, API keys, and provider credentials are absent from traces, metrics, and Grafana dashboards.

## VC-031 — Canonical Lifecycle
The observability stack is integrated into the existing platform `start.sh` / `stop.sh` lifecycle.

---

# 45. Explicit Technical Decisions

1. AI integration is provider-neutral.
2. Provider adapters live exclusively in infrastructure.
3. Core/business code does not know provider/model SDKs.
4. `AiModelPort` is the generic model capability.
5. Business features should prefer task-specific AI ports over direct generic model usage.
6. Global system prompts are supported.
7. Task-specific prompt templates are supported.
8. Prompts are versioned.
9. Structured output is preferred for machine-consumed results.
10. Input and output guardrail extension points are mandatory.
11. Provider-native guardrails may complement but not replace provider-neutral guardrails.
12. Raw prompts/responses are not logged in production by default.
13. Sensitive data must be minimized/redacted.
14. OpenTelemetry-compatible observability is required.
15. Token usage must be captured when available.
16. Token budgets must be enforceable.
17. Output-token limits must be configurable.
18. Context must be explicitly built and budgeted.
19. Cost metadata must be supported.
20. Request-level cost limits must be possible.
21. Provider/model/prompt metadata must be traceable.
22. AI timeouts are mandatory.
23. Retry behavior must be bounded and failure-aware.
24. CI must not depend on live AI providers.
25. Deterministic financial calculations remain outside AI.
26. Missing financial facts must never be invented.
27. Multi-provider support is allowed.
28. Automatic provider fallback is deferred.
29. Local Docker Compose must include OpenTelemetry Collector.
30. Local Docker Compose must include Jaeger for trace visualization.
31. Local Docker Compose must include Prometheus for metrics.
32. Local Docker Compose must include Grafana.
33. Grafana must be automatically provisioned with a Prometheus data source.
34. An AI observability dashboard must be version controlled and automatically provisioned.
35. The AI dashboard must expose requests, latency, token usage, errors, guardrails, provider/model usage, and estimated cost where available.
36. Observability must never expose raw sensitive AI/business content.
37. The observability stack must participate in the canonical platform start/stop lifecycle.

---

# 46. Open Technical Decisions

Resolved by the product owner on 2026-09-04 (before formal specification):

1. **Initial concrete provider — NONE shipped by EN006.** EN006 delivers the full port +
   policy + prompt + guardrail + observability skeleton plus **one deterministic in-repo
   local/stub adapter** (no external call, no API key) that proves `AiModelPort` is
   implementable (VC-002) and drives the Jaeger/Prometheus/Grafana observability proof
   (VC-024–031). A real external provider adapter (Anthropic/OpenAI/Bedrock/Vertex) is
   **deferred to the first AI feature that actually needs one**, consistent with §6 ("at least
   one concrete adapter is required when the first AI feature is implemented") and VC-020 (CI
   never depends on a live provider).
2. **Initial concrete model — N/A for EN006** (no external model is called; deferred with
   decision 1).
6. **Guardrails — rule-based only initially.** Deterministic checks (size limits,
   prompt-injection/system-override heuristics, schema validation) implement the mandatory
   input/output guardrail extension points (§14–16). No AI-based guardrail model in EN006;
   provider-native guardrails remain a future complement, never a replacement (§45.11).
3. Exact Java HTTP/SDK integration strategy — deferred to `/speckit-plan`.
4. Exact structured-output mechanism — deferred to `/speckit-plan`.
5. Exact guardrail implementation (mechanics of the rule-based checks above) — deferred to
   `/speckit-plan`.
7. Exact token-estimation implementation — deferred to `/speckit-plan`.
8. Pricing configuration source — deferred to `/speckit-plan` (no live provider in EN006, so no
   real cost is incurred; cost metadata stays a supported-but-unpopulated capability until a real
   provider is wired).
9. Exact prompt-template storage — deferred to `/speckit-plan` (§31 suggests classpath resources).
10. Prompt metadata in resources vs database — deferred to `/speckit-plan`.
11. Exact redaction implementation — deferred to `/speckit-plan`.
12. Exact OpenTelemetry semantic-convention version — deferred to `/speckit-plan`.
13. Retry backoff policy — deferred to `/speckit-plan`.
14. Timeout defaults — deferred to `/speckit-plan`.
15. Cost-limit defaults — deferred to `/speckit-plan`.
16. Model-routing mechanism — deferred to `/speckit-plan` (single default provider/model
    configuration is sufficient while no real provider is wired).
17. Whether AI response caching is introduced later — deferred, out of scope for EN006.

**Local AI observability stack — proceeds via a new ADR.** Adding OpenTelemetry Collector,
Jaeger, Prometheus, and Grafana to the local Docker Compose / `start.sh` / `stop.sh` lifecycle is
a significant local-runtime/operational change (CLAUDE.md §20). `/speckit-plan` will propose
`product/architecture/adrs/ADR-004-local-ai-observability-stack.md` before implementation, keeping
this decision recorded the same way ADR-001/002/003 record this project's other structural
decisions.

No decision may couple business logic directly to a provider.

---

# 47. Human Approval

Before formal specification:

- [X] Provider-neutral AI integration is approved.
- [X] `AiModelPort` is approved.
- [X] Task-specific AI ports are preferred for business features.
- [X] System prompt support is approved.
- [X] Prompt templates are approved.
- [X] Prompt versioning is approved.
- [X] Structured output support is approved.
- [X] Input guardrails are approved.
- [X] Output guardrails are approved.
- [X] Sensitive-data minimization/redaction is approved.
- [X] Raw prompts/responses are not logged by default.
- [X] OpenTelemetry-safe observability is approved.
- [X] Docker Compose observability stack is approved.
- [X] OpenTelemetry Collector is approved.
- [X] Jaeger is approved.
- [X] Prometheus is approved.
- [X] Grafana is approved.
- [X] Automatically provisioned AI Grafana dashboard is approved.
- [X] Dashboard privacy requirements are approved.
- [X] Integration with canonical `start.sh` / `stop.sh` lifecycle is approved.
- [X] Token counting/estimation is approved.
- [X] Token-budget enforcement is approved.
- [X] Output-token limits are approved.
- [X] Cost metadata/control is approved.
- [X] Provider/model traceability is approved.
- [X] Timeout and bounded retry policies are approved.
- [X] CI without live AI provider is approved.
- [X] Deterministic financial calculations remain outside AI.
- [X] Missing financial facts must not be fabricated.
- [X] Multi-provider support is approved.
- [X] Automatic provider fallback remains out of scope.
- [X] No specific AI business feature is introduced by EN006.

The §46 open technical decisions were resolved by the product owner on 2026-09-04: EN006 ships
**no real external provider adapter** (a deterministic local/stub adapter proves the port and
drives observability instead — a real provider is deferred to the first AI feature that needs
one); guardrails are **rule-based only** initially; the local AI observability stack (OTel
Collector/Jaeger/Prometheus/Grafana) proceeds through a **new ADR-004** during `/speckit-plan`.
The remaining open items are deferred to `/speckit-plan` as the enabler itself allows.

**Approved by:** jaruiz  
**Date:** 2026-09-04  
**Status:** Approved
