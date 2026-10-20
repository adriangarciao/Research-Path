-- V7: retention for anonymous paths (decision S02). Stored paths stay immutable; when a path was last opened is kept
-- in its own table so retention never needs to update a path, revision or item.
CREATE TABLE path_access (
    path_id        UUID        PRIMARY KEY REFERENCES reading_path (id) ON DELETE CASCADE,
    last_viewed_at TIMESTAMPTZ NOT NULL
);

-- Existing paths count as viewed when they were created.
INSERT INTO path_access (path_id, last_viewed_at)
SELECT id, created_at FROM reading_path;
