-- V5: relevance gate (M06, decision D07) and frozen evaluation inputs.
-- normalized_record freezes the normalized work exactly as evaluated, so later cache refreshes or
-- normalizer upgrades cannot change a historical snapshot's inputs (playbook M06 slices 5-6).
-- All new columns are NULL for snapshots created before V5 (not evaluated).

ALTER TABLE candidate_snapshot
    ADD COLUMN relevance_policy_version INTEGER,
    ADD COLUMN warnings JSONB;

ALTER TABLE snapshot_membership
    ADD COLUMN normalized_record  JSONB,
    ADD COLUMN relevance_tier     VARCHAR(16)
               CHECK (relevance_tier IN ('ELIGIBLE', 'TANGENTIAL', 'BORDERLINE', 'INELIGIBLE')),
    ADD COLUMN relevance_reason   VARCHAR(48),
    ADD COLUMN relevance_evidence JSONB;

CREATE INDEX idx_snapshot_membership_eligible
    ON snapshot_membership (snapshot_id, provider_rank)
    WHERE eligibility_status = 'ELIGIBLE';
