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

package org.springframework.cloud.sleuth.autoconfig.instrument.messaging;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.cloud.function.context.catalog.FunctionAroundWrapper;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.messaging.FunctionMessageSpanCustomizer;
import org.springframework.cloud.sleuth.instrument.messaging.TraceFunctionAroundWrapper;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.cloud.stream.messaging.DirectWithAttributesChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable tracing via Spring Cloud Function.
 *
 * @author Oleg Zhurakousky
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.function.enabled", matchIfMissing = true)
@ConditionalOnBean(Tracer.class)
@ConditionalOnClass({ FunctionAroundWrapper.class, RefreshScopeRefreshedEvent.class })
@AutoConfigureAfter(BraveAutoConfiguration.class)
public class TraceFunctionAutoConfiguration {

	@Bean
	TraceFunctionAroundWrapper traceFunctionAroundWrapper(Environment environment, Tracer tracer, Propagator propagator,
			Propagator.Setter<MessageHeaderAccessor> injector, Propagator.Getter<MessageHeaderAccessor> extractor,
			ObjectProvider<List<FunctionMessageSpanCustomizer>> customizers) {
		return new TraceFunctionAroundWrapper(environment, tracer, propagator, injector, extractor,
				customizers.getIfAvailable(ArrayList::new));
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(DirectWithAttributesChannel.class)
	static class TraceFunctionStreamConfiguration {

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnClass(name = "org.springframework.cloud.stream.binder.kafka.config.KafkaBinderConfiguration")
		@ConditionalOnMissingClass(
				value = "org.springframework.cloud.stream.binder.rabbit.properties.RabbitBinderConfigurationProperties")
		static class KafkaOnlyStreamConfiguration {

			@Bean
			FunctionMessageSpanCustomizer traceKafkaFunctionMessageSpanCustomizer() {
				return new FunctionMessageSpanCustomizer() {
					@Override
					public void customizeInputMessageSpan(Span span, Message<?> message) {
						span.remoteServiceName("kafka");
					}

					@Override
					public void customizeOutputMessageSpan(Span span, Message<?> message) {
						span.remoteServiceName("kafka");
					}
				};
			}

		}

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnClass(
				name = "org.springframework.cloud.stream.binder.rabbit.properties.RabbitBinderConfigurationProperties")
		@ConditionalOnMissingClass(
				value = "org.springframework.cloud.stream.binder.kafka.config.KafkaBinderConfiguration")
		static class RabbitOnlyStreamConfiguration {

			@Bean
			FunctionMessageSpanCustomizer traceRabbitFunctionMessageSpanCustomizer() {
				return new FunctionMessageSpanCustomizer() {
					@Override
					public void customizeInputMessageSpan(Span span, Message<?> message) {
						span.remoteServiceName("rabbitmq");
					}

					@Override
					public void customizeOutputMessageSpan(Span span, Message<?> message) {
						span.remoteServiceName("rabbitmq");
					}
				};
			}

		}

	}

}
