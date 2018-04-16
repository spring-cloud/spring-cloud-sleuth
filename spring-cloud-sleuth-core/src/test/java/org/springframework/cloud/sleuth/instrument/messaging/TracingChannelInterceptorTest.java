/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import org.junit.After;
import org.junit.Test;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.messaging.support.NativeMessageHeaderAccessor.NATIVE_HEADERS;

public class TracingChannelInterceptorTest {

	List<Span> spans = new ArrayList<>();
	ChannelInterceptor interceptor = TracingChannelInterceptor.create(Tracing.newBuilder()
			.currentTraceContext(new StrictCurrentTraceContext()).spanReporter(spans::add)
			.build());

	QueueChannel channel = new QueueChannel();
	DirectChannel directChannel = new DirectChannel();
	Message message;
	MessageHandler handler = new MessageHandler() {
		@Override
		public void handleMessage(Message<?> msg) throws MessagingException {
			message = msg;
		}
	};

	@Test public void pollingReceive_emptyQueue() {
		channel.addInterceptor(consumerSideOnly(interceptor));

		assertThat(channel.receive(0)).isNull();
		assertThat(spans).hasSize(0);
	}

	@Test public void injectsProducerSpan() {
		channel.addInterceptor(producerSideOnly(interceptor));

		channel.send(MessageBuilder.withPayload("foo").build());

		assertThat(channel.receive().getHeaders())
				.containsKeys("X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled",
						"nativeHeaders");
		assertThat(spans).hasSize(1).flatExtracting(Span::kind)
				.containsExactly(Span.Kind.PRODUCER);
	}

	@Test public void injectsProducerAndConsumerSpan() {
		directChannel.addInterceptor(interceptor);
		directChannel.subscribe(this.handler);
		directChannel.send(MessageBuilder.withPayload("foo").build());

		assertThat(message).isNotNull();
		assertThat(message.getHeaders())
				.containsKeys("X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled",
						"nativeHeaders");
		assertThat(spans).flatExtracting(Span::kind)
				.contains(Span.Kind.CONSUMER, Span.Kind.PRODUCER);
	}

	@Test public void injectsProducerSpan_nativeHeaders() {
		channel.addInterceptor(producerSideOnly(interceptor));

		channel.send(MessageBuilder.withPayload("foo").build());

		assertThat((Map) channel.receive().getHeaders().get(NATIVE_HEADERS))
				.containsOnlyKeys("X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled",
						"spanTraceId", "spanId", "spanSampled");
	}

	/**
	 * If the producer is acting on an un-processed message (ex via a polling consumer), it should
	 * look at trace headers when there is no span in scope, and use that as the parent context.
	 */
	@Test public void producerConsidersOldSpanIds() {
		channel.addInterceptor(producerSideOnly(interceptor));

		channel.send(MessageBuilder.withPayload("foo")
				.setHeader("X-B3-TraceId", "000000000000000a")
				.setHeader("X-B3-ParentSpanId", "000000000000000a")
				.setHeader("X-B3-SpanId", "000000000000000b").build());

		assertThat(channel.receive().getHeaders())
				.containsEntry("X-B3-ParentSpanId", "000000000000000b");
	}

	@Test public void producerConsidersOldSpanIds_nativeHeaders() {
		channel.addInterceptor(producerSideOnly(interceptor));

		NativeMessageHeaderAccessor accessor = new NativeMessageHeaderAccessor() {
		};

		accessor.setNativeHeader("X-B3-TraceId", "000000000000000a");
		accessor.setNativeHeader("X-B3-ParentSpanId", "000000000000000a");
		accessor.setNativeHeader("X-B3-SpanId", "000000000000000b");

		channel.send(
				MessageBuilder.withPayload("foo").copyHeaders(accessor.toMessageHeaders())
						.build());

		assertThat((Map) channel.receive().getHeaders().get(NATIVE_HEADERS))
				.containsEntry("X-B3-ParentSpanId",
						Collections.singletonList("000000000000000b"));
	}

	/**
	 * We have to inject headers on a polling receive as any future processor will come later
	 */
	@Test public void pollingReceive_injectsConsumerSpan() {
		channel.addInterceptor(consumerSideOnly(interceptor));

		channel.send(MessageBuilder.withPayload("foo").build());

		assertThat(channel.receive().getHeaders())
				.containsKeys("X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled",
						"nativeHeaders");
		assertThat(spans).hasSize(1).flatExtracting(Span::kind)
				.containsExactly(Span.Kind.CONSUMER);
	}

	@Test public void pollingReceive_injectsConsumerSpan_nativeHeaders() {
		channel.addInterceptor(consumerSideOnly(interceptor));

		channel.send(MessageBuilder.withPayload("foo").build());

		assertThat((Map) channel.receive().getHeaders().get(NATIVE_HEADERS))
				.containsOnlyKeys("X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled",
						"spanTraceId", "spanId", "spanSampled");
	}

	@Test public void subscriber_startsAndStopsConsumerAndProcessingSpan() {
		ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
		channel.addInterceptor(executorSideOnly(interceptor));
		List<Message<?>> messages = new ArrayList<>();
		channel.subscribe(messages::add);

		channel.send(MessageBuilder.withPayload("foo").build());

		assertThat(messages.get(0).getHeaders())
				.doesNotContainKeys("X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled",
						"nativeHeaders");
		assertThat(spans).flatExtracting(Span::kind)
				.containsExactly(Span.Kind.CONSUMER, null);
	}

