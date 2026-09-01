-- EN001 — Bootstrap Executable Platform: Flyway baseline.
--
-- This migration intentionally creates NO tables. It exists only to establish the migration
-- baseline so that Feature Definitions (starting with FD001 — Create Investment Portfolio) add
-- their schema as V2__*, V3__*, ... without touching platform infrastructure.
--
-- EN001 introduces no business schema (enabler §8). After this migration the only table present
-- is Flyway's own `flyway_schema_history`.

SELECT 1;
