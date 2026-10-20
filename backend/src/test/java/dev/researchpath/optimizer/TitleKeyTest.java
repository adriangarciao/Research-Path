package dev.researchpath.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** M4: the title key ignores only differences a reader would not notice. */
class TitleKeyTest {

	@Test
	void caseWhitespaceQuotesDashesAndEdgePunctuationAreIgnored() {
		String key = TitleKey.of("Quantum Error Correction");
		assertThat(TitleKey.of("quantum error correction")).isEqualTo(key);
		assertThat(TitleKey.of("  QUANTUM   error\tcorrection. ")).isEqualTo(key);
		assertThat(TitleKey.of("“Quantum error correction”")).isEqualTo(key);
		assertThat(TitleKey.of("Compressed sensing: a review")).isEqualTo(TitleKey.of("Compressed sensing : a review"));
		assertThat(TitleKey.of("Compressed sensing:a review")).isEqualTo(TitleKey.of("compressed sensing: A review"));
		assertThat(TitleKey.of("Error–correcting codes")).isEqualTo(TitleKey.of("Error-correcting codes"));
		assertThat(TitleKey.of("Shor’s algorithm")).isEqualTo(TitleKey.of("Shor's algorithm"));
		// Canonical Unicode composition only: a precomposed and a decomposed é are the same text.
		assertThat(TitleKey.of("Résumé parsing")).isEqualTo(TitleKey.of("Résumé parsing"));
	}

	@Test
	void meaningfulDifferencesStayDistinct() {
		assertThat(TitleKey.of("Résumé parsing")).as("accents are not folded").isNotEqualTo(TitleKey.of("Resume parsing"));
		assertThat(TitleKey.of("Quantum error-correction")).as("hyphen vs space")
				.isNotEqualTo(TitleKey.of("Quantum error correction"));
		assertThat(TitleKey.of("Graph neural network")).as("plural").isNotEqualTo(TitleKey.of("Graph neural networks"));
		assertThat(TitleKey.of("Deep learning")).isNotEqualTo(TitleKey.of("Learning deep"));
		assertThat(TitleKey.of("C++ templates")).isNotEqualTo(TitleKey.of("C templates"));
		assertThat(TitleKey.of("Differential privacy")).isNotEqualTo(TitleKey.of("Differential privacy in practice"));
	}

	@Test
	void missingOrPunctuationOnlyTitlesHaveNoKey() {
		assertThat(TitleKey.of(null)).isNull();
		assertThat(TitleKey.of("")).isNull();
		assertThat(TitleKey.of("  ...  ")).isNull();
	}

	@Test
	void hyphenInsensitiveVariantIsOnlyForTheEvaluation() {
		assertThat(TitleKey.hyphenInsensitive("Quantum error-correction"))
				.isEqualTo(TitleKey.hyphenInsensitive("Quantum Error Correction"));
		assertThat(TitleKey.hyphenInsensitive(null)).isNull();
		assertThat(TitleKey.hyphenInsensitive(" - ")).isNull();
	}

}
