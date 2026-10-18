package dev.researchpath.relevance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.researchpath.publication.NormalizedWork;
import dev.researchpath.publication.NormalizedWork.ConceptAssociation;
import dev.researchpath.publication.NormalizedWork.ConceptKind;
import dev.researchpath.publication.TextNormalization;

/**
 * Conservative, interpretable relevance gate (decision D07). Pure logic: no Spring, DB or network.
 *
 * <p>Evidence = which query terms appear in the title, in confident keywords/topics (excluding labels
 * that name an unrequested aspect), and in the abstract. The abstract is never required: it only distinguishes BORDERLINE from INELIGIBLE.
 * Decision, first match wins:
 * <ol>
 *   <li>Title is about an aspect the query did not ask for (policy, legal, economics, education,
 *       perceptions, ...) and the work matches at least half the query terms → TANGENTIAL.</li>
 *   <li>Title ∪ concepts cover ≥ {@value #ELIGIBLE_COVERAGE} of query terms → ELIGIBLE.</li>
 *   <li>Title ∪ concepts ∪ abstract cover ≥ {@value #BORDERLINE_COVERAGE} → BORDERLINE (excluded).</li>
 *   <li>Otherwise → INELIGIBLE.</li>
 * </ol>
 * Thresholds, the stopword list, the aspect lexicon and the acronym table are PROVISIONAL,
 * derived from the 2026-10-17 labeled pools; they are versioned and recorded with each decision.
 */
public final class RelevancePolicy {

	public static final int VERSION = 1;
	public static final double ELIGIBLE_COVERAGE = 0.75;
	public static final double BORDERLINE_COVERAGE = 0.5;
	/** Audit thresholds for counting classifications as evidence; unknown scores do not count. */
	public static final double MIN_TOPIC_SCORE = 0.15;
	public static final double MIN_KEYWORD_SCORE = 0.25;

	private static final Set<String> STOPWORDS = Set.of("a", "an", "the", "of", "in", "on", "for", "to", "and", "or",
			"with", "by", "from", "at", "as", "into", "via", "using", "use", "based", "towards", "toward", "about",
			"its", "their", "is", "are", "be", "how", "what", "why", "introduction", "overview", "survey", "review");

	/** Document-side acronym expansion: the acronym also counts as its full phrase. */
	private static final Map<String, String> EXPANSIONS = Map.of(
			"gnn", "graph neural network", "rl", "reinforcement learning", "drl", "deep reinforcement learning",
			"ai", "artificial intelligence", "ml", "machine learning", "nlp", "natural language processing",
			"llm", "large language model", "cnn", "convolutional neural network");

	/**
	 * Query acronyms treated as one concept, satisfied by any listed phrase. "ai" is also satisfied by
	 * its narrower technique terms (machine/deep learning, neural networks, LLMs).
	 */
	private static final Map<String, List<String>> QUERY_CONCEPTS = Map.of(
			"ai", List.of("artificial intelligence", "machine learning", "deep learning", "neural network",
					"large language model"),
			"gnn", List.of("graph neural network"), "rl", List.of("reinforcement learning"),
			"ml", List.of("machine learning"), "nlp", List.of("natural language processing"),
			"llm", List.of("large language model"), "cnn", List.of("convolutional neural network"));

	/** Non-technical aspects (title words, matched by stem prefix). Not cues when the query asks for them. */
	private static final List<String> ASPECT_CUES = List.of("polic", "governanc", "regulat", "legal",
			"liabil", "ethic", "econom", "staffing", "workforc", "educat", "student", "curricul", "percept", "attitud",
			"intention", "acceptanc", "adoption", "distrust");
	/** Two-word aspect cues. */
	private static final List<String> ASPECT_PHRASES = List.of("information secur");
	/** Technical senses that cancel a cue: "policy" in RL/control, "student" in teacher-student models. */
	private static final Map<String, List<String>> TECHNICAL_SENSES = Map.of(
			"polic", List.of("reinforc", "optimiz", "gradient", "search", "control", "robot", "agent", "reward"),
			"student", List.of("teacher", "distill"));

