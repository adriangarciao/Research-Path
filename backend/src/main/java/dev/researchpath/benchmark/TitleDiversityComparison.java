package dev.researchpath.benchmark;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import dev.researchpath.benchmark.BenchmarkDataset.Candidate;
import dev.researchpath.concept.ConceptModel;
import dev.researchpath.optimizer.GreedyOptimizer;
import dev.researchpath.optimizer.OptimizerConfig;
import dev.researchpath.optimizer.Preferences;
import dev.researchpath.optimizer.TitleKey;
import dev.researchpath.sequence.ReadingPath;
import dev.researchpath.sequence.SequenceBuilder;

/**
 * D18 evidence (M4 / release R7): the default configuration with and without the one-paper-per-title constraint,
 * on identical frozen pools, against criteria fixed before any result was seen (docs/evaluation/title-diversity.md).
 */
public final class TitleDiversityComparison {

	/** Topics the D12/D09/D16 settings were chosen on. */
	public static final Set<String> AUDIT_TOPICS = LandmarkSweep.SELECTION_TOPICS;

	/** Frozen for this experiment before the constraint was implemented; validation only. */
	public static final Set<String> FRESH_TOPICS = Set.of("compressed sensing", "differential privacy",
			"topological insulators", "photocatalytic water splitting", "few-shot learning");

	public static final List<Integer> BUDGETS = BenchmarkRunner.BUDGETS;

	static final double MAX_COVERAGE_LOSS = 0.02;
	static final double MAX_RELEVANCE_DROP = 0.01;
	static final int MAX_LANDMARK_LOSS = 1;
	static final double MAX_REDUNDANCY_RISE = 0.01;
	static final double MAX_RUNTIME_RATIO = 1.2;
	static final double RUNTIME_SLACK_MS = 5;

	private static final int TIMING_RUNS = 5;

	/** Topics the soft penalty λ is chosen on: the audit topics plus the motivating quantum example (disclosed). */
	public static final Set<String> CHOICE_TOPICS = Set.of("graph neural networks", "reinforcement learning in robotics",
			"AI-assisted medical diagnosis", "quantum error correction");

	/** Pre-registered grid for the soft alternative. */
	public static final List<Double> PENALTIES = List.of(0.005, 0.01, 0.02, 0.05, 0.1, 0.2);

	private TitleDiversityComparison() {
	}

	/** A configuration compared with the unchanged default, with the title key it uses. */
	public record Variant(String label, OptimizerConfig config, UnaryOperator<String> titleKey) {

		public static final Variant HARD = new Variant("hard constraint",
				OptimizerConfig.DEFAULT.withDistinctTitles(true), TitleKey::of);

		public static final Variant HARD_HYPHEN = new Variant("hard constraint, hyphen = space",
				OptimizerConfig.DEFAULT.withDistinctTitles(true), TitleKey::hyphenInsensitive);

		public static Variant soft(double penalty) {
			return new Variant(String.format(Locale.ROOT, "soft λ=%s", penalty),
					OptimizerConfig.DEFAULT.withTitleRepeatPenalty(penalty), TitleKey::of);
		}
	}

	public static List<Variant> variants() {
		List<Variant> v = new ArrayList<>(List.of(Variant.HARD, Variant.HARD_HYPHEN));
		PENALTIES.forEach(p -> v.add(Variant.soft(p)));
		return v;
	}

	/** One selection: metrics, landmarks, timing and the resulting reading-path structure. */
	public record Side(List<String> ids, List<String> titles, int size, int titleRepeats, double coverage,
			double relevanceMean, double redundancy, int landmarks, Double percentileMedian, double medianMillis,
			String stages, int citationLinks, List<String> warnings) {
	}

	/** @param distinctTitles eligible candidates with distinct title keys (untitled works count individually) */
	public record Row(String variant, String topic, String group, int budget, int poolSize, int distinctTitles, Side before,
			Side after, int papersChanged) {

		double coverageLoss() {
			return round(before.coverage() - after.coverage());
		}
	}

	public static List<Row> run(BenchmarkDataset d) {
		return run(d, List.of(Variant.HARD));
	}

