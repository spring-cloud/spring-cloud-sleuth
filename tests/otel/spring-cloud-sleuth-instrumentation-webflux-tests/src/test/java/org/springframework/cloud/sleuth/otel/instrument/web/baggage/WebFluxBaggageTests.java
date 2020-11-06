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

package org.springframework.cloud.sleuth.otel.instrument.web.baggage;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.sdk.trace.samplers.Sampler;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.otel.OtelTestSpanHandler;
import org.springframework.cloud.sleuth.otel.exporter.ArrayListSpanProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.RequestEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT, classes = WebFluxBaggageTests.Config.class,
		properties = "spring.main.web-application-type=REACTIVE")
@ActiveProfiles("baggage")
public class WebFluxBaggageTests {

	@LocalServerPort
	int port;

	@Autowired
	Service2Client service2Client;

	@Autowired
	MockWebServer mockWebServer;

	@BeforeEach
	void setup() {
		this.service2Client.reset();
	}

	@ParameterizedTest
	@ValueSource(strings = { "/start", "/startWithOtel" })
	void should_propagate_baggage(String path) throws InterruptedException {
		this.mockWebServer.enqueue(new MockResponse().setBody("hello"));

		String response = requestWithBaggage(path);

		then(response).as("Request was sent and response received").isEqualTo("hello");
		thenBaggageWasProperlyPropagatedWithinWebFlux();
		thenBaggageWasPropagatedViaHttpClient();
	}

	private String requestWithBaggage(String path) {
		return new TestRestTemplate().exchange(
				RequestEntity.get(URI.create("http://localhost:" + port + path)).header("baggage", "super").build(),
				String.class).getBody();
	}

	private void thenBaggageWasPropagatedViaHttpClient() throws InterruptedException {
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest(1, TimeUnit.SECONDS);
		then(recordedRequest).isNotNull();
		then(recordedRequest.getHeader("baggage")).isEqualTo("super");
		then(recordedRequest.getHeader("key")).isEqualTo("foo");
	}

	private void thenBaggageWasProperlyPropagatedWithinWebFlux() {
		then(service2Client.getSuperSecretBaggage()).isEqualTo("super");
		then(service2Client.getBaggageKey()).isEqualTo("foo");
	}

	@EnableAutoConfiguration
	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		MockWebServer mockWebServer() throws IOException {
			MockWebServer mockWebServer = new MockWebServer();
			mockWebServer.start();
			return mockWebServer;
		}

		@Bean
		WebClient.Builder webClientBuilder() {
			return WebClient.builder();
		}

		@Bean
		Service2Client service2Client(WebClient.Builder builder, MockWebServer mockWebServer, Tracer tracer) {
			return new Service2Client(builder.build(), mockWebServer.url("/").toString(), tracer);
		}

		@Bean
		Service1Controller controller(Service2Client service2Client) {
			return new Service1Controller(service2Client);
		}

		@Bean
		OtelTestSpanHandler testSpanHandler() {
			return new OtelTestSpanHandler(new ArrayListSpanProcessor());
		}

		@Bean
		Sampler alwaysSampler() {
			return Sampler.alwaysOn();
		}

	}

}
