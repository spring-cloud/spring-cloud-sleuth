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

package org.springframework.cloud.sleuth.instrument.r2dbc;

import java.net.URI;

import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.proxy.listener.ProxyExecutionListener;
import io.r2dbc.spi.ConnectionFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.cloud.sleuth.docs.AssertingSpanBuilder;
import org.springframework.cloud.sleuth.internal.ContextUtil;
import org.springframework.util.StringUtils;

/**
 * Trace representation of a {@link ProxyExecutionListener}.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TraceProxyExecutionListener implements ProxyExecutionListener {

	private static final Log log = LogFactory.getLog(TraceProxyExecutionListener.class);

	private final BeanFactory beanFactory;

	private final ConnectionFactory connectionFactory;

	private Tracer tracer;

	public TraceProxyExecutionListener(BeanFactory beanFactory, ConnectionFactory connectionFactory) {
		this.beanFactory = beanFactory;
		this.connectionFactory = connectionFactory;
	}

	@Override
	public void beforeQuery(QueryExecutionInfo executionInfo) {
		if (isContextUnusable()) {
			if (log.isDebugEnabled()) {
				log.debug("Context is not ready - won't do anything");
			}
			return;
		}
		else if (tracer().currentSpan() == null) {
			return;
		}
		String name = this.connectionFactory.getMetadata().getName();
		AssertingSpan span = clientSpan(executionInfo, name);
		if (log.isDebugEnabled()) {
			log.debug("Created a new child span before query [" + span + "]");
		}
		tagQueries(executionInfo, span);
		executionInfo.getValueStore().put(Span.class, span);
	}

	AssertingSpan clientSpan(QueryExecutionInfo executionInfo, String name) {
		R2dbcProperties r2dbcProperties = this.beanFactory.getBean(R2dbcProperties.class);
		String url = r2dbcProperties.getUrl();
		// @formatter:off
		AssertingSpanBuilder builder = AssertingSpanBuilder.of(SleuthR2dbcSpan.R2DBC_QUERY_SPAN, tracer.spanBuilder())
				.kind(Span.Kind.CLIENT)
				.name(SleuthR2dbcSpan.R2DBC_QUERY_SPAN.getName()).remoteServiceName(name)
				.tag(SleuthR2dbcSpan.Tags.CONNECTION, name)
				.tag(SleuthR2dbcSpan.Tags.THREAD, executionInfo.getThreadName());
		// @formatter:on
		if (StringUtils.hasText(url)) {
			try {
				URI uri = URI.create(url);
				builder.remoteUrl(uri.getHost(), uri.getPort());
			}
			catch (Exception e) {
				if (log.isDebugEnabled()) {
					log.debug("Failed to parse the url [" + url + "]. Won't set this value as atag");
				}
			}
		}
		return builder.start();
	}

	private void tagQueries(QueryExecutionInfo executionInfo, AssertingSpan span) {
		int i = 0;
		for (QueryInfo queryInfo : executionInfo.getQueries()) {
			span.tag(String.format(SleuthR2dbcSpan.Tags.QUERY.getKey(), i), queryInfo.getQuery());
			i = i + 1;
		}
	}

	@Override
	public void afterQuery(QueryExecutionInfo executionInfo) {
		if (isContextUnusable()) {
			if (log.isDebugEnabled()) {
				log.debug("Context is not ready - won't do anything");
			}
			return;
		}
		Span span = executionInfo.getValueStore().get(Span.class, Span.class);
		if (span != null) {
			if (log.isDebugEnabled()) {
				log.debug("Continued the child span in after query [" + span + "]");
			}
			final Throwable throwable = executionInfo.getThrowable();
			if (throwable != null) {
				span.error(throwable);
			}
			span.end();
		}
	}

	@Override
	public void eachQueryResult(QueryExecutionInfo executionInfo) {
		if (isContextUnusable()) {
			if (log.isDebugEnabled()) {
				log.debug("Context is not ready - won't do anything");
			}
			return;
		}
		Span span = executionInfo.getValueStore().get(Span.class, Span.class);
		if (span != null) {
			if (log.isDebugEnabled()) {
				log.debug("Marking after query result for span [" + span + "]");
			}
			SleuthR2dbcSpan.R2DBC_QUERY_SPAN.wrap(span).event(SleuthR2dbcSpan.Events.QUERY_RESULT);
		}
	}

	boolean isContextUnusable() {
		return ContextUtil.isContextUnusable(this.beanFactory);
	}

	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

}
