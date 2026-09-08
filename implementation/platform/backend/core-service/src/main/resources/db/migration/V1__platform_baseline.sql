-- EN001 — Establish Executable Platform Foundation.
-- Technical platform metadata only. No product/business entities (BR-002).
-- Holds exactly one row: the authoritative application/platform version
-- returned by the bootstrap `hello` capability (FR-004, FR-008).

CREATE TABLE platform_version (
    id      SMALLINT     NOT NULL PRIMARY KEY DEFAULT 1,
    version VARCHAR(64)  NOT NULL,
    CONSTRAINT platform_version_singleton CHECK (id = 1),
    CONSTRAINT platform_version_not_blank CHECK (length(btrim(version)) > 0)
);

INSERT INTO platform_version (id, version) VALUES (1, '0.1.0');
