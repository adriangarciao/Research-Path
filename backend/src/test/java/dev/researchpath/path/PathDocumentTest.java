package dev.researchpath.path;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** M3: the broader-topic hint is informational and only appears below half of the retrieved works. */
class PathDocumentTest {

	@Test
	void hintAppearsOnlyWhenFewerThanHalfOfTheRetrievedWorksAreEligible() {
		assertThat(PathDocument.smallPoolHint(200, 62)).hasValueSatisfying(h -> assertThat(h)
				.startsWith("SMALL_POOL: only 62 of 200 retrieved papers passed the relevance check")
				.contains("broader or differently worded topic"));
		assertThat(PathDocument.smallPoolHint(100, 49)).isPresent();
		assertThat(PathDocument.smallPoolHint(100, 50)).as("exactly half is not small").isEmpty();
		assertThat(PathDocument.smallPoolHint(200, 167)).as("observed typical pool").isEmpty();
		assertThat(PathDocument.smallPoolHint(100, 100)).isEmpty();
	}

	@Test
	void noHintWithoutAnythingToCompare() {
		assertThat(PathDocument.smallPoolHint(100, 0)).as("no eligible works is a 422, not a path").isEmpty();
		assertThat(PathDocument.smallPoolHint(0, 0)).isEmpty();
	}

}
