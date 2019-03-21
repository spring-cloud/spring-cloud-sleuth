/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.zuul;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

import okhttp3.Request;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class OkHttpClientRibbonRequestCustomizerTests {

	@Mock Tracer tracer;
	@InjectMocks OkHttpClientRibbonRequestCustomizer customizer;
	Span span = Span.builder().name("name").spanId(1L).traceId(2L).parent(3L)
			.processId("processId").build();

	@Test
	public void should_accept_customizer_when_apache_http_client_is_passed() throws Exception {
		then(this.customizer.accepts(String.class)).isFalse();
		then(this.customizer.accepts(Request.Builder.class)).isTrue();
	}

	@Test
	public void should_set_not_sampled_on_the_context_when_there_is_no_span() throws Exception {
		Request.Builder requestBuilder = requestBuilder();

		this.customizer.inject(null, this.customizer.toSpanTextMap(requestBuilder));

		Request request = requestBuilder.build();
		then(request.header(Span.SAMPLED_NAME)).isEqualTo(Span.SPAN_NOT_SAMPLED);
	}

	@Test
	public void should_set_tracing_headers_on_the_context_when_there_is_a_span() throws Exception {
		Request.Builder requestBuilder = requestBuilder();

		this.customizer.inject(this.span, this.customizer.toSpanTextMap(requestBuilder));

		Request request = requestBuilder.build();
		thenThereIsAHeaderWithNameAndValue(request, Span.SPAN_ID_NAME, "0000000000000001");
		thenThereIsAHeaderWithNameAndValue(request, Span.TRACE_ID_NAME, "0000000000000002");
		thenThereIsAHeaderWithNameAndValue(request, Span.PARENT_ID_NAME, "0000000000000003");
		thenThereIsAHeaderWithNameAndValue(request, Span.PROCESS_ID_NAME, "processId");
	}

	@Test
	public void should_not_set_duplicate_tracing_headers_on_the_context_when_there_is_a_span() throws Exception {
		Request.Builder requestBuilder = requestBuilder();

		this.customizer.inject(this.span, this.customizer.toSpanTextMap(requestBuilder));
		this.customizer.inject(this.span, this.customizer.toSpanTextMap(requestBuilder));

		Request request = requestBuilder.build();
		thenThereIsAHeaderWithNameAndValue(request, Span.SPAN_ID_NAME, "0000000000000001");
		thenThereIsAHeaderWithNameAndValue(request, Span.TRACE_ID_NAME, "0000000000000002");
		thenThereIsAHeaderWithNameAndValue(request, Span.PARENT_ID_NAME, "0000000000000003");
		thenThereIsAHeaderWithNameAndValue(request, Span.PROCESS_ID_NAME, "processId");
	}

	private void thenThereIsAHeaderWithNameAndValue(Request request, String name, String value) {
		then(request.headers(name)).hasSize(1);
		then(request.header(name)).isEqualTo(value);
	}

	private Request.Builder requestBuilder() {
		return new Request.Builder().get().url("http://localhost:8080/");
	}
}