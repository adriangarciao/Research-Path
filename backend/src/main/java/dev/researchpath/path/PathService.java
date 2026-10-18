package dev.researchpath.path;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import dev.researchpath.benchmark.BenchmarkDataset;
import dev.researchpath.benchmark.DatasetExportService;
import dev.researchpath.snapshot.CandidateSnapshotService;
import dev.researchpath.snapshot.FrozenCandidate;
import dev.researchpath.snapshot.SnapshotNotEvaluatedException;
import dev.researchpath.snapshot.SnapshotSummary;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Generates, stores and regenerates reading paths (playbook M11, decision D15).
 * <ul>
 * <li>A new path retrieves a fresh candidate snapshot (the only step that calls the provider).</li>
 * <li>Regeneration always reuses the path's original frozen snapshot: no provider calls, no cache reads.</li>
 * <li>Only complete results are stored, in one transaction; stored revisions are immutable and returned as
 * stored.</li>
 * <li>Identical inputs (snapshot, options, engine versions) never create a second revision; an optional
 * idempotency key makes a repeated "new path" request return the first result instead of retrieving again.</li>
 * </ul>
 */
@Service
public class PathService {

	private static final Logger log = LoggerFactory.getLogger(PathService.class);

	public static final Pattern PUBLIC_ID = Pattern.compile("^[A-Za-z0-9_-]{22}$");

	public static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

	private static final SecureRandom RANDOM = new SecureRandom();

	private final CandidateSnapshotService snapshots;
	private final PathRepository repository;
	private final TransactionTemplate transaction;
	private final JsonMapper json;
	private final PathProperties properties;
	private final Semaphore generations;
	private final Map<String, CompletableFuture<PathView>> inFlight = new ConcurrentHashMap<>();

	public PathService(CandidateSnapshotService snapshots, PathRepository repository, TransactionTemplate transaction,
			JsonMapper json, PathProperties properties) {
		this.snapshots = snapshots;
		this.repository = repository;
		this.transaction = transaction;
		this.json = json;
		this.properties = properties;
		this.generations = new Semaphore(properties.maxConcurrentGenerations());
	}

	/**
	 * A stored revision as returned to clients: the stored document plus the path's revision list.
	 *
	 * @param created true if this call stored a new revision, false if an identical one already existed
	 */
	public record PathView(String pathId, int revision, JsonNode body, boolean created) {
	}

