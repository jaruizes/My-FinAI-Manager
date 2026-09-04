# EN005 — Definition of Done checklist

Evaluated against `product/engineering/definition-of-done.md`. Evidence from
`./mvnw -B clean verify` (offline, no `FINNHUB_API_KEY`) and `quickstart.md`. Change type:
**Deterministic Domain Change + External Integration** — **not** an API Change (FR-024), **not** a
Persistence Change (FR-021).

| # | Item | Status | Evidence / note |
|---|---|---|---|
| **1. Product & Specification** | | | |
| 1.1 | Traceable to an approved enabler | PASS | EN005 §32 signed 2026-09-03; `spec.md` Traceability maps VC-001…VC-019 |
| 1.2 | Formal spec approved where SDD requires | PASS | `spec.md` + `checklists/requirements.md` 16/16, zero clarifications |
| 1.3 | All behavior within approved scope | PASS | 3 ports + Finnhub adapter; no valuation, no UI, no API, no persistence |
| 1.4 | No new requirement introduced | PASS | §31 items resolved as OD-EN005-1…11 (technical); no Feature Definition touched |
| 1.5 | Acceptance (VC) implemented | PASS | VC-001…VC-019 → `quickstart.md` coverage table; ArchUnit 18/18; mapping/error tests |
| 1.6 | No speculative out-of-scope behavior | PASS | no persistence, no retry, no metrics infra, no third currency, no LLM (FR-034…FR-036) |
| **2. Architecture** | | | |
| 2.1–2.2 | Complies with `architecture.md` / `architecture-rules.md` | PASS | ADR-003 module-first; AR-001/002/025/037/042/043/046 respected; ArchUnit 18/18 |
| 2.3 | Technology policy | PASS | `RestClient`=`spring-web` (present); `TtlCache` hand-rolled; stub=`spring-test`. **No new dependency.** Cache is in-process (technology-policy "Cache — CONDITIONAL" satisfied by the Finnhub-rate-limit need; no Redis) |
| 2.4–2.5 | Hexagonal / domain free of infrastructure | PASS | ports in `domain`; Finnhub/HTTP/JSON types confined to `infrastructure.finnhub`; ArchUnit rules 1–3 |
| 2.6–2.7 | Module ownership clear / no cross-module persistence | PASS | new `marketdata` module; reads nothing from `portfolio` / `financialinstrument` (ArchUnit rule 4) |
| 2.8 | No unapproved technology | PASS | `MockRestServiceServer` is `spring-test` (already used-adjacent via `spring-boot-starter-test`); no WireMock (OD-EN005-4) |
| 2.9 | ADR for significant architecture change | N/A | a module + an outbound adapter in the existing topology — no topology/persistence/messaging/security change |
| 2.10 | Architecture diagrams updated | N/A | approved architecture unchanged |
| **3. Code Quality** | | | |
| 3.1–3.2 | Readable, domain terminology, cohesive | PASS | `Finnhub{Quote,Profile,ForexRates}Mapper` / `Finnhub*Adapter` / `Caching*Port` single-purpose |
| 3.3 | No speculative abstraction | PASS | no `business` layer (OD-EN005-10), no `Clock` bean, message-only exceptions, hand-rolled cache |
| 3.4 | Safe numeric representation | PASS | `BigDecimal` for every price / rate; ArchUnit + `MarketDataModelTest` reject `double`/`float` and `≤ 0` |
| 3.5 | Optional / unknown states intentional | PASS | `Sector.UNCLASSIFIED`, `ObservedAtSource`, nullable `providerExchange` / `currency` metadata |
| 3.6 | Errors explicit | PASS | 7-member neutral exception set; every provider outcome maps to exactly one (research D5) |
| 3.7 | Unused code / deps removed | PASS | dead defensive branches + unused exception ctors trimmed during the coverage pass; no dependency added |
| 3.8 | No unrelated refactoring | PASS | only `StandardArchitectureRulesTest` (+4 rules) and `pom.xml` (+2 excludes) outside new files |
| **4. Testing** | | | |
| 4.1 | TDD for deterministic logic | PASS | mapper / resolver / `TtlCache` / model tests written RED-first before their implementations |
| 4.2 | Unit / domain tests | PASS | 10 unit classes covering models, mappers (incl. `c=0`, missing sector, exchange≠MIC), resolver, cache |
| 4.3 | Integration tests where infrastructure matters | PASS | `FinnhubRestClientTest` + 3 `*AdapterTest` (`MockRestServiceServer`); `FinnhubIntegrationIT` (full context) |
| 4.4 | Contract tests for external interfaces | PASS (adapted) | EN005 exposes **no** external REST API. The *consumed* Finnhub contract is pinned by `contracts/finnhub-provider-contract.md` + typed DTOs + 12 synthetic wire fixtures + `MockRestServiceServer` request assertions (endpoint, params, `X-Finnhub-Token` header) |
| 4.5 | Architecture tests | PASS | `StandardArchitectureRulesTest` **18/18**; deliberate-violation check performed |
| 4.6 | E2E for critical journeys | N/A | enabler §29 — EN005 has no application E2E; a future valuation-feature E2E stubs the Finnhub boundary |
| 4.7 | AI evaluation | N/A | no LLM (FR-035) |
| 4.8 | Failure / edge cases | PASS | 401 / 403 / 429 / 4xx / 5xx / malformed / network / empty-body / unresolved-symbol / blank-key / missing-FX-pair all tested |
| 4.9 | All required tests pass | PASS | Surefire 181 (3 skipped = opt-in smoke) + Failsafe 62, 0 failures |
| **5. Coverage** | | | |
| 5.1 | ≥ 90 % overall | PASS | JaCoCo bundle **line 97.04 % · branch 91.81 %** — "All coverage checks have been met" |
| 5.2 | Stronger critical-domain coverage | PASS | mappers / error translation / resolver / cache fully unit-tested; EN005 performs **no** financial calculation |
| 5.3 | Exclusions justified | PASS | only `marketdata/infrastructure/{config, finnhub/dto}` — no-logic typed config + record DTOs, same rationale as the existing `config/**` / `persistence/entity/**` excludes (T004) |
| 5.4 | Meaningful assertions | PASS | tests assert decimal equality (no precision loss), status→exception mapping, no-key-in-log, cache N→1, `observedAt` preserved |
| **6. APIs & Contracts** | | | |
| 6.x | REST contract / OpenAPI / error model | N/A | **EN005 adds no external REST API** (FR-024); `openapi.yaml` untouched. The consumed Finnhub API is documented and stub-tested (see 4.4) |
| 6 (provider) | Provider payloads do not leak | PASS | ArchUnit confines Finnhub DTO/client/status to `infrastructure.finnhub`; no Finnhub type on any port (FR-012) |
| 6 (async) | Event contracts | N/A | no event, no broker |
| **7. Persistence** | | | |
| 7.x | Ownership / migration / constraints / tx | N/A | **EN005 adds no schema, no migration, no table, no write** (FR-021). Results live only in the short-lived in-process cache |
| **8. External Integrations** | | | |
| 8.1 | Behind ports / adapters | PASS | 3 outbound ports; Finnhub SDK-free adapter under `infrastructure.finnhub` |
| 8.2 | Provider-specific models inside adapters | PASS | Finnhub DTOs confined; ArchUnit rule 1 |
| 8.3 | Timeouts configured | PASS | explicit connect 2s / read 5s on the `ClientHttpRequestFactory` (FR-017); `FinnhubConfigurationTest` |
| 8.4 | Retry deliberate | PASS | **no retry** in v1 (OD-EN005-5) — documented; a single call per port call |
| 8.5 | Rate-limit behavior considered | PASS | `429` → `ProviderRateLimitedException` (distinct); short-lived cache reduces call volume; no aggressive retry |
| 8.6 | External failures translated safely | PASS | every failure → one of 7 neutral exceptions; `AR-042` — no state to corrupt (EN005 writes none) |
| 8.7 | Deterministic mocks / stubs / containers | PASS | `MockRestServiceServer` (constitution VII — mocks are correct for a true external provider; Testcontainers N/A) |
| **9. AI / LLM** | N/A | | no LLM anywhere (FR-035) |
| **10. Security & Privacy** | | | |
| 10.1–10.2 | AuthN / AuthZ | N/A | no user-facing surface; the Finnhub key is a provider credential, not user auth |
| 10.3 | Investor-owned resources isolated | N/A | EN005 handles instrument/market data, not investor-owned data |
| 10.4 | No secrets in source control | PASS | `api-key: ${FINNHUB_API_KEY:}` (empty default); no key committed; repo/artifact grep clean (SC-007) |
| 10.5 | Logs do not expose secrets | PASS | key sent as a **header**; `FinnhubRestClient` logs no URL/body; `FinnhubRestClientLoggingTest` asserts no key in the record even when the provider error body echoes it |
| 10.6 | External providers get only what's needed | PASS | one `GET` per operation with a symbol/base and the token header — nothing else |
| 10.7 | Inputs validated at trust boundary | PASS | `InstrumentIdentifier` normalizes/validates; resolver rejects unmapped MICs before any call |
| **11. Observability** | | | |
| 11.x | Structured logs / metrics / traces | PASS | one structured `event=FinnhubCall` record per call (`provider`, `operation`, `outcome`, `httpStatusCategory`, `latencyMs`) via the platform's existing ECS mechanism. No new observability infra (enabler §24; platform has no outbound-HTTP tracing yet) |
| **12. Resilience** | | | |
| 12.1 | External call timeouts explicit | PASS | FR-017 |
| 12.2 | Retry policies safe | PASS | none (OD-EN005-5) |
| 12.3 | Idempotency | PASS | reads are naturally idempotent |
| 12.4 | Failure doesn't corrupt state | PASS | EN005 writes nothing |
| 12.5 | Graceful degradation | PASS | a blank key / Finnhub outage does not stop the app or FD001/FD002/FD003 (`FinnhubIntegrationIT`; AR-045) |
| 12.6 | Critical failure scenarios tested | PASS | see 4.8 |
| **13. Documentation** | | | |
| 13.1 | Non-obvious reasoning documented | PASS | Javadoc on every port / mapper / client / resolver / cache / exception; research D1–D12 |
| 13.2 | Public contracts documented | PASS | `contracts/market-data-ports.md` (in-process) + `contracts/finnhub-provider-contract.md` (consumed HTTP) |
| 13.3 | Feature docs reflect approved behavior | PASS | `backend/core-service/README.md` + `implementation/platform/README.md` updated |
| 13.4 | ADRs added / updated | N/A | none required |
| 13.5–13.6 | Diagrams / product docs | N/A | unchanged; **no `product/` edit** |
| 13.7 | No generated doc contradicts product docs | PASS | specs trace to EN005; consistent |
| **14. Repository Hygiene** | | | |
| 14.1–14.2 | No build artifacts / IDE files committed | PASS | `target/` git-ignored; only source + test + 1 CSV + fixtures + specs are new |
| 14.3–14.5 | No private data / credentials; synthetic test data | PASS | fixtures are synthetic JSON; the smoke test reads `FINNHUB_API_KEY` from the env and never prints it |
| 14.6 | Dependency changes intentional | PASS | **none** |
| **15. CI/CD** | N/A (no CI) | | validated locally: `./mvnw -B clean verify` offline — build + tests + coverage gate + ArchUnit all green |
| **16. Review** | | | |
| 16.1–16.2 | Reviewed vs spec / architecture rules | PASS | this checklist + `pr-evidence.md` |
| 16.3 | AI-generated code critically reviewed | PASS | each file checked against ADR-003 placement, the provider-isolation invariant, `BigDecimal`, and the no-key-leak rule |
| 16.4 | Known limitations explicit | PASS | Finnhub free-tier reach; no real-socket timeout test; OD-EN005-9 / exception-ctor deviations — all recorded in `pr-evidence.md` |
| 16.5 | No hidden blocker / TODO | PASS | none in the diff |
| **17. Product Acceptance (enabler)** | | | |
| 17.1 | Evidence for every VC | PASS | `quickstart.md` coverage table (VC-001…VC-019) |
| 17.2 | Behavior matches intent | PASS | provider-neutral price / profile / FX behind 3 replaceable ports, secret-safe, decimal-safe, freshness-tagged — exactly EN005 §4/§7/§9/§13/§21/§27 |
| 17.3 | Owner can understand what was built | PASS | `pr-evidence.md` + README |
| 17.4 | No undocumented assumptions | PASS | Assumptions A1–A20 in `spec.md`; ODs in `plan.md`; deviations in `pr-evidence.md` |

**Result: all applicable items PASS; N/A items explained. EN005 meets the Definition of Done
(pending human closure approval).**
