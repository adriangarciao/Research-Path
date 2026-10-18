package dev.researchpath.sequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.researchpath.optimizer.GreedyOptimizer;
import dev.researchpath.optimizer.Objective;
import dev.researchpath.optimizer.OptimizerConfig;
import dev.researchpath.sequence.ReadingPath.Explanation;

/**
 * "Why it's here" templates (playbook M10). Every sentence is filled only from the optimizer's recorded decision
 * and the paper's stored metadata; nothing describes a paper's content beyond its provider labels.
 */
final class Explanations {

	static final int MAX_LISTED_CONCEPTS = 3;

	private Explanations() {
	}

	static List<Explanation> reasons(PathPaper p, OptimizerConfig.QualityWeights weights) {
		List<Explanation> out = new ArrayList<>();
		out.add(primary(p, weights));
		OverviewCue.Source source = OverviewCue.of(p.workType(), p.title());
		if (source == OverviewCue.Source.PUBLICATION_TYPE) {
			out.add(new Explanation("OVERVIEW_TYPE", "OpenAlex lists its publication type as review."));
		} else if (source == OverviewCue.Source.TITLE) {
			String word = OverviewCue.titleWord(p.title());
			out.add(new Explanation("OVERVIEW_TITLE",
					"Its title presents it as " + article(word) + " " + word + " (content not verified)."));
		}
		return out;
	}

	private static Explanation primary(PathPaper p, OptimizerConfig.QualityWeights weights) {
		GreedyOptimizer.Decision d = p.decision();
		return switch (d.reason()) {
			case ADDS_NEW_CONCEPTS -> addsConcepts(p);
			case HIGH_RELEVANCE -> new Explanation("RELEVANCE",
					"Selected mainly for relevance: the query's terms appear in its title or subject tags.");
			case INFLUENCE -> influence(p);
			case PREFERENCE_FIT -> preferenceFit(p, weights);
		};
	}

	private static Explanation addsConcepts(PathPaper p) {
		Map<String, PathPaper.Concept> byKey = new HashMap<>();
		p.concepts().forEach(c -> byKey.put(c.key(), c));
		List<String> labels = p.decision().newConcepts().stream()
				.map(byKey::get)
				.filter(c -> c != null)
				.sorted(Comparator.comparingDouble(PathPaper.Concept::strength).reversed()
						.thenComparing(PathPaper.Concept::label))
				.map(PathPaper.Concept::label)
				.distinct()
				.toList();
		if (labels.isEmpty()) {
			return new Explanation("ADDS_CONCEPTS", "Adds subtopics the selection did not yet cover.");
		}
		List<String> shown = labels.subList(0, Math.min(MAX_LISTED_CONCEPTS, labels.size()));
		int more = labels.size() - shown.size();
		return new Explanation("ADDS_CONCEPTS", "Adds subtopics the selection did not yet cover: " + join(shown)
				+ (more > 0 ? " (and " + more + " more)" : "") + ".");
	}

	private static Explanation influence(PathPaper p) {
		if (p.citationPercentile() == null) {
			return new Explanation("INFLUENCE", "Selected mainly for citation influence.");
		}
		long top = Math.max(1, (long) Math.ceil((1 - p.citationPercentile()) * 100 - 1e-9));
		return new Explanation("INFLUENCE", "Selected mainly for citation influence: OpenAlex ranks it in the top "
				+ top + "% of works of similar age and field by citations.");
	}

	private static Explanation preferenceFit(PathPaper p, OptimizerConfig.QualityWeights w) {
		Objective.QualityParts q = p.decision().quality();
		double recency = w.recency() * q.recency();
		double age = w.age() * q.age();
		double overview = w.overview() * q.overview();
		double technical = w.technical() * q.technical();
		String year = p.year() == null ? "" : " (published " + p.year() + ")";
		if (recency >= age && recency >= overview && recency >= technical) {
			return new Explanation("FIT_RECENT", "Selected mainly to fit the Recent focus" + year + ".");
		}
		if (age >= overview && age >= technical) {
			return new Explanation("FIT_FOUNDATIONAL", "Selected mainly to fit the Foundational focus" + year + ".");
		}
		if (overview >= technical) {
			return new Explanation("FIT_BEGINNER", "Selected mainly to fit the Beginner preference for overview-type works.");
		}
		return new Explanation("FIT_ADVANCED",
				"Selected mainly to fit the Advanced preference for works not marked as overviews.");
	}

	static String join(List<String> items) {
		if (items.size() <= 1) {
			return String.join("", items);
		}
		return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
	}

	private static String article(String word) {
		return "aeiou".indexOf(word.charAt(0)) >= 0 ? "an" : "a";
	}

}
