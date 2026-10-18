package dev.researchpath.benchmark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.researchpath.benchmark.BenchmarkDataset.Candidate;
import dev.researchpath.concept.ConceptModel;
import dev.researchpath.concept.ConceptModel.Representation;
import dev.researchpath.concept.ConceptPolicy;
import dev.researchpath.optimizer.GreedyOptimizer;
import dev.researchpath.optimizer.OptimizerCandidate;
import dev.researchpath.optimizer.OptimizerConfig;
import dev.researchpath.optimizer.Preferences;
import dev.researchpath.optimizer.Preferences.Experience;
import dev.researchpath.optimizer.Preferences.Focus;

/**
 * M14 ablation: which parts of the optimizer matter. Every variant selects from the same frozen pool and budget;
 * every selection is measured with the same reference concept model (combined topics + keywords) and the original,
 * unmodified candidate data, so denominators are identical across variants. Offline and deterministic.
 */
public final class Ablation {

	private Ablation() {
	}

	public enum Variant {
		DEFAULT("Default: combined concepts, coverage + quality (β = 4)"),
		TOPICS_ONLY("Coverage of OpenAlex topics only"),
		KEYWORDS_ONLY("Coverage of OpenAlex keywords only"),
		COVERAGE_ONLY("Coverage only (β = 0, no quality term)"),
		NO_COVERAGE("Quality only (no coverage term)"),
		NO_INFLUENCE("Citation percentile hidden (neutral)"),
		RECENT("Recent focus"),
		RECENT_NO_YEAR("Recent focus with publication years hidden");

		public final String description;

		Variant(String description) {
			this.description = description;
		}
	}

	/** @param overlapWithDefault papers shared with the DEFAULT selection for the same dataset and budget */
	public record Row(String topic, String variant, int budget, SelectionMetrics metrics, Double percentileMedian,
			int overlapWithDefault, long selectionMicros, List<String> selectedIds) {
	}

	public static List<Row> run(BenchmarkDataset d, List<Integer> budgets) {
		ConceptModel reference = SelectionMetrics.conceptModel(d);
		Map<String, Candidate> byId = new HashMap<>();
		d.candidates().forEach(c -> byId.put(c.openalexId(), c));
		List<Row> rows = new ArrayList<>();
		for (int k : budgets) {
			Set<String> defaultIds = Set.of();
			for (Variant v : Variant.values()) {
				long start = System.nanoTime();
				List<String> ids = select(d, v, k);
				long micros = (System.nanoTime() - start) / 1000;
				if (v == Variant.DEFAULT) {
					defaultIds = new HashSet<>(ids);
				}
				List<Candidate> selected = ids.stream().map(byId::get).toList();
				Set<String> def = defaultIds;
				rows.add(new Row(d.topic(), v.name(), k, SelectionMetrics.of(d, reference, k, selected),
						SensitivitySweep.medianPercentile(selected), (int) ids.stream().filter(def::contains).count(), micros,
						ids));
			}
			Selector best = null;
			double bestCoverage = -1;
			for (Selector b : Baselines.ALL) {
				double c = SelectionMetrics.of(d, reference, k, b.select(d, k)).weightedCoverage();
				if (c > bestCoverage) {
					bestCoverage = c;
					best = b;
				}
			}
			List<Candidate> bs = best.select(d, k);
			Set<String> def = defaultIds;
			rows.add(new Row(d.topic(), "BEST_BASELINE (" + best.name() + ")", k, SelectionMetrics.of(d, reference, k, bs),
					SensitivitySweep.medianPercentile(bs), (int) bs.stream().filter(c -> def.contains(c.openalexId())).count(),
					0, bs.stream().map(Candidate::openalexId).toList()));
		}
		return rows;
	}

