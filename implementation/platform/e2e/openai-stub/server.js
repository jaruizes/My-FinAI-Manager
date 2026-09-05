'use strict';

/*
 * FD005 E2E — external AI provider boundary stub (no network, no key). Serves a canned
 * `POST /v1/chat/completions` response so `./e2e.sh` exercises the REAL `OpenAiModelAdapter` /
 * `OpenAiChatMapper` / error-translation path against a controlled boundary — no live
 * api.openai.com, no outbound Internet (constitution VII; FD005 spec A1/A9). Response shape
 * mirrors `specs/FD005-ai-portfolio-analysis/contracts/openai-provider-contract.md`.
 *
 * Failure mode (E2E-003): when the composed user message contains the magic marker
 * `E2E_PROVIDER_FAILURE` (a Portfolio named that way renders it into the prompt context via
 * PortfolioAnalysisContextBuilder), the stub returns HTTP 503 instead of a completion — driving
 * the provider-failure scenario with no stub restart or mode switch, mirroring the finnhub-stub's
 * own magic-symbol convention (RATELIMIT).
 */

const http = require('http');

const PORT = Number(process.env.PORT || 8080);
const FAILURE_MARKER = 'E2E_PROVIDER_FAILURE';

function send(res, status, body) {
  const json = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(json) });
  res.end(json);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (chunk) => { data += chunk; });
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const path = url.pathname;

  if (path === '/' || path === '/health') {
    return send(res, 200, { status: 'UP' });
  }

  if (path === '/v1/chat/completions' && req.method === 'POST') {
    const raw = await readBody(req);
    let parsed = {};
    try {
      parsed = JSON.parse(raw);
    } catch {
      // malformed request body — fall through to the deterministic completion below anyway;
      // the stub's job is to be a controlled boundary, not to validate the caller's request.
    }
    const messages = Array.isArray(parsed.messages) ? parsed.messages : [];
    const userMessage = messages.find((m) => m && m.role === 'user');
    const userContent = (userMessage && userMessage.content) || '';

    if (userContent.includes(FAILURE_MARKER)) {
      return send(res, 503, { error: { message: 'stubbed provider outage' } });
    }

    const content = JSON.stringify({
      overallDiversification: {
        level: 'MODERATE',
        explanation: 'Concentrated in Technology.',
      },
      keyInsights: [
        { type: 'SECTOR_EXPOSURE', message: 'Technology represents the majority of the Portfolio.' },
      ],
      risks: [
        {
          type: 'SECTOR_CONCENTRATION',
          severity: 'HIGH',
          title: 'Sector concentration',
          explanation: 'The Portfolio is concentrated in a single sector.',
        },
      ],
    });

    return send(res, 200, {
      id: 'chatcmpl-e2e-stub',
      choices: [{ message: { role: 'assistant', content }, finish_reason: 'stop' }],
      usage: { prompt_tokens: 100, completion_tokens: 50, total_tokens: 150 },
    });
  }

  return send(res, 404, { error: 'not found', path });
});

server.listen(PORT, () => {
  // eslint-disable-next-line no-console
  console.log(`openai-stub listening on :${PORT} (POST /v1/chat/completions)`);
});
