import { expect, test } from '@playwright/test';

/**
 * Platform smoke test (EN002, FR-022 / VC-009).
 *
 * Purpose: prove that `Playwright → frontend → containerized platform` works — the Angular app
 * boots, the shell renders, routing config is loaded, and nothing at the platform level blocks
 * interaction.
 *
 * This test deliberately does NOT create a portfolio, submit a form, or assert anything in the
 * database. Feature-specific journeys (e.g. FD001 "create a portfolio") belong in their own
 * `FD00N-*.spec.ts` and are NOT part of EN002.
 */
test('the platform shell loads and is interactive', async ({ page }) => {
  const pageErrors: string[] = [];
  page.on('pageerror', (err) => pageErrors.push(err.message));

  const response = await page.goto('/');
  expect(response, 'navigation response').not.toBeNull();
  expect(response!.ok(), `GET / returned ${response!.status()}`).toBeTruthy();

  // The dark three-area shell renders.
  await expect(page.locator('app-shell')).toBeVisible();
  await expect(page.locator('app-sidebar')).toBeVisible();
  await expect(page.locator('app-top-bar')).toBeVisible();

  // Routing config loaded: the first product capability's nav entry is present and clickable.
  const portfoliosNav = page.getByRole('link', { name: 'Portfolios' });
  await expect(portfoliosNav).toBeVisible();
  await expect(portfoliosNav).toHaveAttribute('href', '/portfolios/new');

  // No uncaught runtime error fired while the shell loaded.
  expect(pageErrors, `page errors: ${pageErrors.join(' | ')}`).toHaveLength(0);
});
