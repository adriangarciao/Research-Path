package dev.researchpath.concept;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.researchpath.concept.ConceptModel.Entry;
import dev.researchpath.concept.ConceptModel.Representation;
import dev.researchpath.concept.ConceptModel.Stats;
import dev.researchpath.publication.NormalizedWork.ConceptAssociation;
import dev.researchpath.publication.NormalizedWork.ConceptKind;
import dev.researchpath.relevance.RelevancePolicy;

/**
 * Conservative concept representation (decisions D03/D08). Pure logic over frozen candidates.
 *
 * <p>Concepts are provider topics/keywords at or above {@value #MIN_CONFIDENCE} confidence, merged only
 * when their normalized labels are identical (case, punctuation, plurals, known acronyms). Excluded,
 * in this order: labels naming an unrequested aspect; labels made only of query and generic words;
 * labels on more than {@value #MAX_SUPPORT_SHARE} of candidates; one-offs (support below
 * {@value #MIN_SUPPORT}); anything beyond the {@value #MAX_CONCEPTS} most supported. Weight =
 * log(1 + support), normalized to sum to 1. All thresholds are PROVISIONAL and versioned.
 */
public final class ConceptPolicy {

	public static final int VERSION = 1;
	public static final double MIN_CONFIDENCE = 0.5;
	public static final int MIN_SUPPORT = 2;
	public static final double MAX_SUPPORT_SHARE = 0.5;
	public static final int MAX_CONCEPTS = 50;
	public static final int MAX_EXCLUDED_LISTED = 100;

	/** Words that alone never make a learnable subtopic (with the query's own words). */
	private static final Set<String> GENERIC = Set.of("machine", "learn", "artificial", "intelligence", "neural",
			"network", "model", "method", "algorithm", "system", "data", "application", "approach", "technique",
			"computer", "science", "healthcare", "health", "medicine", "research", "study", "the", "of", "in", "for",
			"and", "a", "an", "on", "with", "to", "based", "using");

	private ConceptPolicy() {
	}

	/** Minimal view of one frozen candidate. */
	public record Input(long publicationId, String openalexId, List<ConceptAssociation> concepts) {
	}

	public static ConceptModel build(String topic, Collection<Input> candidates, Representation representation) {
		Set<String> queryStems = queryStems(topic);

		// 1. Collect qualifying assignments per normalized concept key (one per candidate, max confidence).
		Map<String, Agg> byKey = new TreeMap<>();
		Map<Long, Map<String, Double>> perPaper = new TreeMap<>();
		for (Input c : candidates) {
			Map<String, Double> mine = new TreeMap<>();
			for (ConceptAssociation a : c.concepts()) {
				if (!accepts(representation, a.kind())) {
					continue;
				}
				List<String> stems = labelStems(a.displayName());
				if (stems.isEmpty()) {
					continue;
				}
				String key = a.kind().name() + ":" + String.join(" ", stems);
				Agg agg = byKey.computeIfAbsent(key, k -> new Agg(k, a.kind(), stems));
				agg.labels.merge(a.displayName(), 1, Integer::sum);
				if (a.score() != null && a.score() >= MIN_CONFIDENCE) {
					mine.merge(key, a.score(), Math::max); // duplicates within one paper count once
				}
			}
			mine.keySet().forEach(k -> byKey.get(k).supporters.add(c.openalexId()));
			perPaper.put(c.publicationId(), mine);
		}

		// 2. Classify each concept.
		int n = candidates.size();
		List<Agg> survivors = new ArrayList<>();
		List<Entry> excluded = new ArrayList<>();
		Map<String, Integer> excludedByReason = new TreeMap<>();
		for (Agg agg : byKey.values()) {
			String reason = exclusionReason(agg, queryStems, n);
			if (reason == null) {
				survivors.add(agg);
			} else {
				excluded.add(agg.entry(0, reason));
				excludedByReason.merge(reason, 1, Integer::sum);
			}
		}
		survivors.sort(Comparator.comparingInt((Agg a) -> -a.supporters.size()).thenComparing(a -> a.key));
		List<Agg> included = new ArrayList<>(survivors.subList(0, Math.min(MAX_CONCEPTS, survivors.size())));
		for (Agg capped : survivors.subList(included.size(), survivors.size())) {
			excluded.add(capped.entry(0, "CAP_EXCEEDED"));
			excludedByReason.merge("CAP_EXCEEDED", 1, Integer::sum);
		}

		// 3. Weights and per-paper vectors restricted to included concepts.
		double total = included.stream().mapToDouble(a -> Math.log1p(a.supporters.size())).sum();
		List<Entry> entries = included.stream()
				.map(a -> a.entry(total == 0 ? 0 : Math.log1p(a.supporters.size()) / total, "INCLUDED"))
				.toList();
		Set<String> keep = new HashSet<>();
		included.forEach(a -> keep.add(a.key));
		Map<Long, Map<String, Double>> paperConcepts = new TreeMap<>();
		perPaper.forEach((pid, m) -> {
			Map<String, Double> kept = new TreeMap<>(m);
			kept.keySet().retainAll(keep);
			paperConcepts.put(pid, kept);
		});

		excluded.sort(Comparator.comparingInt((Entry e) -> -e.support()).thenComparing(Entry::key));
		List<Entry> listed = excluded.subList(0, Math.min(MAX_EXCLUDED_LISTED, excluded.size()));
		return new ConceptModel(VERSION, representation, n, entries, excludedByReason, List.copyOf(listed),
				paperConcepts, stats(entries, paperConcepts, n));
	}

