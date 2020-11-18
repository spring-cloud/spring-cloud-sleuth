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

package org.springframework.cloud.sleuth.zipkin2;

import brave.sampler.Sampler;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = BraveZipkinDiscoveryClientTests.TestConfig.class)
@TestPropertySource(properties = "spring.sleuth.tracer.mode=BRAVE")
public class BraveZipkinDiscoveryClientTests
		extends org.springframework.cloud.sleuth.zipkin2.ZipkinDiscoveryClientTests {

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class TestConfig {

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}

}
