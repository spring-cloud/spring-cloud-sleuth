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

package org.springframework.cloud.sleuth.otel.instrument.opentracing;

import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.otel.autoconfig.OtelAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class OpentracingAutoConfigurationTests {

	@Test
	void should_start_context_with_otel_tracer_when_sleuth_enabled() {
		ApplicationContextRunner runner = withAutoConfiguration();

		runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(Tracer.class)
				.hasSingleBean(io.opentracing.Tracer.class));
	}

	private ApplicationContextRunner withAutoConfiguration() {
		return new ApplicationContextRunner().withPropertyValues("spring.sleuth.tracer.mode=OTEL").withConfiguration(
				AutoConfigurations.of(OtelAutoConfiguration.class, OpentracingAutoConfiguration.class));
	}

	@Test
	void should_start_context_without_tracer_when_sleuth_disabled() {
		ApplicationContextRunner runner = withAutoConfiguration()
				.withPropertyValues("spring.sleuth.opentracing.enabled=false");

		runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(Tracer.class)
				.doesNotHaveBean(io.opentracing.Tracer.class));
	}

}
