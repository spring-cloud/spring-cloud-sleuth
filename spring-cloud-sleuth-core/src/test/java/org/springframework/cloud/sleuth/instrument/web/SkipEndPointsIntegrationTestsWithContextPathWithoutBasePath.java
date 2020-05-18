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

package org.springframework.cloud.sleuth.instrument.web;

import brave.Tracer;
import brave.handler.SpanHandler;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.sleuth.DisableSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(
		classes = SkipEndPointsIntegrationTestsWithContextPathWithoutBasePath.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "management.endpoints.web.exposure.include:*",
				"server.servlet.context-path:/context-path",
				"spring.sleuth.http.legacy.enabled:true",
				"management.endpoints.web.base-path:/" })
public class SkipEndPointsIntegrationTestsWithContextPathWithoutBasePath {

	@LocalServerPort
	int port;

	@Autowired
	private TestSpanHandler spans;

	@Autowired
	private Tracer tracer;

	@Before
	@After
	public void clearSpans() {
		this.spans.clear();
	}

	@Test
	public void should_sample_non_actuator_endpoint_with_context_path() {
		new RestTemplate().getForObject(
				"http://localhost:" + this.port + "/context-path/something",
				String.class);

		then(this.tracer.currentSpan()).isNull();
		then(this.spans).hasSize(1);
	}

	@Test
	public void should_sample_non_actuator_endpoint_with_context_path_and_health_in_path() {
		new RestTemplate().getForObject(
				"http://localhost:" + this.port + "/context-path/healthcare",
				String.class);

		then(this.tracer.currentSpan()).isNull();
		then(this.spans).hasSize(1);
	}

	@Test
	public void should_not_sample_actuator_endpoint_with_base_path_set_to_root() {
		new RestTemplate().getForObject(
				"http://localhost:" + this.port + "/context-path/health", String.class);

		then(this.tracer.currentSpan()).isNull();
		then(this.spans).hasSize(0);
	}

	@Test
	public void should_not_sample_actuator_endpoint_with_base_path_set_to_root_and_parameter() {
		new RestTemplate().getForObject(
				"http://localhost:" + this.port + "/context-path/metrics?xyz",
				String.class);

		then(this.tracer.currentSpan()).isNull();
		then(this.spans).hasSize(0);
	}

	@EnableAutoConfiguration(exclude = RabbitAutoConfiguration.class)
	@Configuration
	@DisableSecurity
	@RestController
	public static class Config {

		@GetMapping("something")
		void doNothing() {
		}

		@GetMapping("healthcare")
		void healthCare() {
		}

		@GetMapping("metrics")
		void metrics() {
		}

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}

}