	/** Every variant against the same unchanged default; repeats and distinct titles use the conservative key. */
	public static List<Row> run(BenchmarkDataset d, List<Variant> variants) {
		ConceptModel model = SelectionMetrics.conceptModel(d);
		Set<String> landmarks = new HashSet<>(Ablation.landmarks(d));
		int distinct = distinctTitles(d.candidates());
		OptimizerConfig off = OptimizerConfig.DEFAULT.withDistinctTitles(false);
		List<Row> rows = new ArrayList<>();
		for (int k : BUDGETS) {
			Side before = side(d, model, landmarks, k, off, TitleKey::of);
			for (Variant v : variants) {
				Side after = side(d, model, landmarks, k, v.config(), v.titleKey());
				Set<String> a = new HashSet<>(after.ids());
				int changed = (int) before.ids().stream().filter(id -> !a.contains(id)).count();
				rows.add(new Row(v.label(), d.topic(), group(d.topic()), k, d.candidates().size(), distinct, before, after,
						changed));
			}
		}
		return rows;
	}

	static String group(String topic) {
		if (AUDIT_TOPICS.contains(topic)) {
			return "audit";
		}
		if (FRESH_TOPICS.contains(topic)) {
			return "fresh (M4)";
		}
		return Set.of("neural machine translation", "CRISPR gene editing", "urban heat island mitigation").contains(topic)
				? "fresh (D16)" : "held-out";
	}

	private static Side side(BenchmarkDataset d, ConceptModel model, Set<String> landmarks, int k, OptimizerConfig config,
			UnaryOperator<String> titleKey) {
		OptimizerSelector selector = new OptimizerSelector(Preferences.DEFAULT, config, "OPTIMIZER", titleKey);
		List<Double> times = new ArrayList<>();
		GreedyOptimizer.Result result = null;
		for (int i = 0; i < TIMING_RUNS; i++) {
			long start = System.nanoTime();
			result = selector.optimize(d, k);
			times.add((System.nanoTime() - start) / 1e6);
		}
		Map<String, Candidate> byId = d.candidates().stream().collect(Collectors.toMap(Candidate::openalexId, Function.identity()));
		List<Candidate> selected = result.selectedIds().stream().map(byId::get).toList();
		SelectionMetrics m = SelectionMetrics.of(d, model, k, selected);
		ReadingPath path = SequenceBuilder.build(ReadingPaths.papers(d, model, result), Preferences.DEFAULT, config);
		String stages = path.stages().stream().map(s -> s.label() + " (" + (s.lastPosition() - s.firstPosition() + 1) + ")")
				.collect(Collectors.joining(" → "));
		return new Side(result.selectedIds(), selected.stream().map(Candidate::title).toList(), selected.size(),
				selected.size() - distinctTitles(selected), m.weightedCoverage(), m.relevanceMean(), m.redundancyJaccard(),
				(int) selected.stream().filter(c -> landmarks.contains(c.openalexId())).count(),
				SensitivitySweep.medianPercentile(selected), median(times), stages, path.citationLinks(),
				java.util.stream.Stream.concat(result.warnings().stream(), path.warnings().stream()).map(w -> w.split(":")[0])
						.toList());
	}

	static int distinctTitles(List<Candidate> candidates) {
		Set<String> keys = new HashSet<>();
		int untitled = 0;
		for (Candidate c : candidates) {
			String key = TitleKey.of(c.title());
			if (key == null) {
				untitled++;
			} else {
				keys.add(key);
			}
		}
		return keys.size() + untitled;
	}

	/** Evaluation of one pre-registered criterion. */
	public record Check(String id, String criterion, boolean passed, String evidence) {
	}

