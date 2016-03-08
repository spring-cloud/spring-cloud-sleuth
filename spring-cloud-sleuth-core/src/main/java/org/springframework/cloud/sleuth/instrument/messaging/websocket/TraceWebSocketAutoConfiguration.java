package org.springframework.cloud.sleuth.instrument.messaging.websocket;

import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.instrument.messaging.TraceChannelInterceptor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketMessageBrokerConfiguration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * that enables tracing for WebSockets.
 *
 * @author Dave Syer
 * @since 1.0.0
 *
 * @see AbstractWebSocketMessageBrokerConfigurer
 */
@Component
@ConditionalOnClass(DelegatingWebSocketMessageBrokerConfiguration.class)
@ConditionalOnBean(AbstractWebSocketMessageBrokerConfigurer.class)
public class TraceWebSocketAutoConfiguration
		extends AbstractWebSocketMessageBrokerConfigurer {

	@Autowired
	private Tracer tracer;

	@Autowired
	private TraceKeys traceKeys;

	@Autowired
	private Random random;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// The user must register their own endpoints
	}

	@Override
	public void configureClientOutboundChannel(ChannelRegistration registration) {
		registration.setInterceptors(
				new TraceChannelInterceptor(this.tracer, this.traceKeys, this.random));
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.setInterceptors(
				new TraceChannelInterceptor(this.tracer, this.traceKeys, this.random));
	}
}