	private RelevancePolicy() {
	}

	/** One query concept: matched if any alternative phrase (stem sequence) occurs in the evidence. */
	public record QueryTerm(String label, List<List<String>> alternatives) {
	}

	public record Query(String topic, List<QueryTerm> terms, Set<String> stems) {

		public boolean hasContent() {
			return !terms.isEmpty();
		}
	}

	public record Decision(RelevanceTier tier, String reason, Map<String, Object> evidence) {
	}

	public static Query analyzeQuery(String topic) {
		List<String> tokens = tokens(topic);
		List<QueryTerm> terms = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (String token : tokens) {
			if (STOPWORDS.contains(token)) {
				continue;
			}
			List<String> phrases = QUERY_CONCEPTS.get(token);
			String label = phrases != null ? token : stem(token);
			if (!seen.add(label)) {
				continue;
			}
			List<List<String>> alternatives = new ArrayList<>();
			alternatives.add(List.of(stem(token)));
			if (phrases != null) {
				phrases.forEach(p -> alternatives.add(stems(p)));
			}
			terms.add(new QueryTerm(label, alternatives));
		}
		Set<String> queryStems = new LinkedHashSet<>(stems(topic));
		return new Query(topic, List.copyOf(terms), queryStems);
	}

	public static Decision decide(Query query, NormalizedWork work) {
		List<String> title = withExpansions(stems(work.title()));
		List<String> concepts = withExpansions(conceptStems(work.concepts(), query.stems()));
		List<String> abstractText = work.abstractText() == null ? List.of() : withExpansions(stems(work.abstractText()));

		List<String> inTitle = new ArrayList<>();
		List<String> inConcepts = new ArrayList<>();
		List<String> inAbstract = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		int strong = 0;
		int any = 0;
		for (QueryTerm term : query.terms()) {
			boolean t = matches(term, title);
			boolean c = matches(term, concepts);
			boolean a = matches(term, abstractText);
			if (t) inTitle.add(term.label());
			if (c) inConcepts.add(term.label());
			if (a) inAbstract.add(term.label());
			if (t || c) strong++;
			if (t || c || a) any++;
			else missing.add(term.label());
		}
		int n = query.terms().size();
		double strongCoverage = n == 0 ? 0 : (double) strong / n;
		double anyCoverage = n == 0 ? 0 : (double) any / n;
		List<String> aspects = aspectCues(title, query.stems());

		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("queryTerms", query.terms().stream().map(QueryTerm::label).toList());
		evidence.put("inTitle", inTitle);
		evidence.put("inConcepts", inConcepts);
		evidence.put("inAbstract", inAbstract);
		evidence.put("missing", missing);
		evidence.put("titleOrConceptCoverage", round(strongCoverage));
		evidence.put("anyCoverage", round(anyCoverage));
		evidence.put("abstractAvailable", work.abstractText() != null);
		evidence.put("aspectCues", aspects);

		if (!aspects.isEmpty() && anyCoverage >= BORDERLINE_COVERAGE) {
			return new Decision(RelevanceTier.TANGENTIAL, "ASPECT_NOT_REQUESTED", evidence);
		}
		if (strongCoverage >= ELIGIBLE_COVERAGE) {
			return new Decision(RelevanceTier.ELIGIBLE, "STRONG_TERM_MATCH", evidence);
		}
		if (anyCoverage >= BORDERLINE_COVERAGE) {
			return new Decision(RelevanceTier.BORDERLINE, "PARTIAL_TERM_MATCH", evidence);
		}
		return new Decision(RelevanceTier.INELIGIBLE, "NO_TERM_EVIDENCE", evidence);
	}

	// ---- text handling ----

	static List<String> tokens(String text) {
		String normalized = TextNormalization.title(text);
		return normalized.isEmpty() ? List.of() : List.of(normalized.split(" "));
	}

	static List<String> stems(String text) {
		return tokens(text).stream().map(RelevancePolicy::stem).toList();
	}

