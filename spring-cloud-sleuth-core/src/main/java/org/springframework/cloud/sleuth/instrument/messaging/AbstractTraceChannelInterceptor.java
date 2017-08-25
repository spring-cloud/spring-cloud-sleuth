package org.springframework.cloud.sleuth.instrument.messaging;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.util.SpanNameUtil;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.util.ClassUtils;

import java.lang.invoke.MethodHandles;

/**
 * Abstraction over classes related to channel intercepting
 *
 * @author Marcin Grzejszczak
 */
abstract class AbstractTraceChannelInterceptor extends ChannelInterceptorAdapter
		implements ExecutorChannelInterceptor {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	/**
	 * If a span comes from messaging components then it will have this value as a prefix
	 * to its name.
	 * <p>
	 * Example of a Span name: {@code message:foo}
	 * <p>
	 * Where {@code message} is the prefix and {@code foo} is the channel name
	 */
	protected static final String MESSAGE_COMPONENT = "message";

	private Tracer tracer;
	private TraceKeys traceKeys;
	private MessagingSpanTextMapExtractor spanExtractor;
	private MessagingSpanTextMapInjector spanInjector;
	private ErrorParser errorParser;
	private final BeanFactory beanFactory;

	protected AbstractTraceChannelInterceptor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	protected Tracer getTracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	protected TraceKeys getTraceKeys() {
		if (this.traceKeys == null) {
			this.traceKeys = this.beanFactory.getBean(TraceKeys.class);
		}
		return this.traceKeys;
	}

	protected MessagingSpanTextMapExtractor getSpanExtractor() {
		if (this.spanExtractor == null) {
			this.spanExtractor = this.beanFactory.getBean(MessagingSpanTextMapExtractor.class);
		}
		return this.spanExtractor;
	}

	protected MessagingSpanTextMapInjector getSpanInjector() {
		if (this.spanInjector == null) {
			this.spanInjector = this.beanFactory.getBean(MessagingSpanTextMapInjector.class);
		}
		return this.spanInjector;
	}

	protected ErrorParser getErrorParser() {
		if (this.errorParser == null) {
			this.errorParser = this.beanFactory.getBean(ErrorParser.class);
		}
		return this.errorParser;
	}

	/**
	 * Returns a span given the message and a channel. Returns {@code null} if ids are
	 * missing.
	 */
	protected Span buildSpan(SpanTextMap carrier) {
		try {
			return getSpanExtractor().joinTrace(carrier);
		} catch (Exception e) {
			log.error("Exception occurred while trying to extract span from carrier", e);
			return null;
		}
	}

	String getChannelName(MessageChannel channel) {
		String name = null;
		if (ClassUtils.isPresent(
				"org.springframework.integration.context.IntegrationObjectSupport",
				null)) {
			if (channel instanceof IntegrationObjectSupport) {
				name = ((IntegrationObjectSupport) channel).getComponentName();
			}
			if (name == null && channel instanceof AbstractMessageChannel) {
				name = ((AbstractMessageChannel) channel).getFullChannelName();
			}
		}
		if (name == null) {
			name = channel.toString();
		}
		return name;
	}

	String getMessageChannelName(MessageChannel channel) {
		return SpanNameUtil.shorten(MESSAGE_COMPONENT + ":" + getChannelName(channel));
	}

}
