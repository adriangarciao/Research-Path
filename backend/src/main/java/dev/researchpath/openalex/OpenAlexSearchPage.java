package dev.researchpath.openalex;

import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * One page of raw work records, kept as provider JSON so snapshots can preserve them exactly.
 *
 * @param totalCount provider-reported number of matching works (null if absent)
 */
public record OpenAlexSearchPage(Long totalCount, int page, List<JsonNode> results, Instant retrievedAt) {

	public OpenAlexSearchPage {
		results = List.copyOf(results);
	}

}
