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

package org.springframework.cloud.sleuth.autoconfig.instrument.tx;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;

class TraceTxAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.sleuth.noop.enabled=true")
			.withConfiguration(AutoConfigurations.of(TraceNoOpAutoConfiguration.class, TraceTxAutoConfiguration.class));

	@Test
	void should_register_bean_post_processors() {
		this.contextRunner.run(context -> Assertions.assertThat(context)
				.hasSingleBean(TraceKafkaPlatformTransactionManagerBeanPostProcessor.class)
				.doesNotHaveBean(TracePlatformTransactionManagerBeanPostProcessor.class)
				.hasSingleBean(TraceReactiveTransactionManagerBeanPostProcessor.class));
	}

	@Test
	void should_register_non_kafka_bean_post_processors_when_kafka_not_on_classpath() {
		this.contextRunner
				.withClassLoader(
						new FilteredClassLoader("org.springframework.kafka.transaction.KafkaAwareTransactionManager"))
				.run(context -> Assertions.assertThat(context)
						.doesNotHaveBean(TraceKafkaPlatformTransactionManagerBeanPostProcessor.class)
						.hasSingleBean(TracePlatformTransactionManagerBeanPostProcessor.class)
						.hasSingleBean(TraceReactiveTransactionManagerBeanPostProcessor.class));
	}

	@Test
	void should_not_register_bean_post_processor_when_tx_not_on_classpath() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(PlatformTransactionManager.class))
				.run(context -> Assertions.assertThat(context)
						.doesNotHaveBean(TracePlatformTransactionManagerBeanPostProcessor.class));
	}

	@Test
	void should_not_register_reactive_bean_post_processor_when_reactive_tx_not_on_classpath() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(ReactiveTransactionManager.class))
				.run(context -> Assertions.assertThat(context)
						.doesNotHaveBean(TraceReactiveTransactionManagerBeanPostProcessor.class));
	}

	@Test
	void should_not_register_reactive_bean_post_processor_when_reactor_not_on_classpath() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Mono.class)).run(context -> Assertions
				.assertThat(context).doesNotHaveBean(TraceReactiveTransactionManagerBeanPostProcessor.class));
	}

}
