package dev.researchpath.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.researchpath.TestcontainersConfiguration;
import dev.researchpath.openalex.OpenAlexClient;
import dev.researchpath.optimizer.Preferences.Experience;
import dev.researchpath.optimizer.Preferences.Focus;

/**
 * M13 performance check: SQL statements per operation, counted at the JDBC boundary, so an N+1 pattern or a
 * regression in round trips shows up as a failing bound rather than as slow production requests.
 */
@Import({ TestcontainersConfiguration.class, QueryCountTests.Counting.class })
@SpringBootTest
class QueryCountTests {

	static final AtomicInteger STATEMENTS = new AtomicInteger();

	/** Wraps the DataSource so every prepared or plain statement is counted. */
	@TestConfiguration(proxyBeanMethods = false)
	static class Counting {
		@Bean
		static BeanPostProcessor countingDataSource() {
			return new BeanPostProcessor() {
				@Override
				public Object postProcessAfterInitialization(Object bean, String name) {
					if (!(bean instanceof DataSource ds)) {
						return bean;
					}
					return Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] { DataSource.class },
							(p, m, args) -> {
								Object result = m.invoke(ds, args);
								return result instanceof Connection c ? counting(c) : result;
							});
				}
			};
		}

		static Connection counting(Connection c) {
			return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
					(p, m, args) -> {
						if (m.getName().equals("prepareStatement") || m.getName().equals("createStatement")
								|| m.getName().equals("prepareCall")) {
							STATEMENTS.incrementAndGet();
						}
						try {
							return m.invoke(c, args);
						} catch (java.lang.reflect.InvocationTargetException e) {
							throw e.getCause();
						}
					});
		}
	}

	@Autowired
	PathService paths;

	@Autowired
	JdbcClient jdbc;

	@MockitoBean
	OpenAlexClient openAlex;

	@BeforeEach
	void setUp() {
		jdbc.sql("TRUNCATE candidate_snapshot, publication, concept RESTART IDENTITY CASCADE").update();
		when(openAlex.searchWorks(eq(PathServiceTests.Q), eq(1), anyInt())).thenReturn(PathServiceTests.pool(""));
	}

	private int count(Runnable r) {
		STATEMENTS.set(0);
		r.run();
		return STATEMENTS.get();
	}

	@Test
	void statementCountsPerOperationAreBoundedAndIndependentOfPathSize() {
		PathService.PathView v = paths.create(PathServiceTests.Q, new PathOptions(Experience.INTERMEDIATE, Focus.BALANCED, 5), null);
		int get = count(() -> paths.get(v.pathId(), null));
		int regen5 = count(() -> paths.regenerate(v.pathId(), new PathOptions(Experience.ADVANCED, Focus.BALANCED, 5), null));
		int regen10 = count(() -> paths.regenerate(v.pathId(), new PathOptions(Experience.ADVANCED, Focus.BALANCED, 10), null));
		int repeat = count(() -> paths.regenerate(v.pathId(), new PathOptions(Experience.ADVANCED, Focus.BALANCED, 10), null));
		System.out.printf("SQL statements: get=%d, regenerate(5)=%d, regenerate(10)=%d, identical repeat=%d%n", get, regen5,
				regen10, repeat);

		assertThat(get).as("get").isLessThanOrEqualTo(5);
		assertThat(regen10).as("regenerate does not grow with the number of papers").isEqualTo(regen5);
		assertThat(regen10).as("regenerate").isLessThanOrEqualTo(15);
		assertThat(repeat).as("identical repeat does no generation work").isLessThanOrEqualTo(get + 1);
		verify(openAlex, never()).searchWorks(anyString(), eq(2), anyInt());
	}

}
