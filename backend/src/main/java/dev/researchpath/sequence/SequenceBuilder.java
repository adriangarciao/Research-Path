package dev.researchpath.sequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import dev.researchpath.optimizer.Preferences;
import dev.researchpath.publication.UrlSafety;
import dev.researchpath.sequence.ReadingPath.Flag;
import dev.researchpath.sequence.ReadingPath.Item;
import dev.researchpath.sequence.ReadingPath.Stage;
import dev.researchpath.sequence.ReadingPath.StageKind;

/**
 * Turns an optimized set into a cautious reading path (playbook M10, decisions D09/D10 conservative):
 * <ol>
 * <li>works marked as reviews/surveys by metadata come first;</li>
 * <li>within that group and within the rest, a cited selected paper is placed before the selected papers citing
 * it (suggestive background, never a stated prerequisite); otherwise by publication year (unknown last), then
 * selection step, then id — a cited paper is ranked by the earliest paper that (transitively) cites it, so it moves
 * just ahead of that paper instead of pushing others back; citation cycles are broken at their earliest paper;</li>
 * <li>the order is cut into at most 2 (≤ 5 papers), 3 (≤ 10) or 4 stages, each of at least 3 papers where
 * possible, labelled only with what the metadata supports: "Overviews and surveys" or a year range.</li>
 * </ol>
 * The selected set is never changed. Experience level does not change the ordering rules; it only affects which
 * papers were selected. Pure and deterministic.
 */
public final class SequenceBuilder {

	public static final int VERSION = 1;

	/** Below this many citations a paper is flagged as having few citations so far. */
	static final int FEW_CITATIONS = 5;

	static final int MAX_TAGS = 4;

	static final List<String> DISCLOSURES = List.of(
			"ORDER_IS_HEURISTIC: the order is a recommendation built from review/survey metadata, citation links "
					+ "among the selected papers and publication year; a citation link suggests background, it does "
					+ "not mean one paper must be read first.",
			"DIFFICULTY_NOT_ESTIMATED: papers carry no difficulty or accessibility rating; review/survey labels "
					+ "describe metadata, not verified content.");

	private static final Comparator<PathPaper> FALLBACK = Comparator
			.comparingInt((PathPaper p) -> p.year() == null ? 1 : 0)
			.thenComparingInt(p -> p.year() == null ? 0 : p.year())
			.thenComparingInt(p -> p.decision().step())
			.thenComparing(PathPaper::id);

	private SequenceBuilder() {
	}

	public static ReadingPath build(List<PathPaper> papers, Preferences preferences) {
		Map<String, PathPaper> byId = new LinkedHashMap<>();
		for (PathPaper p : papers) {
			if (p.decision() == null) {
				throw new IllegalArgumentException("paper " + p.id() + " has no selection decision");
			}
			if (byId.put(p.id(), p) != null) {
				throw new IllegalArgumentException("duplicate paper " + p.id());
			}
		}
		Map<String, Set<String>> cites = new HashMap<>();
		int links = 0;
		for (PathPaper p : papers) {
			Set<String> s = new TreeSet<>();
			for (String r : p.referencedWorkIds()) {
				if (!r.equals(p.id()) && byId.containsKey(r)) {
					s.add(r);
				}
			}
			cites.put(p.id(), s);
			links += s.size();
		}

		List<PathPaper> overviews = papers.stream().filter(p -> OverviewCue.of(p.workType(), p.title()) != null).toList();
		List<PathPaper> rest = papers.stream().filter(p -> OverviewCue.of(p.workType(), p.title()) == null).toList();
		int[] breaks = {0};
		List<PathPaper> order = new ArrayList<>(order(overviews, cites, breaks));
		order.addAll(order(rest, cites, breaks));

		Map<String, Integer> position = new HashMap<>();
		for (int i = 0; i < order.size(); i++) {
			position.put(order.get(i).id(), i + 1);
		}
		List<Stage> stages = stages(order, overviews.size());
		List<Item> items = new ArrayList<>();
		for (PathPaper p : order) {
			int pos = position.get(p.id());
			int stage = stages.stream().filter(s -> s.firstPosition() <= pos && pos <= s.lastPosition())
					.findFirst().orElseThrow().number();
			List<Integer> citesPos = cites.get(p.id()).stream().map(position::get).sorted().toList();
			List<Integer> citedBy = order.stream().filter(o -> cites.get(o.id()).contains(p.id()))
					.map(o -> position.get(o.id())).sorted().toList();
			String link = UrlSafety.safeDisplayUrl(p.sourceLink());
			List<String> tags = p.concepts().stream()
					.sorted(Comparator.comparingDouble(PathPaper.Concept::strength).reversed()
							.thenComparing(PathPaper.Concept::label))
					.map(PathPaper.Concept::label).distinct().limit(MAX_TAGS).toList();
			items.add(new Item(pos, stage, p.id(), p.title(), p.year(), p.workType(), link,
					link == null ? null : p.sourceLinkKind(), tags, Explanations.reasons(p, preferences), citesPos,
					citedBy, flags(p, link, tags), p.decision().step()));
		}

		List<String> warnings = new ArrayList<>();
		if (papers.isEmpty()) {
			warnings.add("EMPTY: no papers were selected");
		} else if (links == 0 && papers.size() > 1) {
			warnings.add("NO_CITATION_LINKS: none of the selected papers cite each other; the order uses "
					+ "review/survey metadata and publication year only");
		}
		if (breaks[0] > 0) {
			warnings.add("CITATION_CYCLE: " + breaks[0] + " citation cycle(s) among the selected papers were broken "
					+ "by publication year");
		}
		return new ReadingPath(VERSION, preferences, stages, items, links, breaks[0], warnings, DISCLOSURES);
	}

