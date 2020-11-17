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

package org.springframework.cloud.sleuth.baggage.multiple;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration;
import org.springframework.cloud.sleuth.api.BaggageInScope;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(classes = MultipleHopsIntegrationTests.TestConfig.class)
@TestPropertySource(properties = { "spring.sleuth.baggage.remote-fields=x-vcap-request-id,country-code",
		"spring.sleuth.baggage.local-fields=bp", "spring.sleuth.integration.enabled=true" })
public abstract class MultipleHopsIntegrationTests {

	protected static final String REQUEST_ID = "x-vcap-request-id";

	protected static final String BUSINESS_PROCESS = "bp";

	protected static final String COUNTRY_CODE = "country-code";

	@Autowired
	Tracer tracer;

	@Autowired
	protected TestSpanHandler spans;

	@Autowired
	RestTemplate restTemplate;

	@Autowired
	TestConfig testConfig;

	@Autowired
	protected DemoApplication application;

	@BeforeEach
	public void setup() {
		this.spans.clear();
	}

	@Test
	public void should_prepare_spans_for_export() {
		this.restTemplate.getForObject("http://localhost:" + this.testConfig.port + "/greeting", String.class);

		await().atMost(5, SECONDS).untilAsserted(() -> {
			then(this.spans).hasSize(14);
		});
		assertSpanNames();
		then(this.spans).extracting(FinishedSpan::getKind)
				// no server kind due to test constraints
				.containsAll(asList(Span.Kind.CONSUMER, Span.Kind.PRODUCER, Span.Kind.SERVER));
		then(this.spans.reportedSpans().stream().map(span -> span.getTags().get("channel")).filter(Objects::nonNull)
				.distinct().collect(toList())).hasSize(3).containsAll(asList("words", "counts", "greetings"));
	}

	protected void assertSpanNames() {
		throw new UnsupportedOperationException("Implement this assertion");
	}

	@Test
	public void should_propagate_the_baggage() {
		Span initialSpan = this.tracer.nextSpan().name("span").start();
		System.out.println("FOO: " + initialSpan.context().traceId());
		// tag::baggage[]
		try (Tracer.SpanInScope ws = this.tracer.withSpan(initialSpan)) {
			BaggageInScope businessProcess = this.tracer.createBaggage(BUSINESS_PROCESS).set("ALM");
			BaggageInScope countryCode = this.tracer.createBaggage(COUNTRY_CODE).set("FO");
			try {
				// end::baggage[]
				// tag::baggage_tag[]
				initialSpan.tag(BUSINESS_PROCESS, "ALM");
				// end::baggage_tag[]

				// set request ID in a header not with the api explicitly
				HttpHeaders headers = new HttpHeaders();
				headers.put(REQUEST_ID, Collections.singletonList("f4308d05-2228-4468-80f6-92a8377ba193"));
				RequestEntity requestEntity = new RequestEntity(headers, HttpMethod.GET,
						URI.create("http://localhost:" + this.testConfig.port + "/greeting"));
				this.restTemplate.exchange(requestEntity, String.class);
			}
			finally {
				countryCode.close();
				businessProcess.close();
			}
		}
		finally {
			initialSpan.end();
		}

		await().atMost(5, SECONDS).untilAsserted(() -> {
			then(this.spans).isNotEmpty();
		});

		List<FinishedSpan> withBagTags = this.spans.reportedSpans().stream()
				.filter(s -> s.getTags().containsKey(BUSINESS_PROCESS)).collect(toList());

		// set with tag api
		then(withBagTags).as("only initialSpan was bag tagged").hasSize(1);
		assertThat(withBagTags.get(0).getTags()).containsEntry(BUSINESS_PROCESS, "ALM");

		Set<String> traceIds = this.application.allSpans().stream().map(s -> s.context().traceId())
				.collect(Collectors.toSet());
		then(traceIds).hasSize(1);
		then(traceIds.iterator().next()).as("All have same trace ID").isEqualTo(initialSpan.context().traceId());
		assertBaggage(initialSpan);
	}

	protected void assertBaggage(Span initialSpan) {
		throw new UnsupportedOperationException("Implement this assertion");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class, JmxAutoConfiguration.class })
	@Import(DemoApplication.class)
	public static class TestConfig implements ApplicationListener<ServletWebServerInitializedEvent> {

		int port;

		@Override
		public void onApplicationEvent(ServletWebServerInitializedEvent event) {
			this.port = event.getSource().getPort();
		}

		@Bean
		RestTemplate restTemplate() {
			return new RestTemplate();
		}

	}

}
