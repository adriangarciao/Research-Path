package dev.researchpath.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M05 gate: the policy against hand-reviewed real pairs ({@code identity/reviewed-pairs.json}).
 * Safety requirement: no false merges. Missed duplicates are allowed under the conservative policy
 * but are pinned here so any change in behavior is deliberate and reviewed.
 */
class ReviewedIdentityPairsTest {

	private record Reviewed(long pairId, String label, IdentityDecision expected, IdentityEvidence a,
			IdentityEvidence b) {
	}

	private static List<Reviewed> pairs;

	@BeforeAll
	static void load() throws Exception {
		try (InputStream in = ReviewedIdentityPairsTest.class.getResourceAsStream("/identity/reviewed-pairs.json")) {
			JsonNode root = JsonMapper.builder().build().readTree(in);
			pairs = new ArrayList<>();
			long syntheticId = 1;
			for (JsonNode p : root.path("pairs")) {
				pairs.add(new Reviewed(p.path("pairId").longValue(), p.path("reviewerLabel").stringValue(),
						IdentityDecision.valueOf(p.path("expectedDecision").stringValue()),
						evidence(syntheticId++, p.path("a")), evidence(syntheticId++, p.path("b"))));
			}
		}
	}

	private static IdentityEvidence evidence(long pid, JsonNode w) {
		Set<String> ids = new HashSet<>();
		Set<String> names = new HashSet<>();
		for (JsonNode author : w.path("authors")) {
			if (author.path("id").isString()) {
				ids.add(author.path("id").stringValue());
			}
			String key = IdentityPolicy.nameKey(author.path("name").stringValue(null));
			if (key != null) {
				names.add(key);
			}
		}
		return new IdentityEvidence(pid, w.path("openalexId").stringValue(), w.path("doi").stringValue(null),
				w.path("normalizedTitle").stringValue(), w.path("year").isIntegralNumber() ? w.path("year").intValue() : null,
				w.path("workType").stringValue(null), ids, names, w.path("authors").size(),
				w.path("hasOpenAccessLink").booleanValue(), w.path("hasSourceLink").booleanValue(),
				w.path("hasAbstract").booleanValue(), w.path("metadataCompleteness").intValue());
	}

	@Test
	void fixtureHasTenToTwentyRealPairsCoveringEveryLabel() {
		assertThat(pairs).hasSizeBetween(10, 20);
		assertThat(pairs).extracting(Reviewed::label)
				.contains("SAME_WORK", "DISTINCT_VERSION", "DIFFERENT_WORK", "UNCERTAIN");
	}

	@Test
	void everyPairGetsItsReviewedExpectedDecision() {
		for (Reviewed p : pairs) {
			assertThat(IdentityPolicy.classify(p.a(), p.b()).decision())
					.as("pair %d (%s vs %s)", p.pairId(), p.a().openalexId(), p.b().openalexId())
					.isEqualTo(p.expected());
		}
	}

	@Test
	void noFalseMerges() {
		List<Long> falseMerges = pairs.stream()
				.filter(p -> !p.label().equals("SAME_WORK"))
				.filter(p -> IdentityPolicy.classify(p.a(), p.b()).decision().suppresses())
				.map(Reviewed::pairId)
				.toList();
		assertThat(falseMerges).isEmpty();
	}

	@Test
	void missedDuplicatesAreTheDocumentedConservativeCases() {
		List<Long> caught = pairs.stream()
				.filter(p -> p.label().equals("SAME_WORK"))
				.filter(p -> IdentityPolicy.classify(p.a(), p.b()).decision().suppresses())
				.map(Reviewed::pairId).sorted().toList();
		List<Long> missed = pairs.stream()
				.filter(p -> p.label().equals("SAME_WORK"))
				.filter(p -> !IdentityPolicy.classify(p.a(), p.b()).decision().suppresses())
				.map(Reviewed::pairId).sorted().toList();
		assertThat(caught).containsExactly(1L, 2L, 29L);
		// 13: year off by one; 28: conflicting DOIs (thesis vs arXiv); 16: wrong author metadata.
		assertThat(missed).containsExactly(13L, 16L, 28L);
	}

	@Test
	void canonicalChoiceForCaughtDuplicatesKeepsTheDoiRecord() {
		for (long id : List.of(1L, 29L)) {
			Reviewed p = pairs.stream().filter(r -> r.pairId() == id).findFirst().orElseThrow();
			IdentityEvidence canonical = IdentityPolicy.chooseCanonical(List.of(p.a(), p.b()));
			assertThat(canonical.doi()).as("pair %d", id).isNotNull();
			assertThat(Objects.equals(canonical, p.a()) || Objects.equals(canonical, p.b())).isTrue();
		}
	}

}
