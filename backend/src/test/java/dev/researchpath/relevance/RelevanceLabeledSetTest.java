package dev.researchpath.relevance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.researchpath.publication.NormalizedWork;
import dev.researchpath.publication.NormalizedWork.ConceptAssociation;
import dev.researchpath.publication.NormalizedWork.ConceptKind;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M06 gate: RelevancePolicy against hand-labeled real candidates ({@code relevance/labeled-candidates.json}).
 * Every expected tier is pinned so behavior changes are deliberate; false positives / negatives are
 * pinned and documented in docs/evaluation/relevance-review.md. Writes target/relevance-labeled-report.json.
 */
class RelevanceLabeledSetTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	record Item(String query, String source, Integer rank, String label, String expected, NormalizedWork work,
			RelevancePolicy.Decision decision) {

		boolean predictedEligible() {
			return decision.tier().eligible();
		}
	}

	private static List<Item> items;

	@BeforeAll
	static void load() throws Exception {
		items = new ArrayList<>();
		try (InputStream in = RelevanceLabeledSetTest.class.getResourceAsStream("/relevance/labeled-candidates.json")) {
			for (JsonNode n : JSON.readTree(in).path("items")) {
				NormalizedWork work = work(n.path("work"));
				String query = n.path("query").stringValue();
				items.add(new Item(query, n.path("source").stringValue(),
						n.path("providerRank").isIntegralNumber() ? n.path("providerRank").intValue() : null,
						n.path("label").stringValue(), n.path("expectedTier").stringValue(null), work,
						RelevancePolicy.decide(RelevancePolicy.analyzeQuery(query), work)));
			}
		}
		writeReport();
	}

	private static NormalizedWork work(JsonNode w) {
		List<ConceptAssociation> concepts = new ArrayList<>();
		for (JsonNode c : w.path("concepts")) {
			concepts.add(new ConceptAssociation(ConceptKind.valueOf(c.path("kind").stringValue()), c.path("id").stringValue(),
					c.path("name").stringValue(), c.path("score").isNumber() ? c.path("score").doubleValue() : null, false,
					null, null, null));
		}
		String title = w.path("title").stringValue();
		return new NormalizedWork(w.path("openalexId").stringValue(), null, title, title.toLowerCase(), null, null, null,
				null, null, null, null, null, w.path("abstractText").stringValue(null), null, null, null, null, null,
				List.of(), concepts, List.of(), 0, false, true);
	}

	private static void writeReport() throws Exception {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (Item i : items) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("query", i.query());
			row.put("openalexId", i.work().openalexId());
			row.put("rank", i.rank());
			row.put("label", i.label());
			row.put("tier", i.decision().tier().name());
			row.put("reason", i.decision().reason());
			row.put("title", i.work().title());
			row.put("evidence", i.decision().evidence());
			rows.add(row);
		}
		Path out = Path.of("target", "relevance-labeled-report.json");
		Files.createDirectories(out.getParent());
		Files.writeString(out, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(rows));
	}

	private static List<String> ids(java.util.function.Predicate<Item> filter) {
		return items.stream().filter(filter).map(i -> i.work().openalexId()).sorted().toList();
	}

	@Test
	void fixtureCoversAllThreeAuditTopicsAndEveryLabel() {
		assertThat(items).hasSizeGreaterThanOrEqualTo(100);
		assertThat(items).extracting(Item::query).containsOnly("graph neural networks",
				"reinforcement learning in robotics", "AI-assisted medical diagnosis");
		assertThat(items).extracting(Item::label).contains("RELEVANT", "TANGENTIAL", "UNRELATED", "UNCERTAIN");
	}

	@Test
	void everyItemGetsItsPinnedTier() {
		for (Item i : items) {
			assertThat(i.expected()).as("expectedTier missing for %s", i.work().openalexId()).isNotNull();
			assertThat(i.decision().tier().name()).as("%s [%s] %s", i.work().openalexId(), i.query(), i.work().title())
					.isEqualTo(i.expected());
		}
	}

	@Test
	void noUnrelatedCandidateIsEligible() {
		assertThat(ids(i -> i.label().equals("UNRELATED") && i.predictedEligible())).isEmpty();
	}

	@Test
	void missingAbstractAloneNeverExcludesARelevantCandidate() {
		assertThat(ids(i -> i.label().equals("RELEVANT") && i.work().abstractText() == null && !i.predictedEligible()))
				.isEmpty();
	}

	@Test
	void falsePositivesAndFalseNegativesArePinned() {
		// False positives: tangential works admitted as ELIGIBLE (documented in relevance-review.md).
		assertThat(ids(i -> i.label().equals("TANGENTIAL") && i.predictedEligible())).isEqualTo(PINNED_FALSE_POSITIVES);
		// False negatives: relevant works excluded.
		assertThat(ids(i -> i.label().equals("RELEVANT") && !i.predictedEligible())).isEqualTo(PINNED_FALSE_NEGATIVES);
	}

	/**
	 * Medical-AI only: two broad AI-in-healthcare overviews with confident "diagnosis" keywords, a clinical
	 * teaching study, two human-trust studies, and an EHR documentation tool whose title contains every
	 * query term. Lexical evidence cannot separate these; see relevance-review.md.
	 */
	static final List<String> PINNED_FALSE_POSITIVES = List.of("W4380319122", "W4403099250", "W4414258483",
			"W7160890064", "W7203651189", "W7220458778");
	/** "Artificial Intelligence in Healthcare: Diagnosis and Treatment": title lacks "medical"/"assisted" → BORDERLINE. */
	static final List<String> PINNED_FALSE_NEGATIVES = List.of("W7217182525");

}
