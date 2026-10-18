package dev.researchpath.publication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import dev.researchpath.publication.NormalizationResult.Accepted;
import dev.researchpath.publication.NormalizedWork.SourceLink;
import dev.researchpath.publication.NormalizedWork.SourceLinkKind;
import tools.jackson.databind.json.JsonMapper;

class UrlSafetyTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@ParameterizedTest
	@ValueSource(strings = {
			"https://doi.org/10.1109/tnn.2008.2005605",
			"https://arxiv.org/pdf/2003.00982",
			"http://repository.example.edu/handle/123",
			"HTTPS://Example.org/Path?q=1#frag" })
	void acceptsAbsoluteHttpUrlsWithHost(String url) {
		assertThat(UrlSafety.safeDisplayUrl(url)).isEqualTo(url);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
			"javascript:alert(1)",
			"JavaScript:alert(document.cookie)",
			"data:text/html,<script>alert(1)</script>",
			"file:///C:/Windows/win.ini",
			"ftp://example.org/paper.pdf",
			"//example.org/no-scheme",
			"/relative/path",
			"https://user:password@example.org/",
			"https://evil.example@bank.example/",
			"https:///missing-host",
			"https://exa mple.org/host-with-space",
			"https://example.org/tab	char",
			"https://example.org/\nnewline",
			"   ",
			"not a url" })
	void rejectsUnsafeOrMalformedUrls(String url) {
		assertThat(UrlSafety.safeDisplayUrl(url)).isNull();
	}

	@Test
	void rejectsOverlongUrlsAndTrimsWhitespace() {
		assertThat(UrlSafety.safeDisplayUrl("https://example.org/" + "a".repeat(UrlSafety.MAX_LENGTH))).isNull();
		assertThat(UrlSafety.safeDisplayUrl("  https://example.org/x  ")).isEqualTo("https://example.org/x");
	}

	@Test
	void interiorSpacesInRealRepositoryUrlsArePercentEncoded() {
		// Observed in live OpenAlex data (W7196959258).
		assertThat(UrlSafety.safeDisplayUrl(
				"https://www.rademics.com/upload/178496514715519954486a64681b163c3preview 10.pdf"))
				.isEqualTo("https://www.rademics.com/upload/178496514715519954486a64681b163c3preview%2010.pdf");
	}

	@Test
	void doiUrlIsBuiltOnlyWhenSafe() {
		assertThat(UrlSafety.doiUrl("10.1038/s43586-024-00294-7")).isEqualTo("https://doi.org/10.1038/s43586-024-00294-7");
		assertThat(UrlSafety.doiUrl("10.1002/(sici)1097-4571(199806)49:8<693::aid-asi4>3.0.co;2-o")).isNull();
		assertThat(UrlSafety.doiUrl(null)).isNull();
	}

	private static NormalizedWork work(String json) {
		return ((Accepted) new OpenAlexWorkNormalizer().normalize(JSON.readTree(json))).work();
	}

	@Test
	void normalizerDropsUnsafeProviderUrls() {
		NormalizedWork w = work("""
				{"id": "https://openalex.org/W1", "display_name": "t",
				 "primary_location": {"landing_page_url": "javascript:alert(1)"},
				 "open_access": {"is_oa": true, "oa_url": "data:text/html,x"}}""");
		assertThat(w.landingPageUrl()).isNull();
		assertThat(w.oaUrl()).isNull();
		assertThat(w.isOa()).isTrue(); // the provider's claim is kept; only the unsafe link is dropped
		assertThat(w.preferredSourceLink()).isEmpty();
	}

	@Test
	void linkPreferenceIsOpenAccessThenDoiThenLandingPage() {
		String base = "\"id\": \"https://openalex.org/W1\", \"display_name\": \"t\", "
				+ "\"primary_location\": {\"landing_page_url\": \"https://publisher.example/article/1\"}";

		assertThat(work("{" + base + ", \"doi\": \"10.1/x\", \"open_access\": {\"oa_url\": \"https://arxiv.org/pdf/1\"}}")
				.preferredSourceLink()).contains(new SourceLink("https://arxiv.org/pdf/1", SourceLinkKind.OPEN_ACCESS));
		assertThat(work("{" + base + ", \"doi\": \"10.1/x\"}").preferredSourceLink())
				.contains(new SourceLink("https://doi.org/10.1/x", SourceLinkKind.DOI));
		assertThat(work("{" + base + "}").preferredSourceLink())
				.contains(new SourceLink("https://publisher.example/article/1", SourceLinkKind.LANDING_PAGE));
	}

}
