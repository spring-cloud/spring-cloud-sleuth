/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.messaging;

import brave.kafka.clients.KafkaTracing;
import brave.sampler.Sampler;
import brave.spring.rabbit.SpringRabbitTracing;
import com.rabbitmq.client.Channel;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TraceMessagingAutoConfigurationTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TraceMessagingAutoConfigurationTests {

	@Autowired RabbitTemplate rabbitTemplate;
	@Autowired ArrayListSpanReporter reporter;
	@Autowired TestSleuthRabbitBeanPostProcessor postProcessor;
	@Autowired MySleuthKafkaAspect mySleuthKafkaAspect;
	@Autowired ProducerFactory producerFactory;
	@Autowired ConsumerFactory consumerFactory;

	@Test
	public void should_wrap_rabbit_template() {
		then(this.rabbitTemplate).isNotNull();
		then(this.postProcessor.rabbitTracingCalled).isTrue();
	}

	@Test
	public void should_wrap_kafka() {
		this.producerFactory.createProducer();
		then(this.mySleuthKafkaAspect.producerWrapped).isTrue();

		this.consumerFactory.createConsumer();
		then(this.mySleuthKafkaAspect.consumerWrapped).isTrue();
	}

	@Configuration
	@EnableAutoConfiguration
	protected static class Config {
		@Bean Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean ArrayListSpanReporter reporter() {
			return new ArrayListSpanReporter();
		}

		@Bean SleuthRabbitBeanPostProcessor postProcessor(BeanFactory beanFactory) {
			return new TestSleuthRabbitBeanPostProcessor(beanFactory);
		}
		@Bean SleuthKafkaAspect sleuthKafkaAspect(KafkaTracing kafkaTracing) {
			return new MySleuthKafkaAspect(kafkaTracing);
		}
	}
}

class TestSleuthRabbitBeanPostProcessor  extends SleuthRabbitBeanPostProcessor {

	boolean rabbitTracingCalled = false;

	TestSleuthRabbitBeanPostProcessor(BeanFactory beanFactory) {
		super(beanFactory);
	}

	@Override SpringRabbitTracing rabbitTracing() {
		this.rabbitTracingCalled = true;
		return super.rabbitTracing();
	}
}

class MySleuthKafkaAspect extends SleuthKafkaAspect {

	boolean producerWrapped;
	boolean consumerWrapped;

	MySleuthKafkaAspect(KafkaTracing kafkaTracing) {
		super(kafkaTracing);
	}

	@Override public Object wrapProducerFactory(ProceedingJoinPoint pjp)
			throws Throwable {
		this.producerWrapped = true;
		return super.wrapProducerFactory(pjp);
	}

	@Override public Object wrapConsumerFactory(ProceedingJoinPoint pjp)
			throws Throwable {
		this.consumerWrapped = true;
		return super.wrapConsumerFactory(pjp);
	}
}