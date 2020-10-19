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

package org.springframework.cloud.sleuth.instrument.messaging;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketMessageBrokerConfiguration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

/**
 * @author Marcin Grzejszczak
 */
@ContextConfiguration(classes = TraceWebSocketAutoConfigurationTests.TestConfig.class)
public abstract class TraceWebSocketAutoConfigurationTests {

	@Autowired
	DelegatingWebSocketMessageBrokerConfiguration delegatingWebSocketMessageBrokerConfiguration;

	@Test
	public void should_register_interceptors_for_all_channels() {
		BDDAssertions.then(this.delegatingWebSocketMessageBrokerConfiguration.clientInboundChannel().getInterceptors())
				.hasAtLeastOneElementOfType(TracingChannelInterceptor.class);
		BDDAssertions.then(this.delegatingWebSocketMessageBrokerConfiguration.clientOutboundChannel().getInterceptors())
				.hasAtLeastOneElementOfType(TracingChannelInterceptor.class);
		BDDAssertions.then(this.delegatingWebSocketMessageBrokerConfiguration.brokerChannel().getInterceptors())
				.hasAtLeastOneElementOfType(TracingChannelInterceptor.class);
	}

	@EnableAutoConfiguration
	@Configuration(proxyBeanMethods = false)
	@EnableWebSocketMessageBroker
	public static class TestConfig extends AbstractWebSocketMessageBrokerConfigurer {

		@Override
		public void configureMessageBroker(MessageBrokerRegistry config) {
			config.enableSimpleBroker("/topic");
			config.setApplicationDestinationPrefixes("/app");
		}

		@Override
		public void registerStompEndpoints(StompEndpointRegistry registry) {
			registry.addEndpoint("/hello").withSockJS();
		}

	}

}