	/** Selected ids for a variant; the pool, budget and tie-breaks are otherwise those of the product optimizer. */
	static List<String> select(BenchmarkDataset d, Variant v, int k) {
		List<Candidate> candidates = d.candidates().stream().map(c -> switch (v) {
			case NO_INFLUENCE -> new Candidate(c.openalexId(), c.providerRank(), c.title(), c.publicationYear(), c.workType(),
					c.doi(), c.citedByCount(), c.fwci(), null, c.sourceLink(), c.sourceLinkKind(), c.relevanceCoverage(),
					c.titleTermShare(), c.concepts(), c.referencedWorkIds());
			case RECENT_NO_YEAR -> new Candidate(c.openalexId(), c.providerRank(), c.title(), null, c.workType(), c.doi(),
					c.citedByCount(), c.fwci(), c.citationPercentile(), c.sourceLink(), c.sourceLinkKind(),
					c.relevanceCoverage(), c.titleTermShare(), c.concepts(), c.referencedWorkIds());
			default -> c;
		}).toList();
		Representation representation = switch (v) {
			case TOPICS_ONLY -> Representation.TOPIC_ONLY;
			case KEYWORDS_ONLY -> Representation.KEYWORD_ONLY;
			default -> Representation.COMBINED;
		};
		ConceptModel model = ConceptPolicy.build(d.topic(), candidates.stream()
				.map(c -> new ConceptPolicy.Input(c.providerRank(), c.openalexId(), c.concepts())).toList(), representation);
		Map<String, Double> weights = new HashMap<>();
		if (v != Variant.NO_COVERAGE) {
			model.included().forEach(e -> weights.put(e.key(), e.weight()));
		}
		OptimizerConfig config = v == Variant.COVERAGE_ONLY
				? new OptimizerConfig(0, false, OptimizerConfig.DEFAULT.experienceWeight())
				: OptimizerConfig.DEFAULT;
		Preferences prefs = v == Variant.RECENT || v == Variant.RECENT_NO_YEAR
				? new Preferences(Experience.INTERMEDIATE, Focus.RECENT)
				: Preferences.DEFAULT;
		List<OptimizerCandidate> pool = candidates.stream()
				.map(c -> OptimizerSelector.candidate(c, model.paperConcepts().getOrDefault((long) c.providerRank(), Map.of())))
				.toList();
		return GreedyOptimizer.select(pool, weights, k, prefs, config).selectedIds();
	}

	public static String markdown(List<Row> rows) {
		StringBuilder sb = new StringBuilder("# Ablation (M14)\n\n")
				.append("Each variant changes one part of the optimizer and selects from the same frozen pool and budget. ")
				.append("All selections are measured with the same reference concept model (combined topics + keywords, ")
				.append("concept policy v").append(ConceptPolicy.VERSION).append(") and the original candidate data. ")
				.append("Coverage is weighted concept coverage of the eligible pool (the optimizer's own target for the ")
				.append("combined variants, so partly circular). Δ = variant − default. Overlap = papers shared with the ")
				.append("default selection. Variants:\n\n");
		for (Variant v : Variant.values()) {
			sb.append("- `").append(v.name()).append("`: ").append(v.description).append('\n');
		}
		sb.append("- `BEST_BASELINE`: the simple ranking with the highest coverage for that dataset and budget (M08)\n");
		String topic = null;
		Map<String, Double> defaultCoverage = new HashMap<>();
		for (Row r : rows) {
			if (r.variant().equals("DEFAULT")) {
				defaultCoverage.put(r.topic() + "|" + r.budget(), r.metrics().weightedCoverage());
			}
		}
		for (Row r : rows) {
			if (!r.topic().equals(topic)) {
				topic = r.topic();
				sb.append("\n## ").append(topic).append("\n\n")
						.append("| Variant | k | Coverage | Δ | Relevance mean | Percentile median | Citations median | ")
						.append("Year median | Redundancy | Overlap | Time |\n")
						.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
			}
			SelectionMetrics m = r.metrics();
			double delta = m.weightedCoverage() - defaultCoverage.get(r.topic() + "|" + r.budget());
			sb.append(String.format(Locale.ROOT, "| %s | %d | %.3f | %+.3f | %.2f | %s | %s | %s | %.3f | %d/%d | %s |%n",
					r.variant(), r.budget(), m.weightedCoverage(), delta, m.relevanceMean(),
					r.percentileMedian() == null ? "–" : String.format(Locale.ROOT, "%.2f", r.percentileMedian()),
					m.citationsMedian() == null ? "–" : m.citationsMedian(), m.yearMedian() == null ? "–" : m.yearMedian(),
					m.redundancyJaccard(), r.overlapWithDefault(), m.selectedCount(),
					r.selectionMicros() == 0 ? "–" : (r.selectionMicros() / 1000) + " ms"));
		}
		return sb.toString().replace("\r\n", "\n");
	}

}
