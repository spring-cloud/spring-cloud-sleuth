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
import org.springframework.cloud.sleuth.instrument.messaging.rsocket.TracingRSocketConnectorConfigurer;
import org.springframework.cloud.sleuth.instrument.messaging.rsocket.TracingRSocketServerCustomizer;
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
