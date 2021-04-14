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

package org.springframework.cloud.sleuth.instrument.kafka;

import java.util.function.Function;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.TransactionManager;

import org.springframework.cloud.sleuth.propagation.Propagator;

public class TracingKafkaReceiver<K, V> implements KafkaReceiver<K, V> {

	private final KafkaReceiver<K, V> delegate;

	private final Propagator propagator;

	private final Propagator.Getter<ConsumerRecord<?, ?>> extractor;

	public TracingKafkaReceiver(KafkaReceiver<K, V> receiver, Propagator propagator,
			Propagator.Getter<ConsumerRecord<?, ?>> getter) {
		this.delegate = receiver;
		this.propagator = propagator;
		this.extractor = getter;
	}

	@Override
	public Flux<ReceiverRecord<K, V>> receive() {
		return buildAndFinishSpanOnNextReceiverRecord(this.delegate.receive());
	}

	@Override
	public Flux<Flux<ConsumerRecord<K, V>>> receiveAutoAck() {
		return this.delegate.receiveAutoAck().map(this::buildAndFinishSpanOnNextConsumerRecord);
	}

	@Override
	public Flux<ConsumerRecord<K, V>> receiveAtmostOnce() {
		return this.buildAndFinishSpanOnNextConsumerRecord(this.delegate.receiveAtmostOnce());
	}

	@Override
	public Flux<Flux<ConsumerRecord<K, V>>> receiveExactlyOnce(TransactionManager transactionManager) {
		return this.delegate.receiveExactlyOnce(transactionManager).map(this::buildAndFinishSpanOnNextConsumerRecord);
	}

	@Override
	public <T> Mono<T> doOnConsumer(Function<Consumer<K, V>, ? extends T> function) {
		return this.delegate.doOnConsumer(function);
	}

	private Flux<ConsumerRecord<K, V>> buildAndFinishSpanOnNextConsumerRecord(Flux<ConsumerRecord<K, V>> flux) {
		return flux.doOnNext(consumerRecord -> KafkaTracingUtils.buildAndFinishSpan(consumerRecord, this.propagator,
				this.extractor));
	}

	private Flux<ReceiverRecord<K, V>> buildAndFinishSpanOnNextReceiverRecord(Flux<ReceiverRecord<K, V>> flux) {
		return flux.doOnNext(consumerRecord -> KafkaTracingUtils.buildAndFinishSpan(consumerRecord, this.propagator,
				this.extractor));
	}

}
