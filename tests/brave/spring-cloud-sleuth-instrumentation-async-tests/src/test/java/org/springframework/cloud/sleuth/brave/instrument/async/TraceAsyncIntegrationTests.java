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

package org.springframework.cloud.sleuth.brave.instrument.async;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracer;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.IntegrationTestSpanHandler;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		classes = { TraceAsyncIntegrationTests.TraceAsyncITestConfiguration.class })
@DirtiesContext // flakey otherwise
public class TraceAsyncIntegrationTests {

	private static final Logger log = LoggerFactory.getLogger(TraceAsyncIntegrationTests.class);

	@ClassRule
	public static IntegrationTestSpanHandler spans = new IntegrationTestSpanHandler();

	TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();

	@Autowired
	AsyncLogic asyncLogic;

	@Autowired
	CurrentTraceContext currentTraceContext;

	@Autowired
	Tracer tracer;

	@Test
	public void should_set_span_on_an_async_annotated_method() {
		Span parent = tracer.joinSpan(context);
		try (Tracer.SpanInScope ws = tracer.withSpanInScope(parent.start())) {
			log.info("HELLO");
			asyncLogic.invokeAsync();

			MutableSpan span = takeDesirableSpan("invoke-async");
			assertThat(span.name()).isEqualTo("invoke-async");
			assertThat(span.containsAnnotation("@Async")).isTrue();
			assertThat(span.tags()).containsEntry("class", "AsyncLogic").containsEntry("method", "invokeAsync");

			// continues the trace
			assertThat(span.traceId()).isEqualTo(context.traceIdString());
		}
		finally {
			parent.finish();
		}

	}

	@Test
	public void should_set_span_with_custom_method_on_an_async_annotated_method() {
		Span parent = tracer.joinSpan(context);
		try (Tracer.SpanInScope ws = tracer.withSpanInScope(parent.start())) {
			log.info("HELLO");
			asyncLogic.invokeAsync_customName();

			MutableSpan span = takeDesirableSpan("foo");
			assertThat(span.name()).isEqualTo("foo");
			assertThat(span.containsAnnotation("@Async")).isTrue();
			assertThat(span.tags()).containsEntry("class", "AsyncLogic").containsEntry("method",
					"invokeAsync_customName");

			// continues the trace
			assertThat(span.traceId()).isEqualTo(context.traceIdString());
		}
		finally {
			parent.finish();
		}
	}

	// Sleuth adds spans named "async" with no tags when an executor is used.
	// We don't want that one.
	MutableSpan takeDesirableSpan(String name) {
		MutableSpan span1 = spans.takeLocalSpan();
		MutableSpan span2 = spans.takeLocalSpan();
		log.info("Two last spans [" + span2 + "] and [" + span1 + "]");
		MutableSpan span = span1 != null && name.equals(span1.name()) ? span1
				: span2 != null && name.equals(span2.name()) ? span2 : null;
		assertThat(span).as("No span with name <> was found", name).isNotNull();
		return span;
	}

	@DefaultTestAutoConfiguration
	@EnableAsync
	@Configuration(proxyBeanMethods = false)
	static class TraceAsyncITestConfiguration {

		@Bean
		AsyncLogic asyncLogic(SpanCustomizer customizer) {
			return new AsyncLogic(customizer);
		}

		@Bean
		SpanHandler testSpanHandler() {
			return spans;
		}

	}

	static class AsyncLogic {

		private static final Logger log = LoggerFactory.getLogger(AsyncLogic.class);

		final SpanCustomizer customizer;

		AsyncLogic(SpanCustomizer customizer) {
			this.customizer = customizer;
		}

		@Async
		public void invokeAsync() {
			customizer.annotate("@Async"); // proves the handler is in scope
			log.info("HELLO ASYNC");
		}

		@Async
		@SpanName("foo")
		public void invokeAsync_customName() {
			customizer.annotate("@Async"); // proves the handler is in scope
			log.info("HELLO ASYNC CUSTOM NAME");
		}

	}

}