	public static List<Check> checks(List<Row> rows) {
		List<Check> checks = new ArrayList<>();
		int repeats = rows.stream().mapToInt(r -> r.after().titleRepeats()).sum();
		checks.add(new Check("C1", "no repeated title with the constraint", repeats == 0,
				"repeats with: " + repeats + "; without: " + rows.stream().mapToInt(r -> r.before().titleRepeats()).sum()));
		Row worstCoverage = rows.stream().max((a, b) -> Double.compare(a.coverageLoss(), b.coverageLoss())).orElseThrow();
		checks.add(new Check("C2", "coverage loss ≤ " + MAX_COVERAGE_LOSS + " everywhere",
				worstCoverage.coverageLoss() <= MAX_COVERAGE_LOSS + 1e-9,
				"max loss " + fmt(worstCoverage.coverageLoss()) + " (" + worstCoverage.topic() + ", k=" + worstCoverage.budget() + ")"));
		Row worstRelevance = rows.stream()
				.min((a, b) -> Double.compare(a.after().relevanceMean() - a.before().relevanceMean(),
						b.after().relevanceMean() - b.before().relevanceMean())).orElseThrow();
		double relDelta = worstRelevance.after().relevanceMean() - worstRelevance.before().relevanceMean();
		checks.add(new Check("C3", "mean relevance change ≥ −" + MAX_RELEVANCE_DROP, relDelta >= -MAX_RELEVANCE_DROP - 1e-9,
				"worst " + fmt(relDelta) + " (" + worstRelevance.topic() + ", k=" + worstRelevance.budget() + ")"));
		List<Row> k10 = rows.stream().filter(r -> r.budget() == 10).toList();
		int maxLandmarkLoss = k10.stream().mapToInt(r -> r.before().landmarks() - r.after().landmarks()).max().orElse(0);
		List<Row> auditK10 = k10.stream().filter(r -> r.group().equals("audit")).toList();
		// The audit part applies only when audit topics are among the rows (validation-only subsets have none).
		double auditMean = auditK10.stream().mapToInt(r -> r.after().landmarks()).average().orElse(Double.NaN);
		boolean auditOk = auditK10.isEmpty() || (auditMean >= LandmarkSweep.MIN_MEAN_LANDMARKS
				&& auditK10.stream().allMatch(r -> r.after().landmarks() >= LandmarkSweep.MIN_TOPIC_LANDMARKS));
		checks.add(new Check("C4", "landmarks at k=10: loss ≤ " + MAX_LANDMARK_LOSS + " per topic; audit mean ≥ 2, each ≥ 1",
				maxLandmarkLoss <= MAX_LANDMARK_LOSS && auditOk,
				"max loss " + maxLandmarkLoss + (auditK10.isEmpty() ? "; no audit topics" : "; audit mean " + fmt(auditMean))));
		List<String> pctBroken = rows.stream()
				.filter(r -> r.before().percentileMedian() != null && r.before().percentileMedian() >= 0.9
						&& (r.after().percentileMedian() == null || r.after().percentileMedian() < 0.9))
				.map(r -> r.topic() + " k=" + r.budget()).toList();
		checks.add(new Check("C5", "median citation percentile stays ≥ 0.9 where it was", pctBroken.isEmpty(),
				pctBroken.isEmpty() ? "held everywhere" : "dropped below 0.9: " + String.join(", ", pctBroken)));
		Row worstRedundancy = rows.stream()
				.max((a, b) -> Double.compare(a.after().redundancy() - a.before().redundancy(),
						b.after().redundancy() - b.before().redundancy())).orElseThrow();
		double redDelta = worstRedundancy.after().redundancy() - worstRedundancy.before().redundancy();
		checks.add(new Check("C6", "redundancy rise ≤ " + MAX_REDUNDANCY_RISE, redDelta <= MAX_REDUNDANCY_RISE + 1e-9,
				"worst " + fmt(redDelta) + " (" + worstRedundancy.topic() + ", k=" + worstRedundancy.budget() + ")"));
		List<String> incomplete = rows.stream()
				.filter(r -> r.after().size() != Math.min(r.budget(), r.distinctTitles()))
				.map(r -> r.topic() + " k=" + r.budget()).toList();
		long shortfalls = rows.stream().filter(r -> r.after().size() < r.budget()).count();
		checks.add(new Check("C7", "size = min(k, distinct titles); shortfalls explained", incomplete.isEmpty(),
				incomplete.isEmpty() ? "every selection complete; " + shortfalls + " explained shortfall(s)"
						: "unexpected sizes: " + String.join(", ", incomplete)));
		double before = median(rows.stream().map(r -> r.before().medianMillis()).toList());
		double after = median(rows.stream().map(r -> r.after().medianMillis()).toList());
		checks.add(new Check("C8", "median runtime ≤ " + MAX_RUNTIME_RATIO + " × before + " + (int) RUNTIME_SLACK_MS + " ms",
				after <= MAX_RUNTIME_RATIO * before + RUNTIME_SLACK_MS,
				String.format(Locale.ROOT, "%.1f ms → %.1f ms", before, after)));
		List<String> newWarnings = rows.stream()
				.filter(r -> !new HashSet<>(r.before().warnings()).containsAll(r.after().warnings()))
				.map(r -> r.topic() + " k=" + r.budget() + " " + r.after().warnings()).toList();
		long stageChanges = rows.stream().filter(r -> !r.before().stages().equals(r.after().stages())).count();
		checks.add(new Check("C9", "progression reported; no unexpected new warnings", newWarnings.isEmpty(),
				stageChanges + " of " + rows.size() + " stage structures changed"
						+ (newWarnings.isEmpty() ? "" : "; new warnings: " + String.join("; ", newWarnings))));
		return checks;
	}

