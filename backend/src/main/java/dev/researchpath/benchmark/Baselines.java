package dev.researchpath.benchmark;

import java.util.Comparator;
import java.util.List;

import dev.researchpath.benchmark.BenchmarkDataset.Candidate;

/**
 * Simple ranking baselines over the same relevance-eligible, de-duplicated candidates (playbook M08).
 * Each is "sort, then take the first k" with a documented, deterministic tie-break.
 */
public final class Baselines {

	private Baselines() {
	}

	/** Top-k by individual topical relevance: gate coverage, then title-term share, then provider rank. */
	public static final Selector RELEVANCE = sorted("RELEVANCE", 1, Comparator
			.comparingDouble((Candidate c) -> -c.relevanceCoverage())
			.thenComparingDouble(c -> -c.titleTermShare())
			.thenComparingInt(Candidate::providerRank)
			.thenComparing(Candidate::openalexId));

	/**
	 * Top-k by raw citation count after the same relevance gate. Policy for unknown counts: they rank
	 * after every known count (unknown is neither best nor zero). No age/field normalization in v1.
	 */
	public static final Selector CITATION = sorted("CITATION", 1, Comparator
			.comparing((Candidate c) -> c.citedByCount() == null)
			.thenComparingInt(c -> c.citedByCount() == null ? 0 : -c.citedByCount())
			.thenComparingInt(Candidate::providerRank)
			.thenComparing(Candidate::openalexId));

	/** Top-k in OpenAlex search order (the provider's relevance ranking). */
	public static final Selector PROVIDER_ORDER = sorted("PROVIDER_ORDER", 1, Comparator
			.comparingInt(Candidate::providerRank)
			.thenComparing(Candidate::openalexId));

	public static final List<Selector> ALL = List.of(RELEVANCE, CITATION, PROVIDER_ORDER);

	private static Selector sorted(String name, int version, Comparator<Candidate> order) {
		return new Selector() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public int version() {
				return version;
			}

			@Override
			public List<Candidate> select(BenchmarkDataset dataset, int budget) {
				if (budget < 0) {
					throw new IllegalArgumentException("budget must be >= 0");
				}
				return dataset.candidates().stream().distinct().sorted(order).limit(budget).toList();
			}
		};
	}

}
