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

package org.springframework.cloud.sleuth.autoconfig.instrument.web;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class TraceTomcatConfigurationTests {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withPropertyValues("spring.sleuth.noop.enabled=true").withUserConfiguration(TestConfig.class)
			.withConfiguration(
					AutoConfigurations.of(TraceWebServletConfiguration.class, TraceNoOpAutoConfiguration.class));

	@Test
	public void should_not_register_customizer_when_tomcat_not_present() throws Exception {
		contextRunner.withClassLoader(new FilteredClassLoader("org.apache.catalina.Valve")).run(context -> BDDAssertions
				.then(context).doesNotHaveBean(TraceWebServletConfiguration.TraceTomcatConfiguration.CUSTOMIZER_NAME));
	}

	@Test
	public void should_not_register_customizer_when_tomcat_customizer_not_present() throws Exception {
		contextRunner
				.withClassLoader(new FilteredClassLoader(
						"org.springframework.boot.web.embedded.tomcat.ConfigurableTomcatWebServerFactory"))
				.run(context -> BDDAssertions.then(context)
						.doesNotHaveBean(TraceWebServletConfiguration.TraceTomcatConfiguration.CUSTOMIZER_NAME));
	}

	@Test
	public void should_not_register_customizer_when_tomcat_disabled() throws Exception {
		contextRunner.withPropertyValues("spring.sleuth.web.tomcat.enabled=false").run(context -> BDDAssertions
				.then(context).doesNotHaveBean(TraceWebServletConfiguration.TraceTomcatConfiguration.CUSTOMIZER_NAME));
	}

	@Test
	public void should_not_register_customizer_when_servlet_disabled() throws Exception {
		contextRunner.withPropertyValues("spring.sleuth.web.servlet.enabled=false").run(context -> BDDAssertions
				.then(context).doesNotHaveBean(TraceWebServletConfiguration.TraceTomcatConfiguration.CUSTOMIZER_NAME));
	}

	@Test
	public void should_not_register_customizer_when_web_disabled() throws Exception {
		contextRunner.withPropertyValues("spring.sleuth.web.enabled=false").run(context -> BDDAssertions.then(context)
				.doesNotHaveBean(TraceWebServletConfiguration.TraceTomcatConfiguration.CUSTOMIZER_NAME));
	}

	@Test
	public void should_register_customizer_when_tomcat_present() throws Exception {
		contextRunner.run(context -> BDDAssertions.then(context)
				.hasBean(TraceWebServletConfiguration.TraceTomcatConfiguration.CUSTOMIZER_NAME));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(SleuthWebProperties.class)
	static class TestConfig {

	}

}
