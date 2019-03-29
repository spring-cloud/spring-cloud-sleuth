/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.log;

import brave.Span;
import brave.Tracing;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Marcin Grzejszczak
 */
public class Slf4JSpanLoggerTest {

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
					.addScopeDecorator(StrictScopeDecorator.create())
					.build())
			.spanReporter(this.reporter)
			.build();

	Span span = this.tracing.tracer().nextSpan().name("span").start();
	Slf4jCurrentTraceContext slf4jCurrentTraceContext =
			new Slf4jCurrentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
					.addScopeDecorator(StrictScopeDecorator.create())
					.build());

	@Before
	@After
	public void setup() {
		MDC.clear();
	}

	@Test
	public void should_set_entries_to_mdc_from_span() throws Exception {
		try (Scope scope = this.slf4jCurrentTraceContext.newScope(this.span.context())) {
			assertMDCInfoEqualToSpanInfo(span.context());
		}

		assertMDCInfoNullOrEmpty();
	}

	@Test
	public void should_set_entries_to_mdc_from_two_spans() throws Exception {
		try (Scope scope = this.slf4jCurrentTraceContext.newScope(this.span.context())) {

			assertMDCInfoEqualToSpanInfo(span.context());

			try (Scope scopeInner = this.slf4jCurrentTraceContext.newScope(this.span.context())) {
				assertMDCInfoEqualToSpanInfo(span.context());
			}

			assertMDCInfoEqualToSpanInfo(span.context());
		}

		assertMDCInfoNullOrEmpty();
	}

	@Test
	public void should_set_entries_to_mdc_from_two_spans1() throws Exception {
		try (Scope scope = this.slf4jCurrentTraceContext.newScope(this.span.context())) {

			assertMDCInfoEqualToSpanInfo(span.context());

			try (Scope scopeInner = this.slf4jCurrentTraceContext.newScope(null)) {
				assertMDCInfoNullOrEmpty();
			}

			assertMDCInfoEqualToSpanInfo(span.context());
		}

		assertMDCInfoNullOrEmpty();
	}

	@Test
	public void should_set_entries_to_mdc_from_two_spans2() throws Exception {
		try (Scope scope = this.slf4jCurrentTraceContext.newScope(this.span.context())) {

			assertMDCInfoEqualToSpanInfo(span.context());

			TraceContext nextSpan = this.tracing.tracer().nextSpan().start().context();
			try (Scope scopeInner = this.slf4jCurrentTraceContext.newScope(nextSpan)) {
				assertMDCInfoEqualToSpanInfo(nextSpan);
			}

			assertMDCInfoEqualToSpanInfo(span.context());
		}

		assertMDCInfoNullOrEmpty();
	}

	@Test
	public void should_set_entries_to_mdc_from_two_spans3() throws Exception {
		try (Scope scope = this.slf4jCurrentTraceContext.newScope(null)) {

			assertMDCInfoNullOrEmpty();

			try (Scope scopeInner = this.slf4jCurrentTraceContext.newScope(null)) {
				assertMDCInfoNullOrEmpty();
			}

			assertMDCInfoNullOrEmpty();
		}

		assertMDCInfoNullOrEmpty();
	}

	@Test
	public void should_remove_entries_from_mdc_from_null_span() throws Exception {
		MDC.put("X-B3-TraceId", "A");
		MDC.put("traceId", "A");
		MDC.put("X-B3-SpanId", "A");
		MDC.put("spanId", "A");

		try (Scope scope = this.slf4jCurrentTraceContext.newScope(null)) {
			assertMDCInfoNullOrEmpty();
		}

		assertThat(MDC.get("X-B3-TraceId")).isEqualTo("A");
		assertThat(MDC.get("traceId")).isEqualTo("A");
	}

	private void assertMDCInfoEqualToSpanInfo(TraceContext span){
		assertThat(MDC.get("X-B3-TraceId")).isEqualTo(span.traceIdString());
		assertThat(MDC.get("traceId")).isEqualTo(span.traceIdString());
		assertThat(MDC.get("X-B3-SpanId")).isEqualTo(span.spanIdString());
		assertThat(MDC.get("spanId")).isEqualTo(span.spanIdString());
	}

	private void assertMDCInfoNullOrEmpty(){
		assertThat(MDC.get("X-B3-TraceId")).isNullOrEmpty();
		assertThat(MDC.get("traceId")).isNullOrEmpty();
		assertThat(MDC.get("X-B3-SpanId")).isNullOrEmpty();
		assertThat(MDC.get("spanId")).isNullOrEmpty();
	}
}
