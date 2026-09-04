# PR Evidence — EN006 (Establish Provider-Neutral AI Model Integration)

**Traces to**: `product/definition/enablers/EN006-establish-ai-model-integration/EN006-establish-ai-model-integration.md`
(Approved, §47 signed jaruiz 2026-09-04) via `specs/EN006-establish-ai-model-integration/spec.md`
(63 FRs, 8 user stories, 31 VCs) → `plan.md` → `research.md` (D1–D9) → `tasks.md` (T001–T068).

**Architecture decision**: `product/architecture/adrs/ADR-004-local-ai-observability-stack.md` —
Approved, jaruiz, 2026-09-04, as drafted (no changes).

## What shipped

- New `ai` module inside `core-service` (ADR-003 layout): `domain/{model,ports,exceptions}`,
  `business`, `infrastructure/{provider/local,prompt,guardrails,tokencount,observability,api,config}`.
- `AiModelPort` with exactly **one** implementation — `LocalAiModelAdapter`: deterministic, no
  network call, no credential (resolved Q1).
- `AiInvocationPolicy` (`implements GenerateAiUseCase`) — the single orchestrator sequencing prompt
  composition (`PromptService` + `ClasspathPromptRepository`/`PromptRepositoryPort`), context
  budgeting/redaction (`ContextBudgetService`), token/cost budget enforcement (`HeuristicTokenCounter`
  + a documented placeholder pricing constant), rule-based guardrails (`RuleBasedInputGuardrail`,
  `RuleBasedOutputGuardrail`), structured-output validation (`StructuredOutputValidator`, a pure
  domain calculator — no Jackson/JSON-schema dependency), provider-neutral error mapping + bounded
  timeout (`CompletableFuture.get(timeout)`) + bounded retry (transient failures only), and telemetry
  (`TelemetryPort`/`TelemetryScope` domain ports, `AiTelemetryRecorder` infrastructure adapter over
  Micrometer Observation + MeterRegistry).
- Eleven provider-neutral exceptions (`AiException` base + 10 concrete types).
- `AiDiagnosticEndpoint` — an internal Actuator endpoint (`@Endpoint(id = "aidiagnostic")`,
  `POST /actuator/aidiagnostic`) that triggers one deterministic invocation for observability
  verification; explicitly **not** a business API, not in `openapi.yaml` (OD-9).
- OpenTelemetry export wired via Spring Boot's native Micrometer/Actuator path
  (`micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`, and — discovered necessary
  during runtime verification, see "Corrections" below — `micrometer-registry-otlp`).
- Local observability stack (ADR-004): `otel-collector`, `jaeger`, `prometheus`, `grafana` added to
  `infrastructure/local/compose.yaml`, config under `infrastructure/observability/` (Collector
  config, Prometheus scrape config, Grafana datasource + dashboard auto-provisioning, the
  `ai-observability.json` dashboard). `start.sh`/`stop.sh` extended to include them.
- Two new ArchUnit-adjacent checks: the existing generic domain/business→infrastructure rules
  already cover `ai.*` (module-agnostic wildcards); one additional EN006-named specialization,
  `ai_domain_and_business_are_free_of_infrastructure_provider_types`, added for traceability.

## Corrections made during implementation (documented, not silent)

1. **Structured content type**: `AiResponse.structuredContent()` is `Optional<Map<String,Object>>`,
   not a Jackson `JsonNode` as an early draft implied — keeps `ai.domain` Jackson-free per the
   project's existing generic ArchUnit rule (`domain_has_no_framework_dependencies`, already
   module-agnostic). `StructuredOutputValidator` is therefore a pure, dependency-free domain
   calculator (`ai.domain.model`), not an infrastructure adapter.
2. **`TelemetryPort` / `PromptRepositoryPort` added**: `AiTelemetryRecorder` (Micrometer) and
   `ClasspathPromptRepository` cannot be called directly from `ai.business` (the existing
   `business_does_not_depend_on_infrastructure` ArchUnit rule already forbids it, module-agnostically)
   — both are proper domain ports with infrastructure adapters, not ad hoc business→infrastructure
   calls. `research.md`/`data-model.md`/`plan.md`/`contracts/` describe the intended shape; these two
   ports are a direct, necessary consequence of that design applied to the existing generic
   architecture rules, not a new decision.
3. **`AiInvocationSettings` (domain-safe config)**: `AiProperties` (`@ConfigurationProperties`) stays
   Spring-only in infrastructure; `AiModuleConfiguration` maps it to the plain
   `AiInvocationSettings` record `ai.business`/`ai.domain` actually depend on.
