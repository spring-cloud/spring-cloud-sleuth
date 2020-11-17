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

import java.lang.reflect.Field;

import brave.Tracer;
import brave.kafka.clients.KafkaTracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.util.ReflectionUtils;

@Aspect
class SleuthKafkaAspect {

	private static final Log log = LogFactory.getLog(SleuthKafkaAspect.class);

	final Field recordMessageConverter;

	private final KafkaTracing kafkaTracing;

	private final Tracer tracer;

	SleuthKafkaAspect(KafkaTracing kafkaTracing, Tracer tracer) {
		this.kafkaTracing = kafkaTracing;
		this.tracer = tracer;
		this.recordMessageConverter = ReflectionUtils.findField(MessagingMessageListenerAdapter.class,
				"recordMessageConverter");
	}

	@Pointcut("execution(public * org.springframework.kafka.core.ProducerFactory.createProducer(..))")
	private void anyProducerFactory() {
	} // NOSONAR

	@Pointcut("execution(public * org.springframework.kafka.core.ConsumerFactory.createConsumer(..))")
	private void anyConsumerFactory() {
	} // NOSONAR

	@Pointcut("execution(public * org.springframework.kafka.config.KafkaListenerContainerFactory.createListenerContainer(..))")
	private void anyCreateListenerContainer() {
	} // NOSONAR

	@Pointcut("execution(public * org.springframework.kafka.config.KafkaListenerContainerFactory.createContainer(..))")
	private void anyCreateContainer() {
	} // NOSONAR

	@Around("anyProducerFactory()")
	public Object wrapProducerFactory(ProceedingJoinPoint pjp) throws Throwable {
		Producer producer = (Producer) pjp.proceed();
		return this.kafkaTracing.producer(producer);
	}

	@Around("anyConsumerFactory()")
	public Object wrapConsumerFactory(ProceedingJoinPoint pjp) throws Throwable {
		Consumer consumer = (Consumer) pjp.proceed();
		return this.kafkaTracing.consumer(consumer);
	}

	@Around("anyCreateListenerContainer() || anyCreateContainer()")
	public Object wrapListenerContainerCreation(ProceedingJoinPoint pjp) throws Throwable {
		MessageListenerContainer listener = (MessageListenerContainer) pjp.proceed();
		if (listener instanceof AbstractMessageListenerContainer) {
			AbstractMessageListenerContainer container = (AbstractMessageListenerContainer) listener;
			Object someMessageListener = container.getContainerProperties().getMessageListener();
			if (someMessageListener == null) {
				if (log.isDebugEnabled()) {
					log.debug("No message listener to wrap. Proceeding");
				}
			}
			else if (someMessageListener instanceof MessageListener) {
				container.setupMessageListener(createProxy(someMessageListener));
			}
			else {
				if (log.isDebugEnabled()) {
					log.debug("ATM we don't support Batch message listeners");
				}
			}
		}
		else {
			if (log.isDebugEnabled()) {
				log.debug("Can't wrap this listener. Proceeding");
			}
		}
		return listener;
	}

	@SuppressWarnings("unchecked")
	Object createProxy(Object bean) {
		ProxyFactoryBean factory = new ProxyFactoryBean();
		factory.setProxyTargetClass(true);
		factory.addAdvice(new MessageListenerMethodInterceptor(this.kafkaTracing, this.tracer));
		factory.setTarget(bean);
		return factory.getObject();
	}

}
