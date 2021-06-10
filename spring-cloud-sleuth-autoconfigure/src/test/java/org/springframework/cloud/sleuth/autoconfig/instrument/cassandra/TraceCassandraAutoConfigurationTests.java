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

package org.springframework.cloud.sleuth.autoconfig.instrument.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.cassandra.TraceCqlSessionBuilderCustomizer;

class TraceCassandraAutoConfigurationTests {

	ApplicationContextRunner runner = new ApplicationContextRunner()
			.withPropertyValues("spring.sleuth.noop.enabled=true").withConfiguration(
					AutoConfigurations.of(TraceNoOpAutoConfiguration.class, TraceCassandraAutoConfiguration.class));

	@Test
	void should_register_cassandra_tracing_beans() {
		runner.run(context -> BDDAssertions.then(context).hasSingleBean(TraceCqlSessionBeanPostProcessor.class)
				.hasSingleBean(TraceCqlSessionBuilderCustomizer.class));
	}

	@Test
	void should_not_register_cassandra_tracing_beans_when_cassandra_not_present() {
		runner.withClassLoader(new FilteredClassLoader(CqlSession.class))
				.run(context -> BDDAssertions.then(context).doesNotHaveBean(TraceCqlSessionBeanPostProcessor.class)
						.doesNotHaveBean(TraceCqlSessionBuilderCustomizer.class));
	}

	@Test
	void should_not_register_cassandra_tracing_beans_when_cassandra_tracing_disabled() {
		runner.withPropertyValues("spring.sleuth.cassandra.enabled=false")
				.run(context -> BDDAssertions.then(context).doesNotHaveBean(TraceCqlSessionBeanPostProcessor.class)
						.doesNotHaveBean(TraceCqlSessionBuilderCustomizer.class));
	}

}
