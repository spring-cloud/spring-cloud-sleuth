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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.TraceFlags;
import io.opentelemetry.trace.TraceId;
import io.opentelemetry.trace.TraceState;
import io.opentelemetry.trace.Tracer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		classes = { OtelTraceAsyncIntegrationTests.TraceAsyncITestConfiguration.class })
@DirtiesContext // flakey otherwise
public class OtelTraceAsyncIntegrationTests {

	private static final Logger log = LoggerFactory.getLogger(OtelTraceAsyncIntegrationTests.class);

	SpanContext parent = SpanContext.create(new TraceId(1L, 0L), new SpanId(2L),
			TraceFlags.builder().setIsSampled(true).build(), TraceState.builder().build());

	@Autowired
	AsyncLogic asyncLogic;

	// TODO: [OTEL] Maybe scope doesn't work for some reason. A span is allocated by Brave
	// even though I don't do it explicitly
	@Autowired
	Tracer tracer;

	@Autowired
	ArrayListSpanProcessor spans;

	@Test
	public void should_set_span_on_an_async_annotated_method() {
		Span parent = tracer.spanBuilder("child").setParent(this.parent).startSpan();
		try (Scope ws = tracer.withSpan(parent)) {
			log.info("HELLO");
			asyncLogic.invokeAsync();

			SpanData span = takeDesirableSpan("invoke-async");
			assertThat(span.getName()).isEqualTo("invoke-async");
			assertThat(span.getEvents()).extracting("name").containsOnly("@Async");
			assertThat(span.getAttributes().get("class").getStringValue()).isEqualTo("AsyncLogic");
			assertThat(span.getAttributes().get("method").getStringValue()).isEqualTo("invokeAsync");

			// continues the trace
			assertThat(span.getTraceId().toLowerBase16()).isEqualTo(this.parent.getTraceId().toLowerBase16());
		}
		finally {
			parent.end();
		}

	}

	@Test
	public void should_set_span_with_custom_method_on_an_async_annotated_method() {
		Span parent = tracer.spanBuilder("child").setParent(this.parent).startSpan();
		try (Scope ws = tracer.withSpan(parent)) {
			log.info("HELLO");
			asyncLogic.invokeAsync_customName();

			SpanData span = takeDesirableSpan("foo");
			assertThat(span.getName()).isEqualTo("foo");
			assertThat(span.getEvents()).extracting("name").containsOnly("@Async");
			assertThat(span.getAttributes().get("class").getStringValue()).isEqualTo("AsyncLogic");
			assertThat(span.getAttributes().get("method").getStringValue()).isEqualTo("invokeAsync_customName");

			// continues the trace
			assertThat(span.getTraceId().toLowerBase16()).isEqualTo(this.parent.getTraceId().toLowerBase16());
		}
		finally {
			parent.end();
		}
	}

	// Sleuth adds spans named "async" with no tags when an executor is used.
	// We don't want that one.
	SpanData takeDesirableSpan(String name) {
		AtomicReference<SpanData> span = new AtomicReference<>();
		Awaitility.await().untilAsserted(() -> {
			SpanData span1 = spans.takeLocalSpan();
			SpanData span2 = spans.takeLocalSpan();
			log.info("Two last spans [" + span2 + "] and [" + span1 + "]");
			span.set(span1 != null && name.equals(span1.getName()) ? span1
					: span2 != null && name.equals(span2.getName()) ? span2 : null);
			assertThat(span.get()).as("No span with name <> was found", name).isNotNull();
		});
		return span.get();
	}

	@EnableAsync
	@Configuration
	@EnableAutoConfiguration
	public static class TraceAsyncITestConfiguration {

		@Bean
		AsyncLogic asyncLogic(Tracer tracer) {
			return new AsyncLogic(tracer);
		}

		@Bean
		ArrayListSpanProcessor testSpanHandler() {
			return new ArrayListSpanProcessor();
		}

	}

	public static class AsyncLogic {

		private static final Logger log = LoggerFactory.getLogger(AsyncLogic.class);

		final Tracer tracer;

		AsyncLogic(Tracer tracer) {
			this.tracer = tracer;
		}

		@Async
		public void invokeAsync() {
			tracer.getCurrentSpan().addEvent("@Async"); // proves the handler is in scope
			log.info("HELLO ASYNC");
		}

		@Async
		@SpanName("foo")
		public void invokeAsync_customName() {
			tracer.getCurrentSpan().addEvent("@Async"); // proves the handler is in scope
			log.info("HELLO ASYNC CUSTOM NAME");
		}

	}

}

class ArrayListSpanProcessor implements SpanProcessor, SpanExporter {

	Queue<SpanData> spans = new LinkedBlockingQueue<>();

	@Override
	public void onStart(ReadWriteSpan span) {

	}

	@Override
	public boolean isStartRequired() {
		return false;
	}

	@Override
	public void onEnd(ReadableSpan span) {
		this.spans.add(span.toSpanData());
	}

	@Override
	public boolean isEndRequired() {
		return true;
	}

	@Override
	public CompletableResultCode export(Collection<SpanData> spans) {
		this.spans.addAll(spans);
		return CompletableResultCode.ofSuccess();
	}

	@Override
	public CompletableResultCode flush() {
		return CompletableResultCode.ofSuccess();
	}

	@Override
	public CompletableResultCode shutdown() {
		return CompletableResultCode.ofSuccess();
	}

	@Override
	public CompletableResultCode forceFlush() {
		return CompletableResultCode.ofSuccess();
	}

	SpanData takeLocalSpan() {
		return this.spans.poll();
	}

}
