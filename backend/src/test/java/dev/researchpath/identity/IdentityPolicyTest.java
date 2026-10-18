package dev.researchpath.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import dev.researchpath.identity.IdentityPolicy.CandidatePair;
import dev.researchpath.identity.IdentityPolicy.PairDecision;
import dev.researchpath.identity.IdentityPolicy.Resolution;

class IdentityPolicyTest {

	/** Small builder for evidence; defaults describe a complete record. */
	static final class W {
		long pid;
		String id;
		String doi;
		String title = "a survey of graph neural networks";
		Integer year = 2020;
		String type = "article";
		Set<String> authorIds = new HashSet<>();
		Set<String> names = new HashSet<>();
		boolean oa;
		boolean link = true;
		boolean abs = true;
		int completeness = 4;

		static W w(long pid) {
			W w = new W();
			w.pid = pid;
			w.id = "W" + pid;
			return w;
		}

		W doi(String d) { doi = d; return this; }
		W title(String t) { title = t; return this; }
		W year(Integer y) { year = y; return this; }
		W authors(String... ids) { authorIds.addAll(List.of(ids)); return this; }
		W names(String... ns) { for (String n : ns) { names.add(IdentityPolicy.nameKey(n)); } return this; }
		W oa(boolean v) { oa = v; return this; }
		W link(boolean v) { link = v; return this; }
		W abs(boolean v) { abs = v; return this; }
		W completeness(int c) { completeness = c; return this; }

		IdentityEvidence e() {
			int count = Math.max(authorIds.size(), names.size());
			return new IdentityEvidence(pid, id, doi, title, year, type, authorIds, names, count, oa, link, abs,
					completeness);
		}
	}

	private static PairDecision classify(W a, W b) {
		return IdentityPolicy.classify(a.e(), b.e());
	}

	@Test
	void sameDoiAndSameTitleIsSameWork() {
		PairDecision d = classify(W.w(1).doi("10.1/x"), W.w(2).doi("10.1/x"));
		assertThat(d.decision()).isEqualTo(IdentityDecision.SAME_WORK);
		assertThat(d.rule()).isEqualTo("EXACT_DOI");
	}

	@Test
	void sameDoiWithDifferentTitlesIsUnresolvedNotMerged() {
		PairDecision d = classify(W.w(1).doi("10.1/book").title("chapter one"),
				W.w(2).doi("10.1/book").title("chapter two"));
		assertThat(d.decision()).isEqualTo(IdentityDecision.UNRESOLVED);
		assertThat(d.rule()).isEqualTo("DOI_TITLE_CONFLICT");
	}

	@Test
	void missingDoiNeverMatchesMissingDoi() {
		// Same title, no authors in common: two missing DOIs must not count as a DOI match.
		PairDecision d = classify(W.w(1).authors("A1"), W.w(2).authors("A2"));
		assertThat(d.details()).containsEntry("doiRelation", "NONE");
		assertThat(d.decision()).isEqualTo(IdentityDecision.DISTINCT);
	}

	@Test
	void sameTitleSameYearSharedAuthorAndAtMostOneDoiIsSameWork() {
		assertThat(classify(W.w(1).doi("10.1/x").authors("A1", "A2"), W.w(2).authors("A2")).decision())
				.isEqualTo(IdentityDecision.SAME_WORK);
		assertThat(classify(W.w(1).authors("A1"), W.w(2).authors("A1")).rule()).isEqualTo("TITLE_AUTHORS_YEAR");
	}

	@Test
	void sharedAuthorByNameKeyCountsWhenIdsDiffer() {
		// Real case: "Joschka Roffe" vs "Roffe, J." with different OpenAlex author ids.
		PairDecision d = classify(W.w(1).doi("10.1/x").authors("A1").names("Joschka Roffe"),
				W.w(2).authors("A9").names("Roffe, J."));
		assertThat(d.decision()).isEqualTo(IdentityDecision.SAME_WORK);
		assertThat(d.details()).containsEntry("sharedAuthorIds", 0).containsEntry("sharedAuthorNameKeys", 1);
	}

	@Test
	void conflictingDoisInTheSameYearAreProbableVersionNotSameWork() {
		// Real case: Caltech thesis DOI vs arXiv DOI, same year and author.
		PairDecision d = classify(W.w(1).doi("10.7907/thesis").authors("A1"),
				W.w(2).doi("10.48550/arxiv.x").authors("A1"));
		assertThat(d.decision()).isEqualTo(IdentityDecision.PROBABLE_VERSION);
		assertThat(d.decision().suppresses()).isFalse();
	}

	@Test
	void sharedAuthorsWithinTwoYearsIsProbableVersion() {
		assertThat(classify(W.w(1).year(2013).authors("A1"), W.w(2).year(2012).authors("A1")).decision())
				.isEqualTo(IdentityDecision.PROBABLE_VERSION);
		assertThat(classify(W.w(1).year(2019).authors("A1"), W.w(2).year(2017).authors("A1")).decision())
				.isEqualTo(IdentityDecision.PROBABLE_VERSION);
	}

	@Test
	void distantYearsAreDistinctEvenWithSharedAuthors() {
		PairDecision d = classify(W.w(1).year(2017).authors("A1"), W.w(2).year(2012).authors("A1"));
		assertThat(d.decision()).isEqualTo(IdentityDecision.DISTINCT);
		assertThat(d.rule()).isEqualTo("DISTANT_YEARS");
	}

	@Test
	void noSharedAuthorsIsDistinct() {
		assertThat(classify(W.w(1).authors("A1"), W.w(2).authors("A2")).rule()).isEqualTo("NO_SHARED_AUTHORS");
	}

