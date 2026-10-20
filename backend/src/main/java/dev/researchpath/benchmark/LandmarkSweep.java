package dev.researchpath.benchmark;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import dev.researchpath.benchmark.BenchmarkDataset.Candidate;
import dev.researchpath.concept.ConceptModel;
import dev.researchpath.optimizer.OptimizerConfig;
import dev.researchpath.optimizer.Preferences;

/**
 * D16 evidence: how two pre-declared mechanisms trade coverage for keeping each pool's most-cited works.
 * <ol>
 * <li>Landmark share λ: part of the influence weight given to log(1 + citations) / log(1 + pool max), with β.</li>
 * <li>Landmark seeds L: up to L reserved slots for strongly relevant most-cited works before greedy selection
 * (declared after mechanism 1 failed, with the same criteria).</li>
 * </ol>
 * The choice is made on the audit topics only, with criteria fixed before each sweep; other topics are validation.
 */
public final class LandmarkSweep {

	public static final List<Double> LANDMARK_SHARES = List.of(0.0, 0.25, 0.5, 0.75, 1.0);

	public static final List<Double> BETAS = List.of(4.0, 6.0, 8.0);

	public static final List<Integer> SEEDS = List.of(1, 2, 3);

	/** The topics the setting is chosen on (the D12/D09 tuning topics). */
	public static final Set<String> SELECTION_TOPICS = Set.of("graph neural networks", "reinforcement learning in robotics",
			"AI-assisted medical diagnosis");

	/** Pre-registered acceptance: mean landmarks at k = 10 ≥ 2 and every topic ≥ 1; coverage loss ≤ 0.05. */
	public static final double MIN_MEAN_LANDMARKS = 2.0;
	public static final int MIN_TOPIC_LANDMARKS = 1;
	public static final double MAX_COVERAGE_LOSS = 0.05;

	private LandmarkSweep() {
	}

	/** One configuration of the sweep: β, λ and the number of landmark seeds. */
	public record Setting(double beta, double landmarkShare, int seeds) {

		OptimizerConfig config() {
			return OptimizerConfig.DEFAULT.withQualityAndLandmark(beta, landmarkShare).withLandmarkSeeds(seeds);
		}

		String label() {
			return String.format(Locale.ROOT, "β=%.0f λ=%.2f seeds=%d", beta, landmarkShare, seeds);
		}
	}

	/** Mechanism 1 (β × λ) first, then mechanism 2 (seeds at β = 4, λ = 0): the order the choice rule follows. */
	public static List<Setting> settings() {
		List<Setting> s = new ArrayList<>();
		for (double beta : BETAS) {
			for (double lambda : LANDMARK_SHARES) {
				s.add(new Setting(beta, lambda, 0));
			}
		}
		for (int seeds : SEEDS) {
			s.add(new Setting(4.0, 0.0, seeds));
		}
		return s;
	}

	/**
	 * @param coverageLoss coverage of the λ = 0, β = 4, no-seed setting minus this setting's (positive = loss)
	 * @param d12 the D12 criteria still hold (coverage above the best baseline, relevance mean ≥ 0.95, median
	 *     percentile ≥ 0.9)
	 */
	public record Row(String topic, Setting setting, int budget, double coverage, double coverageLoss, int landmarksKept,
			double relevanceMean, Double percentileMedian, Double citationsMedian, boolean d12) {
	}

	public static List<Row> run(BenchmarkDataset d, List<Integer> budgets) {
		ConceptModel model = SelectionMetrics.conceptModel(d);
		Set<String> landmarks = new HashSet<>(Ablation.landmarks(d));
		List<Row> rows = new ArrayList<>();
		for (int k : budgets) {
			double bestBaseline = Baselines.ALL.stream()
					.mapToDouble(b -> SelectionMetrics.of(d, model, k, b.select(d, k)).weightedCoverage()).max().orElseThrow();
			double reference = SelectionMetrics.of(d, model, k,
					new OptimizerSelector(Preferences.DEFAULT, new Setting(4.0, 0.0, 0).config()).select(d, k)).weightedCoverage();
			for (Setting s : settings()) {
				List<Candidate> sel = new OptimizerSelector(Preferences.DEFAULT, s.config()).select(d, k);
				SelectionMetrics m = SelectionMetrics.of(d, model, k, sel);
				Double pct = SensitivitySweep.medianPercentile(sel);
				boolean d12 = m.weightedCoverage() > bestBaseline && m.relevanceMean() >= 0.95 && pct != null && pct >= 0.9;
				rows.add(new Row(d.topic(), s, k, m.weightedCoverage(), round(reference - m.weightedCoverage()),
						(int) sel.stream().filter(c -> landmarks.contains(c.openalexId())).count(), m.relevanceMean(), pct,
						m.citationsMedian(), d12));
			}
		}
		return rows;
	}

