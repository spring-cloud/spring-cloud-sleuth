/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.instrument.messaging;

import brave.kafka.clients.KafkaTracing;
import org.apache.kafka.clients.producer.Producer;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.kafka.core.ProducerPostProcessor;

class TraceProducerPostProcessor<K, V> implements ProducerPostProcessor<K, V> {

	private final BeanFactory beanFactory;

	private KafkaTracing kafkaTracing;

	TraceProducerPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	private KafkaTracing kafkaTracing() {
		if (this.kafkaTracing == null) {
			this.kafkaTracing = this.beanFactory.getBean(KafkaTracing.class);
		}
		return this.kafkaTracing;
	}

	@Override
	public Producer<K, V> apply(Producer<K, V> kvProducer) {
		return kafkaTracing().producer(kvProducer);
	}

}
