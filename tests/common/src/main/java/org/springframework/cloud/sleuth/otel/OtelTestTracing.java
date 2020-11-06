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
import java.util.regex.Pattern;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.DefaultContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.jetbrains.annotations.NotNull;

import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.SamplerFunction;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.api.http.HttpRequestParser;
import org.springframework.cloud.sleuth.api.http.HttpServerHandler;
import org.springframework.cloud.sleuth.api.propagation.Propagator;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.otel.bridge.OtelBaggageManager;
import org.springframework.cloud.sleuth.otel.bridge.OtelCurrentTraceContext;
import org.springframework.cloud.sleuth.otel.bridge.OtelPropagator;
import org.springframework.cloud.sleuth.otel.bridge.OtelTracer;
import org.springframework.cloud.sleuth.otel.bridge.http.OtelHttpClientHandler;
import org.springframework.cloud.sleuth.otel.bridge.http.OtelHttpServerHandler;
import org.springframework.cloud.sleuth.otel.exporter.ArrayListSpanProcessor;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.cloud.sleuth.test.TestTracingAssertions;
import org.springframework.cloud.sleuth.test.TestTracingAware;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;
import org.springframework.cloud.sleuth.test.TracerAware;
import org.springframework.context.ApplicationEventPublisher;

public class OtelTestTracing implements TracerAware, TestTracingAware, TestTracingAwareSupplier, Closeable {

	ArrayListSpanProcessor spanProcessor = new ArrayListSpanProcessor();

	ContextPropagators defaultContextPropagators = OpenTelemetry.getGlobalPropagators();

	ContextPropagators contextPropagators = contextPropagators();

	Sampler sampler = Sampler.alwaysOn();

	HttpRequestParser clientRequestParser;

	io.opentelemetry.api.trace.Tracer tracer = otelTracer();

	OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext(publisher());

	OtelBaggageManager otelBaggageManager = new OtelBaggageManager(this.currentTraceContext,
			new SleuthBaggageProperties(), publisher());

	io.opentelemetry.api.trace.Tracer otelTracer() {
		TracerSdkProvider provider = TracerSdkProvider.builder().build();
		provider.addSpanProcessor(this.spanProcessor);
		OpenTelemetry.setGlobalPropagators(this.contextPropagators);
		provider.updateActiveTraceConfig(TraceConfig.getDefault().toBuilder().setSampler(this.sampler).build());
		return provider.get("org.springframework.cloud.sleuth");
	}

	@NotNull
	protected ContextPropagators contextPropagators() {
		return DefaultContextPropagators.builder()
				.addTextMapPropagator(B3Propagator.builder().injectMultipleHeaders().build()).build();
	}

	private void reset() {
		this.contextPropagators = contextPropagators();
		this.tracer = otelTracer();
		this.currentTraceContext = new OtelCurrentTraceContext(publisher());
	}

	@Override
	public TracerAware sampler(TraceSampler sampler) {
		this.sampler = sampler == TraceSampler.ON ? Sampler.alwaysOn() : Sampler.alwaysOff();
		return this;
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
		OpenTelemetry.setGlobalPropagators(this.defaultContextPropagators);
		this.sampler = Sampler.alwaysOn();
	}

	@Override
	public TestTracingAware tracerTest() {
		return this;
	}

	@Override
	public Tracer tracer() {
		reset();
		return OtelTracer.fromOtel(this.tracer, this.otelBaggageManager);
	}

	@Override
	public CurrentTraceContext currentTraceContext() {
		reset();
		return new OtelCurrentTraceContext(publisher());
	}

	@Override
	public Propagator propagator() {
		reset();
		return new OtelPropagator(this.contextPropagators, this.tracer);
	}

	@Override
	public HttpServerHandler httpServerHandler() {
		reset();
		return new OtelHttpServerHandler(this.tracer, null, null, () -> Pattern.compile(""));
	}

	@Override
	public TracerAware clientRequestParser(HttpRequestParser httpRequestParser) {
		this.clientRequestParser = httpRequestParser;
		return this;
	}

	@Override
	public HttpClientHandler httpClientHandler() {
		reset();
		return new OtelHttpClientHandler(this.tracer, this.clientRequestParser, null, SamplerFunction.alwaysSample());
	}

	ApplicationEventPublisher publisher() {
		return event -> {

		};
	}

}
