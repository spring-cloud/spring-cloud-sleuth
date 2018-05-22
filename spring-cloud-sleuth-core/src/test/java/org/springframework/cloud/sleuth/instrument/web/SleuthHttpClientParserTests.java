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

package org.springframework.cloud.sleuth.instrument.web;

import static org.assertj.core.api.BDDAssertions.then;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import brave.SpanCustomizer;
import brave.http.HttpClientAdapter;

/**
 * Test case for HttpTraceKeysInjector
 * 
 * @author Sven Zethelius
 */
public class SleuthHttpClientParserTests {
	private TraceKeys traceKeys = new TraceKeys();
	private TestSpanCustomizer customizer = new TestSpanCustomizer();
	private SleuthHttpClientParser parser = new SleuthHttpClientParser(this.traceKeys);

	@Test
	public void should_set_tags_on_span_with_proper_header_values() throws Exception {
		this.traceKeys.getHttp().setHeaders(Arrays.asList("Accept", "User-Agent", "Content-Type"));

		this.parser.request(new HttpClientAdapter<Object, Object>() {
			private final URL url = new URL("http://localhost:8080/");

			@Override public String method(Object request) {
				return "GET";
			}

			@Override public String url(Object request) {
				return url.toString();
			}

			@Override public String requestHeader(Object request, String name) {
				if (name.equals("Accept")) {
					return "'text/plain','text/xml'";
				} else if (name.equals("User-Agent")) {
					return "Test";
				}
				return null;
			}

			@Override public Integer statusCode(Object response) {
				return 200;
			}
		}, null, this.customizer);

		then(this.customizer.tags)
			.containsEntry("http.user-agent", "Test")
			.containsEntry("http.accept", "'text/plain','text/xml'")
			.doesNotContainKey("http.content-type");
	}
}

class TestSpanCustomizer implements SpanCustomizer {

	Map<String, String> tags = new HashMap<>();

	@Override public SpanCustomizer name(String name) {
		return this;
	}

	@Override public SpanCustomizer tag(String key, String value) {
		this.tags.put(key, value);
		return this;
	}

	@Override public SpanCustomizer annotate(String value) {
		return this;
	}
}