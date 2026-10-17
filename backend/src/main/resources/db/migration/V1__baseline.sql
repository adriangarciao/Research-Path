-- Baseline migration: intentionally creates no application objects.
-- It proves Flyway runs against PostgreSQL and records version 1 in
-- flyway_schema_history (the only object this migration causes to exist).
-- The publication / snapshot / path schema is a later milestone; add it as V2+.
SELECT 1;
