package org.springframework.cloud.sleuth.instrument.websocket;

import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.TraceKeys;
import org.springframework.cloud.sleuth.instrument.messaging.TraceChannelInterceptor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketMessageBrokerConfiguration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

@Component
@ConditionalOnClass(DelegatingWebSocketMessageBrokerConfiguration.class)
@ConditionalOnBean(AbstractWebSocketMessageBrokerConfigurer.class)
public class TraceWebSocketAutoConfiguration
		extends AbstractWebSocketMessageBrokerConfigurer {

	@Autowired
	private Tracer tracer;

	@Autowired
	private TraceKeys traceKeys;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// The user must register their own endpoints
	}

	@Override
	public void configureClientOutboundChannel(ChannelRegistration registration) {
		registration.setInterceptors(
				new TraceChannelInterceptor(this.tracer, this.traceKeys, new Random()));
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.setInterceptors(
				new TraceChannelInterceptor(this.tracer, this.traceKeys, new Random()));
	}
}