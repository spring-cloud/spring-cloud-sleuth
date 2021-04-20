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

package org.springframework.cloud.sleuth.autoconfig.instrument.config;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.config.TraceEnvironmentRepositoryAspect;

class TraceSpringCloudConfigAutoConfigurationTests {

	@Test
	void should_register_the_aspect() {
		new ApplicationContextRunner().withPropertyValues("spring.sleuth.noop.enabled=true")
				.withBean(ConfigServerProperties.class)
				.withConfiguration(AutoConfigurations.of(TraceNoOpAutoConfiguration.class,
						TraceSpringCloudConfigAutoConfiguration.class))
				.run(context -> BDDAssertions.then(context).hasSingleBean(TraceEnvironmentRepositoryAspect.class));
	}

}
