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

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Not using {@linkplain SpringBootTest} as we need to change properties per test.
 *
 * @author Adrian Cole
 */
public class ZipkinSamplerTests {

	@Test
	void should_set_sampler_to_non_never_when_zipkin_handler_on_classpath_for_brave() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
				.withPropertyValues("spring.sleuth.tracer.mode=BRAVE")
				.withConfiguration(AutoConfigurations.of(TestConfig.class));

		contextRunner.run(context -> {
			brave.sampler.Sampler sampler = context.getBean(brave.sampler.Sampler.class);
			BDDAssertions.then(sampler).isNotSameAs(brave.sampler.Sampler.NEVER_SAMPLE);
		});
	}

	@Test
	void should_set_sampler_to_non_off_when_zipkin_handler_on_classpath_for_otel() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
				.withPropertyValues("spring.sleuth.tracer.mode=OTEL")
				.withConfiguration(AutoConfigurations.of(TestConfig.class));

		contextRunner.run(context -> {
			io.opentelemetry.sdk.trace.samplers.Sampler sampler = context
					.getBean(io.opentelemetry.sdk.trace.samplers.Sampler.class);
			BDDAssertions.then(sampler).isNotSameAs(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOff());
		});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class })
	static class TestConfig {

	}

}
