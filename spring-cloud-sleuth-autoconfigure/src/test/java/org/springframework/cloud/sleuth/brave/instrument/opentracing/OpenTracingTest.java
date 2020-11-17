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

package org.springframework.cloud.sleuth.brave.instrument.opentracing;

import java.util.LinkedHashMap;
import java.util.Map;

import brave.Span;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.handler.SpanHandler;
import brave.opentracing.BraveSpan;
import brave.opentracing.BraveSpanContext;
import brave.opentracing.BraveTracer;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import io.opentracing.Scope;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

/**
 * This shows how one might make an OpenTracing adapter for Brave, and how to navigate in
 * and out of the core concepts.
 *
 * Adopted from:
 * https://github.com/openzipkin-contrib/brave-opentracing/tree/master/src/test/java/brave/opentracing
 *
 * @author Marcin Grzejszczak
 */
@SpringBootTest(webEnvironment = NONE,
		properties = { "spring.sleuth.baggage.remote-fields=country-code", "spring.sleuth.tracer.mode=BRAVE" })
public class OpenTracingTest {

	@Autowired
	TestSpanHandler spans;

	@Autowired
	Tracing brave;

	@Autowired
	BraveTracer opentracing;

	@Test
	public void startWithOpenTracingAndFinishWithBrave() {
		BraveSpan openTracingSpan = this.opentracing.buildSpan("encode").withTag("lc", "codec").withStartTimestamp(1L)
				.start();

		Span braveSpan = openTracingSpan.unwrap();

		braveSpan.annotate(2L, "pump fake");
		braveSpan.finish(3L);

		checkSpanReportedToZipkin();
	}

