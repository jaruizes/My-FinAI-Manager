import { test, expect } from '@playwright/test';

/**
 * EN001 mandatory E2E scenario (AC-006): proves the complete technical journey
 *
 *   Playwright → Browser → Angular → REST → Spring Boot → PostgreSQL
 *
 * by opening the Home page and asserting the exact platform version that Flyway
 * seeded into PostgreSQL is rendered in the UI. Fails if the value cannot
 * traverse the whole chain — and the frontend must never fabricate one.
 */
const EXPECTED_VERSION = '0.1.0';

test('Home renders the platform version persisted in PostgreSQL', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByTestId('version')).toHaveText(EXPECTED_VERSION, { timeout: 15_000 });

  // The error state must not be present on the happy path (no fabricated value).
  await expect(page.getByTestId('version-error')).toHaveCount(0);

  // The application shell identifies itself…
  await expect(page.getByRole('heading', { name: 'My-FinAI-Manager' })).toBeVisible();

  // …and exposes no product functionality.
  await expect(page.locator('nav')).toHaveCount(0);
  await expect(page.locator('a')).toHaveCount(0);
  await expect(page.locator('button')).toHaveCount(0);
});