4. **Actuator endpoint URL**: Spring Boot Actuator endpoint URLs are the endpoint id lowercased with
   separators stripped (`@Endpoint(id = "aidiagnostic")` → `/actuator/aidiagnostic`, matching the
   framework's own `threadDump`→`/actuator/threaddump` convention) — not `/actuator/ai-diagnostic`
   as an earlier planning draft assumed. `contracts/ai-diagnostic-endpoint.md` and `quickstart.md`
   are corrected; caught by `PlatformIntegrationIT`'s new full-context test before it reached
   production.
5. **`micrometer-registry-otlp` dependency added**: `micrometer-tracing-bridge-otel` +
   `opentelemetry-exporter-otlp` alone export **traces** correctly (verified in Jaeger) but supply no
   `OtlpMeterRegistry` bean, so `management.otlp.metrics.*` had nothing to bind to and metrics were
   silently never exported. Found during runtime verification (§ below), fixed by adding the metrics
   registry artifact; re-verified end to end.

None of these change FR/VC scope — each is either a direct mechanical consequence of already-approved
architecture rules, or a runtime-discovered implementation-correctness fix, both squarely inside the
constitution IV "safe, reversible, non-material implementation detail" carve-out.

## Gate results

| Gate | Command | Result |
|---|---|---|
| Backend unit + integration + architecture + coverage | `./mvnw -B clean verify` (offline) | **BUILD SUCCESS** — Surefire (unit) all green incl. 124 new `ai.*` tests; Failsafe (IT) all green incl. the new `PlatformIntegrationIT.ai_diagnostic_endpoint_triggers_a_real_deterministic_invocation`; ArchUnit 23/23 (incl. the new EN006 rule); JaCoCo bundle **PASS** — line 2145/2199 (97.5%), branch 777/852 (91.2%), both ≥ 90% |
| Regression | same run | FD001–FD004 / EN004 / EN005 suites and ITs unaffected (0 files under `portfolio/`, `financialinstrument/`, `marketdata/` touched) |
| Frontend | N/A | EN006 has no UI; no frontend file touched |
| Runtime — module only | `./mvnw ... PlatformIntegrationIT` | Full Spring context boots; `POST /actuator/aidiagnostic` returns `200` with `requestId`/`latencyMs`/`totalTokens` only, no leakage |
| Runtime — full stack | `BUILD=1 ./start.sh` (fresh rebuild) | All **7** containers (`postgres`, `backend`, `frontend`, `otel-collector`, `jaeger`, `prometheus`, `grafana`) healthy on first real run |
| Observability chain (SC-007, VC-024…031) | manual, against the running stack | `curl -X POST http://localhost:8080/actuator/aidiagnostic` → Jaeger shows `http post /actuator/aidiagnostic` → `ai.usecase` → `ai.invocation` (nested, as designed) with `ai.task=diagnostic`, `gen_ai.system=local`, `ai.guardrail.result=allowed`, `ai.success=true`, `ai.prompt.id`/`.version` present, **no raw content**; Prometheus `ai_requests_total`/`ai_input_tokens_total`/`ai_output_tokens_total`/`ai_estimated_cost_total` all populated; Grafana dashboard + Prometheus datasource **auto-provisioned** (verified via Grafana's own API — zero manual setup), queried successfully through Grafana's datasource proxy |
| Content-safety scan (SC-009, FR-052) | grep over Collector config + dashboard JSON + Jaeger trace payload | **OK** — no `logging`/`debug` exporter; no prompt/completion/api-key/password/token text in the dashboard definition; no raw diagnostic sentence text in the exported trace |
| Idempotent lifecycle | `./stop.sh` twice | Second call is a safe no-op; all 7 services torn down cleanly both times |
| Scope review (SC-010) | `git status --porcelain` | **No** Flyway migration, **no** `openapi.yaml` change, **no** LLM provider SDK dependency, **no** new independently deployable service; touched files are exactly: `pom.xml` (+3 OTel artifacts), `application.yml`, `StandardArchitectureRulesTest.java`, `PlatformIntegrationIT.java`, `compose.yaml`, `start.sh`, `stop.sh`, plus the new `ai/` module tree, `prompts/`, and `infrastructure/observability/`. `portfolio`/`financialinstrument`/`marketdata` untouched. |

## VC traceability

All 31 VCs (VC-001…VC-031) map to the user stories/FRs in `spec.md`'s Traceability table and have
either automated test evidence (US1–US6, US8 — unit + architecture tests) or the manual runtime
evidence above (US7 — VC-016, VC-017, VC-024…VC-031, gated on ADR-004 and now demonstrated against
the real running stack, not just planned).

## Task completion

`tasks.md` T001–T067 all `[x]`. **T068** (`/project-verify EN006-establish-ai-model-integration`) is
left unchecked — user-triggered closure gate, not run by this implementation pass.

## Not committed

Established repo state (per `CLAUDE.md` — commit only when explicitly asked). **Next**:
`/project-verify EN006-establish-ai-model-integration`, then human closure + commit.
