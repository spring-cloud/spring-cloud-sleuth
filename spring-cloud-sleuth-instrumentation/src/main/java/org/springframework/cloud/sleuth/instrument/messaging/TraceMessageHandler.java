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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.internal.SpanNameUtil;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.core.ResolvableType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.StringUtils;

// TODO: Duplicates a lot from TraceChannelInterceptor, need to figure out how to merge the two
class TraceMessageHandler {

	private static final Log log = LogFactory.getLog(TraceMessageHandler.class);

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

	private static final String TRACE_HANDLER_PARENT_SPAN = "traceHandlerParentSpan";

	final Tracer tracer;

	private final Propagator propagator;

	private final Propagator.Setter<MessageHeaderAccessor> injector;

	private final Propagator.Getter<MessageHeaderAccessor> extractor;

	private final Function<Span, Span> preSendFunction;

	private final TriConsumer<MessageHeaderAccessor, Span, Span> preSendMessageManipulator;

	private final Function<Span, Span.Builder> outputMessageSpanFunction;

	private final List<FunctionMessageSpanCustomizer> customizers;

	TraceMessageHandler(Tracer tracer, Propagator propagator, Propagator.Setter<MessageHeaderAccessor> injector,
			Propagator.Getter<MessageHeaderAccessor> extractor, Function<Span, Span> preSendFunction,
			TriConsumer<MessageHeaderAccessor, Span, Span> preSendMessageManipulator,
			Function<Span, Span.Builder> outputMessageSpanFunction, List<FunctionMessageSpanCustomizer> customizers) {
		this.tracer = tracer;
		this.propagator = propagator;
		this.injector = injector;
		this.extractor = extractor;
		// TODO: Abstractions to reuse in TraceChannelInterceptors?
		this.preSendFunction = preSendFunction;
		this.preSendMessageManipulator = preSendMessageManipulator;
		this.outputMessageSpanFunction = outputMessageSpanFunction;
		this.customizers = customizers;
	}

	static TraceMessageHandler forNonSpringIntegration(Tracer tracer, Propagator propagator,
			Propagator.Setter<MessageHeaderAccessor> injector, Propagator.Getter<MessageHeaderAccessor> extractor,
			List<FunctionMessageSpanCustomizer> customizers) {
		Function<Span, Span> preSendFunction = span -> tracer.nextSpan(span).name("function").start();
		TriConsumer<MessageHeaderAccessor, Span, Span> preSendMessageManipulator = (headers, parentSpan, childSpan) -> {
			headers.setHeader("traceHandlerParentSpan", parentSpan);
			headers.setHeader(Span.class.getName(), childSpan);
		};
		Function<Span, Span.Builder> postReceiveFunction = span -> tracer.spanBuilder().setParent(span.context());
		return new TraceMessageHandler(tracer, propagator, injector, extractor, preSendFunction,
				preSendMessageManipulator, postReceiveFunction, customizers);
	}

	@SuppressWarnings("unchecked")
	static TraceMessageHandler forNonSpringIntegration(BeanFactory beanFactory) {
		Propagator.Setter<MessageHeaderAccessor> setter = firstBeanOrException(beanFactory, Propagator.Setter.class);
		Propagator.Getter<MessageHeaderAccessor> getter = firstBeanOrException(beanFactory, Propagator.Getter.class);
		return forNonSpringIntegration(beanFactory.getBean(Tracer.class), beanFactory.getBean(Propagator.class), setter,
				getter, customizers(beanFactory));
	}

	private static <T> T firstBeanOrException(BeanFactory beanFactory, Class<T> clazz) {
		ObjectProvider<T> setterObjectProvider = beanFactory
				.getBeanProvider(ResolvableType.forClassWithGenerics(clazz, MessageHeaderAccessor.class));
		T object = setterObjectProvider.iterator().hasNext() ? setterObjectProvider.iterator().next() : null;
		if (object == null) {
			throw new NoSuchBeanDefinitionException("No Propagator.Setter has been defined");
		}
		return object;
	}

