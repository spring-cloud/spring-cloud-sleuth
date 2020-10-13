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

import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.messaging.Message;

/**
 * Messaging helpers to manually parse and inject spans. We're treating message headers as
 * a context that gets passed through.
 *
 * IMPORTANT: This API is experimental and might change in the future.
 *
 * The {@code forInputMessage} factory methods will retrieve the tracer context from the
 * message headers and set up a a child span in the header under key
 * {@link Span#getClass()} name. If you need to continue it or tag it, it's enough to
 * retrieve it from the headers.
 *
 * The first messaging span (the one that was first found in the input message) is present
 * under the {@code traceHandlerParentSpan} header key.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public final class MessagingSleuthOperators {

	private static final Log log = LogFactory.getLog(MessagingSleuthOperators.class);

	private MessagingSleuthOperators() {
		throw new IllegalStateException("You can't instantiate a utility class");
	}

	/**
	 * Executes a span wrapped operation for an input message.
	 * @param beanFactory - bean factory
	 * @param message - message to wrap
	 * @param withSpanInScope - an operation that will be wrapped in a span and the span
	 * will be reported at the end
	 * @param <T> - type of payload
	 * @return message with tracer context
	 */
	public static <T> Message<T> forInputMessage(BeanFactory beanFactory, Message<T> message,
			Consumer<Message<T>> withSpanInScope) {
		TraceMessageHandler traceMessageHandler = TraceMessageHandler.forNonSpringIntegration(beanFactory);
		MessageAndSpans wrappedInputMessage = traceMessageHandler.wrapInputMessage(message, "");
		if (log.isDebugEnabled()) {
			log.debug("Wrapped input msg " + wrappedInputMessage);
		}
		Throwable t = null;
		try (Tracer.SpanInScope ws = traceMessageHandler.tracer
				.withSpanInScope(wrappedInputMessage.childSpan.start())) {
			withSpanInScope.accept(wrappedInputMessage.msg);
		}
		catch (Exception e) {
			t = e;
			throw e;
		}
		finally {
			traceMessageHandler.afterMessageHandled(wrappedInputMessage.childSpan, t);
		}
		return wrappedInputMessage.msg;
	}

	/**
	 * Processes the input message and returns a message with a header containing a span.
	 * @param beanFactory - bean factory
	 * @param message - input message to process
	 * @param <T> - payload type
	 * @return message with tracer context
	 */
	public static <T> Message<T> forInputMessage(BeanFactory beanFactory, Message<T> message) {
		TraceMessageHandler traceMessageHandler = TraceMessageHandler.forNonSpringIntegration(beanFactory);
		MessageAndSpans wrappedInputMessage = traceMessageHandler.wrapInputMessage(message, "");
		if (log.isDebugEnabled()) {
			log.debug("Wrapped input msg " + wrappedInputMessage);
		}
		return wrappedInputMessage.msg;
	}

	/**
	 * Function converting an input message to a message with tracer headers.
	 * @param beanFactory - bean factory
	 * @param inputMessage - input message to process
	 * @param <T> input message type
	 * @return function representation of input message with tracer context
	 */
	public static <T> Function<Message<T>, Message<T>> asFunction(BeanFactory beanFactory, Message<T> inputMessage) {
		return stringMessage -> MessagingSleuthOperators.forInputMessage(beanFactory, inputMessage);
	}

	/**
	 * Retrieves tracer information from message headers.
	 * @param beanFactory - bean factory
	 * @param message - message to process
	 * @param <T> - payload type
	 * @return span retrieved from message or {@code null} if there was no span
	 */
	public static <T> Span spanFromMessage(BeanFactory beanFactory, Message<T> message) {
		TraceMessageHandler traceMessageHandler = TraceMessageHandler.forNonSpringIntegration(beanFactory);
		return spanFromMessage(traceMessageHandler, message);
	}

	private static <T> Span spanFromMessage(TraceMessageHandler traceMessageHandler, Message<T> message) {
		Span span = traceMessageHandler.spanFromMessage(message);
		if (log.isDebugEnabled()) {
			log.debug("Found the following span in message " + span);
		}
		return span;
	}

	/**
	 * Retrieves tracer information from message headers and applies the operation.
	 * @param beanFactory - bean factory
	 * @param message - message to process
	 * @param withSpanInScope - an operation that will be wrapped in a span but will not
	 * be reported
	 * @param <T> - payload type
	 */
	public static <T> void withSpanInScope(BeanFactory beanFactory, Message<T> message,
			Consumer<Message<T>> withSpanInScope) {
		TraceMessageHandler traceMessageHandler = TraceMessageHandler.forNonSpringIntegration(beanFactory);
		Span span = spanFromMessage(traceMessageHandler, message);
		try (Tracer.SpanInScope ws = traceMessageHandler.tracer.withSpanInScope(span)) {
			withSpanInScope.accept(message);
		}
	}

	/**
	 * Retrieves tracer information from message headers and applies the operation.
	 * @param beanFactory - bean factory
	 * @param message - message to process
	 * @param withSpanInScope - an operation that will be wrapped in a span but will not
	 * be reported
	 * @param <T> - payload type
	 * @return a message with tracer headers.
	 */
	public static <T> Message<T> withSpanInScope(BeanFactory beanFactory, Message<T> message,
			Function<Message<T>, Message<T>> withSpanInScope) {
		TraceMessageHandler traceMessageHandler = TraceMessageHandler.forNonSpringIntegration(beanFactory);
		Span span = spanFromMessage(traceMessageHandler, message);
		try (Tracer.SpanInScope ws = traceMessageHandler.tracer.withSpanInScope(span)) {
			return withSpanInScope.apply(message);
		}
	}

	/**
	 * Creates an output message with tracer headers and reports the corresponding
	 * producer span. If the message contains a header called {@code destination} it will
	 * be used to tag the span with destination name.
	 * @param beanFactory - bean factory
	 * @param message - message to which tracer headers should be injected
	 * @param <T> - message payload
	 * @return instrumented message
	 */
	public static <T> Message<T> handleOutputMessage(BeanFactory beanFactory, Message<T> message) {
		return handleOutputMessage(beanFactory, message, null);
	}

	/**
	 * Creates an output message with tracer headers and reports the corresponding
	 * producer span. If the message contains a header called {@code destination} it will
	 * be used to tag the span with destination name.
	 * @param beanFactory - bean factory
	 * @param message - message to which tracer headers should be injected
	 * @param throwable - exception that took place while processing the message
	 * @param <T> - message payload
	 * @return instrumented message
	 */
	public static <T> Message<T> handleOutputMessage(BeanFactory beanFactory, Message<T> message, Throwable throwable) {
		TraceMessageHandler traceMessageHandler = TraceMessageHandler.forNonSpringIntegration(beanFactory);
		Span span = traceMessageHandler.parentSpan(message);
		span = span != null ? span : traceMessageHandler.consumerSpan(message);
		if (span == null) {
			log.warn(
					"Can't find neither parent nor consumer span. Will return the message with no tracer header changes");
			return message;
		}
		MessageAndSpan messageAndSpan = traceMessageHandler.wrapOutputMessage(message, span,
				String.valueOf(message.getHeaders().getOrDefault("destination", "")));
		traceMessageHandler.afterMessageHandled(messageAndSpan.span, throwable);
		return messageAndSpan.msg;
	}

	/**
	 * Reports the span stored in the message.
	 * @param beanFactory - bean factory
	 * @param message - message with tracer context
	 * @param ex - potential exception that took place while processing
	 * @param <T> - message payload
	 * @return instrumented message
	 */
	public static <T> Message<T> afterMessageHandled(BeanFactory beanFactory, Message<T> message, Throwable ex) {
		TraceMessageHandler traceMessageHandler = TraceMessageHandler.forNonSpringIntegration(beanFactory);
		Span span = traceMessageHandler.spanFromMessage(message);
		traceMessageHandler.afterMessageHandled(span, ex);
		return message;
	}

}
