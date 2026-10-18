package dev.researchpath.benchmark;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.researchpath.benchmark.BenchmarkDataset.Candidate;
import dev.researchpath.concept.ConceptModel;
import dev.researchpath.concept.ConceptPolicy;
import dev.researchpath.relevance.RelevancePolicy;

/**
 * Runs selectors over a frozen dataset for each budget (playbook M08). Pure and offline: no network,
 * database or Spring. Results are deterministic apart from {@code ranAt} and {@code selectionNanos}.
 */
public final class BenchmarkRunner {

	public static final List<Integer> BUDGETS = List.of(5, 10, 15, 20);

	private BenchmarkRunner() {
	}

	/** Everything needed to reproduce and compare a run. */
	public record Run(
			String datasetFile,
			String datasetSha256,
			UUID snapshotId,
			String topic,
			int candidateCount,
			int datasetFormatVersion,
			BenchmarkDataset.Policies snapshotPolicies,
			int conceptPolicyVersion,
			int textPipelineVersion,
			Instant ranAt,
			String environment,
			List<Result> results) {
	}

	public record Result(String selector, int selectorVersion, int budget, List<String> selectedIds,
			SelectionMetrics metrics, long selectionNanos) {
	}

	public static Run run(BenchmarkDataset dataset, String datasetFile, String sha256, List<Selector> selectors,
			List<Integer> budgets, Clock clock) {
		ConceptModel model = SelectionMetrics.conceptModel(dataset);
		List<Result> results = new ArrayList<>();
		for (Selector selector : selectors) {
			for (int budget : budgets) {
				long start = System.nanoTime();
				List<Candidate> selected = selector.select(dataset, budget);
				long nanos = System.nanoTime() - start;
				if (selected.size() > budget || selected.stream().distinct().count() != selected.size()) {
					throw new IllegalStateException(selector.name() + " violated budget/uniqueness at k=" + budget);
				}
				results.add(new Result(selector.name(), selector.version(), budget,
						selected.stream().map(Candidate::openalexId).toList(),
						SelectionMetrics.of(dataset, model, budget, selected), nanos));
			}
		}
		return new Run(datasetFile, sha256, dataset.snapshotId(), dataset.topic(), dataset.candidates().size(),
				dataset.formatVersion(), dataset.policies(), ConceptPolicy.VERSION, RelevancePolicy.VERSION,
				clock.instant(), environment(), results);
	}

	static String environment() {
		return "Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + "), "
				+ System.getProperty("os.name") + " " + System.getProperty("os.arch") + ", "
				+ Runtime.getRuntime().availableProcessors() + " CPUs";
	}

}
