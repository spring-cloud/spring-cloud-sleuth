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

package org.springframework.cloud.sleuth.propagation;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

public class SleuthTagPropagationAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(
					AutoConfigurations.of(SleuthTagPropagationAutoConfiguration.class));

	@Test
	public void shouldCreateHandlerByDefault() {
		this.contextRunner.withUserConfiguration(TraceAutoConfiguration.class)
				.run((context) -> {
					assertThat(context)
							.hasSingleBean(TagPropagationFinishedSpanHandler.class);
				});
	}

	@Test
	public void shouldNotCreateHandlerByDisablingIt() {
		this.contextRunner
				.withPropertyValues(
						"spring.sleuth.propagation.tag.whitelisted-keys=some-key")
				.withPropertyValues("spring.sleuth.propagation.tag.enabled=false")
				.withUserConfiguration(TraceAutoConfiguration.class).run((context) -> {
					assertThat(context)
							.doesNotHaveBean(TagPropagationFinishedSpanHandler.class);
				});
	}

	@Test
	public void shouldCreateHandler() {
		this.contextRunner
				.withPropertyValues(
						"spring.sleuth.propagation.tag.whitelisted-keys=some-key")
				.withUserConfiguration(TraceAutoConfiguration.class).run((context) -> {
					assertThat(context)
							.hasSingleBean(TagPropagationFinishedSpanHandler.class);
				});
	}

	@Test
	public void shouldCreateHandlerWithYml() {
		this.contextRunner.withPropertyValues("spring.profiles.active=tag-propagation")
				.withUserConfiguration(TraceAutoConfiguration.class).run((context) -> {
					assertThat(context)
							.hasSingleBean(TagPropagationFinishedSpanHandler.class);
				});
	}

}
