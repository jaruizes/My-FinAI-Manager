# EN002 — Prepared governance-doc alignment (needs maintainer approval before commit)

`FR-032` / `FR-033` say EN002 must position E2E in the project testing model and establish the
convention that a feature changing a `frontend → backend → persistence` journey needs a passing
E2E test, checked by the closure verifier.

These files are **human-governed** (`product/…`, and the `project-verify` skill). Per constitution I
the edits below are **proposed**, not applied. Review and, if approved, apply + commit them with
EN002 (or separately).

---

## 1. `product/architecture/technology-policy.md`

**Already applied** (with your approval, this session): Playwright added to the Approved Technology
Matrix (status **PREFERRED**, "Browser E2E Testing") and a new **"Browser End-to-End"** subsection
under *Testing Technology Policy*. No further change needed.

---

## 2. `product/engineering/testing-strategy.md` — §5 "End-to-End Tests"

**Proposed addition** (append after the existing "End-to-end tests must not replace lower-level
tests." line):

```markdown
## Execution model

End-to-end tests run through the fully containerized platform (Docker Compose: `postgres` +
`backend` + `frontend`) using Playwright, driven by `implementation/platform/e2e.sh`
(see `EN002 — Establish Containerized End-to-End Testing Foundation`).

- Tests drive the user-facing frontend in a real browser; they must not bypass the frontend and
  call backend APIs directly as the primary check.
- The E2E suite stays deliberately small — one platform smoke test plus, per Feature Definition,
  at most the critical user journey it introduces.
- E2E tests run in a container; developers do not need locally installed browsers.
- Chromium is the initial browser baseline.
```

---

## 3. `product/engineering/definition-of-done.md` — §4 "Testing"

**Proposed change** — replace the line:

```markdown
- [ ] End-to-end tests cover critical journeys when warranted.
```

with:

```markdown
- [ ] End-to-end tests cover critical journeys when warranted. A Feature Definition that
      introduces or materially changes a user journey spanning frontend → backend → persistence
      normally requires at least one passing Playwright E2E test
      (`implementation/platform/e2e/tests/FD00N-*.spec.ts`, run via `./e2e.sh`). The feature is
      not ready to close while that E2E journey is missing or failing.
```

---

## 4. `.claude/skills/project-verify/SKILL.md` — Step 9 "Test Verification"

**Proposed change** — in the "Possible categories" list, replace `- frontend tests;` with:

```markdown
- frontend tests;
- end-to-end tests (Playwright, containerized — run `./implementation/platform/e2e.sh`);
```

**Proposed addition** — after the "For integration tests against application-managed
infrastructure, verify that Testcontainers is used where required." paragraph:

```markdown
For a Feature Definition that introduces or materially changes a critical
frontend → backend → persistence journey, verify that at least one Playwright E2E test for that
journey exists and passes (`implementation/platform/e2e/tests/FD00N-*.spec.ts`; `./e2e.sh` exits
0). A missing or failing required E2E journey is a FAIL.
```

*(Optionally also note it in Step 17 "Definition of Done Evaluation" and Step 23 "Detect Unapproved
Decisions" as a checked item.)*

---

## Not in scope for these edits

- No change to the E2E **content** for FD001 — that is a follow-up on the FD001 Feature Definition
  (`EN002` §26), which should add `e2e/tests/FD001-create-portfolio.spec.ts`.
- No CI wiring — separate future enabler.
