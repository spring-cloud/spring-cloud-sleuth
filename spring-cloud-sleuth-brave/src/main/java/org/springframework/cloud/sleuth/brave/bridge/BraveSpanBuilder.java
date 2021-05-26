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

package org.springframework.cloud.sleuth.brave.bridge;

import brave.Tracer;
import brave.propagation.TraceContextOrSamplingFlags;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;

/**
 * Brave implementation of a {@link Span.Builder}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
class BraveSpanBuilder implements Span.Builder {

	brave.Span delegate;

	TraceContextOrSamplingFlags parentContext;

	private final Tracer tracer;

	private long startTimestamp;

	BraveSpanBuilder(Tracer tracer) {
		this.tracer = tracer;
	}

	BraveSpanBuilder(Tracer tracer, TraceContextOrSamplingFlags parentContext) {
		this.tracer = tracer;
		this.parentContext = parentContext;
	}

	brave.Span span() {
		if (this.delegate != null) {
			return this.delegate;
		}
		else if (this.parentContext != null) {
			this.delegate = this.tracer.nextSpan(this.parentContext);
		}
		else {
			this.delegate = this.tracer.nextSpan();
		}
		return this.delegate;
	}

	@Override
	public Span.Builder setParent(TraceContext context) {
		this.parentContext = TraceContextOrSamplingFlags.create(BraveTraceContext.toBrave(context));
		return this;
	}

	@Override
	public Span.Builder setNoParent() {
		return this;
	}

	@Override
	public Span.Builder name(String name) {
		span().name(name);
		return this;
	}

	@Override
	public Span.Builder event(String value) {
		span().annotate(value);
		return this;
	}

	@Override
	public Span.Builder tag(String key, String value) {
		span().tag(key, value);
		return this;
	}

	@Override
	public Span.Builder error(Throwable throwable) {
		span().error(throwable);
		return this;
	}

	@Override
	public Span.Builder kind(Span.Kind kind) {
		span().kind(kind != null ? brave.Span.Kind.valueOf(kind.toString()) : null);
		return this;
	}

	@Override
	public Span.Builder remoteServiceName(String remoteServiceName) {
		span().remoteServiceName(remoteServiceName);
		return this;
	}

	@Override
	public Span.Builder remoteIpAndPort(String ip, int port) {
		span().remoteIpAndPort(ip, port);
		return this;
	}

	@Override
	public Span start() {
		if (this.startTimestamp > 0) {
			span().start(this.startTimestamp);
		}
		else {
			span().start();
		}
		return BraveSpan.fromBrave(this.delegate);
	}

	static Span.Builder toBuilder(Tracer tracer, TraceContextOrSamplingFlags context) {
		return new BraveSpanBuilder(tracer, context);
	}

}
