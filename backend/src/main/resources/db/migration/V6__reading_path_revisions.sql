-- V6: generated reading paths and their revisions (M11, decision D15).
-- A reading path is one topic on one frozen candidate snapshot; every generation or regeneration with other
-- preferences is a numbered revision of it. Only successful revisions are stored, each in a single transaction,
-- and stored rows are immutable: retrieval returns exactly what was generated, never a recomputation.

CREATE TABLE reading_path (
    id              UUID         PRIMARY KEY,
    public_id       VARCHAR(22)  NOT NULL UNIQUE CHECK (public_id ~ '^[A-Za-z0-9_-]{22}$'), -- opaque, 128 random bits
    snapshot_id     UUID         NOT NULL REFERENCES candidate_snapshot (id),             -- the original snapshot
    topic           TEXT         NOT NULL,
    idempotency_key VARCHAR(64)  UNIQUE,                                                  -- optional client key
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE TABLE path_revision (
    id                  UUID         PRIMARY KEY,
    path_id             UUID         NOT NULL REFERENCES reading_path (id) ON DELETE CASCADE,
    revision            INTEGER      NOT NULL CHECK (revision >= 1),
    parent_revision     INTEGER      CHECK (parent_revision IS NULL OR (parent_revision >= 1 AND parent_revision < revision)),
    experience          VARCHAR(16)  NOT NULL CHECK (experience IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    focus               VARCHAR(16)  NOT NULL CHECK (focus IN ('BALANCED', 'FOUNDATIONAL', 'RECENT')),
    budget              INTEGER      NOT NULL CHECK (budget BETWEEN 1 AND 20),
    engine_versions     JSONB        NOT NULL,  -- optimizer/config/sequence/concept versions used
    request_fingerprint CHAR(64)     NOT NULL,  -- sha256 of snapshot + options + engine versions
    result              JSONB        NOT NULL,  -- the complete generated result as returned to clients
    created_at          TIMESTAMPTZ  NOT NULL,
    UNIQUE (path_id, revision),
    UNIQUE (path_id, request_fingerprint)       -- identical inputs never create a second revision
);

CREATE TABLE path_revision_item (
    revision_id    UUID             NOT NULL REFERENCES path_revision (id) ON DELETE CASCADE,
    position       INTEGER          NOT NULL CHECK (position BETWEEN 1 AND 20),
    publication_id BIGINT           NOT NULL REFERENCES publication (id),
    openalex_id    VARCHAR(32)      NOT NULL,
    stage          INTEGER          NOT NULL CHECK (stage >= 1),
    selection_step INTEGER          NOT NULL CHECK (selection_step >= 1),
    marginal_gain  DOUBLE PRECISION NOT NULL,
    reason         VARCHAR(32)      NOT NULL,
    PRIMARY KEY (revision_id, position),
    UNIQUE (revision_id, publication_id)
);

-- Immutability: stored paths, revisions and items are never updated (deletion stays possible for a future
-- retention policy, decision S02).
CREATE FUNCTION reject_path_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% rows are immutable', TG_TABLE_NAME;
END
$$;

CREATE TRIGGER reading_path_immutable BEFORE UPDATE ON reading_path
    FOR EACH ROW EXECUTE FUNCTION reject_path_update();
CREATE TRIGGER path_revision_immutable BEFORE UPDATE ON path_revision
    FOR EACH ROW EXECUTE FUNCTION reject_path_update();
CREATE TRIGGER path_revision_item_immutable BEFORE UPDATE ON path_revision_item
    FOR EACH ROW EXECUTE FUNCTION reject_path_update();

CREATE INDEX idx_reading_path_snapshot ON reading_path (snapshot_id);
