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

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.messaging.MessageHeaderPropagatorGetter;
import org.springframework.cloud.sleuth.instrument.messaging.MessageHeaderPropagatorSetter;
import org.springframework.cloud.sleuth.instrument.messaging.TraceMessagingAspect;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.MessageHeaderAccessor;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MessageHeaderAccessor.class)
@ConditionalOnBean(Tracer.class)
@ConditionalOnProperty(value = "spring.sleuth.messaging.enabled", matchIfMissing = true)
@EnableConfigurationProperties({ SleuthIntegrationMessagingProperties.class, SleuthMessagingProperties.class })
class TraceSpringMessagingAutoConfiguration {

	@Bean
	@ConditionalOnProperty(value = "spring.sleuth.messaging.aspect.enabled", matchIfMissing = true)
	TraceMessagingAspect traceMessagingAspect(Tracer tracer, SpanNamer spanNamer) {
		return new TraceMessagingAspect(tracer, spanNamer);
	}

	@Bean
	@ConditionalOnMissingBean(value = MessageHeaderAccessor.class, parameterizedContainer = Propagator.Setter.class)
	Propagator.Setter<MessageHeaderAccessor> traceMessagePropagationSetter() {
		return new MessageHeaderPropagatorSetter();
	}

	@Bean
	@ConditionalOnMissingBean(value = MessageHeaderAccessor.class, parameterizedContainer = Propagator.Getter.class)
	Propagator.Getter<MessageHeaderAccessor> traceMessagePropagationGetter() {
		return new MessageHeaderPropagatorGetter();
	}

}