	/** Creates a path for a topic: retrieves a new snapshot, then generates revision 1. */
	public PathView create(String topic, PathOptions options, String idempotencyKey) {
		if (idempotencyKey == null) {
			return bounded(() -> createNow(topic, options, null));
		}
		if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
			throw new IllegalArgumentException("Idempotency-Key must be 8-64 characters of A-Z, a-z, 0-9, '-' or '_'");
		}
		Optional<PathRepository.PathRow> existing = repository.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			return replay(existing.get(), topic, options);
		}
		// A second request with the same key while the first runs waits for it instead of retrieving again.
		CompletableFuture<PathView> mine = new CompletableFuture<>();
		CompletableFuture<PathView> running = inFlight.putIfAbsent(idempotencyKey, mine);
		if (running != null) {
			PathView first = join(running);
			return new PathView(first.pathId(), first.revision(), first.body(), false);
		}
		try {
			PathView view = bounded(() -> createNow(topic, options, idempotencyKey));
			mine.complete(view);
			return view;
		} catch (RuntimeException e) {
			mine.completeExceptionally(e);
			throw e;
		} finally {
			inFlight.remove(idempotencyKey, mine);
		}
	}

	/** Regenerates from the path's original snapshot with new options, as a child of {@code parentRevision}. */
	public PathView regenerate(String publicId, PathOptions options, Integer parentRevision) {
		PathRepository.PathRow path = find(publicId);
		int latest = repository.latestRevision(path.id());
		int parent = parentRevision == null ? latest : parentRevision;
		if (parent < 1 || parent > latest) {
			throw new IllegalArgumentException("parentRevision must be an existing revision (1-" + latest + ")");
		}
		return bounded(() -> {
			Pool pool = pool(path.snapshotId());
			PathEngine.Generated g = generate(pool, options, path.topic());
			String fingerprint = fingerprint(path.snapshotId(), options);
			return transaction.execute(status -> {
				repository.lock(path.id());
				Optional<Integer> same = repository.revisionWithFingerprint(path.id(), fingerprint);
				if (same.isPresent()) {
					return view(path, same.get(), false);
				}
				int next = repository.latestRevision(path.id()) + 1;
				if (next > properties.maxRevisionsPerPath()) {
					throw new PathExceptions.Conflict("This path reached its limit of " + properties.maxRevisionsPerPath()
							+ " revisions; start a new path instead.");
				}
				store(path, next, parent, options, fingerprint, pool, g);
				return view(path, next, true);
			});
		});
	}

	/** The latest revision, or a specific one. */
	public PathView get(String publicId, Integer revision) {
		PathRepository.PathRow path = find(publicId);
		int r = revision == null ? repository.latestRevision(path.id()) : revision;
		if (r < 1) {
			throw new PathExceptions.NotFound();
		}
		return view(path, r, false);
	}

	private PathView createNow(String topic, PathOptions options, String idempotencyKey) {
		SnapshotSummary summary = snapshots.createSnapshot(topic, null);
		Pool pool = pool(summary.id());
		PathEngine.Generated g = generate(pool, options, summary.topic());
		Instant now = now();
		PathRepository.PathRow path = new PathRepository.PathRow(UUID.randomUUID(), newPublicId(), summary.id(),
				summary.topic(), idempotencyKey, now);
		String fingerprint = fingerprint(summary.id(), options);
		try {
			transaction.executeWithoutResult(status -> {
				repository.insertPath(path);
				store(path, 1, null, options, fingerprint, pool, g);
			});
		} catch (DuplicateKeyException e) {
			// The same idempotency key committed concurrently on another instance: return that path.
			if (idempotencyKey != null) {
				return repository.findByIdempotencyKey(idempotencyKey).map(p -> view(p, 1, false)).orElseThrow(() -> e);
			}
			throw e;
		}
		log.info("Created path {} (snapshot {}, {} papers)", path.publicId(), summary.id(), g.path().items().size());
		return view(path, 1, true);
	}

	private PathView replay(PathRepository.PathRow path, String topic, PathOptions options) {
		PathView first = view(path, 1, false);
		JsonNode o = first.body().path("options");
		boolean same = cleanTopic(topic).equalsIgnoreCase(cleanTopic(path.topic()))
				&& o.path("experience").asString().equals(options.experience().name())
				&& o.path("focus").asString().equals(options.focus().name())
				&& o.path("budget").asInt() == options.budget();
		if (!same) {
			throw new PathExceptions.Conflict("Idempotency-Key was already used for a different request.");
		}
		return first;
	}

	private record Pool(SnapshotSummary summary, BenchmarkDataset dataset, Map<String, FrozenCandidate> frozen) {
	}

	/** The snapshot's frozen optimization candidates (never the refreshable cache, never the provider). */
	private Pool pool(UUID snapshotId) {
		SnapshotSummary summary = snapshots.findSummary(snapshotId).orElseThrow(PathExceptions.NotFound::new);
		if (summary.relevance() == null) {
			throw new SnapshotNotEvaluatedException(snapshotId);
		}
		List<FrozenCandidate> frozen = snapshots.optimizationCandidates(snapshotId);
		Map<String, FrozenCandidate> byId = new HashMap<>();
		frozen.forEach(c -> byId.put(c.work().openalexId(), c));
		return new Pool(summary, DatasetExportService.dataset(summary, frozen), byId);
	}

	private static PathEngine.Generated generate(Pool pool, PathOptions options, String topic) {
		Map<String, Boolean> abstracts = new HashMap<>();
		pool.frozen().forEach((id, c) -> abstracts.put(id, c.work().hasAbstract()));
		PathEngine.Generated g = PathEngine.run(pool.dataset(), options, abstracts);
		if (g.path().items().isEmpty()) {
			throw new PathExceptions.NoEligiblePapers(topic);
		}
		return g;
	}

	private void store(PathRepository.PathRow path, int revision, Integer parent, PathOptions options,
			String fingerprint, Pool pool, PathEngine.Generated g) {
		Instant now = revision == 1 ? path.createdAt() : now();
		PathDocument.Result doc = PathDocument.build(path.publicId(), revision, parent, now, pool.summary(),
				pool.dataset(), options, g, pool.frozen());
		List<PathRepository.ItemRow> rows = g.path().items().stream().map(i -> {
			var decision = g.selection().decisions().stream().filter(d -> d.id().equals(i.id())).findFirst().orElseThrow();
			return new PathRepository.ItemRow(i.position(), pool.frozen().get(i.id()).publicationId(), i.id(), i.stage(),
					i.selectionStep(), decision.marginalGain(), decision.reason().name());
		}).toList();
		repository.insertRevision(UUID.randomUUID(), path.id(), revision, parent, options,
				json.writeValueAsString(PathEngine.versions()), fingerprint, json.writeValueAsString(doc), now, rows);
	}

	private PathView view(PathRepository.PathRow path, int revision, boolean created) {
		String stored = repository.result(path.id(), revision).orElseThrow(PathExceptions.NotFound::new);
		ObjectNode body = (ObjectNode) json.readTree(stored);
		body.set("revisions", json.valueToTree(repository.revisions(path.id())));
		return new PathView(path.publicId(), revision, body, created);
	}

	private PathRepository.PathRow find(String publicId) {
		if (publicId == null || !PUBLIC_ID.matcher(publicId).matches()) {
			throw new PathExceptions.NotFound();
		}
		return repository.findByPublicId(publicId).orElseThrow(PathExceptions.NotFound::new);
	}

	private PathView bounded(Supplier<PathView> work) {
		if (!generations.tryAcquire()) {
			throw new PathExceptions.Busy();
		}
		try {
			return work.get();
		} finally {
			generations.release();
		}
	}

	static String fingerprint(UUID snapshotId, PathOptions options) {
		String canonical = snapshotId + "|" + options.experience() + "|" + options.focus() + "|" + options.budget() + "|"
				+ PathEngine.versions();
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	static String newPublicId() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String cleanTopic(String topic) {
		return topic == null ? "" : topic.strip().replaceAll("\\s+", " ");
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	private static PathView join(CompletableFuture<PathView> future) {
		try {
			return future.join();
		} catch (CompletionException e) {
			if (e.getCause() instanceof RuntimeException r) {
				throw r;
			}
			throw e;
		}
	}

}