	@Test
	void missingAuthorDataOrYearIsUnresolved() {
		assertThat(classify(W.w(1).authors("A1"), W.w(2)).rule()).isEqualTo("NO_AUTHOR_DATA");
		assertThat(classify(W.w(1).authors("A1").year(null), W.w(2).authors("A1")).rule()).isEqualTo("UNKNOWN_YEAR");
	}

	@Test
	void onlySameWorkSuppresses() {
		assertThat(Stream.of(IdentityDecision.values()).filter(IdentityDecision::suppresses))
				.containsExactly(IdentityDecision.SAME_WORK);
	}

	@Test
	void canonicalPrefersDoiThenOpenAccessThenLinkThenAbstractThenCompletenessThenLowestId() {
		IdentityEvidence noDoi = W.w(1).oa(true).e();
		IdentityEvidence doi = W.w(9).doi("10.1/x").e();
		assertThat(IdentityPolicy.chooseCanonical(List.of(noDoi, doi))).isEqualTo(doi);

		IdentityEvidence doiOa = W.w(8).doi("10.1/x").oa(true).e();
		assertThat(IdentityPolicy.chooseCanonical(List.of(doi, doiOa))).isEqualTo(doiOa);

		IdentityEvidence noLink = W.w(2).link(false).e();
		IdentityEvidence withLink = W.w(3).e();
		assertThat(IdentityPolicy.chooseCanonical(List.of(noLink, withLink))).isEqualTo(withLink);

		IdentityEvidence noAbstract = W.w(2).abs(false).e();
		assertThat(IdentityPolicy.chooseCanonical(List.of(noAbstract, withLink))).isEqualTo(withLink);

		IdentityEvidence richer = W.w(7).completeness(5).e();
		assertThat(IdentityPolicy.chooseCanonical(List.of(withLink, richer))).isEqualTo(richer);

		IdentityEvidence w4210257598 = W.w(4210257598L).e();
		IdentityEvidence w2907492528 = W.w(2907492528L).e();
		assertThat(IdentityPolicy.chooseCanonical(List.of(w4210257598, w2907492528))).isEqualTo(w2907492528);
	}

	@Test
	void resolveGroupsSameWorkTransitivelyAndKeepsOthersSeparate() {
		IdentityEvidence a = W.w(1).doi("10.1/a").authors("X").e();
		IdentityEvidence b = W.w(2).authors("X").e(); // same work as a (one DOI, same year, shared author)
		IdentityEvidence c = W.w(3).authors("X").e(); // same work as b
		IdentityEvidence d = W.w(4).year(2019).doi("10.1/d").authors("X").e(); // probable version of a
		IdentityEvidence e = W.w(5).authors("Y").e(); // same title, different authors
		// Publication 6 (pair 14) is not a member of this snapshot.

		Resolution r = IdentityPolicy.resolve(List.of(a, b, c, d, e), List.of(
				new CandidatePair(10, 1, 2), new CandidatePair(11, 2, 3), new CandidatePair(12, 1, 4),
				new CandidatePair(13, 1, 5), new CandidatePair(14, 1, 6)));

		assertThat(r.canonicalByPublicationId()).isEqualTo(Map.of(1L, 1L, 2L, 1L, 3L, 1L, 4L, 4L, 5L, 5L));
		assertThat(r.decisionsByPairId().keySet()).containsExactly(10L, 11L, 12L, 13L); // pair 14 not in snapshot
		assertThat(r.decisionsByPairId().get(12L).decision()).isEqualTo(IdentityDecision.PROBABLE_VERSION);
		assertThat(r.decisionsByPairId().get(13L).decision()).isEqualTo(IdentityDecision.DISTINCT);
	}

	@Test
	void resolveIsDeterministicRegardlessOfInputOrder() {
		IdentityEvidence a = W.w(5).authors("X").e();
		IdentityEvidence b = W.w(3).authors("X").e();
		IdentityEvidence c = W.w(4).authors("X").e();
		List<CandidatePair> pairs = List.of(new CandidatePair(1, 5, 3), new CandidatePair(2, 3, 4));
		Resolution r1 = IdentityPolicy.resolve(List.of(a, b, c), pairs);
		Resolution r2 = IdentityPolicy.resolve(List.of(c, a, b), pairs.reversed());
		assertThat(r1.canonicalByPublicationId()).isEqualTo(r2.canonicalByPublicationId());
		assertThat(r1.canonicalByPublicationId().values().stream().collect(Collectors.toSet())).containsExactly(3L);
	}

	@Test
	void nameKeysHandleRealAuthorFormats() {
		assertThat(IdentityPolicy.nameKey("Joschka Roffe")).isEqualTo("roffe j");
		assertThat(IdentityPolicy.nameKey("Roffe, J.")).isEqualTo("roffe j");
		assertThat(IdentityPolicy.nameKey("Zonghan Wu")).isEqualTo(IdentityPolicy.nameKey("Wu, Z"));
		assertThat(IdentityPolicy.nameKey("Philip S. Yu")).isEqualTo(IdentityPolicy.nameKey("Yu, PS"));
		assertThat(IdentityPolicy.nameKey("Marinka Žitnik")).isEqualTo("zitnik m");
		assertThat(IdentityPolicy.nameKey("Ijrei")).isEqualTo("ijrei");
		assertThat(IdentityPolicy.nameKey("  ")).isNull();
		assertThat(IdentityPolicy.nameKey(null)).isNull();
	}

}
