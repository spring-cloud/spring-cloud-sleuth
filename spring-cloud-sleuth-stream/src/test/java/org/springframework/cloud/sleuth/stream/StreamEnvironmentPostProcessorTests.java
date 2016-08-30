/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.sleuth.stream;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.EnvironmentTestUtils;
import org.springframework.cloud.sleuth.Span;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class StreamEnvironmentPostProcessorTests {

	private StreamEnvironmentPostProcessor processor = new StreamEnvironmentPostProcessor();
	private ConfigurableEnvironment environment = new StandardEnvironment();

	@Test
	public void should_append_tracing_headers() {
		postProcess();
		assertThat(this.environment.getProperty("spring.cloud.stream.test.binder.headers[0]"))
				.isEqualTo(Span.SPAN_ID_NAME);
	}

	@Test
	public void should_append_tracing_headers_to_existing_ones() {
		EnvironmentTestUtils.addEnvironment(this.environment, "spring.cloud.stream.test.binder.headers[0]=X-Custom",
				"spring.cloud.stream.test.binder.headers[1]=X-Mine");
		postProcess();
		assertThat(this.environment.getProperty("spring.cloud.stream.test.binder.headers[2]"))
				.isEqualTo(Span.SPAN_ID_NAME);
	}

	@Test
	public void should_not_append_tracing_headers_if_they_are_already_appended() {
		postProcess();
		postProcess();
		postProcess();

		Collection<String> headerValues = defaultPropertiesSource().values();

		Collection<String> traceIds = headerValues.stream()
				.filter(input -> input.contains(Span.TRACE_ID_NAME)).collect(Collectors.toList());
		assertThat(traceIds).hasSize(1);
		assertThat(defaultPropertiesSource().keySet().stream()
				.filter(input -> input.startsWith("spring.cloud.stream.test.binder.headers"))
				.collect(Collectors.toList())).hasSize(StreamEnvironmentPostProcessor.headers.length);
	}

	private void postProcess() {
		this.processor.postProcessEnvironment(this.environment,
				new SpringApplication(StreamEnvironmentPostProcessorTests.class));
	}

	private Map<String, String> defaultPropertiesSource() {
		return (Map<String, String>) this.environment.getPropertySources().get("defaultProperties").getSource();
	}

}
