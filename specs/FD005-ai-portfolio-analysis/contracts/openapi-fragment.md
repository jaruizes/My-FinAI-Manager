# OpenAPI fragment — FD005 (merged into `contracts/openapi/openapi.yaml` during implementation)

Two new paths under the existing `Portfolio` tag; one new schema group. No existing path/schema
changes. `Problem` (not `ValidationProblem` — no per-field errors here) is reused for 404/409.

```yaml
  /api/portfolios/{portfolioId}/analysis/latest:
    get:
      operationId: getLatestPortfolioAnalysis
      tags: [Portfolio]
      summary: Get the most recently requested AI Portfolio Analysis
      description: >
        Returns the latest AI-generated analysis for the portfolio — diversification, key insights,
        and structured risks — computed asynchronously from the portfolio's deterministic valuation
        (FD004) via the provider-neutral AI architecture (EN006). A portfolio that has never had an
        analysis requested returns status `NONE`. Content fields are present only when `status` is
        `COMPLETED`. Read-only.
      parameters:
        - name: portfolioId
          in: path
          required: true
          schema: { type: string, format: uuid }
      responses:
        '200':
          description: The latest analysis (or an explicit `NONE` placeholder).
          content:
            application/json:
              schema: { $ref: '#/components/schemas/PortfolioAnalysis' }
              examples:
                none:
                  summary: Never requested
                  value: { status: NONE }
                pending:
                  summary: Requested, not yet processed
                  value: { status: PENDING, requestedAt: "2026-09-05T20:00:00Z" }
                completed:
                  summary: Completed analysis
                  value:
                    status: COMPLETED
                    requestedAt: "2026-09-05T20:00:00Z"
                    completedAt: "2026-09-05T20:00:07Z"
                    overallDiversification: { level: MODERATE, explanation: "Concentrated in Technology." }
                    keyInsights:
                      - { type: SECTOR_EXPOSURE, message: "Technology represents 76.19% of the Portfolio." }
                    risks:
                      - { type: SECTOR_CONCENTRATION, severity: HIGH, title: "Sector concentration", explanation: "..." }
                failed:
                  summary: Failed analysis
                  value: { status: FAILED, requestedAt: "2026-09-05T20:00:00Z", completedAt: "2026-09-05T20:00:03Z" }
        '400':
          description: The `{portfolioId}` path segment is not a valid UUID.
        '404':
          description: No portfolio with this id belongs to the current investor.
          content:
            application/problem+json:
              schema: { $ref: '#/components/schemas/Problem' }
              example:
                type: /problems/portfolio-not-found
                title: Portfolio not found
                status: 404

  /api/portfolios/{portfolioId}/analysis:
    post:
      operationId: requestPortfolioAnalysis
      tags: [Portfolio]
      summary: Request a new AI Portfolio Analysis (manual re-analysis)
      description: >
        Creates a new immutable analysis record and submits it for asynchronous background
        processing. Returns promptly — never waits for AI completion. Rejected with `409` when the
        latest analysis for this portfolio is already `PENDING` or `RUNNING` (duplicate-request
        prevention is enforced by the database, not only the frontend). The previous analysis, if
        any, is never modified.
      parameters:
        - name: portfolioId
          in: path
          required: true
          schema: { type: string, format: uuid }
      responses:
        '202':
          description: A new analysis was accepted for background processing.
          content:
            application/json:
              schema: { $ref: '#/components/schemas/RequestedPortfolioAnalysis' }
        '400':
          description: The `{portfolioId}` path segment is not a valid UUID.
        '404':
          description: No portfolio with this id belongs to the current investor.
          content:
            application/problem+json:
              schema: { $ref: '#/components/schemas/Problem' }
        '409':
          description: The latest analysis for this portfolio is already `PENDING` or `RUNNING`.
          content:
            application/problem+json:
              schema: { $ref: '#/components/schemas/Problem' }
              example:
                type: /problems/analysis-already-in-progress
                title: Analysis already in progress
                status: 409
                detail: A Portfolio Analysis is already pending or running for this portfolio.
```

```yaml
    PortfolioAnalysis:
      type: object
      additionalProperties: false
      required: [status]
      properties:
        status:
          type: string
          enum: [NONE, PENDING, RUNNING, COMPLETED, FAILED]
        requestedAt: { type: string, format: date-time, nullable: true }
        completedAt: { type: string, format: date-time, nullable: true }
        overallDiversification:
          type: object
          nullable: true
          additionalProperties: false
          required: [level, explanation]
          properties:
            level: { type: string, enum: [LOW, MODERATE, HIGH] }
            explanation: { type: string }
        keyInsights:
          type: array
          nullable: true
          items:
            type: object
            additionalProperties: false
            required: [type, message]
            properties:
              type: { type: string }
              message: { type: string }
        risks:
          type: array
          nullable: true
          items:
            type: object
            additionalProperties: false
            required: [type, severity, title, explanation]
            properties:
              type: { type: string }
              severity: { type: string, enum: [HIGH, MEDIUM, LOW] }
              title: { type: string }
              explanation: { type: string }

    RequestedPortfolioAnalysis:
      type: object
      additionalProperties: false
      required: [analysisId, status, requestedAt]
      properties:
        analysisId: { type: string, format: uuid }
        status: { type: string, enum: [PENDING] }
        requestedAt: { type: string, format: date-time }
```

**No `provider`/`model`/`promptId`/`promptVersion`/token/cost field anywhere in these schemas** —
FD005 §28 explicitly keeps that metadata out of the Investor-facing API (it stays in the persisted
row and EN006 telemetry only).
