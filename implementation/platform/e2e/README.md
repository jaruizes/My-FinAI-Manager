# Browser E2E tests (Playwright) — My-FinAI-Manager

Containerized end-to-end tests that drive the **real frontend** in a real browser against the
**fully containerized platform** (`postgres` + `backend` + `frontend`). Introduced by
`EN002 — Establish Containerized End-to-End Testing Foundation`.

E2E is one layer among several and is deliberately **small and journey-focused** — it does not
replace unit / integration / contract / architecture tests. See
`product/engineering/testing-strategy.md`.

## Prerequisites

- **Docker** + Docker Compose v2. That's it — Playwright and its browsers run inside a container,
  so you do **not** need Node or `npx playwright install` on your host.

## Run

Everything goes through the single entry point:

```bash
cd implementation/platform
./e2e.sh
```

`e2e.sh` builds the images, starts an **isolated** throwaway platform (Compose project
`finai-e2e`, disposable database volume, host ports shifted to `15432 / 18080 / 14200` so it never
collides with your normal `./start.sh` platform), waits for health, runs Playwright, prints the
result, and **always tears the environment down** (including its volume). The script's exit code
is Playwright's exit code.

### Run a single test / filter

Arguments are passed straight through to `playwright test`:

```bash
./e2e.sh -g "shell loads"          # only tests whose title matches
./e2e.sh tests/platform-smoke.spec.ts
```

## Debug a failure

On failure, artifacts land in `implementation/platform/e2e/test-results/` (git-ignored):

| Artifact | Path |
|---|---|
| Screenshot | `test-results/artifacts/<test>/test-failed-1.png` |
| Trace | `test-results/artifacts/<test>/trace.zip` |
| HTML report | `test-results/html/index.html` |
| Container logs (on failure) | `test-results/containers/{postgres,backend,frontend}.log` |

Open the trace (needs Playwright locally, or use any machine with it):

```bash
npx playwright show-trace implementation/platform/e2e/test-results/artifacts/**/trace.zip
```

Passing runs keep only the HTML report (no trace, no video).

## Add a feature-specific E2E test

`EN002` ships **only** the platform smoke test (`tests/platform-smoke.spec.ts`). Each Feature
Definition owns the E2E coverage for its own critical `frontend → backend → persistence` journey.

1. Create `tests/FD00N-<slug>.spec.ts` (e.g. `tests/FD001-create-portfolio.spec.ts`).
2. **Start from the frontend** — navigate the UI, click, type, assert what the user sees. Do
   **not** call the backend API directly as the primary check (a `request.*` call is only
   acceptable as secondary test support, with a comment explaining why).
3. Use `support/data.ts` for synthetic, collision-free values — never real portfolio data, and
   never a test-only backend endpoint.
4. Keep it to the **critical** journey; lower-level behaviour is already covered by unit /
   integration / contract tests.
5. The change is not "done" until `./e2e.sh` passes with the new test.

## Files

```text
e2e/
├── Dockerfile                 # FROM mcr.microsoft.com/playwright:v1.62.1-noble (pinned == package.json)
├── package.json               # @playwright/test pinned to 1.62.1
├── playwright.config.ts       # chromium only; baseURL from E2E_BASE_URL (default http://frontend)
├── tests/
│   └── platform-smoke.spec.ts # the only spec EN002 ships
├── support/
│   ├── readiness.ts           # optional in-test HTTP readiness guard
│   └── data.ts                # synthetic value helpers for feature specs
└── test-results/              # git-ignored diagnostics (screenshots / traces / html / container logs)
```
