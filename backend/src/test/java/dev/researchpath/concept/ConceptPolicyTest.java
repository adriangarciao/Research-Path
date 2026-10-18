package dev.researchpath.concept;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.researchpath.concept.ConceptModel.Entry;
import dev.researchpath.concept.ConceptModel.Representation;
import dev.researchpath.publication.NormalizedWork.ConceptAssociation;
import dev.researchpath.publication.NormalizedWork.ConceptKind;

class ConceptPolicyTest {

	private static final String GNN = "graph neural networks";

	private static ConceptAssociation kw(String name, double score) {
		return new ConceptAssociation(ConceptKind.KEYWORD, "keywords/" + name.replace(' ', '-'), name, score, false,
				null, null, null);
	}

	private static ConceptAssociation topic(String name, double score) {
		return new ConceptAssociation(ConceptKind.TOPIC, "T" + Math.abs(name.hashCode()), name, score, false, null,
				null, null);
	}

	private static ConceptPolicy.Input paper(long id, ConceptAssociation... concepts) {
		return new ConceptPolicy.Input(id, "W" + id, List.of(concepts));
	}

	private static List<String> includedLabels(ConceptModel m) {
		return m.included().stream().map(Entry::label).toList();
	}

	private static String statusOf(ConceptModel m, String label) {
		return m.included().stream().filter(e -> e.label().equals(label)).map(Entry::status).findFirst()
				.orElseGet(() -> m.excluded().stream().filter(e -> e.label().equals(label)).map(Entry::status)
						.findFirst().orElse("ABSENT"));
	}

	@Test
	void irrelevantConceptsAreNotRewarded() {
		List<ConceptPolicy.Input> papers = List.of(
				paper(1, kw("graph neural networks", 1.0), kw("node classification", 0.9), kw("machine learning", 0.9),
						kw("AI ethics", 0.9)),
				paper(2, kw("GNNs", 1.0), kw("node classification", 0.8), kw("machine learning", 0.8), kw("AI ethics", 0.8)),
				paper(3, kw("graph neural networks", 0.9), kw("link prediction", 0.9)),
				paper(4, kw("link prediction", 0.7)));
		ConceptModel m = ConceptPolicy.build(GNN, papers, Representation.KEYWORD_ONLY);

		assertThat(includedLabels(m)).containsExactly("link prediction", "node classification");
		assertThat(statusOf(m, "graph neural networks")).isEqualTo("QUERY_OR_GENERIC"); // incl. merged "GNNs"
		assertThat(statusOf(m, "machine learning")).isEqualTo("QUERY_OR_GENERIC");
		assertThat(statusOf(m, "AI ethics")).isEqualTo("ASPECT_LABEL");
		assertThat(m.paperConcepts().get(1L)).containsOnlyKeys("KEYWORD:node classification");
	}

	@Test
	void acronymAndPluralVariantsMergeIntoOneConcept() {
		ConceptModel m = ConceptPolicy.build("molecular property prediction", List.of(
				paper(1, kw("Graph Neural Networks", 0.9)), paper(2, kw("GNN", 0.9)), paper(3, kw("graph neural network", 0.9)),
				paper(4, kw("chemistry", 0.9)), paper(5, kw("chemistry", 0.9)), paper(6, kw("chemistry", 0.9)),
				paper(7, kw("toxicity", 0.9))), Representation.KEYWORD_ONLY);

		Entry gnn = m.included().stream().filter(e -> e.key().equals("KEYWORD:graph neural network")).findFirst().orElseThrow();
		assertThat(gnn.support()).isEqualTo(3);
		assertThat(gnn.originalLabels()).containsExactly("GNN", "Graph Neural Networks", "graph neural network");
	}

	@Test
	void confidenceBoundaryIsInclusive() {
		ConceptModel m = ConceptPolicy.build(GNN, List.of(
				paper(1, kw("oversmoothing", 0.5)), paper(2, kw("oversmoothing", 0.5)),
				paper(3, kw("pooling", 0.49)), paper(4, kw("pooling", 0.49)), paper(5, kw("pooling", 0.49))),
				Representation.KEYWORD_ONLY);

		assertThat(includedLabels(m)).containsExactly("oversmoothing");
		assertThat(statusOf(m, "pooling")).isEqualTo("LOW_CONFIDENCE");
	}

	@Test
	void duplicateAssignmentsWithinOnePaperCountOnceAtMaxConfidence() {
		ConceptModel m = ConceptPolicy.build(GNN, List.of(
				paper(1, kw("link prediction", 0.6), kw("Link Prediction", 0.9)), paper(2, kw("link prediction", 0.7)),
				paper(3, kw("x", 0.9)), paper(4, kw("y", 0.9)), paper(5, kw("z", 0.9))),
				Representation.KEYWORD_ONLY);

		assertThat(m.included()).singleElement().satisfies(e -> assertThat(e.support()).isEqualTo(2));
		assertThat(m.paperConcepts().get(1L)).containsEntry("KEYWORD:link prediction", 0.9);
	}

