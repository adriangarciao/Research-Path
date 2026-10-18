package dev.researchpath.sequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import dev.researchpath.optimizer.GreedyOptimizer;
import dev.researchpath.optimizer.GreedyOptimizer.Reason;
import dev.researchpath.optimizer.Objective;
import dev.researchpath.optimizer.Preferences;
import dev.researchpath.optimizer.Preferences.Experience;
import dev.researchpath.optimizer.Preferences.Focus;
import dev.researchpath.sequence.ReadingPath.Flag;
import dev.researchpath.sequence.ReadingPath.Item;
import dev.researchpath.sequence.ReadingPath.Stage;
import dev.researchpath.sequence.ReadingPath.StageKind;

class SequenceBuilderTest {

	private static final Objective.QualityParts NEUTRAL = new Objective.QualityParts(1, 0.5, 0.5, 0.5, 0, 1, 0.8);

	/** Claims that citation evidence must never be turned into (reasons and stage text only). */
	private static final Pattern PREREQUISITE_CLAIM = Pattern.compile("(?i)prerequisite|requires|must|read .* first|depends on");

	private static int steps;

	private static PathPaper paper(String id, Integer year, String... refs) {
		return paper(id, year, "article", "Paper " + id, List.of(new PathPaper.Concept("k_" + id, "Concept " + id, 0.9)),
				Reason.ADDS_NEW_CONCEPTS, List.of("k_" + id), refs);
	}

	private static PathPaper paper(String id, Integer year, String type, String title, List<PathPaper.Concept> concepts,
			Reason reason, List<String> newConcepts, String... refs) {
		GreedyOptimizer.Decision d = new GreedyOptimizer.Decision(++steps, id, 0.1, 0.05, 0.05, NEUTRAL, newConcepts,
				reason);
		return new PathPaper(id, title, year, type, 40, 0.9, "https://doi.org/10.1/" + id, "DOI", null, concepts,
				List.of(refs), d);
	}

	private static ReadingPath build(List<PathPaper> papers) {
		return SequenceBuilder.build(papers, Preferences.DEFAULT);
	}

	@Test
	void zeroCitationLinksFallBackToChronologyWithWarning() {
		ReadingPath path = build(List.of(paper("C", 2021), paper("A", 2012), paper("B", 2016)));
		assertThat(path.orderedIds()).containsExactly("A", "B", "C");
		assertThat(path.citationLinks()).isZero();
		assertThat(path.warnings()).anyMatch(w -> w.startsWith("NO_CITATION_LINKS"));
		assertThat(path.items()).allSatisfy(i -> assertThat(i.cites()).isEmpty());
	}

	@Test
	void citedSelectedPaperComesBeforeTheCitingOneAndPositionsAreRecorded() {
		// A preprint-era quirk: the older-dated paper cites the newer-dated one.
		ReadingPath path = build(List.of(paper("A", 2015, "B", "W_outside"), paper("B", 2018), paper("C", 2016)));
		assertThat(path.orderedIds()).containsExactly("B", "A", "C");
		assertThat(path.citationLinks()).isEqualTo(1);
		Item a = path.items().get(1);
		assertThat(a.cites()).containsExactly(1);
		assertThat(path.items().get(0).citedBy()).containsExactly(2);
		assertThat(path.warnings()).noneMatch(w -> w.startsWith("NO_CITATION_LINKS"));
	}

	@Test
	void citationCycleIsBrokenAtItsEarliestPaperWithoutMovingOtherPapersAheadOfTheirLinks() {
		// C (oldest) cites A; A and B cite each other. C must still follow A.
		ReadingPath path = build(List.of(paper("C", 2010, "A"), paper("A", 2018, "B"), paper("B", 2019, "A")));
		assertThat(path.orderedIds()).containsExactly("A", "C", "B");
		assertThat(path.cycleBreaks()).isEqualTo(1);
		assertThat(path.warnings()).anyMatch(w -> w.startsWith("CITATION_CYCLE: 1"));
	}

	@Test
	void separateCitationComponentsInterleaveByYear() {
		ReadingPath path = build(List.of(paper("A2", 2014, "A1"), paper("A1", 2010), paper("B2", 2016, "B1"),
				paper("B1", 2012)));
		assertThat(path.orderedIds()).containsExactly("A1", "B1", "A2", "B2");
		assertThat(path.citationLinks()).isEqualTo(2);
	}

