package dev.researchpath.publication;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import dev.researchpath.publication.NormalizationResult.Accepted;
import dev.researchpath.publication.NormalizationResult.Rejected;
import dev.researchpath.publication.NormalizationResult.RejectionReason;
import dev.researchpath.publication.NormalizedWork.ConceptKind;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Expected values were extracted independently (Python) from the real OpenAlex fixture. */
class OpenAlexWorkNormalizerTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private final OpenAlexWorkNormalizer normalizer = new OpenAlexWorkNormalizer();

	private static JsonNode fixtureResult(int index) throws IOException {
		try (InputStream in = OpenAlexWorkNormalizerTest.class
				.getResourceAsStream("/openalex/search-graph-neural-networks-page1.json")) {
			return JSON.readTree(in).path("results").get(index);
		}
	}

	private NormalizedWork accepted(JsonNode node) {
		NormalizationResult result = normalizer.normalize(node);
		assertThat(result).isInstanceOf(Accepted.class);
		return ((Accepted) result).work();
	}

	@Test
	void realReviewWithoutAbstractKeepsEverythingElse() throws IOException {
		NormalizedWork work = accepted(fixtureResult(0));

		assertThat(work.openalexId()).isEqualTo("W4392553889");
		assertThat(work.doi()).isEqualTo("10.1038/s43586-024-00294-7");
		assertThat(work.title()).isEqualTo("Graph neural networks");
		assertThat(work.normalizedTitle()).isEqualTo("graph neural networks");
		assertThat(work.publicationYear()).isEqualTo(2024);
		assertThat(work.publicationDate()).isEqualTo(LocalDate.of(2024, 3, 7));
		assertThat(work.workType()).isEqualTo("review");
		assertThat(work.sourceName()).isEqualTo("Nature Reviews Methods Primers");
		assertThat(work.isOa()).isFalse();
		assertThat(work.oaUrl()).isNull();
		assertThat(work.citedByCount()).isEqualTo(459);
		assertThat(work.fwci()).isEqualTo(19.904);
		assertThat(work.citationPercentile()).isEqualTo(0.9988081);
		assertThat(work.providerUpdatedAt()).isEqualTo(Instant.parse("2026-10-17T09:40:24.721904Z"));

		// Missing abstract is a normal case, not a rejection (blueprint §4.2).
		assertThat(work.abstractText()).isNull();
		assertThat(work.hasAbstract()).isFalse();

		assertThat(work.authors()).hasSize(5);
		assertThat(work.authors().get(0).displayName()).isEqualTo("Gabriele Corso");
		assertThat(work.authors().get(0).authorPosition()).isEqualTo("first");
		assertThat(work.authors().get(0).order()).isEqualTo(1);

		assertThat(work.concepts()).filteredOn(c -> c.kind() == ConceptKind.TOPIC).hasSize(3);
		assertThat(work.concepts()).filteredOn(c -> c.kind() == ConceptKind.KEYWORD).hasSize(9);
		assertThat(work.concepts()).filteredOn(NormalizedWork.ConceptAssociation::primary)
				.singleElement()
				.satisfies(primary -> {
					assertThat(primary.openalexId()).isEqualTo("T11273");
					assertThat(primary.score()).isEqualTo(0.9911999702453613);
					assertThat(primary.subfield()).isEqualTo("Artificial Intelligence");
				});
		assertThat(work.concepts()).filteredOn(c -> c.kind() == ConceptKind.KEYWORD).first()
				.satisfies(k -> {
					assertThat(k.openalexId()).isEqualTo("keywords/graph-neural-networks");
					assertThat(k.score()).isEqualTo(1.0);
				});

		assertThat(work.referencedWorkIds()).hasSize(76).allMatch(id -> id.matches("W\\d+"));
		assertThat(work.referencedWorksCount()).isEqualTo(76);
	}

	@Test
	void realArticleAbstractIsRebuiltInWordOrder() throws IOException {
		NormalizedWork work = accepted(fixtureResult(1));

		assertThat(work.openalexId()).isEqualTo("W2116341502");
		assertThat(work.abstractText()).startsWith(
				"Many underlying relationships among data in several areas of science and engineering, e.g.");
		assertThat(work.abstractText().split(" ")).hasSize(149);
		assertThat(work.isOa()).isTrue();
		assertThat(work.oaUrl()).contains("figshare.com");
		assertThat(work.referencedWorkIds()).hasSize(111);
	}

	@Test
	void realPreprintWithoutCitationMetricsKeepsThemUnknown() throws IOException {
		NormalizedWork work = accepted(fixtureResult(2));

		assertThat(work.workType()).isEqualTo("preprint");
		assertThat(work.doi()).isEqualTo("10.48550/arxiv.2003.00982");
		assertThat(work.fwci()).isNull();
		assertThat(work.citationPercentile()).isNull();
		assertThat(work.citedByCount()).isEqualTo(573);
	}

	@Test
	void abstractReconstructionHandlesRepeatedWords() {
		JsonNode index = JSON.readTree("""
				{"the": [0, 3], "cat": [1], "saw": [2], "dog": [4]}""");
		assertThat(OpenAlexWorkNormalizer.abstractText(index)).isEqualTo("the cat saw the dog");
		assertThat(OpenAlexWorkNormalizer.abstractText(JSON.readTree("{}"))).isNull();
		assertThat(OpenAlexWorkNormalizer.abstractText(JSON.readTree("null"))).isNull();
	}

	@Test
	void sparseRecordIsAcceptedWithUnknownsAndFalseFlags() {
		NormalizedWork work = accepted(JSON.readTree("""
				{"id": "https://openalex.org/W1", "display_name": "  A sparse record  ",
				 "doi": null, "publication_year": null, "publication_date": "not-a-date",
				 "abstract_inverted_index": null, "topics": [], "keywords": null,
				 "authorships": [{"author": {"id": null, "display_name": null}}],
				 "referenced_works": ["https://openalex.org/W9", "https://openalex.org/W9", "garbage"],
				 "open_access": {"is_oa": null}, "updated_date": "2026-10-01"}"""));

		assertThat(work.title()).isEqualTo("A sparse record");
		assertThat(work.doi()).isNull();
		assertThat(work.publicationYear()).isNull();
		assertThat(work.publicationDate()).isNull();
		assertThat(work.isOa()).isNull();
		assertThat(work.authors()).isEmpty(); // no invented author names
		assertThat(work.hasTopics()).isFalse();
		assertThat(work.hasKeywords()).isFalse();
		assertThat(work.hasAbstract()).isFalse();
		assertThat(work.referencedWorkIds()).containsExactly("W9"); // deduplicated, invalid dropped
		assertThat(work.providerUpdatedAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
	}

	@Test
	void doiFallsBackToIdsObject() {
		NormalizedWork work = accepted(JSON.readTree("""
				{"id": "https://openalex.org/W2", "title": "T",
				 "ids": {"doi": "https://doi.org/10.1000/ABC"}}"""));
		assertThat(work.doi()).isEqualTo("10.1000/abc");
	}

	@Test
	void recordWithoutValidIdIsRejected() {
		assertThat(normalizer.normalize(JSON.readTree("""
				{"id": "https://openalex.org/A123", "display_name": "Author, not a work"}""")))
				.isEqualTo(new Rejected("https://openalex.org/A123", RejectionReason.MISSING_OR_INVALID_ID));
		assertThat(normalizer.normalize(JSON.readTree("{\"display_name\": \"No id\"}")))
				.isEqualTo(new Rejected(null, RejectionReason.MISSING_OR_INVALID_ID));
	}

	@Test
	void recordWithoutTitleIsRejected() {
		assertThat(normalizer.normalize(JSON.readTree("""
				{"id": "https://openalex.org/W3", "display_name": "   ", "title": null}""")))
				.isEqualTo(new Rejected("W3", RejectionReason.MISSING_TITLE));
	}

}
