-- V2: publication cache, classifications, references, identity evidence, candidate snapshots.
-- Blueprint references: §3.5 (data-quality gate), §4.2 (data to retain), §4.4 (snapshots), §6.
--
-- Snapshot rule: `publication` and its child tables hold the LATEST normalized provider data
-- (a cache that may be refreshed). Each `snapshot_membership` row stores the exact provider
-- record as retrieved (`source_record`), so a snapshot stays reproducible after the cache changes.

-- ---------------------------------------------------------------------------
-- Publication cache (one row per OpenAlex work; never duplicated per search)
-- ---------------------------------------------------------------------------
CREATE TABLE publication (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    openalex_id            VARCHAR(32)  NOT NULL UNIQUE,   -- short form, e.g. W2116341502
    doi                    VARCHAR(255),                   -- lowercase, without https://doi.org/
    title                  TEXT         NOT NULL,
    normalized_title       TEXT         NOT NULL,          -- for possible-duplicate detection only
    publication_year       INTEGER,
    publication_date       DATE,
    work_type              VARCHAR(64),
    language               VARCHAR(16),
    source_name            TEXT,
    landing_page_url       TEXT,
    oa_url                 TEXT,                           -- provider-reported open-access URL, if any
    is_oa                  BOOLEAN,                        -- NULL = unknown
    abstract_text          TEXT,                           -- reconstructed from abstract_inverted_index
    cited_by_count         INTEGER,
    fwci                   DOUBLE PRECISION,
    citation_percentile    DOUBLE PRECISION,
    referenced_works_count INTEGER,
    -- Metadata availability flags (missing is "unknown", never zero/false evidence of quality)
    has_abstract           BOOLEAN      NOT NULL,
    has_topics             BOOLEAN      NOT NULL,
    has_keywords           BOOLEAN      NOT NULL,
    has_references         BOOLEAN      NOT NULL,
    provider_updated_at    TIMESTAMPTZ,
    first_retrieved_at     TIMESTAMPTZ  NOT NULL,
    last_retrieved_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_publication_doi ON publication (doi);
CREATE INDEX idx_publication_normalized_title ON publication (normalized_title);

CREATE TABLE publication_author (
    publication_id     BIGINT      NOT NULL REFERENCES publication (id) ON DELETE CASCADE,
    author_order       INTEGER     NOT NULL,
    author_openalex_id VARCHAR(32),
    display_name       TEXT        NOT NULL,
    author_position    VARCHAR(16),                        -- first / middle / last, as provided
    PRIMARY KEY (publication_id, author_order)
);

-- ---------------------------------------------------------------------------
-- Classifications: broad topics and fine keywords are kept distinct (blueprint §4.6)
-- ---------------------------------------------------------------------------
CREATE TABLE concept (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind         VARCHAR(16)  NOT NULL CHECK (kind IN ('TOPIC', 'KEYWORD')),
    openalex_id  VARCHAR(160) NOT NULL,                    -- e.g. T11273, keywords/graph-neural-networks
    display_name TEXT         NOT NULL,
    subfield     TEXT,                                     -- topics only
    field        TEXT,
    domain       TEXT,
    UNIQUE (kind, openalex_id)
);

CREATE TABLE publication_concept (
    publication_id BIGINT  NOT NULL REFERENCES publication (id) ON DELETE CASCADE,
    concept_id     BIGINT  NOT NULL REFERENCES concept (id),
    score          DOUBLE PRECISION,                       -- provider confidence; NULL = not supplied
    is_primary     BOOLEAN NOT NULL DEFAULT FALSE,         -- OpenAlex primary_topic
    PRIMARY KEY (publication_id, concept_id)
);
CREATE INDEX idx_publication_concept_concept ON publication_concept (concept_id);

-- Actual referenced work IDs (not just counts). The referenced work need not be in our cache.
CREATE TABLE publication_reference (
    citing_publication_id  BIGINT      NOT NULL REFERENCES publication (id) ON DELETE CASCADE,
    referenced_openalex_id VARCHAR(32) NOT NULL,
    PRIMARY KEY (citing_publication_id, referenced_openalex_id)
);
CREATE INDEX idx_publication_reference_referenced ON publication_reference (referenced_openalex_id);

-- ---------------------------------------------------------------------------
-- Possible duplicates / versions: EVIDENCE ONLY. Nothing is merged automatically
-- (merge policy is OPEN, decision D06). Pairs are stored with a < b to avoid mirrors.
-- ---------------------------------------------------------------------------
CREATE TABLE publication_identity_candidate (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    publication_a_id BIGINT      NOT NULL REFERENCES publication (id) ON DELETE CASCADE,
    publication_b_id BIGINT      NOT NULL REFERENCES publication (id) ON DELETE CASCADE,
    match_type       VARCHAR(32) NOT NULL CHECK (match_type IN ('SAME_DOI', 'SAME_NORMALIZED_TITLE')),
    evidence         JSONB       NOT NULL,
    review_status    VARCHAR(16) NOT NULL DEFAULT 'UNREVIEWED'
                     CHECK (review_status IN ('UNREVIEWED', 'SAME_WORK', 'DISTINCT_VERSION', 'DIFFERENT_WORK')),
    detected_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (publication_a_id < publication_b_id),
    UNIQUE (publication_a_id, publication_b_id, match_type)
);

-- ---------------------------------------------------------------------------
-- Candidate snapshots: frozen, reproducible pools of retrieved works for one topic
-- ---------------------------------------------------------------------------
CREATE TABLE candidate_snapshot (
    id                   UUID        PRIMARY KEY,          -- unguessable identifier
    topic                TEXT        NOT NULL,
    normalized_topic     TEXT        NOT NULL,
    provider             VARCHAR(32) NOT NULL,
    retrieval_params     JSONB       NOT NULL,             -- exact provider request parameters
    requested_count      INTEGER     NOT NULL,
    provider_total_count BIGINT,
    received_count       INTEGER     NOT NULL,             -- records returned by the provider
    stored_count         INTEGER     NOT NULL,             -- distinct, normalizable records kept
    rejected_count       INTEGER     NOT NULL,
    normalizer_version   INTEGER     NOT NULL,
    retrieval_notes      JSONB       NOT NULL DEFAULT '[]'::jsonb, -- rejections, page-drift duplicates
    created_at           TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_candidate_snapshot_topic ON candidate_snapshot (normalized_topic, created_at DESC);

CREATE TABLE snapshot_membership (
    snapshot_id              UUID        NOT NULL REFERENCES candidate_snapshot (id) ON DELETE CASCADE,
    publication_id           BIGINT      NOT NULL REFERENCES publication (id),
    provider_rank            INTEGER     NOT NULL,         -- 1-based position in provider results
    provider_relevance_score DOUBLE PRECISION,
    -- Relevance gate is not implemented yet (threshold is OPEN, decision D07).
    eligibility_status       VARCHAR(16) NOT NULL DEFAULT 'UNSCREENED'
                             CHECK (eligibility_status IN ('UNSCREENED', 'ELIGIBLE', 'INELIGIBLE')),
    source_record            JSONB       NOT NULL,         -- provider record exactly as retrieved
    retrieved_at             TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (snapshot_id, publication_id),
    UNIQUE (snapshot_id, provider_rank)
);
CREATE INDEX idx_snapshot_membership_publication ON snapshot_membership (publication_id);
