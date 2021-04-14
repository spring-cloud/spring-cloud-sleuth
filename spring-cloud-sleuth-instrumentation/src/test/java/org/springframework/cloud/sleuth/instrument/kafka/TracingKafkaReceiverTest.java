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
		StepVerifier.create(tracingKafkaReceiver.receive())
				.expectNextMatches(Predicate.isEqual(receiverRecord))
				.verifyComplete();
		Mockito.verify(kafkaReceiver).receive();
	}

}