	@Test
	void dateTiesUseSelectionStepThenIdAndUnknownYearsGoLast() {
		steps = 0;
		PathPaper z = paper("Z", 2020);
		PathPaper y = paper("Y", 2020);
		PathPaper u = paper("U", null);
		ReadingPath path = build(List.of(u, y, z));
		assertThat(path.orderedIds()).containsExactly("Z", "Y", "U");
		assertThat(path.items().get(2).flags()).contains(Flag.YEAR_UNKNOWN);
	}

	@Test
	void overviewsLeadEvenWhenTheyCiteOlderSelectedPapers() {
		PathPaper survey = paper("S", 2022, "article", "A Survey of Graph Learning", List.of(), Reason.HIGH_RELEVANCE,
				List.of(), "A", "B");
		PathPaper review = paper("R", 2023, "review", "Graph models in chemistry", List.of(), Reason.HIGH_RELEVANCE,
				List.of());
		ReadingPath path = build(List.of(paper("A", 2009), paper("B", 2012), survey, review, paper("C", 2015, "A")));
		assertThat(path.orderedIds()).containsExactly("S", "R", "A", "B", "C");
		Stage first = path.stages().get(0);
		assertThat(first.kind()).isEqualTo(StageKind.OVERVIEW);
		assertThat(first.label()).isEqualTo("Overviews and surveys");
		assertThat(first.lastPosition()).isEqualTo(2);
		assertThat(path.items().get(0).reasons()).extracting(ReadingPath.Explanation::code)
				.containsExactly("RELEVANCE", "OVERVIEW_TITLE");
		assertThat(path.items().get(0).reasons().get(1).text()).isEqualTo("Its title presents it as a survey (content not verified).");
		assertThat(path.items().get(1).reasons()).extracting(ReadingPath.Explanation::code)
				.containsExactly("RELEVANCE", "OVERVIEW_TYPE");
		assertThat(path.items().get(0).cites()).containsExactly(3, 4);
	}

