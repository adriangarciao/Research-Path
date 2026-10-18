-- V3: record what normalization bounded or could not observe (playbook M04).
--   author_count        total named authors the provider supplied; the stored list is capped
--   abstract_truncated  abstract text was cut at the length limit (word boundary)
--   references_supplied provider supplied a referenced_works list at all (TRUE even if empty);
--                       distinguishes "no references listed" from "list not supplied"

ALTER TABLE publication
    ADD COLUMN author_count        INTEGER,
    ADD COLUMN abstract_truncated  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN references_supplied BOOLEAN;

-- Backfill existing rows from evidence already stored (no provider calls):
-- before V3 author lists were never capped, so the stored count is the total.
UPDATE publication p
SET author_count = (SELECT count(*) FROM publication_author a WHERE a.publication_id = p.id);

ALTER TABLE publication ALTER COLUMN author_count SET NOT NULL;

-- The frozen raw provider record shows whether referenced_works was supplied.
-- Rows without any snapshot record stay NULL (unknown).
UPDATE publication p
SET references_supplied = (
    SELECT jsonb_typeof(m.source_record -> 'referenced_works') = 'array'
    FROM snapshot_membership m
    WHERE m.publication_id = p.id
    ORDER BY m.retrieved_at DESC
    LIMIT 1);
