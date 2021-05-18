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

import java.util.concurrent.atomic.AtomicReference;

import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.ValueStore;
import io.r2dbc.proxy.test.MockQueryExecutionInfo;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.cloud.sleuth.tracer.SimpleSpan;
import org.springframework.cloud.sleuth.tracer.SimpleTracer;

import static org.assertj.core.api.BDDAssertions.then;

class TraceProxyExecutionListenerTests {

	SimpleTracer simpleTracer = new SimpleTracer();

	ConnectionFactory connectionFactory = connectionFactory();

	TraceProxyExecutionListener listener = new TraceProxyExecutionListener(beanFactory(), connectionFactory) {
		@Override
		boolean isContextUnusable() {
			return false;
		}
	};

	@Test
	void should_do_nothing_on_before_query_when_there_was_no_previous_span() {
		MockQueryExecutionInfo queryExecutionInfo = MockQueryExecutionInfo.empty();

		listener.beforeQuery(queryExecutionInfo);

		then(this.simpleTracer.spans).isEmpty();
		then(queryExecutionInfo.getValueStore().get(Span.class)).isNull();
	}

	@Test
	void should_do_nothing_on_after_query_when_there_was_no_previous_span() {
		MockQueryExecutionInfo queryExecutionInfo = MockQueryExecutionInfo.empty();

		listener.afterQuery(queryExecutionInfo);

		then(this.simpleTracer.spans).isEmpty();
		then(queryExecutionInfo.getValueStore().get(Span.class)).isNull();
	}

	@Test
	void should_do_nothing_on_each_query_when_there_was_no_previous_span() {
		MockQueryExecutionInfo queryExecutionInfo = MockQueryExecutionInfo.empty();

		listener.eachQueryResult(queryExecutionInfo);

		then(this.simpleTracer.spans).isEmpty();
		then(queryExecutionInfo.getValueStore().get(Span.class)).isNull();
	}

	@Test
	void should_put_a_child_span_in_value_store_when_a_span_was_already_in_context() {
		MockQueryExecutionInfo queryExecutionInfo = MockQueryExecutionInfo.empty();
		this.simpleTracer.nextSpan().start();
		AtomicReference<Span> clientSpan = new AtomicReference<>();
		listener = new TraceProxyExecutionListener(beanFactory(), connectionFactory) {
			@Override
			AssertingSpan clientSpan(QueryExecutionInfo executionInfo, String name) {
				AssertingSpan span = super.clientSpan(executionInfo, name);
				clientSpan.set(span);
				return span;
			}
		};

		listener.beforeQuery(queryExecutionInfo);

		then(queryExecutionInfo.getValueStore().get(Span.class)).isSameAs(clientSpan.get());
	}

	@Test
	void should_annotate_a_span_on_query_result() {
		SimpleSpan span = new SimpleSpan();
		ValueStore valueStore = ValueStore.create();
		valueStore.put(Span.class, span);
		MockQueryExecutionInfo queryExecutionInfo = MockQueryExecutionInfo.builder().valueStore(valueStore).build();

		listener.eachQueryResult(queryExecutionInfo);

		then(span.events).isNotEmpty();
	}

	@Test
	void should_end_span_on_after_query() {
		SimpleSpan span = new SimpleSpan().start();
		ValueStore valueStore = ValueStore.create();
		valueStore.put(Span.class, span);
		MockQueryExecutionInfo queryExecutionInfo = MockQueryExecutionInfo.builder().throwable(new RuntimeException())
				.valueStore(valueStore).build();

		listener.afterQuery(queryExecutionInfo);

		then(span.throwable).isNotNull();
		then(span.ended).isTrue();
	}

	private ConnectionFactory connectionFactory() {
		return new ConnectionFactory() {
			@Override
			public Publisher<? extends Connection> create() {
				return null;
			}

			@Override
			public ConnectionFactoryMetadata getMetadata() {
				return () -> "my-name";
			}
		};
	}

	private BeanFactory beanFactory() {
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
		beanFactory.addBean("tracer", this.simpleTracer);
		beanFactory.addBean("r2dbcProperties", new R2dbcProperties());
		return beanFactory;
	}

}
