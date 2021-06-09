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

import java.util.StringJoiner;

import javax.annotation.Nonnull;

import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchableStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Trace implementation of a {@link Statement}.
 *
 * @author Mark Paluch
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
// TODO: Package scope?
public class TraceStatement implements MethodInterceptor {

	private final Span span;

	private Statement<?> delegate;

	public TraceStatement(Span span, Statement<?> delegate) {
		this.span = span;
		this.delegate = delegate;
	}

	/**
	 * @return stored span
	 */
	public Span getSpan() {
		return this.span;
	}

	/**
	 * Tries to parse the CQL query or provides the default name.
	 * @param defaultName if there's not query
	 * @return span name
	 */
	public String getSpanName(String defaultName) {
		String query = getCql();
		if (query.indexOf(' ') > -1) {
			return query.substring(0, query.indexOf(' '));
		}
		return defaultName;
	}

	private String getCql() {
		String query = "";
		if (this.delegate instanceof SimpleStatement) {
			query = getQuery(this.delegate);
		}
		else if (this.delegate instanceof BoundStatement) {
			query = getQuery(this.delegate);
		}
		else if (this.delegate instanceof BatchStatement) {
			StringJoiner joiner = new StringJoiner(";");
			for (BatchableStatement bs : (BatchStatement) this.delegate) {
				joiner.add(getQuery(bs));
			}
			query = joiner.toString();
		}
		return query;
	}

	private static String getQuery(Statement<?> statement) {
		if (statement instanceof SimpleStatement) {
			return ((SimpleStatement) statement).getQuery();
		}
		else if (statement instanceof BoundStatement) {
			return ((BoundStatement) statement).getPreparedStatement().getQuery();
		}
		return "";
	}

	/**
	 * Creates a proxy with {@link TraceStatement} attached to it.
	 * @param span current span
	 * @param target target to proxy
	 * @param <T> type of target
	 * @return proxied object with trace advice
	 */
	public static <T> T createProxy(Span span, T target) {
		ProxyFactory factory = new ProxyFactory(ClassUtils.getAllInterfaces(target));
		factory.addInterface(CassandraSpanCustomizer.class);
		factory.addInterface(CassandraSpanSupplier.class);
		factory.setTarget(target);
		factory.addAdvice(new TraceStatement(span, (Statement<?>) target));
		return (T) factory.getProxy();
	}

	@Nullable
	@Override
	public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
		if (invocation.getMethod().getName().equals("getSpan")) {
			return this.span;
		}
		if (invocation.getMethod().getName().equals("customizeSpan")) {
			AssertingSpan.of(SleuthCassandraSpan.CASSANDRA_SPAN, this.span)
					.name(getSpanName((String) invocation.getArguments()[0]))
					.tag(SleuthCassandraSpan.Tags.CQL_TAG, getCql());
			return null;
		}
		Object result = invocation.proceed();
		if (result instanceof Statement<?>) {
			this.delegate = (Statement<?>) result;
		}
		return result;
	}

}
