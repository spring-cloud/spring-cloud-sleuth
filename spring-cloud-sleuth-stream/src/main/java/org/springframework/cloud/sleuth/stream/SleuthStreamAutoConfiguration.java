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

package org.springframework.cloud.sleuth.stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.config.ChannelBindingAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ChannelInterceptorAdapter;

/**
 * Autoconfiguration for sending Spans over Spring Cloud Stream. This is for the producer
 * (via {@link SleuthSource}). A consumer can enable binding to {@link SleuthSink} and
 * receive the messages coming from the source (they have the same channel name so there
 * is no additional configuration to do by default).
 *
 * @author Dave Syer
 */
@Configuration
@EnableConfigurationProperties(SleuthStreamProperties.class)
@AutoConfigureBefore(ChannelBindingAutoConfiguration.class)
@EnableBinding(SleuthSource.class)
@ConditionalOnProperty(value = "spring.sleuth.stream.enabled", matchIfMissing = true)
public class SleuthStreamAutoConfiguration {

	@Bean
	@GlobalChannelInterceptor(patterns = SleuthSource.OUTPUT, order = Ordered.HIGHEST_PRECEDENCE)
	public ChannelInterceptor zipkinChannelInterceptor() {
		// don't trace the tracer (suppress spans originating from our own source)
		return new ChannelInterceptorAdapter() {
			@Override
			public Message<?> preSend(Message<?> message, MessageChannel channel) {
				return MessageBuilder.fromMessage(message)
						.setHeader(Trace.NOT_SAMPLED_NAME, "").build();
			}
		};
	}

	@Bean
	public StreamSpanListener sleuthTracer(HostLocator endpointLocator) {
		return new StreamSpanListener(endpointLocator);
	}

	@Configuration
	@ConditionalOnMissingClass("org.springframework.cloud.client.discovery.DiscoveryClient")
	protected static class DefaultEndpointLocatorConfiguration {

		@Autowired(required = false)
		private ServerProperties serverProperties;

		@Bean
		public HostLocator zipkinEndpointLocator() {
			return new ServerPropertiesHostLocator(this.serverProperties);
		}

	}

	@Configuration
	@ConditionalOnClass(DiscoveryClient.class)
	protected static class DiscoveryClientEndpointLocatorConfiguration {

		@Autowired(required = false)
		private ServerProperties serverProperties;

		@Autowired(required = false)
		private DiscoveryClient client;

		@Bean
		public HostLocator zipkinEndpointLocator() {
			if (this.client != null) {
				return new DiscoveryClientHostLocator(this.client);
			}
			return new ServerPropertiesHostLocator(this.serverProperties);
		}

	}

}
