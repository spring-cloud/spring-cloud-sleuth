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

import brave.http.HttpTracing;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * It is unusual to inject {@link HttpTracing} into an endpoint vs an end-user api such as
 * {@link brave.Tracer} or {@link brave.SpanCustomizer}. However, someone has done this in
 * the past in #1679 and we decided to allow this. This test shows the odd case will work
 * even though it implies a cyclic dependency.
 *
 * @author Marcin Grzejszczak
 */
@SpringBootTest(classes = { EndpointWithCyclicDependenciesTests.Config.class })
public class EndpointWithCyclicDependenciesTests {

	@Test
	public void should_load_context() {

	}

	@Configuration
	@EnableAutoConfiguration
	static class Config {

		@Bean
		MyEndpoint myEndpoint(HttpTracing httpTracing) {
			return new MyEndpoint(httpTracing);
		}

	}

	@Endpoint(id = "my-endpoint")
	public static class MyEndpoint {

		public MyEndpoint(HttpTracing httpTracing) {
			// do something with httpTracing
		}

	}

}
