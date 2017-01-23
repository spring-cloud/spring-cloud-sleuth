/*
 * Copyright 2013-2016 the original author or authors.
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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.servlet.http.HttpServletRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static com.jayway.awaitility.Awaitility.await;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TraceFilterCustomExtractorTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
public class TraceFilterCustomExtractorTests {
	@Autowired Random random;
	@Autowired RestTemplate restTemplate;
	@Autowired Config config;
	@Autowired CustomRestController customRestController;
	@Autowired ArrayListSpanAccumulator accumulator;

	@Before
	public void setup() {
		this.accumulator.getSpans().clear();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void should_create_a_valid_span_from_custom_headers() {
		long spanId = this.random.nextLong();
		long traceId = this.random.nextLong();
		RequestEntity<?> requestEntity = RequestEntity
				.get(URI.create("http://localhost:" + this.config.port + "/headers"))
				.header("correlationId", Span.idToHex(traceId))
				.header("mySpanId", Span.idToHex(spanId)).build();

		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> responseEntity = this.restTemplate.exchange(requestEntity,
				Map.class);

		await().until(() -> then(this.accumulator.getSpans().stream().filter(
				span -> span.getSpanId() == spanId).findFirst().get())
				.hasTraceIdEqualTo(traceId));
		then(responseEntity.getBody())
				.containsEntry("correlationid", Span.idToHex(traceId))
				.containsEntry("myspanid", Span.idToHex(spanId))
				.as("input request headers");
	}

	@Configuration
	@EnableAutoConfiguration
	static class Config
			implements ApplicationListener<EmbeddedServletContainerInitializedEvent> {
		int port;

		// tag::configuration[]
		@Bean
		@Primary
		SpanExtractor<HttpServletRequest> customHttpServletRequestSpanExtractor() {
			return new CustomHttpServletRequestSpanExtractor();
		}
		// end::configuration[]

		@Override
		public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
			this.port = event.getEmbeddedServletContainer().getPort();
		}

		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean
		CustomRestController customRestController() {
			return new CustomRestController();
		}

		@Bean
		Sampler alwaysSampler() {
			return new AlwaysSampler();
		}

		@Bean
		SpanReporter spanReporter() {
			return new ArrayListSpanAccumulator();
		}
	}

	// tag::extractor[]
	static class CustomHttpServletRequestSpanExtractor
			implements SpanExtractor<HttpServletRequest> {

		@Override
		public Span joinTrace(HttpServletRequest carrier) {
			long traceId = Span.hexToId(carrier.getHeader("correlationId"));
			long spanId = Span.hexToId(carrier.getHeader("mySpanId"));
			// extract all necessary headers
			Span.SpanBuilder builder = Span.builder().traceId(traceId).spanId(spanId);
			// build rest of the Span
			return builder.build();
		}
	}
	// end::extractor[]

	@RestController
	static class CustomRestController {

		@RequestMapping("/headers")
		public Map<String, String> headers(@RequestHeader HttpHeaders headers) {
			Map<String, String> map = new HashMap<>();
			for (String key : headers.keySet()) {
				map.put(key, headers.getFirst(key));
			}
			return map;
		}
	}
}
