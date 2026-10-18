package dev.researchpath.publication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.researchpath.publication.NormalizedWork.ConceptAssociation;

/**
 * Publication cache writes. Plain SQL is used deliberately: ingestion is upsert/batch heavy.
 * Callers must run inside a transaction.
 */
@Repository
public class PublicationRepository {

	private final JdbcClient jdbc;
	private final NamedParameterJdbcTemplate batch;

	public PublicationRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
		this.jdbc = jdbc;
		this.batch = batch;
	}

	/**
	 * Inserts or refreshes one publication (latest provider data wins), replaces its authors,
	 * concepts and references, and records possible duplicate/version evidence.
	 *
	 * @return the publication's database id
	 */
	public long upsert(NormalizedWork work, Instant retrievedAt) {
		long id = jdbc.sql("""
				INSERT INTO publication (openalex_id, doi, title, normalized_title, publication_year,
				    publication_date, work_type, language, source_name, landing_page_url, oa_url, is_oa,
				    abstract_text, cited_by_count, fwci, citation_percentile, referenced_works_count,
				    has_abstract, has_topics, has_keywords, has_references, provider_updated_at,
				    first_retrieved_at, last_retrieved_at)
				VALUES (:openalexId, :doi, :title, :normalizedTitle, :year, :date, :type, :language, :source,
				    :landing, :oaUrl, :isOa, :abstract, :citedBy, :fwci, :percentile, :refCount,
				    :hasAbstract, :hasTopics, :hasKeywords, :hasReferences, :providerUpdatedAt,
				    :retrievedAt, :retrievedAt)
				ON CONFLICT (openalex_id) DO UPDATE SET
				    doi = EXCLUDED.doi, title = EXCLUDED.title, normalized_title = EXCLUDED.normalized_title,
				    publication_year = EXCLUDED.publication_year, publication_date = EXCLUDED.publication_date,
				    work_type = EXCLUDED.work_type, language = EXCLUDED.language,
				    source_name = EXCLUDED.source_name, landing_page_url = EXCLUDED.landing_page_url,
				    oa_url = EXCLUDED.oa_url, is_oa = EXCLUDED.is_oa, abstract_text = EXCLUDED.abstract_text,
				    cited_by_count = EXCLUDED.cited_by_count, fwci = EXCLUDED.fwci,
				    citation_percentile = EXCLUDED.citation_percentile,
				    referenced_works_count = EXCLUDED.referenced_works_count,
				    has_abstract = EXCLUDED.has_abstract, has_topics = EXCLUDED.has_topics,
				    has_keywords = EXCLUDED.has_keywords, has_references = EXCLUDED.has_references,
				    provider_updated_at = EXCLUDED.provider_updated_at,
				    last_retrieved_at = EXCLUDED.last_retrieved_at
				RETURNING id
				""")
				.param("openalexId", work.openalexId())
				.param("doi", work.doi())
				.param("title", work.title())
				.param("normalizedTitle", work.normalizedTitle())
				.param("year", work.publicationYear())
				.param("date", work.publicationDate())
				.param("type", work.workType())
				.param("language", work.language())
				.param("source", work.sourceName())
				.param("landing", work.landingPageUrl())
				.param("oaUrl", work.oaUrl())
				.param("isOa", work.isOa())
				.param("abstract", work.abstractText())
				.param("citedBy", work.citedByCount())
				.param("fwci", work.fwci())
				.param("percentile", work.citationPercentile())
				.param("refCount", work.referencedWorksCount())
				.param("hasAbstract", work.hasAbstract())
				.param("hasTopics", work.hasTopics())
				.param("hasKeywords", work.hasKeywords())
				.param("hasReferences", work.hasReferences())
				.param("providerUpdatedAt", timestamp(work.providerUpdatedAt()))
				.param("retrievedAt", timestamp(retrievedAt))
				.query(Long.class)
				.single();

		replaceAuthors(id, work);
		replaceConcepts(id, work.concepts());
		replaceReferences(id, work.referencedWorkIds());
		recordIdentityCandidates(id);
		return id;
	}

	private void replaceAuthors(long publicationId, NormalizedWork work) {
		jdbc.sql("DELETE FROM publication_author WHERE publication_id = :id").param("id", publicationId).update();
		SqlParameterSource[] rows = work.authors().stream()
				.map(a -> new MapSqlParameterSource()
						.addValue("pid", publicationId)
						.addValue("order", a.order())
						.addValue("authorId", a.openalexId())
						.addValue("name", a.displayName())
						.addValue("position", a.authorPosition()))
				.toArray(SqlParameterSource[]::new);
		batch.batchUpdate("""
				INSERT INTO publication_author (publication_id, author_order, author_openalex_id, display_name, author_position)
				VALUES (:pid, :order, :authorId, :name, :position)
				""", rows);
	}

	private void replaceConcepts(long publicationId, List<ConceptAssociation> concepts) {
		jdbc.sql("DELETE FROM publication_concept WHERE publication_id = :id").param("id", publicationId).update();
		for (ConceptAssociation concept : concepts) {
			long conceptId = jdbc.sql("""
					INSERT INTO concept (kind, openalex_id, display_name, subfield, field, domain)
					VALUES (:kind, :openalexId, :name, :subfield, :field, :domain)
					ON CONFLICT (kind, openalex_id) DO UPDATE SET
					    display_name = EXCLUDED.display_name,
					    subfield = COALESCE(EXCLUDED.subfield, concept.subfield),
					    field = COALESCE(EXCLUDED.field, concept.field),
					    domain = COALESCE(EXCLUDED.domain, concept.domain)
					RETURNING id
					""")
					.param("kind", concept.kind().name())
					.param("openalexId", concept.openalexId())
					.param("name", concept.displayName())
					.param("subfield", concept.subfield())
					.param("field", concept.field())
					.param("domain", concept.domain())
					.query(Long.class)
					.single();
			jdbc.sql("""
					INSERT INTO publication_concept (publication_id, concept_id, score, is_primary)
					VALUES (:pid, :cid, :score, :primary)
					""")
					.param("pid", publicationId)
					.param("cid", conceptId)
					.param("score", concept.score())
					.param("primary", concept.primary())
					.update();
		}
	}

	private void replaceReferences(long publicationId, List<String> referencedIds) {
		jdbc.sql("DELETE FROM publication_reference WHERE citing_publication_id = :id")
				.param("id", publicationId)
				.update();
		SqlParameterSource[] rows = referencedIds.stream()
				.map(ref -> new MapSqlParameterSource().addValue("pid", publicationId).addValue("ref", ref))
				.toArray(SqlParameterSource[]::new);
		batch.batchUpdate("""
				INSERT INTO publication_reference (citing_publication_id, referenced_openalex_id)
				VALUES (:pid, :ref)
				""", rows);
	}

	/**
	 * Records possible duplicates/versions as evidence for later review. Never merges:
	 * the merge policy is an OPEN decision (D06), and equal titles alone are ambiguous.
	 */
	private void recordIdentityCandidates(long publicationId) {
		insertIdentityCandidates(publicationId, "SAME_DOI", "me.doi IS NOT NULL AND other.doi = me.doi");
		insertIdentityCandidates(publicationId, "SAME_NORMALIZED_TITLE",
				"me.normalized_title <> '' AND other.normalized_title = me.normalized_title");
	}

	private void insertIdentityCandidates(long publicationId, String matchType, String condition) {
		jdbc.sql("""
				INSERT INTO publication_identity_candidate (publication_a_id, publication_b_id, match_type, evidence)
				SELECT a.id, b.id, :matchType, jsonb_build_object(
				    'a', jsonb_build_object('openalex_id', a.openalex_id, 'doi', a.doi, 'title', a.title,
				                            'year', a.publication_year, 'type', a.work_type),
				    'b', jsonb_build_object('openalex_id', b.openalex_id, 'doi', b.doi, 'title', b.title,
				                            'year', b.publication_year, 'type', b.work_type))
				FROM publication me
				JOIN publication other ON other.id <> me.id AND %s
				JOIN publication a ON a.id = LEAST(me.id, other.id)
				JOIN publication b ON b.id = GREATEST(me.id, other.id)
				WHERE me.id = :id
				ON CONFLICT (publication_a_id, publication_b_id, match_type) DO NOTHING
				""".formatted(condition))
				.param("matchType", matchType)
				.param("id", publicationId)
				.update();
	}

	private static Timestamp timestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

}
