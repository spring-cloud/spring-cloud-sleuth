/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging;

import brave.Tracer;
import brave.kafka.clients.KafkaTracing;
import brave.sampler.Sampler;
import brave.spring.rabbit.SpringRabbitTracing;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TraceMessagingAutoConfigurationTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TraceMessagingAutoConfigurationTests {

	@Autowired
	RabbitTemplate rabbitTemplate;

	@Autowired
	ArrayListSpanReporter reporter;

	@Autowired
	TestSleuthRabbitBeanPostProcessor postProcessor;

	@Autowired
	TestSleuthJmsBeanPostProcessor jmsBeanPostProcessor;

	@Autowired
	MySleuthKafkaAspect mySleuthKafkaAspect;

	@Autowired
	TestSleuthKafkaHeaderMapperBeanPostProcessor testSleuthKafkaHeaderMapperBeanPostProcessor;

	@Autowired
	ProducerFactory producerFactory;

	@Autowired
	ConsumerFactory consumerFactory;

	@Test
	public void should_wrap_rabbit_template() {
		then(this.rabbitTemplate).isNotNull();
		then(this.postProcessor.rabbitTracingCalled).isTrue();
	}

	@Test
	public void should_wrap_jms() {
		then(this.jmsBeanPostProcessor).isNotNull();
		then(this.jmsBeanPostProcessor.tracingCalled).isTrue();
	}

	@Test
	public void should_wrap_kafka() {
		this.producerFactory.createProducer();
		then(this.mySleuthKafkaAspect.producerWrapped).isTrue();

		this.consumerFactory.createConsumer();
		then(this.mySleuthKafkaAspect.consumerWrapped).isTrue();

		then(this.mySleuthKafkaAspect.adapterWrapped).isTrue();

		then(this.testSleuthKafkaHeaderMapperBeanPostProcessor.tracingCalled).isTrue();
	}

	@Configuration
	@EnableAutoConfiguration
	protected static class Config {

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		ArrayListSpanReporter reporter() {
			return new ArrayListSpanReporter();
		}

		@Bean
		SleuthRabbitBeanPostProcessor sleuthRabbitBeanPostProcessor(
				BeanFactory beanFactory) {
			return new TestSleuthRabbitBeanPostProcessor(beanFactory);
		}

		@Bean
		SleuthKafkaAspect sleuthKafkaAspect(KafkaTracing kafkaTracing, Tracer tracer) {
			return new MySleuthKafkaAspect(kafkaTracing, tracer);
		}

		@Bean
		TestSleuthJmsBeanPostProcessor sleuthJmsBeanPostProcessor(
				BeanFactory beanFactory) {
			return new TestSleuthJmsBeanPostProcessor(beanFactory);
		}

		@Bean
		TestSleuthKafkaHeaderMapperBeanPostProcessor testSleuthKafkaHeaderMapperBeanPostProcessor() {
			return new TestSleuthKafkaHeaderMapperBeanPostProcessor();
		}

		@KafkaListener(topics = "backend", groupId = "foo")
		public void onMessage(ConsumerRecord<?, ?> message) {
			System.err.println(message);
		}

	}

}

class TestSleuthRabbitBeanPostProcessor extends SleuthRabbitBeanPostProcessor {

	boolean rabbitTracingCalled = false;

	TestSleuthRabbitBeanPostProcessor(BeanFactory beanFactory) {
		super(beanFactory);
	}

	@Override
	SpringRabbitTracing rabbitTracing() {
		this.rabbitTracingCalled = true;
		return super.rabbitTracing();
	}

}

class MySleuthKafkaAspect extends SleuthKafkaAspect {

	boolean producerWrapped;

	boolean consumerWrapped;

	boolean adapterWrapped;

	MySleuthKafkaAspect(KafkaTracing kafkaTracing, Tracer tracer) {
		super(kafkaTracing, tracer);
	}

	@Override
	public Object wrapProducerFactory(ProceedingJoinPoint pjp) throws Throwable {
		this.producerWrapped = true;
		return Mockito.mock(Producer.class);
	}

	@Override
	public Object wrapConsumerFactory(ProceedingJoinPoint pjp) throws Throwable {
		this.consumerWrapped = true;
		return Mockito.mock(Consumer.class);
	}

	@Override
	public Object wrapListenerContainerCreation(ProceedingJoinPoint pjp)
			throws Throwable {
		this.adapterWrapped = true;
		return Mockito.mock(MessageListenerContainer.class);
	}

}

class TestSleuthJmsBeanPostProcessor extends TracingConnectionFactoryBeanPostProcessor {

	boolean tracingCalled = false;

	TestSleuthJmsBeanPostProcessor(BeanFactory beanFactory) {
		super(beanFactory);
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		this.tracingCalled = true;
		return super.postProcessAfterInitialization(bean, beanName);
	}

}

class TestSleuthKafkaHeaderMapperBeanPostProcessor
		extends SleuthKafkaHeaderMapperBeanPostProcessor {

	boolean tracingCalled = false;

	@Override
	Object sleuthDefaultKafkaHeaderMapper(Object bean) {
		this.tracingCalled = true;
		return super.sleuthDefaultKafkaHeaderMapper(bean);
	}

}
