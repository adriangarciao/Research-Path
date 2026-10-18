package dev.researchpath.publication;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import dev.researchpath.TestcontainersConfiguration;
import dev.researchpath.publication.NormalizationResult.Accepted;
import dev.researchpath.publication.NormalizedWork.ConceptKind;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** M02 roundtrip: real-format records must survive the database with their distinctions intact. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PublicationRepositoryTests {

	private static final Instant T1 = Instant.parse("2026-10-17T12:00:00Z");
	private static final Instant T2 = Instant.parse("2026-10-18T12:00:00Z");
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	PublicationRepository repository;

	@Autowired
	TransactionTemplate tx;

	@Autowired
	JdbcClient jdbc;

	private final OpenAlexWorkNormalizer normalizer = new OpenAlexWorkNormalizer();

	@BeforeEach
	void clean() {
		jdbc.sql("TRUNCATE candidate_snapshot, publication, concept RESTART IDENTITY CASCADE").update();
	}

	private List<NormalizedWork> realFixtureWorks() throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/openalex/search-graph-neural-networks-page1.json")) {
			List<NormalizedWork> works = new ArrayList<>();
			for (JsonNode node : JSON.readTree(in).path("results")) {
				works.add(((Accepted) normalizer.normalize(node)).work());
			}
			return works;
		}
	}

	private NormalizedWork normalize(String json) {
		return ((Accepted) normalizer.normalize(JSON.readTree(json))).work();
	}

	private long save(NormalizedWork work, Instant at) {
		return tx.execute(status -> repository.upsert(work, at));
	}

	private static void assertSameContent(NormalizedWork actual, NormalizedWork expected) {
		assertThat(actual).usingRecursiveComparison().ignoringFields("concepts", "referencedWorkIds")
				.isEqualTo(expected);
		assertThat(actual.concepts()).containsExactlyInAnyOrderElementsOf(expected.concepts());
		assertThat(actual.referencedWorkIds()).containsExactlyInAnyOrderElementsOf(expected.referencedWorkIds());
	}

	@Test
	void realRecordsRoundTripWithAllDistinctionsIntact() throws Exception {
		for (NormalizedWork work : realFixtureWorks()) {
			save(work, T1);
			NormalizedWork read = repository.findByOpenalexId(work.openalexId()).orElseThrow();
			assertSameContent(read, work);
		}
	}

	@Test
	void missingAbstractStaysMissingNotEmpty() throws Exception {
		NormalizedWork review = realFixtureWorks().get(0); // W4392553889: no abstract in OpenAlex
		save(review, T1);

		assertThat(repository.findByOpenalexId("W4392553889").orElseThrow().abstractText()).isNull();
		assertThat(jdbc.sql("SELECT has_abstract FROM publication WHERE openalex_id = 'W4392553889'")
				.query(Boolean.class).single()).isFalse();
	}

	@Test
	void topicsAndKeywordsStayDistinctWithExactConfidence() throws Exception {
		NormalizedWork review = realFixtureWorks().get(0);
		save(review, T1);
		NormalizedWork read = repository.findByOpenalexId(review.openalexId()).orElseThrow();

		assertThat(read.concepts()).filteredOn(c -> c.kind() == ConceptKind.TOPIC).hasSize(3);
		assertThat(read.concepts()).filteredOn(c -> c.kind() == ConceptKind.KEYWORD).hasSize(9);
		// Topics come first and keep provider scores bit-for-bit, including tiny ones.
		assertThat(read.concepts().get(0).openalexId()).isEqualTo("T11273");
		assertThat(read.concepts().get(0).score()).isEqualTo(0.9911999702453613);
		assertThat(read.concepts().get(0).primary()).isTrue();
		assertThat(read.concepts().get(2).score()).isLessThan(0.001);
	}

	@Test
	void sameIdentifierCanBeATopicAndAKeywordWithoutCollision() {
		NormalizedWork w = normalize("""
				{"id": "https://openalex.org/W1", "display_name": "x",
				 "topics": [{"id": "https://openalex.org/same", "display_name": "Topic label", "score": 0.5}],
				 "keywords": [{"id": "https://openalex.org/same", "display_name": "keyword label", "score": 0.4}]}""");
		save(w, T1);

		assertThat(repository.findByOpenalexId("W1").orElseThrow().concepts())
				.extracting(c -> c.kind() + ":" + c.displayName())
				.containsExactly("TOPIC:Topic label", "KEYWORD:keyword label");
	}

	@Test
	void referencesToWorksNotInTheCacheAreStoredAsIds() {
		NormalizedWork w = normalize("""
				{"id": "https://openalex.org/W1", "display_name": "cites unknown works",
				 "referenced_works": ["https://openalex.org/W999", "https://openalex.org/W5"]}""");
		save(w, T1);

		assertThat(repository.findByOpenalexId("W1").orElseThrow().referencedWorkIds()).containsExactly("W5", "W999");
		assertThat(jdbc.sql("SELECT count(*) FROM publication WHERE openalex_id IN ('W5', 'W999')")
				.query(Long.class).single()).isZero(); // no fabricated placeholder publications
	}

	@Test
	void upsertRefreshesInPlaceWithoutStaleChildren() {
		save(normalize("""
				{"id": "https://openalex.org/W1", "display_name": "Old", "publication_year": 2019,
				 "authorships": [{"author": {"display_name": "A"}}, {"author": {"display_name": "B"}}],
				 "keywords": [{"id": "https://openalex.org/keywords/k1", "display_name": "k1", "score": 0.9},
				              {"id": "https://openalex.org/keywords/k2", "display_name": "k2", "score": 0.8}],
				 "referenced_works": ["https://openalex.org/W7"]}"""), T1);
		long firstId = jdbc.sql("SELECT id FROM publication WHERE openalex_id = 'W1'").query(Long.class).single();

		NormalizedWork refreshed = normalize("""
				{"id": "https://openalex.org/W1", "display_name": "New", "publication_year": 2019,
				 "authorships": [{"author": {"display_name": "A"}}],
				 "keywords": [{"id": "https://openalex.org/keywords/k2", "display_name": "k2", "score": 0.7}],
				 "referenced_works": []}""");
		long secondId = save(refreshed, T2);

		assertThat(secondId).isEqualTo(firstId);
		assertSameContent(repository.findByOpenalexId("W1").orElseThrow(), refreshed);
		assertThat(jdbc.sql("SELECT count(*) FROM publication").query(Long.class).single()).isEqualTo(1);
		assertThat(jdbc.sql("""
				SELECT first_retrieved_at < last_retrieved_at FROM publication WHERE openalex_id = 'W1'
				""").query(Boolean.class).single()).isTrue();
	}

	@Test
	void sameDoiOnDifferentWorksIsKeptAndFlaggedNotRejected() {
		save(normalize("{\"id\": \"https://openalex.org/W1\", \"title\": \"Preprint\", \"doi\": \"10.1/x\"}"), T1);
		save(normalize("{\"id\": \"https://openalex.org/W2\", \"title\": \"Journal\", \"doi\": \"10.1/X\"}"), T1);

		assertThat(repository.findByOpenalexId("W1")).isPresent();
		assertThat(repository.findByOpenalexId("W2")).isPresent();
		assertThat(jdbc.sql("SELECT match_type FROM publication_identity_candidate").query(String.class).list())
				.containsExactly("SAME_DOI");
	}

	@Test
	void unicodeTitlesRoundTripExactly() {
		NormalizedWork w = normalize("""
				{"id": "https://openalex.org/W1", "display_name": "Réseaux de neurones sur graphes — 图神经网络 🧠"}""");
		save(w, T1);

		NormalizedWork read = repository.findByOpenalexId("W1").orElseThrow();
		assertThat(read.title()).isEqualTo("Réseaux de neurones sur graphes — 图神经网络 🧠");
		assertThat(read.normalizedTitle()).isEqualTo(w.normalizedTitle());
	}

	@Test
	void unknownWorkIsEmpty() {
		assertThat(repository.findByOpenalexId("W404")).isEmpty();
	}

}
