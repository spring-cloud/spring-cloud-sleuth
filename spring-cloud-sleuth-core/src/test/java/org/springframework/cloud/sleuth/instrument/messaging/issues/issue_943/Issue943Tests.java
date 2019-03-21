/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging.issues.issue_943;

import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Example taken from https://github.com/spring-cloud/spring-cloud-sleuth/issues/943
 */
public class Issue943Tests {

	@Before
	public void setup() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_start_context() {
		try (ConfigurableApplicationContext applicationContext = SpringApplication
				.run(HelloSpringIntegration.class, "--spring.jmx.enabled=false", "--server.port=0")) {
			Span newSpan = applicationContext.getBean(Tracer.class).createSpan("foo");
			RestTemplate restTemplate = applicationContext.getBean(RestTemplate.class);
			String object = restTemplate.getForObject(
					"http://localhost:" + applicationContext.getEnvironment()
							.getProperty("local.server.port") + "/getHelloWorldMessage",
					String.class);
			ArrayListSpanAccumulator accumulator = applicationContext.getBean(ArrayListSpanAccumulator.class);

			then(object)
					.contains("Hellow World Message 1 Persist into DB")
					.contains("Hellow World Message 2 Persist into DB")
					.contains("Hellow World Message 3 Persist into DB");
			then(accumulator.getSpans().stream()
					.filter(span -> span.getTraceId() == newSpan.getTraceId())
					.collect(Collectors.toList())).isNotEmpty()
			.extracting("name")
			.contains("message:splitterOutChannel", "message:messagingChannel",
					"message:messagingProcessedChannel", "message:messagingOutputChannel",
					"http:/getHelloWorldMessage");
		}
		then(ExceptionUtils.getLastException()).isNull();
	}
}
