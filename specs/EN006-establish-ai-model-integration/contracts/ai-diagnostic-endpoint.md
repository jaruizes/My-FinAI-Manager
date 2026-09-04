# Contract — `/actuator/aidiagnostic` (operational tooling, NOT a business API)

**Class**: `ai.infrastructure.api.AiDiagnosticEndpoint` (`@Endpoint(id = "aidiagnostic")` — Actuator
endpoint URLs are the lowercased id with no separator, so this is `/actuator/aidiagnostic`, not
`/actuator/ai-diagnostic`) ·
**Decision**: research.md D6 / plan.md OD-9

> This is **not** part of `contracts/openapi/openapi.yaml` and is **not** a business capability. It
> exists solely so a human/script can trigger one deterministic AI invocation against a running
> `./start.sh` instance, to prove the observability chain end-to-end (FR-054, VC-024…031) — the same
> role a `curl /actuator/health` plays for basic liveness, not a feature the frontend or an investor
> ever calls.

---

## Request

```
POST /actuator/aidiagnostic
```

- No request body.
- No authentication beyond whatever already gates `/actuator/*` locally (none, in local dev — same
  as `/actuator/health` today; ADR-002's interim unauthenticated posture applies equally here).
- Only reachable when `management.endpoints.web.exposure.include` includes `aidiagnostic` **and**
  `management.endpoint.aidiagnostic.enabled` is `true` (both default `true` locally; either can be
  set to disable the endpoint in an environment where it's unwanted, with zero code change).

## Response (200)

```json
{
  "requestId": "b6b6e6b0-2f7a-4b7e-9c9a-2b0f2e6f9a11",
  "latencyMs": 3,
  "totalTokens": 27
}
```

| Field | Source |
|---|---|
| `requestId` | `AiResponse.requestId()` — also correlatable with the trace's `ai.provider.request.id` attribute |
| `latencyMs` | `AiResponse.latencyMs()` |
| `totalTokens` | `AiResponse.usage().totalTokens()` |

**Never returned**: the prompt text, the response `content`, `structuredContent`, `provider`,
`model`, or any telemetry attribute beyond the three fields above — the endpoint's purpose is to
*trigger* an invocation whose telemetry is then inspected in Jaeger/Prometheus/Grafana, not to
surface AI output over HTTP.

## Failure

A guardrail rejection, budget rejection, or provider error on the fixed diagnostic request is not
expected in normal operation (the diagnostic request is deliberately small and injection-pattern
free — research D6), but if `AiInvocationPolicy` throws, the endpoint returns the Actuator
framework's standard `500` with a generic message — never a stack trace or the underlying exception's
raw detail — consistent with CLAUDE.md §15 ("do not expose stack traces to users").

## Verification use

`quickstart.md`'s observability-proof procedure (gated on ADR-004 approval) is:

```bash
curl -X POST http://localhost:8080/actuator/aidiagnostic
# then, within a few seconds:
#  - open http://localhost:16686 (Jaeger) and find the ai.usecase / ai.invocation span pair
#  - open http://localhost:9090 (Prometheus) and query ai_requests_total
#  - open http://localhost:3000 (Grafana) and confirm the AI dashboard's panels are non-empty
```