	private static List<FunctionMessageSpanCustomizer> customizers(BeanFactory beanFactory) {
		List<FunctionMessageSpanCustomizer> customizers = new ArrayList<>();
		ObjectProvider<FunctionMessageSpanCustomizer> provider = beanFactory
				.getBeanProvider(FunctionMessageSpanCustomizer.class);
		for (FunctionMessageSpanCustomizer functionMessageSpanCustomizer : provider) {
			customizers.add(functionMessageSpanCustomizer);
		}
		return customizers;
	}

	/**
	 * Wraps the given input message with tracing headers and returns a corresponding
	 * span.
	 * @param message - message to wrap
	 * @param destinationName - destination from which the message was received
	 * @return a tuple with the wrapped message and a corresponding span
	 */
	MessageAndSpans wrapInputMessage(Message<?> message, String destinationName) {
		MessageHeaderAccessor headers = mutableHeaderAccessor(message);
		Span.Builder consumerSpanBuilder = this.propagator.extract(headers, this.extractor);
		Span consumerSpan = consumerSpan(destinationName, consumerSpanBuilder, message);
		if (log.isDebugEnabled()) {
			log.debug("Built a consumer span " + consumerSpan);
		}
		Span childSpan = this.preSendFunction.apply(consumerSpan);
		clearTracingHeaders(headers);
		this.preSendMessageManipulator.accept(headers, consumerSpan, childSpan);
		this.customizers.forEach(customizer -> customizer.customizeFunctionSpan(childSpan, message));
		if (message instanceof ErrorMessage) {
			return new MessageAndSpans(new ErrorMessage((Throwable) message.getPayload(), headers.getMessageHeaders()),
					consumerSpan, childSpan);
		}
		headers.setImmutable();
		return new MessageAndSpans(new GenericMessage<>(message.getPayload(), headers.getMessageHeaders()),
				consumerSpan, childSpan);
	}

	private Span consumerSpan(String destinationName, Span.Builder consumerSpanBuilder, Message<?> message) {
		consumerSpanBuilder.kind(Span.Kind.CONSUMER).name("handle");
		addTags(consumerSpanBuilder, destinationName);
		consumerSpanBuilder.remoteServiceName(REMOTE_SERVICE_NAME);
		// this is the consumer part of the producer->consumer mechanism
		Span consumerSpan = consumerSpanBuilder.start();
		this.customizers.forEach(customizer -> customizer.customizeInputMessageSpan(consumerSpan, message));
		// we're ending this immediately just to have a properly nested graph
		consumerSpan.end();
		return consumerSpan;
	}

	Span spanFromMessage(Message<?> message) {
		MessageHeaderAccessor headers = mutableHeaderAccessor(message);
		Span span = span(headers, Span.class.getName());
		if (span != null) {
			return span;
		}
		span = span(headers, TRACE_HANDLER_PARENT_SPAN);
		if (span != null) {
			return span;
		}
		return this.propagator.extract(headers, this.extractor).start();
	}

	private void addTags(Span.Builder result, String destinationName) {
		if (StringUtils.hasText(destinationName)) {
			result.tag("channel", SpanNameUtil.shorten(destinationName));
		}
	}

	/**
	 * Called either when message got received and processed or message got sent.
	 * @param span - span that corresponds to the given operation
	 * @param ex - an optional exception that occurred while processing / sending.
	 */
	void afterMessageHandled(Span span, Throwable ex) {
		if (log.isDebugEnabled()) {
			log.debug("Will finish the current span after message handled " + span);
		}
		finishSpan(span, ex);
	}

	Span parentSpan(Message message) {
		return span(mutableHeaderAccessor(message), "traceHandlerParentSpan");
	}

	Span consumerSpan(Message message) {
		return span(mutableHeaderAccessor(message), Span.class.getName());
	}

	private Span span(MessageHeaderAccessor headerAccessor, String key) {
		return headerAccessor.getMessageHeaders().get(key, Span.class);
	}

