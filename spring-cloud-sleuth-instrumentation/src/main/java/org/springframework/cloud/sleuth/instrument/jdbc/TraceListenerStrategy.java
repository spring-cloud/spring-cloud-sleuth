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

import java.net.URI;
import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.CommonDataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAndScope;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.cloud.sleuth.docs.AssertingSpanBuilder;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * Partially taken from
 * https://github.com/openzipkin/brave/blob/v5.6.4/instrumentation/p6spy/src/main/java/brave/p6spy/TracingJdbcEventListener.java.
 *
 * @param <CON> connection type
 * @param <STMT> statement
 * @param <RS> result set
 */
class TraceListenerStrategy<CON, STMT, RS> {

	private static final Log log = LogFactory.getLog(TraceListenerStrategy.class);

	private final Map<CON, ConnectionInfo> openConnections = new ConcurrentHashMap<>();

	// Captures all the characters between = and either the next & or the end of the
	// string.
	private static final Pattern URL_SERVICE_NAME_FINDER = Pattern.compile("sleuthServiceName=(.*?)(?:&|$)");

	private final Tracer tracer;

	private final List<TraceType> traceTypes;

	private final List<TraceListenerStrategySpanCustomizer> customizers;

	TraceListenerStrategy(Tracer tracer, List<TraceType> traceTypes,
			List<TraceListenerStrategySpanCustomizer> customizers) {
		this.tracer = tracer;
		this.traceTypes = traceTypes;
		this.customizers = customizers;
	}

	void beforeGetConnection(CON connectionKey, @Nullable CommonDataSource dataSource, String dataSourceName) {
		if (log.isTraceEnabled()) {
			log.trace("Before get connection key [" + connectionKey + "] - current span is [" + tracer.currentSpan()
					+ "]");
		}
		SpanAndScope spanAndScope = null;
		if (this.traceTypes.contains(TraceType.CONNECTION)) {
			AssertingSpanBuilder connectionSpanBuilder = AssertingSpanBuilder
					.of(SleuthJdbcSpan.JDBC_CONNECTION_SPAN, tracer.spanBuilder())
					.name(SleuthJdbcSpan.JDBC_CONNECTION_SPAN.getName());
			connectionSpanBuilder.remoteServiceName(dataSourceName);
			connectionSpanBuilder.kind(Span.Kind.CLIENT);
			this.customizers.stream().filter(customizer -> customizer.isApplicable(dataSource))
					.forEach(customizer -> customizer.customizeConnectionSpan(dataSource, connectionSpanBuilder));
			Span connectionSpan = connectionSpanBuilder.start();
			spanAndScope = new SpanAndScope(connectionSpan, tracer.withSpan(connectionSpan));
			if (log.isTraceEnabled()) {
				log.trace("Started client span before connection [" + connectionSpan + "] - current span is ["
						+ tracer.currentSpan() + "]");
			}
		}
		ConnectionInfo connectionInfo = new ConnectionInfo(spanAndScope);
		this.openConnections.put(connectionKey, connectionInfo);
	}