	/** Light English suffix stripping: robotics/robotic→robot, learning→learn, networks→network. */
	static String stem(String token) {
		String t = token;
		if (t.length() > 4 && t.endsWith("ies")) {
			return t.substring(0, t.length() - 3) + "y";
		}
		if (t.length() > 5 && t.endsWith("ics")) {
			return t.substring(0, t.length() - 3);
		}
		if (t.length() > 5 && t.endsWith("ic")) {
			return t.substring(0, t.length() - 2);
		}
		if (t.length() > 5 && t.endsWith("ing")) {
			return t.substring(0, t.length() - 3);
		}
		if (t.length() > 4 && t.endsWith("ed")) {
			return t.substring(0, t.length() - 2);
		}
		if (t.length() > 3 && t.endsWith("s") && !t.endsWith("ss")) {
			return t.substring(0, t.length() - 1);
		}
		return t;
	}

	/** Equal stems, or both ≥ 6 characters sharing their first 6 (diagnosis ≈ diagnostic). */
	static boolean stemMatch(String a, String b) {
		return a.equals(b) || (a.length() >= 6 && b.length() >= 6 && a.regionMatches(0, b, 0, 6));
	}

	private static boolean matches(QueryTerm term, List<String> evidence) {
		for (List<String> phrase : term.alternatives()) {
			if (containsSequence(evidence, phrase)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsSequence(List<String> haystack, List<String> needle) {
		if (needle.isEmpty()) {
			return false;
		}
		for (int i = 0; i + needle.size() <= haystack.size(); i++) {
			boolean all = true;
			for (int j = 0; j < needle.size() && all; j++) {
				all = stemMatch(haystack.get(i + j), needle.get(j));
			}
			if (all) {
				return true;
			}
		}
		return false;
	}

	private static List<String> withExpansions(List<String> stems) {
		List<String> out = new ArrayList<>(stems);
		for (String s : stems) {
			String expansion = EXPANSIONS.get(s);
			if (expansion != null) {
				out.add("|"); // sentinel: expansions must not join with neighbouring tokens
				out.addAll(stems(expansion));
			}
		}
		return out;
	}

	/**
	 * Confident topic/keyword labels as evidence. Labels that themselves name an unrequested aspect
	 * (e.g. "medical education", "AI adoption barriers") are not evidence of technical relevance.
	 */
	private static List<String> conceptStems(Collection<ConceptAssociation> concepts, Set<String> queryStems) {
		List<String> out = new ArrayList<>();
		for (ConceptAssociation c : concepts) {
			double min = c.kind() == ConceptKind.TOPIC ? MIN_TOPIC_SCORE : MIN_KEYWORD_SCORE;
			if (c.score() == null || c.score() < min) {
				continue;
			}
			List<String> label = stems(c.displayName());
			if (!aspectCues(label, queryStems).isEmpty()) {
				continue;
			}
			out.add("|");
			out.addAll(label);
		}
		return out;
	}

	/** Cues match title stems by prefix. ("law" is deliberately absent: power law, Moore's law.) */
	private static boolean cueMatches(String cue, String stem) {
		return stem.startsWith(cue);
	}

	private static List<String> aspectCues(List<String> titleStems, Set<String> queryStems) {
		List<String> found = new ArrayList<>();
		for (String cue : ASPECT_CUES) {
			boolean inTitle = titleStems.stream().anyMatch(s -> cueMatches(cue, s));
			boolean requested = queryStems.stream().anyMatch(q -> cueMatches(cue, q));
			if (!inTitle || requested) {
				continue;
			}
			List<String> technical = TECHNICAL_SENSES.getOrDefault(cue, List.of());
			boolean technicalSense = titleStems.stream().anyMatch(s -> technical.stream().anyMatch(s::startsWith));
			if (!technicalSense) {
				found.add(cue);
			}
		}
		String joinedTitle = String.join(" ", titleStems);
		String joinedQuery = String.join(" ", queryStems);
		for (String phrase : ASPECT_PHRASES) {
			if (joinedTitle.contains(phrase) && !joinedQuery.contains(phrase)) {
				found.add(phrase);
			}
		}
		return found;
	}

	private static double round(double v) {
		return Math.round(v * 100) / 100.0;
	}

}
