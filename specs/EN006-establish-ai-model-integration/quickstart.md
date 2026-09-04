# Quickstart — Validate EN006 (Establish Provider-Neutral AI Model Integration)

Run guide proving the enabler end to end. Details: [plan.md](./plan.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/). No implementation code here.

**Prerequisites**: JDK 21 + `./mvnw`; repo root = `implementation/platform/`. Sections A–D need no
Docker. Sections E–F need Docker/Compose (`DOCKER_HOST=unix://${HOME}/.colima/default/docker.sock`)
**and require ADR-004 to be approved first** (`product/architecture/adrs/ADR-004-local-ai-observability-stack.md`)
— see the gate note in plan.md.

---

## A. Backend build, tests, coverage, architecture (SC-003)

```bash
cd implementation/platform/backend/core-service
./mvnw -B clean verify        # OFFLINE — no external network of any kind
```

**Expect** BUILD SUCCESS; JaCoCo bundle ≥ 90 % line & branch; `StandardArchitectureRulesTest` green
with the two new `ai.*` rules ([research.md](./research.md) D8). New tests all green:

- `PromptServiceTest` — layering order; `promptId`/`promptVersion` resolution; unknown task/version
  → `AiConfigurationErrorException` (SC-005 area).
- `ContextBudgetServiceTest` / `HeuristicTokenCounterTest` — context assembly excludes
  irrelevant/sensitive fields; token-estimate formula; over-budget detection.
- `RuleBasedInputGuardrailTest` / `RuleBasedOutputGuardrailTest` — each rule's accept/reject cases
  (SC-006).
- `StructuredOutputValidatorTest` — conforming / missing-required-field / wrong-type fixtures
  (SC-005).
- `AiInvocationPolicyTest` — full sequencing: happy path; token-budget rejection (adapter never
  invoked, SC-004); cost-budget rejection (adapter never invoked, SC-004); input-guardrail rejection
  (adapter never invoked); output-guardrail rejection; every provider-neutral error mapping; timeout;
  bounded retry vs never-retry (contract `ai-model-port.md` C2).
- `LocalAiModelAdapterTest` — deterministic output for a fixed input (contract `local-ai-adapter.md`,
  determinism contract); structured-output happy path.
- `AiDiagnosticEndpointTest` (`@WebMvcTest` slice) — returns only `invocationId`/`latencyMs`/
  `totalTokens`, never the prompt/response body.

**Grep checks**:
```bash
grep -rln "com.anthropic\|com.openai\|software.amazon.awssdk.services.bedrock\|com.google.cloud.vertexai" \
  src/main/java/com/myfinaimanager/core/ai/ && echo "LEAK — provider SDK referenced" || echo "OK — no provider SDK"
grep -rln "AI_API_KEY" src/ .env* 2>/dev/null | grep -v ".env.example" && echo "CHECK" || echo "OK — no committed AI key"
git diff --stat -- '**/openapi.yaml'   # empty — EN006 adds no business API
```

## B. Frontend (N/A)

EN006 has no UI and no frontend change. `ng test` is unaffected — skip, or run as a regression check
that it's still green.

## C. Runtime — local `./start.sh`, no observability stack yet (before ADR-004)

```bash
cd implementation/platform
./start.sh
curl -s -X POST http://localhost:8080/actuator/aidiagnostic | python3 -m json.tool
./stop.sh
```

**Expect**: the platform starts exactly as before EN006 (no new required container yet — the `ai`
module has no external dependency). The diagnostic call returns `200` with `invocationId`,
`latencyMs`, `totalTokens` — proving the module works end-to-end inside the real running service,
independent of the observability stack. `./stop.sh` idempotent as before.

## D. Definition-of-Done quick gate (module only, ADR-004-independent)

- [ ] `./mvnw -B clean verify` green offline (§A).
- [ ] `git diff` shows: **no** Flyway migration, **no** `openapi.yaml` change, **no** LLM provider SDK
  dependency, **no** JSON-schema-library dependency; the only new `pom.xml` entries are
  `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` + `micrometer-registry-otlp`
  (the third — the OTLP *metrics* registry — was added during implementation after runtime
  verification showed metrics need it, distinct from the trace exporter; see pr-evidence.md).
- [ ] `./start.sh` / `./stop.sh` still work with the `ai` module present but the observability
  services absent (they're additive, gated on ADR-004 — §C above proves this independence).
- [ ] No `product/` file was silently edited (EN006's own governance edit, and ADR-004, were both
  explicitly human-directed/flagged — see spec.md Clarifications and plan.md's gate note).

---

## The following sections require ADR-004 to be **Approved** first

## E. Runtime — full observability stack (SC-007, SC-008)

```bash
cd implementation/platform
./start.sh   # now also brings up otel-collector, jaeger, prometheus, grafana
curl -s -X POST http://localhost:8080/actuator/aidiagnostic | python3 -m json.tool
```

**Expect**: all containers healthy, including the four observability services. Then:

1. **Jaeger** — open `http://localhost:16686`, select service `my-finai-manager-core`, find the most
   recent trace. **Expect** the `ai.usecase` → `ai.invocation` span pair, with `gen_ai.system=local`,
   `ai.task=diagnostic`, `ai.prompt.id`/`ai.prompt.version`, `ai.success=true`, `ai.guardrail.result=
   allowed`, token-count attributes — and **no** raw prompt/response text anywhere in the span.
2. **Prometheus** — open `http://localhost:9090`, query `ai_requests_total`. **Expect** a non-zero
   count with `task="diagnostic"`, `provider="local"`, `outcome="success"`.
3. **Grafana** — open `http://localhost:3000`, the AI observability dashboard is already provisioned
   (no manual datasource/import step). **Expect** the Request Volume and Token Usage panels to show
   the diagnostic call; the Estimated Cost panel renders (as `0`, not an error).

```bash
./stop.sh
./stop.sh   # idempotent — second call is a safe no-op
```

## F. Content-safety scan (SC-009)

```bash
# Collector config never dumps raw bodies:
grep -n "logging\|debug" implementation/platform/infrastructure/observability/otel-collector-config.yaml

# Nothing sensitive in the dashboard definition:
grep -inE "prompt|completion|api[_-]?key|password|token=" \
  implementation/platform/infrastructure/observability/grafana/dashboards/ai-observability.json \
  && echo "CHECK" || echo "OK — dashboard definition carries no sensitive terms"
```

**Expect** `OK` — the dashboard queries Prometheus metric names only (`ai_requests_total`, …), never
raw text.

## G. Full Definition-of-Done gate (module + observability stack)

- [ ] Sections A–F all pass.
- [ ] `ADR-004-local-ai-observability-stack.md` — `Status: Approved`, `Approved by`/`Date` filled in.
- [ ] `product/architecture/diagrams/containers.md`, if it exists by the time this is implemented,
  is updated to show the four new services (optional — the file doesn't exist yet in the repo today;
  not a new gap introduced by EN006).
- [ ] 100 % of VC-001…VC-031 have evidence (spec.md Traceability table cross-checked against this
  quickstart's sections).
