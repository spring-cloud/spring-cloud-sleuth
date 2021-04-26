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

package org.springframework.cloud.sleuth.autoconfig.instrument.rsocket;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.integration.IntegrationProperties.RSocket;
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.rsocket.server.RSocketServerCustomizer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.rsocket.TracingRSocketConnectorConfigurer;
import org.springframework.cloud.sleuth.instrument.rsocket.TracingRSocketServerCustomizer;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.messaging.rsocket.RSocketConnectorConfigurer;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketRequester.Builder;
import org.springframework.messaging.rsocket.RSocketStrategies;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.rsocket.enabled", matchIfMissing = true)
@ConditionalOnClass({RSocket.class, RSocketStrategies.class})
@AutoConfigureAfter(BraveAutoConfiguration.class)
@AutoConfigureBefore({RSocketRequesterAutoConfiguration.class, RSocketServerAutoConfiguration.class})
@EnableConfigurationProperties(SleuthRSocketProperties.class)
public class RSocketAutoConfiguration {

	@Bean
	@Scope("prototype")
	@ConditionalOnMissingBean
	public Builder rSocketRequesterBuilder(RSocketStrategies strategies,
			ObjectProvider<RSocketConnectorConfigurer> connectorConfigurerProvider) {
		// FIXME: should be in srping boot
		final Builder builder = RSocketRequester.builder().rsocketStrategies(strategies);

		connectorConfigurerProvider.forEach(builder::rsocketConnector);

		return builder;
	}

	@Bean
	public RSocketConnectorConfigurer tracingRSocketConnectorConfigurer(Propagator propagator,
			Tracer tracer) {
		return new TracingRSocketConnectorConfigurer(propagator, tracer);
	}

	@Bean
	public RSocketServerCustomizer tracingRSocketServerCustomizer(Propagator propagator,
			Tracer tracer) {
		return new TracingRSocketServerCustomizer(propagator, tracer);
	}
}
