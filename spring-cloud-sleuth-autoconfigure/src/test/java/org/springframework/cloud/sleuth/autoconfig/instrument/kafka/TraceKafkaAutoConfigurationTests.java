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

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaConsumer;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaProducer;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaPropagatorGetter;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaPropagatorSetter;

import static org.assertj.core.api.Assertions.assertThat;

class TraceKafkaAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.sleuth.noop.enabled=true")
			.withConfiguration(AutoConfigurations.of(TraceNoOpAutoConfiguration.class,
					TracingKafkaAutoConfiguration.class, TracingReactorKafkaAutoConfiguration.class));

	@Test
	void should_inject_beans_for_getter_setter_kafka_propagation() {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(TracingKafkaPropagatorGetter.class)
				.hasSingleBean(TracingKafkaPropagatorSetter.class));
	}

	@Test
	void should_decorate_kafka_producer() {
		this.contextRunner.withBean(Producer.class, MockProducer::new)
				.run(context -> assertThat(context).hasSingleBean(TracingKafkaProducer.class));
	}

	@Test
	void should_decorate_kafka_consumer() {
		this.contextRunner.withBean(Consumer.class, () -> new MockConsumer<>(OffsetResetStrategy.NONE))
				.run(context -> assertThat(context).hasSingleBean(TracingKafkaConsumer.class));
	}

	@Test
	void should_not_decorate_tracing_kafka_consumer() {
		TracingKafkaConsumer<String, String> kafkaConsumer = new TracingKafkaConsumer<>(
				new MockConsumer<>(OffsetResetStrategy.NONE), null);
		this.contextRunner.withBean(TracingKafkaConsumer.class, () -> kafkaConsumer)
				.run(context -> assertThat(context).getBean(Consumer.class).isEqualTo(kafkaConsumer));
	}

	@Test
	void should_not_decorate_tracing_kafka_producer() {
		TracingKafkaProducer<String, String> kafkaProducer = new TracingKafkaProducer<>(new MockProducer<>(), null);
		this.contextRunner.withBean(TracingKafkaProducer.class, () -> kafkaProducer)
				.run(context -> assertThat(context).getBean(Producer.class).isEqualTo(kafkaProducer));
	}

}
