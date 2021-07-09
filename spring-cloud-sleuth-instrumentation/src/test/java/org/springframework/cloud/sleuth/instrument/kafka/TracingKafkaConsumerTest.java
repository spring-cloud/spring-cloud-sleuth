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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.sleuth.propagation.Propagator;

import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class TracingKafkaConsumerTest {

	@Mock
	KafkaConsumer<String, String> kafkaConsumer;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	Propagator propagator;

	@Mock
	Propagator.Getter<ConsumerRecord<?, ?>> extractor;

	@Test
	void should_delegate_poll_calls() {
		Duration pollTimeout = Duration.of(5, ChronoUnit.SECONDS);
		ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 1, "test-key", "test-value");
		Map<TopicPartition, List<ConsumerRecord<String, String>>> map = new HashMap<>();
		map.put(new TopicPartition("topic", 0), Collections.singletonList(record));
		ConsumerRecords<String, String> records = new ConsumerRecords<>(map);
		BDDMockito.given(kafkaConsumer.poll(pollTimeout)).willReturn(records);
		TracingKafkaConsumer<String, String> tracingKafkaConsumer = new TracingKafkaConsumer<>(kafkaConsumer,
				beanFactory());

		tracingKafkaConsumer.poll(pollTimeout);

		Mockito.verify(kafkaConsumer).poll(eq(pollTimeout));
	}

	private BeanFactory beanFactory() {
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
		beanFactory.addBean("propagator", this.propagator);
		beanFactory.addBean("extractor", this.extractor);
		return beanFactory;
	}

}
