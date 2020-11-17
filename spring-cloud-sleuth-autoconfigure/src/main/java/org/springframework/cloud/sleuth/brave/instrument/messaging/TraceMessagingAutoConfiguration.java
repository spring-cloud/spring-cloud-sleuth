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

import java.util.List;

import brave.Tracer;
import brave.Tracing;
import brave.jms.JmsTracing;
import brave.kafka.clients.KafkaTracing;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.messaging.MessagingTracingCustomizer;
import brave.sampler.SamplerFunction;
import brave.spring.rabbit.SpringRabbitTracing;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.lang.Nullable;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that registers a tracing instrumentation of messaging components.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MessagingTracing.class)
@OnMessagingEnabled
@ConditionalOnBean(Tracing.class)
@EnableConfigurationProperties(SleuthMessagingProperties.class)
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
	@ConditionalOnProperty(value = "spring.sleuth.messaging.rabbit.enabled", matchIfMissing = true)
	@ConditionalOnClass(RabbitTemplate.class)
	protected static class SleuthRabbitConfiguration {

		@Bean
		// for tests
		@ConditionalOnMissingBean
		static SleuthRabbitBeanPostProcessor sleuthRabbitBeanPostProcessor(BeanFactory beanFactory) {
			return new SleuthRabbitBeanPostProcessor(beanFactory);
		}

		@Bean
		@ConditionalOnMissingBean
		SpringRabbitTracing springRabbitTracing(MessagingTracing messagingTracing,
				SleuthMessagingProperties properties) {
			return SpringRabbitTracing.newBuilder(messagingTracing)
					.remoteServiceName(properties.getMessaging().getRabbit().getRemoteServiceName()).build();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(value = "spring.sleuth.messaging.kafka.enabled", matchIfMissing = true)
	@ConditionalOnClass(ProducerFactory.class)
	protected static class SleuthKafkaConfiguration {

		@Bean
		@ConditionalOnMissingBean
		KafkaTracing kafkaTracing(MessagingTracing messagingTracing, SleuthMessagingProperties properties) {
			return KafkaTracing.newBuilder(messagingTracing)
					.remoteServiceName(properties.getMessaging().getKafka().getRemoteServiceName()).build();
		}

		@Bean
		// for tests
		@ConditionalOnMissingBean
		SleuthKafkaAspect sleuthKafkaAspect(KafkaTracing kafkaTracing, Tracer tracer) {
			return new SleuthKafkaAspect(kafkaTracing, tracer);
		}

		@Bean
		KafkaFactoryBeanPostProcessor kafkaFactoryBeanPostProcessor(BeanFactory beanFactory) {
			return new KafkaFactoryBeanPostProcessor(beanFactory);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(value = "spring.sleuth.messaging.jms.enabled", matchIfMissing = true)
	@ConditionalOnClass(JmsListenerConfigurer.class)
	@ConditionalOnBean(JmsListenerEndpointRegistry.class)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	protected static class SleuthJmsConfiguration {

		@Bean
		@ConditionalOnMissingBean
		JmsTracing jmsTracing(MessagingTracing messagingTracing, SleuthMessagingProperties properties) {
			return JmsTracing.newBuilder(messagingTracing)
					.remoteServiceName(properties.getMessaging().getJms().getRemoteServiceName()).build();
		}

		@Bean
		// for tests
		@ConditionalOnMissingBean
		TracingConnectionFactoryBeanPostProcessor tracingConnectionFactoryBeanPostProcessor(BeanFactory beanFactory) {
			return new TracingConnectionFactoryBeanPostProcessor(beanFactory);
		}

		@Bean
		JmsListenerConfigurer configureTracing(BeanFactory beanFactory, JmsListenerEndpointRegistry defaultRegistry) {
			return registrar -> {
				TracingJmsBeanPostProcessor processor = beanFactory.getBean(TracingJmsBeanPostProcessor.class);
				JmsListenerEndpointRegistry registry = registrar.getEndpointRegistry();
				registrar.setEndpointRegistry(
						(JmsListenerEndpointRegistry) processor.wrap(registry == null ? defaultRegistry : registry));
			};
		}

		// Setup the tracing endpoint registry.
		@Bean
		TracingJmsBeanPostProcessor tracingJmsBeanPostProcessor(BeanFactory beanFactory) {
			return new TracingJmsBeanPostProcessor(beanFactory);
		}

	}

}
