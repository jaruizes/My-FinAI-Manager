# ADR-002 — Interim Unauthenticated Write Access for Pre-Identity Features

> **Status:** Approved
> **Decision ID:** ADR-002
> **Date:** 2026-09-01
> **Relates to:** ADR-001; architecture-rules AR-035, AR-038; `EN001 — Bootstrap Executable Platform`; `FD001 — Create Investment Portfolio`

---

# Context

`EN001` deliberately shipped the executable platform with **no authentication** — its endpoints
(only the Actuator health mechanism at that point) are reachable without a login, and the platform
is intended for local execution only.

`FD001 — Create Investment Portfolio` introduces the **first business write operation**,
`POST /api/portfolios`, which persists private Portfolio data. Two MANDATORY architecture rules
apply:

- **AR-035** — Private Portfolio and Investor data must be treated as private information.
- **AR-038** — Authorization decisions concerning Investor-owned resources must be enforced by
  backend components.

However:

- There is no authentication capability and no Investor-management capability yet.
- `FD001`'s approved scope (Feature Definition §3) **explicitly excludes** authentication,
  authorization, and multi-investor identity management.
- `FD001` was clarified (2026-09-01) to attribute every created Portfolio to a single
  **platform-seeded default Investor**.

Blocking `FD001` (and every subsequent product feature) on the delivery of a full identity and
authentication system would stall all product delivery. Conversely, adding a partial ad-hoc auth
mechanism now would mean inventing an identity model that no Feature Definition describes.

---

# Decision

Until a dedicated **identity / authentication Technical Enabler** is delivered, product features
MAY expose read and write operations **without authentication**, provided **all** of the following
hold:

1. All Investor-owned data is attributed to the single platform-seeded **default Investor**
   (fixed, well-known id; seeded via the approved database migration mechanism).
2. The platform runs only in **local / non-production** environments. The platform MUST NOT be
   deployed to any shared or internet-reachable environment while this ADR is in force.
3. No design choice is made that would obstruct adding authentication and **per-Investor
   authorization** later:
   - every Investor-owned table carries a non-null `investor_id` foreign key from the start;
   - domain and application logic do not assume "there is exactly one Investor" beyond reading
     the seeded default id at the edge;
   - the external API models operations in business terms, not around "the current user".
4. Each feature that relies on this ADR **records the acceptance explicitly** in its
   implementation plan and its pull-request evidence (Definition of Done).

`FD001` is the first feature to rely on this ADR and satisfies conditions 1–4.

---

# Rationale

- **Unblocks product delivery.** The first vertical features (portfolio creation, then listing,
  valuation, …) can be built and validated now.
- **The data model is already multi-investor-shaped.** `investor` / `portfolio` (`investor_id`)
  are in place from `FD001`, so the future identity enabler adds authentication and an Investor
  lifecycle without reshaping the schema or the domain.
- **The risk is bounded.** No production deployment; a single synthetic Investor; private data
  never leaves the developer's machine.
- **The acceptance is explicit and time-bound**, not an accidental omission — this ADR exists so
  the debt is visible and has a defined trigger to close it.

---

# Consequences

## Positive

- Product features are not gated on a full identity system.
- The schema and domain are forward-compatible with real authentication + authorization.
- The security debt is documented, discoverable, and owned.

## Negative

- The external API is unauthenticated and unauthorized. It MUST NOT be exposed beyond a
  developer's machine.
- A follow-up Technical Enabler is required before any non-local deployment.
- Every feature built under this ADR inherits the obligation to record the acceptance and to
  avoid design choices that block future auth.

---

# Revisit / Supersede Trigger

This ADR is superseded when **either** of the following occurs, whichever is first:

- the **identity / authentication Technical Enabler** is delivered (it MUST add authentication,
  per-Investor authorization enforced backend-side per AR-038, and a real Investor lifecycle); or
- there is any plan to run the platform **outside a local developer environment**.

At that point, a new ADR records the identity/authorization model and this ADR moves to
**Superseded**.

---

# Alternatives Considered

## A — Deliver the identity/auth enabler before any product feature

Rejected for now: it front-loads a large enabler and delays all product validation; the identity
model also benefits from being informed by real product features.

## B — Add a minimal ad-hoc authentication (e.g. a static token or basic auth) in FD001

Rejected: it invents an identity/access model that no Feature Definition describes (contradicts
the AI-development and SDD policies), and a throwaway mechanism tends to ossify.

## C — Ship FD001 unauthenticated with no ADR, relying only on the spec's assumption note

Rejected: an unauthenticated write path for private data is a material posture that warrants an
explicit, human-approved architecture decision with a defined trigger to close it.

---

# Human Approval

- [X] The interim posture (conditions 1–4) is acceptable for the current stage of the project.
- [X] The non-production constraint (condition 2) is understood and enforced.
- [X] The supersede trigger is agreed.

**Approved by:*jaruiz*
**Date:*2026-09-01*
**Status:** Approved
