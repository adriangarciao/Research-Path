package dev.researchpath.benchmark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.researchpath.benchmark.BenchmarkDataset.Candidate;
import dev.researchpath.concept.ConceptModel;
import dev.researchpath.optimizer.OptimizerConfig;
import dev.researchpath.optimizer.Preferences;

/**
 * D16 evidence: how the landmark share λ (and the quality share β) trade coverage for keeping each pool's most-cited
 * works. The choice is made on the audit topics only, with criteria fixed before the sweep; held-out and fresh
 * validation topics are reported but never used to choose.
 */
public final class LandmarkSweep {

	public static final List<Double> LANDMARK_SHARES = List.of(0.0, 0.25, 0.5, 0.75, 1.0);

	public static final List<Double> BETAS = List.of(4.0, 6.0, 8.0);

	/** The topics the setting is chosen on (the D12/D09 tuning topics). */
	public static final Set<String> SELECTION_TOPICS = Set.of("graph neural networks", "reinforcement learning in robotics",
			"AI-assisted medical diagnosis");

	/** Pre-registered acceptance: mean landmarks at k = 10 ≥ 2 and every topic ≥ 1; coverage loss ≤ 0.05. */
	public static final double MIN_MEAN_LANDMARKS = 2.0;
	public static final int MIN_TOPIC_LANDMARKS = 1;
	public static final double MAX_COVERAGE_LOSS = 0.05;

	private LandmarkSweep() {
	}

	/**
	 * @param coverageLoss coverage of the current default minus this setting's coverage (positive = loss)
	 * @param d12 the D12 criteria still hold (coverage above the best baseline, relevance mean ≥ 0.95, median
	 *     percentile ≥ 0.9)
	 */
	public record Row(String topic, double beta, double landmarkShare, int budget, double coverage, double coverageLoss,
			int landmarksKept, double relevanceMean, Double percentileMedian, Double citationsMedian, boolean d12) {
	}

	public static List<Row> run(BenchmarkDataset d, List<Integer> budgets) {
		ConceptModel model = SelectionMetrics.conceptModel(d);
		Set<String> landmarks = new HashSet<>(Ablation.landmarks(d));
		Map<String, Candidate> byId = new HashMap<>();
		d.candidates().forEach(c -> byId.put(c.openalexId(), c));
		List<Row> rows = new ArrayList<>();
		for (int k : budgets) {
			double bestBaseline = Baselines.ALL.stream()
					.mapToDouble(b -> SelectionMetrics.of(d, model, k, b.select(d, k)).weightedCoverage()).max().orElseThrow();
			double reference = coverage(d, model, byId, k, OptimizerConfig.DEFAULT.withQualityAndLandmark(4.0, 0.0));
			for (double beta : BETAS) {
				for (double lambda : LANDMARK_SHARES) {
					OptimizerConfig config = OptimizerConfig.DEFAULT.withQualityAndLandmark(beta, lambda);
					List<Candidate> sel = new OptimizerSelector(Preferences.DEFAULT, config).select(d, k);
					SelectionMetrics m = SelectionMetrics.of(d, model, k, sel);
					Double pct = SensitivitySweep.medianPercentile(sel);
					boolean d12 = m.weightedCoverage() > bestBaseline && m.relevanceMean() >= 0.95 && pct != null && pct >= 0.9;
					rows.add(new Row(d.topic(), beta, lambda, k, m.weightedCoverage(), round(reference - m.weightedCoverage()),
							(int) sel.stream().filter(c -> landmarks.contains(c.openalexId())).count(), m.relevanceMean(), pct,
							m.citationsMedian(), d12));
				}
			}
		}
		return rows;
	}

	private static double coverage(BenchmarkDataset d, ConceptModel model, Map<String, Candidate> byId, int k,
			OptimizerConfig config) {
		return SelectionMetrics.of(d, model, k, new OptimizerSelector(Preferences.DEFAULT, config).select(d, k))
				.weightedCoverage();
	}

	/** True if a (β, λ) setting meets every pre-registered criterion on the selection topics. */
	public static boolean accepted(List<Row> rows, double beta, double lambda) {
		List<Row> sel = rows.stream().filter(r -> SELECTION_TOPICS.contains(r.topic()) && r.beta() == beta
				&& r.landmarkShare() == lambda).toList();
		if (sel.isEmpty()) {
			return false;
		}
		List<Row> k10 = sel.stream().filter(r -> r.budget() == 10).toList();
		double mean = k10.stream().mapToInt(Row::landmarksKept).average().orElse(0);
		return mean >= MIN_MEAN_LANDMARKS
				&& k10.stream().allMatch(r -> r.landmarksKept() >= MIN_TOPIC_LANDMARKS)
				&& sel.stream().allMatch(r -> r.coverageLoss() <= MAX_COVERAGE_LOSS && r.d12());
	}

