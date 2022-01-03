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

package org.springframework.cloud.sleuth.autoconfig.instrument.kafka;

import java.util.ArrayList;
import java.util.List;

import brave.kafka.clients.KafkaTracing;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaAspect;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ConsumerPostProcessor;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.ProducerPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class SpringKafkaAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.sleuth.noop.enabled=true").withConfiguration(
					AutoConfigurations.of(TraceNoOpAutoConfiguration.class, TracingKafkaAutoConfiguration.class,
							TracingReactorKafkaAutoConfiguration.class, SpringKafkaAutoConfiguration.class));

	@Test
	void should_be_disabled_when_brave_on_classpath() {
		this.contextRunner.run((context) -> assertThat(context)
				.doesNotHaveBean(SpringKafkaFactoryBeanPostProcessor.class).doesNotHaveBean(TracingKafkaAspect.class));
	}

	@Test
	void should_decorate_spring_kafka_producer_factory() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(KafkaTracing.class))
				.withBean(ProducerFactory.class, TestProducerFactory::new)
				.run(context -> assertThat(context).getBean(ProducerFactory.class)
						.extracting(ProducerFactory::getPostProcessors).matches(postProcessors -> postProcessors
								.stream().filter(p -> p instanceof SpringKafkaProducerPostProcessor).count() == 1));
	}

	@Test
	void should_decorate_spring_kafka_consumer_factory() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(KafkaTracing.class))
				.withBean(ConsumerFactory.class, TestConsumerFactory::new)
				.run(context -> assertThat(context).getBean(ConsumerFactory.class)
						.extracting(ConsumerFactory::getPostProcessors).matches(postProcessors -> postProcessors
								.stream().filter(p -> p instanceof SpringKafkaConsumerPostProcessor).count() == 1));
	}

	@Test
	void should_register_tracing_kafka_aspect() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(KafkaTracing.class))
				.run((context) -> assertThat(context).hasSingleBean(TracingKafkaAspect.class));
	}

	class TestConsumerFactory implements ConsumerFactory {

		List<ConsumerPostProcessor> postProcessors = new ArrayList<>();

		@Override
		public Consumer createConsumer(String groupId, String clientIdPrefix, String clientIdSuffix) {
			return null;
		}

		@Override
		public boolean isAutoCommit() {
			return false;
		}

		@Override
		public void addPostProcessor(ConsumerPostProcessor postProcessor) {
			this.postProcessors.add(postProcessor);
		}

		@Override
		public List<ConsumerPostProcessor> getPostProcessors() {
			return this.postProcessors;
		}

	}

	class TestProducerFactory implements ProducerFactory {

		List<ProducerPostProcessor> postProcessors = new ArrayList<>();

		@Override
		public Producer createProducer() {
			return null;
		}

		@Override
		public void addPostProcessor(ProducerPostProcessor postProcessor) {
			this.postProcessors.add(postProcessor);
		}

		@Override
		public List<ProducerPostProcessor> getPostProcessors() {
			return this.postProcessors;
		}

	}

}
