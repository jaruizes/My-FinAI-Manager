/**
 * Belt-and-braces readiness helper (EN002, FR-021 secondary support).
 *
 * The PRIMARY readiness gate is Docker Compose health + `e2e.sh` polling before Playwright starts.
 * This helper lets a test (or a future global setup) additionally confirm the frontend origin is
 * answering before it drives the UI, with a bounded timeout and an actionable failure.
 */
export async function waitForHttpOk(url: string, timeoutMs = 30_000, intervalMs = 1_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  let lastError = '';
  while (Date.now() < deadline) {
    try {
      const res = await fetch(url, { method: 'GET' });
      if (res.ok) return;
      lastError = `HTTP ${res.status}`;
    } catch (err) {
      lastError = err instanceof Error ? err.message : String(err);
    }
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  throw new Error(`readiness: ${url} did not return 2xx within ${timeoutMs}ms (last: ${lastError})`);
}
