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

package org.springframework.cloud.sleuth.autoconfig.actuate;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TraceSleuthActuatorAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("management.endpoints.web.exposure.include=traces")
			.withConfiguration(AutoConfigurations.of(TraceSleuthActuatorAutoConfiguration.class));

	@Test
	void should_register_actuator_by_default() {
		this.contextRunner.run(context -> BDDAssertions.then(context).hasSingleBean(TracesScrapeEndpoint.class));
	}

	@Test
	void should_not_register_actuator_when_endpoint_disabled() {
		this.contextRunner.withPropertyValues("management.endpoint.traces.enabled=false")
				.run(context -> BDDAssertions.then(context).doesNotHaveBean(TracesScrapeEndpoint.class));
	}

	@Test
	void should_not_register_actuator_when_sleuth_disabled() {
		this.contextRunner.withPropertyValues("spring.sleuth.enabled=false")
				.run(context -> BDDAssertions.then(context).doesNotHaveBean(TracesScrapeEndpoint.class));
	}

}