	public static String markdown(List<Row> rows) {
		List<Check> checks = checks(rows);
		StringBuilder sb = new StringBuilder("# One paper per title (M4 / R7) — before vs after\n\n")
				.append("Generated by `BenchmarkCli`. Before = current default without the title constraint; after = the ")
				.append("same configuration with it (Intermediate / Balanced). Identical frozen pools. Criteria were fixed ")
				.append("before any result was seen (docs/evaluation/title-diversity.md). Runtime = median of ")
				.append(TIMING_RUNS).append(" runs per selection, including the concept model; machine-dependent.\n\n")
				.append("## Criteria\n\n| # | Criterion | Result | Evidence |\n|---|---|---|---|\n");
		for (Check c : checks) {
			sb.append("| ").append(c.id()).append(" | ").append(c.criterion()).append(" | ")
					.append(c.passed() ? "PASS" : "**FAIL**").append(" | ").append(c.evidence()).append(" |\n");
		}
		sb.append("\n## Per topic and budget\n\n")
				.append("| Topic | Group | k | Distinct titles / pool | Repeats before → after | Changed papers | Coverage before → after ")
				.append("| Relevance | Redundancy | Landmarks | Percentile median | Size | ms |\n")
				.append("|---|---|---:|---|---|---:|---|---|---|---|---|---|---|\n");
		for (Row r : rows) {
			sb.append("| ").append(r.topic()).append(" | ").append(r.group()).append(" | ").append(r.budget()).append(" | ")
					.append(r.distinctTitles()).append(" / ").append(r.poolSize()).append(" | ")
					.append(r.before().titleRepeats()).append(" → ").append(r.after().titleRepeats()).append(" | ")
					.append(r.papersChanged()).append(" | ")
					.append(fmt(r.before().coverage())).append(" → ").append(fmt(r.after().coverage())).append(" | ")
					.append(fmt(r.before().relevanceMean())).append(" → ").append(fmt(r.after().relevanceMean())).append(" | ")
					.append(fmt(r.before().redundancy())).append(" → ").append(fmt(r.after().redundancy())).append(" | ")
					.append(r.before().landmarks()).append(" → ").append(r.after().landmarks()).append(" | ")
					.append(pct(r.before().percentileMedian())).append(" → ").append(pct(r.after().percentileMedian())).append(" | ")
					.append(r.before().size()).append(" → ").append(r.after().size()).append(" | ")
					.append(String.format(Locale.ROOT, "%.0f → %.0f", r.before().medianMillis(), r.after().medianMillis()))
					.append(" |\n");
		}
		sb.append("\n## Learning progression where the selection changed\n\n")
				.append("| Topic | k | Stages before | Stages after | Citation links | Warnings before → after |\n")
				.append("|---|---:|---|---|---|---|\n");
		for (Row r : rows) {
			if (r.papersChanged() == 0) {
				continue;
			}
			sb.append("| ").append(r.topic()).append(" | ").append(r.budget()).append(" | ").append(r.before().stages())
					.append(" | ").append(r.after().stages()).append(" | ").append(r.before().citationLinks()).append(" → ")
					.append(r.after().citationLinks()).append(" | ").append(warn(r.before().warnings())).append(" → ")
					.append(warn(r.after().warnings())).append(" |\n");
		}
		sb.append("\n## Selections with repeated titles before the constraint\n");
		for (Row r : rows) {
			if (r.before().titleRepeats() == 0) {
				continue;
			}
			sb.append("\n### ").append(r.topic()).append(", k = ").append(r.budget()).append("\n\n")
					.append("| # | Before | After |\n|---:|---|---|\n");
			for (int i = 0; i < Math.max(r.before().size(), r.after().size()); i++) {
				sb.append("| ").append(i + 1).append(" | ").append(cell(r.before(), i)).append(" | ").append(cell(r.after(), i))
						.append(" |\n");
			}
		}
		return sb.toString();
	}

