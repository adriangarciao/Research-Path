package dev.researchpath.path;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import dev.researchpath.benchmark.BenchmarkDataset;
import dev.researchpath.benchmark.SelectionMetrics;
import dev.researchpath.concept.ConceptModel;
import dev.researchpath.publication.NormalizedWork;
import dev.researchpath.sequence.PathPaper;
import dev.researchpath.sequence.ReadingPath;
import dev.researchpath.snapshot.FrozenCandidate;
import dev.researchpath.snapshot.SnapshotSummary;

/**
 * The complete, self-contained result of one revision, stored as-is and returned unchanged on every retrieval
 * (blueprint §5.6). All text is plain text; clients render it as text.
 */
public final class PathDocument {

	public static final int VERSION = 1;

	static final int MAX_AUTHORS = 5;

	static final int MAX_COVERAGE_CONCEPTS = 30;

	public static final String ACCESS_NOTICE = "Anyone with this link can view this path. Paths are not private and "
			+ "are not tied to an account.";

	public static final String COVERAGE_DENOMINATOR = "Concepts carried by the eligible candidates examined for this "
			+ "topic (weighted by how many candidates carry them); not a measure of everything there is to know.";

	private PathDocument() {
	}

	public record Result(int documentVersion, String pathId, int revision, Integer parentRevision, String topic,
			Instant createdAt, Options options, Snapshot snapshot, Map<String, Integer> versions, int requestedCount,
			int selectedCount, Coverage coverage, List<ReadingPath.Stage> stages, List<Paper> papers,
			List<String> warnings, List<String> disclosures, String access) {
	}

	public record Options(String experience, String focus, int budget) {
	}

	/**
	 * @param candidatesRetrieved works stored from the provider for this topic
	 * @param eligibleCandidates eligible, non-duplicate candidates the selection chose from (the coverage pool)
	 */
	public record Snapshot(Instant retrievedAt, int candidatesRetrieved, int eligibleCandidates) {
	}

	public record Coverage(double weightedCoverage, int conceptsCovered, int conceptsInPool, String denominator,
			List<CoveredConcept> concepts, int conceptsNotShown) {
	}

	/** @param coveredBy positions of the path papers carrying this concept */
	public record CoveredConcept(String label, String kind, double weight, List<Integer> coveredBy) {
	}

	public record Paper(int position, int stage, String openalexId, String title, List<String> authors,
			int authorCount, Integer year, String workType, Integer citedByCount, String link, String linkKind,
			List<String> tags, List<ReadingPath.Explanation> reasons, List<Integer> cites, List<Integer> citedBy,
			List<ReadingPath.Flag> flags, int selectionStep) {
	}

	static Result build(String pathId, int revision, Integer parentRevision, Instant createdAt, SnapshotSummary summary,
			BenchmarkDataset pool, PathOptions options, PathEngine.Generated g, Map<String, FrozenCandidate> frozen) {
		ReadingPath path = g.path();
		List<Paper> papers = path.items().stream().map(i -> paper(i, frozen.get(i.id()).work())).toList();
		List<String> warnings = Stream.concat(g.selection().warnings().stream(), path.warnings().stream()).toList();
		return new Result(VERSION, pathId, revision, parentRevision, summary.topic(), createdAt,
				new Options(options.experience().name(), options.focus().name(), options.budget()),
				new Snapshot(summary.createdAt(), summary.storedCount(), pool.candidates().size()),
				PathEngine.versions(), options.budget(), papers.size(), coverage(pool, g), path.stages(), papers,
				warnings, path.disclosures(), ACCESS_NOTICE);
	}

	private static Paper paper(ReadingPath.Item i, NormalizedWork w) {
		List<String> authors = w.authors().stream().sorted(Comparator.comparingInt(NormalizedWork.Author::order))
				.map(NormalizedWork.Author::displayName).filter(n -> n != null && !n.isBlank()).limit(MAX_AUTHORS).toList();
		return new Paper(i.position(), i.stage(), i.id(), i.title(), authors, w.authorCount(), i.year(), i.workType(),
				w.citedByCount(), i.link(), i.linkKind(), i.tags(), i.reasons(), i.cites(), i.citedBy(), i.flags(),
				i.selectionStep());
	}

	private static Coverage coverage(BenchmarkDataset pool, PathEngine.Generated g) {
		Map<String, BenchmarkDataset.Candidate> byId = new HashMap<>();
		pool.candidates().forEach(c -> byId.put(c.openalexId(), c));
		List<BenchmarkDataset.Candidate> selected = g.selection().selectedIds().stream().map(byId::get).toList();
		SelectionMetrics m = SelectionMetrics.of(pool, g.model(), g.selection().budget(), selected);

		Map<String, Integer> position = new HashMap<>();
		g.path().items().forEach(i -> position.put(i.id(), i.position()));
		Map<String, List<Integer>> carriers = new HashMap<>();
		for (PathPaper p : g.papers()) {
			for (PathPaper.Concept c : p.concepts()) {
				carriers.computeIfAbsent(c.key(), k -> new ArrayList<>()).add(position.get(p.id()));
			}
		}
		List<CoveredConcept> covered = g.model().included().stream()
				.filter(e -> carriers.containsKey(e.key()))
				.sorted(Comparator.comparingDouble(ConceptModel.Entry::weight).reversed().thenComparing(ConceptModel.Entry::label))
				.map(e -> new CoveredConcept(e.label(), e.kind().name(), Math.round(e.weight() * 1e6) / 1e6,
						carriers.get(e.key()).stream().sorted().toList()))
				.toList();
		List<CoveredConcept> shown = covered.subList(0, Math.min(MAX_COVERAGE_CONCEPTS, covered.size()));
		return new Coverage(m.weightedCoverage(), m.conceptsCovered(), m.conceptUniverseSize(), COVERAGE_DENOMINATOR,
				shown, covered.size() - shown.size());
	}

}
