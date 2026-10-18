package dev.researchpath.publication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormalizationTest {

	@Test
	void doiStripsResolverPrefixesAndLowercases() {
		assertThat(TextNormalization.doi("https://doi.org/10.1109/TNN.2008.2005605")).isEqualTo("10.1109/tnn.2008.2005605");
		assertThat(TextNormalization.doi("http://dx.doi.org/10.1/X")).isEqualTo("10.1/x");
		assertThat(TextNormalization.doi("doi:10.1/X")).isEqualTo("10.1/x");
		assertThat(TextNormalization.doi("  ")).isNull();
		assertThat(TextNormalization.doi(null)).isNull();
	}

	@Test
	void openAlexIdsAreShortened() {
		assertThat(TextNormalization.shortOpenAlexId("https://openalex.org/W123")).isEqualTo("W123");
		assertThat(TextNormalization.shortOpenAlexId("https://openalex.org/keywords/graph-neural-networks"))
				.isEqualTo("keywords/graph-neural-networks");
		assertThat(TextNormalization.workId("https://openalex.org/W123")).isEqualTo("W123");
		assertThat(TextNormalization.workId("W123")).isEqualTo("W123");
		assertThat(TextNormalization.workId("https://openalex.org/A123")).isNull();
		assertThat(TextNormalization.workId("https://openalex.org/W12x")).isNull();
	}

	@Test
	void titleKeyIgnoresCaseAccentsAndPunctuation() {
		assertThat(TextNormalization.title("A Comprehensive Survey on Graph Neural Networks"))
				.isEqualTo(TextNormalization.title("A comprehensive survey on graph neural networks."));
		assertThat(TextNormalization.title("Réseaux  de neurones: graphes!")).isEqualTo("reseaux de neurones graphes");
		assertThat(TextNormalization.title(null)).isEmpty();
	}

	@Test
	void topicIsTrimmedCollapsedAndCaseFolded() {
		assertThat(TextNormalization.topic("  Graph   Neural\tNetworks ")).isEqualTo("graph neural networks");
	}

}
