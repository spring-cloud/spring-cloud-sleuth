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

package org.springframework.cloud.sleuth.autoconfig;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.cloud.sleuth.instrument.messaging.TraceMessageHeaders;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class TraceStreamEnvironmentPostProcessorTests {

	private TraceStreamEnvironmentPostProcessor processor = new TraceStreamEnvironmentPostProcessor() {
		@Override Resource[] getAllSpringBinders(
				PathMatchingResourcePatternResolver resolver) throws IOException {
			return new Resource[] { new ClassPathResource("/META-INF/spring.factories") };
		}

		@Override Collection<String> parseBinderConfigurations(Resource resource) {
			return Collections.singleton("test");
		}
	};
	private MockEnvironment environment = new MockEnvironment();

	@Test
	public void should_append_tracing_headers() {
		this.environment.setProperty("spring.cloud.stream.test.binder.headers", "foo,bar,baz");
		postProcess();
		assertThat(this.environment
				.getProperty("spring.cloud.stream.test.binder.HEADERS[0]"))
					.isEqualTo("foo");
		assertThat(this.environment
				.getProperty("spring.cloud.stream.test.binder.HEADERS[1]"))
					.isEqualTo("bar");
		assertThat(this.environment
				.getProperty("spring.cloud.stream.test.binder.HEADERS[2]"))
					.isEqualTo("baz");
		assertThat(this.environment
				.getProperty("spring.cloud.stream.test.binder.HEADERS[3]"))
					.isEqualTo(TraceMessageHeaders.SPAN_ID_NAME);
	}

	@Test
	public void should_append_tracing_headers_to_existing_ones() {
		EnvironmentTestUtils.addEnvironment(this.environment,
				"spring.cloud.stream.test.binder.HEADERS[0]=X-Custom",
				"spring.cloud.stream.test.binder.HEADERS[1]=X-Mine");
		postProcess();
		assertThat(this.environment
				.getProperty("spring.cloud.stream.test.binder.HEADERS[2]"))
						.isEqualTo(TraceMessageHeaders.SPAN_ID_NAME);
	}

	@Test
	public void should_append_tracing_headers_to_existing_ones_in_single_line() {
		EnvironmentTestUtils.addEnvironment(this.environment,
				"spring.cloud.stream.test.binder.HEADERS=foo,bar");
		postProcess();
		assertThat(this.environment
				.getProperty("spring.cloud.stream.test.binder.HEADERS[2]"))
						.isEqualTo(TraceMessageHeaders.SPAN_ID_NAME);
	}

	@Test
	public void should_not_append_tracing_headers_if_they_are_already_appended() {
		postProcess();
		postProcess();
		postProcess();

		Collection<String> headerValues = defaultPropertiesSource().values();

		Collection<String> traceIds = headerValues.stream()
				.filter(input -> input.contains(TraceMessageHeaders.TRACE_ID_NAME))
				.collect(Collectors.toList());
		assertThat(traceIds).hasSize(1);
		assertThat(defaultPropertiesSource().keySet().stream()
				.filter(input -> input
						.startsWith("spring.cloud.stream.test.binder.HEADERS"))
				.collect(Collectors.toList()))
						.hasSize(TraceStreamEnvironmentPostProcessor.HEADERS.length);
	}

	private void postProcess() {
		this.processor.postProcessEnvironment(this.environment,
				new SpringApplication(TraceStreamEnvironmentPostProcessor.class));
	}

	private Map<String, String> defaultPropertiesSource() {
		return (Map<String, String>) this.environment.getPropertySources()
				.get("defaultProperties").getSource();
	}

}
