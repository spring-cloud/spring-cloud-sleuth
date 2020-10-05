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

import io.opentelemetry.trace.Tracer;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class TraceOtelAutoConfigurationTests {

	@Test
	void should_start_context_with_otel_tracer() {
		ApplicationContextRunner runner = new ApplicationContextRunner().withConfiguration(otelConfiguration());

		runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(Tracer.class));
	}

	@Test
	void should_start_context_with_noop_tracer_when_property_set() {
		ApplicationContextRunner runner = new ApplicationContextRunner().withConfiguration(otelConfiguration())
				.withPropertyValues("spring.sleuth.enabled=false");

		runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(Tracer.class));
	}

	private AutoConfigurations otelConfiguration() {
		return AutoConfigurations.of(TraceAutoConfiguration.class, TraceOtelAutoConfiguration.class);
	}

	@Configuration
	@EnableAutoConfiguration
	static class Config {

	}

}
