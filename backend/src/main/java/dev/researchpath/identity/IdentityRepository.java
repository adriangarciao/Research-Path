package dev.researchpath.identity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.researchpath.identity.IdentityPolicy.CandidatePair;
import dev.researchpath.identity.IdentityPolicy.PairDecision;
import dev.researchpath.identity.IdentityPolicy.Resolution;
import dev.researchpath.publication.UrlSafety;
import tools.jackson.databind.json.JsonMapper;

/** Loads identity evidence for a snapshot's members and stores the frozen resolution. */
@Repository
public class IdentityRepository {

	private final JdbcClient jdbc;
	private final JsonMapper jsonMapper;

	public IdentityRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
	}

	public List<IdentityEvidence> loadMemberEvidence(UUID snapshotId) {
		Map<Long, Set<String>> authorIds = new HashMap<>();
		Map<Long, Set<String>> nameKeys = new HashMap<>();
		jdbc.sql("""
				SELECT a.publication_id, a.author_openalex_id, a.display_name
				FROM publication_author a
				JOIN snapshot_membership m ON m.publication_id = a.publication_id AND m.snapshot_id = :id
				""")
				.param("id", snapshotId)
				.query(rs -> {
					long pid = rs.getLong(1);
					String authorId = rs.getString(2);
					if (authorId != null) {
						authorIds.computeIfAbsent(pid, k -> new HashSet<>()).add(authorId);
					}
					String key = IdentityPolicy.nameKey(rs.getString(3));
					if (key != null) {
						nameKeys.computeIfAbsent(pid, k -> new HashSet<>()).add(key);
					}
				});

		return jdbc.sql("""
				SELECT p.id, p.openalex_id, p.doi, p.normalized_title, p.publication_year, p.work_type,
				       p.author_count, p.oa_url, p.landing_page_url, p.has_abstract,
				       (p.has_abstract::int + p.has_topics::int + p.has_keywords::int
				        + COALESCE(p.references_supplied, false)::int + (p.author_count > 0)::int) AS completeness
				FROM snapshot_membership m JOIN publication p ON p.id = m.publication_id
				WHERE m.snapshot_id = :id
				""")
				.param("id", snapshotId)
				.query((rs, n) -> {
					long pid = rs.getLong("id");
					String doi = rs.getString("doi");
					boolean oa = rs.getString("oa_url") != null;
					boolean link = oa || UrlSafety.doiUrl(doi) != null || rs.getString("landing_page_url") != null;
					return new IdentityEvidence(pid, rs.getString("openalex_id"), doi, rs.getString("normalized_title"),
							(Integer) rs.getObject("publication_year"), rs.getString("work_type"),
							authorIds.getOrDefault(pid, Set.of()), nameKeys.getOrDefault(pid, Set.of()),
							rs.getInt("author_count"), oa, link, rs.getBoolean("has_abstract"), rs.getInt("completeness"));
				})
				.list();
	}

	/** Flagged pairs whose two works are both members of the snapshot. */
	public List<CandidatePair> loadPairsAmongMembers(UUID snapshotId) {
		return jdbc.sql("""
				SELECT ic.id, ic.publication_a_id, ic.publication_b_id
				FROM publication_identity_candidate ic
				JOIN snapshot_membership ma ON ma.publication_id = ic.publication_a_id AND ma.snapshot_id = :id
				JOIN snapshot_membership mb ON mb.publication_id = ic.publication_b_id AND mb.snapshot_id = :id
				ORDER BY ic.id
				""")
				.param("id", snapshotId)
				.query((rs, n) -> new CandidatePair(rs.getLong(1), rs.getLong(2), rs.getLong(3)))
				.list();
	}

	/** Persists the frozen decisions and canonical mapping. Must run inside the snapshot transaction. */
	public void saveResolution(UUID snapshotId, Resolution resolution, int policyVersion) {
		for (Map.Entry<Long, PairDecision> e : resolution.decisionsByPairId().entrySet()) {
			jdbc.sql("""
					INSERT INTO snapshot_identity_decision (snapshot_id, pair_id, decision, rule, details)
					VALUES (:sid, :pair, :decision, :rule, CAST(:details AS jsonb))
					""")
					.param("sid", snapshotId)
					.param("pair", e.getKey())
					.param("decision", e.getValue().decision().name())
					.param("rule", e.getValue().rule())
					.param("details", jsonMapper.writeValueAsString(e.getValue().details()))
					.update();
		}
		for (Map.Entry<Long, Long> e : resolution.canonicalByPublicationId().entrySet()) {
			jdbc.sql("""
					UPDATE snapshot_membership SET canonical_publication_id = :canonical
					WHERE snapshot_id = :sid AND publication_id = :pid
					""")
					.param("canonical", e.getValue())
					.param("sid", snapshotId)
					.param("pid", e.getKey())
					.update();
		}
		jdbc.sql("UPDATE candidate_snapshot SET identity_policy_version = :v WHERE id = :sid")
				.param("v", policyVersion)
				.param("sid", snapshotId)
				.update();
	}

}
