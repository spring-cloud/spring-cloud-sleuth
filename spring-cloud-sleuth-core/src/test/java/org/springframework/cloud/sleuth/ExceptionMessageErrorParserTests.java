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

package org.springframework.cloud.sleuth;

import java.util.AbstractMap;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class ExceptionMessageErrorParserTests {

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(CurrentTraceContext.Default.create())
			.spanReporter(this.reporter)
			.build();
	Tracer tracer = this.tracing.tracer();

	@Before
	public void setup() {
		this.reporter.clear();
	}

	@Test
	public void should_append_tag_for_exportable_span() throws Exception {
		Throwable e = new RuntimeException("foo");
		Span span = this.tracer.nextSpan();

		new ExceptionMessageErrorParser().parseErrorTags(span, e);

		span.finish();
		then(this.reporter.getSpans()).hasSize(1);
		then(this.reporter.getSpans().get(0).tags()).contains(new AbstractMap.SimpleEntry<>("error", "foo"));
	}

	@Test
	public void should_not_throw_an_exception_when_span_is_null() throws Exception {
		new ExceptionMessageErrorParser().parseErrorTags(null, null);

		then(this.reporter.getSpans()).isEmpty();
	}

	@Test
	public void should_not_append_tag_for_non_exportable_span() throws Exception {
		Span span = this.tracer.nextSpan();

		new ExceptionMessageErrorParser().parseErrorTags(span, null);

		span.finish();
		then(this.reporter.getSpans()).isEmpty();
	}

}