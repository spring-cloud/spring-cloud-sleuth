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
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpanBuilder;
import org.springframework.cloud.sleuth.internal.ContextUtil;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * A {@link MethodInterceptor} that wraps calls around {@link CqlSession} in a trace
 * representation.
 *
 * @author Mark Paluch
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TraceCassandraInterceptor implements MethodInterceptor {

	private static final Log log = LogFactory.getLog(TraceCassandraInterceptor.class);

	private final CqlSession session;

	private final BeanFactory beanFactory;

	private Tracer tracer;

	public TraceCassandraInterceptor(CqlSession session, BeanFactory beanFactory) {
		this.session = session;
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
		if (method.getName().equals("execute")) {
			if (args.length > 0) {
				if (args[0] instanceof Statement) {
					return tracedExecute((Statement) args[0]);
				}
				else if (args[0] instanceof String && args.length == 1) {
					return tracedExecute(SimpleStatement.newInstance((String) args[0]));
				}
				else if (args[0] instanceof String && args.length == 2) {
					String query = (String) args[0];
					SimpleStatement statement = args[1] instanceof Map
							? SimpleStatement.newInstance(query, (Map) args[1])
							: SimpleStatement.newInstance(query, (Object[]) args[1]);
					return tracedExecute(statement);
				}
			}
		}
		if (method.getName().equals("executeAsync")) {
			if (args.length > 0) {
				if (args[0] instanceof Statement) {
					return tracedExecuteAsync((Statement) args[0]);
				}
				else if (args[0] instanceof String && args.length == 1) {
					return tracedExecuteAsync(SimpleStatement.newInstance((String) args[0]));
				}
				else if (args[0] instanceof String && args.length == 2) {
					String query = (String) args[0];
					SimpleStatement statement = args[1] instanceof Map
							? SimpleStatement.newInstance(query, (Map) args[1])
							: SimpleStatement.newInstance(query, (Object[]) args[1]);
					return tracedExecuteAsync(statement);
				}
			}
		}
		if (method.getName().equals("prepare")) {
			if (args.length > 0) {
				if (args[0] instanceof SimpleStatement) {
					return tracedPrepare((SimpleStatement) args[0]);
				}
				else if (args[0] instanceof String && args.length == 1) {
					return tracedPrepare(SimpleStatement.newInstance((String) args[0]));
				}
				else if (args[0] instanceof String && args.length == 2) {
					String query = (String) args[0];
					SimpleStatement statement = args[1] instanceof Map
							? SimpleStatement.newInstance(query, (Map) args[1])
							: SimpleStatement.newInstance(query, (Object[]) args[1]);
					return tracedPrepare(statement);
				}
			}
		}
		return invocation.proceed();
	}

	boolean isContextUnusable() {
		return ContextUtil.isContextUnusable(this.beanFactory);
	}

	private Object tracedExecute(Statement statement) {
		return tracedCall(statement, "execute", session::execute);
	}

	private Object tracedExecuteAsync(Statement statement) {
		return tracedCall(statement, "executeAsync", session::executeAsync);
	}

	private Object tracedPrepare(SimpleStatement statement) {
		return tracedCall(statement, "prepare", proxied -> session.prepare((SimpleStatement) proxied));
	}

	private Object tracedCall(Statement statement, String defaultSpanName, Function<Statement<?>, Object> function) {
		Span span = cassandraClientSpan();
		Statement<?> proxied = TraceStatement.createProxy(span, statement);
		((CassandraSpanCustomizer) proxied).customizeSpan(defaultSpanName);
		try (Tracer.SpanInScope ws = tracer().withSpan(span)) {
			log.debug("Will execute statement");
			return function.apply(proxied);
		}
	}

	private Span cassandraClientSpan() {
		return AssertingSpanBuilder.of(SleuthCassandraSpan.CASSANDRA_SPAN, tracer().spanBuilder())
				.kind(Span.Kind.CLIENT).remoteServiceName("cassandra-" + getSessionName())
				.tag(SleuthCassandraSpan.Tags.KEYSPACE_NAME,
						getKeyspace().map(CqlIdentifier::asInternal).orElse("unknown"))
				.start();
	}

	String getSessionName() {
		return session.getContext().getSessionName();
	}

	Optional<CqlIdentifier> getKeyspace() {
		return session.getKeyspace();
	}

	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

}
