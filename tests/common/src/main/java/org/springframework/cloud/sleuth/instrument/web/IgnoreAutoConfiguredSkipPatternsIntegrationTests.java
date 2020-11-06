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

package org.springframework.cloud.sleuth.instrument.web;

import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@ContextConfiguration(classes = IgnoreAutoConfiguredSkipPatternsIntegrationTests.TestConfig.class)
@TestPropertySource(properties = { "management.endpoints.web.exposure.include:*",
		"server.servlet.context-path:/context-path", "spring.sleuth.web.ignoreAutoConfiguredSkipPatterns:true" })
public abstract class IgnoreAutoConfiguredSkipPatternsIntegrationTests {

	@Autowired
	TestSpanHandler spans;

	@Autowired
	Tracer tracer;

	@LocalServerPort
	int port;

	@BeforeEach
	@AfterEach
	public void clearSpans() {
		this.spans.clear();
	}

	@Test
	public void should_sample_actuator_endpoint_when_override_pattern_is_true() {
		new RestTemplate().getForObject("http://localhost:" + this.port + "/context-path/actuator/health",
				String.class);

		Awaitility.await().untilAsserted(() -> {
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
			BDDAssertions.then(this.spans).hasSize(1);
		});
	}

	@Test
	public void should_sample_non_actuator_endpoint_when_override_pattern_is_true() {
		new RestTemplate().getForObject("http://localhost:" + this.port + "/context-path/something", String.class);

		Awaitility.await().untilAsserted(() -> {
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
			BDDAssertions.then(this.spans).hasSize(1);
		});
	}

	@Test
	public void should_not_sample_default_skip_patterns_when_override_pattern_is_true() {
		new RestTemplate().getForObject("http://localhost:" + this.port + "/context-path/index.html", String.class);

		Awaitility.await().untilAsserted(() -> {
			BDDAssertions.then(this.tracer.currentSpan()).isNull();
			BDDAssertions.then(this.spans).hasSize(0);
		});
	}

	@EnableAutoConfiguration
	@Configuration(proxyBeanMethods = false)
	public static class TestConfig {

		@Bean
		TestRestController testRestController() {
			return new TestRestController();
		}

	}

	@RestController
	public static class TestRestController {

		@GetMapping("something")
		void doNothing() {
		}

		@GetMapping("index.html")
		void html() {
		}

	}

}
