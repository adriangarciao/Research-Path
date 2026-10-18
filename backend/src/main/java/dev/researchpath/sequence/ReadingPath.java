package dev.researchpath.sequence;

import java.util.List;

import dev.researchpath.optimizer.Preferences;

/**
 * A recommended reading order for an already selected set (playbook M10). All text is plain text built from
 * templates and provider labels; clients must render it as text, never as HTML.
 *
 * @param citationLinks citation links among the selected papers (A cites B, both selected)
 * @param cycleBreaks times a citation cycle had to be broken to produce an order
 * @param disclosures statements that always apply to this kind of path (heuristic order, no difficulty estimate)
 */
public record ReadingPath(int version, Preferences preferences, List<Stage> stages, List<Item> items,
		int citationLinks, int cycleBreaks, List<String> warnings, List<String> disclosures) {

	public ReadingPath {
		stages = List.copyOf(stages);
		items = List.copyOf(items);
		warnings = List.copyOf(warnings);
		disclosures = List.copyOf(disclosures);
	}

	public List<String> orderedIds() {
		return items.stream().map(Item::id).toList();
	}

	public enum StageKind {
		/** Works marked as reviews/surveys by publication type or title. */
		OVERVIEW,
		/** A contiguous, year-labelled part of the remaining order. */
		PERIOD,
		/** The remaining papers as one group. */
		REMAINING
	}

	/** Papers {@code firstPosition..lastPosition} (1-based, inclusive) of the path. */
	public record Stage(int number, StageKind kind, String label, String purpose, int firstPosition, int lastPosition) {
	}

	/**
	 * @param reasons one or two grounded "why it's here" statements
	 * @param tags up to four of the paper's included concept labels
	 * @param cites positions of earlier-or-later path papers this paper cites
	 * @param citedBy positions of path papers citing this paper
	 * @param link validated http(s) link or null
	 * @param selectionStep the optimizer step that chose the paper (differs from reading position)
	 */
	public record Item(int position, int stage, String id, String title, Integer year, String workType, String link,
			String linkKind, List<String> tags, List<Explanation> reasons, List<Integer> cites, List<Integer> citedBy,
			List<Flag> flags, int selectionStep) {

		public Item {
			tags = List.copyOf(tags);
			reasons = List.copyOf(reasons);
			cites = List.copyOf(cites);
			citedBy = List.copyOf(citedBy);
			flags = List.copyOf(flags);
		}
	}

	/** A template code with its rendered plain-text sentence. */
	public record Explanation(String code, String text) {
	}

	/** Uncertainty indicators shown with a paper. */
	public enum Flag {
		CITATIONS_UNKNOWN, FEW_CITATIONS, INFLUENCE_UNKNOWN, YEAR_UNKNOWN, NO_SAFE_LINK, ABSTRACT_MISSING, NO_SUBJECT_TAGS
	}

}
