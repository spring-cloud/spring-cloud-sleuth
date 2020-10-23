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

package org.springframework.cloud.sleuth.brave.instrument.web.client;

import java.util.Map;

import brave.propagation.Propagation;
import brave.sampler.Sampler;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.brave.BraveTestSpanHandler;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(classes = WebClientNotSampledTests.Config.class)
public class WebClientNotSampledTests
		extends org.springframework.cloud.sleuth.instrument.web.client.integration.notsampled.WebClientNotSampledTests {

	@Override
	public void assertB3SingleNotSampled(ResponseEntity<Map<String, String>> response) {
		then(response.getBody().get("b3")).isNotNull().contains("-0-"); // not sampled
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		Propagation.Factory sleuthFactory() {
			return new MergedFactory();
		}

		@Bean
		TestSpanHandler testSpanHandlerSupplier(brave.test.TestSpanHandler testSpanHandler) {
			return new BraveTestSpanHandler(testSpanHandler);
		}

		@Bean
		Sampler alwaysSampler() {
			return Sampler.NEVER_SAMPLE;
		}

		@Bean
		brave.test.TestSpanHandler braveTestSpanHandler() {
			return new brave.test.TestSpanHandler();
		}

	}

}
