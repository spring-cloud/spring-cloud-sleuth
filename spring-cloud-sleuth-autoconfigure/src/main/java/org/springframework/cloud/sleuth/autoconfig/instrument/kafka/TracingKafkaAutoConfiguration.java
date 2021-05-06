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

import org.apache.kafka.clients.KafkaClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaConsumerFactory;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaProducerFactory;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaPropagatorGetter;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaPropagatorSetter;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that registers instrumentation for Kafka.
 *
 * @author Anders Clausen
 * @author Flaviu Muresan
 * @since 3.1.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(KafkaClient.class)
@ConditionalOnBean(Tracer.class)
@ConditionalOnProperty(value = "spring.sleuth.kafka.enabled", matchIfMissing = true)
@AutoConfigureAfter(BraveAutoConfiguration.class)
public class TracingKafkaAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	Propagator.Setter<ProducerRecord<?, ?>> tracingKafkaPropagationSetter() {
		return new TracingKafkaPropagatorSetter();
	}

	@Bean
	@ConditionalOnMissingBean
	Propagator.Getter<ConsumerRecord<?, ?>> tracingKafkaPropagationGetter() {
		return new TracingKafkaPropagatorGetter();
	}

	@Bean
	@ConditionalOnMissingBean
	TracingKafkaProducerFactory tracingKafkaProducerFactory(Tracer tracer, Propagator propagator,
			Propagator.Setter<ProducerRecord<?, ?>> injector) {
		return new TracingKafkaProducerFactory(tracer, propagator, injector);
	}

	@Bean
	@ConditionalOnMissingBean
	TracingKafkaConsumerFactory tracingKafkaConsumerFactory(Propagator propagator,
			Propagator.Getter<ConsumerRecord<?, ?>> extractor) {
		return new TracingKafkaConsumerFactory(propagator, extractor);
	}

	@Bean
	static TracingKafkaProducerBeanPostProcessor tracingKafkaProducerBeanPostProcessor(Tracer tracer,
			Propagator propagator, Propagator.Setter<ProducerRecord<?, ?>> injector) {
		return new TracingKafkaProducerBeanPostProcessor(tracer, propagator, injector);
	}

	@Bean
	static TracingKafkaConsumerBeanPostProcessor tracingKafkaConsumerBeanPostProcessor(Propagator propagator,
			Propagator.Getter<ConsumerRecord<?, ?>> extractor) {
		return new TracingKafkaConsumerBeanPostProcessor(propagator, extractor);
	}

}