	/** True if a setting meets every pre-registered criterion on the selection topics. */
	public static boolean accepted(List<Row> rows, Setting setting) {
		List<Row> sel = rows.stream().filter(r -> SELECTION_TOPICS.contains(r.topic()) && r.setting().equals(setting)).toList();
		if (sel.isEmpty()) {
			return false;
		}
		List<Row> k10 = sel.stream().filter(r -> r.budget() == 10).toList();
		double mean = k10.stream().mapToInt(Row::landmarksKept).average().orElse(0);
		return mean >= MIN_MEAN_LANDMARKS
				&& k10.stream().allMatch(r -> r.landmarksKept() >= MIN_TOPIC_LANDMARKS)
				&& sel.stream().allMatch(r -> r.coverageLoss() <= MAX_COVERAGE_LOSS && r.d12());
	}

	/** The smallest change that passes, in {@link #settings()} order, excluding the unchanged setting. */
	public static Optional<Setting> choose(List<Row> rows) {
		return settings().stream()
				.filter(s -> !(s.landmarkShare() == 0 && s.seeds() == 0))
				.filter(s -> accepted(rows, s))
				.findFirst();
	}

	public static String markdown(List<Row> rows) {
		Optional<Setting> chosen = choose(rows);
		StringBuilder sb = new StringBuilder("# Landmark sweep (D16)\n\n")
				.append("Two pre-declared mechanisms. (1) Landmark share λ = part of the influence weight given to ")
				.append("log(1 + citations) / log(1 + pool max) instead of the normalized percentile, with quality share β. ")
				.append("(2) Landmark seeds = up to that many reserved slots (at most max(1, k / 5)) for the pool's ")
				.append(Ablation.LANDMARKS).append(" most-cited works that have strong relevance evidence, placed before ")
				.append("greedy selection; declared after (1) failed. Landmarks = how many of the pool's ")
				.append(Ablation.LANDMARKS).append(" most-cited works are kept. Loss = coverage of the unchanged setting ")
				.append("(β = 4, λ = 0, no seeds) minus this setting's. D12 = coverage above the best baseline, relevance ")
				.append("mean ≥ 0.95 and median percentile ≥ 0.9.\n\nPre-registered acceptance, judged on the audit topics ")
				.append("only (").append(String.join(", ", SELECTION_TOPICS.stream().sorted().toList()))
				.append("): mean landmarks at k = 10 ≥ ").append(MIN_MEAN_LANDMARKS).append(" with every topic ≥ ")
				.append(MIN_TOPIC_LANDMARKS).append("; coverage loss ≤ ").append(MAX_COVERAGE_LOSS)
				.append(" for every topic and budget; D12 still met. Choice = the first passing setting in the order below ")
				.append("(smallest change first). Other topics are validation only.\n\n**Chosen:** ")
				.append(chosen.map(Setting::label).orElse("none — no setting met every criterion"))
				.append("\n\n## Selection topics: acceptance per setting\n\n")
				.append("| Setting | Mean landmarks k=10 | Min | Max loss | D12 | Accepted |\n|---|---:|---:|---:|---|---|\n");
		for (Setting s : settings()) {
			List<Row> sel = rows.stream().filter(r -> SELECTION_TOPICS.contains(r.topic()) && r.setting().equals(s)).toList();
			List<Row> k10 = sel.stream().filter(r -> r.budget() == 10).toList();
			sb.append(String.format(Locale.ROOT, "| %s | %.2f | %d | %.3f | %s | %s |%n", s.label(),
					k10.stream().mapToInt(Row::landmarksKept).average().orElse(0),
					k10.stream().mapToInt(Row::landmarksKept).min().orElse(0),
					sel.stream().mapToDouble(Row::coverageLoss).max().orElse(0),
					sel.stream().allMatch(Row::d12) ? "yes" : "no", accepted(rows, s) ? "**yes**" : "no"));
		}
		String topic = null;
		for (Row r : rows) {
			if (!r.topic().equals(topic)) {
				topic = r.topic();
				sb.append("\n## ").append(topic).append(SELECTION_TOPICS.contains(topic) ? " (selection)" : " (validation)")
						.append("\n\n| Setting | k | Coverage | Loss | Landmarks | Relevance | Percentile median | Citations median | D12 |\n")
						.append("|---|---:|---:|---:|---:|---:|---:|---:|---|\n");
			}
			sb.append(String.format(Locale.ROOT, "| %s | %d | %.3f | %+.3f | %d/%d | %.2f | %s | %s | %s |%n",
					r.setting().label(), r.budget(), r.coverage(), r.coverageLoss(), r.landmarksKept(), Ablation.LANDMARKS,
					r.relevanceMean(),
					r.percentileMedian() == null ? "–" : String.format(Locale.ROOT, "%.2f", r.percentileMedian()),
					r.citationsMedian() == null ? "–" : r.citationsMedian(), r.d12() ? "yes" : "no"));
		}
		return sb.toString().replace("\r\n", "\n");
	}

	private static double round(double v) {
		return Math.round(v * 1_000_000) / 1_000_000.0;
	}

}