	/**
	 * The subscriber consumes a message then synchronously processes it. Since we only inject trace
	 * IDs on unprocessed messages, we remove IDs to prevent accidental re-use of the same span.
	 */
	@Test public void subscriber_removesTraceIdsFromMessage() {
		ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
		channel.addInterceptor(interceptor);
		List<Message<?>> messages = new ArrayList<>();
		channel.subscribe(messages::add);

		channel.send(MessageBuilder.withPayload("foo").build());

		assertThat(messages.get(0).getHeaders())
				.doesNotContainKeys("X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled");
	}

	@Test public void subscriber_removesTraceIdsFromMessage_nativeHeaders() {
		ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
		channel.addInterceptor(interceptor);
		List<Message<?>> messages = new ArrayList<>();
		channel.subscribe(messages::add);

		channel.send(MessageBuilder.withPayload("foo").build());

		assertThat((Map) messages.get(0).getHeaders().get(NATIVE_HEADERS))
				.doesNotContainKeys("X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled");
	}

	@Test public void integrated_sendAndPoll() {
		channel.addInterceptor(interceptor);

		channel.send(MessageBuilder.withPayload("foo").build());
		channel.receive();

		assertThat(spans).flatExtracting(Span::kind)
				.containsExactlyInAnyOrder(Span.Kind.CONSUMER, Span.Kind.PRODUCER);
	}

	@Test public void integrated_sendAndSubscriber() {
		ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
		channel.addInterceptor(interceptor);
		List<Message<?>> messages = new ArrayList<>();
		channel.subscribe(messages::add);

		channel.send(MessageBuilder.withPayload("foo").build());

		assertThat(spans).flatExtracting(Span::kind)
				.containsExactly(Span.Kind.CONSUMER, null, Span.Kind.PRODUCER);
	}

	@Test
	public void errorMessageHeadersRetained() {
		this.channel.addInterceptor(interceptor);
		QueueChannel deadReplyChannel = new QueueChannel();
		QueueChannel errorsReplyChannel = new QueueChannel();
		Map<String, Object> errorChannelHeaders = new HashMap<>();
		errorChannelHeaders.put(MessageHeaders.REPLY_CHANNEL, errorsReplyChannel);
		errorChannelHeaders.put(MessageHeaders.ERROR_CHANNEL, errorsReplyChannel);
		this.channel.send(new ErrorMessage(
				new MessagingException(MessageBuilder.withPayload("hi")
						.setHeader(TraceMessageHeaders.TRACE_ID_NAME, "000000000000000a")
						.setHeader(TraceMessageHeaders.SPAN_ID_NAME, "000000000000000a")
						.setReplyChannel(deadReplyChannel)
						.setErrorChannel(deadReplyChannel)
						.build()),
				errorChannelHeaders));

		this.message = this.channel.receive();

		assertThat(this.message).isNotNull();
		String spanId = this.message.getHeaders().get(TraceMessageHeaders.SPAN_ID_NAME, String.class);
		assertThat(spanId).isNotNull();
		String traceId = this.message.getHeaders().get(TraceMessageHeaders.TRACE_ID_NAME, String.class);
		assertThat(traceId).isEqualTo("000000000000000a");
		assertThat(spanId).isNotEqualTo("000000000000000a");
		assertThat(this.spans).hasSize(2);
		assertThat(this.message.getHeaders().getReplyChannel()).isSameAs(errorsReplyChannel);
		assertThat(this.message.getHeaders().getErrorChannel()).isSameAs(errorsReplyChannel);
	}

	ChannelInterceptor producerSideOnly(ChannelInterceptor delegate) {
		return new ChannelInterceptorAdapter() {
			@Override
			public Message<?> preSend(Message<?> message, MessageChannel channel) {
				return delegate.preSend(message, channel);
			}

			@Override
			public void afterSendCompletion(Message<?> message, MessageChannel channel,
					boolean sent, Exception ex) {
				delegate.afterSendCompletion(message, channel, sent, ex);
			}
		};
	}

	ChannelInterceptor consumerSideOnly(ChannelInterceptor delegate) {
		return new ChannelInterceptorAdapter() {
			@Override
			public Message<?> postReceive(Message<?> message, MessageChannel channel) {
				return delegate.postReceive(message, channel);
			}

			@Override
			public void afterReceiveCompletion(Message<?> message, MessageChannel channel,
					Exception ex) {
				delegate.afterReceiveCompletion(message, channel, ex);
			}
		};
	}

	ExecutorChannelInterceptor executorSideOnly(ChannelInterceptor delegate) {
		class ExecutorSideOnly extends ChannelInterceptorAdapter
				implements ExecutorChannelInterceptor {
			@Override
			public Message<?> beforeHandle(Message<?> message, MessageChannel channel,
					MessageHandler handler) {
				return ((ExecutorChannelInterceptor) delegate)
						.beforeHandle(message, channel, handler);
			}

			@Override
			public void afterMessageHandled(Message<?> message, MessageChannel channel,
					MessageHandler handler, Exception ex) {
				((ExecutorChannelInterceptor) delegate)
						.afterMessageHandled(message, channel, handler, ex);
			}
		}
		return new ExecutorSideOnly();
	}

	@After public void close() {
		assertThat(Tracing.current().currentTraceContext().get()).isNull();
		Tracing.current().close();
	}
}
