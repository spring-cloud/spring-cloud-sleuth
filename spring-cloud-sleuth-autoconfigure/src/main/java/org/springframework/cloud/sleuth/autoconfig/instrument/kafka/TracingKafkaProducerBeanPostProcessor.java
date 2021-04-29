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

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaProducer;
import org.springframework.cloud.sleuth.propagation.Propagator;

/**
 * Bean post processor for {@link org.apache.kafka.clients.producer.Producer}.
 *
 * @author Anders Clausen
 * @author Flaviu Muresan
 * @since 3.1.0
 */
public class TracingKafkaProducerBeanPostProcessor implements BeanPostProcessor {

	private final Tracer tracer;

	private final Propagator propagator;

	private final Propagator.Setter<ProducerRecord<?, ?>> injector;

	TracingKafkaProducerBeanPostProcessor(Tracer tracer, Propagator propagator,
			Propagator.Setter<ProducerRecord<?, ?>> injector) {
		this.tracer = tracer;
		this.propagator = propagator;
		this.injector = injector;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof Producer) {
			return new TracingKafkaProducer<>((Producer) bean, tracer, propagator, injector);
		}
		return bean;
	}

}
