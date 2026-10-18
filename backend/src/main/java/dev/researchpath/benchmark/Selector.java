package dev.researchpath.benchmark;

import java.util.List;

/**
 * Chooses up to {@code budget} candidates from a frozen dataset. Implementations must be deterministic
 * (stable tie-breaks) and must never return more than {@code budget} or repeat a candidate.
 */
public interface Selector {

	String name();

	int version();

	List<BenchmarkDataset.Candidate> select(BenchmarkDataset dataset, int budget);

}
