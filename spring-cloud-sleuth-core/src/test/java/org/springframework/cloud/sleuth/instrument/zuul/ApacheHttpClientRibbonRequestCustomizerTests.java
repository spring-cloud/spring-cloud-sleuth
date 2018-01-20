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

package org.springframework.cloud.sleuth.instrument.zuul;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.sampler.Sampler;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.junit.Test;
import org.springframework.cloud.sleuth.ExceptionMessageErrorParser;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.instrument.web.SleuthHttpParserAccessor;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.cloud.sleuth.util.SpanUtil;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class ApacheHttpClientRibbonRequestCustomizerTests {

	private static final String SAMPLED_NAME = "X-B3-Sampled";
	private static final String TRACE_ID_NAME = "X-B3-TraceId";
	private static final String SPAN_ID_NAME = "X-B3-SpanId";
	
	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(CurrentTraceContext.Default.create())
			.spanReporter(this.reporter)
			.build();
	TraceKeys traceKeys = new TraceKeys();
	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing)
			.clientParser(SleuthHttpParserAccessor.getClient(this.traceKeys))
			.serverParser(SleuthHttpParserAccessor.getServer(this.traceKeys, new ExceptionMessageErrorParser()))
			.build();
	brave.Span span = this.tracing.tracer().nextSpan().name("name").start();
	ApacheHttpClientRibbonRequestCustomizer customizer =
			new ApacheHttpClientRibbonRequestCustomizer(this.httpTracing) {
				@Override brave.Span getCurrentSpan() {
					return span;
				}
			};

	@Test
	public void should_accept_customizer_when_apache_http_client_is_passed() throws Exception {
		then(this.customizer.accepts(String.class)).isFalse();
		then(this.customizer.accepts(RequestBuilder.class)).isTrue();
	}

	@Test
	public void should_set_not_sampled_on_the_context_when_there_is_no_span() throws Exception {
		this.span = null;
		Tracing tracing = Tracing.newBuilder()
				.currentTraceContext(CurrentTraceContext.Default.create())
				.spanReporter(this.reporter)
				.sampler(Sampler.NEVER_SAMPLE)
				.build();
		TraceKeys traceKeys = new TraceKeys();
		HttpTracing httpTracing = HttpTracing.newBuilder(tracing)
				.clientParser(SleuthHttpParserAccessor.getClient(traceKeys))
				.serverParser(SleuthHttpParserAccessor.getServer(traceKeys, new ExceptionMessageErrorParser()))
				.build();
		RequestBuilder requestBuilder = RequestBuilder.create("GET").setUri("http://foo");

		new ApacheHttpClientRibbonRequestCustomizer(httpTracing) {
			@Override brave.Span getCurrentSpan() {
				return span;
			}
		}.customize(requestBuilder);

		HttpUriRequest request = requestBuilder.build();
		Header header = request.getFirstHeader(SAMPLED_NAME);
		then(header.getName()).isEqualTo(SAMPLED_NAME);
		then(header.getValue()).isEqualTo("0");
	}

	@Test
	public void should_set_tracing_headers_on_the_context_when_there_is_a_span() throws Exception {
		RequestBuilder requestBuilder = RequestBuilder.create("GET").setUri("http://foo");

		this.customizer.customize(requestBuilder);

		HttpUriRequest request = requestBuilder.build();
		thenThereIsAHeaderWithNameAndValue(request, SPAN_ID_NAME, SpanUtil.idToHex(this.span.context().spanId()));
		thenThereIsAHeaderWithNameAndValue(request, TRACE_ID_NAME, this.span.context().traceIdString());
	}

	@Test
	public void should_not_set_duplicate_tracing_headers_on_the_context_when_there_is_a_span() throws Exception {
		RequestBuilder requestBuilder = RequestBuilder.create("GET").setUri("http://foo");

		this.customizer.customize(requestBuilder);
		this.customizer.customize(requestBuilder);

		HttpUriRequest request = requestBuilder.build();
		thenThereIsAHeaderWithNameAndValue(request, SPAN_ID_NAME, SpanUtil.idToHex(this.span.context().spanId()));
		thenThereIsAHeaderWithNameAndValue(request, TRACE_ID_NAME, this.span.context().traceIdString());
	}

	public void thenThereIsAHeaderWithNameAndValue(HttpUriRequest request, String name, String value) {
		then(request.getHeaders(name)).hasSize(1);
		Header header = request.getFirstHeader(name);
		then(header.getName()).isEqualTo(name);
		then(header.getValue()).isEqualTo(value);
	}
}