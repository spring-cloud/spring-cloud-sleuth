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

package org.springframework.cloud.sleuth.otel;

import java.io.Closeable;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.DefaultContextPropagators;
import io.opentelemetry.extensions.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.trace.TracerSdkProvider;

import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.api.http.HttpServerHandler;
import org.springframework.cloud.sleuth.api.propagation.Propagator;
import org.springframework.cloud.sleuth.otel.bridge.OtelCurrentTraceContext;
import org.springframework.cloud.sleuth.otel.bridge.OtelPropagator;
import org.springframework.cloud.sleuth.otel.bridge.OtelTracer;
import org.springframework.cloud.sleuth.otel.bridge.http.OtelHttpClientHandler;
import org.springframework.cloud.sleuth.otel.bridge.http.OtelHttpServerHandler;
import org.springframework.cloud.sleuth.otel.instrument.OtelTestTracingAssertions;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.cloud.sleuth.test.TestTracingAssertions;
import org.springframework.cloud.sleuth.test.TestTracingAware;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;
import org.springframework.cloud.sleuth.test.TracerAware;

public class OtelTestTracing implements TracerAware, TestTracingAware, TestTracingAwareSupplier, Closeable {

	ArrayListSpanProcessor spanProcessor = new ArrayListSpanProcessor();

	ContextPropagators defaultContextPropagators = OpenTelemetry.getPropagators();

	ContextPropagators contextPropagators = DefaultContextPropagators.builder()
			.addTextMapPropagator(B3Propagator.getMultipleHeaderPropagator())
			.addTextMapPropagator(B3Propagator.getSingleHeaderPropagator()).build();

	io.opentelemetry.trace.Tracer tracer = otelTracer();

	io.opentelemetry.trace.Tracer otelTracer() {
		TracerSdkProvider provider = TracerSdkProvider.builder().build();
		provider.addSpanProcessor(this.spanProcessor);
		OpenTelemetry.setPropagators(this.contextPropagators);
		return provider.get("org.springframework.cloud.sleuth");
	}

	@Override
	public TracerAware tracing() {
		return this;
	}

	@Override
	public TestSpanHandler handler() {
		return new OtelTestSpanHandler(this.spanProcessor);
	}

	@Override
	public TestTracingAssertions assertions() {
		return new OtelTestTracingAssertions();
	}

	@Override
	public void close() {
		this.spanProcessor.clear();
		OpenTelemetry.setPropagators(this.defaultContextPropagators);
	}

	@Override
	public TestTracingAware tracerTest() {
		return this;
	}

	@Override
	public Tracer tracer() {
		return OtelTracer.fromOtel(this.tracer);
	}

	@Override
	public CurrentTraceContext currentTraceContext() {
		return new OtelCurrentTraceContext(this.tracer);
	}

	@Override
	public Propagator propagator() {
		return new OtelPropagator(this.contextPropagators, tracer);
	}

	@Override
	public HttpServerHandler httpServerHandler() {
		return new OtelHttpServerHandler(this.tracer);
	}

	@Override
	public HttpClientHandler httpClientHandler() {
		return new OtelHttpClientHandler(this.tracer);
	}

}
