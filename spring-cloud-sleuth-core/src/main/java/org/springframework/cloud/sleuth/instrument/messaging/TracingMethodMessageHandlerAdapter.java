/*
 * Copyright 2013-2019 the original author or authors.
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
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageHeaderAccessor;

import static brave.Span.Kind.CONSUMER;

/**
 * Adds tracing extraction to an instance of
 * {@link org.springframework.messaging.handler.invocation.AbstractMethodMessageHandler} in a
 * reusable way. When sub-classing a provider specific class of that type you would wrap the
 * <pre>super.handleMessage(...)</pre> call with a call to this. See
 * {@link org.springframework.cloud.sleuth.instrument.messaging.SqsQueueMessageHandler} for an
 * example.
 *
 * This implementation also allows for supplying a {@link java.util.function.BiConsumer} instance
 * that can be used to add queue specific tags and modifications to the span.
 *
 * @author Brian Devins-Suresh
 */
class TracingMethodMessageHandlerAdapter {

	private Tracing tracing;
	private Tracer tracer;
	private TraceContext.Extractor<MessageHeaderAccessor> extractor;


	TracingMethodMessageHandlerAdapter(Tracing tracing,
			Propagation.Getter<MessageHeaderAccessor, String> traceMessagePropagationGetter) {
		this.tracing = tracing;
		this.tracer = tracing.tracer();
		this.extractor = tracing.propagation().extractor(traceMessagePropagationGetter);
	}

	void wrapMethodMessageHandler(Message<?> message,
			MessageHandler messageHandler, BiConsumer<Span, Message<?>> messageSpanTagger) {
		TraceContextOrSamplingFlags extracted = extractAndClearHeaders(message);

		Span consumerSpan = tracer.nextSpan(extracted);
		Span listenerSpan = tracer.newChild(consumerSpan.context());

		if (!consumerSpan.isNoop()) {
			consumerSpan.name("next-message").kind(CONSUMER);
			if (messageSpanTagger != null) {
				messageSpanTagger.accept(consumerSpan, message);
			}

			// incur timestamp overhead only once
			long timestamp = tracing.clock(consumerSpan.context()).currentTimeMicroseconds();
			consumerSpan.start(timestamp);
			long consumerFinish = timestamp + 1L; // save a clock reading
			consumerSpan.finish(consumerFinish);

			// not using scoped span as we want to start with a pre-configured time
			listenerSpan.name("on-message").start(consumerFinish);
		}

		try (Tracer.SpanInScope ws = tracer.withSpanInScope(listenerSpan)) {
			messageHandler.handleMessage(message);
		} catch (Throwable t) {
			listenerSpan.error(t);
			throw t;
		} finally {
			listenerSpan.finish();
		}
	}

	private TraceContextOrSamplingFlags extractAndClearHeaders(Message<?> message) {
		MessageHeaderAccessor headers = MessageHeaderAccessor.getMutableAccessor(message);
		TraceContextOrSamplingFlags extracted = extractor.extract(headers);

		for (String propagationKey : tracing.propagation().keys()) {
			headers.removeHeader(propagationKey);
		}

		return extracted;
	}
}
