package dev.researchpath.path;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes anonymous paths nobody has opened for {@code pathDays}, then candidate snapshots no path uses after
 * {@code snapshotDays} (decision S02). Stored rows are never updated; whole paths are deleted with their revisions.
 */
@Service
public class RetentionService {

	private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

	/** Repeated views within this window do not rewrite the access time. */
	static final Duration ACCESS_GRANULARITY = Duration.ofHours(1);

	private final JdbcClient jdbc;
	private final RetentionProperties properties;

	public RetentionService(JdbcClient jdbc, org.springframework.beans.factory.ObjectProvider<RetentionProperties> properties) {
		this.jdbc = jdbc;
		this.properties = properties.getIfAvailable(RetentionProperties::defaults);
	}

	public record Purge(int paths, int snapshots) {
	}

	public RetentionProperties properties() {
		return properties;
	}

	/** Records that a path was opened (at most one write per path per hour). */
	public void touch(UUID pathId, Instant now) {
		jdbc.sql("""
				INSERT INTO path_access (path_id, last_viewed_at) VALUES (:id, :now)
				ON CONFLICT (path_id) DO UPDATE SET last_viewed_at = excluded.last_viewed_at
				WHERE path_access.last_viewed_at < excluded.last_viewed_at - make_interval(secs => :granularity)
				""").param("id", pathId).param("now", Timestamp.from(now))
				.param("granularity", (double) ACCESS_GRANULARITY.toSeconds()).update();
	}

	@Transactional
	public Purge purge(Instant now) {
		Instant pathCutoff = now.minus(Duration.ofDays(properties.pathDays()));
		Instant snapshotCutoff = now.minus(Duration.ofDays(properties.snapshotDays()));
		int paths = jdbc.sql("""
				DELETE FROM reading_path p
				WHERE coalesce((SELECT a.last_viewed_at FROM path_access a WHERE a.path_id = p.id), p.created_at) < :cutoff
				""").param("cutoff", Timestamp.from(pathCutoff)).update();
		int snapshots = jdbc.sql("""
				DELETE FROM candidate_snapshot s
				WHERE s.created_at < :cutoff
				  AND NOT EXISTS (SELECT 1 FROM reading_path p WHERE p.snapshot_id = s.id)
				""").param("cutoff", Timestamp.from(snapshotCutoff)).update();
		if (paths > 0 || snapshots > 0) {
			log.info("Retention purge: {} path(s) not opened for {} days, {} unused snapshot(s) older than {} days", paths,
					properties.pathDays(), snapshots, properties.snapshotDays());
		}
		return new Purge(paths, snapshots);
	}

	/** The daily job; off when {@code researchpath.retention.enabled=false}. */
	@Component
	@EnableScheduling
	@ConditionalOnProperty(name = "researchpath.retention.enabled", havingValue = "true", matchIfMissing = true)
	static class Job {

		private final RetentionService service;

		Job(RetentionService service) {
			this.service = service;
		}

		@Scheduled(cron = "${researchpath.retention.cron:0 17 3 * * *}")
		void run() {
			service.purge(Instant.now());
		}
	}

}
