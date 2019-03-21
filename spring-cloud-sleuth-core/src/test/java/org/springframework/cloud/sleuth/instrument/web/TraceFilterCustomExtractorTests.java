/*
 * Copyright 2013-2017 the original author or authors.
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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.BDDAssertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.TextMapUtil;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = TraceFilterCustomExtractorTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
public class TraceFilterCustomExtractorTests {
	@Autowired RestTemplate restTemplate;
	@Autowired Config config;
	@Autowired CustomRestController customRestController;
	@Autowired ArrayListSpanAccumulator accumulator;
	@Autowired Tracer tracer;

	@Before
	public void setup() {
		this.accumulator.getSpans().clear();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void should_create_a_valid_span_from_custom_headers() {
		final Span newSpan = this.tracer.createSpan("new_span");
		ResponseEntity<Map> responseEntity = null;
		try {
			RequestEntity<?> requestEntity = RequestEntity
					.get(URI.create("http://localhost:" + this.config.port + "/headers"))
					.build();
			responseEntity = this.restTemplate.exchange(requestEntity, Map.class);

 		} finally {
			this.tracer.close(newSpan);
		}
		await().atMost(5, SECONDS).untilAsserted(() -> {
			then(this.accumulator.getSpans().stream().filter(
					span -> span.getSpanId() == newSpan.getSpanId()).findFirst().get())
					.hasTraceIdEqualTo(newSpan.getTraceId());
		});
		BDDAssertions.then(responseEntity.getBody())
				.containsEntry("correlationid", Span.idToHex(newSpan.getTraceId()))
				.containsKey("myspanid")
				.as("input request headers");
	}

	@Configuration
	@EnableAutoConfiguration
	static class Config
			implements ApplicationListener<EmbeddedServletContainerInitializedEvent> {
		int port;

		// tag::configuration[]
		@Bean
		HttpSpanInjector customHttpSpanInjector() {
			return new CustomHttpSpanInjector();
		}

		@Bean
		HttpSpanExtractor customHttpSpanExtractor() {
			return new CustomHttpSpanExtractor();
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
	static class CustomHttpSpanExtractor implements HttpSpanExtractor {

		@Override public Span joinTrace(SpanTextMap carrier) {
			Map<String, String> map = TextMapUtil.asMap(carrier);
			long traceId = Span.hexToId(map.get("correlationid"));
			long spanId = Span.hexToId(map.get("myspanid"));
			// extract all necessary headers
			Span.SpanBuilder builder = Span.builder().traceId(traceId).spanId(spanId);
			// build rest of the Span
			return builder.build();
		}
	}

	static class CustomHttpSpanInjector implements HttpSpanInjector {

		@Override
		public void inject(Span span, SpanTextMap carrier) {
			carrier.put("correlationId", span.traceIdString());
			carrier.put("mySpanId", Span.idToHex(span.getSpanId()));
		}
	}
	// end::extractor[]

	@RestController
	static class CustomRestController {

		@Autowired Tracer tracer;

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
