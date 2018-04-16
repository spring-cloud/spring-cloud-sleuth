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

package org.springframework.cloud.sleuth.instrument.messaging.issues.issue_943;

import java.util.stream.Collectors;

import brave.Span;
import brave.Tracer;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Example taken from https://github.com/spring-cloud/spring-cloud-sleuth/issues/943
 */
public class Issue943Tests {

	@Test
	public void should_pass_tracing_context_via_spring_integration() {
		try (ConfigurableApplicationContext applicationContext = SpringApplication
				.run(HelloSpringIntegration.class, "--spring.jmx.enabled=false", "--server.port=0")) {
			// given
			Tracer tracer = applicationContext.getBean(Tracer.class);
			Span newSpan = tracer.nextSpan().name("foo").start();
			String object;
			try (Tracer.SpanInScope ws = tracer.withSpanInScope(newSpan)) {
				RestTemplate restTemplate = applicationContext.getBean(RestTemplate.class);
				// when
				object = restTemplate.getForObject(
						"http://localhost:" + applicationContext.getEnvironment()
								.getProperty("local.server.port") + "/getHelloWorldMessage",
						String.class);
			}

			// then
			ArrayListSpanReporter accumulator = applicationContext.getBean(ArrayListSpanReporter.class);
			then(object)
					.contains("Hellow World Message 1 Persist into DB")
					.contains("Hellow World Message 2 Persist into DB")
					.contains("Hellow World Message 3 Persist into DB");
			then(accumulator.getSpans().stream()
					.filter(span -> span.traceId().equals(newSpan.context().traceIdString()))
					.map(span -> span.tags().getOrDefault("channel", span.tags().get("http.path")))
					.collect(Collectors.toList()))
					.as("trace context was propagated successfully")
					.isNotEmpty()
					.contains("splitterOutChannel", "messagingChannel",
							"messagingProcessedChannel", "messagingOutputChannel",
							"/getHelloWorldMessage");
		}
	}
}
