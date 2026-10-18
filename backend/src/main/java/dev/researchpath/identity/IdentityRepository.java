package dev.researchpath.identity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

	/** Frozen decisions and suppressed members for a snapshot; empty if the snapshot does not exist. */
	public Optional<IdentityDiagnostics> diagnostics(UUID snapshotId) {
		// Wrapped so "snapshot exists, version NULL (pre-M05)" is not confused with "no such snapshot".
		record Version(Integer value) {
		}
		Optional<Version> version = jdbc.sql("SELECT identity_policy_version FROM candidate_snapshot WHERE id = :id")
				.param("id", snapshotId)
				.query((rs, n) -> new Version((Integer) rs.getObject(1)))
				.optional();
		if (version.isEmpty()) {
			return Optional.empty();
		}
		List<IdentityDiagnostics.Decision> decisions = jdbc.sql("""
				SELECT d.pair_id, ic.match_type, d.decision, d.rule, d.details::text AS details,
				       a.openalex_id a_id, a.title a_title, a.publication_year a_year, a.work_type a_type, a.doi a_doi,
				       b.openalex_id b_id, b.title b_title, b.publication_year b_year, b.work_type b_type, b.doi b_doi
				FROM snapshot_identity_decision d
				JOIN publication_identity_candidate ic ON ic.id = d.pair_id
				JOIN publication a ON a.id = ic.publication_a_id
				JOIN publication b ON b.id = ic.publication_b_id
				WHERE d.snapshot_id = :id
				ORDER BY CASE d.decision WHEN 'SAME_WORK' THEN 0 WHEN 'PROBABLE_VERSION' THEN 1
				         WHEN 'UNRESOLVED' THEN 2 ELSE 3 END, d.pair_id
				""")
				.param("id", snapshotId)
				.query((rs, n) -> new IdentityDiagnostics.Decision(rs.getLong("pair_id"), rs.getString("match_type"),
						rs.getString("decision"), rs.getString("rule"), jsonMapper.readTree(rs.getString("details")),
						new IdentityDiagnostics.Work(rs.getString("a_id"), rs.getString("a_title"),
								(Integer) rs.getObject("a_year"), rs.getString("a_type"), rs.getString("a_doi")),
						new IdentityDiagnostics.Work(rs.getString("b_id"), rs.getString("b_title"),
								(Integer) rs.getObject("b_year"), rs.getString("b_type"), rs.getString("b_doi"))))
				.list();
		List<IdentityDiagnostics.Suppressed> suppressed = jdbc.sql("""
				SELECT p.openalex_id, p.title, c.openalex_id AS c_id, c.title AS c_title
				FROM snapshot_membership m
				JOIN publication p ON p.id = m.publication_id
				JOIN publication c ON c.id = m.canonical_publication_id
				WHERE m.snapshot_id = :id AND m.canonical_publication_id <> m.publication_id
				ORDER BY m.provider_rank
				""")
				.param("id", snapshotId)
				.query((rs, n) -> new IdentityDiagnostics.Suppressed(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getString(4)))
				.list();
		return Optional.of(new IdentityDiagnostics(snapshotId, version.get().value(), decisions, suppressed));
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
