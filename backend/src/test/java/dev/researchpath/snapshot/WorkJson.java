package dev.researchpath.snapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.researchpath.openalex.OpenAlexSearchPage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Builds synthetic OpenAlex work records and pages for tests. */
final class WorkJson {

	static final JsonMapper JSON = JsonMapper.builder().build();
	static final Instant RETRIEVED = Instant.parse("2026-10-17T12:00:00Z");

	private final Map<String, Object> fields = new LinkedHashMap<>();
	private final List<String> references = new ArrayList<>();

	private WorkJson(String id, String title) {
		fields.put("id", id == null ? null : "https://openalex.org/" + id);
		fields.put("display_name", title);
		fields.put("relevance_score", 10.0);
	}

	static WorkJson work(String id, String title) {
		return new WorkJson(id, title);
	}

	WorkJson doi(String doi) {
		fields.put("doi", "https://doi.org/" + doi);
		return this;
	}

	WorkJson year(int year) {
		fields.put("publication_year", year);
		return this;
	}

	/** Adds an authorship with an OpenAlex author id (e.g. "A1") and display name. */
	@SuppressWarnings("unchecked")
	WorkJson author(String id, String name) {
		List<Object> authorships = (List<Object>) fields.computeIfAbsent("authorships", k -> new ArrayList<>());
		authorships.add(Map.of("author", Map.of("id", "https://openalex.org/" + id, "display_name", name)));
		return this;
	}

	WorkJson cites(String... ids) {
		for (String id : ids) {
			references.add("https://openalex.org/" + id);
		}
		return this;
	}

	JsonNode json() {
		Map<String, Object> copy = new LinkedHashMap<>(fields);
		copy.put("referenced_works", references);
		return JSON.valueToTree(copy);
	}

	static OpenAlexSearchPage page(int page, long total, WorkJson... works) {
		List<JsonNode> results = new ArrayList<>();
		for (WorkJson w : works) {
			results.add(w.json());
		}
		return new OpenAlexSearchPage(total, page, results, RETRIEVED);
	}

}
