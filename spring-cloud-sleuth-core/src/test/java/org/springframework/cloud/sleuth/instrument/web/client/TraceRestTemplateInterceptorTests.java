/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.client;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.SpanContextHolder;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.test.web.client.MockMvcClientHttpRequestFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.assertFalse;

/**
 * @author Dave Syer
 *
 */
public class TraceRestTemplateInterceptorTests {

	private MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.build();

	private RestTemplate template = new RestTemplate(
			new MockMvcClientHttpRequestFactory(this.mockMvc));

	private DefaultTracer traces;

	private StaticApplicationContext publisher = new StaticApplicationContext();

	@Before
	public void setup() {
		this.publisher.refresh();
		this.traces = new DefaultTracer(new AlwaysSampler(), new Random(), this.publisher);
		this.template.setInterceptors(Arrays.<ClientHttpRequestInterceptor>asList(
				new TraceRestTemplateInterceptor(this.traces)));
		SpanContextHolder.removeCurrentSpan();
	}

	@After
	public void clean() {
		SpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void headersAddedWhenTracing() {
		this.traces.continueSpan(Span.builder().traceId(1L).spanId(2L).build());
		@SuppressWarnings("unchecked")
		Map<String, String> headers = this.template.getForEntity("/", Map.class)
				.getBody();
		then(Long.valueOf(headers.get(Span.TRACE_ID_NAME))).isEqualTo(1L);
		then(Long.valueOf(headers.get(Span.SPAN_ID_NAME))).isEqualTo(2L);
	}

	@Test
	public void headersNotAddedWhenNotTracing() {
		@SuppressWarnings("unchecked")
		Map<String, String> headers = this.template.getForEntity("/", Map.class)
				.getBody();
		assertFalse("Wrong headers: " + headers, headers.containsKey(Span.SPAN_ID_NAME));
	}

	@RestController
	public static class TestController {
		@RequestMapping("/")
		public Map<String, String> home(@RequestHeader HttpHeaders headers) {
			Map<String, String> map = new HashMap<String, String>();
			addHeaders(map, headers, Span.SPAN_ID_NAME, Span.TRACE_ID_NAME,
					Span.PARENT_ID_NAME);
			return map;
		}

		private void addHeaders(Map<String, String> map, HttpHeaders headers,
				String... names) {
			if (headers != null) {
				for (String name : names) {
					String value = headers.getFirst(name);
					if (value != null) {
						map.put(name, value);
					}
				}
			}
		}
	}

}
