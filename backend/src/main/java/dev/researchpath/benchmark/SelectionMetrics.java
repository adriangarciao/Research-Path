package dev.researchpath.benchmark;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.researchpath.benchmark.BenchmarkDataset.Candidate;
import dev.researchpath.concept.ConceptModel;
import dev.researchpath.concept.ConceptModel.Entry;
import dev.researchpath.concept.ConceptPolicy;

/**
 * Metrics for one selection, each with an explicit denominator (playbook M08). These are candidate-set
 * proxies, not measures of learning.
 *
 * @param weightedCoverage weight of concepts covered by ≥ 1 selected paper ÷ total weight of the pool's concept
 *     universe (combined representation, {@link ConceptPolicy})
 * @param relevanceMin minimum relevance-gate coverage among selected (all are eligible by construction)
 * @param redundancyJaccard mean pairwise Jaccard of selected papers' concept sets (0 = no overlap)
 * @param conceptRepeats Σ over covered concepts of (papers covering it − 1)
 * @param citationsKnownShare selected papers with a known citation count ÷ selected
 * @param citationsMedian median citations over selected papers with known counts (null if none)
 * @param topQuartileCitationShare selected papers at or above the pool's 75th citation percentile ÷ selected
 * @param sourceLinkShare selected papers with a safe source link ÷ selected
 * @param openAccessShare selected papers whose preferred link is open access ÷ selected
 * @param duplicateDoiPairs pairs of selected papers sharing a non-null DOI (expected 0)
 */
public record SelectionMetrics(
		int budget,
		int selectedCount,
		int conceptUniverseSize,
		int conceptsCovered,
		double weightedCoverage,
		double relevanceMin,
		double relevanceMean,
		double titleTermShareMean,
		double redundancyJaccard,
		int conceptRepeats,
		double citationsKnownShare,
		Double citationsMedian,
		double topQuartileCitationShare,
		Integer yearMin,
		Integer yearMedian,
		Integer yearMax,
		double sourceLinkShare,
		double openAccessShare,
		int duplicateDoiPairs) {

	/** Concept model of the whole pool, built once per dataset and reused for every selection. */
	public static ConceptModel conceptModel(BenchmarkDataset dataset) {
		List<ConceptPolicy.Input> inputs = dataset.candidates().stream()
				.map(c -> new ConceptPolicy.Input(c.providerRank(), c.openalexId(), c.concepts()))
				.toList();
		return ConceptPolicy.build(dataset.topic(), inputs, ConceptModel.Representation.COMBINED);
	}

	public static SelectionMetrics of(BenchmarkDataset dataset, ConceptModel model, int budget,
			List<Candidate> selected) {
		int n = selected.size();
		Map<String, Double> weights = new HashMap<>();
		model.included().forEach(e -> weights.put(e.key(), e.weight()));
		double totalWeight = model.included().stream().mapToDouble(Entry::weight).sum();

		Map<String, Integer> coverCount = new HashMap<>();
		List<Set<String>> sets = new ArrayList<>();
		for (Candidate c : selected) {
			Set<String> s = model.paperConcepts().getOrDefault((long) c.providerRank(), Map.of()).keySet();
			sets.add(s);
			s.forEach(k -> coverCount.merge(k, 1, Integer::sum));
		}
		double covered = coverCount.keySet().stream().mapToDouble(k -> weights.getOrDefault(k, 0.0)).sum();
		int repeats = coverCount.values().stream().mapToInt(v -> v - 1).sum();

		List<Integer> knownCitations = selected.stream().map(Candidate::citedByCount).filter(x -> x != null)
				.sorted().toList();
		Integer q3 = percentile75(dataset.candidates().stream().map(Candidate::citedByCount).filter(x -> x != null)
				.sorted().toList());
		long topQuartile = q3 == null ? 0
				: selected.stream().filter(c -> c.citedByCount() != null && c.citedByCount() >= q3).count();
		List<Integer> years = selected.stream().map(Candidate::publicationYear).filter(x -> x != null).sorted().toList();

		return new SelectionMetrics(budget, n, model.included().size(), coverCount.size(),
				round(totalWeight == 0 ? 0 : covered / totalWeight),
				round(selected.stream().mapToDouble(Candidate::relevanceCoverage).min().orElse(0)),
				round(selected.stream().mapToDouble(Candidate::relevanceCoverage).average().orElse(0)),
				round(selected.stream().mapToDouble(Candidate::titleTermShare).average().orElse(0)),
				round(meanJaccard(sets)), repeats,
				round(share(knownCitations.size(), n)),
				knownCitations.isEmpty() ? null : median(knownCitations),
				round(share((int) topQuartile, n)),
				years.isEmpty() ? null : years.getFirst(),
				years.isEmpty() ? null : (int) Math.round(median(years)),
				years.isEmpty() ? null : years.getLast(),
				round(share((int) selected.stream().filter(c -> c.sourceLink() != null).count(), n)),
				round(share((int) selected.stream().filter(c -> "OPEN_ACCESS".equals(c.sourceLinkKind())).count(), n)),
				duplicateDoiPairs(selected));
	}

	private static double meanJaccard(List<Set<String>> sets) {
		double sum = 0;
		int pairs = 0;
		for (int i = 0; i < sets.size(); i++) {
			for (int j = i + 1; j < sets.size(); j++) {
				Set<String> union = new HashSet<>(sets.get(i));
				union.addAll(sets.get(j));
				if (!union.isEmpty()) {
					Set<String> inter = new HashSet<>(sets.get(i));
					inter.retainAll(sets.get(j));
					sum += inter.size() / (double) union.size();
				}
				pairs++;
			}
		}
		return pairs == 0 ? 0 : sum / pairs;
	}

	private static int duplicateDoiPairs(Collection<Candidate> selected) {
		Map<String, Integer> byDoi = new HashMap<>();
		selected.stream().map(Candidate::doi).filter(d -> d != null).forEach(d -> byDoi.merge(d, 1, Integer::sum));
		return byDoi.values().stream().mapToInt(v -> v * (v - 1) / 2).sum();
	}

	private static Integer percentile75(List<Integer> sorted) {
		if (sorted.isEmpty()) {
			return null;
		}
		return sorted.get((int) Math.ceil(0.75 * sorted.size()) - 1);
	}

	private static double median(List<Integer> sorted) {
		int n = sorted.size();
		return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
	}

	private static double share(int part, int whole) {
		return whole == 0 ? 0 : part / (double) whole;
	}

	private static double round(double v) {
		return Math.round(v * 1000) / 1000.0;
	}

}
