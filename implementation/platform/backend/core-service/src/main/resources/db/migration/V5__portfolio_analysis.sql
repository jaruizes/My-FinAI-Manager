-- FD005 — AI Portfolio Analysis.
-- Owned by the `portfolioanalysis` capability module. No other module reads/writes these tables.
-- Flyway owns the schema; Hibernate never alters it (ddl-auto: none).
--
-- IMMUTABLE ANALYSIS HISTORY (FD005 §10; BR-005): every requested analysis (automatic or manual)
-- is its OWN row, never overwritten by a later request. A single row DOES transition in place
-- through its own lifecycle (PENDING -> RUNNING -> COMPLETED|FAILED) — "immutable" means a
-- DIFFERENT analysis never touches an already-persisted row, not that one row never changes state.
--
-- DUPLICATE-REQUEST PREVENTION (FR-013; research D4): the partial unique index below is the
-- authoritative guard — at most one OPEN (PENDING/RUNNING) request per portfolio may exist at a
-- time, enforced by the database itself, not only application logic.
--
-- No FK back into any FD004 table: FD005 reads FD004 data only transiently, through its own ACL,
-- never by a persisted reference (FD004's own valuation snapshot is mutable/replaced-in-place).

CREATE TABLE portfolio_analysis (
    id                       UUID PRIMARY KEY,
    portfolio_id             UUID NOT NULL REFERENCES portfolio (id) ON DELETE CASCADE,
    status                   TEXT NOT NULL,
    requested_at             TIMESTAMPTZ NOT NULL,
    started_at               TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    summary                  TEXT,
    overall_diversification  TEXT,
    provider                 TEXT,
    model                    TEXT,
    prompt_id                TEXT,
    prompt_version           TEXT,
    input_tokens             INTEGER,
    output_tokens            INTEGER,
    total_tokens             INTEGER,
    estimated_cost           NUMERIC,
    failure_reason_code      TEXT,
    created_by_trigger       TEXT NOT NULL,
    CONSTRAINT portfolio_analysis_status_chk
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT portfolio_analysis_diversification_chk
        CHECK (overall_diversification IS NULL OR overall_diversification IN ('LOW', 'MODERATE', 'HIGH')),
    CONSTRAINT portfolio_analysis_trigger_chk
        CHECK (created_by_trigger IN ('AUTOMATIC', 'MANUAL'))
);

-- FR-015: fast "latest for this portfolio" lookup.
CREATE INDEX ix_portfolio_analysis_portfolio_requested
    ON portfolio_analysis (portfolio_id, requested_at DESC);

-- FR-013: at most one open (not-yet-terminal) request per portfolio.
CREATE UNIQUE INDEX portfolio_analysis_one_open_per_portfolio_uk
    ON portfolio_analysis (portfolio_id)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE TABLE portfolio_analysis_insight (
    id                     UUID PRIMARY KEY,
    portfolio_analysis_id  UUID NOT NULL REFERENCES portfolio_analysis (id) ON DELETE CASCADE,
    type                   TEXT NOT NULL,
    message                TEXT NOT NULL,
    display_order          INTEGER NOT NULL
);

CREATE INDEX ix_portfolio_analysis_insight_parent ON portfolio_analysis_insight (portfolio_analysis_id);

CREATE TABLE portfolio_analysis_risk (
    id                     UUID PRIMARY KEY,
    portfolio_analysis_id  UUID NOT NULL REFERENCES portfolio_analysis (id) ON DELETE CASCADE,
    type                   TEXT NOT NULL,
    severity               TEXT NOT NULL,
    title                  TEXT NOT NULL,
    explanation            TEXT NOT NULL,
    display_order          INTEGER NOT NULL,
    CONSTRAINT portfolio_analysis_risk_severity_chk
        CHECK (severity IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX ix_portfolio_analysis_risk_parent ON portfolio_analysis_risk (portfolio_analysis_id);
