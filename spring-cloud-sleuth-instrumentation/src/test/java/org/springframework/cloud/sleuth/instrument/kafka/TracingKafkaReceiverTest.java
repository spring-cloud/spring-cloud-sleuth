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

import java.util.function.Predicate;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.test.StepVerifier;

import org.springframework.cloud.sleuth.propagation.Propagator;

@ExtendWith(MockitoExtension.class)
public class TracingKafkaReceiverTest {

	@Mock
	KafkaReceiver<String, String> kafkaReceiver;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	Propagator propagator;

	@Test
	void should_delegate_receive_calls() {
		ReceiverOffset receiverOffset = BDDMockito.mock(ReceiverOffset.class);
		ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 1, "test-key", "test-value");
		ReceiverRecord<String, String> receiverRecord = new ReceiverRecord<>(record, receiverOffset);
		BDDMockito.given(kafkaReceiver.receive()).willReturn(Flux.just(receiverRecord));
		TracingKafkaReceiver<String, String> tracingKafkaReceiver = new TracingKafkaReceiver<>(kafkaReceiver,
				propagator, new TracingKafkaPropagatorGetter());

		StepVerifier.create(tracingKafkaReceiver.receive()).expectNextMatches(Predicate.isEqual(receiverRecord))
				.verifyComplete();

		Mockito.verify(kafkaReceiver).receive();
	}

}
