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

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import brave.SpanCustomizer;
import brave.http.HttpClientRequest;
import brave.http.HttpRequest;
import brave.propagation.TraceContext;
import org.junit.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test case for Sleuth 1.0 tagging implementation.
 *
 * @author Sven Zethelius
 */
@Deprecated
public class SleuthHttpClientParserTests {

	private TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();

	private TraceKeys traceKeys = new TraceKeys();

	private TestSpan span = new TestSpan();

	private SleuthHttpClientParser parser = new SleuthHttpClientParser(this.traceKeys);

	@Test
	public void should_shorten_the_span_name() {
		HttpRequest request = mock(HttpRequest.class);
		when(request.method()).thenReturn("GET");
		when(request.url()).thenReturn("https://foo/" + bigName());

		parser.parse(request, context, span);

		then(this.span.name).hasSize(50);
	}

	private String bigName() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 60; i++) {
			sb.append("a");
		}
		return sb.toString();
	}

	@Test
	public void recordsMethodHostAndUrl() {
		HttpRequest request = mock(HttpRequest.class);
		when(request.method()).thenReturn("POST");
		when(request.url()).thenReturn("http://localhost/?foo=bar");
		when(request.header("host")).thenReturn("localhost");

		parser.parse(request, context, span);

		then(this.span.tags).containsEntry("http.url", "http://localhost/?foo=bar")
				.containsEntry("http.host", "localhost").containsEntry("http.path", "/")
				.containsEntry("http.method", "POST");
	}

	@Test
	public void addsAdditionalHeaders() {
		this.traceKeys.getHttp().getHeaders().add("x-foo");

		HttpRequest request = mock(HttpRequest.class);
		when(request.header("x-foo")).thenReturn("bar");

		parser.parse(request, context, span);

		then(this.span.tags).containsEntry("http.x-foo", "bar");
	}

	@Test
	public void should_set_tags_on_span_with_proper_header_values() throws Exception {
		this.traceKeys.getHttp()
				.setHeaders(Arrays.asList("Accept", "User-Agent", "Content-Type"));

		this.parser.parse(new HttpClientRequest() {
			private final URL url = new URL("http://localhost:8080/");

			@Override
			public String method() {
				return "GET";
			}

			@Override
			public String path() {
				return null;
			}

			@Override
			public String url() {
				return this.url.toString();
			}

			@Override
			public String header(String name) {
				if (name.equals("Accept")) {
					return "'text/plain','text/xml'";
				}
				else if (name.equals("User-Agent")) {
					return "Test";
				}
				return null;
			}

			@Override
			public Object unwrap() {
				return null;
			}

			@Override
			public void header(String name, String value) {
			}
		}, context, this.span);

		then(this.span.tags).containsEntry("http.user-agent", "Test")
				.containsEntry("http.accept", "'text/plain','text/xml'")
				.doesNotContainKey("http.content-type");
	}

}

class TestSpan implements SpanCustomizer {

	String name;

	Map<String, String> tags = new HashMap<>();

	@Override
	public SpanCustomizer name(String name) {
		this.name = name;
		return this;
	}

	@Override
	public SpanCustomizer tag(String key, String value) {
		this.tags.put(key, value);
		return this;
	}

	@Override
	public SpanCustomizer annotate(String value) {
		return this;
	}

}
