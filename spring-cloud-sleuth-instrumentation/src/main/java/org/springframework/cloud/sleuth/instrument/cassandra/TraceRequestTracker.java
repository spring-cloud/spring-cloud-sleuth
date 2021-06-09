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

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.session.Request;
import com.datastax.oss.driver.api.core.session.Session;
import com.datastax.oss.driver.api.core.tracker.RequestTracker;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import static org.springframework.cloud.sleuth.instrument.cassandra.SleuthCassandraSpan.CASSANDRA_SPAN;
import static org.springframework.cloud.sleuth.instrument.cassandra.SleuthCassandraSpan.Events.NODE_ERROR;
import static org.springframework.cloud.sleuth.instrument.cassandra.SleuthCassandraSpan.Events.NODE_SUCCESS;
import static org.springframework.cloud.sleuth.instrument.cassandra.SleuthCassandraSpan.Tags.NODE_ERROR_TAG;

/**
 * Trace implementation of the {@link RequestTracker}.
 *
 * @author Mark Paluch
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TraceRequestTracker implements RequestTracker {

	private static final Log log = LogFactory.getLog(TraceRequestTracker.class);

	@Override
	public void onSuccess(@NonNull Request request, long latencyNanos, @NonNull DriverExecutionProfile executionProfile,
			@NonNull Node node, @NonNull String requestLogPrefix) {
		if (request instanceof CassandraSpanSupplier) {
			Span span = ((CassandraSpanSupplier) request).getSpan();
			if (log.isDebugEnabled()) {
				log.debug("Closing span [" + span + "]");
			}
			span.end();
		}
	}

	@Override
	public void onError(@NonNull Request request, @NonNull Throwable error, long latencyNanos,
			@NonNull DriverExecutionProfile executionProfile, @Nullable Node node, @NonNull String requestLogPrefix) {
		if (request instanceof CassandraSpanSupplier) {
			Span span = ((CassandraSpanSupplier) request).getSpan();
			span.error(error);
			if (log.isDebugEnabled()) {
				log.debug("Closing span [" + span + "]");
			}
			span.end();
		}
	}

	@Override
	public void onNodeError(@NonNull Request request, @NonNull Throwable error, long latencyNanos,
			@NonNull DriverExecutionProfile executionProfile, @NonNull Node node, @NonNull String requestLogPrefix) {
		if (request instanceof CassandraSpanSupplier) {
			AssertingSpan span = AssertingSpan.of(CASSANDRA_SPAN, ((CassandraSpanSupplier) request).getSpan());
			span.event(NODE_ERROR);
			span.tag(String.format(NODE_ERROR_TAG.getKey(), node.getEndPoint()), error.toString());
			tryAddingRemoteIpAndPort(node, span);
			if (log.isDebugEnabled()) {
				log.debug("Marking node error for [" + span + "]");
			}
		}
	}

	private void tryAddingRemoteIpAndPort(Node node, Span span) {
		try {
			SocketAddress socketAddress = node.getEndPoint().resolve();
			String host;
			int port;
			if (socketAddress instanceof InetSocketAddress) {
				InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
				host = inetSocketAddress.getHostString();
				port = inetSocketAddress.getPort();
			}
			else {
				host = socketAddress.toString();
				port = 0;
			}
			span.remoteIpAndPort(host, port);
		}
		catch (Exception e) {
			log.debug("Exception occurred while trying to set ip and port", e);
		}
	}

	@Override
	public void onNodeSuccess(@NonNull Request request, long latencyNanos,
			@NonNull DriverExecutionProfile executionProfile, @NonNull Node node, @NonNull String requestLogPrefix) {
		if (request instanceof CassandraSpanSupplier) {
			AssertingSpan span = AssertingSpan.of(CASSANDRA_SPAN, ((CassandraSpanSupplier) request).getSpan());
			span.event(NODE_SUCCESS);
			tryAddingRemoteIpAndPort(node, span);
			if (log.isDebugEnabled()) {
				log.debug("Marking node error for [" + span + "]");
			}
		}
	}

	@Override
	public void onSessionReady(@NonNull Session session) {
	}

	@Override
	public void close() throws Exception {

	}

}
