package dev.researchpath.benchmark;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.researchpath.benchmark.BenchmarkDataset.Candidate;
import dev.researchpath.concept.ConceptModel;
import dev.researchpath.optimizer.GreedyOptimizer;
import dev.researchpath.optimizer.OptimizerCandidate;
import dev.researchpath.optimizer.OptimizerConfig;
import dev.researchpath.optimizer.Preferences;
import dev.researchpath.sequence.OverviewCue;

/**
 * Runs {@link GreedyOptimizer} on a frozen dataset, with the same concept model the metrics use, so the
 * optimizer is compared with the baselines on identical inputs.
 */
public final class OptimizerSelector implements Selector {

	private final Preferences preferences;
	private final OptimizerConfig config;
	private final String name;

	public OptimizerSelector(Preferences preferences, OptimizerConfig config) {
		this.preferences = preferences;
		this.config = config;
		this.name = "OPTIMIZER";
	}

	public OptimizerSelector(Preferences preferences, OptimizerConfig config, String name) {
		this.preferences = preferences;
		this.config = config;
		this.name = name;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public int version() {
		return GreedyOptimizer.VERSION;
	}

	@Override
	public List<Candidate> select(BenchmarkDataset dataset, int budget) {
		GreedyOptimizer.Result result = optimize(dataset, budget);
		Map<String, Candidate> byId = new HashMap<>();
		dataset.candidates().forEach(c -> byId.put(c.openalexId(), c));
		return result.selectedIds().stream().map(byId::get).toList();
	}

	/** Full optimizer result (decisions, contributions, reasons) for review. */
	public GreedyOptimizer.Result optimize(BenchmarkDataset dataset, int budget) {
		ConceptModel model = SelectionMetrics.conceptModel(dataset);
		Map<String, Double> weights = new HashMap<>();
		model.included().forEach(e -> weights.put(e.key(), e.weight()));
		List<OptimizerCandidate> pool = dataset.candidates().stream()
				.map(c -> candidate(c, model.paperConcepts().getOrDefault((long) c.providerRank(), Map.of())))
				.toList();
		return GreedyOptimizer.select(pool, weights, budget, preferences, config);
	}

	static OptimizerCandidate candidate(Candidate c, Map<String, Double> concepts) {
		// Weak, documented accessibility cue (D09 OPEN): review work type or survey-like title words.
		boolean overview = OverviewCue.of(c.workType(), c.title()) != null;
		return new OptimizerCandidate(c.openalexId(), c.providerRank(), concepts,
				(c.relevanceCoverage() + c.titleTermShare()) / 2, c.citationPercentile(), c.publicationYear(), overview,
				c.citedByCount());
	}

}