	@Test
	public void extractTraceContext() {
		Map<String, String> map = singletonMap("b3", "0000000000000001-0000000000000002-1");

		BraveSpanContext openTracingContext = this.opentracing.extract(Format.Builtin.HTTP_HEADERS,
				new TextMapAdapter(map));

		assertThat(openTracingContext.unwrap())
				.isEqualTo(TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true).build());
	}

	@Test
	public void extractBaggage() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("b3", "0000000000000001-0000000000000002-1");
		map.put("country-code", "FO");

		BraveSpanContext openTracingContext = this.opentracing.extract(Format.Builtin.HTTP_HEADERS,
				new TextMapAdapter(map));

		assertThat(openTracingContext.baggageItems()).containsExactly(entry("country-code", "FO"));
	}

	@Test
	public void extractTraceContextTextMap() {
		Map<String, String> map = singletonMap("b3", "0000000000000001-0000000000000002-1");

		BraveSpanContext openTracingContext = this.opentracing.extract(Format.Builtin.TEXT_MAP,
				new TextMapAdapter(map));

		assertThat(openTracingContext.unwrap())
				.isEqualTo(TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true).build());
	}

	@Test
	public void extractTraceContextCaseInsensitive() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("B3", "0000000000000001-0000000000000002-1");
		map.put("other", "1");

		BraveSpanContext openTracingContext = this.opentracing.extract(Format.Builtin.HTTP_HEADERS,
				new TextMapAdapter(map));

		assertThat(openTracingContext.unwrap())
				.isEqualTo(TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true).build());
	}

	@Test
	public void injectTraceContext_baggage() {
		BraveSpan span = this.opentracing.buildSpan("foo").start();
		span.setBaggageItem("country-code", "FO");

		Map<String, String> map = new LinkedHashMap<>();
		TextMapAdapter carrier = new TextMapAdapter(map);
		this.opentracing.inject(span.context(), Format.Builtin.HTTP_HEADERS, carrier);

		assertThat(map).containsEntry("country-code", "FO");
	}

	void checkSpanReportedToZipkin() {
		assertThat(this.spans).first().satisfies(s -> {
			assertThat(s.name()).isEqualTo("encode");
			assertThat(s.startTimestamp()).isEqualTo(1L);
			assertThat(s.annotations()).containsExactly(entry(2L, "pump fake"));
			assertThat(s.tags()).containsExactly(entry("lc", "codec"));
			assertThat(s.finishTimestamp()).isEqualTo(3L);
		});
	}

	@Test
	public void activate_nested() {
		BraveSpan spanA = this.opentracing.buildSpan("spanA").start();
		BraveSpan spanB = this.opentracing.buildSpan("spanB").start();

		try (Scope scopeA = this.opentracing.scopeManager().activate(spanA)) {
			try (Scope scopeB = this.opentracing.scopeManager().activate(spanB)) {
				assertThat(this.opentracing.scopeManager().activeSpan().context().unwrap())
						.isEqualTo(spanB.context().unwrap());
			}

			assertThat(opentracing.scopeManager().activeSpan().context().unwrap()).isEqualTo(spanA.context().unwrap());
		}
	}

	@Test
	public void subsequentChildrenNestProperly_BraveStyle() {
		// this test is semantically identical to subsequentChildrenNestProperly_OTStyle,
		// but uses
		// the Brave API instead of the OpenTracing API.

		Long shouldBeIdOfSpanA;
		Long idOfSpanB;
		Long shouldBeIdOfSpanB;
		Long parentIdOfSpanB;
		Long parentIdOfSpanC;

		Span spanA = this.brave.tracer().newTrace().name("spanA").start();
		Long idOfSpanA = spanA.context().spanId();
		try (SpanInScope scopeA = this.brave.tracer().withSpanInScope(spanA)) {

			Span spanB = this.brave.tracer().newChild(spanA.context()).name("spanB").start();
			idOfSpanB = spanB.context().spanId();
			parentIdOfSpanB = spanB.context().parentId();
			try (SpanInScope scopeB = this.brave.tracer().withSpanInScope(spanB)) {
				shouldBeIdOfSpanB = this.brave.currentTraceContext().get().spanId();
			}
			finally {
				spanB.finish();
			}

			shouldBeIdOfSpanA = this.brave.currentTraceContext().get().spanId();

			Span spanC = this.brave.tracer().newChild(spanA.context()).name("spanC").start();
			parentIdOfSpanC = spanC.context().parentId();
			try (SpanInScope scopeC = this.brave.tracer().withSpanInScope(spanC)) {
				// nothing to do here
			}
			finally {
				spanC.finish();
			}
		}
		finally {
			spanA.finish();
		}

		assertThat(shouldBeIdOfSpanA).as("SpanA should have been active again after closing B").isEqualTo(idOfSpanA);
		assertThat(shouldBeIdOfSpanB).as("SpanB should have been active prior to its closure").isEqualTo(idOfSpanB);
		assertThat(parentIdOfSpanB).as("SpanB's parent should be SpanA").isEqualTo(idOfSpanA);
		assertThat(parentIdOfSpanC).as("SpanC's parent should be SpanA").isEqualTo(idOfSpanA);
	}

	@Test
	public void implicitParentFromSpanManager_start() {
		BraveSpan spanA = this.opentracing.buildSpan("spanA").start();
		try (Scope scopeA = this.opentracing.activateSpan(spanA)) {
			BraveSpan spanB = this.opentracing.buildSpan("spanB").start();
			// OpenTracing doesn't expose parent ID, so we will check trace ID instead
			assertThat(spanB.context().toTraceId()).isEqualTo(spanA.context().toTraceId());
		}
	}

	@Test
	public void implicitParentFromSpanManager_start_ignoreActiveSpan() {
		BraveSpan spanA = this.opentracing.buildSpan("spanA").start();
		try (Scope scopeA = this.opentracing.activateSpan(spanA)) {
			BraveSpan spanB = this.opentracing.buildSpan("spanB").ignoreActiveSpan().start();
			assertThat(spanB.unwrap().context().parentId()).isNull(); // new trace
		}
	}

	@Test
	public void ignoresErrorFalseTag_beforeStart() {
		this.opentracing.buildSpan("encode").withTag("error", false).start().finish();

		assertThat(this.spans.get(0).tags()).isEmpty();
	}

	@Test
	public void ignoresErrorFalseTag_afterStart() {
		this.opentracing.buildSpan("encode").start().setTag("error", false).finish();

		assertThat(this.spans.get(0).tags()).isEmpty();
	}

	@BeforeEach
	public void clear() {
		this.spans.clear();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class Config {

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

	}

}
