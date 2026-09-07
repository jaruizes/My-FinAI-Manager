# Change Request Template

> **ID:** CRNNN
> **Related Definition:** FDNNN / ENNNN
> **Status:** Draft
> **Requested by:** Human
> **Requested date:** YYYY-MM-DD
> **Approved by:** —
> **Approval date:** —
> **Related PR:** —

# Purpose

Record a deliberate change in human intent after the current implementation has been reviewed and found to correctly satisfy the previously approved definition.

A Change Request is **not** an implementation defect.

Use it when:

```text
implementation == approved definition
```

but the human decides that the desired result should change.

---

# 1. Context

What was reviewed, and why does the current correct implementation no longer represent the preferred outcome?

> ...

---

# 2. Requested Change

Describe the new desired behavior clearly and concisely.

- ...
- ...

---

# 3. Reason

Why does the human want this change?

> ...

---

# 4. Impact

| Area | Impact |
|---|---|
| Product behavior | Yes / No |
| UX | Yes / No |
| Business rules | Yes / No |
| Acceptance criteria | Yes / No |
| Architecture | Yes / No |
| Technology policy | Yes / No |
| Specification | Yes / No |
| Tests | Yes / No |
| Existing data / migration | Yes / No |

---

# 5. Definition Changes

Identify the sections of the authoritative Feature / Enabler Definition that must change.

| Section | Required Update |
|---|---|
| ... | ... |

After approval, update `definition.md` so it remains the current authoritative truth.

---

# 6. Acceptance Impact

List acceptance criteria to add, modify, or remove.

```text
AC-...
```

If none:

```text
No acceptance-criteria change required.
```

---

# 7. Derived Artifact Impact

List required changes to specification, plan, tasks, or tests.

- ...

---

# 8. Decision

**Status:** Draft / Approved / Rejected / Implemented / Superseded

**Human Decision:**

> ...

---

# 9. Traceability

```text
Original Definition
        ↓
Validated Implementation
        ↓
Human Review
        ↓
CRNNN
        ↓
Updated Definition
        ↓
Updated Spec / Tests
        ↓
Implementation
        ↓
CI + Validation
        ↓
Human Review
```

> **A Change Request records intentional evolution of human intent. The definition is updated; the CR remains as historical evidence.**
