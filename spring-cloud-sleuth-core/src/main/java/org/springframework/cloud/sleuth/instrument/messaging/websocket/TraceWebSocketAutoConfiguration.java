package org.springframework.cloud.sleuth.instrument.messaging.websocket;

import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceHeaders;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.messaging.MessagingSpanExtractor;
import org.springframework.cloud.sleuth.instrument.messaging.MessagingSpanInjector;
import org.springframework.cloud.sleuth.instrument.messaging.TraceChannelInterceptor;
import org.springframework.cloud.sleuth.instrument.messaging.TraceSpringIntegrationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.support.MessageBuilder;
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
@Configuration
@AutoConfigureAfter(TraceSpringIntegrationAutoConfiguration.class)
@ConditionalOnClass(DelegatingWebSocketMessageBrokerConfiguration.class)
@ConditionalOnBean(AbstractWebSocketMessageBrokerConfigurer.class)
public class TraceWebSocketAutoConfiguration
		extends AbstractWebSocketMessageBrokerConfigurer {

	@Autowired Tracer tracer;
	@Autowired TraceKeys traceKeys;
	@Autowired TraceHeaders traceHeaders;
	@Autowired @Qualifier("stompMessagingSpanExtractor") SpanExtractor<Message> spanExtractor;
	@Autowired @Qualifier("stompMessagingSpanInjector") SpanInjector<MessageBuilder> spanInjector;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// The user must register their own endpoints
	}

	@Override
	public void configureClientOutboundChannel(ChannelRegistration registration) {
		registration.setInterceptors(
				new TraceChannelInterceptor(this.tracer, this.traceKeys, this.traceHeaders,
						this.spanExtractor, this.spanInjector));
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.setInterceptors(
				new TraceChannelInterceptor(this.tracer, this.traceKeys, this.traceHeaders,
						this.spanExtractor, this.spanInjector));
	}

	// TODO: Qualifier + ConditionalOnProp cause autowiring generics doesn't work
	@Bean
	@Qualifier("stompMessagingSpanExtractor")
	@ConditionalOnProperty(value = "spring.sleuth.integration.websocket.injector.enabled", matchIfMissing = true)
	public SpanExtractor<Message> stompMessagingSpanExtractor(Random random, TraceHeaders traceHeaders) {
		return new MessagingSpanExtractor(random, traceHeaders);
	}

	// TODO: Qualifier + ConditionalOnProp cause autowiring generics doesn't work
	@Bean
	@Qualifier("stompMessagingSpanInjector")
	@ConditionalOnProperty(value = "spring.sleuth.integration.websocket.injector.enabled", matchIfMissing = true)
	public SpanInjector<MessageBuilder> stompMessagingSpanInjector(TraceKeys traceKeys, TraceHeaders traceHeaders) {
		return new MessagingSpanInjector(traceKeys, traceHeaders);
	}
}