	/** Kahn-style ordering: a paper is placed once every selected paper it cites in this group is placed. */
	static List<PathPaper> order(List<PathPaper> group, Map<String, Set<String>> cites, int[] breaks) {
		Map<String, PathPaper> inGroup = new HashMap<>();
		group.forEach(p -> inGroup.put(p.id(), p));
		Map<String, Set<String>> pending = new HashMap<>();
		for (PathPaper p : group) {
			Set<String> s = new TreeSet<>(cites.get(p.id()));
			s.retainAll(inGroup.keySet());
			pending.put(p.id(), s);
		}
		// A paper's effective rank is the earliest among itself and the group papers that cite it, transitively.
		Map<String, Set<String>> citers = new HashMap<>();
		for (PathPaper p : group) {
			for (String c : pending.get(p.id())) {
				citers.computeIfAbsent(c, x -> new TreeSet<>()).add(p.id());
			}
		}
		Map<String, PathPaper> effective = new HashMap<>();
		for (PathPaper p : group) {
			PathPaper earliest = p;
			List<String> queue = new ArrayList<>(List.of(p.id()));
			Set<String> seen = new TreeSet<>(queue);
			for (int i = 0; i < queue.size(); i++) {
				for (String c : citers.getOrDefault(queue.get(i), Set.of())) {
					if (seen.add(c)) {
						queue.add(c);
						if (FALLBACK.compare(inGroup.get(c), earliest) < 0) {
							earliest = inGroup.get(c);
						}
					}
				}
			}
			effective.put(p.id(), earliest);
		}
		List<PathPaper> remaining = new ArrayList<>(group);
		remaining.sort(Comparator.comparing((PathPaper p) -> effective.get(p.id()), FALLBACK).thenComparing(FALLBACK));
		List<PathPaper> out = new ArrayList<>();
		while (!remaining.isEmpty()) {
			PathPaper next = remaining.stream().filter(p -> pending.get(p.id()).isEmpty()).findFirst().orElse(null);
			if (next == null) {
				next = cycleEntry(remaining.get(0), pending, inGroup);
				breaks[0]++;
			}
			out.add(next);
			remaining.remove(next);
			for (Set<String> s : pending.values()) {
				s.remove(next.id());
			}
		}
		return out;
	}

	/**
	 * Every remaining paper waits on another, so following "cites" from any of them reaches a cycle; the cycle's
	 * earliest paper (by the fallback order) is placed first.
	 */
	private static PathPaper cycleEntry(PathPaper start, Map<String, Set<String>> pending, Map<String, PathPaper> inGroup) {
		List<String> walk = new ArrayList<>();
		PathPaper cur = start;
		while (!walk.contains(cur.id())) {
			walk.add(cur.id());
			cur = pending.get(cur.id()).stream().map(inGroup::get).min(FALLBACK).orElseThrow();
		}
		return walk.subList(walk.indexOf(cur.id()), walk.size()).stream().map(inGroup::get).min(FALLBACK).orElseThrow();
	}

