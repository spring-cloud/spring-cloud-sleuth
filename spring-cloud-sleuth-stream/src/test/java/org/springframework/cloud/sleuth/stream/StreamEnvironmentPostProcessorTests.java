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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.EnvironmentTestUtils;
import org.springframework.cloud.sleuth.Span;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

/**
 * @author Dave Syer
 *
 */
public class StreamEnvironmentPostProcessorTests {

	private StreamEnvironmentPostProcessor processor = new StreamEnvironmentPostProcessor();
	private ConfigurableEnvironment environment = new StandardEnvironment();

	@Test
	public void testHeadersAdded() {
		this.processor.postProcessEnvironment(this.environment,
				new SpringApplication(StreamEnvironmentPostProcessorTests.class));
		assertThat(this.environment.getProperty("spring.cloud.stream.test.binder.headers[0]"))
				.isEqualTo(Span.SPAN_ID_NAME);
	}

	@Test
	public void headersAppendedIfNecessary() {
		EnvironmentTestUtils.addEnvironment(this.environment, "spring.cloud.stream.test.binder.headers[0]=X-Custom",
				"spring.cloud.stream.test.binder.headers[1]=X-Mine");
		this.processor.postProcessEnvironment(this.environment,
				new SpringApplication(StreamEnvironmentPostProcessorTests.class));
		assertThat(this.environment.getProperty("spring.cloud.stream.test.binder.headers[2]"))
				.isEqualTo(Span.SPAN_ID_NAME);
	}

}
