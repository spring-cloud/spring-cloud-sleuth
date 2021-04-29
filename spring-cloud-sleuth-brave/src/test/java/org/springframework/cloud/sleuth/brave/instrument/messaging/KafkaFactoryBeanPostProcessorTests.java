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

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.Test;

import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ConsumerPostProcessor;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.ProducerPostProcessor;

import static org.assertj.core.api.BDDAssertions.then;

class KafkaFactoryBeanPostProcessorTests {

	@Test
	void should_add_consumer_post_processor_when_one_is_not_present() {
		TestConsumerFactory factory = new TestConsumerFactory();
		then(factory.postProcessors).isEmpty();
		KafkaFactoryBeanPostProcessor processor = new KafkaFactoryBeanPostProcessor(null);

		processor.postProcessAfterInitialization(factory, "");

		then(factory.postProcessors).hasSize(1);
	}

	@Test
	void should_add_producer_post_processor_when_one_is_not_present() {
		TestProducerFactory factory = new TestProducerFactory();
		then(factory.postProcessors).isEmpty();
		KafkaFactoryBeanPostProcessor processor = new KafkaFactoryBeanPostProcessor(null);

		processor.postProcessAfterInitialization(factory, "");

		then(factory.postProcessors).hasSize(1);
	}

	@Test
	void should_not_add_consumer_post_processor_when_one_is_present() {
		TestConsumerFactory factory = new TestConsumerFactory();
		factory.postProcessors.add(new TraceConsumerPostProcessor(null));
		KafkaFactoryBeanPostProcessor processor = new KafkaFactoryBeanPostProcessor(null);

		processor.postProcessAfterInitialization(factory, "");

		then(factory.postProcessors).hasSize(1);
	}

	@Test
	void should_not_add_producer_post_processor_when_one_is_present() {
		TestProducerFactory factory = new TestProducerFactory();
		factory.postProcessors.add(new TraceProducerPostProcessor(null));
		KafkaFactoryBeanPostProcessor processor = new KafkaFactoryBeanPostProcessor(null);

		processor.postProcessAfterInitialization(factory, "");

		then(factory.postProcessors).hasSize(1);
	}

}

class TestConsumerFactory implements ConsumerFactory {

	List<ConsumerPostProcessor> postProcessors = new ArrayList<>();

	@Override
	public Consumer createConsumer(String groupId, String clientIdPrefix, String clientIdSuffix) {
		return null;
	}

	@Override
	public boolean isAutoCommit() {
		return false;
	}

	@Override
	public void addPostProcessor(ConsumerPostProcessor postProcessor) {
		this.postProcessors.add(postProcessor);
	}

	@Override
	public List<ConsumerPostProcessor> getPostProcessors() {
		return this.postProcessors;
	}

}

class TestProducerFactory implements ProducerFactory {

	List<ProducerPostProcessor> postProcessors = new ArrayList<>();

	@Override
	public Producer createProducer() {
		return null;
	}

	@Override
	public void addPostProcessor(ProducerPostProcessor postProcessor) {
		this.postProcessors.add(postProcessor);
	}

	@Override
	public List<ProducerPostProcessor> getPostProcessors() {
		return this.postProcessors;
	}

}
