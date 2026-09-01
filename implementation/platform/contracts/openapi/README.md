# Platform Contracts — OpenAPI

This directory holds the **contract-first** definitions for My-FinAI-Manager's external business
API. It represents the *current* platform contract, not a per-feature copy.

## Convention

- External REST APIs are **contract-first**: the OpenAPI document is written/updated before or
  alongside implementation, reviewed, then implemented, then covered by contract tests
  (`AR-011`, `DR-017`, `testing-strategy §4`).
- Contracts use **business language** from `product/definition/global/glossary.md`. They must not
  expose persistence models, framework types, or provider payloads.
- Errors use stable, machine-readable identifiers (RFC 9457 problem details are the intended
  shape).
- Compatibility changes are intentional and explicit.

## Current state (EN001)

`openapi.yaml` is an **empty skeleton** (`paths: {}`). EN001 — Bootstrap Executable Platform
introduces no business or operational operations. Operational health is served by Spring Boot
Actuator at `/actuator/health` and is deliberately **not** modelled here (it is a framework
mechanism, not a business capability).

## Next

`FD001 — Create Investment Portfolio` adds the first operation(s) and schema(s) to `openapi.yaml`
(e.g. portfolio creation), and the corresponding contract test in `core-service`.
