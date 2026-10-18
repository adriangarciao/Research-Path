package dev.researchpath.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.researchpath.benchmark.BenchmarkDataset.Candidate;
import dev.researchpath.concept.ConceptModel;
import dev.researchpath.publication.NormalizedWork.ConceptAssociation;
import dev.researchpath.publication.NormalizedWork.ConceptKind;

class BaselinesAndMetricsTest {

	private static ConceptAssociation kw(String name) {
		return new ConceptAssociation(ConceptKind.KEYWORD, "keywords/" + name, name, 0.9, false, null, null, null);
	}

	private static Candidate c(String id, int rank, double coverage, double titleShare, Integer citations, Integer year,
			String doi, String linkKind, String... keywords) {
		return new Candidate(id, rank, "title " + id, year, "article", doi, citations, null, null,
				linkKind == null ? null : "https://example.org/" + id, linkKind, coverage, titleShare,
				java.util.Arrays.stream(keywords).map(BaselinesAndMetricsTest::kw).toList(), List.of());
	}

	private static BenchmarkDataset dataset(Candidate... candidates) {
		return new BenchmarkDataset(1, UUID.randomUUID(), "graph neural networks", Instant.parse("2026-10-18T00:00:00Z"),
				new BenchmarkDataset.Policies(2, 1, 1), List.of(candidates));
	}

	private static List<String> ids(List<Candidate> cs) {
		return cs.stream().map(Candidate::openalexId).toList();
	}

	@Test
	void relevanceOrdersByCoverageThenTitleShareThenProviderRank() {
		BenchmarkDataset d = dataset(c("W1", 1, 0.75, 1.0, 10, 2020, null, "DOI"), c("W2", 2, 1.0, 0.5, 5, 2021, null, "DOI"),
				c("W3", 3, 1.0, 1.0, 1, 2022, null, "DOI"), c("W4", 4, 1.0, 1.0, 1, 2022, null, "DOI"));
		assertThat(ids(Baselines.RELEVANCE.select(d, 10))).containsExactly("W3", "W4", "W2", "W1");
	}

	@Test
	void citationRanksUnknownCountsAfterAllKnownOnes() {
		BenchmarkDataset d = dataset(c("W1", 1, 1, 1, null, 2020, null, null), c("W2", 2, 1, 1, 0, 2020, null, null),
				c("W3", 3, 1, 1, 900, 2020, null, null), c("W4", 4, 1, 1, 900, 2020, null, null));
		assertThat(ids(Baselines.CITATION.select(d, 4))).containsExactly("W3", "W4", "W2", "W1");
	}

	@Test
	void providerOrderFollowsRank() {
		BenchmarkDataset d = dataset(c("W9", 3, 1, 1, 1, 2020, null, null), c("W8", 1, 1, 1, 1, 2020, null, null));
		assertThat(ids(Baselines.PROVIDER_ORDER.select(d, 5))).containsExactly("W8", "W9");
	}

	@Test
	void budgetsAreNeverExceeded() {
		BenchmarkDataset d = dataset(c("W1", 1, 1, 1, 1, 2020, null, null), c("W2", 2, 1, 1, 1, 2020, null, null));
		for (Selector s : Baselines.ALL) {
			assertThat(s.select(d, 1)).hasSize(1);
			assertThat(s.select(d, 20)).hasSize(2); // fewer than requested when the pool is small
			assertThat(s.select(d, 0)).isEmpty();
			assertThatThrownBy(() -> s.select(d, -1)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void metricsUseExplicitDenominators() {
		BenchmarkDataset d = dataset(
				c("W1", 1, 1.0, 1.0, 100, 2018, "10.1/a", "OPEN_ACCESS", "pooling", "attention"),
				c("W2", 2, 0.75, 0.5, 10, 2020, "10.1/a", "DOI", "pooling"),
				c("W3", 3, 1.0, 1.0, null, null, null, null, "attention"),
				c("W4", 4, 1.0, 1.0, 1, 2022, null, "LANDING_PAGE", "message passing"),
				c("W5", 5, 1.0, 1.0, 50, 2021, null, "DOI", "message passing"));
		ConceptModel model = SelectionMetrics.conceptModel(d);
		assertThat(model.included()).hasSize(3); // pooling, attention, message passing (each support 2)

		SelectionMetrics m = SelectionMetrics.of(d, model, 3, Baselines.PROVIDER_ORDER.select(d, 3));

		assertThat(m.selectedCount()).isEqualTo(3);
		assertThat(m.conceptsCovered()).isEqualTo(2);
		assertThat(m.weightedCoverage()).isEqualTo(0.667); // equal weights: 2 of 3 concepts
		assertThat(m.relevanceMin()).isEqualTo(0.75);
		assertThat(m.conceptRepeats()).isEqualTo(2); // pooling ×2, attention ×2
		assertThat(m.citationsKnownShare()).isEqualTo(0.667);
		assertThat(m.citationsMedian()).isEqualTo(55.0);
		assertThat(m.topQuartileCitationShare()).isEqualTo(0.333); // pool q3 = 100 → only W1
		assertThat(m.yearMin()).isEqualTo(2018);
		assertThat(m.yearMax()).isEqualTo(2020);
		assertThat(m.sourceLinkShare()).isEqualTo(0.667);
		assertThat(m.openAccessShare()).isEqualTo(0.333);
		assertThat(m.duplicateDoiPairs()).isEqualTo(1); // W1 and W2 share a DOI
	}

	@Test
	void emptySelectionHasZeroMetricsNotErrors() {
		BenchmarkDataset d = dataset(c("W1", 1, 1, 1, 1, 2020, null, null, "x"));
		SelectionMetrics m = SelectionMetrics.of(d, SelectionMetrics.conceptModel(d), 0, List.of());
		assertThat(m.selectedCount()).isZero();
		assertThat(m.weightedCoverage()).isZero();
		assertThat(m.citationsMedian()).isNull();
	}

}
