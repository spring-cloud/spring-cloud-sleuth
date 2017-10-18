package org.springframework.cloud.sleuth.instrument.messaging.websocket;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.messaging.MessagingSpanTextMapExtractor;
import org.springframework.cloud.sleuth.instrument.messaging.MessagingSpanTextMapInjector;
import org.springframework.cloud.sleuth.instrument.messaging.TraceChannelInterceptor;
import org.springframework.cloud.sleuth.instrument.messaging.TraceSpanMessagingAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketMessageBrokerConfiguration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that enables tracing for WebSockets.
 *
 * @author Dave Syer
 * @since 1.0.0
 *
 * @see AbstractWebSocketMessageBrokerConfigurer
 */
@Component
@Configuration
@AutoConfigureAfter(TraceSpanMessagingAutoConfiguration.class)
@ConditionalOnClass(DelegatingWebSocketMessageBrokerConfiguration.class)
@ConditionalOnBean(Tracer.class)
@ConditionalOnProperty(value = "spring.sleuth.integration.websockets.enabled", matchIfMissing = true)
public class TraceWebSocketAutoConfiguration
		extends AbstractWebSocketMessageBrokerConfigurer {

	@Autowired
	BeanFactory beanFactory;
	@Autowired
	Tracer tracer;
	@Autowired
	TraceKeys traceKeys;
	@Autowired
	MessagingSpanTextMapExtractor spanExtractor;
	@Autowired
	MessagingSpanTextMapInjector spanInjector;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// The user must register their own endpoints
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.configureBrokerChannel().setInterceptors(new TraceChannelInterceptor(this.beanFactory));
	}

	@Override
	public void configureClientOutboundChannel(ChannelRegistration registration) {
		registration.setInterceptors(new TraceChannelInterceptor(this.beanFactory));
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.setInterceptors(new TraceChannelInterceptor(this.beanFactory));
	}
}