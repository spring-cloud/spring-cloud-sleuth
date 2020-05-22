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

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.jms.JmsTracing;
import brave.kafka.clients.KafkaTracing;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.messaging.MessagingTracingCustomizer;
import brave.propagation.Propagation.Getter;
import brave.sampler.SamplerFunction;
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
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.aws.messaging.config.QueueMessageHandlerFactory;
import org.springframework.cloud.aws.messaging.listener.QueueMessageHandler;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.config.TracingJmsListenerEndpointRegistry;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that registers a tracing instrumentation of messaging components.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(Tracing.class)
@ConditionalOnClass(MessagingTracing.class)
@AutoConfigureAfter({ TraceAutoConfiguration.class,
		TraceSpringMessagingAutoConfiguration.class })
@OnMessagingEnabled
@EnableConfigurationProperties(SleuthMessagingProperties.class)
// public allows @AutoConfigureAfter(TraceMessagingAutoConfiguration)
// for components needing MessagingTracing
public class TraceMessagingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	// NOTE: stable bean name as might be used outside sleuth
	MessagingTracing messagingTracing(Tracing tracing,
			@Nullable @ProducerSampler SamplerFunction<MessagingRequest> producerSampler,
			@Nullable @ConsumerSampler SamplerFunction<MessagingRequest> consumerSampler,
			@Nullable List<MessagingTracingCustomizer> messagingTracingCustomizers) {

		MessagingTracing.Builder builder = MessagingTracing.newBuilder(tracing);
		if (producerSampler != null) {
			builder.producerSampler(producerSampler);
		}
		if (consumerSampler != null) {
			builder.consumerSampler(consumerSampler);
		}
		if (messagingTracingCustomizers != null) {
			for (MessagingTracingCustomizer customizer : messagingTracingCustomizers) {
				customizer.customize(builder);
			}
		}
		return builder.build();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(value = "spring.sleuth.messaging.rabbit.enabled",
			matchIfMissing = true)
	@ConditionalOnClass(RabbitTemplate.class)
	protected static class SleuthRabbitConfiguration {

		@Bean
		// for tests
		@ConditionalOnMissingBean
		static SleuthRabbitBeanPostProcessor sleuthRabbitBeanPostProcessor(
				BeanFactory beanFactory) {
			return new SleuthRabbitBeanPostProcessor(beanFactory);
		}

		@Bean
		@ConditionalOnMissingBean
		SpringRabbitTracing springRabbitTracing(MessagingTracing messagingTracing,
				SleuthMessagingProperties properties) {
			return SpringRabbitTracing.newBuilder(messagingTracing)
					.remoteServiceName(
							properties.getMessaging().getRabbit().getRemoteServiceName())
					.build();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(value = "spring.sleuth.messaging.kafka.enabled",
			matchIfMissing = true)
	@ConditionalOnClass(ProducerFactory.class)
	protected static class SleuthKafkaConfiguration {

		@Bean
		@ConditionalOnMissingBean
		KafkaTracing kafkaTracing(MessagingTracing messagingTracing,
				SleuthMessagingProperties properties) {
			return KafkaTracing.newBuilder(messagingTracing)
					.remoteServiceName(
							properties.getMessaging().getKafka().getRemoteServiceName())
					.build();
		}

		@Bean
		// for tests
		@ConditionalOnMissingBean
		SleuthKafkaAspect sleuthKafkaAspect(KafkaTracing kafkaTracing, Tracer tracer) {
			return new SleuthKafkaAspect(kafkaTracing, tracer);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(value = "spring.sleuth.messaging.jms.enabled",
			matchIfMissing = true)
	@ConditionalOnClass(JmsListenerConfigurer.class)
	@ConditionalOnBean(JmsListenerEndpointRegistry.class)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	protected static class SleuthJmsConfiguration {

		@Bean
		@ConditionalOnMissingBean
		JmsTracing jmsTracing(MessagingTracing messagingTracing,
				SleuthMessagingProperties properties) {
			return JmsTracing.newBuilder(messagingTracing)
					.remoteServiceName(
							properties.getMessaging().getJms().getRemoteServiceName())
					.build();
		}

		@Bean
		// for tests
		@ConditionalOnMissingBean
		TracingConnectionFactoryBeanPostProcessor tracingConnectionFactoryBeanPostProcessor(
				BeanFactory beanFactory) {
			return new TracingConnectionFactoryBeanPostProcessor(beanFactory);
		}

		@Bean
		JmsListenerConfigurer configureTracing(BeanFactory beanFactory,
				JmsListenerEndpointRegistry defaultRegistry) {
			return registrar -> {
				TracingJmsBeanPostProcessor processor = beanFactory
						.getBean(TracingJmsBeanPostProcessor.class);
				JmsListenerEndpointRegistry registry = registrar.getEndpointRegistry();
				registrar.setEndpointRegistry((JmsListenerEndpointRegistry) processor
						.wrap(registry == null ? defaultRegistry : registry));
			};
		}

		// Setup the tracing endpoint registry.
		@Bean
		TracingJmsBeanPostProcessor tracingJmsBeanPostProcessor(BeanFactory beanFactory) {
			return new TracingJmsBeanPostProcessor(beanFactory);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(value = "spring.sleuth.messaging.sqs.enabled",
			matchIfMissing = true)
	@ConditionalOnClass(QueueMessageHandler.class)
	protected static class SleuthSqsConfiguration {

		@Bean
		TracingMethodMessageHandlerAdapter tracingMethodMessageHandlerAdapter(
				MessagingTracing messagingTracing,
				Getter<MessageHeaderAccessor, String> traceMessagePropagationGetter) {
			return new TracingMethodMessageHandlerAdapter(messagingTracing,
					traceMessagePropagationGetter);
		}

		@Bean
		QueueMessageHandlerFactory sqsQueueMessageHandlerFactory(
				TracingMethodMessageHandlerAdapter tracingMethodMessageHandlerAdapter) {
			return new SqsQueueMessageHandlerFactory(tracingMethodMessageHandlerAdapter);
		}

	}

}

class SleuthRabbitBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	private SpringRabbitTracing tracing;

	SleuthRabbitBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof RabbitTemplate) {
			return rabbitTracing().decorateRabbitTemplate((RabbitTemplate) bean);
		}
		else if (bean instanceof SimpleRabbitListenerContainerFactory) {
			return rabbitTracing().decorateSimpleRabbitListenerContainerFactory(
					(SimpleRabbitListenerContainerFactory) bean);
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

	final Field recordMessageConverter;

	private final KafkaTracing kafkaTracing;

	private final Tracer tracer;

	SleuthKafkaAspect(KafkaTracing kafkaTracing, Tracer tracer) {
		this.kafkaTracing = kafkaTracing;
		this.tracer = tracer;
		this.recordMessageConverter = ReflectionUtils.findField(
				MessagingMessageListenerAdapter.class, "recordMessageConverter");
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
	public Object wrapListenerContainerCreation(ProceedingJoinPoint pjp)
			throws Throwable {
		MessageListenerContainer listener = (MessageListenerContainer) pjp.proceed();
		if (listener instanceof AbstractMessageListenerContainer) {
			AbstractMessageListenerContainer container = (AbstractMessageListenerContainer) listener;
			Object someMessageListener = container.getContainerProperties()
					.getMessageListener();
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
		factory.addAdvice(
				new MessageListenerMethodInterceptor(this.kafkaTracing, this.tracer));
		factory.setTarget(bean);
		return factory.getObject();
	}

}

class MessageListenerMethodInterceptor<T extends MessageListener>
		implements MethodInterceptor {

	private static final Log log = LogFactory
			.getLog(MessageListenerMethodInterceptor.class);

	private final KafkaTracing kafkaTracing;

	private final Tracer tracer;

	MessageListenerMethodInterceptor(KafkaTracing kafkaTracing, Tracer tracer) {
		this.kafkaTracing = kafkaTracing;
		this.tracer = tracer;
	}

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		if (!"onMessage".equals(invocation.getMethod().getName())) {
			return invocation.proceed();
		}
		Object[] arguments = invocation.getArguments();
		Object record = record(arguments);
		if (record == null) {
			return invocation.proceed();
		}
		if (log.isDebugEnabled()) {
			log.debug("Wrapping onMessage call");
		}
		Span span = this.kafkaTracing.nextSpan((ConsumerRecord<?, ?>) record)
				.name("on-message").start();
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			return invocation.proceed();
		}
		catch (RuntimeException | Error e) {
			String message = e.getMessage();
			if (message == null) {
				message = e.getClass().getSimpleName();
			}
			span.tag("error", message);
			throw e;
		}
		finally {
			span.finish();
		}
	}

	private Object record(Object[] arguments) {
		for (Object object : arguments) {
			if (object instanceof ConsumerRecord) {
				return object;
			}
		}
		return null;
	}

}

class TracingJmsBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	TracingJmsBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		return wrap(bean);
	}

	Object wrap(Object bean) {
		if (typeMatches(bean)) {
			return new TracingJmsListenerEndpointRegistry(
					(JmsListenerEndpointRegistry) bean, this.beanFactory);
		}
		return bean;
	}

	private boolean typeMatches(Object bean) {
		return bean instanceof JmsListenerEndpointRegistry
				&& !(bean instanceof TracingJmsListenerEndpointRegistry);
	}

}

class SqsQueueMessageHandlerFactory extends QueueMessageHandlerFactory {

	private TracingMethodMessageHandlerAdapter handlerAdapter;

	SqsQueueMessageHandlerFactory(TracingMethodMessageHandlerAdapter handlerAdapter) {
		this.handlerAdapter = handlerAdapter;
	}

	@Override
	public QueueMessageHandler createQueueMessageHandler() {
		if (CollectionUtils.isEmpty(getMessageConverters())) {
			return new SqsQueueMessageHandler(handlerAdapter, Collections.emptyList());
		}
		return new SqsQueueMessageHandler(handlerAdapter, getMessageConverters());
	}

}

class SqsQueueMessageHandler extends QueueMessageHandler {

	// copied from QueueMessageHandler
	static final String LOGICAL_RESOURCE_ID = "LogicalResourceId";

	private TracingMethodMessageHandlerAdapter handlerAdapter;

	SqsQueueMessageHandler(TracingMethodMessageHandlerAdapter handlerAdapter,
			List<MessageConverter> messageConverters) {
		super(messageConverters);
		this.handlerAdapter = handlerAdapter;
	}

	@Override
	public void handleMessage(Message<?> message) throws MessagingException {
		handlerAdapter.wrapMethodMessageHandler(message, super::handleMessage,
				this::messageSpanTagger);
	}

	private void messageSpanTagger(Span span, Message<?> message) {
		span.remoteServiceName("sqs");
		if (message.getHeaders().get(LOGICAL_RESOURCE_ID) != null) {
			span.tag("sqs.queue_url",
					message.getHeaders().get(LOGICAL_RESOURCE_ID).toString());
		}
	}

}
