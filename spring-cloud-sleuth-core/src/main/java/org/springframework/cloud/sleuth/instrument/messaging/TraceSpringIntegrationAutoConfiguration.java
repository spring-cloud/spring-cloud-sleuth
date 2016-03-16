/*
 * Copyright 2013-2015 the original author or authors.
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

import java.util.Random;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * that registers a Sleuth version of the {@link org.springframework.messaging.support.ChannelInterceptor}.
 *
 * @author Spencer Gibb
 * @since 1.0.0
 *
 * @see TraceChannelInterceptor
 */
@Configuration
@ConditionalOnClass(GlobalChannelInterceptor.class)
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
@ConditionalOnProperty(value = "spring.sleuth.integration.enabled", matchIfMissing = true)
@EnableConfigurationProperties(TraceKeys.class)
public class TraceSpringIntegrationAutoConfiguration {

	@Bean
	@GlobalChannelInterceptor
	public TraceChannelInterceptor traceChannelInterceptor(Tracer tracer,
			TraceKeys traceKeys, Random random,
			@Qualifier("messagingSpanExtractor") SpanExtractor<Message> spanExtractor,
			@Qualifier("messagingSpanInjector") SpanInjector<MessageBuilder> spanInjector) {
		return new TraceChannelInterceptor(tracer, traceKeys, spanExtractor, spanInjector);
	}

	// TODO: Qualifier + ConditionalOnProp cause there were some issues with autowiring generics
	@Bean
	@Qualifier("messagingSpanExtractor")
	@ConditionalOnProperty(value = "spring.sleuth.integration.injector.enabled", matchIfMissing = true)
	public SpanExtractor<Message> messagingSpanExtractor(Random random) {
		return new MessagingSpanExtractor(random);
	}

	// TODO: Qualifier + ConditionalOnProp cause there were some issues with autowiring generics
	@Bean
	@Qualifier("messagingSpanInjector")
	@ConditionalOnProperty(value = "spring.sleuth.integration.injector.enabled", matchIfMissing = true)
	public SpanInjector<MessageBuilder> messagingSpanInjector(TraceKeys traceKeys) {
		return new MessagingSpanInjector(traceKeys);
	}

}
