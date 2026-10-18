package dev.researchpath.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.researchpath.benchmark.BenchmarkDataset.Candidate;
import dev.researchpath.concept.ConceptModel;
import dev.researchpath.optimizer.OptimizerConfig;
import dev.researchpath.optimizer.Preferences;
import dev.researchpath.optimizer.Preferences.Experience;
import dev.researchpath.optimizer.Preferences.Focus;

/**
 * D12 evidence: how the quality share β and the preference profiles change the optimizer's selections,
 * compared with the best baseline for the same dataset and budget. Offline and deterministic.
 */
public final class SensitivitySweep {

	public static final List<Double> BETAS = List.of(0.0, 0.25, 1.0, 2.0, 4.0, 8.0);

	private SensitivitySweep() {
	}

	public record Row(String topic, String setting, int budget, double coverage, double bestBaselineCoverage,
			String bestBaseline, double relevanceMean, Double citationPercentileMedian, Double citationsMedian,
			Integer yearMedian, double openAccessShare, int overlapWithBestBaseline) {
	}

	public static List<Row> run(BenchmarkDataset dataset, List<Integer> budgets, OptimizerConfig chosen) {
		ConceptModel model = SelectionMetrics.conceptModel(dataset);
		List<Row> rows = new ArrayList<>();
		for (int k : budgets) {
			SelectionMetrics bestBaseline = null;
			String bestName = null;
			List<Candidate> bestSelection = List.of();
			for (Selector b : Baselines.ALL) {
				List<Candidate> sel = b.select(dataset, k);
				SelectionMetrics m = SelectionMetrics.of(dataset, model, k, sel);
				if (bestBaseline == null || m.weightedCoverage() > bestBaseline.weightedCoverage()) {
					bestBaseline = m;
					bestName = b.name();
					bestSelection = sel;
				}
			}
			for (boolean qualityWeighted : List.of(false, true)) {
				for (double beta : BETAS) {
					rows.add(row(dataset, model, k, "Intermediate/Balanced " + (qualityWeighted ? "QW" : "additive")
							+ " β=" + beta, Preferences.DEFAULT, new OptimizerConfig(beta, qualityWeighted), bestBaseline,
							bestName, bestSelection));
				}
			}
			for (Experience e : Experience.values()) {
				for (Focus f : Focus.values()) {
					Preferences p = new Preferences(e, f);
					if (!p.equals(Preferences.DEFAULT)) {
						rows.add(row(dataset, model, k, label(p) + " (chosen)", p, chosen, bestBaseline, bestName,
								bestSelection));
					}
				}
			}
		}
		return rows;
	}

	private static Row row(BenchmarkDataset d, ConceptModel model, int k, String setting, Preferences p,
			OptimizerConfig config, SelectionMetrics baseline, String baselineName, List<Candidate> baselineSelection) {
		List<Candidate> sel = new OptimizerSelector(p, config).select(d, k);
		SelectionMetrics m = SelectionMetrics.of(d, model, k, sel);
		int overlap = (int) sel.stream().filter(baselineSelection::contains).count();
		return new Row(d.topic(), setting, k, m.weightedCoverage(), baseline.weightedCoverage(), baselineName,
				m.relevanceMean(), medianPercentile(sel), m.citationsMedian(), m.yearMedian(), m.openAccessShare(), overlap);
	}

	/** Median OpenAlex normalized citation percentile of a selection (known values only). */
	public static Double medianPercentile(List<Candidate> selection) {
		List<Double> v = selection.stream().map(Candidate::citationPercentile).filter(x -> x != null).sorted().toList();
		return v.isEmpty() ? null : v.get(v.size() / 2);
	}

	static String label(Preferences p) {
		String e = p.experience().name().charAt(0) + p.experience().name().substring(1).toLowerCase(Locale.ROOT);
		String f = p.focus().name().charAt(0) + p.focus().name().substring(1).toLowerCase(Locale.ROOT);
		return e + "/" + f;
	}

	public static String markdown(List<Row> rows) {
		StringBuilder sb = new StringBuilder("# Optimizer sensitivity sweep (D12)\n\n")
				.append("Weighted concept coverage of the optimizer vs the best baseline for the same dataset and budget. ")
				.append("Δ = optimizer − best baseline. Overlap = papers shared with the best baseline's selection.\n");
		String topic = null;
		for (Row r : rows) {
			if (!r.topic().equals(topic)) {
				topic = r.topic();
				sb.append("\n## ").append(topic).append("\n\n")
						.append("| Setting | k | Coverage | Best baseline | Δ | Relevance mean | Citation percentile median | ")
						.append("Citations median | Year median | OA share | Overlap |\n")
						.append("|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|\n");
			}
			sb.append(String.format(Locale.ROOT, "| %s | %d | %.3f | %.3f (%s) | %+.3f | %.2f | %s | %s | %s | %.2f | %d/%d |%n",
					r.setting(), r.budget(), r.coverage(), r.bestBaselineCoverage(), r.bestBaseline(),
					r.coverage() - r.bestBaselineCoverage(), r.relevanceMean(),
					r.citationPercentileMedian() == null ? "–" : String.format(Locale.ROOT, "%.2f", r.citationPercentileMedian()),
					r.citationsMedian() == null ? "–" : r.citationsMedian(), r.yearMedian() == null ? "–" : r.yearMedian(),
					r.openAccessShare(), r.overlapWithBestBaseline(), r.budget()));
		}
		return sb.toString().replace("\r\n", "\n");
	}

}
