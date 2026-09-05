# Contract — OpenAI provider adapter (`ai.infrastructure.provider.openai`, FD005)

**Adapter**: `OpenAiModelAdapter` (`implements ai.domain.ports.AiModelPort`, `@Component("openai")`) ·
**Client**: `OpenAiRestClient` · Env: `OPENAI_API_KEY` (never committed), `OPENAI_BASE_URL`
(default `https://api.openai.com/v1`), `OPENAI_MODEL` (default `gpt-4o-mini`).

Rationale: [plan.md](../plan.md) OD-3, [research.md](../research.md) D3.

---

## Request

```
POST {base-url}/chat/completions
Authorization: Bearer {OPENAI_API_KEY}
Content-Type: application/json
```

```json
{
  "model": "gpt-4o-mini",
  "messages": [
    { "role": "system", "content": "<composed system prompt: global + portfolio-analysis task instructions>" },
    { "role": "user", "content": "<user prompt + deterministic Portfolio context text>" }
  ],
  "response_format": { "type": "json_object" },
  "max_tokens": 800,
  "temperature": 0.2
}
```

- No key in the URL or query string — header only.
- `response_format: json_object` (not OpenAI's native `json_schema` mode — resolved OD-3); the
  prompt itself instructs the exact required shape.

## Response (200)

```json
{
  "id": "chatcmpl-abc123",
  "choices": [
    {
      "message": { "role": "assistant", "content": "{\"overallDiversification\":{...},\"keyInsights\":[...],\"risks\":[...]}" },
      "finish_reason": "stop"
    }
  ],
  "usage": { "prompt_tokens": 512, "completion_tokens": 180, "total_tokens": 692 }
}
```

| OpenAI field | → neutral `AiResponse` field |
|---|---|
| `choices[0].message.content` | `content`; also parsed as JSON → `structuredContent` (when `outputSchema` was requested) |
| `choices[0].finish_reason` | `finishReason` |
| `usage.prompt_tokens` / `.completion_tokens` / `.total_tokens` | `AiUsage.inputTokens` / `.outputTokens` / `.totalTokens` |
| *(computed)* `inputTokens/1000 × pricing.inputPer1k + outputTokens/1000 × pricing.outputPer1k` | `AiUsage.estimatedCost` (placeholder pricing config — spec A1) |
| `id` | `requestId` |
| — | `provider = "openai"`, `model = <configured model>` |

## Failure translation (inside the adapter — nothing OpenAI-specific crosses `AiModelPort`)

| Condition | Neutral outcome |
|---|---|
| blank `OPENAI_API_KEY` | `AiProviderNotConfiguredException` — **no outbound call** |
| HTTP `401` / `403` | `AiProviderAuthenticationFailedException` |
| HTTP `429` | `AiProviderRateLimitedException` |
| HTTP `5xx`, connect/read timeout, `IOException` | `AiProviderUnavailableException` |
| `choices[0].message.content` is not valid JSON when structured output was requested | `AiInvalidResponseException` |
| empty/missing `choices` | `AiInvalidResponseException` |

The API key never appears in the return value, an exception message, or any log line on this path
(mirrors EN005's Finnhub `X-Finnhub-Token` handling exactly).
