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

package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAndScope;
import org.springframework.cloud.sleuth.ThreadLocalSpan;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.cloud.stream.binder.BinderType;
import org.springframework.cloud.stream.binder.BinderTypeRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.integration.channel.DirectChannel;
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
import org.springframework.util.StringUtils;

/**
 * This starts and propagates {@link Span.Kind#PRODUCER} span for each message sent (via
 * native headers. It also extracts or creates a {@link Span.Kind#CONSUMER} span for each
 * message received. This span is injected onto each message so it becomes the parent when
 * a handler later calls {@link MessageHandler#handleMessage(Message)}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public final class TracingChannelInterceptor extends ChannelInterceptorAdapter
		implements ExecutorChannelInterceptor, ApplicationContextAware {

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

	final MessageSpanCustomizer messageSpanCustomizer;

	private final boolean hasDirectChannelClass;

	private final boolean hasBinderTypeRegistry;

	// special case of a Stream
	private final Class<?> directWithAttributesChannelClass;

	private ApplicationContext applicationContext;

	private final Propagator propagator;

	private final ThreadLocalSpan threadLocalSpan;

	private final Function<String, String> remoteServiceNameMapper;

	public TracingChannelInterceptor(Tracer tracer, Propagator propagator,
			Propagator.Setter<MessageHeaderAccessor> setter, Propagator.Getter<MessageHeaderAccessor> getter,
			Function<String, String> remoteServiceNameMapper, MessageSpanCustomizer messageSpanCustomizer) {
		this.tracer = tracer;
		this.threadLocalSpan = new ThreadLocalSpan(tracer);
		this.propagator = propagator;
		this.injector = setter;
		this.extractor = getter;
		this.remoteServiceNameMapper = remoteServiceNameMapper;
		this.messageSpanCustomizer = messageSpanCustomizer;
		this.hasDirectChannelClass = ClassUtils.isPresent("org.springframework.integration.channel.DirectChannel",
				null);
		this.hasBinderTypeRegistry = ClassUtils.isPresent("org.springframework.cloud.stream.binder.BinderTypeRegistry",
				null);
		this.directWithAttributesChannelClass = ClassUtils.isPresent(STREAM_DIRECT_CHANNEL, null)
				? ClassUtils.resolveClassName(STREAM_DIRECT_CHANNEL, null) : null;
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
		MessageHeaderPropagatorSetter.removeAnyTraceHeaders(headers, this.propagator.fields());
		spanBuilder = spanBuilder.kind(Span.Kind.PRODUCER);
		spanBuilder = this.messageSpanCustomizer.customizeSend(spanBuilder, message, channel)
				.remoteServiceName(toRemoteServiceName(headers));
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
		this.threadLocalSpan.set(span);
		if (log.isDebugEnabled()) {
			log.debug("Put span in scope " + span);
		}
	}

	private String toRemoteServiceName(MessageHeaderAccessor headers) {
		for (String key : headers.getMessageHeaders().keySet()) {
			String remoteServiceName = this.remoteServiceNameMapper.apply(key);
			if (StringUtils.hasText(remoteServiceName)) {
				return remoteServiceName;
			}
		}
		if (this.hasBinderTypeRegistry && this.applicationContext != null) {
			BinderTypeRegistry typeRegistry = this.applicationContext.getBean(BinderTypeRegistry.class);
			Iterator<Map.Entry<String, BinderType>> iterator = typeRegistry.getAll().entrySet().iterator();
			if (iterator.hasNext()) {
				String binderName = iterator.next().getKey();
				String remoteServiceName = this.remoteServiceNameMapper.apply(binderName);
				if (StringUtils.hasText(remoteServiceName)) {
					return remoteServiceName;
				}
			}
		}
		return REMOTE_SERVICE_NAME;
	}

	private Message<?> outputMessage(Message<?> originalMessage, Message<?> retrievedMessage,
			MessageHeaderAccessor additionalHeaders) {
		MessageHeaderAccessor headers = mutableHeaderAccessor(originalMessage);
		if (originalMessage instanceof ErrorMessage) {
			ErrorMessage errorMessage = (ErrorMessage) originalMessage;
			headers.copyHeaders(MessageHeaderPropagatorSetter.propagationHeaders(additionalHeaders.getMessageHeaders(),
					this.propagator.fields()));
			return new ErrorMessage(errorMessage.getPayload(), isWebSockets(headers) ? headers.getMessageHeaders()
					: new MessageHeaders(headers.getMessageHeaders()), errorMessage.getOriginalMessage());
		}
		headers.copyHeaders(new MessageHeaders(additionalHeaders.getMessageHeaders()));
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
		MessageHeaderPropagatorSetter.removeAnyTraceHeaders(headers, this.propagator.fields());
		builder = builder.kind(Span.Kind.CONSUMER);
		builder = this.messageSpanCustomizer.customizeReceive(builder, message, channel);
		builder = builder.remoteServiceName(toRemoteServiceName(headers));
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
		Span handle = this.tracer.nextSpan(consumerSpan);
		handle = this.messageSpanCustomizer.customizeHandle(handle, message, channel).start();
		if (log.isDebugEnabled()) {
			log.debug("Created consumer span " + handle);
		}
		setSpanInScope(handle);
		// remove any trace headers, but don't re-inject as we are synchronously
		// processing the
		// message and can rely on scoping to access this span later.
		MessageHeaderPropagatorSetter.removeAnyTraceHeaders(headers, this.propagator.fields());
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
		consumerSpanBuilder = this.messageSpanCustomizer.customizeHandle(consumerSpanBuilder, message, channel);
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

	void finishSpan(Exception error) {
		SpanAndScope spanAndScope = getSpanFromThreadLocal();
		if (spanAndScope == null) {
			return;
		}
		Span span = spanAndScope.getSpan();
		Tracer.SpanInScope scope = spanAndScope.getScope();
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

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

}