	/**
	 * Wraps the given output message with tracing headers and returns a corresponding
	 * span.
	 * @param message - message to wrap
	 * @param destinationName - destination to which the message should be sent
	 * @return a tuple with the wrapped message and a corresponding span
	 */
	MessageAndSpan wrapOutputMessage(Message<?> message, Span parentSpan, String destinationName) {
		Message<?> retrievedMessage = getMessage(message);
		MessageHeaderAccessor headers = mutableHeaderAccessor(retrievedMessage);
		Span.Builder span = this.outputMessageSpanFunction.apply(parentSpan);
		clearTracingHeaders(headers);
		Span producerSpan = createProducerSpan(headers, span, destinationName, message);
		this.propagator.inject(producerSpan.context(), headers, this.injector);
		if (log.isDebugEnabled()) {
			log.debug("Created a new span output message " + span);
		}
		return new MessageAndSpan(outputMessage(message, retrievedMessage, headers), producerSpan);
	}

	private Span createProducerSpan(MessageHeaderAccessor headers, Span.Builder spanBuilder, String destinationName,
			Message<?> message) {
		spanBuilder.kind(Span.Kind.PRODUCER).name("send").remoteServiceName(toRemoteServiceName(headers));
		Span span = spanBuilder.start();
		if (!span.isNoop()) {
			addTags(spanBuilder, destinationName);
		}
		this.customizers.forEach(customizer -> customizer.customizeOutputMessageSpan(span, message));
		return span;
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
		clearTechnicalTracingHeaders(headers);
		if (originalMessage instanceof ErrorMessage) {
			ErrorMessage errorMessage = (ErrorMessage) originalMessage;
			headers.copyHeaders(MessageHeaderPropagatorSetter.propagationHeaders(additionalHeaders.getMessageHeaders(),
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

	private Message<?> getMessage(Message<?> message) {
		Object payload = message.getPayload();
		if (payload instanceof MessagingException) {
			MessagingException e = (MessagingException) payload;
			Message<?> failedMessage = e.getFailedMessage();
			return failedMessage != null ? failedMessage : message;
		}
		return message;
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

	private void clearTracingHeaders(MessageHeaderAccessor headers) {
		List<String> keysToRemove = new ArrayList<>(this.propagator.fields());
		keysToRemove.add(Span.class.getName());
		keysToRemove.add("traceHandlerParentSpan");
		MessageHeaderPropagatorSetter.removeAnyTraceHeaders(headers, keysToRemove);
	}

	private void clearTechnicalTracingHeaders(MessageHeaderAccessor headers) {
		MessageHeaderPropagatorSetter.removeAnyTraceHeaders(headers,
				Arrays.asList(Span.class.getName(), "traceHandlerParentSpan"));
	}

	private void finishSpan(Span span, Throwable error) {
		if (span == null || span.isNoop()) {
			return;
		}
		if (error != null) { // an error occurred, adding error to span
			String message = error.getMessage();
			if (message == null) {
				message = error.getClass().getSimpleName();
			}
			span.tag("error", message);
		}
		span.end();
	}

}

class MessageAndSpan {

	final Message msg;

	final Span span;

	MessageAndSpan(Message msg, Span span) {
		this.msg = msg;
		this.span = span;
	}

	@Override
	public String toString() {
		return "MessageAndSpan{" + "msg=" + this.msg + ", span=" + this.span + '}';
	}

}

class MessageAndSpans {

	final Message msg;

	final Span parentSpan;

	final Span childSpan;

	MessageAndSpans(Message msg, Span parentSpan, Span childSpan) {
		this.msg = msg;
		this.parentSpan = parentSpan;
		this.childSpan = childSpan;
	}

	@Override
	public String toString() {
		return "MessageAndSpans{" + "msg=" + msg + ", parentSpan=" + parentSpan + ", childSpan=" + childSpan + '}';
	}

}

interface TriConsumer<K, V, S> {

	/**
	 * Performs the operation given the specified arguments.
	 * @param k the first input argument
	 * @param v the second input argument
	 * @param s the third input argument
	 */
	void accept(K k, V v, S s);

}
