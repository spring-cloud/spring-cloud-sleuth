/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.baggage;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.BaggageInScope;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

/**
 * @author Taras Danylchuk
 */
@ContextConfiguration(classes = W3CBaggageTests.TestConfig.class)
@TestPropertySource(properties = { "spring.sleuth.baggage.remote-fields[0]=foo", "spring.sleuth.propagation.type=W3C" })
public abstract class W3CBaggageTests {

	@Autowired
	Tracer tracer;

	@Autowired
	RestTemplate restTemplate;

	@Autowired
	MockWebServer mockWebServer;

	@Test
	@SuppressWarnings("unchecked")
	public void shouldProduceOnlyW3cBaggageEntries() throws InterruptedException {
		this.mockWebServer.enqueue(new MockResponse().setBody("hello"));
		Span span = this.tracer.nextSpan();

		try (Tracer.SpanInScope spanInScope = this.tracer.withSpan(span.start())) {
			try (BaggageInScope bs = this.tracer.createBaggage("foo").set("bar")) {
				// when
				this.restTemplate.getForObject(this.mockWebServer.url("/baggage").toString(), String.class);

				// then
				RecordedRequest request = this.mockWebServer.takeRequest(1, TimeUnit.SECONDS);
				Map<String, List<String>> map = request.getHeaders().toMultimap();
				BDDAssertions.then(map).doesNotContainKey("foo");
				List<String> baggage = map.get("baggage");
				BDDAssertions.then(baggage.stream().anyMatch(s -> s.contains("foo=bar"))).isTrue();
			}
			finally {
				span.end();
			}
		}
	}

	@EnableAutoConfiguration
	@Configuration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean
		MockWebServer mockWebServer() throws IOException {
			MockWebServer mockWebServer = new MockWebServer();
			mockWebServer.start();
			return mockWebServer;
		}

	}

}
