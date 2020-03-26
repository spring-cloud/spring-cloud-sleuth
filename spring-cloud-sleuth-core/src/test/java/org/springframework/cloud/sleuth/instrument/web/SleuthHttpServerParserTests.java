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

package org.springframework.cloud.sleuth.instrument.web;

import brave.Span;
import brave.Tracing;
import brave.http.HttpResponse;
import brave.sampler.Sampler;
import org.assertj.core.api.BDDAssertions;
import org.junit.Test;

import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class SleuthHttpServerParserTests {

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();

	@Test
	public void should_tag_span_with_error_when_response_has_error_and_status_is_ok() {
		HttpResponse response = errorResponseWithOkStatus();

		whenSpanGetsParsed(response, newSpan());

		thenReportedSpanContainsErrorTag();
	}

	@Test
	public void should_not_tag_span_with_error_when_response_has_no_error_and_status_is_ok() {
		HttpResponse response = responseWithOkStatus();

		whenSpanGetsParsed(response, newSpan());

		thenReportedSpanDoesNotContainErrorTag();
	}

	private void whenSpanGetsParsed(HttpResponse response, Span span) {
		try {
			new SleuthHttpServerParser(null).parse(response, null, span);
		}
		finally {
			span.finish();
		}
	}

	private void thenReportedSpanContainsErrorTag() {
		BDDAssertions
				.then(this.reporter.getSpans().stream()
						.flatMap(s -> s.tags().keySet().stream()))
				.contains("http.status_code");
	}

	private void thenReportedSpanDoesNotContainErrorTag() {
		BDDAssertions
				.then(this.reporter.getSpans().stream()
						.flatMap(s -> s.tags().keySet().stream()))
				.doesNotContain("http.status_code");
	}

	private HttpResponse errorResponseWithOkStatus() {
		HttpResponse response = mock(HttpResponse.class);
		given(response.statusCode()).willReturn(200);
		given(response.error()).willReturn(new RuntimeException("hello"));
		return response;
	}

	private HttpResponse responseWithOkStatus() {
		HttpResponse response = mock(HttpResponse.class);
		given(response.statusCode()).willReturn(200);
		return response;
	}

	private Span newSpan() {
		return Tracing.newBuilder().spanReporter(this.reporter)
				.sampler(Sampler.ALWAYS_SAMPLE).build().tracer().nextSpan().name("span")
				.start();
	}

}
