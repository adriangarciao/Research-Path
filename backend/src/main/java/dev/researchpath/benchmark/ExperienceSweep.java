package dev.researchpath.benchmark;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.researchpath.benchmark.BenchmarkDataset.Candidate;
import dev.researchpath.concept.ConceptModel;
import dev.researchpath.optimizer.OptimizerConfig;
import dev.researchpath.optimizer.Preferences;
import dev.researchpath.optimizer.Preferences.Experience;
import dev.researchpath.optimizer.Preferences.Focus;
import dev.researchpath.sequence.OverviewCue;

/**
 * D09 evidence: how strongly the experience level changes the selection for each experience weight. Overview
 * counts use the same weak metadata cue the optimizer uses; overlap is measured against the Intermediate selection
 * (which the weight does not affect). Offline and deterministic.
 */
public final class ExperienceSweep {

	public static final List<Double> WEIGHTS = List.of(0.0, 0.01, 0.02, 0.03, 0.05, 0.075, 0.10, 0.15);

	/** Minimum papers a Beginner or Advanced selection keeps in common with Intermediate (D09 criterion). */
	public static int minSharedWithIntermediate(int k) {
		return (int) Math.ceil(0.6 * k);
	}

	private ExperienceSweep() {
	}

	/**
	 * @param overviews overview-marked papers in the Beginner / Intermediate / Advanced selections
	 * @param sharedBeginner papers the Beginner selection shares with Intermediate
	 */
	public record Row(String topic, double weight, int budget, int overviewsBeginner, int overviewsIntermediate,
			int overviewsAdvanced, int sharedBeginner, int sharedAdvanced, double coverageBeginner,
			double coverageIntermediate, double coverageAdvanced) {

		public boolean ordered() {
			return overviewsBeginner >= overviewsIntermediate && overviewsIntermediate >= overviewsAdvanced;
		}

		public boolean nudgesOnly() {
			int min = minSharedWithIntermediate(budget);
			return sharedBeginner >= min && sharedAdvanced >= min;
		}

		/** Advanced keeps at least one overview when Intermediate has two or more. */
		public boolean advancedKeepsOverview() {
			return overviewsIntermediate < 2 || overviewsAdvanced >= 1;
		}

		public boolean changesSomething() {
			return sharedBeginner < budget || sharedAdvanced < budget;
		}

		public boolean meetsCriteria() {
			return ordered() && nudgesOnly() && advancedKeepsOverview();
		}
	}

	public static List<Row> run(BenchmarkDataset d, List<Integer> budgets, OptimizerConfig base) {
		ConceptModel model = SelectionMetrics.conceptModel(d);
		List<Row> rows = new ArrayList<>();
		for (double w : WEIGHTS) {
			OptimizerConfig config = base.withExperienceWeight(w);
			for (int k : budgets) {
				List<Candidate> b = select(d, k, Experience.BEGINNER, config);
				List<Candidate> i = select(d, k, Experience.INTERMEDIATE, config);
				List<Candidate> a = select(d, k, Experience.ADVANCED, config);
				rows.add(new Row(d.topic(), w, k, overviews(b), overviews(i), overviews(a), shared(b, i), shared(a, i),
						SelectionMetrics.of(d, model, k, b).weightedCoverage(),
						SelectionMetrics.of(d, model, k, i).weightedCoverage(),
						SelectionMetrics.of(d, model, k, a).weightedCoverage()));
			}
		}
		return rows;
	}

	private static List<Candidate> select(BenchmarkDataset d, int k, Experience e, OptimizerConfig config) {
		return new OptimizerSelector(new Preferences(e, Focus.BALANCED), config).select(d, k);
	}

	private static int overviews(List<Candidate> selection) {
		return (int) selection.stream().filter(c -> OverviewCue.of(c.workType(), c.title()) != null).count();
	}

	private static int shared(List<Candidate> x, List<Candidate> y) {
		Set<String> ids = new HashSet<>();
		y.forEach(c -> ids.add(c.openalexId()));
		return (int) x.stream().filter(c -> ids.contains(c.openalexId())).count();
	}

	public static String markdown(List<Row> rows, double chosen) {
		StringBuilder sb = new StringBuilder("# Experience-weight sweep (D09)\n\n")
				.append("Balanced focus. Overviews = papers marked as review/survey by type or title (weak cue) in the ")
				.append("Beginner / Intermediate / Advanced selections. Shared = papers in common with Intermediate. ")
				.append("Criteria fixed before the sweep: overview counts ordered B ≥ I ≥ A; Beginner and Advanced each ")
				.append("share ≥ 60% of papers with Intermediate (a nudge, not a different path); Advanced keeps ≥ 1 ")
				.append("overview when Intermediate has ≥ 2. Chosen weight: ").append(chosen).append(".\n");
		String topic = null;
		for (Row r : rows) {
			if (!r.topic().equals(topic)) {
				topic = r.topic();
				sb.append("\n## ").append(topic).append("\n\n")
						.append("| Weight | k | Overviews B / I / A | Shared B / A | Coverage B / I / A | Meets criteria |\n")
						.append("|---:|---:|---|---|---|---|\n");
			}
			sb.append(String.format(Locale.ROOT, "| %s | %d | %d / %d / %d | %d / %d of %d | %.3f / %.3f / %.3f | %s |%n",
					r.weight() == chosen ? "**" + r.weight() + "**" : String.valueOf(r.weight()), r.budget(),
					r.overviewsBeginner(), r.overviewsIntermediate(), r.overviewsAdvanced(), r.sharedBeginner(),
					r.sharedAdvanced(), r.budget(), r.coverageBeginner(), r.coverageIntermediate(), r.coverageAdvanced(),
					r.meetsCriteria() ? "yes" : "no"));
		}
		return sb.toString().replace("\r\n", "\n");
	}

}
