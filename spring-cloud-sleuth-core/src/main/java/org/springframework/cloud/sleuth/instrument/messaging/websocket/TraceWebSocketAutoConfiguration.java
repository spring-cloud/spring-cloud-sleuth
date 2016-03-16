package org.springframework.cloud.sleuth.instrument.messaging.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.messaging.TraceChannelInterceptor;
import org.springframework.cloud.sleuth.instrument.messaging.TraceSpanMessagingAutoConfiguration;
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
@AutoConfigureAfter(TraceSpanMessagingAutoConfiguration.class)
@ConditionalOnClass(DelegatingWebSocketMessageBrokerConfiguration.class)
@ConditionalOnBean(AbstractWebSocketMessageBrokerConfigurer.class)
@ConditionalOnProperty(value = "spring.sleuth.integration.websocket.enabled", matchIfMissing = true)
public class TraceWebSocketAutoConfiguration
		extends AbstractWebSocketMessageBrokerConfigurer {

	@Autowired Tracer tracer;
	@Autowired TraceKeys traceKeys;
	@Autowired @Qualifier("messagingSpanExtractor") SpanExtractor<Message> spanExtractor;
	@Autowired @Qualifier("messagingSpanInjector") SpanInjector<MessageBuilder> spanInjector;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// The user must register their own endpoints
	}

	@Override
	public void configureClientOutboundChannel(ChannelRegistration registration) {
		registration.setInterceptors(
				new TraceChannelInterceptor(this.tracer, this.traceKeys, this.spanExtractor, this.spanInjector));
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.setInterceptors(
				new TraceChannelInterceptor(this.tracer, this.traceKeys, this.spanExtractor, this.spanInjector));
	}

	/*// TODO: Qualifier cause there were some issues with autowiring generics
	@Bean
	@Qualifier("stompMessagingSpanExtractor")
	public SpanExtractor<Message> stompMessagingSpanExtractor(Random random) {
		return new MessagingSpanExtractor(random);
	}

	// TODO: Qualifier cause there were some issues with autowiring generics
	@Bean
	@Qualifier("stompMessagingSpanInjector")
	public SpanInjector<MessageBuilder> stompMessagingSpanInjector(TraceKeys traceKeys) {
		return new MessagingSpanInjector(traceKeys);
	}*/
}