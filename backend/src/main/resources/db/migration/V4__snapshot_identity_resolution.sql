-- V4: conservative identity resolution frozen per snapshot (M05, decision D06).
-- Nothing is merged or deleted. A member whose canonical_publication_id differs from its own
-- publication_id is suppressed as an obvious duplicate of that canonical member; its raw provider
-- record and publication row stay intact as provenance.

-- NULL = snapshot created before identity resolution existed (not evaluated).
ALTER TABLE candidate_snapshot ADD COLUMN identity_policy_version INTEGER;

-- NULL = not evaluated (pre-V4 snapshot); otherwise the member's canonical representative.
ALTER TABLE snapshot_membership ADD COLUMN canonical_publication_id BIGINT REFERENCES publication (id);

-- Policy decision for every flagged pair whose two works are both members of the snapshot.
CREATE TABLE snapshot_identity_decision (
    snapshot_id UUID        NOT NULL REFERENCES candidate_snapshot (id) ON DELETE CASCADE,
    pair_id     BIGINT      NOT NULL REFERENCES publication_identity_candidate (id),
    decision    VARCHAR(24) NOT NULL
                CHECK (decision IN ('SAME_WORK', 'PROBABLE_VERSION', 'DISTINCT', 'UNRESOLVED')),
    rule        VARCHAR(48) NOT NULL,
    details     JSONB       NOT NULL,
    PRIMARY KEY (snapshot_id, pair_id)
);
