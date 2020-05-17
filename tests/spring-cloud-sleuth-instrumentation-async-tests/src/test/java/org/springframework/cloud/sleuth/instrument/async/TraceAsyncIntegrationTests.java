/*
 * Copyright 2013-2019 the original author or authors.
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

import brave.SpanCustomizer;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import brave.test.IntegrationTestSpanHandler;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(
		classes = { TraceAsyncIntegrationTests.TraceAsyncITestConfiguration.class })
public class TraceAsyncIntegrationTests {

	@ClassRule
	public static IntegrationTestSpanHandler spans = new IntegrationTestSpanHandler();

	TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true)
			.build();

	@Autowired
	AsyncLogic asyncLogic;

	@Autowired
	CurrentTraceContext currentTraceContext;

	@Test
	public void should_set_span_on_an_async_annotated_method() {
		asyncLogic.invokeAsync();

		assertSpan_invokeAsync(takeDesirableSpan());
	}

	@Test
	public void should_set_span_with_custom_method_on_an_async_annotated_method() {
		asyncLogic.invokeAsync_customName();

		assertSpan_invokeAsync_customName(takeDesirableSpan());
	}

	@Test
	public void should_continue_a_span_on_an_async_annotated_method() {
		try (Scope ws = currentTraceContext.maybeScope(context)) {
			asyncLogic.invokeAsync();

			MutableSpan span = assertSpan_invokeAsync(takeDesirableSpan());
			assertThat(span.traceId()).isEqualTo(context.traceIdString());
		}
	}

	@Test
	public void should_continue_a_span_with_custom_method_on_an_async_annotated_method() {
		try (Scope ws = currentTraceContext.maybeScope(context)) {
			asyncLogic.invokeAsync_customName();

			MutableSpan span = assertSpan_invokeAsync_customName(takeDesirableSpan());
			assertThat(span.traceId()).isEqualTo(context.traceIdString());
		}
	}

	static MutableSpan assertSpan_invokeAsync_customName(MutableSpan span) {
		assertThat(span.name()).isEqualTo("foo");
		assertThat(span.containsAnnotation("@Async")).isTrue();
		assertThat(span.tags()).containsEntry("class", "AsyncLogic")
				.containsEntry("method", "invokeAsync_customName");
		return span;
	}

	static MutableSpan assertSpan_invokeAsync(MutableSpan span) {
		assertThat(span.name()).isEqualTo("invoke-async");
		assertThat(span.containsAnnotation("@Async")).isTrue();
		assertThat(span.tags()).containsEntry("class", "AsyncLogic")
				.containsEntry("method", "invokeAsync");
		return span;
	}

	// Sleuth adds spans named "async" with no tags when an executor is used.
	// We don't want that one.
	MutableSpan takeDesirableSpan() {
		MutableSpan span1 = spans.takeLocalSpan();
		MutableSpan span2 = spans.takeLocalSpan();
		return span1.name().equals("async") ? span2 : span1;
	}

	@DefaultTestAutoConfiguration
	@EnableAsync
	@Configuration
	static class TraceAsyncITestConfiguration {

		@Bean
		AsyncLogic asyncLogic(SpanCustomizer customizer) {
			return new AsyncLogic(customizer);
		}

		@Bean
		SpanHandler spanHandler() {
			return spans;
		}

	}

	static class AsyncLogic {
		final SpanCustomizer customizer;

		AsyncLogic(SpanCustomizer customizer) {
			this.customizer = customizer;
		}

		@Async
		public void invokeAsync() {
			customizer.annotate("@Async"); // proves the handler is in scope
		}

		@Async
		@SpanName("foo")
		public void invokeAsync_customName() {
			customizer.annotate("@Async"); // proves the handler is in scope
		}

	}

}
