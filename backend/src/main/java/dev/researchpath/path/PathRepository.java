package dev.researchpath.path;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain-SQL access to reading paths and their immutable revisions (V6). */
@Repository
public class PathRepository {

	private final JdbcClient jdbc;
	private final JdbcTemplate batch;

	public PathRepository(JdbcClient jdbc, JdbcTemplate batch) {
		this.jdbc = jdbc;
		this.batch = batch;
	}

	public record PathRow(UUID id, String publicId, UUID snapshotId, String topic, String idempotencyKey,
			Instant createdAt) {
	}

	public record RevisionSummary(int revision, Integer parentRevision, String experience, String focus, int budget,
			Instant createdAt) {
	}

	/** One stored path item (relational copy of the document's papers, for joins and integrity). */
	public record ItemRow(int position, long publicationId, String openalexId, int stage, int selectionStep,
			double marginalGain, String reason) {
	}

	public void insertPath(PathRow p) {
		jdbc.sql("""
				INSERT INTO reading_path (id, public_id, snapshot_id, topic, idempotency_key, created_at)
				VALUES (:id, :publicId, :snapshotId, :topic, :key, :createdAt)
				""")
				.param("id", p.id()).param("publicId", p.publicId()).param("snapshotId", p.snapshotId())
				.param("topic", p.topic()).param("key", p.idempotencyKey())
				.param("createdAt", Timestamp.from(p.createdAt()))
				.update();
	}

	public void insertRevision(UUID revisionId, UUID pathId, int revision, Integer parentRevision, PathOptions options,
			String versionsJson, String fingerprint, String resultJson, Instant createdAt, List<ItemRow> items) {
		jdbc.sql("""
				INSERT INTO path_revision (id, path_id, revision, parent_revision, experience, focus, budget,
				    engine_versions, request_fingerprint, result, created_at)
				VALUES (:id, :pathId, :revision, :parent, :experience, :focus, :budget, CAST(:versions AS jsonb),
				    :fingerprint, CAST(:result AS jsonb), :createdAt)
				""")
				.param("id", revisionId).param("pathId", pathId).param("revision", revision)
				.param("parent", parentRevision).param("experience", options.experience().name())
				.param("focus", options.focus().name()).param("budget", options.budget())
				.param("versions", versionsJson).param("fingerprint", fingerprint).param("result", resultJson)
				.param("createdAt", Timestamp.from(createdAt))
				.update();
		// One batched statement for all items (M13: no per-item round trips).
		batch.batchUpdate("""
				INSERT INTO path_revision_item (revision_id, position, publication_id, openalex_id, stage,
				    selection_step, marginal_gain, reason)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""", items, items.size(), (ps, i) -> {
			ps.setObject(1, revisionId);
			ps.setInt(2, i.position());
			ps.setLong(3, i.publicationId());
			ps.setString(4, i.openalexId());
			ps.setInt(5, i.stage());
			ps.setInt(6, i.selectionStep());
			ps.setDouble(7, i.marginalGain());
			ps.setString(8, i.reason());
		});
	}

	public Optional<PathRow> findByPublicId(String publicId) {
		return jdbc.sql("""
				SELECT id, public_id, snapshot_id, topic, idempotency_key, created_at FROM reading_path
				WHERE public_id = :publicId
				""").param("publicId", publicId).query(PathRepository::path).optional();
	}

	public Optional<PathRow> findByIdempotencyKey(String key) {
		return jdbc.sql("""
				SELECT id, public_id, snapshot_id, topic, idempotency_key, created_at FROM reading_path
				WHERE idempotency_key = :key
				""").param("key", key).query(PathRepository::path).optional();
	}

	/** Serializes revision creation for one path until the surrounding transaction ends. */
	public void lock(UUID pathId) {
		jdbc.sql("SELECT id FROM reading_path WHERE id = :id FOR UPDATE").param("id", pathId).query(UUID.class).single();
	}

	public Optional<Integer> revisionWithFingerprint(UUID pathId, String fingerprint) {
		return jdbc.sql("SELECT revision FROM path_revision WHERE path_id = :id AND request_fingerprint = :fp")
				.param("id", pathId).param("fp", fingerprint).query(Integer.class).optional();
	}

	public int latestRevision(UUID pathId) {
		return jdbc.sql("SELECT coalesce(max(revision), 0) FROM path_revision WHERE path_id = :id")
				.param("id", pathId).query(Integer.class).single();
	}

	public Optional<String> result(UUID pathId, int revision) {
		return jdbc.sql("SELECT result::text FROM path_revision WHERE path_id = :id AND revision = :revision")
				.param("id", pathId).param("revision", revision).query(String.class).optional();
	}

	public List<RevisionSummary> revisions(UUID pathId) {
		return jdbc.sql("""
				SELECT revision, parent_revision, experience, focus, budget, created_at FROM path_revision
				WHERE path_id = :id ORDER BY revision
				""").param("id", pathId)
				.query((rs, n) -> new RevisionSummary(rs.getInt(1), (Integer) rs.getObject(2), rs.getString(3),
						rs.getString(4), rs.getInt(5), rs.getTimestamp(6).toInstant()))
				.list();
	}

	public List<ItemRow> items(UUID pathId, int revision) {
		return jdbc.sql("""
				SELECT i.position, i.publication_id, i.openalex_id, i.stage, i.selection_step, i.marginal_gain, i.reason
				FROM path_revision_item i JOIN path_revision r ON r.id = i.revision_id
				WHERE r.path_id = :id AND r.revision = :revision ORDER BY i.position
				""").param("id", pathId).param("revision", revision)
				.query((rs, n) -> new ItemRow(rs.getInt(1), rs.getLong(2), rs.getString(3), rs.getInt(4), rs.getInt(5),
						rs.getDouble(6), rs.getString(7)))
				.list();
	}

	private static PathRow path(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
		return new PathRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4),
				rs.getString(5), rs.getTimestamp(6).toInstant());
	}

}