	@Test
	void stagesPartitionThePathAndRespectTheBudgetLimits() {
		for (int n : new int[] {1, 2, 3, 5, 6, 10, 11, 15, 20}) {
			List<PathPaper> papers = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				papers.add(paper("P" + i, 2000 + i));
			}
			ReadingPath path = build(papers);
			int max = n <= 5 ? 2 : n <= 10 ? 3 : 4;
			assertThat(path.stages()).hasSizeBetween(1, max);
			int expectedFirst = 1;
			for (Stage s : path.stages()) {
				assertThat(s.firstPosition()).isEqualTo(expectedFirst);
				assertThat(s.lastPosition()).isGreaterThanOrEqualTo(s.firstPosition());
				if (path.stages().size() > 1) {
					assertThat(s.lastPosition() - s.firstPosition() + 1).isGreaterThanOrEqualTo(2);
				}
				expectedFirst = s.lastPosition() + 1;
			}
			assertThat(expectedFirst - 1).isEqualTo(n);
			assertThat(path.items()).allSatisfy(i -> assertThat(path.stages().get(i.stage() - 1).firstPosition())
					.isLessThanOrEqualTo(i.position()));
		}
	}

	@Test
	void fiveAndTwentyPaperPathsGetHonestStageNames() {
		List<PathPaper> five = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			five.add(paper("F" + i, 2010 + i));
		}
		ReadingPath small = build(five);
		assertThat(small.stages()).singleElement().satisfies(s -> {
			assertThat(s.kind()).isEqualTo(StageKind.REMAINING);
			assertThat(s.label()).isEqualTo("Selected papers");
		});

		List<PathPaper> twenty = new ArrayList<>();
		twenty.add(paper("S", 2021, "review", "Deep learning on graphs", List.of(), Reason.HIGH_RELEVANCE, List.of()));
		for (int i = 0; i < 19; i++) {
			twenty.add(paper("T" + i, 2005 + i));
		}
		ReadingPath large = build(twenty);
		assertThat(large.stages()).hasSize(4);
		assertThat(large.stages()).extracting(Stage::kind)
				.containsExactly(StageKind.OVERVIEW, StageKind.PERIOD, StageKind.PERIOD, StageKind.PERIOD);
		for (Stage s : large.stages().subList(1, 4)) {
			List<Integer> years = large.items().subList(s.firstPosition() - 1, s.lastPosition()).stream()
					.map(Item::year).toList();
			int min = years.stream().mapToInt(Integer::intValue).min().orElseThrow();
			int max = years.stream().mapToInt(Integer::intValue).max().orElseThrow();
			assertThat(s.label()).isEqualTo("Papers from " + min + "–" + max);
		}
	}

	@Test
	void stageCutsPreferBoundariesBetweenDifferentYears() {
		// Ideal cut at 3 would split the 2015 pair; it moves by one.
		assertThat(SequenceBuilder.cuts(List.of(2010, 2012, 2015, 2015, 2018, 2020), 2)).containsExactly(4, 6);
		assertThat(SequenceBuilder.cuts(List.of(2020, 2020, 2020, 2020, 2020, 2020), 2)).containsExactly(3, 6);
	}

	@Test
	void experienceLevelDoesNotChangeTheOrderingRules() {
		List<PathPaper> papers = List.of(paper("A", 2012), paper("B", 2016, "A"),
				paper("S", 2020, "review", "x", List.of(), Reason.HIGH_RELEVANCE, List.of()), paper("C", 2014));
		ReadingPath beginner = SequenceBuilder.build(papers, new Preferences(Experience.BEGINNER, Focus.BALANCED));
		ReadingPath advanced = SequenceBuilder.build(papers, new Preferences(Experience.ADVANCED, Focus.BALANCED));
		assertThat(beginner.orderedIds()).isEqualTo(advanced.orderedIds());
		assertThat(beginner.stages()).isEqualTo(advanced.stages());
	}

	@Test
	void sameInputsReproduceExactOrderStagesAndText() {
		List<PathPaper> papers = List.of(paper("A", 2012), paper("B", 2016, "A"), paper("C", 2014), paper("D", 2019, "B", "C"));
		assertThat(build(papers)).isEqualTo(build(papers));
		assertThat(build(new ArrayList<>(papers).reversed()).orderedIds()).isEqualTo(build(papers).orderedIds());
	}

	@Test
	void orderingNeverChangesTheSelectedSet() {
		List<PathPaper> papers = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			papers.add(paper("P" + i, 2000 + (i * 7) % 13, "P" + ((i + 3) % 20), "P" + ((i * 5) % 20)));
		}
		ReadingPath path = build(papers);
		Set<String> in = new HashSet<>();
		papers.forEach(p -> in.add(p.id()));
		assertThat(path.orderedIds()).hasSize(20).doesNotHaveDuplicates();
		assertThat(new HashSet<>(path.orderedIds())).isEqualTo(in);
	}

	@Test
	void explanationsUseOnlyStoredConceptsAndNeverClaimPrerequisites() {
		List<PathPaper.Concept> concepts = List.of(new PathPaper.Concept("t1", "Graph theory", 0.95),
				new PathPaper.Concept("t2", "Message passing", 0.8), new PathPaper.Concept("t3", "Chemistry", 0.7),
				new PathPaper.Concept("t4", "Drug discovery", 0.6), new PathPaper.Concept("t5", "Benchmarks", 0.55));
		PathPaper p = paper("A", 2018, "article", "Neural message passing", concepts, Reason.ADDS_NEW_CONCEPTS,
				List.of("t5", "t2", "t1", "t3"));
		ReadingPath path = build(List.of(p, paper("B", 2020, "A")));
		Item a = path.items().get(0);
		assertThat(a.reasons().get(0).text()).isEqualTo(
				"Adds subtopics the selection did not yet cover: Graph theory, Message passing and Chemistry (and 1 more).");
		assertThat(a.tags()).containsExactly("Graph theory", "Message passing", "Chemistry", "Drug discovery");
		Set<String> stored = new HashSet<>();
		p.concepts().forEach(c -> stored.add(c.label()));
		assertThat(stored).containsAll(a.tags());
		for (Item item : path.items()) {
			item.reasons().forEach(r -> assertThat(r.text()).doesNotContainPattern(PREREQUISITE_CLAIM));
		}
		path.stages().forEach(s -> assertThat(s.label() + " " + s.purpose()).doesNotContainPattern(PREREQUISITE_CLAIM));
		assertThat(path.disclosures()).anyMatch(d -> d.startsWith("ORDER_IS_HEURISTIC"))
				.anyMatch(d -> d.startsWith("DIFFICULTY_NOT_ESTIMATED"));
	}

	@Test
	void otherReasonTemplatesAreFilledFromTheDecision() {
		PathPaper influential = new PathPaper("I", "t", 2015, "article", 900, 0.987, null, null, null, List.of(), List.of(),
				new GreedyOptimizer.Decision(1, "I", 0.1, 0, 0.1, NEUTRAL, List.of(), Reason.INFLUENCE));
		PathPaper recent = new PathPaper("R", "t", 2025, "article", 2, 0.4, null, null, null, List.of(), List.of(),
				new GreedyOptimizer.Decision(2, "R", 0.1, 0, 0.1, new Objective.QualityParts(1, 0.4, 1, 0, 0, 1, 0.9),
						List.of(), Reason.PREFERENCE_FIT));
		ReadingPath path = SequenceBuilder.build(List.of(influential, recent), new Preferences(Experience.INTERMEDIATE, Focus.RECENT));
		assertThat(path.items().get(0).reasons().get(0).text()).isEqualTo(
				"Selected mainly for citation influence: OpenAlex ranks it in the top 2% of works of similar age and field by citations.");
		assertThat(path.items().get(1).reasons().get(0).text()).isEqualTo("Selected mainly to fit the Recent focus (published 2025).");
		assertThat(path.items().get(1).flags()).contains(Flag.FEW_CITATIONS, Flag.NO_SAFE_LINK, Flag.NO_SUBJECT_TAGS);
	}

	@Test
	void missingAbstractsAreFlaggedButDoNotChangeTheOrder() {
		List<PathPaper> known = List.of(paper("A", 2012), paper("B", 2016, "A"), paper("C", 2014));
		List<PathPaper> missing = known.stream().map(p -> new PathPaper(p.id(), p.title(), p.year(), p.workType(),
				p.citedByCount(), p.citationPercentile(), p.sourceLink(), p.sourceLinkKind(), false, p.concepts(),
				p.referencedWorkIds(), p.decision())).toList();
		ReadingPath a = build(known);
		ReadingPath b = build(missing);
		assertThat(b.orderedIds()).isEqualTo(a.orderedIds());
		assertThat(b.items()).allSatisfy(i -> assertThat(i.flags()).contains(Flag.ABSTRACT_MISSING));
		assertThat(a.items()).allSatisfy(i -> assertThat(i.flags()).doesNotContain(Flag.ABSTRACT_MISSING));
	}

	@Test
	void unsafeLinksAreDroppedAndProviderTextIsPassedThroughAsPlainText() {
		String title = "<script>alert(1)</script> Graphs & \"things\"";
		PathPaper p = new PathPaper("X", title, 2020, "article", null, null, "javascript:alert(1)", "LANDING_PAGE", null,
				List.of(), List.of(), new GreedyOptimizer.Decision(1, "X", 0.1, 0, 0.1, NEUTRAL, List.of(), Reason.HIGH_RELEVANCE));
		Item item = build(List.of(p)).items().get(0);
		assertThat(item.link()).isNull();
		assertThat(item.linkKind()).isNull();
		assertThat(item.title()).isEqualTo(title);
		assertThat(item.flags()).contains(Flag.NO_SAFE_LINK, Flag.CITATIONS_UNKNOWN, Flag.INFLUENCE_UNKNOWN);
		item.reasons().forEach(r -> assertThat(r.text()).doesNotContain("<", ">"));
	}

	@Test
	void emptyAndInvalidInputs() {
		ReadingPath empty = build(List.of());
		assertThat(empty.items()).isEmpty();
		assertThat(empty.stages()).isEmpty();
		assertThat(empty.warnings()).anyMatch(w -> w.startsWith("EMPTY"));
		assertThatThrownBy(() -> build(List.of(paper("A", 2010), paper("A", 2011))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
		PathPaper noDecision = new PathPaper("N", "t", 2010, null, null, null, null, null, null, List.of(), List.of(), null);
		assertThatThrownBy(() -> build(List.of(noDecision))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void overviewCueNamesItsSource() {
		assertThat(OverviewCue.of("review", "Anything")).isEqualTo(OverviewCue.Source.PUBLICATION_TYPE);
		assertThat(OverviewCue.of("article", "An Introduction to RL")).isEqualTo(OverviewCue.Source.TITLE);
		assertThat(OverviewCue.titleWord("An Introduction to RL")).isEqualTo("introduction");
		assertThat(OverviewCue.of("article", "Reviewing reviewers")).isNull();
		assertThat(OverviewCue.of(null, null)).isNull();
	}

}
