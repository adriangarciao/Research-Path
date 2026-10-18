package dev.researchpath.benchmark;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dev.researchpath.benchmark.BenchmarkDataset.Candidate;
import dev.researchpath.concept.ConceptModel;
import dev.researchpath.optimizer.GreedyOptimizer;
import dev.researchpath.optimizer.OptimizerConfig;
import dev.researchpath.optimizer.Preferences;

/** Human-review artifact (playbook M09 D12/D13): optimizer picks with reasons next to the best baseline. */
public final class SideBySide {

	private SideBySide() {
	}

	public static String markdown(List<BenchmarkDataset> datasets, int k) {
		StringBuilder sb = new StringBuilder("# Optimizer vs best baseline — side by side (k = ").append(k).append(")\n\n")
				.append("Optimizer: default preferences (Intermediate/Balanced) and default configuration. Reasons and ")
				.append("gains are the optimizer's recorded decisions; \"new concepts\" are concept labels the paper is the ")
				.append("first selected paper to carry. ★ = also chosen by the best baseline.\n");
		for (BenchmarkDataset d : datasets) {
			ConceptModel model = SelectionMetrics.conceptModel(d);
			Map<String, String> labels = new HashMap<>();
			model.included().forEach(e -> labels.put(e.key(), e.label()));
			Map<String, Candidate> byId = d.candidates().stream().collect(Collectors.toMap(Candidate::openalexId, c -> c));

			Selector best = null;
			double bestCoverage = -1;
			for (Selector b : Baselines.ALL) {
				double cov = SelectionMetrics.of(d, model, k, b.select(d, k)).weightedCoverage();
				if (cov > bestCoverage) {
					bestCoverage = cov;
					best = b;
				}
			}
			Set<String> baselineIds = best.select(d, k).stream().map(Candidate::openalexId).collect(Collectors.toSet());
			GreedyOptimizer.Result r = new OptimizerSelector(Preferences.DEFAULT, OptimizerConfig.DEFAULT).optimize(d, k);

			sb.append("\n## ").append(d.topic()).append("\n\n### Optimizer\n\n")
					.append("| # | Paper | Year | Citations (percentile) | Reason | Gain (coverage + quality) | New concepts |\n")
					.append("|---:|---|---:|---|---|---|---|\n");
			for (GreedyOptimizer.Decision dec : r.decisions()) {
				Candidate c = byId.get(dec.id());
				String concepts = dec.newConcepts().stream().map(key -> labels.getOrDefault(key, key))
						.collect(Collectors.joining(", "));
				sb.append(String.format(Locale.ROOT, "| %d | %s%s (%s) | %s | %s | %s | %.3f + %.3f | %s |%n", dec.step(),
						baselineIds.contains(c.openalexId()) ? "★ " : "", escape(c.title()), c.openalexId(),
						c.publicationYear() == null ? "–" : c.publicationYear(), citations(c), dec.reason(),
						dec.coverageGain(), dec.qualityGain(), concepts.isEmpty() ? "–" : concepts));
			}
			sb.append("\n### Best baseline: ").append(best.name()).append("\n\n| # | Paper | Year | Citations (percentile) |\n")
					.append("|---:|---|---:|---|\n");
			int i = 1;
			for (Candidate c : best.select(d, k)) {
				sb.append(String.format(Locale.ROOT, "| %d | %s (%s) | %s | %s |%n", i++, escape(c.title()), c.openalexId(),
						c.publicationYear() == null ? "–" : c.publicationYear(), citations(c)));
			}
		}
		return sb.toString().replace("\r\n", "\n");
	}

	private static String citations(Candidate c) {
		String count = c.citedByCount() == null ? "?" : c.citedByCount().toString();
		String pct = c.citationPercentile() == null ? "?" : String.format(Locale.ROOT, "%.2f", c.citationPercentile());
		return count + " (" + pct + ")";
	}

	private static String escape(String title) {
		return title.replace("|", "\\|").replace("\n", " ");
	}

}
