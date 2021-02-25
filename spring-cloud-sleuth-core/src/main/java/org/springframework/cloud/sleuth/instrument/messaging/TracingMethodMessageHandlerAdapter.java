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

import java.util.function.BiConsumer;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.messaging.ConsumerRequest;
import brave.messaging.MessagingTracing;
import brave.propagation.Propagation.Getter;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageHeaderAccessor;

import static brave.Span.Kind.CONSUMER;
import static org.springframework.cloud.sleuth.instrument.messaging.SqsQueueMessageHandler.LOGICAL_RESOURCE_ID;

/**
 * Adds tracing extraction to an instance of
 * {@link org.springframework.messaging.handler.invocation.AbstractMethodMessageHandler}
 * in a reusable way. When sub-classing a provider specific class of that type you would
 * wrap the <pre>super.handleMessage(...)</pre> call with a call to this. See
 * {@link org.springframework.cloud.sleuth.instrument.messaging.SqsQueueMessageHandler}
 * for an example.
 *
 * This implementation also allows for supplying a {@link java.util.function.BiConsumer}
 * instance that can be used to add queue specific tags and modifications to the span.
 *
 * @author Brian Devins-Suresh
 */
class TracingMethodMessageHandlerAdapter {

	private final Tracing tracing;

	private final Tracer tracer;

	private final Extractor<MessageConsumerRequest> extractor;

	private final Getter<MessageHeaderAccessor, String> getter;

	TracingMethodMessageHandlerAdapter(MessagingTracing messagingTracing,
			Getter<MessageHeaderAccessor, String> getter) {
		this.tracing = messagingTracing.tracing();
		this.tracer = tracing.tracer();
		this.extractor = tracing.propagation().extractor(MessageConsumerRequest.GETTER);
		this.getter = getter;
	}

	void wrapMethodMessageHandler(Message<?> message, MessageHandler messageHandler,
			BiConsumer<Span, Message<?>> messageSpanTagger) {
		MessageConsumerRequest request = new MessageConsumerRequest(message, this.getter);
		TraceContextOrSamplingFlags extracted = extractAndClearHeaders(request);

		Span consumerSpan = tracer.nextSpan(extracted);
		Span listenerSpan = tracer.newChild(consumerSpan.context());

		if (!consumerSpan.isNoop()) {
			consumerSpan.name("next-message").kind(CONSUMER);
			if (messageSpanTagger != null) {
				messageSpanTagger.accept(consumerSpan, message);
			}

			// incur timestamp overhead only once
			long timestamp = tracing.clock(consumerSpan.context())
					.currentTimeMicroseconds();
			consumerSpan.start(timestamp);
			long consumerFinish = timestamp + 1L; // save a clock reading
			consumerSpan.finish(consumerFinish);

			// not using scoped span as we want to start with a pre-configured time
			listenerSpan.name("on-message").start(consumerFinish);
		}

		try (Tracer.SpanInScope ws = tracer.withSpanInScope(listenerSpan)) {
			messageHandler.handleMessage(message);
		}
		catch (Throwable t) {
			listenerSpan.error(t);
			throw t;
		}
		finally {
			listenerSpan.finish();
		}
	}

	private TraceContextOrSamplingFlags extractAndClearHeaders(
			MessageConsumerRequest request) {
		TraceContextOrSamplingFlags extracted = extractor.extract(request);

		for (String propagationKey : tracing.propagation().keys()) {
			request.removeHeader(propagationKey);
		}

		return extracted;
	}

}

final class MessageConsumerRequest extends ConsumerRequest {

	static final Getter<MessageConsumerRequest, String> GETTER = new Getter<MessageConsumerRequest, String>() {
		@Override
		public String get(MessageConsumerRequest request, String name) {
			return request.getHeader(name);
		}

		@Override
		public String toString() {
			return "MessageConsumerRequest::getHeader";
		}
	};

	final Message delegate;

	final MessageHeaderAccessor mutableHeaders;

	final Getter<MessageHeaderAccessor, String> getter;

	MessageConsumerRequest(Message delegate,
			Getter<MessageHeaderAccessor, String> getter) {
		this.delegate = delegate;
		this.mutableHeaders = mutableAccessor(delegate);
		this.getter = getter;
	}

	private MessageHeaderAccessor mutableAccessor(Message message) {
		MessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message,
				MessageHeaderAccessor.class);
		if (accessor != null && accessor.isMutable()) {
			return accessor;
		}
		return MessageHeaderAccessor.getMutableAccessor(delegate);
	}

	@Override
	public Span.Kind spanKind() {
		return Span.Kind.CONSUMER;
	}

	@Override
	public Object unwrap() {
		return this.delegate;
	}

	@Override
	public String operation() {
		return "receive";
	}

	@Override
	public String channelKind() {
		return "queue";
	}

	@Override
	public String channelName() {
		return this.delegate.getHeaders().get(LOGICAL_RESOURCE_ID).toString();
	}

	String getHeader(String name) {
		return this.getter.get(this.mutableHeaders, name);
	}

	void removeHeader(String name) {
		this.mutableHeaders.removeHeader(name);
	}

}
