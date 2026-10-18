package dev.researchpath.snapshot;

import dev.researchpath.openalex.OpenAlexSearchPage;

/** Public entry points to the package-private {@link WorkJson} test builder for other test packages. */
public final class WorkJsonAccess {

	private WorkJsonAccess() {
	}

	public static Builder work(String id, String title) {
		return new Builder(WorkJson.work(id, title));
	}

	public static OpenAlexSearchPage page(int page, long total, Builder... works) {
		WorkJson[] raw = new WorkJson[works.length];
		for (int i = 0; i < works.length; i++) {
			raw[i] = works[i].w;
		}
		return WorkJson.page(page, total, raw);
	}

	public static final class Builder {
		private final WorkJson w;

		private Builder(WorkJson w) {
			this.w = w;
		}

		public Builder year(int year) { w.year(year); return this; }
		public Builder doi(String doi) { w.doi(doi); return this; }
		public Builder author(String id, String name) { w.author(id, name); return this; }
		public Builder cites(String... ids) { w.cites(ids); return this; }
	}

}