	@Test
	void oneOffsAndUbiquitousConceptsAreExcluded() {
		List<ConceptPolicy.Input> papers = new ArrayList<>();
		for (long i = 1; i <= 10; i++) {
			papers.add(paper(i, kw("message passing", 0.9), kw(i <= 3 ? "pooling" : "unique-" + i, 0.9)));
		}
		ConceptModel m = ConceptPolicy.build(GNN, papers, Representation.KEYWORD_ONLY);

		assertThat(statusOf(m, "message passing")).isEqualTo("TOO_COMMON"); // on 10 of 10 candidates
		assertThat(statusOf(m, "unique-4")).isEqualTo("ONE_OFF");
		assertThat(includedLabels(m)).containsExactly("pooling");
	}

	@Test
	void topicOnlyFailsWhenEveryPaperSharesOneTopicButKeywordsStillDiscriminate() {
		List<ConceptPolicy.Input> papers = new ArrayList<>();
		String[] subtopics = { "robot navigation", "robotic manipulation", "sim-to-real transfer" };
		for (long i = 1; i <= 9; i++) {
			papers.add(paper(i, topic("Reinforcement Learning in Robotics", 0.99), kw(subtopics[(int) (i % 3)], 0.9)));
		}
		ConceptModel topics = ConceptPolicy.build("reinforcement learning in robotics", papers, Representation.TOPIC_ONLY);
		ConceptModel combined = ConceptPolicy.build("reinforcement learning in robotics", papers, Representation.COMBINED);

		assertThat(topics.included()).isEmpty(); // the shared topic repeats the query → no discrimination
		assertThat(topics.stats().distinctSignatures()).isEqualTo(1);
		assertThat(combined.included()).hasSize(3);
		assertThat(combined.stats().distinctSignatures()).isEqualTo(3);
	}

	@Test
	void papersWithoutKeywordsFallBackToTopicsInCombined() {
		ConceptModel m = ConceptPolicy.build(GNN, List.of(
				paper(1, topic("Molecular Biology", 0.9)), paper(2, topic("Molecular Biology", 0.8), kw("drug discovery", 0.9)),
				paper(3, kw("drug discovery", 0.9)), paper(4, kw("q", 0.9)), paper(5, kw("r", 0.9))),
				Representation.COMBINED);

		assertThat(m.paperConcepts().get(1L)).containsOnlyKeys("TOPIC:molecular biology");
		assertThat(m.stats().candidatesWithConcepts()).isEqualTo(3);
	}

	@Test
	void weightsAreFrequencyAwareAndSumToOne() {
		ConceptModel m = ConceptPolicy.build(GNN, List.of(
				paper(1, kw("a1", 0.9), kw("b1", 0.9)), paper(2, kw("a1", 0.9), kw("b1", 0.9)), paper(3, kw("a1", 0.9)),
				paper(4, kw("c1", 0.9)), paper(5, kw("c1", 0.9)), paper(6, kw("d1", 0.9)), paper(7, kw("e1", 0.9))),
				Representation.KEYWORD_ONLY);

		assertThat(m.included().stream().mapToDouble(Entry::weight).sum()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
		assertThat(m.included().get(0).label()).isEqualTo("a1"); // most supported first
		assertThat(m.included().get(0).weight()).isGreaterThan(m.included().get(1).weight());
	}

	@Test
	void conceptUniverseIsCapped() {
		List<ConceptPolicy.Input> papers = new ArrayList<>();
		for (int c = 0; c < 70; c++) {
			papers.add(paper(c * 2L + 1, kw("concept " + (char) ('a' + c / 26) + (char) ('a' + c % 26), 0.9)));
			papers.add(paper(c * 2L + 2, kw("concept " + (char) ('a' + c / 26) + (char) ('a' + c % 26), 0.9)));
		}
		ConceptModel m = ConceptPolicy.build(GNN, papers, Representation.KEYWORD_ONLY);
		assertThat(m.included()).hasSize(ConceptPolicy.MAX_CONCEPTS);
		assertThat(m.excludedByReason()).containsEntry("CAP_EXCEEDED", 20);
	}

	@Test
	void buildIsDeterministicRegardlessOfInputOrder() {
		List<ConceptPolicy.Input> papers = List.of(paper(1, kw("pooling", 0.9), kw("attention", 0.8)),
				paper(2, kw("attention", 0.7)), paper(3, kw("pooling", 0.6)), paper(4, kw("x", 0.9)), paper(5, kw("y", 0.9)));
		ConceptModel a = ConceptPolicy.build(GNN, papers, Representation.COMBINED);
		ConceptModel b = ConceptPolicy.build(GNN, papers.reversed(), Representation.COMBINED);
		assertThat(a).isEqualTo(b);
		assertThat(Map.copyOf(a.excludedByReason())).isEqualTo(Map.copyOf(b.excludedByReason()));
	}

}
