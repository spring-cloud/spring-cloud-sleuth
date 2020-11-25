/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.concurrent.LinkedBlockingDeque;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.support.AopUtils;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.internal.SpanNameUtil;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.ClassUtils;

/**
 * This starts and propagates {@link Span.Kind#PRODUCER} span for each message sent (via
 * native headers. It also extracts or creates a {@link Span.Kind#CONSUMER} span for each
 * message received. This span is injected onto each message so it becomes the parent when
 * a handler later calls {@link MessageHandler#handleMessage(Message)}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public final class TracingChannelInterceptor extends ChannelInterceptorAdapter implements ExecutorChannelInterceptor {

	/**
	 * Name of the class in Spring Cloud Stream that is a direct channel.
	 */
	public static final String STREAM_DIRECT_CHANNEL = "org.springframework."
			+ "cloud.stream.messaging.DirectWithAttributesChannel";

	private static final Log log = LogFactory.getLog(TracingChannelInterceptor.class);

	/**
	 * Using the literal "broker" until we come up with a better solution.
	 *
	 * <p>
	 * If the message originated from a binder (consumer binding), there will be different
	 * headers present (e.g. "KafkaHeaders.RECEIVED_TOPIC" Vs.
	 * "AmqpHeaders.CONSUMER_QUEUE" (unless the application removes them before sending).
	 * These don't represent the broker, rather a queue, and in any case the heuristics
	 * are not great. At least we might be able to tell if this is rabbit or not (ex how
	 * spring-rabbit works). We need to think this through before making an api, possibly
	 * experimenting.
	 *
	 * <p>
	 * If the app is outbound only (producer), there's no indication of what type the
	 * destination broker is. This may hint at a non-manual solution being overwriting the
	 * remoteServiceName later, similar to how servlet instrumentation lazy set
	 * "http.route".
	 */
	private static final String REMOTE_SERVICE_NAME = "broker";

	final Tracer tracer;

	final Propagator.Setter<MessageHeaderAccessor> injector;

	final Propagator.Getter<MessageHeaderAccessor> extractor;

	final boolean integrationObjectSupportPresent;

	private final boolean hasDirectChannelClass;

	// special case of a Stream
	private final Class<?> directWithAttributesChannelClass;

	private final Propagator propagator;

	private final ThreadLocalSpan threadLocalSpan = new ThreadLocalSpan();

	TracingChannelInterceptor(Tracer tracer, Propagator propagator) {
		this(tracer, propagator, MessageHeaderPropagation.INSTANCE, MessageHeaderPropagation.INSTANCE);
	}

	public TracingChannelInterceptor(Tracer tracer, Propagator propagator,
			Propagator.Setter<MessageHeaderAccessor> setter, Propagator.Getter<MessageHeaderAccessor> getter) {
		this.tracer = tracer;
		this.propagator = propagator;
		this.injector = setter;
		this.extractor = getter;
		this.integrationObjectSupportPresent = ClassUtils
				.isPresent("org.springframework.integration.context.IntegrationObjectSupport", null);
		this.hasDirectChannelClass = ClassUtils.isPresent("org.springframework.integration.channel.DirectChannel",
				null);
		this.directWithAttributesChannelClass = ClassUtils.isPresent(STREAM_DIRECT_CHANNEL, null)
				? ClassUtils.resolveClassName(STREAM_DIRECT_CHANNEL, null) : null;
	}

	public static TracingChannelInterceptor create(Tracer tracer, Propagator propagator) {
		return new TracingChannelInterceptor(tracer, propagator);
	}

	/**
	 * Use this to create a span for processing the given message. Note: the result has no
	 * name and is not started. This creates a child from identifiers extracted from the
	 * message headers, or a new span if one couldn't be extracted.
	 * @param message message to use for span creation
	 * @return span to be created
	 */
	public Span nextSpan(Message<?> message) {
		MessageHeaderAccessor headers = mutableHeaderAccessor(message);
		Span result = this.propagator.extract(headers, this.extractor).start();
		headers.setImmutable();
		if (!result.isNoop()) {
			addTags(result, null);
		}
		if (log.isDebugEnabled()) {
			log.debug("Created a new span " + result);
		}
		return result;
	}

	/**
	 * Starts and propagates {@link Span.Kind#PRODUCER} span for each message sent.
	 */
	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		if (emptyMessage(message)) {
			return message;
		}
		Message<?> retrievedMessage = getMessage(message);
		if (log.isDebugEnabled()) {
			log.debug("Received a message in pre-send " + retrievedMessage);
		}
		MessageHeaderAccessor headers = mutableHeaderAccessor(retrievedMessage);
		Span.Builder spanBuilder = this.propagator.extract(headers, this.extractor);
		MessageHeaderPropagation.removeAnyTraceHeaders(headers, this.propagator.fields());
		spanBuilder.kind(Span.Kind.PRODUCER).name("send").remoteServiceName(toRemoteServiceName(headers));
		addTags(spanBuilder, channel);
		Span span = spanBuilder.start();
		if (log.isDebugEnabled()) {
			log.debug("Extracted result from headers " + span);
		}
		setSpanInScope(span);
		this.propagator.inject(span.context(), headers, this.injector);
		if (log.isDebugEnabled()) {
			log.debug("Created a new span in pre send " + span);
		}
		Message<?> outputMessage = outputMessage(message, retrievedMessage, headers);
		if (isDirectChannel(channel)) {
			beforeHandle(outputMessage, channel, null);
		}
		return outputMessage;
	}

	private void setSpanInScope(Span span) {
		Tracer.SpanInScope spanInScope = this.tracer.withSpan(span);
		this.threadLocalSpan.set(new SpanAndScope(span, spanInScope));
		if (log.isDebugEnabled()) {
			log.debug("Put span in scope " + span);
		}
	}

	private String toRemoteServiceName(MessageHeaderAccessor headers) {
		for (String key : headers.getMessageHeaders().keySet()) {
			if (key.startsWith("kafka_")) {
				return "kafka";
			}
			else if (key.startsWith("amqp_")) {
				return "rabbitmq";
			}
		}
		return REMOTE_SERVICE_NAME;
	}

	private Message<?> outputMessage(Message<?> originalMessage, Message<?> retrievedMessage,
			MessageHeaderAccessor additionalHeaders) {
		MessageHeaderAccessor headers = mutableHeaderAccessor(originalMessage);
		if (originalMessage instanceof ErrorMessage) {
			ErrorMessage errorMessage = (ErrorMessage) originalMessage;
			headers.copyHeaders(MessageHeaderPropagation.propagationHeaders(additionalHeaders.getMessageHeaders(),
					this.propagator.fields()));
			return new ErrorMessage(errorMessage.getPayload(), isWebSockets(headers) ? headers.getMessageHeaders()
					: new MessageHeaders(headers.getMessageHeaders()), errorMessage.getOriginalMessage());
		}
		headers.copyHeaders(additionalHeaders.getMessageHeaders());
		return new GenericMessage<>(retrievedMessage.getPayload(),
				isWebSockets(headers) ? headers.getMessageHeaders() : new MessageHeaders(headers.getMessageHeaders()));
	}

	private boolean isWebSockets(MessageHeaderAccessor headerAccessor) {
		return headerAccessor.getMessageHeaders().containsKey("stompCommand")
				|| headerAccessor.getMessageHeaders().containsKey("simpMessageType");
	}

	private boolean isDirectChannel(MessageChannel channel) {
		Class<?> targetClass = AopUtils.getTargetClass(channel);
		boolean directChannel = this.hasDirectChannelClass && DirectChannel.class.isAssignableFrom(targetClass);
		if (!directChannel) {
			return false;
		}
		if (this.directWithAttributesChannelClass == null) {
			return true;
		}
		return !isStreamSpecialDirectChannel(targetClass);
	}

	private boolean isStreamSpecialDirectChannel(Class<?> targetClass) {
		return this.directWithAttributesChannelClass.isAssignableFrom(targetClass);
	}

	@Override
	public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
		if (emptyMessage(message)) {
			return;
		}
		if (isDirectChannel(channel)) {
			afterMessageHandled(message, channel, null, ex);
		}
		if (log.isDebugEnabled()) {
			log.debug("Will finish the current span after completion " + this.tracer.currentSpan());
		}
		finishSpan(ex);
	}

	/**
	 * This starts a consumer span as a child of the incoming message or the current trace
	 * context, placing it in scope until the receive completes.
	 */
	@Override
	public Message<?> postReceive(Message<?> message, MessageChannel channel) {
		if (emptyMessage(message)) {
			return message;
		}
		MessageHeaderAccessor headers = mutableHeaderAccessor(message);
		if (log.isDebugEnabled()) {
			log.debug("Received a message in post-receive " + message);
		}
		Span result = this.propagator.extract(headers, this.extractor).start();
		if (log.isDebugEnabled()) {
			log.debug("Extracted result from headers " + result);
		}
		Span span = consumerSpanReceive(message, channel, headers, result);
		setSpanInScope(span);
		if (log.isDebugEnabled()) {
			log.debug("Created a new span that will be injected in the headers " + span);
		}
		this.propagator.inject(span.context(), headers, this.injector);
		if (log.isDebugEnabled()) {
			log.debug("Created a new span in post receive " + span);
		}
		headers.setImmutable();
		if (message instanceof ErrorMessage) {
			ErrorMessage errorMessage = (ErrorMessage) message;
			return new ErrorMessage(errorMessage.getPayload(), headers.getMessageHeaders(),
					errorMessage.getOriginalMessage());
		}
		return new GenericMessage<>(message.getPayload(), headers.getMessageHeaders());
	}

	private Span consumerSpanReceive(Message<?> message, MessageChannel channel, MessageHeaderAccessor headers,
			Span result) {
		Span.Builder builder = this.tracer.spanBuilder().setParent(result.context());
		MessageHeaderPropagation.removeAnyTraceHeaders(headers, this.propagator.fields());
		builder.kind(Span.Kind.CONSUMER).name("receive").remoteServiceName(toRemoteServiceName(headers));
		addTags(builder, channel);
		return builder.start();
	}

	@Override
	public void afterReceiveCompletion(Message<?> message, MessageChannel channel, Exception ex) {
		if (emptyMessage(message)) {
			return;
		}
		if (log.isDebugEnabled()) {
			log.debug("Will finish the current span after receive completion " + this.tracer.currentSpan());
		}
		finishSpan(ex);
	}

	/**
	 * This starts a consumer span as a child of the incoming message or the current trace
	 * context. It then creates a span for the handler, placing it in scope.
	 */
	@Override
	public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
		if (emptyMessage(message)) {
			return message;
		}
		MessageHeaderAccessor headers = mutableHeaderAccessor(message);
		if (log.isDebugEnabled()) {
			log.debug("Received a message in before handle " + message);
		}
		Span consumerSpan = consumerSpan(message, channel, headers);
		// create and scope a span for the message processor
		Span handle = this.tracer.nextSpan(consumerSpan).name("handle").start();
		if (log.isDebugEnabled()) {
			log.debug("Created consumer span " + handle);
		}
		setSpanInScope(handle);
		// remove any trace headers, but don't re-inject as we are synchronously
		// processing the
		// message and can rely on scoping to access this span later.
		MessageHeaderPropagation.removeAnyTraceHeaders(headers, this.propagator.fields());
		if (log.isDebugEnabled()) {
			log.debug("Created a new span in before handle " + handle);
		}
		if (message instanceof ErrorMessage) {
			return new ErrorMessage((Throwable) message.getPayload(), headers.getMessageHeaders());
		}
		headers.setImmutable();
		return new GenericMessage<>(message.getPayload(), headers.getMessageHeaders());
	}

	private Span consumerSpan(Message<?> message, MessageChannel channel, MessageHeaderAccessor headers) {
		Span.Builder consumerSpanBuilder = this.propagator.extract(headers, this.extractor);
		if (log.isDebugEnabled()) {
			log.debug("Extracted result from headers - will finish it immediately " + consumerSpanBuilder);
		}
		// Start and finish a consumer span as we will immediately process it.
		consumerSpanBuilder.kind(Span.Kind.CONSUMER).start();
		consumerSpanBuilder.remoteServiceName(REMOTE_SERVICE_NAME);
		addTags(consumerSpanBuilder, channel);
		Span consumerSpan = consumerSpanBuilder.start();
		consumerSpan.end();
		return consumerSpan;
	}

	@Override
	public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
		if (emptyMessage(message)) {
			return;
		}
		if (log.isDebugEnabled()) {
			log.debug("Will finish the current span after message handled " + this.tracer.currentSpan());
		}
		finishSpan(ex);
	}

	/**
	 * When an upstream context was not present, lookup keys are unlikely added.
	 * @param result span to customize
	 * @param channel channel to which a message was sent
	 */
	void addTags(Span.Builder result, MessageChannel channel) {
		// TODO topic etc
		if (channel != null) {
			result.tag("channel", messageChannelName(channel));
		}
	}

	void addTags(Span result, MessageChannel channel) {
		// TODO topic etc
		if (channel != null) {
			result.tag("channel", messageChannelName(channel));
		}
	}

	private String channelName(MessageChannel channel) {
		String name = null;
		if (this.integrationObjectSupportPresent) {
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

	private String messageChannelName(MessageChannel channel) {
		return SpanNameUtil.shorten(channelName(channel));
	}

	void finishSpan(Exception error) {
		SpanAndScope spanAndScope = getSpanFromThreadLocal();
		if (spanAndScope == null) {
			return;
		}
		Span span = spanAndScope.span;
		Tracer.SpanInScope scope = spanAndScope.scope;
		if (span.isNoop()) {
			if (log.isDebugEnabled()) {
				log.debug("Span " + span + " is noop - will stope the scope");
			}
			scope.close();
			return;
		}
		if (error != null) { // an error occurred, adding error to span
			String message = error.getMessage();
			if (message == null) {
				message = error.getClass().getSimpleName();
			}
			span.tag("error", message);
		}
		if (log.isDebugEnabled()) {
			log.debug("Will finish the and its corresponding scope " + span);
		}
		span.end();
		scope.close();
	}

	private SpanAndScope getSpanFromThreadLocal() {
		SpanAndScope span = this.threadLocalSpan.get();
		if (log.isDebugEnabled()) {
			log.debug("Took span [" + span + "] from thread local");
		}
		this.threadLocalSpan.remove();
		return span;
	}

	private MessageHeaderAccessor mutableHeaderAccessor(Message<?> message) {
		MessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, MessageHeaderAccessor.class);
		if (accessor != null && accessor.isMutable()) {
			return accessor;
		}
		MessageHeaderAccessor headers = MessageHeaderAccessor.getMutableAccessor(message);
		headers.setLeaveMutable(true);
		return headers;
	}

	private Message<?> getMessage(Message<?> message) {
		Object payload = message.getPayload();
		if (payload instanceof MessagingException) {
			MessagingException e = (MessagingException) payload;
			Message<?> failedMessage = e.getFailedMessage();
			return failedMessage != null ? failedMessage : message;
		}
		return message;
	}

	private boolean emptyMessage(Message<?> message) {
		return message == null;
	}

}

class SpanAndScope {

	final Span span;

	final Tracer.SpanInScope scope;

	SpanAndScope(Span span, Tracer.SpanInScope scope) {
		this.span = span;
		this.scope = scope;
	}

}

class ThreadLocalSpan {

	private static final Log log = LogFactory.getLog(ThreadLocalSpan.class);

	final ThreadLocal<SpanAndScope> threadLocalSpan = new ThreadLocal<>();

	final LinkedBlockingDeque<SpanAndScope> spans = new LinkedBlockingDeque<>();

	void set(SpanAndScope spanAndScope) {
		SpanAndScope scope = this.threadLocalSpan.get();
		if (scope != null) {
			this.spans.addFirst(scope);
		}
		this.threadLocalSpan.set(spanAndScope);
	}

	SpanAndScope get() {
		return this.threadLocalSpan.get();
	}

	void remove() {
		this.threadLocalSpan.remove();
		if (this.spans.isEmpty()) {
			return;
		}
		SpanAndScope span = this.spans.removeFirst();
		if (log.isDebugEnabled()) {
			log.debug("Took span [" + span + "] from thread local");
		}
		this.threadLocalSpan.set(span);
	}

}
