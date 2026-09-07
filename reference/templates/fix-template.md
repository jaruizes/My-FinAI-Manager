# Fix Template

> **ID:** FIXNNN
> **Related Definition:** FDNNN / ENNNN
> **Status:** Open
> **Detected by:** Human
> **Detected date:** YYYY-MM-DD
> **Related PR:** —
> **Validation status before detection:** OK / KO / BLOCKED

# Purpose

Record a defect identified during human review when the implementation does **not** correctly satisfy the already-approved definition.

A Fix is **not** a new product requirement.

Use it when:

```text
implementation != approved definition
```

even if CI and automated validation previously returned `PASS` / `OK`.

---

# 1. Observed Problem

What did the human observe?

> ...

---

# 2. Expected Behavior

Reference the approved source that defines the expected behavior.

| Source | Reference |
|---|---|
| Feature / Enabler | FDNNN / ENNNN |
| Requirement / Rule / AC | FR-... / BR-... / AC-... |
| Architecture rule if applicable | AAC-... |
| Other | ... |

Expected behavior:

> ...

---

# 3. Observed Behavior

> ...

---

# 4. Why Existing Validation Did Not Catch It

Select or explain the relevant cause where known:

- [ ] acceptance criterion missing or incomplete;
- [ ] automated test missing;
- [ ] test asserted the wrong behavior;
- [ ] specification misrepresented the approved definition;
- [ ] validator missed an explicit requirement;
- [ ] validator interpreted evidence incorrectly;
- [ ] implementation and tests encoded the same wrong assumption;
- [ ] edge case not covered;
- [ ] other: ...

A Fix is also feedback for improving validation.

---

# 5. Required Remediation

Describe the smallest correction needed.

- ...
- ...

The approved intent remains unchanged.

---

# 6. Impact

| Area | Impact |
|---|---|
| Production implementation | Yes / No |
| Specification correction | Yes / No |
| Acceptance tests | Yes / No |
| Unit / integration tests | Yes / No |
| Architecture checks | Yes / No |
| Validator rules / skill | Yes / No |
| Authoritative definition | Normally No |

If the authoritative definition itself is wrong or ambiguous, escalate for human decision instead of silently changing it.

---

# 7. Validation Improvement

How should recurrence be prevented, where appropriate?

Examples:

- add a missing acceptance scenario;
- strengthen project-validation;
- correct spec-conformance validation;
- add an architecture check;
- improve CI evidence.

> ...

---

# 8. Resolution Evidence

| Verification | Result | Evidence |
|---|---|---|
| CI | PASS / FAIL | ... |
| Acceptance test | PASS / FAIL | ... |
| Architecture validation | PASS / FAIL | ... |
| Project validation | OK / KO / BLOCKED | ... |
| Human retest | PASS / FAIL | ... |

---

# 9. Traceability

```text
Approved Definition
        ↓
Implementation
        ↓
CI / Validation
        ↓
Human Review
        ↓
FIXNNN
        ↓
Remediation
        ↓
same PR
        ↓
CI + Validation
        ↓
Human Retest
```

> **A Fix corrects implementation or validation so that it matches existing approved intent. It does not redefine that intent.**
