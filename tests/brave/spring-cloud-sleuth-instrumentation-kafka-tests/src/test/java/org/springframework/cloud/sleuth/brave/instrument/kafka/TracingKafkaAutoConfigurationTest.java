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

package org.springframework.cloud.sleuth.brave.instrument.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaConsumer;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaProducer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.BDDAssertions.then;

@SpringBootTest(classes = TracingKafkaAutoConfigurationTest.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TracingKafkaAutoConfigurationTest {

	@Autowired
	Consumer<String, String> kafkaConsumer;

	@Autowired
	Producer<String, String> kafkaProducer;

	@Test
	public void should_wrap_kafka_consumer() {
		then(this.kafkaConsumer).isNotNull();
		then(this.kafkaConsumer).isInstanceOf(TracingKafkaConsumer.class);
	}

	@Test
	public void should_wrap_kafka_producer() {
		then(this.kafkaProducer).isNotNull();
		then(this.kafkaProducer).isInstanceOf(TracingKafkaProducer.class);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	protected static class Config {

		@Bean
		Consumer<String, String> kafkaConsumer() {
			return new MockConsumer<>(OffsetResetStrategy.NONE);
		}

		@Bean
		Producer<String, String> kafkaProducer() {
			return new MockProducer<>();
		}

	}

}
