/*
 * Copyright 2018-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.cassandra;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpanBuilder;
import org.springframework.cloud.sleuth.internal.ContextUtil;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * A {@link MethodInterceptor} that wraps calls around {@link CqlSession} in a trace
 * representation. This interceptor wraps statements for {@code execute} and
 * {@code prepare} (including their asynchronous variants) only. Graph and reactive
 * {@link CqlSession} method remain called as-is.
 *
 * @author Mark Paluch
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
class TraceCqlSessionInterceptor implements MethodInterceptor {

	private static final Log log = LogFactory.getLog(TraceCqlSessionInterceptor.class);

	private final CqlSession delegate;

	private final BeanFactory beanFactory;

	private Tracer tracer;

	private CurrentTraceContext currentTraceContext;

	TraceCqlSessionInterceptor(CqlSession delegate, BeanFactory beanFactory) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
	}

	@Nullable
	@Override
	public Object invoke(@NonNull MethodInvocation invocation) throws Throwable {
		Method method = invocation.getMethod();
		Object[] args = invocation.getArguments();
		if (isContextUnusable()) {
			return invocation.proceed();
		}
		if (method.getName().equals("execute") && args.length > 0) {
			return observe(createStatement(args), method.getName(), this.delegate::execute);
		}

		if (method.getName().equals("executeAsync") && args.length > 0) {
			return observe(createStatement(args), method.getName(), this.delegate::executeAsync);
		}
		// prepare calls do not notify RequestTracker so we need to stop the observation
		// ourselves
		if (method.getName().equals("prepare") && args.length > 0) {

			Statement<?> statement = createStatement(args);

			if (TraceStatement.isTraceStatement(statement)) {
				return this.delegate.prepareAsync((SimpleStatement) statement);
			}

			Span span = cassandraClientSpan();
			Statement<?> proxied = TraceStatement.createProxy(span, statement);
			((CassandraSpanCustomizer) proxied).customizeSpan("prepare");
			try {
				return this.delegate.prepare((SimpleStatement) proxied);
			}
			catch (RuntimeException e) {
				span.error(e);
				throw e;
			}
			finally {
				span.end();
			}
		}
		if (method.getName().equals("prepareAsync") && args.length > 0) {
			Statement<?> statement = createStatement(args);

			if (TraceStatement.isTraceStatement(statement)) {
				return this.delegate.prepareAsync((SimpleStatement) statement);
			}

			Span span = cassandraClientSpan();
			Statement<?> proxied = TraceStatement.createProxy(span, statement);
			((CassandraSpanCustomizer) proxied).customizeSpan("prepareAsync");

			return this.delegate.prepareAsync((SimpleStatement) proxied)
					.whenComplete((preparedStatement, throwable) -> {

						if (throwable != null) {
							span.error(throwable);
						}

						span.end();
					});
		}
		return invocation.proceed();
	}

	/**
	 * Observe a {@link Statement}.
	 * @param statement original CQL {@link Statement}
	 * @param statementExecutor function that transforms a {@link Statement} into a
	 * resulting {@link Object}
	 * @return the statement execution result.
	 */
	private Object observe(Statement<?> statement, String methodName,
			Function<Statement<?>, Object> statementExecutor) {
		// avoid duplicate statement wrapping
		if (TraceStatement.isTraceStatement(statement)) {
			return statementExecutor.apply(statement);
		}
		Statement<?> proxied = getStatement(statement, methodName);
		return statementExecutor.apply(proxied);
	}

	private Statement<?> getStatement(Statement<?> statement, String methodName) {
		Span span = cassandraClientSpan();
		Statement<?> proxied = TraceStatement.createProxy(span, statement);
		((CassandraSpanCustomizer) proxied).customizeSpan(methodName);
		return proxied;
	}

	private static Statement<?> createStatement(Object[] args) {
		if (args[0] instanceof Statement) {
			return (Statement<?>) (args[0]);
		}
		else if (args[0] instanceof String && args.length == 1) {
			return SimpleStatement.newInstance((String) args[0]);
		}
		else if (args[0] instanceof String && args.length == 2) {
			String query = (String) args[0];
			return args[1] instanceof Map ? SimpleStatement.newInstance(query, (Map) args[1])
					: SimpleStatement.newInstance(query, (Object[]) args[1]);
		}
		throw new IllegalArgumentException(String.format("Unsupported arguments %s", Arrays.toString(args)));
	}

	boolean isContextUnusable() {
		return ContextUtil.isContextUnusable(this.beanFactory);
	}

	private Span cassandraClientSpan() {
		return cassandraClientSpan(tracer().spanBuilder(), getSessionName(), getKeyspace());
	}

	static Span cassandraClientSpan(Span.Builder builder, String sessionName, Optional<CqlIdentifier> keyspace) {
		return AssertingSpanBuilder.of(SleuthCassandraSpan.CASSANDRA_SPAN, builder).kind(Span.Kind.CLIENT)
				.remoteServiceName("cassandra-" + sessionName)
				.tag(SleuthCassandraSpan.Tags.KEYSPACE_NAME, keyspace.map(CqlIdentifier::asInternal).orElse("unknown"))
				.start();
	}

	String getSessionName() {
		return this.delegate.getContext().getSessionName();
	}

	Optional<CqlIdentifier> getKeyspace() {
		return this.delegate.getKeyspace();
	}

	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

}
