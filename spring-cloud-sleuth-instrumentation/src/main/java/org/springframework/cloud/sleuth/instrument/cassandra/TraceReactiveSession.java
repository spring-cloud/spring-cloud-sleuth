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

import java.util.Map;
import java.util.Optional;

import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;
import org.springframework.data.cassandra.ReactiveResultSet;
import org.springframework.data.cassandra.ReactiveSession;

/**
 * Tracing variant of {@link ReactiveSession}.
 *
 * @author Mark Paluch
 * @author Marcin Grzejszczak
 */
public class TraceReactiveSession implements ReactiveSession {

	private final ReactiveSession delegate;

	private final BeanFactory beanFactory;

	private Tracer tracer;

	private CurrentTraceContext currentTraceContext;

	public TraceReactiveSession(ReactiveSession delegate, BeanFactory beanFactory) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
	}

	@Override
	public boolean isClosed() {
		return this.delegate.isClosed();
	}

	@Override
	public DriverContext getContext() {
		return this.delegate.getContext();
	}

	@Override
	public Mono<ReactiveResultSet> execute(String cql) {
		return execute(SimpleStatement.newInstance(cql));
	}

	@Override
	public Mono<ReactiveResultSet> execute(String cql, Object... objects) {
		return execute(SimpleStatement.newInstance(cql, objects));
	}

	@Override
	public Mono<ReactiveResultSet> execute(String cql, Map<String, Object> map) {
		return execute(SimpleStatement.newInstance(cql, map));
	}

	@Override
	public Mono<ReactiveResultSet> execute(Statement<?> statement) {
		return Mono.deferContextual(contextView -> {
			Span span = null;
			return this.delegate.execute(proxiedStatement(span, statement, "execute"));
		}).contextWrite(context -> {
			return ReactorSleuth.putSpanInScope(tracer(), context, createSpan(context));
		});
	}

	@Override
	public Mono<PreparedStatement> prepare(String cql) {
		return prepare(SimpleStatement.newInstance(cql));
	}

	@Override
	public Mono<PreparedStatement> prepare(SimpleStatement statement) {
		return Mono.deferContextual(contextView -> {
			Span span = ReactorSleuth
					.spanFromContext(tracer(), currentTraceContext(), contextView);
			return this.delegate
					.prepare((SimpleStatement) proxiedStatement(span, statement, "prepare"));
		}).contextWrite(context -> ReactorSleuth
				.putSpanInScope(tracer(), context, createSpan(context)));
	}

	private Statement<?> proxiedStatement(Span span, Statement<?> statement, String defaultName) {
		Statement<?> proxied = TraceStatement.createProxy(span, statement);
		((CassandraSpanCustomizer) proxied).customizeSpan(defaultName);
		return proxied;
	}

	@Override
	public void close() {
		this.delegate.close();
	}

	private Span createSpan(ContextView contextView) {
		return TraceCqlSessionInterceptor
				.cassandraClientSpan(spanBuilder(contextView), getContext()
								.getSessionName(),
						Optional.empty() /* todo @since 3.2.2 */);
	}

	private Span.Builder spanBuilder(ContextView contextView) {
		Span.Builder spanBuilder = tracer().spanBuilder();
		if (contextView.hasKey(TraceContext.class)) {
			return spanBuilder.setParent(contextView.get(TraceContext.class));
		}
		else if (contextView.hasKey(Span.class)) {
			return spanBuilder.setParent(contextView.get(Span.class).context());
		}
		Span span = tracer().currentSpan();
		if (span != null) {
			return spanBuilder.setParent(span.context());
		}
		return spanBuilder;
	}

	private CurrentTraceContext currentTraceContext() {
		if (this.currentTraceContext == null) {
			this.currentTraceContext = beanFactory.getBean(CurrentTraceContext.class);
		}
		return this.currentTraceContext;
	}

	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

}
