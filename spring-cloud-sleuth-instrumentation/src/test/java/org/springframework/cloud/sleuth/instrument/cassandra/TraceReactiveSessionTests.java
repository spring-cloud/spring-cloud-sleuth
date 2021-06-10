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

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.addresstranslation.AddressTranslator;
import com.datastax.oss.driver.api.core.auth.AuthProvider;
import com.datastax.oss.driver.api.core.config.DriverConfig;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.connection.ReconnectionPolicy;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.loadbalancing.LoadBalancingPolicy;
import com.datastax.oss.driver.api.core.metadata.NodeStateListener;
import com.datastax.oss.driver.api.core.metadata.schema.SchemaChangeListener;
import com.datastax.oss.driver.api.core.retry.RetryPolicy;
import com.datastax.oss.driver.api.core.session.throttling.RequestThrottler;
import com.datastax.oss.driver.api.core.specex.SpeculativeExecutionPolicy;
import com.datastax.oss.driver.api.core.ssl.SslEngineFactory;
import com.datastax.oss.driver.api.core.time.TimestampGenerator;
import com.datastax.oss.driver.api.core.tracker.RequestTracker;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.sleuth.tracer.SimpleCurrentTraceContext;
import org.springframework.cloud.sleuth.tracer.SimpleSpan;
import org.springframework.cloud.sleuth.tracer.SimpleTracer;
import org.springframework.data.cassandra.ReactiveSession;
import org.springframework.lang.NonNull;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class TraceReactiveSessionTests {

	SimpleTracer simpleTracer = new SimpleTracer();

	SimpleCurrentTraceContext simpleCurrentTraceContext = SimpleCurrentTraceContext.withTracer(this.simpleTracer);

	@Test
	void should_register_a_span_for_execute() {
		ReactiveSession session = mock(ReactiveSession.class);
		given(session.execute(any(Statement.class))).willReturn(Mono.empty());
		SimpleStatement statement = mock(SimpleStatement.class);
		given(statement.getQuery())
				.willReturn("Insert into University.Student(RollNo,Name,dept,Semester) values(2,'Michael','CS', 2);");

		new TraceReactiveSession(session, beanFactory()) {
			@Override
			public DriverContext getContext() {
				return driverContext();
			}
		}.execute(statement).block(Duration.ofMillis(10));

		SimpleSpan span = this.simpleTracer.getLastSpan();
		then(span).isNotNull();
		then(span.tags).containsKeys(SleuthCassandraSpan.Tags.CQL_TAG.getKey(),
				SleuthCassandraSpan.Tags.KEYSPACE_NAME.getKey());
		then(span.remoteServiceName).isNotBlank();
	}

	private BeanFactory beanFactory() {
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
		beanFactory.addBean("tracer", this.simpleTracer);
		beanFactory.addBean("currentTraceContext", this.simpleCurrentTraceContext);
		return beanFactory;
	}

	private DriverContext driverContext() {
		return new DriverContext() {
			@NonNull
			@Override
			public String getSessionName() {
				return "session";
			}

			@NonNull
			@Override
			public DriverConfig getConfig() {
				return null;
			}

			@NonNull
			@Override
			public DriverConfigLoader getConfigLoader() {
				return null;
			}

			@NonNull
			@Override
			public Map<String, LoadBalancingPolicy> getLoadBalancingPolicies() {
				return null;
			}

			@NonNull
			@Override
			public Map<String, RetryPolicy> getRetryPolicies() {
				return null;
			}

			@NonNull
			@Override
			public Map<String, SpeculativeExecutionPolicy> getSpeculativeExecutionPolicies() {
				return null;
			}

			@NonNull
			@Override
			public TimestampGenerator getTimestampGenerator() {
				return null;
			}

			@NonNull
			@Override
			public ReconnectionPolicy getReconnectionPolicy() {
				return null;
			}

			@NonNull
			@Override
			public AddressTranslator getAddressTranslator() {
				return null;
			}

			@NonNull
			@Override
			public Optional<AuthProvider> getAuthProvider() {
				return Optional.empty();
			}

			@NonNull
			@Override
			public Optional<SslEngineFactory> getSslEngineFactory() {
				return Optional.empty();
			}

			@NonNull
			@Override
			public RequestTracker getRequestTracker() {
				return null;
			}

			@NonNull
			@Override
			public RequestThrottler getRequestThrottler() {
				return null;
			}

			@NonNull
			@Override
			public NodeStateListener getNodeStateListener() {
				return null;
			}

			@NonNull
			@Override
			public SchemaChangeListener getSchemaChangeListener() {
				return null;
			}

			@NonNull
			@Override
			public ProtocolVersion getProtocolVersion() {
				return null;
			}

			@NonNull
			@Override
			public CodecRegistry getCodecRegistry() {
				return null;
			}
		};
	}

}
