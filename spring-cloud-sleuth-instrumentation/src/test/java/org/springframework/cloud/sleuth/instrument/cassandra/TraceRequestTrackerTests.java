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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.metadata.token.Token;
import com.datastax.oss.driver.api.core.session.Request;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.tracer.SimpleSpan;

import static org.assertj.core.api.BDDAssertions.then;

class TraceRequestTrackerTests {

	@Test
	void should_end_span_on_success() {
		TraceRequestTracker traceRequestTracker = new TraceRequestTracker();
		SimpleSpan span = new SimpleSpan();

		traceRequestTracker.onSuccess(new MyRequest(span), 1L, BDDMockito.mock(DriverExecutionProfile.class),
				BDDMockito.mock(Node.class), "");

		then(span.ended).isTrue();
	}

	@Test
	void should_end_span_on_error() {
		TraceRequestTracker traceRequestTracker = new TraceRequestTracker();
		SimpleSpan span = new SimpleSpan();

		traceRequestTracker.onError(new MyRequest(span), new IllegalStateException("Foo"), 1L,
				BDDMockito.mock(DriverExecutionProfile.class), BDDMockito.mock(Node.class), "");

		then(span.ended).isTrue();
		then(span.throwable).isNotNull();
	}

	@Test
	void should_customize_span_on_node_error() {
		TraceRequestTracker traceRequestTracker = new TraceRequestTracker();
		SimpleSpan span = new SimpleSpan();
		Node node = BDDMockito.mock(Node.class);
		BDDMockito.given(node.getEndPoint()).willReturn(endpoint());

		traceRequestTracker.onNodeError(new MyRequest(span), new IllegalStateException("Foo"), 1L,
				BDDMockito.mock(DriverExecutionProfile.class), node, "");

		then(span.events).contains(SleuthCassandraSpan.Events.NODE_ERROR.getValue());
		then(span.tags).containsEntry("cassandra.node[localhost/127.0.0.1:1234].error",
				"java.lang.IllegalStateException: Foo");
	}

	@Test
	void should_customize_span_on_node_success() {
		TraceRequestTracker traceRequestTracker = new TraceRequestTracker();
		SimpleSpan span = new SimpleSpan();
		Node node = BDDMockito.mock(Node.class);
		BDDMockito.given(node.getEndPoint()).willReturn(endpoint());

		traceRequestTracker.onNodeSuccess(new MyRequest(span), 1L, BDDMockito.mock(DriverExecutionProfile.class), node,
				"");

		then(span.events).contains(SleuthCassandraSpan.Events.NODE_SUCCESS.getValue());
	}

	private EndPoint endpoint() {
		return new EndPoint() {
			@NonNull
			@Override
			public SocketAddress resolve() {
				return new InetSocketAddress("localhost", 1234);
			}

			@NonNull
			@Override
			public String asMetricPrefix() {
				return "";
			}

			@Override
			public String toString() {
				return resolve().toString();
			}
		};
	}

}

class MyRequest implements Request, CassandraSpanSupplier {

	private final Span span;

	MyRequest(Span span) {
		this.span = span;
	}

	@Nullable
	@Override
	public String getExecutionProfileName() {
		return null;
	}

	@Nullable
	@Override
	public DriverExecutionProfile getExecutionProfile() {
		return null;
	}

	@Nullable
	@Override
	public CqlIdentifier getKeyspace() {
		return null;
	}

	@Nullable
	@Override
	public CqlIdentifier getRoutingKeyspace() {
		return null;
	}

	@Nullable
	@Override
	public ByteBuffer getRoutingKey() {
		return null;
	}

	@Nullable
	@Override
	public Token getRoutingToken() {
		return null;
	}

	@NonNull
	@Override
	public Map<String, ByteBuffer> getCustomPayload() {
		return null;
	}

	@Nullable
	@Override
	public Boolean isIdempotent() {
		return null;
	}

	@Nullable
	@Override
	public Duration getTimeout() {
		return null;
	}

	@Nullable
	@Override
	public Node getNode() {
		return null;
	}

	@Override
	public Span getSpan() {
		return this.span;
	}

}
