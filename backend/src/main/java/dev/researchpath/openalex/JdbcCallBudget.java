package dev.researchpath.openalex;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * PostgreSQL-backed daily provider budget (V8). Reservation is one statement: insert today's row or increment it
 * only while it is below the limit, returning nothing when the budget is used up. The row lock makes concurrent
 * reservations serialize, also across processes, and the count survives restarts. Accounting is conservative: an
 * attempt is counted before it is sent, so crashes can only over-count, never let spending exceed the limit.
 */
public class JdbcCallBudget implements CallBudget {

	private static final Logger log = LoggerFactory.getLogger(JdbcCallBudget.class);

	private final JdbcClient jdbc;
	private final ProviderBudgetProperties properties;
	private final Clock clock;

	public JdbcCallBudget(JdbcClient jdbc, ProviderBudgetProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.properties = properties;
		this.clock = clock.withZone(ZoneOffset.UTC);
	}

	@Override
	public boolean tryReserve() {
		if (!properties.enabled()) {
			return true;
		}
		LocalDate today = LocalDate.now(clock);
		boolean reserved = jdbc.sql("""
				INSERT INTO provider_call_budget (day, calls, updated_at) VALUES (:day, 1, :now)
				ON CONFLICT (day) DO UPDATE SET calls = provider_call_budget.calls + 1, updated_at = excluded.updated_at
				WHERE provider_call_budget.calls < :limit
				RETURNING calls
				""").param("day", today).param("now", Timestamp.from(clock.instant())).param("limit", properties.dailyCalls())
				.query(Integer.class).optional()
				.filter(calls -> calls <= properties.dailyCalls())
				.isPresent();
		if (!reserved) {
			log.warn("Daily provider call budget ({}) exhausted for {}", properties.dailyCalls(), today);
		}
		return reserved;
	}

	@Override
	public void refund() {
		if (!properties.enabled()) {
			return;
		}
		jdbc.sql("UPDATE provider_call_budget SET calls = calls - 1, updated_at = :now WHERE day = :day AND calls > 0")
				.param("day", LocalDate.now(clock)).param("now", Timestamp.from(clock.instant())).update();
	}

	@Override
	public long secondsUntilReset() {
		long now = clock.instant().getEpochSecond();
		long midnight = LocalDate.now(clock).plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
		return Math.max(1, midnight - now);
	}

	/** Calls counted today (diagnostics and tests). */
	public int usedToday() {
		return jdbc.sql("SELECT coalesce((SELECT calls FROM provider_call_budget WHERE day = :day), 0)")
				.param("day", LocalDate.now(clock)).query(Integer.class).single();
	}

}