	/** Soft-penalty choice per the pre-registered rule: smallest λ with no repeats and C2–C6 on the choice topics. */
	public static Optional<String> chooseSoft(List<Row> rows) {
		for (double p : PENALTIES) {
			String label = Variant.soft(p).label();
			List<Row> choice = rows.stream().filter(r -> r.variant().equals(label) && CHOICE_TOPICS.contains(r.topic()))
					.toList();
			boolean noRepeats = choice.stream().allMatch(r -> r.after().titleRepeats() == 0);
			boolean criteria = checks(choice).stream().filter(c -> Set.of("C2", "C3", "C5", "C6").contains(c.id()))
					.allMatch(Check::passed)
					&& choice.stream().filter(r -> r.budget() == 10)
							.allMatch(r -> r.before().landmarks() - r.after().landmarks() <= MAX_LANDMARK_LOSS);
			if (!choice.isEmpty() && noRepeats && criteria) {
				return Optional.of(label);
			}
		}
		return Optional.empty();
	}

	/** One line per variant and topic group: repeats left and the criteria. */
	public static String sweepMarkdown(List<Row> rows) {
		StringBuilder sb = new StringBuilder("# One paper per title (M4 / R7) — variants\n\n")
				.append("Each variant against the unchanged default on the same frozen pools. Choice topics: ")
				.append(String.join(", ", CHOICE_TOPICS.stream().sorted().toList()))
				.append("; validation: all other topics. Repeats = selected works sharing a conservative title key. ")
				.append("Soft-penalty choice rule (pre-registered): the smallest λ with no repeats and C2–C6 on the choice ")
				.append("topics.\n\n**Soft choice:** ").append(chooseSoft(rows).orElse("none — no λ met the rule"))
				.append("\n\n| Variant | Topics | Repeats left (before) | Max coverage loss | Worst relevance Δ ")
				.append("| Max redundancy rise | Max landmark loss k=10 | Changed papers | Failed criteria |\n")
				.append("|---|---|---|---|---|---|---|---|---|\n");
		for (String label : rows.stream().map(Row::variant).distinct().toList()) {
			for (boolean choice : List.of(true, false)) {
				List<Row> sel = rows.stream()
						.filter(r -> r.variant().equals(label) && CHOICE_TOPICS.contains(r.topic()) == choice).toList();
				if (sel.isEmpty()) {
					continue;
				}
				sb.append("| ").append(label).append(" | ").append(choice ? "choice" : "validation").append(" | ")
						.append(sel.stream().mapToInt(r -> r.after().titleRepeats()).sum()).append(" (")
						.append(sel.stream().mapToInt(r -> r.before().titleRepeats()).sum()).append(") | ")
						.append(fmt(sel.stream().mapToDouble(Row::coverageLoss).max().orElse(0))).append(" | ")
						.append(fmt(sel.stream().mapToDouble(r -> r.after().relevanceMean() - r.before().relevanceMean())
								.min().orElse(0))).append(" | ")
						.append(fmt(sel.stream().mapToDouble(r -> r.after().redundancy() - r.before().redundancy())
								.max().orElse(0))).append(" | ")
						.append(sel.stream().filter(r -> r.budget() == 10)
								.mapToInt(r -> r.before().landmarks() - r.after().landmarks()).max().orElse(0)).append(" | ")
						.append(sel.stream().mapToInt(Row::papersChanged).sum()).append(" | ")
						.append(failed(sel))
						.append(" |\n");
			}
		}
		return sb.toString();
	}

	private static String failed(List<Row> rows) {
		String ids = String.join(", ", checks(rows).stream().filter(x -> !x.passed()).map(Check::id).toList());
		return ids.isEmpty() ? "–" : ids;
	}

	private static String cell(Side s, int i) {
		if (i >= s.size()) {
			return "–";
		}
		return Objects.toString(s.titles().get(i), "(untitled)").replace("|", "\\|") + " (" + s.ids().get(i) + ")";
	}

	private static String warn(List<String> warnings) {
		return warnings.isEmpty() ? "–" : String.join(", ", warnings);
	}

	private static String pct(Double v) {
		return v == null ? "–" : fmt(v);
	}

	private static String fmt(double v) {
		return String.format(Locale.ROOT, "%.3f", v);
	}

	private static double median(List<Double> values) {
		List<Double> s = values.stream().sorted().toList();
		int n = s.size();
		return n == 0 ? 0 : n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2;
	}

	private static double round(double v) {
		return Math.round(v * 1_000_000) / 1_000_000.0;
	}

}
