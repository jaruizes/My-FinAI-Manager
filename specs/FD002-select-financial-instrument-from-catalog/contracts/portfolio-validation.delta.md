# Contract delta — `POST /api/portfolios` (FD002)

FD002 makes **one additive change** to the FD001 `POST /api/portfolios` OpenAPI operation in
`implementation/platform/contracts/openapi/openapi.yaml`. Everything else about the operation —
request body, `Idempotency-Key`, `201` / `200`-replay / `415` / `503`, the `Portfolio` schema — is
**unchanged**.

`GET /api/financial-instruments` (EN004) is **reused exactly as shipped** — no change.

---

## Change: `ValidationProblem.errors[].code` gains `INSTRUMENT_NOT_IN_CATALOG`

### Before

```yaml
code:
  type: string
  enum:
    - REQUIRED
    - AT_LEAST_ONE
    - INVALID_NUMBER
    - INVALID_DATE
    - NOT_POSITIVE
    - DUPLICATE_INSTRUMENT
    - FUTURE_DATE
    - CURRENCY_FORMAT
    - NAME_TOO_LONG
  description: Stable machine token identifying the rule that failed. Canonical list of FD001 validation codes.
```

### After

```yaml
code:
  type: string
  enum:
    - REQUIRED
    - AT_LEAST_ONE
    - INVALID_NUMBER
    - INVALID_DATE
    - NOT_POSITIVE
    - DUPLICATE_INSTRUMENT
    - FUTURE_DATE
    - CURRENCY_FORMAT
    - NAME_TOO_LONG
    - INSTRUMENT_NOT_IN_CATALOG        # FD002 — position's ticker+market+currency is not one active catalogued listing
  description: >
    Stable machine token identifying the rule that failed. `REQUIRED … NAME_TOO_LONG` are the
    FD001 structural/business codes. `INSTRUMENT_NOT_IN_CATALOG` (FD002) means the position's
    `ticker + market + currency` does not, as a whole, correspond to one **active** Financial
    Instrument listing in the platform catalog — the instrument is unknown, inactive, on a
    different market, or the submitted currency differs from the listing's currency.
```

### Add an example to the `400` response

Extend the existing `missingNameAndDuplicate` examples block (or add a sibling example):

```yaml
'400':
  content:
    application/problem+json:
      examples:
        notInCatalog:
          value:
            type: /problems/portfolio-validation
            title: Portfolio could not be validated
            status: 400
            detail: The portfolio has 1 problem that needs to be fixed.
            errors:
              - field: positions[0]
                code: INSTRUMENT_NOT_IN_CATALOG
                message: "AAPL on XMAD in EUR is not a selectable instrument."
```

---

## Semantics (normative)

| Aspect | Value |
|---|---|
| HTTP status | `400` |
| `type` | `/problems/portfolio-validation` (**unchanged** — same problem class) |
| `errors[].field` | `positions[i]` (the offending position; **not** a sub-field) |
| `errors[].code` | `INSTRUMENT_NOT_IN_CATALOG` |
| `errors[].message` | non-technical, names the ticker + market (+ currency); e.g. `"AAPL on XMAD in EUR is not a selectable instrument."` |
| Persistence | **nothing is persisted** (same atomic guarantee as every other `ValidationProblem`) |
| Combination with other codes | reported together — a portfolio with a structural error on `positions[0]` and a non-catalogued `positions[1]` returns both in one `errors[]` |
| Idempotency | a rejected request creates nothing; retrying the corrected portfolio with the **same** `Idempotency-Key` is allowed (FD001 semantics unchanged) |

### Backward compatibility

Additive enum value only. Existing FD001 clients that switch on the known codes are unaffected
(they treat an unknown code as a generic error, which is the correct fallback). Not a breaking
change (constitution VIII).

---

## Contract test (required — FR-020)

Add to `CreatePortfolioControllerContractTest` (`@WebMvcTest` + `swagger-request-validator`):

- **Given** the service throws `PortfolioValidationException` with a single
  `INSTRUMENT_NOT_IN_CATALOG` violation on `positions[0]`,
- **When** `POST /api/portfolios` is called,
- **Then** the response is `400 application/problem+json`, **conforms to `openapi.yaml`**, and
  `$.errors[0].code == "INSTRUMENT_NOT_IN_CATALOG"`, `$.errors[0].field == "positions[0]"`,
  `$.type == "/problems/portfolio-validation"`.

The OpenAPI edit + this test land **before** `CreatePortfolioService` emits the code (contract-first).
