package dev.researchpath.path;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.researchpath.benchmark.BenchmarkDataset;
import dev.researchpath.benchmark.OptimizerSelector;
import dev.researchpath.benchmark.ReadingPaths;
import dev.researchpath.benchmark.SelectionMetrics;
import dev.researchpath.concept.ConceptModel;
import dev.researchpath.concept.ConceptPolicy;
import dev.researchpath.optimizer.GreedyOptimizer;
import dev.researchpath.optimizer.OptimizerConfig;
import dev.researchpath.sequence.PathPaper;
import dev.researchpath.sequence.ReadingPath;
import dev.researchpath.sequence.SequenceBuilder;

/**
 * Selection followed by ordering on one frozen candidate pool: the same code the offline benchmarks run, so a
 * product path and a benchmark run on the same snapshot agree. Pure and deterministic; no provider or database.
 */
public final class PathEngine {

	private PathEngine() {
	}

	public record Generated(GreedyOptimizer.Result selection, ReadingPath path, ConceptModel model,
			List<PathPaper> papers) {
	}

	/** @param abstractAvailable OpenAlex id → whether the frozen record had an abstract (absent = unknown) */
	public static Generated run(BenchmarkDataset pool, PathOptions options, Map<String, Boolean> abstractAvailable) {
		ConceptModel model = SelectionMetrics.conceptModel(pool);
		GreedyOptimizer.Result selection = new OptimizerSelector(options.preferences(), OptimizerConfig.DEFAULT)
				.optimize(pool, options.budget());
		List<PathPaper> papers = ReadingPaths.papers(pool, model, selection).stream()
				.map(p -> new PathPaper(p.id(), p.title(), p.year(), p.workType(), p.citedByCount(),
						p.citationPercentile(), p.sourceLink(), p.sourceLinkKind(), abstractAvailable.get(p.id()),
						p.concepts(), p.referencedWorkIds(), p.decision()))
				.toList();
		ReadingPath path = SequenceBuilder.build(papers, options.preferences(), OptimizerConfig.DEFAULT);
		return new Generated(selection, path, model, papers);
	}

	/** Versions of every rule that shapes a result; part of the request fingerprint. */
	public static Map<String, Integer> versions() {
		Map<String, Integer> v = new LinkedHashMap<>();
		v.put("optimizer", GreedyOptimizer.VERSION);
		v.put("optimizerConfig", OptimizerConfig.VERSION);
		v.put("conceptPolicy", ConceptPolicy.VERSION);
		v.put("sequence", SequenceBuilder.VERSION);
		v.put("document", PathDocument.VERSION);
		return v;
	}

}
