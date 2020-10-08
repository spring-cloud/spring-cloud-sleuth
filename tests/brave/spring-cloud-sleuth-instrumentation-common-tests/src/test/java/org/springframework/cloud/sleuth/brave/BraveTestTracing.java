/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.brave;

import java.io.Closeable;

import brave.Tracing;
import brave.handler.SpanHandler;
import brave.http.HttpTracing;
import brave.propagation.B3Propagation;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;

import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.api.http.HttpServerHandler;
import org.springframework.cloud.sleuth.api.propagation.Propagator;
import org.springframework.cloud.sleuth.brave.bridge.BraveCurrentTraceContext;
import org.springframework.cloud.sleuth.brave.bridge.BravePropagator;
import org.springframework.cloud.sleuth.brave.bridge.BraveTracer;
import org.springframework.cloud.sleuth.brave.bridge.http.BraveHttpClientHandler;
import org.springframework.cloud.sleuth.brave.bridge.http.BraveHttpServerHandler;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.cloud.sleuth.test.TestTracingAssertions;
import org.springframework.cloud.sleuth.test.TestTracingAware;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;
import org.springframework.cloud.sleuth.test.TracerAware;

public class BraveTestTracing implements TracerAware, TestTracingAware, TestTracingAwareSupplier, Closeable {

	brave.test.TestSpanHandler spans = new brave.test.TestSpanHandler();

	ThreadLocalCurrentTraceContext context = ThreadLocalCurrentTraceContext.newBuilder()
			.addScopeDecorator(StrictScopeDecorator.create()).build();

	Tracing tracing = tracingBuilder().build();

	Tracing.Builder tracingBuilder() {
		return Tracing.newBuilder().currentTraceContext(context).sampler(Sampler.ALWAYS_SAMPLE)
				.addSpanHandler(spanHandler());
	}

	brave.Tracer tracer = this.tracing.tracer();

	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing).build();

	@Override
	public Tracer tracer() {
		return BraveTracer.fromBrave(this.tracer);
	}

	SpanHandler spanHandler() {
		return this.spans;
	}

	@Override
	public CurrentTraceContext currentTraceContext() {
		return BraveCurrentTraceContext.fromBrave(this.context);
	}

	@Override
	public Propagator propagator() {
		return new BravePropagator(B3Propagation.get(), this.tracer);
	}

	@Override
	public HttpServerHandler httpServerHandler() {
		return new BraveHttpServerHandler(brave.http.HttpServerHandler.create(this.httpTracing));
	}

	@Override
	public HttpClientHandler httpClientHandler() {
		return new BraveHttpClientHandler(brave.http.HttpClientHandler.create(this.httpTracing));
	}

	@Override
	public TracerAware tracing() {
		return this;
	}

	@Override
	public TestSpanHandler handler() {
		return new BraveTestSpanHandler(this.spans);
	}

	@Override
	public TestTracingAssertions assertions() {
		return new BraveTestTracingAssertions();
	}

	@Override
	public TestTracingAware tracerTest() {
		return this;
	}

	@Override
	public void close() {
		this.spans.clear();
		this.context.clear();
		handler().clear();
	}

}
