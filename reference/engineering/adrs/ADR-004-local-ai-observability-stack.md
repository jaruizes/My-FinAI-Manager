# ADR-004 — Local AI Observability Stack

> **Status:** Approved
> **Date:** 2026-09-04
> **Decision Owner:** Human Architecture Governance (drafted by Claude Code per EN006 §46 Q3,
> approved by jaruiz 2026-09-04, per `product/governance/ai-development-policy.md` — "AI may draft
> ADRs... the ADR is not considered approved merely because AI generated it.")
> **Scope:** Local development observability topology for AI (EN006) invocations

---

## Context

EN006 (Establish AI Model Integration) requires every AI invocation to be observable — provider,
model, task, prompt id/version, token usage, latency, guardrail result, and estimated cost — without
exposing raw prompts, raw completions, or credentials (EN006 §17–§19, §36–§37; VC-016, VC-017,
VC-024…VC-031). `core-service` currently has **no** tracing/metrics-export dependency at all (no
`opentelemetry-*`, `micrometer-tracing-*`, or `otlp` reference in `pom.xml` or `application.yml`) —
this is the platform's first telemetry-export capability.

Making a multi-hop chain (business operation → AI use-case span → AI invocation span → provider-call
span) inspectable requires a place to receive, store, and visualize that telemetry. `product/architecture/technology-policy.md`
already lists `OpenTelemetry` as `PREFERRED` for observability and distributed tracing, but does not
name a specific local trace store, metrics store, or dashboard tool — so *which* concrete components
implement that preference, and *how* they join the platform's local runtime, is a decision this ADR
makes explicit, per CLAUDE.md §20 ("major technology adoption," "significant operational
characteristics") even though it changes neither backend topology (ADR-001) nor persistence
ownership.

This ADR is scoped **only** to the local development Docker Compose environment
(`implementation/platform/infrastructure/local/compose.yaml`, `start.sh`, `stop.sh`) — it makes no
decision about a production/deployed observability topology, which does not exist yet for this
project.

---

## Decision

The local platform Docker Compose environment adds four services, each doing exactly one job in the
standard OpenTelemetry topology:

```text
core-service (Spring Boot)
        │  OTLP (traces + metrics), gRPC/HTTP
        ▼
  otel-collector  (OpenTelemetry Collector — contrib distribution)
        │
        ├── traces  ──► jaeger      (all-in-one; OTLP-native trace store + UI)
        │
        └── metrics ──► prometheus  (scrapes the Collector's Prometheus-exporter endpoint)
                              │
                              ▼
                          grafana   (Prometheus data source + AI dashboard, both
                                     auto-provisioned from version-controlled config —
                                     no manual post-start setup)
```

- `core-service` exports OTLP traces and metrics to the Collector via `OTEL_EXPORTER_OTLP_ENDPOINT`
  / `OTEL_SERVICE_NAME` (EN006 FR-047); it never talks to Jaeger, Prometheus, or Grafana directly.
- The Collector's configuration is version-controlled and forwards traces to Jaeger and exposes
  metrics on a Prometheus-scrapeable endpoint; it must not export raw prompt/completion request or
  response bodies (EN006 FR-048).
- Jaeger, Prometheus, and Grafana are each the reference OTel-ecosystem tool the enabler explicitly
  names (EN006 §19.5–§19.7) — no substitution.
- Grafana starts with its Prometheus data source and the AI observability dashboard **already
  provisioned** from files checked into the repository (EN006 FR-051) — never a manual click-through
  step after `./start.sh`.
- All four services participate in the existing canonical `start.sh` / `stop.sh` lifecycle
  (EN006 FR-053) — no separate startup procedure.
- No AI provider credential passes through any observability component (EN006 FR-042, FR-047,
  FR-052).

Exact image tags, ports, and file layout are recorded in `specs/EN006-establish-ai-model-integration/research.md`
(a planning detail, not an architectural one — consistent with how EN001/EN002 pinned their base
images).

---

## Alternatives Considered

### No local observability stack — structured logs only

Rejected. Logs alone cannot show the nested span chain across a business operation → AI use-case →
AI invocation → provider call, nor a queryable time series for latency/token/cost trends — both are
explicit enabler requirements (VC-016, VC-017, VC-027, VC-029), not incidental nice-to-haves.

### Cloud-hosted observability (Grafana Cloud, Honeycomb, Datadog, …)

Rejected for this ADR. This is a personal-scale, local-first project (`product/architecture/technology-policy.md`
— avoid speculative infrastructure); a hosted SaaS backend would add an external account, a new
credential to manage, and a network dependency for purely local development telemetry, none of which
the enabler asks for. Nothing here precludes a future ADR introducing hosted observability once a
real deployment target exists.

### Zipkin instead of Jaeger

Both are OTLP-compatible trace stores. Rejected in favor of Jaeger because the enabler explicitly
names Jaeger (§19.5) and Jaeger's OTel-native ingestion and trace UI are more actively documented for
this exact Collector → trace-store pairing.

### Skip Prometheus; read metrics straight from the Collector

Rejected. Grafana dashboards need a queryable time-series store to show trends (request volume over
time, token usage over time, etc. — EN006 §19.8); the Collector alone doesn't retain history. The
enabler explicitly requires Prometheus (§19.6).

### A single all-in-one agent (e.g. Grafana Alloy) instead of a separate Collector

A reasonable simplification in general, but rejected here to keep each concern in the specific,
independently-documented component the enabler names (§19: Collector, Jaeger, Prometheus, Grafana as
four distinct required components) — deviating would be a scope reinterpretation of an approved
enabler, not a technical simplification this ADR is free to make unilaterally.

---

## Consequences

### Positive

- Directly satisfies EN006 VC-024…VC-031 with no gap.
- A developer inspects AI cost, latency, token usage, error rate, and guardrail activity locally,
  with zero external account and zero recurring cost.
- Establishes a reusable OpenTelemetry foundation — any future component (not just AI) can export
  OTLP to the same Collector without a new architectural decision.
- Keeps `core-service` provider-neutral for telemetry the same way EN005 kept it provider-neutral for
  market data — the application only ever talks to "the Collector," never a named trace/metrics
  vendor.

### Negative / Trade-offs

- Four more containers in local development — heavier `./start.sh`, more memory/CPU, a longer first
  `docker compose up` while images are pulled.
- More Compose/provisioning configuration surface to maintain (Collector config, Prometheus scrape
  config, Grafana datasource + dashboard provisioning files).
- Local disk usage grows for trace/metric storage (bounded by each tool's default retention; exact
  retention is a planning detail, not architectural).
- No production/deployed observability topology is decided by this ADR — a future ADR is required
  once a real deployment target exists, to decide whether this same topology, a managed equivalent,
  or something else applies there.

---

## Verification

- `./start.sh` brings the Collector, Jaeger, Prometheus, and Grafana up healthy alongside the
  existing platform services, and `./stop.sh` stops them, idempotently (EN006 FR-053, SC-008).
- A deterministic AI invocation (EN006's local/stub `AiModelPort` adapter — no live provider
  required) produces: a trace visible in Jaeger with the full nested span chain; metrics queryable in
  Prometheus; and the auto-provisioned Grafana AI dashboard rendering them (EN006 FR-054, SC-007).
- A content-safety scan of the Collector configuration, emitted spans, metric labels, and the
  Grafana dashboard definition finds no raw prompt/response text, Portfolio identifiers, user
  identifiers, or credentials (EN006 FR-052, SC-009).

No ArchUnit rule applies (this ADR is infrastructure/deployment topology, not application code
structure).

---

## Migration

Not applicable — this is net-new local infrastructure; nothing existing is migrated. `core-service`
gains its first OpenTelemetry exporter dependency; no existing dependency is removed or replaced.

---

## Supersedes / Updates

Complements, and does not change, **ADR-001** (`core-service` remains the single independently
deployable backend service — the AI module and its telemetry export live inside it, not in a new
service) and **ADR-003** (the `ai` module still follows the standard `domain`/`business`/
`infrastructure` layout).

Implementation-detail interpretation of `product/architecture/technology-policy.md`'s Observability
section is narrowed by this ADR from "OpenTelemetry, `PREFERRED`" to the concrete four-component
local topology above.

If `product/architecture/diagrams/containers.md` is created in the future (it does not exist yet —
a pre-existing gap, not introduced by this ADR), it should be updated to show these four services
alongside `core-service`, `postgres`, and the frontend container.

---

## Approval

Approved as drafted, with no changes to the decision, alternatives, or consequences above.

**Approved by:** jaruiz
**Date:** 2026-09-04
**Status:** Approved
