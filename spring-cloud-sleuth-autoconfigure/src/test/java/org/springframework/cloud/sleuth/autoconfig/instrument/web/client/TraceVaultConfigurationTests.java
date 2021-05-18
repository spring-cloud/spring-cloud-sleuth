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

package org.springframework.cloud.sleuth.autoconfig.instrument.web.client;

import org.junit.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.vault.client.RestTemplateCustomizer;
import org.springframework.vault.client.WebClientCustomizer;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceVaultConfigurationTests {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withPropertyValues("spring.sleuth.noop.enabled=true").withConfiguration(
					AutoConfigurations.of(TraceNoOpAutoConfiguration.class, TraceWebClientAutoConfiguration.class));

	@Test
	public void shouldNotCreateVaultRestTemplateCustomizerWhenVaultNotOnClasspath() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(RestTemplateCustomizer.class))
				.run((context) -> assertThat(context).doesNotHaveBean(RestTemplateCustomizer.class));
	}

	@Test
	public void shouldNotCreateVaultWebClientCustomizerWhenVaultNotOnClasspath() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(WebClientCustomizer.class))
				.run((context) -> assertThat(context).doesNotHaveBean(WebClientCustomizer.class));
	}

	@Test
	public void shouldNotCreateVaultRestTemplateCustomizerWhenSleuthVaultDisabled() {
		this.contextRunner.withPropertyValues("spring.sleuth.vault.enabled=false")
				.run((context) -> assertThat(context).doesNotHaveBean(RestTemplateCustomizer.class));
	}

	@Test
	public void shouldNotCreateVaultWebClientCustomizerWhenSleuthVaultDisabled() {
		this.contextRunner.withPropertyValues("spring.sleuth.vault.enabled=false")
				.run((context) -> assertThat(context).doesNotHaveBean(WebClientCustomizer.class));
	}

	@Test
	public void shouldCreateVaultRestTemplateCustomizerWhenVaultNotOnClasspath() {
		this.contextRunner.run((context) -> assertThat(context).hasSingleBean(RestTemplateCustomizer.class));
	}

	@Test
	public void shouldCreateVaultWebClientCustomizerWhenVaultNotOnClasspath() {
		this.contextRunner.run((context) -> assertThat(context).hasSingleBean(WebClientCustomizer.class));
	}

}
