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

package org.springframework.cloud.sleuth.instrument.multiple;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import brave.Span;
import brave.Tags;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagationConfig;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(classes = MultipleHopsIntegrationTests.Config.class,
		webEnvironment = RANDOM_PORT,
		properties = {
				"spring.sleuth.baggage.remote-fields=x-vcap-request-id,country-code",
				"spring.sleuth.baggage.local-fields=bp",
				"spring.sleuth.integration.enabled=true" })
public class MultipleHopsIntegrationTests {

	static final BaggageField REQUEST_ID = BaggageField.create("x-vcap-request-id");
	static final BaggageField BUSINESS_PROCESS = BaggageField.create("bp");
	static final BaggageField COUNTRY_CODE = BaggageField.create("country-code");

	@Autowired
	Tracer tracer;

	@Autowired
	TestSpanHandler spans;

	@Autowired
	RestTemplate restTemplate;

	@Autowired
	Config config;

	@Autowired
	DemoApplication application;

	@BeforeEach
	public void setup() {
		this.spans.clear();
	}

	@Test
	public void should_prepare_spans_for_export() {
		this.restTemplate.getForObject(
				"http://localhost:" + this.config.port + "/greeting", String.class);

		await().atMost(5, SECONDS).untilAsserted(() -> {
			then(this.spans).hasSize(14);
		});
		then(this.spans).extracting(MutableSpan::name)
				.containsAll(asList("GET /greeting", "send"));
		then(this.spans).extracting(MutableSpan::kind)
				// no server kind due to test constraints
				.containsAll(
						asList(Span.Kind.CONSUMER, Span.Kind.PRODUCER, Span.Kind.SERVER));
		then(this.spans.spans().stream().map(span -> span.tags().get("channel"))
				.filter(Objects::nonNull).distinct().collect(toList())).hasSize(3)
						.containsAll(asList("words", "counts", "greetings"));
	}

	@Test
	public void should_propagate_the_baggage() {
		// tag::baggage[]
		Span initialSpan = this.tracer.nextSpan().name("span").start();
		BUSINESS_PROCESS.updateValue(initialSpan.context(), "ALM");
		COUNTRY_CODE.updateValue(initialSpan.context(), "FO");
		// end::baggage[]

		try (SpanInScope ws = this.tracer.withSpanInScope(initialSpan)) {
			// tag::baggage_tag[]
			Tags.BAGGAGE_FIELD.tag(BUSINESS_PROCESS, initialSpan);
			// end::baggage_tag[]

			// set request ID in a header not with the api explicitly
			HttpHeaders headers = new HttpHeaders();
			headers.put(REQUEST_ID.name(),
					Collections.singletonList("f4308d05-2228-4468-80f6-92a8377ba193"));
			RequestEntity requestEntity = new RequestEntity(headers, HttpMethod.GET,
					URI.create("http://localhost:" + this.config.port + "/greeting"));
			this.restTemplate.exchange(requestEntity, String.class);
		}
		finally {
			initialSpan.finish();
		}

		await().atMost(5, SECONDS).untilAsserted(() -> {
			then(this.spans).isNotEmpty();
		});

		List<MutableSpan> withBagTags = this.spans.spans().stream()
				.filter(s -> s.tags().containsKey(BUSINESS_PROCESS.name()))
				.collect(toList());

		// set with tag api
		then(withBagTags).as("only initialSpan was bag tagged").hasSize(1);
		assertThat(withBagTags.get(0).tags()).containsEntry(BUSINESS_PROCESS.name(),
				"ALM");

		// set with baggage api
		then(this.application.allSpans()).as("All have request ID")
				.allMatch(span -> "f4308d05-2228-4468-80f6-92a8377ba193"
						.equals(REQUEST_ID.getValue(span.context())));

		// baz is not tagged in the initial span, only downstream!
		then(this.application.allSpans()).as("All downstream have country-code")
				.filteredOn(span -> !span.equals(initialSpan))
				.allMatch(span -> "FO".equals(COUNTRY_CODE.getValue(span.context())));
	}

	@Configuration
	@SpringBootApplication(exclude = JmxAutoConfiguration.class)
	public static class Config
			implements ApplicationListener<ServletWebServerInitializedEvent> {

		int port;

		@Override
		public void onApplicationEvent(ServletWebServerInitializedEvent event) {
			this.port = event.getSource().getPort();
		}

		@Bean
		BaggagePropagationConfig notInProperties() {
			return SingleBaggageField.remote(BaggageField.create("bar"));
		}

		@Bean
		RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		@Bean
		Sampler defaultTraceSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}

}
