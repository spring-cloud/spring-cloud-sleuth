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

package org.springframework.cloud.sleuth.brave.instrument.messaging;

import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.apache.kafka.clients.producer.Producer;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import org.springframework.beans.factory.BeanFactory;

class TraceProducerPostProcessorTests {

	TestSpanHandler spans = new TestSpanHandler();

	StrictCurrentTraceContext traceContext = StrictCurrentTraceContext.create();

	Tracing tracing = Tracing.newBuilder().currentTraceContext(this.traceContext).sampler(Sampler.ALWAYS_SAMPLE)
			.addSpanHandler(this.spans).build();

	KafkaTracing kafkaTracing = KafkaTracing.newBuilder(this.tracing).build();

	@Test
	void should_not_double_wrap_kafka_producer() {
		BeanFactory beanFactory = BDDMockito.mock(BeanFactory.class);
		BDDMockito.given(beanFactory.getBean(KafkaTracing.class)).willReturn(kafkaTracing);
		Producer producer = BDDMockito.mock(Producer.class);
		Producer wrappedProducer = kafkaTracing.producer(producer);

		final Producer apply = new TraceProducerPostProcessor(beanFactory) {
			@Override
			Producer wrapInTracing(Producer producer) {
				throw new AssertionError("This method must not be called");
			}
		}.apply(wrappedProducer);

		BDDAssertions.then(apply).isSameAs(wrappedProducer);
	}

}
