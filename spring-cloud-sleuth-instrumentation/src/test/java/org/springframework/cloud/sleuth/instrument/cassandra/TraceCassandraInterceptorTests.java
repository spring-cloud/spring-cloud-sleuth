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

package org.springframework.cloud.sleuth.instrument.cassandra;

import java.util.Optional;
import java.util.function.BiConsumer;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncCqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.SyncCqlSession;
import org.junit.jupiter.api.Test;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.sleuth.tracer.SimpleSpan;
import org.springframework.cloud.sleuth.tracer.SimpleTracer;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class TraceCassandraInterceptorTests {

	SimpleTracer simpleTracer = new SimpleTracer();

	BeanFactory beanFactory = beanFactory();

	@Test
	void should_register_a_span_for_execute() {
		assertThatTracingWorks(SyncCqlSession::execute);
	}

	@Test
	void should_register_a_span_for_executeAsync() {
		assertThatTracingWorks(AsyncCqlSession::executeAsync);
	}

	@Test
	void should_register_a_span_for_prepare() {
		assertThatTracingWorks(SyncCqlSession::prepare);
	}

	private void assertThatTracingWorks(BiConsumer<CqlSession, SimpleStatement> consumer) {
		CqlSession session = proxied(mock(CqlSession.class));
		SimpleStatement statement = mock(SimpleStatement.class);
		given(statement.getQuery())
				.willReturn("Insert into University.Student(RollNo,Name,dept,Semester) values(2,'Michael','CS', 2);");

		consumer.accept(session, statement);

		SimpleSpan span = this.simpleTracer.getLastSpan();
		then(span).isNotNull();
		then(span.tags).containsKeys(SleuthCassandraSpan.Tags.CQL_TAG.getKey(),
				SleuthCassandraSpan.Tags.KEYSPACE_NAME.getKey());
		then(span.remoteServiceName).isNotBlank();
	}

	private BeanFactory beanFactory() {
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
		beanFactory.addBean("tracer", this.simpleTracer);
		return beanFactory;
	}

	private CqlSession proxied(CqlSession session) {
		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.setTarget(session);
		proxyFactory.addAdvice(new TraceCassandraInterceptor(session, this.beanFactory) {
			@Override
			boolean isContextUnusable() {
				return false;
			}

			@Override
			String getSessionName() {
				return "test";
			}

			@Override
			Optional<CqlIdentifier> getKeyspace() {
				return Optional.empty();
			}
		});
		proxyFactory.addInterface(CqlSession.class);
		return (CqlSession) proxyFactory.getProxy();
	}

}
