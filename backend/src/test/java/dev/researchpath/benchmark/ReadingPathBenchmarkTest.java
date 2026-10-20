package dev.researchpath.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.researchpath.concept.ConceptModel;
import dev.researchpath.optimizer.GreedyOptimizer;
import dev.researchpath.optimizer.OptimizerConfig;
import dev.researchpath.optimizer.Preferences;
import dev.researchpath.optimizer.Preferences.Experience;
import dev.researchpath.optimizer.Preferences.Focus;
import dev.researchpath.sequence.PathPaper;
import dev.researchpath.sequence.ReadingPath;
import dev.researchpath.sequence.SequenceBuilder;

/** M10 on the committed datasets: ordering is presentation only and every explanation is grounded. */
class ReadingPathBenchmarkTest {

	private static final List<Preferences> ALL_PREFERENCES = java.util.Arrays.stream(Experience.values())
			.flatMap(e -> java.util.Arrays.stream(Focus.values()).map(f -> new Preferences(e, f))).toList();

	@ParameterizedTest
	@ValueSource(strings = { "gnn-200", "rl-robotics-200", "medical-ai-200" })
	void pathsKeepTheSelectedSetAndAreWellFormedForEveryBudgetAndPreference(String name) throws Exception {
		BenchmarkDataset d = OptimizerBenchmarkTest.load(name);
		ConceptModel model = SelectionMetrics.conceptModel(d);
		Map<String, String> labels = new HashMap<>();
		model.included().forEach(e -> labels.put(e.key(), e.label()));
		for (Preferences prefs : ALL_PREFERENCES) {
			for (int k : BenchmarkRunner.BUDGETS) {
				GreedyOptimizer.Result selection = new OptimizerSelector(prefs, OptimizerConfig.DEFAULT).optimize(d, k);
				List<PathPaper> papers = ReadingPaths.papers(d, model, selection);
				ReadingPath path = SequenceBuilder.build(papers, prefs);

				assertThat(new HashSet<>(path.orderedIds())).as("%s k=%d %s", name, k, prefs)
						.isEqualTo(new HashSet<>(selection.selectedIds()));
				assertThat(path.orderedIds()).hasSize(selection.selectedIds().size());
				assertThat(path.stages()).hasSizeBetween(1, k <= 5 ? 2 : k <= 10 ? 3 : 4);
				assertThat(path.stages().get(path.stages().size() - 1).lastPosition()).isEqualTo(path.items().size());

				Map<String, PathPaper> byId = new HashMap<>();
				papers.forEach(p -> byId.put(p.id(), p));
				for (ReadingPath.Item item : path.items()) {
					Set<String> stored = new HashSet<>();
					byId.get(item.id()).concepts().forEach(c -> stored.add(c.label()));
					assertThat(stored).containsAll(item.tags());
					GreedyOptimizer.Decision dec = byId.get(item.id()).decision();
					for (ReadingPath.Explanation r : item.reasons()) {
						if (r.code().equals("ADDS_CONCEPTS")) {
							// Every concept named in the sentence is one this paper newly added at selection time.
							dec.newConcepts().stream().map(labels::get).filter(l -> r.text().contains(l))
									.forEach(l -> assertThat(stored).contains(l));
							assertThat(dec.newConcepts()).isNotEmpty();
						}
						assertThat(r.text()).doesNotContainPattern("(?i)prerequisite|requires|must|read .* first");
					}
					if (item.link() != null) {
						assertThat(item.link()).matches("https?://.+");
					}
				}
			}
		}
	}

	@Test
	void namedConceptsInReasonsAreExactlyStoredLabels() throws Exception {
		BenchmarkDataset d = OptimizerBenchmarkTest.load("gnn-200");
		ConceptModel model = SelectionMetrics.conceptModel(d);
		GreedyOptimizer.Result selection = new OptimizerSelector(Preferences.DEFAULT, OptimizerConfig.DEFAULT).optimize(d, 20);
		List<PathPaper> papers = ReadingPaths.papers(d, model, selection);
		ReadingPath path = SequenceBuilder.build(papers, Preferences.DEFAULT);
		Map<String, PathPaper> byId = new HashMap<>();
		papers.forEach(p -> byId.put(p.id(), p));
		for (ReadingPath.Item item : path.items()) {
			for (ReadingPath.Explanation r : item.reasons()) {
				if (!r.code().equals("ADDS_CONCEPTS") || !r.text().contains(": ")) {
					continue;
				}
				String list = r.text().substring(r.text().indexOf(": ") + 2).replaceAll(" \\(and \\d+ more\\)\\.$", "")
						.replaceAll("\\.$", "");
				Set<String> stored = new HashSet<>();
				byId.get(item.id()).concepts().forEach(c -> stored.add(c.label()));
				// Labels may themselves contain commas, so check the sentence is built only from stored labels.
				String remainder = list;
				for (String label : stored.stream().sorted((a, b) -> b.length() - a.length()).toList()) {
					remainder = remainder.replace(label, "");
				}
				assertThat(remainder.replace(",", "").replace(" and ", "").trim()).as(r.text()).isEmpty();
			}
		}
	}

	/** Reading order for the default preferences; a change must be deliberate (and the review regenerated). */
	static final Map<String, String> PATH_FINGERPRINTS = Map.of(
			"gnn-200", "0c0b6247d7f2ed4e9772c3a7716428d0a752a71642bee63cb1cd8bf0dc72f75f", // D18: one paper changes at k = 20
			"rl-robotics-200", "6efb05ec1cc676d356a7ba431dae89dbbdb7d2f8d1aea1d928345f60b26c205c",
			"medical-ai-200", "44bdebc5747343d5303de3b91c2edd8b9d682d731b350e7fd34599fb17e8b566");

	@ParameterizedTest
	@ValueSource(strings = { "gnn-200", "rl-robotics-200", "medical-ai-200" })
	void defaultPathsMatchThePinnedFingerprint(String name) throws Exception {
		BenchmarkDataset d = OptimizerBenchmarkTest.load(name);
		StringBuilder sb = new StringBuilder();
		for (int k : BenchmarkRunner.BUDGETS) {
			ReadingPath p = ReadingPaths.path(d, k, Preferences.DEFAULT);
			sb.append(k).append(':').append(p.orderedIds()).append(' ')
					.append(p.stages().stream().map(s -> s.label() + "@" + s.firstPosition()).toList()).append('\n');
		}
		String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
		assertThat(actual).as("reading paths changed for %s:%n%s", name, sb).isEqualTo(PATH_FINGERPRINTS.get(name));
	}

	@Test
	void reviewArtifactCoversThreeTopicsAndBothExperienceLevels() throws Exception {
		List<BenchmarkDataset> all = List.of(OptimizerBenchmarkTest.load("gnn-200"),
				OptimizerBenchmarkTest.load("rl-robotics-200"), OptimizerBenchmarkTest.load("medical-ai-200"));
		String md = ReadingPaths.markdown(all);
		assertThat(md).contains("## graph neural networks", "## reinforcement learning in robotics",
				"## AI-assisted medical diagnosis", "k = 10, Beginner / Balanced", "k = 10, Advanced / Balanced",
				"ORDER_IS_HEURISTIC", "Beginner vs Advanced");
		assertThat(md).isEqualTo(ReadingPaths.markdown(all));
	}

}