	private static boolean accepts(Representation r, ConceptKind kind) {
		return r == Representation.COMBINED || (r == Representation.TOPIC_ONLY) == (kind == ConceptKind.TOPIC);
	}

	private static String exclusionReason(Agg agg, Set<String> queryStems, int candidates) {
		if (agg.supporters.isEmpty()) {
			return "LOW_CONFIDENCE";
		}
		if (RelevancePolicy.namesUnrequestedAspect(agg.stems, queryStems)) {
			return "ASPECT_LABEL";
		}
		boolean onlyQueryOrGeneric = agg.stems.stream()
				.allMatch(s -> GENERIC.contains(s) || queryStems.stream().anyMatch(q -> sameStem(q, s)));
		if (onlyQueryOrGeneric) {
			return "QUERY_OR_GENERIC";
		}
		if (candidates > 0 && agg.supporters.size() > MAX_SUPPORT_SHARE * candidates) {
			return "TOO_COMMON";
		}
		if (agg.supporters.size() < MIN_SUPPORT) {
			return "ONE_OFF";
		}
		return null;
	}

	/** Normalized label stems with known acronyms replaced by their phrases ("GNNs" → graph neural network). */
	static List<String> labelStems(String label) {
		List<String> out = new ArrayList<>();
		for (String s : RelevancePolicy.stems(label)) {
			List<String> expansion = RelevancePolicy.acronymExpansion(s);
			if (expansion.isEmpty()) {
				out.add(s);
			} else {
				out.addAll(expansion);
			}
		}
		return out;
	}

	/** Query stems plus the phrases of query acronyms ("ai" adds artificial, intelligence). */
	static Set<String> queryStems(String topic) {
		Set<String> stems = new TreeSet<>(labelStems(topic));
		stems.addAll(RelevancePolicy.stems(topic));
		return stems;
	}

	private static boolean sameStem(String a, String b) {
		return a.equals(b) || (a.length() >= 6 && b.length() >= 6 && a.regionMatches(0, b, 0, 6));
	}

	private static Stats stats(List<Entry> included, Map<Long, Map<String, Double>> paperConcepts, int n) {
		List<Set<String>> sets = paperConcepts.values().stream().map(m -> (Set<String>) new TreeSet<>(m.keySet())).toList();
		int withConcepts = (int) sets.stream().filter(s -> !s.isEmpty()).count();
		double mean = n == 0 ? 0 : sets.stream().mapToInt(Set::size).sum() / (double) n;
		double top = included.isEmpty() || n == 0 ? 0 : included.get(0).support() / (double) n;
		int signatures = new HashSet<>(sets).size();
		double jaccardSum = 0;
		long pairs = 0;
		for (int i = 0; i < sets.size(); i++) {
			for (int j = i + 1; j < sets.size(); j++) {
				Set<String> a = sets.get(i);
				Set<String> b = sets.get(j);
				Set<String> union = new HashSet<>(a);
				union.addAll(b);
				if (!union.isEmpty()) {
					Set<String> inter = new HashSet<>(a);
					inter.retainAll(b);
					jaccardSum += inter.size() / (double) union.size();
				}
				pairs++;
			}
		}
		return new Stats(included.size(), withConcepts, round(mean), round(top), signatures,
				round(pairs == 0 ? 0 : jaccardSum / pairs));
	}

	private static double round(double v) {
		return Math.round(v * 1000) / 1000.0;
	}

	private static final class Agg {
		final String key;
		final ConceptKind kind;
		final List<String> stems;
		final Map<String, Integer> labels = new LinkedHashMap<>();
		final Set<String> supporters = new TreeSet<>();

		Agg(String key, ConceptKind kind, List<String> stems) {
			this.key = key;
			this.kind = kind;
			this.stems = stems;
		}

		Entry entry(double weight, String status) {
			String label = labels.entrySet().stream()
					.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
					.map(Map.Entry::getKey).findFirst().orElse(key);
			return new Entry(key, kind, label, new ArrayList<>(new TreeSet<>(labels.keySet())), supporters.size(),
					Math.round(weight * 1_000_000) / 1_000_000.0, status, supporters.stream().limit(5).toList());
		}
	}

}
