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

package org.springframework.cloud.sleuth.brave.instrument.messaging;

import brave.Tracer;
import brave.handler.SpanHandler;
import brave.kafka.clients.KafkaTracing;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import brave.spring.rabbit.SpringRabbitTracing;
import brave.test.TestSpanHandler;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.brave.autoconfig.BraveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@SpringBootTest(classes = BraveMessagingAutoConfigurationTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class BraveMessagingAutoConfigurationTests {

	@Autowired
	RabbitTemplate rabbitTemplate;

	@Autowired
	TestSpanHandler spans;

	@Autowired
	TestSleuthRabbitBeanPostProcessor postProcessor;

	@Autowired
	TestSleuthJmsBeanPostProcessor jmsBeanPostProcessor;

	@Autowired
	MySleuthKafkaAspect mySleuthKafkaAspect;

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
	}

	@Test
	public void defaultsToBraveProducerSampler() {
		contextRunner().run((context) -> {
			SamplerFunction<MessagingRequest> producerSampler = context.getBean(MessagingTracing.class)
					.producerSampler();

			then(producerSampler).isSameAs(SamplerFunctions.deferDecision());
		});
	}

	@Test
	public void configuresUserProvidedProducerSampler() {
		contextRunner().withUserConfiguration(ProducerSamplerConfig.class).run((context) -> {
			SamplerFunction<MessagingRequest> producerSampler = context.getBean(MessagingTracing.class)
					.producerSampler();

			then(producerSampler).isSameAs(ProducerSamplerConfig.INSTANCE);
		});
	}

	@Test
	public void defaultsToBraveConsumerSampler() {
		contextRunner().run((context) -> {
			SamplerFunction<MessagingRequest> consumerSampler = context.getBean(MessagingTracing.class)
					.consumerSampler();

			then(consumerSampler).isSameAs(SamplerFunctions.deferDecision());
		});
	}

	@Test
	public void configuresUserProvidedConsumerSampler() {
		contextRunner().withUserConfiguration(ConsumerSamplerConfig.class).run((context) -> {
			SamplerFunction<MessagingRequest> consumerSampler = context.getBean(MessagingTracing.class)
					.consumerSampler();

			then(consumerSampler).isSameAs(ConsumerSamplerConfig.INSTANCE);
		});
	}

	private ApplicationContextRunner contextRunner(String... propertyValues) {
		return new ApplicationContextRunner().withPropertyValues(propertyValues).withConfiguration(
				AutoConfigurations.of(BraveAutoConfiguration.class, BraveMessagingAutoConfiguration.class));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	protected static class Config {

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		@Bean
		SleuthRabbitBeanPostProcessor sleuthRabbitBeanPostProcessor(BeanFactory beanFactory) {
			return new TestSleuthRabbitBeanPostProcessor(beanFactory);
		}

		@Bean
		SleuthKafkaAspect sleuthKafkaAspect(KafkaTracing kafkaTracing, Tracer tracer) {
			return new MySleuthKafkaAspect(kafkaTracing, tracer);
		}

		@Bean
		TestSleuthJmsBeanPostProcessor sleuthJmsBeanPostProcessor(BeanFactory beanFactory) {
			return new TestSleuthJmsBeanPostProcessor(beanFactory);
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
	public Object wrapListenerContainerCreation(ProceedingJoinPoint pjp) throws Throwable {
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
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		this.tracingCalled = true;
		return super.postProcessAfterInitialization(bean, beanName);
	}

}

@Configuration(proxyBeanMethods = false)
class ProducerSamplerConfig {

	static final SamplerFunction<MessagingRequest> INSTANCE = request -> null;

	@Bean(ProducerSampler.NAME)
	SamplerFunction<MessagingRequest> sleuthProducerSampler() {
		return INSTANCE;
	}

}

@Configuration(proxyBeanMethods = false)
class ConsumerSamplerConfig {

	static final SamplerFunction<MessagingRequest> INSTANCE = request -> null;

	@Bean(ConsumerSampler.NAME)
	SamplerFunction<MessagingRequest> sleuthConsumerSampler() {
		return INSTANCE;
	}

}
