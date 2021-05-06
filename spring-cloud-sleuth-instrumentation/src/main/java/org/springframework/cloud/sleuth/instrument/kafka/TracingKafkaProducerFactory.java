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

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.internals.ProducerFactory;

import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.propagation.Propagator;

/**
 * This decorates a Reactor Kafka {@link ProducerFactory} to create decorated producers of
 * type {@link TracingKafkaProducer}. This can be used by the {@link KafkaSender} factory
 * methods to create instrumented senders.
 *
 * @author Anders Clausen
 * @author Flaviu Muresan
 * @since 3.1.0
 */
public class TracingKafkaProducerFactory extends ProducerFactory {

	private final Tracer tracer;

	private final Propagator propagator;

	private final Propagator.Setter<ProducerRecord<?, ?>> injector;

	public TracingKafkaProducerFactory(Tracer tracer, Propagator propagator,
			Propagator.Setter<ProducerRecord<?, ?>> injector) {
		super();
		this.tracer = tracer;
		this.propagator = propagator;
		this.injector = injector;
	}

	@Override
	public <K, V> Producer<K, V> createProducer(SenderOptions<K, V> senderOptions) {
		return new TracingKafkaProducer<>(super.createProducer(senderOptions), tracer, propagator, injector);
	}

}
