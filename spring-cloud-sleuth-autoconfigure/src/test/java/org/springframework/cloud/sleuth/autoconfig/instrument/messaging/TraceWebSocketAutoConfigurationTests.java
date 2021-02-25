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

package org.springframework.cloud.sleuth.autoconfig.instrument.messaging;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class TraceWebSocketAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.sleuth.noop.enabled=true").withConfiguration(
					AutoConfigurations.of(TraceNoOpAutoConfiguration.class, TraceSpringMessagingAutoConfiguration.class,
							TraceSpringIntegrationAutoConfiguration.class, TraceWebSocketAutoConfiguration.class));

	@Test
	void should_inject_beans_for_getter_setter_messaging_propagation() {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(TraceWebSocketAutoConfiguration.class));
	}

}
