package dev.researchpath.identity;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Conservative identity policy (decision D06, owner-approved 2026-10-17): leave ambiguous versions
 * separate, suppress only obvious duplicates. Pure logic: no Spring, database or network.
 *
 * <p>Rules, first match wins, for a pair already flagged by detection (same DOI or same normalized title):
 * <ol>
 *   <li>Same DOI and same normalized title → SAME_WORK ({@code EXACT_DOI}).</li>
 *   <li>Same DOI, different titles → UNRESOLVED ({@code DOI_TITLE_CONFLICT}): e.g. chapters carrying a book DOI.</li>
 *   <li>Years known and more than {@value #MAX_VERSION_YEAR_GAP} apart → DISTINCT ({@code DISTANT_YEARS}).</li>
 *   <li>Either side has no author data → UNRESOLVED ({@code NO_AUTHOR_DATA}).</li>
 *   <li>No shared author (by OpenAlex id or surname + first initial) → DISTINCT ({@code NO_SHARED_AUTHORS}).</li>
 *   <li>Either year unknown → UNRESOLVED ({@code UNKNOWN_YEAR}).</li>
 *   <li>Same year and DOIs do not conflict (at most one DOI) → SAME_WORK ({@code TITLE_AUTHORS_YEAR}):
 *       repository copies, PubMed/PMC copies, duplicate provider records.</li>
 *   <li>Otherwise (shared authors, ≤ {@value #MAX_VERSION_YEAR_GAP} years apart or conflicting DOIs)
 *       → PROBABLE_VERSION ({@code LIKELY_VERSION}): preprint vs. journal, republished chapter.</li>
 * </ol>
 * A missing DOI never matches another missing DOI. Equal titles alone never suppress anything.
 */
public final class IdentityPolicy {

	/** Bump when rules change; snapshots record the version that produced their decisions. */
	public static final int VERSION = 1;
	public static final int MAX_VERSION_YEAR_GAP = 2;

	private static final Pattern MARKS = Pattern.compile("\\p{M}+");
	private static final Pattern NON_LETTERS = Pattern.compile("[^\\p{L}\\s,]+");

	private IdentityPolicy() {
	}

	public record PairDecision(IdentityDecision decision, String rule, Map<String, Object> details) {
	}

	public static PairDecision classify(IdentityEvidence a, IdentityEvidence b) {
		Map<String, Object> details = new LinkedHashMap<>();
		boolean sameTitle = !a.normalizedTitle().isEmpty() && a.normalizedTitle().equals(b.normalizedTitle());
		String doiRelation = a.doi() == null || b.doi() == null ? (a.doi() == null && b.doi() == null ? "NONE" : "ONE")
				: a.doi().equals(b.doi()) ? "SAME" : "DIFFERENT";
		Integer yearGap = a.year() == null || b.year() == null ? null : Math.abs(a.year() - b.year());
		Set<String> sharedIds = intersection(a.authorIds(), b.authorIds());
		Set<String> sharedNames = intersection(a.authorNameKeys(), b.authorNameKeys());
		details.put("sameNormalizedTitle", sameTitle);
		details.put("doiRelation", doiRelation);
		details.put("yearGap", yearGap);
		details.put("sharedAuthorIds", sharedIds.size());
		details.put("sharedAuthorNameKeys", sharedNames.size());

		if (doiRelation.equals("SAME")) {
			return sameTitle ? new PairDecision(IdentityDecision.SAME_WORK, "EXACT_DOI", details)
					: new PairDecision(IdentityDecision.UNRESOLVED, "DOI_TITLE_CONFLICT", details);
		}
		if (!sameTitle) {
			return new PairDecision(IdentityDecision.UNRESOLVED, "NO_TITLE_OR_DOI_MATCH", details);
		}
		if (yearGap != null && yearGap > MAX_VERSION_YEAR_GAP) {
			return new PairDecision(IdentityDecision.DISTINCT, "DISTANT_YEARS", details);
		}
		if (a.authorCount() == 0 || b.authorCount() == 0) {
			return new PairDecision(IdentityDecision.UNRESOLVED, "NO_AUTHOR_DATA", details);
		}
		if (sharedIds.isEmpty() && sharedNames.isEmpty()) {
			return new PairDecision(IdentityDecision.DISTINCT, "NO_SHARED_AUTHORS", details);
		}
		if (yearGap == null) {
			return new PairDecision(IdentityDecision.UNRESOLVED, "UNKNOWN_YEAR", details);
		}
		if (yearGap == 0 && !doiRelation.equals("DIFFERENT")) {
			return new PairDecision(IdentityDecision.SAME_WORK, "TITLE_AUTHORS_YEAR", details);
		}
		return new PairDecision(IdentityDecision.PROBABLE_VERSION, "LIKELY_VERSION", details);
	}

	/**
	 * Picks the representative of a SAME_WORK group: stable link and metadata quality first, never
	 * citation counts. Order: has DOI, has open-access link, has any safe source link, has abstract,
	 * metadata completeness, then lowest numeric OpenAlex id (deterministic tie-break).
	 */
	public static IdentityEvidence chooseCanonical(Collection<IdentityEvidence> group) {
		return group.stream().min(CANONICAL_ORDER).orElseThrow();
	}

	static final Comparator<IdentityEvidence> CANONICAL_ORDER = Comparator
			.comparing((IdentityEvidence e) -> e.doi() == null)
			.thenComparing(e -> !e.hasOpenAccessLink())
			.thenComparing(e -> !e.hasSourceLink())
			.thenComparing(e -> !e.hasAbstract())
			.thenComparing(e -> -e.metadataCompleteness())
			.thenComparingLong(e -> numericId(e.openalexId()));

	/** Result of resolving one snapshot's members. */
	public record Resolution(Map<Long, PairDecision> decisionsByPairId, Map<Long, Long> canonicalByPublicationId) {
	}

	/** A flagged pair between two publications, identified by its stored pair id. */
	public record CandidatePair(long pairId, long publicationA, long publicationB) {
	}

	/**
	 * Classifies every pair whose two works are both members, groups SAME_WORK pairs transitively,
	 * and maps every member to its canonical representative (itself if not suppressed).
	 */
	public static Resolution resolve(Collection<IdentityEvidence> members, Collection<CandidatePair> pairs) {
		Map<Long, IdentityEvidence> byId = new HashMap<>();
		members.forEach(m -> byId.put(m.publicationId(), m));
		Map<Long, Long> parent = new HashMap<>();
		byId.keySet().forEach(id -> parent.put(id, id));

		Map<Long, PairDecision> decisions = new TreeMap<>();
		for (CandidatePair pair : pairs) {
			IdentityEvidence a = byId.get(pair.publicationA());
			IdentityEvidence b = byId.get(pair.publicationB());
			if (a == null || b == null) {
				continue; // only pairs inside this snapshot matter here
			}
			PairDecision decision = classify(a, b);
			decisions.put(pair.pairId(), decision);
			if (decision.decision().suppresses()) {
				union(parent, a.publicationId(), b.publicationId());
			}
		}

		Map<Long, List<IdentityEvidence>> groups = new HashMap<>();
		for (IdentityEvidence m : byId.values()) {
			groups.computeIfAbsent(find(parent, m.publicationId()), k -> new ArrayList<>()).add(m);
		}
		Map<Long, Long> canonical = new TreeMap<>();
		for (List<IdentityEvidence> group : groups.values()) {
			long representative = chooseCanonical(group).publicationId();
			group.forEach(m -> canonical.put(m.publicationId(), representative));
		}
		return new Resolution(decisions, canonical);
	}

	/**
	 * Author-name key "surname initial", accent- and case-insensitive. Handles "Last, First" and
	 * "First Middle Last": {@code "Roffe, J."} and {@code "Joschka Roffe"} both give {@code "roffe j"}.
	 * Returns null when no usable name exists.
	 */
	public static String nameKey(String displayName) {
		if (displayName == null) {
			return null;
		}
		String folded = MARKS.matcher(Normalizer.normalize(displayName, Normalizer.Form.NFKD)).replaceAll("")
				.toLowerCase(Locale.ROOT);
		folded = NON_LETTERS.matcher(folded).replaceAll(" ").trim();
		if (folded.isEmpty()) {
			return null;
		}
		String surname;
		String given;
		int comma = folded.indexOf(',');
		if (comma >= 0) {
			surname = folded.substring(0, comma).trim();
			given = folded.substring(comma + 1).trim();
		} else {
			String[] tokens = folded.split("\\s+");
			surname = tokens[tokens.length - 1];
			given = tokens.length > 1 ? tokens[0] : "";
		}
		surname = surname.replaceAll("\\s+", " ");
		if (surname.isEmpty()) {
			return null;
		}
		return given.isEmpty() ? surname : surname + " " + given.charAt(0);
	}

	private static long numericId(String openalexId) {
		try {
			return Long.parseLong(openalexId.substring(1));
		} catch (RuntimeException e) {
			return Long.MAX_VALUE;
		}
	}

	private static <T> Set<T> intersection(Set<T> a, Set<T> b) {
		Set<T> result = new HashSet<>(a);
		result.retainAll(b);
		return result;
	}

	private static long find(Map<Long, Long> parent, long x) {
		long root = x;
		while (parent.get(root) != root) {
			root = parent.get(root);
		}
		parent.put(x, root);
		return root;
	}

	private static void union(Map<Long, Long> parent, long a, long b) {
		long ra = find(parent, a);
		long rb = find(parent, b);
		if (ra != rb) {
			parent.put(Math.max(ra, rb), Math.min(ra, rb));
		}
	}

}
