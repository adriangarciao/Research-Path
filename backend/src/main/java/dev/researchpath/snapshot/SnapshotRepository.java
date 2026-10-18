package dev.researchpath.snapshot;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.researchpath.publication.UrlSafety;
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
			String sourceRecordJson, Instant retrievedAt) {
		jdbc.sql("""
				INSERT INTO snapshot_membership (snapshot_id, publication_id, provider_rank,
				    provider_relevance_score, source_record, retrieved_at)
				VALUES (:sid, :pid, :rank, :score, CAST(:source AS jsonb), :retrievedAt)
				""")
				.param("sid", snapshotId)
				.param("pid", publicationId)
				.param("rank", providerRank)
				.param("score", relevanceScore)
				.param("source", sourceRecordJson)
				.param("retrievedAt", Timestamp.from(retrievedAt))
				.update();
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
