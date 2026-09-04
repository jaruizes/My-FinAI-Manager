'use strict';

/*
 * FD004 E2E — external market-data boundary stub (no network, no key). Serves canned responses for
 * the provider endpoints EN005 calls, so `./e2e.sh` exercises EN005's REAL adapter / mapper / cache
 * / error-translation path against a controlled boundary — no live finnhub.io / api.frankfurter.dev,
 * no outbound Internet (FD004 §25; SC-010). Response shapes mirror
 * `specs/EN005-establish-finnhub-market-data-integration/contracts/{finnhub,frankfurter}-provider-contract.md`.
 *
 * Endpoints:
 *   - Finnhub  GET /quote?symbol=          latest price
 *   - Finnhub  GET /stock/profile2?symbol= company profile / sector
 *   - Frankfurter GET /v1/latest?base=&symbols=   EUR/USD FX rate (EN005 Revision 2 moved FX here)
 *
 * Behaviour is keyed by the requested symbol / base:
 *   - known symbols (AAPL, SAN.MC) return the deterministic E2E-001 values;
 *   - the magic symbol RATELIMIT returns HTTP 429 (→ ProviderRateLimitedException);
 *   - every other symbol returns Finnhub's "unknown symbol" answer: `{}` (→ unavailable),
 *     which drives the E2E-002 provider-failure scenario without any mode switch.
 * FX always resolves (both directions) so E2E-001 can produce both totals.
 */

const http = require('http');

const PORT = Number(process.env.PORT || 8080);
const NOW = Math.floor(Date.now() / 1000);
const TODAY = new Date().toISOString().slice(0, 10); // Frankfurter dates its rates (YYYY-MM-DD)

const QUOTES = {
  AAPL: { c: 200, t: NOW },
  'SAN.MC': { c: 5, t: NOW },
};

const PROFILES = {
  AAPL: { ticker: 'AAPL', name: 'Apple Inc.', currency: 'USD', exchange: 'NASDAQ NMS - GLOBAL MARKET', finnhubIndustry: 'Technology' },
  'SAN.MC': { ticker: 'SAN.MC', name: 'Banco Santander SA', currency: 'EUR', exchange: 'BOLSA DE MADRID', finnhubIndustry: 'Financial Services' },
};

// Legacy Finnhub /forex/rates shape (unused after EN005 Revision 2 — kept harmless).
const FOREX = {
  USD: { base: 'USD', quote: { EUR: 0.8 } },
  EUR: { base: 'EUR', quote: { USD: 1.25 } },
};

// Frankfurter /v1/latest shape — the deterministic FD004 E2E-001 rates.
const FRANKFURTER_RATES = {
  USD: { EUR: 0.8 },
  EUR: { USD: 1.25 },
};

function send(res, status, body) {
  const json = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(json) });
  res.end(json);
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const path = url.pathname;

  if (path === '/' || path === '/health') {
    return send(res, 200, { status: 'UP' });
  }

  if (path === '/quote') {
    const symbol = (url.searchParams.get('symbol') || '').toUpperCase();
    if (symbol === 'RATELIMIT') return send(res, 429, { error: 'rate limited' });
    return send(res, 200, QUOTES[symbol] || {});
  }

  if (path === '/stock/profile2') {
    const symbol = (url.searchParams.get('symbol') || '').toUpperCase();
    if (symbol === 'RATELIMIT') return send(res, 429, { error: 'rate limited' });
    return send(res, 200, PROFILES[symbol] || {});
  }

  if (path === '/forex/rates') {
    const base = (url.searchParams.get('base') || '').toUpperCase();
    return send(res, 200, FOREX[base] || { base, quote: {} });
  }

  if (path === '/v1/latest') {
    const base = (url.searchParams.get('base') || '').toUpperCase();
    const symbols = (url.searchParams.get('symbols') || '').toUpperCase();
    const all = FRANKFURTER_RATES[base] || {};
    const rates = symbols
      ? Object.fromEntries(symbols.split(',').filter((s) => all[s] !== undefined).map((s) => [s, all[s]]))
      : all;
    return send(res, 200, { amount: 1.0, base, date: TODAY, rates });
  }

  return send(res, 404, { error: 'not found', path });
});

server.listen(PORT, () => {
  // eslint-disable-next-line no-console
  console.log(`market-data stub listening on :${PORT} (Finnhub /quote, /stock/profile2; Frankfurter /v1/latest)`);
});
