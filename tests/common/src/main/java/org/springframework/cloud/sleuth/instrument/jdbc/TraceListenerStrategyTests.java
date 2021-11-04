/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class TraceListenerStrategyTests {

	public static final String SPAN_SQL_QUERY_TAG_NAME = "jdbc.query";

	public static final String SPAN_ROW_COUNT_TAG_NAME = "jdbc.row-count";

	abstract ApplicationContextRunner parentContextRunner();

	protected abstract Class<?> autoConfiguration();

	protected abstract Class<?> testConfiguration();

	@Test
	void testShouldAddSpanForConnection() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			connection.commit();
			connection.rollback();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(1);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(connectionSpan.getRemoteServiceName()).isEqualTo("TESTDB-BAZ");
			assertThat(connectionSpan.getEvents()).extracting("value").contains("jdbc.commit");
			assertThat(connectionSpan.getEvents()).extracting("value").contains("jdbc.rollback");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldAddSpanForConnectionWithFixedRemoteServiceName() {
		parentContextRunner()
				.withPropertyValues("spring.datasource.url:jdbc:h2:mem:testdb-baz?sleuthServiceName=aaaabbbb")
				.run(context -> {
					DataSource dataSource = context.getBean(DataSource.class);
					TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

					Connection connection = dataSource.getConnection();
					connection.commit();
					connection.rollback();
					connection.close();

					assertThat(spanReporter.reportedSpans()).hasSize(1);
					FinishedSpan connectionSpan = spanReporter.reportedSpans().get(0);
					assertThat(connectionSpan.getName()).isEqualTo("connection");
					assertThat(connectionSpan.getRemoteServiceName()).isEqualTo("aaaabbbb");
					assertThat(connectionSpan.getEvents()).extracting("value").contains("jdbc.commit");
					assertThat(connectionSpan.getEvents()).extracting("value").contains("jdbc.rollback");
					assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
				});
	}

	@Test
	void testShouldAddSpanForPreparedStatementExecute() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			connection.prepareStatement("SELECT NOW()").execute();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(2);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(statementSpan.getName()).isEqualTo("select");
			assertThat(statementSpan.getRemoteServiceName()).isEqualTo("TESTDB-BAZ");
			assertThat(statementSpan.getTags()).containsEntry(SPAN_SQL_QUERY_TAG_NAME, "SELECT NOW()");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldAddSpanForPreparedStatementExecuteUpdate() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			connection.prepareStatement("UPDATE INFORMATION_SCHEMA.TABLES SET table_Name = '' WHERE 0 = 1")
					.executeUpdate();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(2);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(statementSpan.getName()).isEqualTo("update");
			assertThat(statementSpan.getTags()).containsEntry(SPAN_SQL_QUERY_TAG_NAME,
					"UPDATE INFORMATION_SCHEMA.TABLES SET table_Name = '' WHERE 0 = 1");
			assertThat(statementSpan.getTags()).containsEntry(SPAN_ROW_COUNT_TAG_NAME, "0");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldAddSpanForStatementExecuteUpdate() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			connection.createStatement()
					.executeUpdate("UPDATE INFORMATION_SCHEMA.TABLES SET table_Name = '' WHERE 0 = 1");
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(2);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(statementSpan.getName()).isEqualTo("update");
			assertThat(statementSpan.getTags()).containsEntry(SPAN_SQL_QUERY_TAG_NAME,
					"UPDATE INFORMATION_SCHEMA.TABLES SET table_Name = '' WHERE 0 = 1");
			assertThat(statementSpan.getTags()).containsEntry(SPAN_ROW_COUNT_TAG_NAME, "0");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldAddSpanForPreparedStatementExecuteQueryIncludingTimeToCloseResultSet() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			ResultSet resultSet = connection.prepareStatement("SELECT NOW() UNION ALL select NOW()").executeQuery();
			resultSet.next();
			resultSet.next();
			resultSet.close();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(3);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(2);
			FinishedSpan resultSetSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(statementSpan.getName()).isEqualTo("select");
			assertThat(statementSpan.getTags()).containsEntry(SPAN_SQL_QUERY_TAG_NAME,
					"SELECT NOW() UNION ALL select NOW()");
			assertThat(resultSetSpan.getName()).isEqualTo("result-set");
			assertThat(resultSetSpan.getRemoteServiceName()).isEqualTo("TESTDB-BAZ");
			if (isP6Spy(context)) {
				assertThat(resultSetSpan.getTags()).containsEntry(SPAN_ROW_COUNT_TAG_NAME, "2");
			}
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldAddSpanForStatementAndResultSet() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			ResultSet resultSet = connection.createStatement().executeQuery("SELECT NOW()");
			resultSet.next();
			Thread.sleep(200L);
			resultSet.close();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(3);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(2);
			FinishedSpan resultSetSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(statementSpan.getName()).isEqualTo("select");
			assertThat(statementSpan.getTags()).containsEntry(SPAN_SQL_QUERY_TAG_NAME, "SELECT NOW()");
			assertThat(resultSetSpan.getName()).isEqualTo("result-set");
			if (isP6Spy(context)) {
				assertThat(resultSetSpan.getTags()).containsEntry(SPAN_ROW_COUNT_TAG_NAME, "1");
			}
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldNotFailWhenStatementIsClosedWihoutResultSet() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("SELECT NOW()");
			resultSet.next();
			statement.close();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(3);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(2);
			FinishedSpan resultSetSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(statementSpan.getName()).isEqualTo("select");
			assertThat(resultSetSpan.getName()).isEqualTo("result-set");
			assertThat(statementSpan.getTags()).containsEntry(SPAN_SQL_QUERY_TAG_NAME, "SELECT NOW()");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldNotFailWhenConnectionIsClosedWihoutResultSet() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("SELECT NOW()");
			resultSet.next();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(3);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(2);
			FinishedSpan resultSetSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(statementSpan.getName()).isEqualTo("select");
			assertThat(resultSetSpan.getName()).isEqualTo("result-set");
			assertThat(statementSpan.getTags()).containsEntry(SPAN_SQL_QUERY_TAG_NAME, "SELECT NOW()");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldNotFailWhenResultSetNextWasNotCalled() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("SELECT NOW()");
			resultSet.next();
			resultSet.close();
			statement.close();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(3);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(2);
			FinishedSpan resultSetSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(statementSpan.getName()).isEqualTo("select");
			assertThat(resultSetSpan.getName()).isEqualTo("result-set");
			assertThat(statementSpan.getTags()).containsEntry(SPAN_SQL_QUERY_TAG_NAME, "SELECT NOW()");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldNotFailWhenResourceIsAlreadyClosed() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("SELECT NOW()");
			resultSet.next();
			resultSet.close();
			resultSet.close();
			statement.close();
			statement.close();
			connection.close();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(3);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(2);
			FinishedSpan resultSetSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(statementSpan.getName()).isEqualTo("select");
			assertThat(resultSetSpan.getName()).isEqualTo("result-set");
			assertThat(statementSpan.getTags()).containsEntry(SPAN_SQL_QUERY_TAG_NAME, "SELECT NOW()");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldNotFailWhenResourceIsAlreadyClosed2() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			assertThatThrownBy(() -> {
				connection.close();
				connection.prepareStatement("SELECT NOW()");
			}).isInstanceOf(SQLException.class);

			assertThat(spanReporter.reportedSpans()).hasSize(1);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldNotFailWhenResourceIsAlreadyClosed3() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);

			Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			assertThatThrownBy(() -> {
				statement.close();
				statement.executeQuery("SELECT NOW()");
			}).isInstanceOf(SQLException.class);
			connection.close();
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldNotFailWhenResourceIsAlreadyClosed4() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);

			Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("SELECT NOW()");
			assertThatThrownBy(() -> {
				resultSet.close();
				resultSet.next();
			}).isInstanceOf(SQLException.class);
			statement.close();
			connection.close();
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldNotFailToCloseSpanForTwoConsecutiveConnections() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection1 = dataSource.getConnection();
			Connection connection2 = dataSource.getConnection();
			connection1.close();
			connection2.close();

			assertThat(spanReporter.reportedSpans()).hasSize(2);
			FinishedSpan connection1Span = spanReporter.reportedSpans().get(0);
			FinishedSpan connection2Span = spanReporter.reportedSpans().get(1);
			assertThat(connection1Span.getName()).isEqualTo("connection");
			assertThat(connection2Span.getName()).isEqualTo("connection");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldNotFailWhenClosedInReversedOrder() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("SELECT NOW()");
			resultSet.next();
			connection.close();
			statement.close();
			resultSet.close();

			assertThat(spanReporter.reportedSpans()).hasSize(3);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(2);
			FinishedSpan resultSetSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(statementSpan.getName()).isEqualTo("select");
			assertThat(resultSetSpan.getName()).isEqualTo("result-set");
			assertThat(statementSpan.getTags()).containsEntry(SPAN_SQL_QUERY_TAG_NAME, "SELECT NOW()");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	void testShouldNotCauseMemoryLeakOnTomcatPool() {
		parentContextRunner().withPropertyValues("spring.datasource.type:org.apache.tomcat.jdbc.pool.DataSource")
				.run(context -> {
					DataSource dataSource = context.getBean(DataSource.class);
					Object listener = isP6Spy(context) ? context.getBean(TraceJdbcEventListener.class)
							: context.getBean(TraceQueryExecutionListener.class);

					Connection connection = dataSource.getConnection();
					Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("select 1 FROM dual");
					resultSet.next();
					resultSet.close();
					statement.close();
					connection.close();

					assertThat(listener).extracting("strategy").extracting("openConnections")
							.isInstanceOfSatisfying(Map.class, map -> assertThat(map).isEmpty());
					assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
				});
	}

	private boolean isP6Spy(AssertableApplicationContext context) {
		if (context.getBeansOfType(TraceJdbcEventListener.class).size() == 1) {
			return true;
		}
		else if (context.getBeansOfType(TraceQueryExecutionListener.class).size() == 1) {
			return false;
		}
		else {
			throw new IllegalStateException("Expected exactly 1 tracing listener bean in the context.");
		}
	}

	@Test
	void testSingleConnectionAcrossMultipleThreads() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			IntStream.range(0, 5).mapToObj(i -> CompletableFuture.runAsync(() -> {
				try {
					Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("SELECT NOW()");
					resultSet.next();
					statement.close();
					resultSet.close();
				}
				catch (SQLException e) {
					throw new IllegalStateException(e);
				}
			})).collect(Collectors.toList()).forEach(CompletableFuture::join);
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(1 + 2 * 5);
			assertThat(spanReporter.reportedSpans()).extracting("name").contains("select", "result-set", "connection");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldIncludeOnlyConnectionTraces() {
		parentContextRunner().withPropertyValues("spring.sleuth.jdbc.includes: connection").run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("select 1 FROM dual");
			resultSet.next();
			resultSet.close();
			statement.close();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(1);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldIncludeOnlyQueryTraces() {
		parentContextRunner().withPropertyValues("spring.sleuth.jdbc.includes: query").run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("select 1 FROM dual");
			resultSet.next();
			resultSet.close();
			statement.close();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(statementSpan.getName()).isEqualTo("select");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldIncludeOnlyFetchTraces() {
		parentContextRunner().withPropertyValues("spring.sleuth.jdbc.includes: fetch").run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("select 1 FROM dual");
			resultSet.next();
			resultSet.close();
			statement.close();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(1);
			FinishedSpan resultSetSpan = spanReporter.reportedSpans().get(0);
			assertThat(resultSetSpan.getName()).isEqualTo("result-set");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldIncludeOnlyConnectionAndQueryTraces() {
		parentContextRunner().withPropertyValues("spring.sleuth.jdbc.includes: connection, query").run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("select 1 FROM dual");
			resultSet.next();
			resultSet.close();
			statement.close();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(2);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(statementSpan.getName()).isEqualTo("select");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldIncludeOnlyConnectionAndFetchTraces() {
		parentContextRunner().withPropertyValues("spring.sleuth.jdbc.includes: connection, fetch").run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("select 1 FROM dual");
			resultSet.next();
			resultSet.close();
			statement.close();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(2);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan resultSetSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(resultSetSpan.getName()).isEqualTo("result-set");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldIncludeOnlyQueryAndFetchTraces() {
		parentContextRunner().withPropertyValues("spring.sleuth.jdbc.includes: query, fetch").run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("select 1 FROM dual");
			resultSet.next();
			resultSet.close();
			statement.close();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(2);
			FinishedSpan resultSetSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(statementSpan.getName()).isEqualTo("select");
			assertThat(resultSetSpan.getName()).isEqualTo("result-set");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldNotOverrideExceptionWhenConnectionWasClosedBeforeExecutingQuery() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT NOW()");
			connection.close();
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();

			assertThatThrownBy(statement::executeQuery).isInstanceOf(SQLException.class);

			assertThat(spanReporter.reportedSpans()).hasSize(1);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldNotOverrideExceptionWhenStatementWasClosedBeforeExecutingQuery() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT NOW()");
			statement.close();
			assertThatThrownBy(statement::executeQuery).isInstanceOf(SQLException.class);
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(2);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(statementSpan.getName()).isEqualTo("select");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldNotOverrideExceptionWhenResultSetWasClosedBeforeNext() {
		parentContextRunner().run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			TestSpanHandler spanReporter = context.getBean(TestSpanHandler.class);

			Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("SELECT NOW()");
			resultSet.close();
			assertThatThrownBy(resultSet::next).isInstanceOf(SQLException.class);
			statement.close();
			connection.close();

			assertThat(spanReporter.reportedSpans()).hasSize(3);
			FinishedSpan connectionSpan = spanReporter.reportedSpans().get(2);
			FinishedSpan resultSetSpan = spanReporter.reportedSpans().get(1);
			FinishedSpan statementSpan = spanReporter.reportedSpans().get(0);
			assertThat(connectionSpan.getName()).isEqualTo("connection");
			assertThat(statementSpan.getName()).isEqualTo("select");
			assertThat(resultSetSpan.getName()).isEqualTo("result-set");
			assertThat(statementSpan.getTags()).containsEntry(SPAN_SQL_QUERY_TAG_NAME, "SELECT NOW()");
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testShouldNotFailWhenClosingConnectionFromDifferentDataSource() {
		ApplicationContextRunner contextRunner = parentContextRunner()
				.withUserConfiguration(MultiDataSourceConfiguration.class);

		contextRunner.run(context -> {
			DataSource dataSource1 = context.getBean("test1", DataSource.class);
			DataSource dataSource2 = context.getBean("test2", DataSource.class);

			dataSource1.getConnection().close();
			dataSource2.getConnection().close();

			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				try {
					Connection connection1 = dataSource1.getConnection();
					PreparedStatement statement = connection1.prepareStatement("SELECT NOW()");
					ResultSet resultSet = statement.executeQuery();
					Thread.sleep(200);
					resultSet.close();
					statement.close();
					connection1.close();
				}
				catch (SQLException | InterruptedException e) {
					throw new IllegalStateException(e);
				}
			});
			Thread.sleep(100);
			Connection connection2 = dataSource2.getConnection();
			Thread.sleep(300);
			connection2.close();

			future.join();
		});
	}

	private static class MultiDataSourceConfiguration {

		@Bean
		public HikariDataSource test1() {
			HikariDataSource dataSource = new HikariDataSource();
			dataSource.setJdbcUrl("jdbc:h2:mem:testdb-1-foo");
			dataSource.setPoolName("test1");
			return dataSource;
		}

		@Bean
		public HikariDataSource test2() {
			HikariDataSource dataSource = new HikariDataSource();
			dataSource.setJdbcUrl("jdbc:h2:mem:testdb-2-bar");
			dataSource.setPoolName("test2");
			return dataSource;
		}

	}

}
