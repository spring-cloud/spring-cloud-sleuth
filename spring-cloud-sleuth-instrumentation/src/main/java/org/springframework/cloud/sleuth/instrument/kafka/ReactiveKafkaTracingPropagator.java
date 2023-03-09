/*
 * Copyright 2013-2023 the original author or authors.
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

import org.apache.kafka.clients.consumer.ConsumerRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;
import org.springframework.cloud.sleuth.propagation.Propagator;

/**
 * Uses {@link ReactorSleuth} to create separate mono publisher for each element in flux,
 * that will be injecting the tracing context to {@link Tracer} and
 * {@link reactor.util.context.Context} for each element separately, giving downstream
 * operators proper tracing context and span.
 *
 * @see TracingKafkaReceiver
 */
public class ReactiveKafkaTracingPropagator {

	private final Tracer tracer;

	private final Propagator propagator;

	private final Propagator.Getter<ConsumerRecord<?, ?>> extractor;

	public ReactiveKafkaTracingPropagator(Tracer tracer, Propagator propagator,
			Propagator.Getter<ConsumerRecord<?, ?>> extractor) {
		this.tracer = tracer;
		this.propagator = propagator;
		this.extractor = extractor;
	}

	public <K, V, T extends ConsumerRecord<K, V>> Flux<T> propagateSpanContextToReactiveContext(Flux<T> publisher) {
		return publisher.flatMap(consumerRecord -> Mono.deferContextual((contextView) -> {
			Span newSpanWithParent = propagator.extract(consumerRecord, extractor).kind(Span.Kind.CONSUMER)
					.name("kafka.consumer").tag("kafka.topic", consumerRecord.topic())
					.tag("kafka.offset", Long.toString(consumerRecord.offset()))
					.tag("kafka.partition", Integer.toString(consumerRecord.partition())).start();

			return ReactorSleuth.tracedMono(tracer, newSpanWithParent, () -> Mono.just(consumerRecord));
		}));
	}

}
