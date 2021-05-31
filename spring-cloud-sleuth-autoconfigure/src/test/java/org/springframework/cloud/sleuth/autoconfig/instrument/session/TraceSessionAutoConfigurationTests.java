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

package org.springframework.cloud.sleuth.autoconfig.instrument.session;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.session.TraceSessionRepositoryAspect;
import org.springframework.session.SessionRepository;

class TraceSessionAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.sleuth.noop.enabled=true").withConfiguration(
					AutoConfigurations.of(TraceNoOpAutoConfiguration.class, TraceSessionAutoConfiguration.class));

	@Test
	void should_register_session_aspect() {
		this.contextRunner
				.run(context -> Assertions.assertThat(context).hasSingleBean(TraceSessionRepositoryAspect.class));
	}

	@Test
	void should_not_register_session_aspect_when_session_repository_not_on_classpath() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(SessionRepository.class))
				.run(context -> Assertions.assertThat(context).doesNotHaveBean(TraceSessionRepositoryAspect.class));
	}

	@Test
	void should_not_register_session_aspect_when_session_instrumentation_is_disabled() {
		this.contextRunner.withPropertyValues("spring.sleuth.session.enabled=false")
				.run(context -> Assertions.assertThat(context).doesNotHaveBean(TraceSessionRepositoryAspect.class));
	}

}
