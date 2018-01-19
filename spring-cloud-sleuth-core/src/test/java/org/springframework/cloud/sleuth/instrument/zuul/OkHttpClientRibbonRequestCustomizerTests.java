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
import okhttp3.Request;
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
public class OkHttpClientRibbonRequestCustomizerTests {

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
	OkHttpClientRibbonRequestCustomizer customizer =
			new OkHttpClientRibbonRequestCustomizer(this.httpTracing) {
				@Override brave.Span getCurrentSpan() {
					return span;
				}
			};

	@Test
	public void should_accept_customizer_when_apache_http_client_is_passed() throws Exception {
		then(this.customizer.accepts(String.class)).isFalse();
		then(this.customizer.accepts(Request.Builder.class)).isTrue();
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
		Request.Builder requestBuilder = requestBuilder();

		new OkHttpClientRibbonRequestCustomizer(httpTracing) {
			@Override brave.Span getCurrentSpan() {
				return span;
			}
		}.customize(requestBuilder);

		this.customizer.customize(requestBuilder);

		Request request = requestBuilder.build();
		then(request.header(SAMPLED_NAME)).isEqualTo("0");
	}

	@Test
	public void should_set_tracing_headers_on_the_context_when_there_is_a_span() throws Exception {
		Request.Builder requestBuilder = requestBuilder();

		this.customizer.customize(requestBuilder);

		Request request = requestBuilder.build();

		thenThereIsAHeaderWithNameAndValue(request, SPAN_ID_NAME, SpanUtil.idToHex(this.span.context().spanId()));
		thenThereIsAHeaderWithNameAndValue(request, TRACE_ID_NAME, this.span.context().traceIdString());
	}

	@Test
	public void should_not_set_duplicate_tracing_headers_on_the_context_when_there_is_a_span() throws Exception {
		Request.Builder requestBuilder = requestBuilder();

		this.customizer.customize(requestBuilder);
		this.customizer.customize(requestBuilder);

		Request request = requestBuilder.build();
		thenThereIsAHeaderWithNameAndValue(request, SPAN_ID_NAME, SpanUtil.idToHex(this.span.context().spanId()));
		thenThereIsAHeaderWithNameAndValue(request, TRACE_ID_NAME, this.span.context().traceIdString());
	}

	private void thenThereIsAHeaderWithNameAndValue(Request request, String name, String value) {
		then(request.headers(name)).hasSize(1);
		then(request.header(name)).isEqualTo(value);
	}

	private Request.Builder requestBuilder() {
		return new Request.Builder().get().url("http://localhost:8080/");
	}
}