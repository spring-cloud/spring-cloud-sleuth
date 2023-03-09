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

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
public class TracingKafkaReceiverTest {

	@Mock
	KafkaReceiver<String, String> sourceReceiver;

	@Mock
	ReactiveKafkaTracingPropagator reactiveKafkaTracingPropagator;

	@Test
	void should_wrap_delegate_kafka_receiver() {

		ReceiverOffset offset = Mockito.mock(ReceiverOffset.class);
		TracingKafkaReceiver<String, String> tracingReceiverTest = new TracingKafkaReceiver<>(
				reactiveKafkaTracingPropagator, sourceReceiver);

		String key = "foo";
		String value = "bar";

		Flux<ReceiverRecord<String, String>> recordsPublisher = Flux
				.just(new ReceiverRecord<>(new ConsumerRecord<>("topic", 0, 0, key, value), offset));

		Mockito.when(sourceReceiver.receive()).thenReturn(recordsPublisher);
		Mockito.when(reactiveKafkaTracingPropagator.propagateSpanContextToReactiveContext(Mockito.any()))
				.thenAnswer(invocation -> invocation.getArguments()[0]);

		StepVerifier.create(tracingReceiverTest.receive()).expectNextCount(1).expectComplete().verify();
	}

}