	void afterGetConnection(CON connectionKey, Connection connection, Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After get connection [" + connectionKey + "]. Current span is [" + tracer.currentSpan() + "]");
		}
		this.openConnections.get(connectionKey).getSpan().ifPresent(spanAndScope -> {
			parseServerIpAndPort(connection, spanAndScope.getSpan());
		});
		if (t != null) {
			ConnectionInfo connectionInfo = this.openConnections.remove(connectionKey);
			connectionInfo.getSpan().ifPresent(connectionSpan -> {
				parseServerIpAndPort(connection, connectionSpan.getSpan());
				if (log.isTraceEnabled()) {
					log.trace("Closing client span due to exception [" + connectionSpan.getSpan()
							+ "] - current span is [" + tracer.currentSpan() + "]");
				}
				connectionSpan.getSpan().error(t);
				connectionSpan.close();
				if (log.isTraceEnabled()) {
					log.trace("Current span [" + tracer.currentSpan() + "]");
				}
			});
		}
	}

	void beforeQuery(CON connectionKey, Connection connection, STMT statementKey, String dataSourceName) {
		if (log.isTraceEnabled()) {
			log.trace("Before query - connection [" + connectionKey + "] and current span [" + tracer.currentSpan()
					+ "]");
		}
		ConnectionInfo connectionInfo = openConnections.get(connectionKey);
		if (connectionInfo == null) {
			if (log.isTraceEnabled()) {
				log.trace("Connection may be closed after statement preparation, but before statement execution");
			}
			return;
		}
		SpanAndScope spanAndScope = null;
		if (traceTypes.contains(TraceType.QUERY)) {
			Span.Builder statementSpanBuilder = AssertingSpanBuilder
					.of(SleuthJdbcSpan.JDBC_QUERY_SPAN, tracer.spanBuilder())
					.name(String.format(SleuthJdbcSpan.JDBC_QUERY_SPAN.getName(), "query"));
			statementSpanBuilder.remoteServiceName(dataSourceName);
			parseServerIpAndPort(connection, statementSpanBuilder);
			statementSpanBuilder.kind(Span.Kind.CLIENT);
			Span statementSpan = statementSpanBuilder.start();
			spanAndScope = new SpanAndScope(statementSpan, tracer.withSpan(statementSpan));
			if (log.isTraceEnabled()) {
				log.trace("Started client span before query [" + statementSpan + "] - current span is ["
						+ tracer.currentSpan() + "]");
			}
		}
		StatementInfo statementInfo = new StatementInfo(spanAndScope);
		connectionInfo.getNestedStatements().put(statementKey, statementInfo);
	}

	void addQueryRowCount(CON connectionKey, STMT statementKey, int rowCount) {
		if (log.isTraceEnabled()) {
			log.trace("Add query row count for connection key [" + connectionKey + "]");
		}
		ConnectionInfo connectionInfo = openConnections.get(connectionKey);
		if (connectionInfo == null) {
			if (log.isTraceEnabled()) {
				log.trace("Connection is already closed");
			}
			return;
		}
		StatementInfo statementInfo = connectionInfo.getNestedStatements().get(statementKey);
		statementInfo.getSpan()
				.ifPresent(statementSpan -> AssertingSpan.of(SleuthJdbcSpan.JDBC_QUERY_SPAN, statementSpan.getSpan())
						.tag(SleuthJdbcSpan.QueryTags.ROW_COUNT, String.valueOf(rowCount)));
	}

	void afterQuery(CON connectionKey, STMT statementKey, String sql, Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After query for connection key [" + connectionKey + "]");
		}
		ConnectionInfo connectionInfo = openConnections.get(connectionKey);
		if (connectionInfo == null) {
			if (log.isTraceEnabled()) {
				log.trace(
						"Connection may be closed after statement preparation, but before statement execution. Current span is ["
								+ tracer.currentSpan() + "]");
			}
			return;
		}
		StatementInfo statementInfo = connectionInfo.getNestedStatements().get(statementKey);
		statementInfo.getSpan().ifPresent(statementSpan -> {
			updateQuerySpan(sql, t, statementSpan);
			statementSpan.close();
			if (log.isTraceEnabled()) {
				log.trace("Current span [" + tracer.currentSpan() + "]");
			}
		});
	}

	private void updateQuerySpan(String sql, Throwable t, SpanAndScope statementSpan) {
		AssertingSpan.of(SleuthJdbcSpan.JDBC_QUERY_SPAN, statementSpan.getSpan())
				.tag(SleuthJdbcSpan.QueryTags.QUERY, sql).name(spanName(sql));
		if (t != null) {
			statementSpan.getSpan().error(t);
		}
		if (log.isTraceEnabled()) {
			log.trace(
					"Closing statement span [" + statementSpan + "] - current span is [" + tracer.currentSpan() + "]");
		}
	}

	void beforeResultSetNext(CON connectionKey, Connection connection, STMT statementKey, RS resultSetKey,
			String dataSourceName) {
		if (log.isTraceEnabled()) {
			log.trace("Before result set next");
		}
		if (!traceTypes.contains(TraceType.FETCH)) {
			return;
		}
		ConnectionInfo connectionInfo = openConnections.get(connectionKey);
		// ConnectionInfo may be null if Connection was closed before ResultSet
		if (connectionInfo == null) {
			if (log.isTraceEnabled()) {
				log.trace("No connection info, skipping");
			}
			return;
		}
		if (connectionInfo.getNestedResultSetSpans().containsKey(resultSetKey)) {
			if (log.isTraceEnabled()) {
				log.trace("ResultSet span is already created");
			}
			return;
		}
		AssertingSpanBuilder resultSetSpanBuilder = AssertingSpanBuilder
				.of(SleuthJdbcSpan.JDBC_RESULT_SET_SPAN, tracer.spanBuilder())
				.name(SleuthJdbcSpan.JDBC_RESULT_SET_SPAN.getName());
		resultSetSpanBuilder.remoteServiceName(dataSourceName);
		resultSetSpanBuilder.kind(Span.Kind.CLIENT);
		parseServerIpAndPort(connection, resultSetSpanBuilder);
		Span resultSetSpan = resultSetSpanBuilder.start();
		SpanAndScope SpanAndScope = new SpanAndScope(resultSetSpan, tracer.withSpan(resultSetSpan));
		if (log.isTraceEnabled()) {
			log.trace("Started client result set span [" + resultSetSpan + "] - current span is ["
					+ tracer.currentSpan() + "]");
		}
		connectionInfo.getNestedResultSetSpans().put(resultSetKey, SpanAndScope);
		StatementInfo statementInfo = connectionInfo.getNestedStatements().get(statementKey);
		// StatementInfo may be null when Statement is proxied and instance returned from
		// ResultSet is different from instance returned in query method
		// in this case if Statement is closed before ResultSet span won't be finished
		// immediately, but when Connection is closed
		if (statementInfo != null) {
			statementInfo.getNestedResultSetSpans().put(resultSetKey, SpanAndScope);
		}
	}

	void afterResultSetClose(CON connectionKey, RS resultSetKey, int rowCount, Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After result set close");
		}
		ConnectionInfo connectionInfo = openConnections.get(connectionKey);
		// ConnectionInfo may be null if Connection was closed before ResultSet
		if (connectionInfo == null) {
			return;
		}
		SpanAndScope resultSetSpan = connectionInfo.getNestedResultSetSpans().remove(resultSetKey);
		// ResultSet span may be null if Statement or ResultSet were already closed
		if (resultSetSpan == null) {
			return;
		}
		if (rowCount != -1) {
			AssertingSpan.of(SleuthJdbcSpan.JDBC_RESULT_SET_SPAN, resultSetSpan.getSpan())
					.tag(SleuthJdbcSpan.QueryTags.ROW_COUNT, String.valueOf(rowCount));
		}
		if (t != null) {
			resultSetSpan.getSpan().error(t);
		}
		if (log.isTraceEnabled()) {
			log.trace("Closing client result set span [" + resultSetSpan + "] - current span is ["
					+ tracer.currentSpan() + "]");
		}
		resultSetSpan.close();
		if (log.isTraceEnabled()) {
			log.trace("Current span [" + tracer.currentSpan() + "]");
		}
	}

	void afterStatementClose(CON connectionKey, STMT statementKey) {
		if (log.isTraceEnabled()) {
			log.trace("After statement close");
		}
		ConnectionInfo connectionInfo = openConnections.get(connectionKey);
		// ConnectionInfo may be null if Connection was closed before Statement
		if (connectionInfo == null) {
			return;
		}
		StatementInfo statementInfo = connectionInfo.getNestedStatements().remove(statementKey);
		if (statementInfo != null) {
			statementInfo.getNestedResultSetSpans().forEach((resultSetKey, span) -> {
				connectionInfo.getNestedResultSetSpans().remove(resultSetKey);
				if (log.isTraceEnabled()) {
					log.trace("Closing span after statement close [" + span.getSpan() + "] - current span is ["
							+ tracer.currentSpan() + "]");
				}
				span.close();
				if (log.isTraceEnabled()) {
					log.trace("Current span [" + tracer.currentSpan() + "]");
				}
			});
			statementInfo.getNestedResultSetSpans().clear();
		}
	}

	void afterCommit(CON connectionKey, Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After commit");
		}
		ConnectionInfo connectionInfo = openConnections.get(connectionKey);
		if (connectionInfo == null) {
			// Connection is already closed
			return;
		}
		connectionInfo.getSpan().ifPresent(connectionSpan -> {
			if (t != null) {
				connectionSpan.getSpan().error(t);
			}
			AssertingSpan.of(SleuthJdbcSpan.JDBC_QUERY_SPAN, connectionSpan.getSpan())
					.event(SleuthJdbcSpan.QueryEvents.COMMIT);
		});
	}

	void afterRollback(CON connectionKey, Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After rollback");
		}
		ConnectionInfo connectionInfo = openConnections.get(connectionKey);
		if (connectionInfo == null) {
			// Connection is already closed
			return;
		}
		connectionInfo.getSpan().ifPresent(connectionSpan -> {
			if (t != null) {
				connectionSpan.getSpan().error(t);
			}
			else {
				connectionSpan.getSpan().error(new JdbcException("Transaction rolled back"));
			}
			AssertingSpan.of(SleuthJdbcSpan.JDBC_QUERY_SPAN, connectionSpan.getSpan())
					.event(SleuthJdbcSpan.QueryEvents.ROLLBACK);
		});
	}

	void afterConnectionClose(CON connectionKey, Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After connection close with key [" + connectionKey + "]");
		}
		ConnectionInfo connectionInfo = openConnections.remove(connectionKey);
		if (connectionInfo == null) {
			// connection is already closed
			return;
		}
		connectionInfo.getNestedResultSetSpans().values().forEach(SpanAndScope::close);
		connectionInfo.getNestedStatements().values()
				.forEach(statementInfo -> statementInfo.getSpan().ifPresent(SpanAndScope::close));
		if (log.isTraceEnabled()) {
			log.trace("Current span after closing statements [" + tracer.currentSpan() + "]");
		}
		connectionInfo.getSpan().ifPresent(connectionSpan -> {
			if (t != null) {
				connectionSpan.getSpan().error(t);
			}
			if (log.isTraceEnabled()) {
				log.trace("Closing span after connection close [" + connectionSpan.getSpan() + "] - current span is ["
						+ tracer.currentSpan() + "]");
			}
			connectionSpan.close();
			if (log.isTraceEnabled()) {
				log.trace("Current span [" + tracer.currentSpan() + "]");
			}
		});
	}

	private String spanName(String sql) {
		return sql.substring(0, sql.indexOf(' ')).toLowerCase(Locale.ROOT);
	}

	private void parseServerIpAndPort(Connection connection, Span.Builder span) {
		if (connection == null) {
			return;
		}
		UrlAndRemoteServiceName urlAndRemoteServiceName = parseServerIpAndPort(connection);
		span.remoteServiceName(urlAndRemoteServiceName.remoteServiceName);
		URI url = urlAndRemoteServiceName.url;
		if (url != null) {
			span.remoteIpAndPort(url.getHost(), url.getPort());
		}
	}

	private void parseServerIpAndPort(Connection connection, Span span) {
		if (connection == null) {
			return;
		}
		UrlAndRemoteServiceName urlAndRemoteServiceName = parseServerIpAndPort(connection);
		span.remoteServiceName(urlAndRemoteServiceName.remoteServiceName);
		URI url = urlAndRemoteServiceName.url;
		if (url != null) {
			span.remoteIpAndPort(url.getHost(), url.getPort());
		}
	}

	/**
	 * This attempts to get the ip and port from the JDBC URL. Ex. localhost and 5555 from
	 * {@code
	 * jdbc:mysql://localhost:5555/mydatabase}.
	 *
	 * Taken from Brave.
	 */
	private UrlAndRemoteServiceName parseServerIpAndPort(Connection connection) {
		String remoteServiceName = "";
		try {
			String urlAsString = connection.getMetaData().getURL().substring(5); // strip
																					// "jdbc:"
			URI url = URI.create(urlAsString.replace(" ", "")); // Remove all white space
																// according to RFC 2396
			Matcher matcher = URL_SERVICE_NAME_FINDER.matcher(url.toString());
			if (matcher.find() && matcher.groupCount() == 1) {
				String parsedServiceName = matcher.group(1);
				if (parsedServiceName != null && !parsedServiceName.isEmpty()) {
					remoteServiceName = parsedServiceName;
				}
			}
			if (!StringUtils.hasText(remoteServiceName)) {
				String databaseName = connection.getCatalog();
				if (databaseName != null && !databaseName.isEmpty()) {
					remoteServiceName = databaseName;
				}
			}
			return new UrlAndRemoteServiceName(url, remoteServiceName);
		}
		catch (Exception e) {
			// remote address is optional
			return new UrlAndRemoteServiceName(null, remoteServiceName);
		}
	}

	private final class ConnectionInfo {

		private final SpanAndScope span;

		private final Map<STMT, StatementInfo> nestedStatements = new ConcurrentHashMap<>();

		private final Map<RS, SpanAndScope> nestedResultSetSpans = new ConcurrentHashMap<>();

		private ConnectionInfo(@Nullable SpanAndScope span) {
			this.span = span;
		}

		Optional<SpanAndScope> getSpan() {
			return Optional.ofNullable(span);
		}

		Map<STMT, StatementInfo> getNestedStatements() {
			return nestedStatements;
		}

		Map<RS, SpanAndScope> getNestedResultSetSpans() {
			return nestedResultSetSpans;
		}

	}

	private final class StatementInfo {

		private final SpanAndScope span;

		private final Map<RS, SpanAndScope> nestedResultSetSpans = new ConcurrentHashMap<>();

		private StatementInfo(SpanAndScope span) {
			this.span = span;
		}

		Optional<SpanAndScope> getSpan() {
			return Optional.ofNullable(span);
		}

		Map<RS, SpanAndScope> getNestedResultSetSpans() {
			return nestedResultSetSpans;
		}

	}

	private final class UrlAndRemoteServiceName {

		final URI url;

		final String remoteServiceName;

		private UrlAndRemoteServiceName(@Nullable URI url, String remoteServiceName) {
			this.url = url;
			this.remoteServiceName = remoteServiceName;
		}

	}

	private static final class JdbcException extends RuntimeException {

		JdbcException(String message) {
			super(message);
		}

	}

}