	/** The smallest change that passes: β = 4 before larger β, then the smallest λ. Empty if none passes. */
	public static Optional<double[]> choose(List<Row> rows) {
		for (double beta : BETAS) {
			for (double lambda : LANDMARK_SHARES) {
				if (lambda > 0 && accepted(rows, beta, lambda)) {
					return Optional.of(new double[] { beta, lambda });
				}
			}
		}
		return Optional.empty();
	}

	public static String markdown(List<Row> rows) {
		Optional<double[]> chosen = choose(rows);
		StringBuilder sb = new StringBuilder("# Landmark sweep (D16)\n\n")
				.append("Landmark share λ = part of the influence weight given to log(1 + citations) / log(1 + pool max) ")
				.append("instead of the normalized percentile; β = quality share. Landmarks = how many of the pool's ")
				.append(Ablation.LANDMARKS).append(" most-cited works are kept. Loss = coverage of the λ = 0, β = 4 setting ")
				.append("minus this setting's. D12 = coverage above the best baseline, relevance mean ≥ 0.95 and median ")
				.append("percentile ≥ 0.9.\n\nPre-registered acceptance, judged on the audit topics only (")
				.append(String.join(", ", SELECTION_TOPICS.stream().sorted().toList()))
				.append("): mean landmarks at k = 10 ≥ ").append(MIN_MEAN_LANDMARKS).append(" with every topic ≥ ")
				.append(MIN_TOPIC_LANDMARKS).append("; coverage loss ≤ ").append(MAX_COVERAGE_LOSS)
				.append(" for every topic and budget; D12 still met. Choice = the smallest change that passes (β = 4 first, ")
				.append("then the smallest λ). Other topics are validation only.\n\n**Chosen:** ")
				.append(chosen.map(c -> String.format(Locale.ROOT, "β = %.0f, λ = %.2f", c[0], c[1]))
						.orElse("none — no setting met every criterion"))
				.append("\n\n## Selection topics: acceptance per setting\n\n| β | λ | Mean landmarks k=10 | Min | Max loss | D12 | Accepted |\n")
				.append("|---:|---:|---:|---:|---:|---|---|\n");
		for (double beta : BETAS) {
			for (double lambda : LANDMARK_SHARES) {
				List<Row> sel = rows.stream().filter(r -> SELECTION_TOPICS.contains(r.topic()) && r.beta() == beta
						&& r.landmarkShare() == lambda).toList();
				List<Row> k10 = sel.stream().filter(r -> r.budget() == 10).toList();
				sb.append(String.format(Locale.ROOT, "| %.0f | %.2f | %.2f | %d | %.3f | %s | %s |%n", beta, lambda,
						k10.stream().mapToInt(Row::landmarksKept).average().orElse(0),
						k10.stream().mapToInt(Row::landmarksKept).min().orElse(0),
						sel.stream().mapToDouble(Row::coverageLoss).max().orElse(0),
						sel.stream().allMatch(Row::d12) ? "yes" : "no", accepted(rows, beta, lambda) ? "**yes**" : "no"));
			}
		}
		String topic = null;
		for (Row r : rows) {
			if (!r.topic().equals(topic)) {
				topic = r.topic();
				sb.append("\n## ").append(topic).append(SELECTION_TOPICS.contains(topic) ? " (selection)" : " (validation)")
						.append("\n\n| β | λ | k | Coverage | Loss | Landmarks | Relevance | Percentile median | Citations median | D12 |\n")
						.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");
			}
			sb.append(String.format(Locale.ROOT, "| %.0f | %.2f | %d | %.3f | %+.3f | %d/%d | %.2f | %s | %s | %s |%n",
					r.beta(), r.landmarkShare(), r.budget(), r.coverage(), r.coverageLoss(), r.landmarksKept(),
					Ablation.LANDMARKS, r.relevanceMean(),
					r.percentileMedian() == null ? "–" : String.format(Locale.ROOT, "%.2f", r.percentileMedian()),
					r.citationsMedian() == null ? "–" : r.citationsMedian(), r.d12() ? "yes" : "no"));
		}
		return sb.toString().replace("\r\n", "\n");
	}

	private static double round(double v) {
		return Math.round(v * 1_000_000) / 1_000_000.0;
	}

}
