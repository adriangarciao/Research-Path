package dev.researchpath.path;

import dev.researchpath.openalex.OpenAlexSearchPage;

/** Public access to the path test pool for tests in other packages. */
public final class PathFixtures {

	public static final String Q = PathServiceTests.Q;

	private PathFixtures() {
	}

	public static OpenAlexSearchPage pool(String titlePrefix) {
		return PathServiceTests.pool(titlePrefix);
	}

}
