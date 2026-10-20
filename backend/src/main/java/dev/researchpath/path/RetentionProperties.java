package dev.researchpath.path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Retention of anonymous data ({@code researchpath.retention.*}; decision S02, PROVISIONAL).
 *
 * @param pathDays a path not opened for this many days is deleted with all its revisions
 * @param snapshotDays a candidate snapshot no path uses is deleted this many days after it was created (the
 *     publication cache is kept)
 * @param cron when the daily purge runs (server time)
 */
@ConfigurationProperties("researchpath.retention")
public record RetentionProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("90") int pathDays,
		@DefaultValue("30") int snapshotDays,
		@DefaultValue("0 17 3 * * *") String cron) {

	public RetentionProperties {
		if (pathDays < 1 || snapshotDays < 1) {
			throw new IllegalArgumentException("retention days must be >= 1");
		}
	}

	public static RetentionProperties defaults() {
		return new RetentionProperties(true, 90, 30, "0 17 3 * * *");
	}

}
