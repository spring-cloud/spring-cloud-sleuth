/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.instrument.security;

import brave.sampler.Sampler;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.brave.BraveTestSpanHandler;
import org.springframework.cloud.sleuth.instrument.security.SpringSecurityTests;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

/**
 * @author Jonatan Ivanov
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = TracingSecurityContextChangedListenerIntegrationTests.Config.class)
public class TracingSecurityContextChangedListenerIntegrationTests extends SpringSecurityTests {

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		TestSpanHandler testSpanHandler(brave.test.TestSpanHandler spanHandler) {
			return new BraveTestSpanHandler(spanHandler);
		}

		@Bean
		brave.test.TestSpanHandler spanHandler() {
			return new brave.test.TestSpanHandler();
		}

	}

}
