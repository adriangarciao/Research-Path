package dev.researchpath.snapshot;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import dev.researchpath.identity.IdentityResolutionService;
import dev.researchpath.openalex.OpenAlexClient;
import dev.researchpath.openalex.OpenAlexProperties;
import dev.researchpath.openalex.OpenAlexSearchPage;
import dev.researchpath.publication.NormalizationResult;
import dev.researchpath.publication.NormalizationResult.Accepted;
import dev.researchpath.publication.NormalizationResult.Rejected;
import dev.researchpath.publication.NormalizedWork;
import dev.researchpath.publication.OpenAlexWorkNormalizer;
import dev.researchpath.publication.PublicationRepository;
import dev.researchpath.publication.TextNormalization;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Retrieves a bounded candidate pool from OpenAlex, normalizes it, refreshes the publication
 * cache and freezes the pool as a candidate snapshot (blueprint §3.5 steps 1-3 and 6).
 *
 * <p>Obvious duplicates are suppressed by the conservative identity policy (D06), frozen with the
 * snapshot. Not done here yet: the query-relevance gate (D07 is OPEN), so every member is UNSCREENED.
 */
@Service
public class CandidateSnapshotService {

	public static final String PROVIDER = "OPENALEX";

	private static final Logger log = LoggerFactory.getLogger(CandidateSnapshotService.class);

	private final OpenAlexClient openAlex;
	private final OpenAlexProperties openAlexProperties;
	private final IngestionProperties ingestion;
	private final OpenAlexWorkNormalizer normalizer;
	private final PublicationRepository publications;
	private final SnapshotRepository snapshots;
	private final IdentityResolutionService identity;
	private final TransactionTemplate transaction;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	public CandidateSnapshotService(OpenAlexClient openAlex, OpenAlexProperties openAlexProperties,
			IngestionProperties ingestion, OpenAlexWorkNormalizer normalizer, PublicationRepository publications,
			SnapshotRepository snapshots, IdentityResolutionService identity, TransactionTemplate transaction,
			JsonMapper jsonMapper, Clock clock) {
		this.openAlex = openAlex;
		this.openAlexProperties = openAlexProperties;
		this.ingestion = ingestion;
		this.normalizer = normalizer;
		this.publications = publications;
		this.snapshots = snapshots;
		this.identity = identity;
		this.transaction = transaction;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	public SnapshotSummary createSnapshot(String topic, Integer maxCandidates) {
		String cleanTopic = topic == null ? "" : topic.trim().replaceAll("\\s+", " ");
		if (cleanTopic.isEmpty()) {
			throw new IllegalArgumentException("topic must not be blank");
		}
		if (cleanTopic.length() > ingestion.maxTopicLength()) {
			throw new IllegalArgumentException("topic must be at most " + ingestion.maxTopicLength() + " characters");
		}
		int requested = maxCandidates == null ? ingestion.defaultCandidates() : maxCandidates;
		if (requested < 1 || requested > ingestion.maxCandidates()) {
			throw new IllegalArgumentException("maxCandidates must be between 1 and " + ingestion.maxCandidates());
		}

		// 1. Retrieve (outside any DB transaction).
		int perPage = Math.min(requested, openAlexProperties.maxPerPage());
		List<RetrievedRecord> received = new ArrayList<>();
		List<Integer> pagesFetched = new ArrayList<>();
		Long providerTotal = null;
		for (int page = 1; received.size() < requested; page++) {
			OpenAlexSearchPage result = openAlex.searchWorks(cleanTopic, page, perPage);
			pagesFetched.add(page);
			if (providerTotal == null) {
				providerTotal = result.totalCount();
			}
			for (JsonNode work : result.results()) {
				if (received.size() < requested) {
					received.add(new RetrievedRecord(received.size() + 1, work, result.retrievedAt()));
				}
			}
			if (result.results().size() < perPage) {
				break; // provider has no more results
			}
		}

		// 2. Normalize; drop unusable records and within-snapshot repeats (page drift), noting both.
		List<Map<String, Object>> notes = new ArrayList<>();
		List<Candidate> candidates = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		int rejected = 0;
		for (RetrievedRecord record : received) {
			NormalizationResult result = normalizer.normalize(record.json());
			switch (result) {
				case Rejected r -> {
					rejected++;
					notes.add(note("REJECTED", record.rank(), r.providerId(), r.reason().name()));
				}
				case Accepted a -> {
					if (seen.add(a.work().openalexId())) {
						candidates.add(new Candidate(record, a.work()));
					} else {
						notes.add(note("DUPLICATE_IN_RESPONSE", record.rank(), a.work().openalexId(),
								"same work returned on more than one page"));
					}
				}
			}
		}

		// 3. Persist atomically: refresh publication cache, then freeze the snapshot.
		UUID snapshotId = UUID.randomUUID();
		Instant createdAt = clock.instant();
		String params = json(retrievalParams(cleanTopic, perPage, pagesFetched));
		String notesJson = json(notes);
		int storedCount = candidates.size();
		int rejectedCount = rejected;
		Long total = providerTotal;
		transaction.executeWithoutResult(status -> {
			snapshots.insert(new SnapshotRepository.NewSnapshot(snapshotId, cleanTopic,
					TextNormalization.topic(cleanTopic), PROVIDER, params, requested, total, received.size(),
					storedCount, rejectedCount, OpenAlexWorkNormalizer.VERSION, notesJson, createdAt));
			for (Candidate c : candidates) {
				long publicationId = publications.upsert(c.work(), c.record().retrievedAt());
				JsonNode score = c.record().json().path("relevance_score");
				snapshots.addMember(snapshotId, publicationId, c.record().rank(),
						score.isNumber() ? score.doubleValue() : null, c.record().json().toString(),
						c.record().retrievedAt());
			}
			// Conservative identity resolution (D06), frozen with the snapshot.
			identity.resolveSnapshot(snapshotId);
		});
		log.info("Created snapshot {} for topic '{}': received={}, stored={}, rejected={}", snapshotId, cleanTopic,
				received.size(), storedCount, rejectedCount);

		return snapshots.findSummary(snapshotId).orElseThrow();
	}

	public Optional<SnapshotSummary> findSummary(UUID id) {
		return snapshots.findSummary(id);
	}

	private Map<String, Object> retrievalParams(String topic, int perPage, List<Integer> pages) {
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("endpoint", "/works");
		params.put("search", topic);
		params.put("search_mode", "OpenAlex search= (full-text), provider default relevance order");
		params.put("per_page", perPage);
		params.put("pages", pages);
		params.put("select", OpenAlexClient.WORK_FIELDS);
		params.put("authenticated", openAlexProperties.hasApiKey());
		return params;
	}

	private static Map<String, Object> note(String type, int rank, String providerId, String detail) {
		Map<String, Object> note = new LinkedHashMap<>();
		note.put("type", type);
		note.put("provider_rank", rank);
		note.put("provider_id", providerId);
		note.put("detail", detail);
		return note;
	}

	private String json(Object value) {
		return jsonMapper.writeValueAsString(value);
	}

	private record RetrievedRecord(int rank, JsonNode json, Instant retrievedAt) {
	}

	private record Candidate(RetrievedRecord record, NormalizedWork work) {
	}

}
