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
import brave.sampler.Sampler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
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
		webEnvironment = RANDOM_PORT, properties = { "spring.sleuth.baggage-keys=baz",
				"spring.sleuth.remote-keys=country-code" })
public class MultipleHopsIntegrationTests {

	static final BaggageField COUNTRY_CODE = BaggageField.create("country-code");

	@Autowired
	Tracer tracer;

	@Autowired
	ArrayListSpanReporter reporter;

	@Autowired
	RestTemplate restTemplate;

	@Autowired
	Config config;

	@Autowired
	DemoApplication application;

	@BeforeEach
	public void setup() {
		this.reporter.clear();
	}

	@Test
	public void should_prepare_spans_for_export() {
		this.restTemplate.getForObject(
				"http://localhost:" + this.config.port + "/greeting", String.class);

		await().atMost(5, SECONDS).untilAsserted(() -> {
			then(this.reporter.getSpans()).hasSize(14);
		});
		then(this.reporter.getSpans().stream().map(zipkin2.Span::name).collect(toList()))
				.containsAll(asList("get /greeting", "send"));
		then(this.reporter.getSpans().stream().map(zipkin2.Span::kind)
				// no server kind due to test constraints
				.collect(toList()))
						.containsAll(asList(zipkin2.Span.Kind.CONSUMER,
								zipkin2.Span.Kind.PRODUCER, zipkin2.Span.Kind.SERVER));
		then(this.reporter.getSpans().stream().map(span -> span.tags().get("channel"))
				.filter(Objects::nonNull).distinct().collect(toList())).hasSize(3)
						.containsAll(asList("words", "counts", "greetings"));
	}

	@Test
	public void should_propagate_the_baggage() {
		// TODO: make a DemoBaggage type instead of saying to use the api directly
		BaggageField bar = BaggageField.create("bar");
		BaggageField baz = BaggageField.create("baz");

		// tag::baggage[]
		Span initialSpan = this.tracer.nextSpan().name("span").start();
		COUNTRY_CODE.updateValue(initialSpan.context(), "FO");
		bar.updateValue(initialSpan.context(), "2");
		// end::baggage[]

		try (SpanInScope ws = this.tracer.withSpanInScope(initialSpan)) {
			// tag::baggage_tag[]
			Tags.BAGGAGE_FIELD.tag(COUNTRY_CODE, initialSpan);
			Tags.BAGGAGE_FIELD.tag(bar, initialSpan);
			// end::baggage_tag[]

			// set baz in a header not with the api explicitly
			HttpHeaders headers = new HttpHeaders();
			headers.put("baggage-baz", Collections.singletonList("3"));
			RequestEntity requestEntity = new RequestEntity(headers, HttpMethod.GET,
					URI.create("http://localhost:" + this.config.port + "/greeting"));
			this.restTemplate.exchange(requestEntity, String.class);
		}
		finally {
			initialSpan.finish();
		}

		await().atMost(5, SECONDS).untilAsserted(() -> {
			then(this.reporter.getSpans()).isNotEmpty();
		});

		List<zipkin2.Span> withBagTags = this.reporter.getSpans().stream()
				.filter(s -> s.tags().containsKey(COUNTRY_CODE.name())).collect(toList());

		// set with tag api
		then(withBagTags).as("only initialSpan was bag tagged").hasSize(1);
		assertThat(withBagTags.get(0).tags()).containsEntry("country-code", "FO")
				.containsEntry("bar", "2");

		// set with baggage api
		then(this.application.allSpans()).as("All have country-code")
				.allMatch(span -> "FO".equals(COUNTRY_CODE.getValue(span.context())));
		then(this.application.allSpans()).as("All have bar")
				.allMatch(span -> "2".equals(bar.getValue(span.context())));

		// baz is not tagged in the initial span, only downstream!
		then(this.application.allSpans()).as("All downstream have baz")
				.filteredOn(span -> !span.equals(initialSpan))
				.allMatch(span -> "3".equals(baz.getValue(span.context())));
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
		ArrayListSpanReporter arrayListSpanAccumulator() {
			return new ArrayListSpanReporter();
		}

		@Bean
		Sampler defaultTraceSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}

}
