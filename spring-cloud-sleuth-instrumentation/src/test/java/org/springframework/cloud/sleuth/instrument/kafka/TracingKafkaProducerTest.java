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

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class TracingKafkaProducerTest {

	@Mock
	KafkaProducer<String, String> kafkaProducer;

	@Mock
	Propagator propagator;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	Tracer tracer;

	@Test
	void should_delegate_send_calls() {
		ProducerRecord<String, String> testRecord = new ProducerRecord<>("test", "test");
		Callback callback = (record, ex) -> {
		};
		TracingKafkaProducer<String, String> tracingKafkaProducer = new TracingKafkaProducer<>(kafkaProducer,
				beanFactory());

		tracingKafkaProducer.send(testRecord, callback);

		Mockito.verify(kafkaProducer).send(eq(testRecord), any());
	}

	@Test
	void should_wrap_user_callback_on_send() {
		ProducerRecord<String, String> testRecord = new ProducerRecord<>("test", "test");
		Callback callback = (record, ex) -> {
		};
		TracingKafkaProducer<String, String> tracingKafkaProducer = new TracingKafkaProducer<>(kafkaProducer,
				beanFactory());

		tracingKafkaProducer.send(testRecord, callback);

		ArgumentCaptor<KafkaTracingCallback> callbackArgument = ArgumentCaptor.forClass(KafkaTracingCallback.class);
		Mockito.verify(kafkaProducer).send(any(), callbackArgument.capture());
		BDDAssertions.then(callbackArgument.getValue()).isNotNull();
		BDDAssertions.then(ReflectionTestUtils.getField(callbackArgument.getValue(), "callback")).isEqualTo(callback);
	}

	private BeanFactory beanFactory() {
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
		beanFactory.addBean("tracer", this.tracer);
		beanFactory.addBean("propagator", this.propagator);
		return beanFactory;
	}

}
