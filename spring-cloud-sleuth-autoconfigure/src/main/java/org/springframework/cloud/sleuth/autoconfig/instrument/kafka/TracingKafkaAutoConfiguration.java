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

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
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
@AutoConfigureAfter(BraveAutoConfiguration.class)
@ConditionalOnProperty(value = "spring.sleuth.kafka.enabled", matchIfMissing = true)
public class TracingKafkaAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(value = ProducerRecord.class, parameterizedContainer = Propagator.Setter.class)
	Propagator.Setter<ProducerRecord<?, ?>> tracingKafkaPropagationSetter() {
		return new TracingKafkaPropagatorSetter();
	}

	@Bean
	@ConditionalOnMissingBean(value = ConsumerRecord.class, parameterizedContainer = Propagator.Getter.class)
	Propagator.Getter<ConsumerRecord<?, ?>> tracingKafkaPropagationGetter() {
		return new TracingKafkaPropagatorGetter();
	}

	@Bean
	static TracingKafkaProducerBeanPostProcessor tracingKafkaProducerBeanPostProcessor(BeanFactory beanFactory) {
		return new TracingKafkaProducerBeanPostProcessor(beanFactory);
	}

	@Bean
	static TracingKafkaConsumerBeanPostProcessor tracingKafkaConsumerBeanPostProcessor(BeanFactory beanFactory) {
		return new TracingKafkaConsumerBeanPostProcessor(beanFactory);
	}

}
