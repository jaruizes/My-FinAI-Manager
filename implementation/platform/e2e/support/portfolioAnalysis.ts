import { Locator, Page, expect } from '@playwright/test';

/** Helpers for asserting the FD005 "AI Portfolio Analysis" section on the portfolio detail page. */

export function detail(page: Page): Locator {
  return page.locator('app-portfolio-detail-page');
}

/** The whole AI Portfolio Analysis section. */
export function analysisSection(page: Page): Locator {
  return detail(page).locator('.analysis');
}

/** The in-progress / unavailable / NONE state line, when the completed content is not shown. */
export function analysisState(page: Page): Locator {
  return analysisSection(page).locator('.analysis__state').first();
}

/** The diversification summary line, present only when the analysis is COMPLETED. */
export function analysisDiversification(page: Page): Locator {
  return analysisSection(page).locator('.analysis__diversification');
}

/** One risk item, matched by its title text. */
export function analysisRisk(page: Page, title: string): Locator {
  return analysisSection(page).locator('.analysis__risk', { hasText: title });
}

/** The "Run analysis again" button (US4) — present only when a new request may be submitted. */
export function rerunButton(page: Page): Locator {
  return analysisSection(page).getByRole('button', { name: 'Run analysis again' });
}

/**
 * Waits until the page's own in-app polling (research D9 — already unit-tested) has driven the
 * analysis section to a terminal state: the COMPLETED content is showing, or the FAILED message is
 * showing. Relies on the frontend's already-running 2s poll rather than reloading the page — a
 * reload would discard the SPA's in-flight optimistic state and its polling subscription. A
 * background worker under heavier load (many portfolios created earlier in the same E2E run, all
 * competing for the same small executor pool) can take longer than the frontend's own soft cap, so
 * the default timeout here is generous.
 */
export async function waitForAnalysisTerminal(page: Page, timeoutMs = 90_000): Promise<void> {
  await expect(async () => {
    const completed = await analysisDiversification(page).count();
    const stateText = await analysisState(page).innerText().catch(() => '');
    const failed = stateText.includes('We could not complete this analysis');
    expect(completed > 0 || failed).toBeTruthy();
  }).toPass({ timeout: timeoutMs, intervals: [500] });
}

/** Assert no provider payload, stack trace, or internal failure code ever appears (US5, E2E-003). */
export async function expectNoLeakedProviderDetails(page: Page): Promise<void> {
  const text = (await detail(page).innerText()) ?? '';
  const lower = text.toLowerCase();
  for (const forbidden of [
    'openai',
    'not_configured',
    'provider_unavailable',
    'guardrail',
    'exception',
    'stack trace',
    'at com.',
  ]) {
    expect(lower).not.toContain(forbidden);
  }
}
