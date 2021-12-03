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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Trace listener strategy makes the best effort at tracking all open JDBC resources (and
 * therefore spans/scopes) because of two main reasons: 1. JDBC allows to not close child
 * resources, in which case closing the parent will close everything -
 * {@link Connection#close()} closes all underlying {@link Statement}s and
 * {@link Statement#close()} close all underlying {@link ResultSet}s. Ideally this should
 * not happen, but practically some applications (and some frameworks) rely on this
 * mechanism. 2. While most JDBC drivers don't support concurrency, multiple connections
 * might be opened at the same time in the same thread. JDBC treats those connections as
 * completely separate resources, and we cannot rely on the order of closing those
 * connections.
 *
 * Tracking covers such cases as long as resources are closed in the same thread they were
 * opened.
 *
 * Partially taken from
 * https://github.com/openzipkin/brave/blob/v5.6.4/instrumentation/p6spy/src/main/java/brave/p6spy/TracingJdbcEventListener.java
 * and
 * https://github.com/gavlyukovskiy/spring-boot-data-source-decorator/blob/master/datasource-decorator-spring-boot-autoconfigure/src/main/java/com/github/gavlyukovskiy/cloud/sleuth/TracingListenerStrategy.java.
 *
 * @param <CON> connection type
 * @param <STMT> statement
 * @param <RS> result set
 * @author Arthur Gavlyukovskiy
 */
class TraceListenerStrategy<CON, STMT, RS> {

	private static final Log log = LogFactory.getLog(TraceListenerStrategy.class);

	// Captures all the characters between = and either the next & or the end of the
	// string.
	private static final Pattern URL_SERVICE_NAME_FINDER = Pattern.compile("sleuthServiceName=(.*?)(?:&|$)");

	private final Map<CON, ConnectionInfo> openConnections = new ConcurrentHashMap<>();

	private final ThreadLocal<ConnectionInfo> currentConnection = new ThreadLocal<>();

	private final Tracer tracer;

	private final List<TraceType> traceTypes;

	private final List<TraceListenerStrategySpanCustomizer<? super CommonDataSource>> customizers;

	TraceListenerStrategy(Tracer tracer, List<TraceType> traceTypes,
			List<TraceListenerStrategySpanCustomizer<? super CommonDataSource>> customizers) {
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
			Tracer.SpanInScope scope = isCurrent(null) ? tracer.withSpan(connectionSpan) : null;
			spanAndScope = new SpanAndScope(connectionSpan, scope);
			if (log.isTraceEnabled()) {
				log.trace("Started client span before connection [" + connectionSpan + "] - current span is ["
						+ tracer.currentSpan() + "]");
			}
		}
		ConnectionInfo connectionInfo = new ConnectionInfo(spanAndScope);
		connectionInfo.remoteServiceName = dataSourceName;
		this.openConnections.put(connectionKey, connectionInfo);
		if (isCurrent(null)) {
			this.currentConnection.set(connectionInfo);
		}
	}

	void afterGetConnection(CON connectionKey, @Nullable Connection connection, String dataSourceName,
			@Nullable Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After get connection [" + connectionKey + "]. Current span is [" + tracer.currentSpan() + "]");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		SpanAndScope connectionSpan = connectionInfo.span;
		if (connection != null) {
			parseAndSetServerIpAndPort(connectionInfo, connection, dataSourceName);
			if (connectionSpan != null) {
				connectionSpan.getSpan().remoteServiceName(connectionInfo.remoteServiceName);
				connectionSpan.getSpan().remoteIpAndPort(connectionInfo.url.getHost(), connectionInfo.url.getPort());
			}
		}
		else if (t != null) {
			this.openConnections.remove(connectionKey);
			if (isCurrent(connectionInfo)) {
				this.currentConnection.set(null);
			}
			if (connectionSpan != null) {
				if (log.isTraceEnabled()) {
					log.trace("Closing client span due to exception [" + connectionSpan.getSpan()
							+ "] - current span is [" + tracer.currentSpan() + "]");
				}
				connectionSpan.getSpan().error(t);
				connectionSpan.close();
				if (log.isTraceEnabled()) {
					log.trace("Current span [" + tracer.currentSpan() + "]");
				}
			}
		}
	}

	/**
	 * Returns true if connection belong to the one, that is currently in scope.
	 */
	private boolean isCurrent(@Nullable ConnectionInfo connectionInfo) {
		return this.currentConnection.get() == connectionInfo;
	}

	void beforeQuery(CON connectionKey, STMT statementKey) {
		if (log.isTraceEnabled()) {
			log.trace("Before query - connection [" + connectionKey + "] and current span [" + tracer.currentSpan()
					+ "]");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
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
			statementSpanBuilder.remoteServiceName(connectionInfo.remoteServiceName);
			if (connectionInfo.url != null) {
				statementSpanBuilder.remoteIpAndPort(connectionInfo.url.getHost(), connectionInfo.url.getPort());
			}
			statementSpanBuilder.kind(Span.Kind.CLIENT);
			Span statementSpan = statementSpanBuilder.start();
			Tracer.SpanInScope scope = isCurrent(connectionInfo) ? tracer.withSpan(statementSpan) : null;
			spanAndScope = new SpanAndScope(statementSpan, scope);
			if (log.isTraceEnabled()) {
				log.trace("Started client span before query [" + statementSpan + "] - current span is ["
						+ tracer.currentSpan() + "]");
			}
		}
		StatementInfo statementInfo = new StatementInfo(spanAndScope);
		connectionInfo.nestedStatements.put(statementKey, statementInfo);
	}

	void addQueryRowCount(CON connectionKey, STMT statementKey, int rowCount) {
		if (log.isTraceEnabled()) {
			log.trace("Add query row count for connection key [" + connectionKey + "]");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		if (connectionInfo == null) {
			if (log.isTraceEnabled()) {
				log.trace("Connection is already closed");
			}
			return;
		}
		StatementInfo statementInfo = connectionInfo.nestedStatements.get(statementKey);
		SpanAndScope statementSpan = statementInfo.span;
		if (statementSpan != null) {
			AssertingSpan.of(SleuthJdbcSpan.JDBC_QUERY_SPAN, statementSpan.getSpan())
					.tag(SleuthJdbcSpan.QueryTags.ROW_COUNT, String.valueOf(rowCount));
		}
	}

	void afterQuery(CON connectionKey, STMT statementKey, String sql, @Nullable Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After query for connection key [" + connectionKey + "]");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		if (connectionInfo == null) {
			if (log.isTraceEnabled()) {
				log.trace(
						"Connection may be closed after statement preparation, but before statement execution. Current span is ["
								+ tracer.currentSpan() + "]");
			}
			return;
		}
		StatementInfo statementInfo = connectionInfo.nestedStatements.get(statementKey);
		SpanAndScope statementSpan = statementInfo.span;
		if (statementSpan != null) {
			AssertingSpan.of(SleuthJdbcSpan.JDBC_QUERY_SPAN, statementSpan.getSpan())
					.tag(SleuthJdbcSpan.QueryTags.QUERY, sql).name(spanName(sql));
			if (t != null) {
				statementSpan.getSpan().error(t);
			}
			if (log.isTraceEnabled()) {
				log.trace("Closing statement span [" + statementSpan + "] - current span is [" + tracer.currentSpan()
						+ "]");
			}
			statementSpan.close();
			if (log.isTraceEnabled()) {
				log.trace("Current span [" + tracer.currentSpan() + "]");
			}
		}
	}

	void beforeResultSetNext(CON connectionKey, STMT statementKey, RS resultSetKey) {
		if (log.isTraceEnabled()) {
			log.trace("Before result set next");
		}
		if (!traceTypes.contains(TraceType.FETCH)) {
			return;
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		// ConnectionInfo may be null if Connection was closed before ResultSet
		if (connectionInfo == null) {
			if (log.isTraceEnabled()) {
				log.trace("No connection info, skipping");
			}
			return;
		}
		if (connectionInfo.nestedResultSetSpans.containsKey(resultSetKey)) {
			if (log.isTraceEnabled()) {
				log.trace("ResultSet span is already created");
			}
			return;
		}
		AssertingSpanBuilder resultSetSpanBuilder = AssertingSpanBuilder
				.of(SleuthJdbcSpan.JDBC_RESULT_SET_SPAN, tracer.spanBuilder())
				.name(SleuthJdbcSpan.JDBC_RESULT_SET_SPAN.getName());
		resultSetSpanBuilder.kind(Span.Kind.CLIENT);
		resultSetSpanBuilder.remoteServiceName(connectionInfo.remoteServiceName);
		if (connectionInfo.url != null) {
			resultSetSpanBuilder.remoteIpAndPort(connectionInfo.url.getHost(), connectionInfo.url.getPort());
		}
		Span resultSetSpan = resultSetSpanBuilder.start();
		Tracer.SpanInScope scope = isCurrent(connectionInfo) ? tracer.withSpan(resultSetSpan) : null;
		SpanAndScope spanAndScope = new SpanAndScope(resultSetSpan, scope);
		if (log.isTraceEnabled()) {
			log.trace("Started client result set span [" + resultSetSpan + "] - current span is ["
					+ tracer.currentSpan() + "]");
		}
		connectionInfo.nestedResultSetSpans.put(resultSetKey, spanAndScope);
		StatementInfo statementInfo = connectionInfo.nestedStatements.get(statementKey);
		// StatementInfo may be null when Statement is proxied and instance returned from
		// ResultSet is different from instance returned in query method
		// in this case if Statement is closed before ResultSet span won't be finished
		// immediately, but when Connection is closed
		if (statementInfo != null) {
			statementInfo.nestedResultSetSpans.put(resultSetKey, spanAndScope);
		}
	}

	void afterStatementClose(CON connectionKey, STMT statementKey) {
		if (log.isTraceEnabled()) {
			log.trace("After statement close");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		// ConnectionInfo may be null if Connection was closed before Statement
		if (connectionInfo == null) {
			return;
		}
		StatementInfo statementInfo = connectionInfo.nestedStatements.remove(statementKey);
		if (statementInfo != null) {
			statementInfo.nestedResultSetSpans.forEach((resultSetKey, span) -> {
				connectionInfo.nestedResultSetSpans.remove(resultSetKey);
				if (log.isTraceEnabled()) {
					log.trace("Closing span after statement close [" + span.getSpan() + "] - current span is ["
							+ tracer.currentSpan() + "]");
				}
				span.close();
				if (log.isTraceEnabled()) {
					log.trace("Current span [" + tracer.currentSpan() + "]");
				}
			});
			statementInfo.nestedResultSetSpans.clear();
		}
	}

	void afterResultSetClose(CON connectionKey, RS resultSetKey, int rowCount, @Nullable Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After result set close");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		// ConnectionInfo may be null if Connection was closed before ResultSet
		if (connectionInfo == null) {
			return;
		}
		SpanAndScope resultSetSpan = connectionInfo.nestedResultSetSpans.remove(resultSetKey);
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

	void afterCommit(CON connectionKey, @Nullable Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After commit");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		if (connectionInfo == null) {
			// Connection is already closed
			return;
		}
		SpanAndScope connectionSpan = connectionInfo.span;
		if (connectionSpan != null) {
			if (t != null) {
				connectionSpan.getSpan().error(t);
			}
			AssertingSpan.of(SleuthJdbcSpan.JDBC_QUERY_SPAN, connectionSpan.getSpan())
					.event(SleuthJdbcSpan.QueryEvents.COMMIT);
		}
	}

	void afterRollback(CON connectionKey, @Nullable Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After rollback");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		if (connectionInfo == null) {
			// Connection is already closed
			return;
		}
		SpanAndScope connectionSpan = connectionInfo.span;
		if (connectionSpan != null) {
			if (t != null) {
				connectionSpan.getSpan().error(t);
			}
			else {
				connectionSpan.getSpan().error(new JdbcException("Transaction rolled back"));
			}
			AssertingSpan.of(SleuthJdbcSpan.JDBC_QUERY_SPAN, connectionSpan.getSpan())
					.event(SleuthJdbcSpan.QueryEvents.ROLLBACK);
		}
	}

	void afterConnectionClose(CON connectionKey, @Nullable Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After connection close with key [" + connectionKey + "]");
		}
		ConnectionInfo connectionInfo = this.openConnections.remove(connectionKey);
		if (isCurrent(connectionInfo)) {
			this.currentConnection.set(null);
		}
		if (connectionInfo == null) {
			// connection is already closed
			return;
		}
		connectionInfo.nestedResultSetSpans.values().forEach(SpanAndScope::close);
		connectionInfo.nestedStatements.values().forEach(statementInfo -> {
			SpanAndScope statementSpan = statementInfo.span;
			if (statementSpan != null) {
				statementSpan.close();
			}
		});
		if (log.isTraceEnabled()) {
			log.trace("Current span after closing statements [" + tracer.currentSpan() + "]");
		}
		SpanAndScope connectionSpan = connectionInfo.span;
		if (connectionSpan != null) {
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
		}
	}

	private String spanName(String sql) {
		return sql.substring(0, sql.indexOf(' ')).toLowerCase(Locale.ROOT);
	}

	/**
	 * This attempts to get the ip and port from the JDBC URL. Ex. localhost and 5555 from
	 * {@code
	 * jdbc:mysql://localhost:5555/mydatabase}.
	 *
	 * Taken from Brave.
	 */
	private void parseAndSetServerIpAndPort(ConnectionInfo connectionInfo, Connection connection,
			String dataSourceName) {
		URI url = null;
		String remoteServiceName = "";
		try {
			String urlAsString = connection.getMetaData().getURL().substring(5); // strip
																					// "jdbc:"
			url = URI.create(urlAsString.replace(" ", "")); // Remove all white space
															// according to RFC 2396;
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
		}
		catch (Exception e) {
			// remote address is optional
		}
		connectionInfo.url = url;
		if (StringUtils.hasText(remoteServiceName)) {
			connectionInfo.remoteServiceName = remoteServiceName;
		}
		else {
			connectionInfo.remoteServiceName = dataSourceName;
		}
	}

	private final class ConnectionInfo {

		final SpanAndScope span;

		final Map<STMT, StatementInfo> nestedStatements = new ConcurrentHashMap<>();

		final Map<RS, SpanAndScope> nestedResultSetSpans = new ConcurrentHashMap<>();

		URI url;

		String remoteServiceName;

		ConnectionInfo(@Nullable SpanAndScope span) {
			this.span = span;
		}

	}

	private final class StatementInfo {

		final SpanAndScope span;

		final Map<RS, SpanAndScope> nestedResultSetSpans = new ConcurrentHashMap<>();

		StatementInfo(SpanAndScope span) {
			this.span = span;
		}

	}

	private static final class JdbcException extends RuntimeException {

		JdbcException(String message) {
			super(message);
		}

	}

}