	static List<Stage> stages(List<PathPaper> order, int overviewCount) {
		int n = order.size();
		List<Stage> out = new ArrayList<>();
		if (n == 0) {
			return out;
		}
		int max = n <= 5 ? 2 : n <= 10 ? 3 : 4;
		if (overviewCount > 0) {
			out.add(new Stage(1, StageKind.OVERVIEW, "Overviews and surveys",
					"Start with works that their metadata marks as reviews, surveys or introductions.", 1, overviewCount));
		}
		int m = n - overviewCount;
		if (m == 0) {
			return out;
		}
		int chunks = Math.max(1, Math.min(max - out.size(), m / 3));
		List<PathPaper> rest = order.subList(overviewCount, n);
		if (chunks == 1) {
			out.add(new Stage(out.size() + 1, StageKind.REMAINING, overviewCount > 0 ? "Further papers" : "Selected papers",
					"Ordered by citation links among them where present, otherwise by publication year.",
					overviewCount + 1, n));
			return out;
		}
		List<Integer> years = rest.stream().map(PathPaper::year).toList();
		int start = 0;
		for (int end : cuts(years, chunks)) {
			List<Integer> part = years.subList(start, end);
			String range = range(part);
			out.add(new Stage(out.size() + 1, StageKind.PERIOD, "Papers from " + range,
					"Published " + range + "; ordered by citation links among them where present, otherwise by year.",
					overviewCount + start + 1, overviewCount + end));
			start = end;
		}
		return out;
	}

	/** End indices of near-equal contiguous parts; a cut moves up to two places to fall between different years. */
	static List<Integer> cuts(List<Integer> years, int chunks) {
		int n = years.size();
		List<Integer> cuts = new ArrayList<>();
		int prev = 0;
		for (int i = 1; i < chunks; i++) {
			int ideal = (int) Math.round(i * n / (double) chunks);
			int chosen = -1;
			for (int d : new int[] {0, 1, -1, 2, -2}) {
				int c = ideal + d;
				if (c - prev < 2 || n - c < 2 * (chunks - i)) {
					continue;
				}
				if (chosen < 0) {
					chosen = c;
				}
				if (!Objects.equals(years.get(c - 1), years.get(c))) {
					chosen = c;
					break;
				}
			}
			chosen = chosen < 0 ? ideal : chosen;
			cuts.add(chosen);
			prev = chosen;
		}
		cuts.add(n);
		return cuts;
	}

	private static String range(List<Integer> years) {
		List<Integer> known = years.stream().filter(Objects::nonNull).toList();
		if (known.isEmpty()) {
			return "an unknown year";
		}
		int min = known.stream().mapToInt(Integer::intValue).min().orElseThrow();
		int max = known.stream().mapToInt(Integer::intValue).max().orElseThrow();
		return (min == max ? String.valueOf(min) : min + "–" + max) + (known.size() < years.size() ? " (and undated)" : "");
	}

	private static List<Flag> flags(PathPaper p, String link, List<String> tags) {
		List<Flag> flags = new ArrayList<>();
		if (p.citedByCount() == null) {
			flags.add(Flag.CITATIONS_UNKNOWN);
		} else if (p.citedByCount() < FEW_CITATIONS) {
			flags.add(Flag.FEW_CITATIONS);
		}
		if (p.citationPercentile() == null) {
			flags.add(Flag.INFLUENCE_UNKNOWN);
		}
		if (p.year() == null) {
			flags.add(Flag.YEAR_UNKNOWN);
		}
		if (link == null) {
			flags.add(Flag.NO_SAFE_LINK);
		}
		if (Boolean.FALSE.equals(p.abstractAvailable())) {
			flags.add(Flag.ABSTRACT_MISSING);
		}
		if (tags.isEmpty()) {
			flags.add(Flag.NO_SUBJECT_TAGS);
		}
		return flags;
	}

}
