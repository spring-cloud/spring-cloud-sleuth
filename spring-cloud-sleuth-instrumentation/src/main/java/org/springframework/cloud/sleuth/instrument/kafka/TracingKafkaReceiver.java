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

import java.util.function.Function;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.receiver.internals.ConsumerFactory;
import reactor.kafka.sender.TransactionManager;

/**
 * Decorator for {@link KafkaReceiver} that delegates most of the work back to original
 * consumer, but returns publishers decorated with tracing context per each element.
 *
 * @author Maciej Gromuł
 * @see ReactiveKafkaTracingPropagator
 */
public class TracingKafkaReceiver<K, V> implements KafkaReceiver<K, V> {

	private final ReactiveKafkaTracingPropagator reactiveKafkaTracingPropagator;

	private final KafkaReceiver<K, V> delegate;

	public TracingKafkaReceiver(ReactiveKafkaTracingPropagator reactiveKafkaTracingPropagator,
			KafkaReceiver<K, V> delegate) {
		this.reactiveKafkaTracingPropagator = reactiveKafkaTracingPropagator;
		this.delegate = delegate;
	}

	public static <K, V> KafkaReceiver<K, V> create(ReactiveKafkaTracingPropagator reactiveKafkaTracingPropagator,
			ReceiverOptions<K, V> options) {
		return new TracingKafkaReceiver<>(reactiveKafkaTracingPropagator,
				KafkaReceiver.create(ConsumerFactory.INSTANCE, options));
	}

	public static <K, V> KafkaReceiver<K, V> create(ReactiveKafkaTracingPropagator reactiveKafkaTracingPropagator,
			ConsumerFactory factory, ReceiverOptions<K, V> options) {
		return new TracingKafkaReceiver<>(reactiveKafkaTracingPropagator, KafkaReceiver.create(factory, options));
	}

	@Override
	public Flux<ReceiverRecord<K, V>> receive(Integer prefetch) {
		return delegate.receive(prefetch)
				.transformDeferred(reactiveKafkaTracingPropagator::propagateSpanContextToReactiveContext);
	}

	@Override
	public Flux<ReceiverRecord<K, V>> receive() {
		return delegate.receive()
				.transformDeferred(reactiveKafkaTracingPropagator::propagateSpanContextToReactiveContext);
	}

	@Override
	public Flux<Flux<ConsumerRecord<K, V>>> receiveAutoAck(Integer prefetch) {
		return delegate.receiveAutoAck(prefetch)
				.map(reactiveKafkaTracingPropagator::propagateSpanContextToReactiveContext);
	}

	@Override
	public Flux<Flux<ConsumerRecord<K, V>>> receiveAutoAck() {
		return delegate.receiveAutoAck().map(reactiveKafkaTracingPropagator::propagateSpanContextToReactiveContext);
	}

	@Override
	public Flux<ConsumerRecord<K, V>> receiveAtmostOnce(Integer prefetch) {
		return delegate.receiveAtmostOnce(prefetch)
				.transformDeferred(reactiveKafkaTracingPropagator::propagateSpanContextToReactiveContext);
	}

	@Override
	public Flux<ConsumerRecord<K, V>> receiveAtmostOnce() {
		return delegate.receiveAtmostOnce()
				.transformDeferred(reactiveKafkaTracingPropagator::propagateSpanContextToReactiveContext);
	}

	@Override
	public Flux<Flux<ConsumerRecord<K, V>>> receiveExactlyOnce(TransactionManager transactionManager,
			Integer prefetch) {
		return delegate.receiveExactlyOnce(transactionManager, prefetch);
	}

	@Override
	public <T> Mono<T> doOnConsumer(Function<Consumer<K, V>, ? extends T> function) {
		return delegate.doOnConsumer(function);
	}

}
