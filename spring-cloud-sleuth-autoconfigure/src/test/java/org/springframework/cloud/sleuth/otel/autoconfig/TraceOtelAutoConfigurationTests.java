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

package org.springframework.cloud.sleuth.otel.autoconfig;

import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class TraceOtelAutoConfigurationTests {

	@Test
	void should_start_context_with_otel_tracer_when_sleuth_enabled() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withPropertyValues("spring.sleuth.tracer.mode=OTEL").withUserConfiguration(Config.class);

		runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(Tracer.class));
	}

	@Test
	void should_start_context_without_tracer_when_sleuth_disabled() {
		ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class)
				.withPropertyValues("spring.sleuth.enabled=false");

		runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(Tracer.class));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class })
	static class Config {

	}

}
