package dev.researchpath.sequence;

import java.util.List;

import dev.researchpath.optimizer.GreedyOptimizer;

/**
 * One selected paper as the sequence builder sees it: frozen metadata plus the optimizer's recorded decision.
 * Unknown values are null and are reported as uncertainty, never filled in.
 *
 * @param concepts the paper's included coverage concepts (key, label, strength)
 * @param referencedWorkIds OpenAlex ids this work cites (only links to other selected papers are used)
 * @param sourceLink provider link; re-validated before it is returned
 * @param abstractAvailable null when not known for this input (e.g. benchmark datasets carry no abstracts)
 */
public record PathPaper(String id, String title, Integer year, String workType, Integer citedByCount,
		Double citationPercentile, String sourceLink, String sourceLinkKind, Boolean abstractAvailable,
		List<Concept> concepts, List<String> referencedWorkIds, GreedyOptimizer.Decision decision) {

	public PathPaper {
		concepts = List.copyOf(concepts);
		referencedWorkIds = List.copyOf(referencedWorkIds);
	}

	public record Concept(String key, String label, double strength) {
	}

}
