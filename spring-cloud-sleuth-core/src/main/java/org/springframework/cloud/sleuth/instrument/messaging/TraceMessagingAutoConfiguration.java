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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.spring.rabbit.SpringRabbitTracing;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.util.ReflectionUtils;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that registers a tracing instrumentation of
 * messaging components.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration
@ConditionalOnBean(Tracing.class)
@AutoConfigureAfter({ TraceAutoConfiguration.class })
@OnMessagingEnabled
@EnableConfigurationProperties(SleuthMessagingProperties.class)
public class TraceMessagingAutoConfiguration {

	@Configuration
	@ConditionalOnProperty(value = "spring.sleuth.messaging.rabbit.enabled", matchIfMissing = true)
	@ConditionalOnClass(RabbitTemplate.class)
	protected static class SleuthRabbitConfiguration {
		@Bean
		@ConditionalOnMissingBean
		SpringRabbitTracing springRabbitTracing(Tracing tracing,
				SleuthMessagingProperties properties) {
			return SpringRabbitTracing.newBuilder(tracing)
					.remoteServiceName(properties.getMessaging().getRabbit().getRemoteServiceName())
					.build();
		}

		@Bean
		// for tests
		@ConditionalOnMissingBean
		static SleuthRabbitBeanPostProcessor sleuthRabbitBeanPostProcessor(BeanFactory beanFactory) {
			return new SleuthRabbitBeanPostProcessor(beanFactory);
		}
	}

	@Configuration
	@ConditionalOnProperty(value = "spring.sleuth.messaging.kafka.enabled", matchIfMissing = true)
	@ConditionalOnClass(ProducerFactory.class)
	protected static class SleuthKafkaConfiguration {

		@Bean
		@ConditionalOnMissingBean
		KafkaTracing kafkaTracing(Tracing tracing, SleuthMessagingProperties properties) {
			return KafkaTracing
					.newBuilder(tracing)
					.remoteServiceName(properties.getMessaging().getKafka().getRemoteServiceName())
					.build();
		}

		@Bean
		// for tests
		@ConditionalOnMissingBean
		SleuthKafkaAspect sleuthKafkaAspect(KafkaTracing kafkaTracing, Tracer tracer) {
			return new SleuthKafkaAspect(kafkaTracing, tracer);
		}
	}
}

class SleuthRabbitBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;
	private SpringRabbitTracing tracing;

	SleuthRabbitBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof RabbitTemplate) {
			return rabbitTracing()
					.decorateRabbitTemplate((RabbitTemplate) bean);
		} else if (bean instanceof SimpleRabbitListenerContainerFactory) {
			return rabbitTracing()
					.decorateSimpleRabbitListenerContainerFactory((SimpleRabbitListenerContainerFactory) bean);
		}
		return bean;
	}

	SpringRabbitTracing rabbitTracing() {
		if (this.tracing == null) {
			this.tracing = this.beanFactory.getBean(SpringRabbitTracing.class);
		}
		return this.tracing;
	}
}

@Aspect
class SleuthKafkaAspect {

	private static final Log log = LogFactory.getLog(SleuthKafkaAspect.class);

	private final KafkaTracing kafkaTracing;
	private final Tracer tracer;
	final Field recordMessageConverter;

	SleuthKafkaAspect(KafkaTracing kafkaTracing, Tracer tracer) {
		this.kafkaTracing = kafkaTracing;
		this.tracer = tracer;
		this.recordMessageConverter = ReflectionUtils.findField(MessagingMessageListenerAdapter.class, "recordMessageConverter");
	}

	@Pointcut("execution(public * org.springframework.kafka.core.ProducerFactory.createProducer(..))")
	private void anyProducerFactory() { } // NOSONAR

	@Pointcut("execution(public * org.springframework.kafka.core.ConsumerFactory.createConsumer(..))")
	private void anyConsumerFactory() { } // NOSONAR

	@Pointcut("execution(public * org.springframework.kafka.config.KafkaListenerContainerFactory.createListenerContainer(..))")
	private void anyCreateListenerContainer() { } // NOSONAR

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

	@Around("anyCreateListenerContainer()")
	public Object wrapListenerContainerCreation(ProceedingJoinPoint pjp) throws Throwable {
		MessageListenerContainer listener = (MessageListenerContainer) pjp.proceed();
		if (listener instanceof AbstractMessageListenerContainer) {
			AbstractMessageListenerContainer container = (AbstractMessageListenerContainer) listener;
			Object someMessageListener = container.getContainerProperties().getMessageListener();
			if (someMessageListener == null) {
				if (log.isDebugEnabled()) {
					log.debug("No message listener to wrap. Proceeding");
				}
			} else if (someMessageListener instanceof MessageListener) {
				container.setupMessageListener(createProxy(someMessageListener));
			} else {
				if (log.isDebugEnabled()) {
					log.debug("ATM we don't support Batch message listeners");
				}
			}
		} else {
			if (log.isDebugEnabled()) {
				log.debug("Can't wrap this listener. Proceeding");
			}
		}
		return listener;
	}

	private RecordMessageConverter currentRecordMessageConverter(MessagingMessageListenerAdapter adapter)
			throws IllegalAccessException {
		if (this.recordMessageConverter != null) {
			return (RecordMessageConverter) this.recordMessageConverter.get(adapter);
		}
		return null;
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

class MessageListenerMethodInterceptor<T extends MessageListener> implements MethodInterceptor {

	private static final Log log = LogFactory.getLog(MessageListenerMethodInterceptor.class);

	private final KafkaTracing kafkaTracing;
	private final Tracer tracer;

	MessageListenerMethodInterceptor(KafkaTracing kafkaTracing, Tracer tracer) {
		this.kafkaTracing = kafkaTracing;
		this.tracer = tracer;
	}

	@Override public Object invoke(MethodInvocation invocation)
			throws Throwable {
		if (!"onMessage".equals(invocation.getMethod().getName())) {
			return invocation.proceed();
		}
		Object[] arguments = invocation.getArguments();
		Optional<Object> record = Arrays.stream(arguments).filter(o -> o instanceof ConsumerRecord).findFirst();
		if (!record.isPresent()) {
			return invocation.proceed();
		}
		if (log.isDebugEnabled()) {
			log.debug("Wrapping onMessage call");
		}
		Span span = this.kafkaTracing.nextSpan((ConsumerRecord<?, ?>) record.get()).name("on-message").start();
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			return invocation.proceed();
		} catch (RuntimeException | Error e) {
			String message = e.getMessage();
			if (message == null) message = e.getClass().getSimpleName();
			span.tag("error", message);
			throw e;
		} finally {
			span.finish();
		}
	}
}