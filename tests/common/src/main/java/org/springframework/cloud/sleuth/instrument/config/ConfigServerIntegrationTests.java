/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.config;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;
import static org.awaitility.Awaitility.await;

@ContextConfiguration(classes = ConfigServerIntegrationTests.TestConfig.class)
@TestPropertySource(properties = { "server.port=0",
		"spring.cloud.config.server.git.uri=https://github.com/spring-cloud-samples/config-repo" })
public abstract class ConfigServerIntegrationTests {

	@Autowired
	TestSpanHandler spans;

	@Autowired
	WebClientService webClientService;

	@LocalServerPort
	int port;

	@BeforeEach
	public void setup() {
		this.spans.clear();
	}

	@Test
	public void should_instrument_config_server() {
		this.webClientService.call(port);

		await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
			then(this.spans.reportedSpans()).as("1 for mvc, 1 for composite env repo and 1 for git env repo")
					.hasSize(3);
			then(this.spans.reportedSpans().stream().map(FinishedSpan::getTraceId).collect(Collectors.toSet()))
					.as("There must be 1 trace id").hasSize(1);
		});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@EnableConfigServer
	public static class TestConfig {

		@Bean
		WebClientService webClientService() {
			return new WebClientService();
		}

	}

	public static class WebClientService {

		private static final Logger log = LoggerFactory.getLogger(WebClientService.class);

		void call(int port) {
			log.info("Sending request");
			String result = new RestTemplate().getForObject("http://localhost:" + port + "/foo/default/main",
					String.class);
			log.info("Got [\n" + result + "\n]");
		}

	}

}
