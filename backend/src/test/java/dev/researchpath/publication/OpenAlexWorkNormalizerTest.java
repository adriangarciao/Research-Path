package dev.researchpath.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

	// ---- M04 bounds and edge cases ----

	private static String authorships(int n) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 1; i <= n; i++) {
			sb.append(i > 1 ? "," : "").append("{\"author\": {\"display_name\": \"Author ").append(i).append("\"}}");
		}
		return sb.append("]").toString();
	}

	@Test
	void longAuthorListIsCappedButTotalCountIsKept() {
		NormalizedWork work = accepted(JSON.readTree("""
				{"id": "https://openalex.org/W1", "display_name": "Big collaboration", "authorships": %s}"""
				.formatted(authorships(150))));

		assertThat(work.authors()).hasSize(OpenAlexWorkNormalizer.MAX_STORED_AUTHORS);
		assertThat(work.authors().get(99).displayName()).isEqualTo("Author 100");
		assertThat(work.authorCount()).isEqualTo(150);
	}

	@Test
	void overlongAbstractIsTruncatedAtAWordAndFlagged() {
		StringBuilder index = new StringBuilder("{");
		int words = 4_000; // "word0000 " ... ~36,000 characters
		for (int i = 0; i < words; i++) {
			index.append(i > 0 ? "," : "").append("\"w").append(String.format("%07d", i)).append("\": [").append(i).append("]");
		}
		NormalizedWork work = accepted(JSON.readTree("""
				{"id": "https://openalex.org/W1", "display_name": "t", "abstract_inverted_index": %s}"""
				.formatted(index.append("}"))));

		assertThat(work.abstractTruncated()).isTrue();
		assertThat(work.abstractText().length()).isLessThanOrEqualTo(OpenAlexWorkNormalizer.MAX_ABSTRACT_CHARS);
		assertThat(work.abstractText()).startsWith("w0000000 w0000001").doesNotEndWith(" ");
		assertThat(work.abstractText().split(" ")).allMatch(word -> word.length() == 8); // no word cut in half
	}

	@Test
	void shortAbstractIsNotFlagged() throws IOException {
		NormalizedWork work = accepted(fixtureResult(1));
		assertThat(work.abstractTruncated()).isFalse();
		assertThat(work.authorCount()).isEqualTo(5);
	}

	@Test
	void truncateAtWordFallsBackToHardCutWithoutSpaces() {
		assertThat(OpenAlexWorkNormalizer.truncateAtWord("abcdefghij", 4)).isEqualTo("abcd");
		assertThat(OpenAlexWorkNormalizer.truncateAtWord("ab cd ef", 6)).isEqualTo("ab cd");
		assertThat(OpenAlexWorkNormalizer.truncateAtWord("short", 10)).isEqualTo("short");
	}

	@Test
	void overlongTitleAndOversizedRecordAreRejected() {
		assertThat(normalizer.normalize(JSON.readTree("""
				{"id": "https://openalex.org/W1", "display_name": "%s"}""".formatted("x".repeat(1_001)))))
				.isEqualTo(new Rejected("W1", RejectionReason.TITLE_TOO_LONG));
		assertThat(normalizer.normalize(JSON.readTree("""
				{"id": "https://openalex.org/W2", "display_name": "%s"}""".formatted("x".repeat(1_000)))))
				.isInstanceOf(Accepted.class);
		assertThat(normalizer.normalize(JSON.readTree("""
				{"id": "https://openalex.org/W3", "display_name": "t", "padding": "%s"}"""
				.formatted("x".repeat(OpenAlexWorkNormalizer.MAX_RECORD_BYTES)))))
				.isEqualTo(new Rejected("W3", RejectionReason.RECORD_TOO_LARGE));
	}

	@Test
	void confidenceOutsideZeroToOneIsUnknownButBoundariesAreKept() {
		NormalizedWork work = accepted(JSON.readTree("""
				{"id": "https://openalex.org/W1", "display_name": "t", "keywords": [
				  {"id": "https://openalex.org/keywords/zero", "display_name": "zero", "score": 0.0},
				  {"id": "https://openalex.org/keywords/one", "display_name": "one", "score": 1},
				  {"id": "https://openalex.org/keywords/neg", "display_name": "neg", "score": -0.1},
				  {"id": "https://openalex.org/keywords/big", "display_name": "big", "score": 1.5},
				  {"id": "https://openalex.org/keywords/str", "display_name": "str", "score": "0.5"},
				  {"id": "https://openalex.org/keywords/none", "display_name": "none"}]}"""));

		assertThat(work.concepts()).extracting(NormalizedWork.ConceptAssociation::displayName,
				NormalizedWork.ConceptAssociation::score)
				.containsExactly(tuple("zero", 0.0), tuple("one", 1.0), tuple("neg", null), tuple("big", null),
						tuple("str", null), tuple("none", null));
	}

	@Test
	void missingReferenceListDiffersFromEmptyList() {
		NormalizedWork empty = accepted(JSON.readTree("""
				{"id": "https://openalex.org/W1", "display_name": "t", "referenced_works": []}"""));
		NormalizedWork missing = accepted(JSON.readTree("""
				{"id": "https://openalex.org/W2", "display_name": "t"}"""));
		NormalizedWork nullList = accepted(JSON.readTree("""
				{"id": "https://openalex.org/W3", "display_name": "t", "referenced_works": null}"""));

		assertThat(empty.referencesSupplied()).isTrue();
		assertThat(empty.hasReferences()).isFalse();
		assertThat(missing.referencesSupplied()).isFalse();
		assertThat(nullList.referencesSupplied()).isFalse();
	}

	@Test
	void blankAbstractWordsYieldNoAbstract() {
		NormalizedWork work = accepted(JSON.readTree("""
				{"id": "https://openalex.org/W1", "display_name": "t", "abstract_inverted_index": {" ": [0], "": [1]}}"""));
		assertThat(work.abstractText()).isNull();
		assertThat(work.hasAbstract()).isFalse();
	}

	@Test
	void duplicateKeywordIdsCountOnceButDistinctIdsWithSameLabelAreKept() {
		NormalizedWork work = accepted(JSON.readTree("""
				{"id": "https://openalex.org/W1", "display_name": "t", "keywords": [
				  {"id": "https://openalex.org/keywords/gnn", "display_name": "GNN", "score": 0.9},
				  {"id": "https://openalex.org/keywords/gnn", "display_name": "GNN", "score": 0.8},
				  {"id": "https://openalex.org/keywords/gnn-2", "display_name": "GNN", "score": 0.7}]}"""));

		assertThat(work.concepts()).extracting(NormalizedWork.ConceptAssociation::openalexId)
				.containsExactly("keywords/gnn", "keywords/gnn-2"); // synonyms are not merged here (M07)
		assertThat(work.concepts().get(0).score()).isEqualTo(0.9); // first occurrence wins
	}

	@Test
	void unknownYearAndDateStayNull() {
		NormalizedWork work = accepted(JSON.readTree("""
				{"id": "https://openalex.org/W1", "display_name": "t", "publication_year": "2020", "publication_date": "2020-13-45"}"""));
		assertThat(work.publicationYear()).isNull(); // a string is not a trusted year
		assertThat(work.publicationDate()).isNull();
	}

	@Test
	void recordWithoutTitleIsRejected() {
		assertThat(normalizer.normalize(JSON.readTree("""
				{"id": "https://openalex.org/W3", "display_name": "   ", "title": null}""")))
				.isEqualTo(new Rejected("W3", RejectionReason.MISSING_TITLE));
	}

}
