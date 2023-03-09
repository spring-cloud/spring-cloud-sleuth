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

package org.springframework.cloud.sleuth.instrument.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.internals.ConsumerFactory;

import org.springframework.beans.factory.BeanFactory;

/**
 * This decorates a Reactor Kafka {@link ConsumerFactory} to create decorated consumers of
 * type {@link TracingKafkaConsumer}. This can be used by the {@link KafkaReceiver}
 * factory methods to create instrumented receivers.
 *
 * @author Anders Clausen
 * @author Flaviu Muresan
 * @since 3.1.0
 * @deprecated Please use {@link TracingKafkaReceiver} that leverages
 * {@link ReactiveKafkaTracingPropagator}
 */
public class TracingKafkaConsumerFactory extends ConsumerFactory {

	private final BeanFactory beanFactory;

	public TracingKafkaConsumerFactory(BeanFactory beanFactory) {
		super();
		this.beanFactory = beanFactory;
	}

	@Override
	public <K, V> Consumer<K, V> createConsumer(ReceiverOptions<K, V> receiverOptions) {
		return new TracingKafkaConsumer<>(super.createConsumer(receiverOptions), beanFactory);
	}

}
