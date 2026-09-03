import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for the containerized platform E2E suite (EN002).
 *
 * - Tests run INSIDE the `e2e` container and target the already-running frontend
 *   (`E2E_BASE_URL`, default `http://frontend` on the Compose network). This config does NOT
 *   manage the application lifecycle — `e2e.sh` / Docker Compose own it (no `webServer`).
 * - Chromium only for the initial baseline (OD-4).
 * - Diagnostics (OD-8): screenshot + trace on failure only; video off. Artifacts under
 *   `test-results/` (git-ignored, bind-mounted to the host by `e2e.sh`).
 * - FD003: `e2e.sh` gives one disposable database per run. The `portfolio-empty` project runs the
 *   empty-state check (E2E-002) and the `chromium` project declares `dependencies: ['portfolio-empty']`
 *   so Playwright guarantees the empty-state spec completes on the fresh, empty database before any
 *   portfolio-creating spec runs (declaration order alone is not a Playwright ordering guarantee).
 */
export default defineConfig({
  testDir: './tests',
  // Per-test artifacts (screenshots, traces) go in a subfolder so they don't clash with the
  // HTML report, which also lives under test-results/ (git-ignored, bind-mounted by e2e.sh).
  outputDir: './test-results/artifacts',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: './test-results/html' }],
  ],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://frontend',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },
  projects: [
    {
      name: 'portfolio-empty',
      testMatch: /fd003-portfolio-empty\.spec\.ts$/,
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'chromium',
      testIgnore: /fd003-portfolio-empty\.spec\.ts$/,
      dependencies: ['portfolio-empty'],
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
