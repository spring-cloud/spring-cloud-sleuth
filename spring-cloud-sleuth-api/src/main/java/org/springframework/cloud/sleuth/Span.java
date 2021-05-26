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

package org.springframework.cloud.sleuth;

import org.springframework.cloud.sleuth.propagation.Propagator;

/**
 *
 * This API was heavily influenced by Brave. Parts of its documentation were taken
 * directly from Brave.
 *
 * Span is a single unit of work that needs to be started and stopped. Contains timing
 * information and events and tags.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface Span extends SpanCustomizer {

	/**
	 * @return {@code true} when no recording is done and nothing is reported to an
	 * external system. However, this span should still be injected into outgoing
	 * requests. Use this flag to avoid performing expensive computation.
	 */
	boolean isNoop();

	/**
	 * @return {@link TraceContext} corresponding to this span.
	 */
	TraceContext context();

	/**
	 * Starts this span.
	 * @return this span
	 */
	Span start();

	/**
	 * Sets a name on this span.
	 * @param name name to set on the span
	 * @return this span
	 */
	Span name(String name);

	/**
	 * Sets an event on this span.
	 * @param value event name to set on the span
	 * @return this span
	 */
	Span event(String value);

	/**
	 * Sets a tag on this span.
	 * @param key tag key
	 * @param value tag value
	 * @return this span
	 */
	Span tag(String key, String value);

	/**
	 * Records an exception for this span.
	 * @param throwable to record
	 * @return this span
	 */
	Span error(Throwable throwable);

	/**
	 * Ends the span. The span gets stopped and recorded if not noop.
	 */
	void end();

	/**
	 * Ends the span. The span gets stopped but does not get recorded.
	 */
	void abandon();

	/**
	 * Sets the remote service name for the span.
	 * @param remoteServiceName remote service name
	 * @return this span
	 * @since 3.0.3
	 */
	default Span remoteServiceName(String remoteServiceName) {
		return this;
	}

	/**
	 * Sets the remote url on the span.
	 * @param ip remote ip
	 * @param port remote port
	 * @return this span
	 * @since 3.1.0
	 */
	default Span remoteIpAndPort(String ip, int port) {
		return this;
	}

	/**
	 * Type of span. Can be used to specify additional relationships between spans in
	 * addition to a parent/child relationship.
	 *
	 * Documentation of the enum taken from OpenTelemetry.
	 */
	enum Kind {

		/**
		 * Indicates that the span covers server-side handling of an RPC or other remote
		 * request.
		 */
		SERVER,

		/**
		 * Indicates that the span covers the client-side wrapper around an RPC or other
		 * remote request.
		 */
		CLIENT,

		/**
		 * Indicates that the span describes producer sending a message to a broker.
		 * Unlike client and server, there is no direct critical path latency relationship
		 * between producer and consumer spans.
		 */
		PRODUCER,

		/**
		 * Indicates that the span describes consumer receiving a message from a broker.
		 * Unlike client and server, there is no direct critical path latency relationship
		 * between producer and consumer spans.
		 */
		CONSUMER

	}

	/**
	 * In some cases (e.g. when dealing with
	 * {@link Propagator#extract(Object, Propagator.Getter)}'s we want to create a span
	 * that has not yet been started, yet it's heavily configurable (some options are not
	 * possible to be set when a span has already been started). We can achieve that by
	 * using a builder.
	 *
	 * Inspired by OpenZipkin Brave and OpenTelemetry API.
	 */
	interface Builder {

		/**
		 * Sets the parent of the built span.
		 * @param context parent's context
		 * @return this
		 */
		Builder setParent(TraceContext context);

		/**
		 * Sets no parent of the built span.
		 * @return this
		 */
		Builder setNoParent();

		/**
		 * Sets the name of the span.
		 * @param name span name
		 * @return this
		 */
		Builder name(String name);

		/**
		 * Sets an event on the span.
		 * @param value event value
		 * @return this
		 */
		Builder event(String value);

		/**
		 * Sets a tag on the span.
		 * @param key tag key
		 * @param value tag value
		 * @return this
		 */
		Builder tag(String key, String value);

		/**
		 * Sets an error on the span.
		 * @param throwable error to set
		 * @return this
		 */
		Builder error(Throwable throwable);

		/**
		 * Sets the kind on the span.
		 * @param spanKind kind of the span
		 * @return this
		 */
		Builder kind(Span.Kind spanKind);

		/**
		 * Sets the remote service name for the span.
		 * @param remoteServiceName remote service name
		 * @return this
		 */
		Builder remoteServiceName(String remoteServiceName);

		/**
		 * Sets the remote URL for the span.
		 * @param ip remote service ip
		 * @param port remote service port
		 * @return this
		 */
		default Builder remoteIpAndPort(String ip, int port) {
			return this;
		}

		/**
		 * Builds and starts the span.
		 * @return started span
		 */
		Span start();

	}

}
