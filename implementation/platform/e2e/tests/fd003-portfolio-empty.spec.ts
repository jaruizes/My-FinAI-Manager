import { expect, test } from '@playwright/test';

/**
 * FD003 — List and View Portfolio Details · E2E-002 (mandatory closure gate, FD003 §16).
 *
 * Runs in its own `portfolio-empty` Playwright project, which the `chromium` project depends on, so
 * it always executes on the fresh, empty database `e2e.sh` provisions per run — before any spec
 * creates a portfolio.
 *
 * An investor with no portfolios opens Home and sees an explicit empty-state message, not a blank
 * page and not a misleading placeholder row (FD003 AC-004 / SC-002).
 */
test('E2E-002: Home shows the empty state when no portfolios exist', async ({ page }) => {
  const pageErrors: string[] = [];
  page.on('pageerror', (err) => pageErrors.push(err.message));

  await page.goto('/');

  const list = page.locator('app-portfolio-list');
  const emptyState = list.locator('.empty');
  await expect(emptyState).toContainText('do not have any portfolios yet');
  await expect(list.locator('tbody tr')).toHaveCount(0);
  await expect(emptyState.getByRole('link', { name: 'Create portfolio' })).toBeVisible();

  expect(pageErrors, `page errors: ${pageErrors.join(' | ')}`).toHaveLength(0);
});
