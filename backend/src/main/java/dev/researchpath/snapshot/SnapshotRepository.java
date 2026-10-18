package dev.researchpath.snapshot;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.researchpath.publication.UrlSafety;
import dev.researchpath.publication.NormalizedWork;
import dev.researchpath.relevance.RelevanceTier;
import dev.researchpath.snapshot.SnapshotSummary.Availability;
import dev.researchpath.snapshot.SnapshotSummary.Classification;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class SnapshotRepository {

	private final JdbcClient jdbc;
	private final JsonMapper jsonMapper;

	public SnapshotRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
	}

	public record NewSnapshot(UUID id, String topic, String normalizedTopic, String provider, String retrievalParams,
			int requestedCount, Long providerTotalCount, int receivedCount, int storedCount, int rejectedCount,
			int normalizerVersion, String retrievalNotes, Instant createdAt) {
	}

	public void insert(NewSnapshot s) {
		jdbc.sql("""
				INSERT INTO candidate_snapshot (id, topic, normalized_topic, provider, retrieval_params,
				    requested_count, provider_total_count, received_count, stored_count, rejected_count,
				    normalizer_version, retrieval_notes, created_at)
				VALUES (:id, :topic, :normalizedTopic, :provider, CAST(:params AS jsonb), :requested, :total,
				    :received, :stored, :rejected, :normalizerVersion, CAST(:notes AS jsonb), :createdAt)
				""")
				.param("id", s.id())
				.param("topic", s.topic())
				.param("normalizedTopic", s.normalizedTopic())
				.param("provider", s.provider())
				.param("params", s.retrievalParams())
				.param("requested", s.requestedCount())
				.param("total", s.providerTotalCount())
				.param("received", s.receivedCount())
				.param("stored", s.storedCount())
				.param("rejected", s.rejectedCount())
				.param("normalizerVersion", s.normalizerVersion())
				.param("notes", s.retrievalNotes())
				.param("createdAt", Timestamp.from(s.createdAt()))
				.update();
	}

	public void addMember(UUID snapshotId, long publicationId, int providerRank, Double relevanceScore,
			String sourceRecordJson, Instant retrievedAt, String normalizedRecordJson, RelevanceTier tier,
			String relevanceReason, String relevanceEvidenceJson) {
		jdbc.sql("""
				INSERT INTO snapshot_membership (snapshot_id, publication_id, provider_rank,
				    provider_relevance_score, source_record, retrieved_at, normalized_record,
				    relevance_tier, relevance_reason, relevance_evidence, eligibility_status)
				VALUES (:sid, :pid, :rank, :score, CAST(:source AS jsonb), :retrievedAt, CAST(:normalized AS jsonb),
				    :tier, :reason, CAST(:evidence AS jsonb), :eligibility)
				""")
				.param("sid", snapshotId)
				.param("pid", publicationId)
				.param("rank", providerRank)
				.param("score", relevanceScore)
				.param("source", sourceRecordJson)
				.param("retrievedAt", Timestamp.from(retrievedAt))
				.param("normalized", normalizedRecordJson)
				.param("tier", tier.name())
				.param("reason", relevanceReason)
				.param("evidence", relevanceEvidenceJson)
				.param("eligibility", tier.eligible() ? "ELIGIBLE" : "INELIGIBLE")
				.update();
	}

	/** Members that may enter optimization: relevance-eligible and not suppressed as duplicates. */
	public int countOptimizationCandidates(UUID snapshotId) {
		return jdbc.sql("""
				SELECT count(*) FROM snapshot_membership
				WHERE snapshot_id = :id AND eligibility_status = 'ELIGIBLE' AND canonical_publication_id = publication_id
				""")
				.param("id", snapshotId)
				.query(Integer.class)
				.single();
	}

	public void finishRelevance(UUID snapshotId, int policyVersion, String warningsJson) {
		jdbc.sql("""
				UPDATE candidate_snapshot SET relevance_policy_version = :v, warnings = CAST(:w AS jsonb) WHERE id = :id
				""")
				.param("v", policyVersion)
				.param("w", warningsJson)
				.param("id", snapshotId)
				.update();
	}

	public boolean exists(UUID snapshotId) {
		return jdbc.sql("SELECT count(*) FROM candidate_snapshot WHERE id = :id").param("id", snapshotId)
				.query(Integer.class).single() > 0;
	}

	/** Frozen relevance decisions in provider rank order, optionally limited to one tier. */
	public List<RelevanceRow> relevanceRows(UUID snapshotId, RelevanceTier tier) {
		return jdbc.sql("""
				SELECT m.provider_rank, p.openalex_id, p.title, m.relevance_tier, m.relevance_reason,
				       (m.eligibility_status = 'ELIGIBLE' AND m.canonical_publication_id = m.publication_id) AS candidate,
				       m.relevance_evidence::text AS evidence
				FROM snapshot_membership m JOIN publication p ON p.id = m.publication_id
				WHERE m.snapshot_id = :id AND (CAST(:tier AS varchar) IS NULL OR m.relevance_tier = :tier)
				ORDER BY m.provider_rank
				""")
				.param("id", snapshotId)
				.param("tier", tier == null ? null : tier.name())
				.query((rs, n) -> new RelevanceRow(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getString(5), rs.getBoolean(6),
						rs.getString(7) == null ? null : jsonMapper.readTree(rs.getString(7))))
				.list();
	}

	/** Frozen normalized inputs of eligible canonical members, in provider rank order. */
	public List<FrozenCandidate> loadOptimizationCandidates(UUID snapshotId) {
		return jdbc.sql("""
				SELECT publication_id, provider_rank, normalized_record::text AS work, relevance_evidence::text AS evidence
				FROM snapshot_membership
				WHERE snapshot_id = :id AND eligibility_status = 'ELIGIBLE'
				  AND canonical_publication_id = publication_id AND normalized_record IS NOT NULL
				ORDER BY provider_rank
				""")
				.param("id", snapshotId)
				.query((rs, n) -> new FrozenCandidate(rs.getLong("publication_id"), rs.getInt("provider_rank"),
						jsonMapper.readValue(rs.getString("work"), NormalizedWork.class),
						jsonMapper.readTree(rs.getString("evidence"))))
				.list();
	}

	public Optional<SnapshotSummary> findSummary(UUID id) {
		int withSourceLink = countMembersWithSourceLink(id);
		return jdbc.sql("""
				WITH members AS (
				    SELECT p.* FROM snapshot_membership m JOIN publication p ON p.id = m.publication_id
				    WHERE m.snapshot_id = :id
				),
				primary_topics AS (
				    SELECT c.display_name, count(*) AS n
				    FROM members p
				    JOIN publication_concept pc ON pc.publication_id = p.id AND pc.is_primary
				    JOIN concept c ON c.id = pc.concept_id
				    GROUP BY c.display_name
				    ORDER BY n DESC, c.display_name
				    LIMIT 1
				)
				SELECT s.id, s.topic, s.created_at, s.provider, s.requested_count, s.provider_total_count,
				       s.received_count, s.stored_count, s.rejected_count, s.normalizer_version,
				       s.retrieval_notes::text AS notes, s.identity_policy_version,
				       s.relevance_policy_version, s.warnings::text AS warnings,
				       (SELECT count(*) FROM snapshot_membership m WHERE m.snapshot_id = s.id AND m.relevance_tier = 'ELIGIBLE') AS rel_eligible,
				       (SELECT count(*) FROM snapshot_membership m WHERE m.snapshot_id = s.id AND m.relevance_tier = 'TANGENTIAL') AS rel_tangential,
				       (SELECT count(*) FROM snapshot_membership m WHERE m.snapshot_id = s.id AND m.relevance_tier = 'BORDERLINE') AS rel_borderline,
				       (SELECT count(*) FROM snapshot_membership m WHERE m.snapshot_id = s.id AND m.relevance_tier = 'INELIGIBLE') AS rel_ineligible,
				       (SELECT count(*) FROM snapshot_membership m WHERE m.snapshot_id = s.id AND m.eligibility_status = 'ELIGIBLE'
				            AND m.canonical_publication_id = m.publication_id) AS optimization_candidates,
				       (SELECT count(DISTINCT (ic.publication_a_id, ic.publication_b_id)) FROM snapshot_identity_decision d
				            JOIN publication_identity_candidate ic ON ic.id = d.pair_id
				            WHERE d.snapshot_id = s.id AND d.decision = 'SAME_WORK') AS same_work_pairs,
				       (SELECT count(DISTINCT (ic.publication_a_id, ic.publication_b_id)) FROM snapshot_identity_decision d
				            JOIN publication_identity_candidate ic ON ic.id = d.pair_id
				            WHERE d.snapshot_id = s.id AND d.decision = 'PROBABLE_VERSION') AS probable_version_pairs,
				       (SELECT count(DISTINCT (ic.publication_a_id, ic.publication_b_id)) FROM snapshot_identity_decision d
				            JOIN publication_identity_candidate ic ON ic.id = d.pair_id
				            WHERE d.snapshot_id = s.id AND d.decision = 'DISTINCT') AS distinct_pairs,
				       (SELECT count(DISTINCT (ic.publication_a_id, ic.publication_b_id)) FROM snapshot_identity_decision d
				            JOIN publication_identity_candidate ic ON ic.id = d.pair_id
				            WHERE d.snapshot_id = s.id AND d.decision = 'UNRESOLVED') AS unresolved_pairs,
				       (SELECT count(*) FROM snapshot_membership m WHERE m.snapshot_id = s.id AND m.canonical_publication_id = m.publication_id) AS canonical_members,
				       (SELECT count(*) FROM snapshot_membership m WHERE m.snapshot_id = s.id AND m.canonical_publication_id <> m.publication_id) AS suppressed_members,
				       (SELECT count(*) FILTER (WHERE has_abstract) FROM members)          AS with_abstract,
				       (SELECT count(*) FILTER (WHERE has_topics) FROM members)            AS with_topics,
				       (SELECT count(*) FILTER (WHERE has_keywords) FROM members)          AS with_keywords,
				       (SELECT count(*) FILTER (WHERE has_references) FROM members)        AS with_references,
				       (SELECT count(*) FILTER (WHERE doi IS NOT NULL) FROM members)       AS with_doi,
				       (SELECT count(*) FILTER (WHERE publication_year IS NOT NULL) FROM members) AS with_year,
				       (SELECT count(*) FILTER (WHERE author_count > 0) FROM members)      AS with_authors,
				       (SELECT count(*) FILTER (WHERE references_supplied) FROM members)   AS with_reference_list,
				       (SELECT count(*) FILTER (WHERE abstract_truncated) FROM members)    AS abstracts_truncated,
				       (SELECT count(DISTINCT pc.concept_id) FROM members p
				            JOIN publication_concept pc ON pc.publication_id = p.id
				            JOIN concept c ON c.id = pc.concept_id AND c.kind = 'TOPIC')   AS distinct_topics,
				       (SELECT count(DISTINCT pc.concept_id) FROM members p
				            JOIN publication_concept pc ON pc.publication_id = p.id
				            JOIN concept c ON c.id = pc.concept_id AND c.kind = 'KEYWORD') AS distinct_keywords,
				       (SELECT display_name FROM primary_topics)                          AS top_primary_topic,
				       COALESCE((SELECT n FROM primary_topics), 0)                         AS top_primary_topic_count,
				       (SELECT count(*) FROM members p
				            JOIN publication_reference r ON r.citing_publication_id = p.id
				            JOIN members cited ON cited.openalex_id = r.referenced_openalex_id) AS internal_edges,
				       (SELECT count(DISTINCT (ic.publication_a_id, ic.publication_b_id))
				            FROM publication_identity_candidate ic
				            WHERE ic.publication_a_id IN (SELECT id FROM members)
				              AND ic.publication_b_id IN (SELECT id FROM members))         AS duplicate_pairs
				FROM candidate_snapshot s
				WHERE s.id = :id
				""")
				.param("id", id)
				.query((rs, n) -> new SnapshotSummary(
						rs.getObject("id", UUID.class),
						rs.getString("topic"),
						rs.getTimestamp("created_at").toInstant(),
						rs.getString("provider"),
						rs.getInt("requested_count"),
						(Long) rs.getObject("provider_total_count"),
						rs.getInt("received_count"),
						rs.getInt("stored_count"),
						rs.getInt("rejected_count"),
						rs.getInt("normalizer_version"),
						new Availability(rs.getInt("with_abstract"), rs.getInt("with_topics"),
								rs.getInt("with_keywords"), rs.getInt("with_references"), rs.getInt("with_doi"),
								rs.getInt("with_year"), rs.getInt("with_authors"), withSourceLink,
								rs.getInt("with_reference_list"), rs.getInt("abstracts_truncated")),
						new Classification(rs.getInt("distinct_topics"), rs.getInt("distinct_keywords"),
								rs.getString("top_primary_topic"), rs.getInt("top_primary_topic_count")),
						rs.getLong("internal_edges"),
						rs.getLong("duplicate_pairs"),
						rs.getObject("identity_policy_version") == null ? null
								: new SnapshotSummary.Identity(rs.getInt("identity_policy_version"),
										rs.getInt("same_work_pairs"), rs.getInt("probable_version_pairs"),
										rs.getInt("distinct_pairs"), rs.getInt("unresolved_pairs"),
										rs.getInt("canonical_members"), rs.getInt("suppressed_members")),
						rs.getObject("relevance_policy_version") == null ? null
								: new SnapshotSummary.Relevance(rs.getInt("relevance_policy_version"),
										rs.getInt("rel_eligible"), rs.getInt("rel_tangential"),
										rs.getInt("rel_borderline"), rs.getInt("rel_ineligible"),
										rs.getInt("optimization_candidates"),
										jsonMapper.readTree(rs.getString("warnings"))),
						jsonMapper.readTree(rs.getString("notes"))))
				.optional();
	}

	/** Applies the same preference rule as {@code NormalizedWork.preferredSourceLink()}. */
	private int countMembersWithSourceLink(UUID snapshotId) {
		return (int) jdbc.sql("""
				SELECT p.oa_url, p.doi, p.landing_page_url
				FROM snapshot_membership m JOIN publication p ON p.id = m.publication_id
				WHERE m.snapshot_id = :id
				""")
				.param("id", snapshotId)
				.query((rs, n) -> rs.getString(1) != null || UrlSafety.doiUrl(rs.getString(2)) != null
						|| rs.getString(3) != null)
				.list()
				.stream()
				.filter(Boolean::booleanValue)
				.count();
	}

}
