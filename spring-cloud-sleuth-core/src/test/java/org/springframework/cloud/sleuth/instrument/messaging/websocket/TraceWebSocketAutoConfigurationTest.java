/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging.websocket;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.instrument.messaging.TraceChannelInterceptor;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketMessageBrokerConfiguration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TraceWebSocketAutoConfigurationTest.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TraceWebSocketAutoConfigurationTest {

	@Autowired
	DelegatingWebSocketMessageBrokerConfiguration delegatingWebSocketMessageBrokerConfiguration;

	@Test
	public void should_register_interceptors_for_inbound_and_outbound_channels() {
		then(this.delegatingWebSocketMessageBrokerConfiguration.clientInboundChannel()
				.getInterceptors())
						.hasAtLeastOneElementOfType(TraceChannelInterceptor.class);
		then(this.delegatingWebSocketMessageBrokerConfiguration.clientOutboundChannel()
				.getInterceptors())
						.hasAtLeastOneElementOfType(TraceChannelInterceptor.class);
	}

	@EnableAutoConfiguration
	@Configuration
	@EnableWebSocketMessageBroker
	public static class Config extends AbstractWebSocketMessageBrokerConfigurer {

		@Override
		public void configureMessageBroker(MessageBrokerRegistry config) {
			config.enableSimpleBroker("/topic");
			config.setApplicationDestinationPrefixes("/app");
		}

		@Override
		public void registerStompEndpoints(StompEndpointRegistry registry) {
			registry.addEndpoint("/hello").withSockJS();
		}

		@Bean Sampler testSampler() {
			return new AlwaysSampler();
		}
	}
}