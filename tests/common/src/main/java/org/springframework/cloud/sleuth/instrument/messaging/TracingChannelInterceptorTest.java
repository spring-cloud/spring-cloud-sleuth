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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.messaging.support.NativeMessageHeaderAccessor.NATIVE_HEADERS;

public abstract class TracingChannelInterceptorTest implements TestTracingAwareSupplier {

	protected ChannelInterceptor interceptor = TracingChannelInterceptor.create(tracerTest().tracing().tracer(),
			tracerTest().tracing().propagator(), new SleuthIntegrationMessagingProperties());

	protected TestSpanHandler spans = tracerTest().handler();

	protected QueueChannel channel = new QueueChannel();

	protected DirectChannel directChannel = new DirectChannel();

	protected Message message;

	protected MessageHandler handler = new MessageHandler() {
		@Override
		public void handleMessage(Message<?> msg) throws MessagingException {
			TracingChannelInterceptorTest.this.message = msg;
		}
	};

	@AfterEach
	public void close() {
		tracerTest().close();
	}

	@Test
	public void pollingReceive_emptyQueue() {
		this.channel.addInterceptor(consumerSideOnly(this.interceptor));

		assertThat(this.channel.receive(0)).isNull();
		assertThat(this.spans).hasSize(0);
	}

	@Test
	public void injectsProducerSpan() {
		this.channel.addInterceptor(producerSideOnly(this.interceptor));

		this.channel.send(MessageBuilder.withPayload("foo").build());

		assertThat(this.channel.receive().getHeaders()).containsKey("b3");
		assertThat(this.spans).hasSize(1).extracting(FinishedSpan::getKind).containsExactly(Span.Kind.PRODUCER);
	}

	@Test
	public void injectsProducerAndConsumerSpan() {
		this.directChannel.addInterceptor(this.interceptor);
		this.directChannel.subscribe(this.handler);
		this.directChannel.send(MessageBuilder.withPayload("foo").build());

		assertThat(this.message).isNotNull();
		assertThat(this.message.getHeaders()).containsKeys("b3", "nativeHeaders");
		assertThat(this.spans).extracting(FinishedSpan::getKind).contains(Span.Kind.CONSUMER, Span.Kind.PRODUCER);
	}

	@Test
	public void injectsProducerSpan_nativeHeaders() {
		this.channel.addInterceptor(producerSideOnly(this.interceptor));

		this.channel.send(MessageBuilder.withPayload("foo").build());

		assertThat((Map) this.channel.receive().getHeaders().get(NATIVE_HEADERS)).containsOnlyKeys("b3");
	}

	/**
	 * We have to inject headers on a polling receive as any future processor will come
	 * later.
	 */
	@Test
	public void pollingReceive_injectsConsumerSpan() {
		this.channel.addInterceptor(consumerSideOnly(this.interceptor));

		this.channel.send(MessageBuilder.withPayload("foo").build());

		assertThat(this.channel.receive().getHeaders()).containsKeys("b3", "nativeHeaders");
		assertThat(this.spans).hasSize(1).extracting(FinishedSpan::getKind).containsExactly(Span.Kind.CONSUMER);
	}

	@Test
	public void pollingReceive_injectsConsumerSpan_nativeHeaders() {
		this.channel.addInterceptor(consumerSideOnly(this.interceptor));

		this.channel.send(MessageBuilder.withPayload("foo").build());

		assertThat((Map) this.channel.receive().getHeaders().get(NATIVE_HEADERS)).containsOnlyKeys("b3");
	}

	@Test
	public void subscriber_startsAndStopsConsumerAndProcessingSpan() {
		ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
		channel.addInterceptor(executorSideOnly(this.interceptor));
		List<Message<?>> messages = new ArrayList<>();
		channel.subscribe(messages::add);

		channel.send(MessageBuilder.withPayload("foo").build());

		assertThat(messages.get(0).getHeaders()).doesNotContainKeys("b3", "nativeHeaders");
		assertThat(this.spans).extracting(FinishedSpan::getKind).containsExactly(Span.Kind.CONSUMER, null);
	}

	/**
	 * The subscriber consumes a message then synchronously processes it. Since we only
	 * inject trace IDs on unprocessed messages, we remove IDs to prevent accidental
	 * re-use of the same span.
	 */
	@Test
	public void subscriber_removesTraceIdsFromMessage() {
		ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
		channel.addInterceptor(this.interceptor);
		List<Message<?>> messages = new ArrayList<>();
		channel.subscribe(messages::add);

		channel.send(MessageBuilder.withPayload("foo").build());

		assertThat(messages.get(0).getHeaders()).doesNotContainKeys("b3");
	}

	@Test
	public void subscriber_removesTraceIdsFromMessage_nativeHeaders() {
		ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
		channel.addInterceptor(this.interceptor);
		List<Message<?>> messages = new ArrayList<>();
		channel.subscribe(messages::add);

		channel.send(MessageBuilder.withPayload("foo").build());

		assertThat((Map) messages.get(0).getHeaders().get(NATIVE_HEADERS)).doesNotContainKeys("b3");
	}

	@Test
	public void integrated_sendAndPoll() {
		this.channel.addInterceptor(this.interceptor);

		this.channel.send(MessageBuilder.withPayload("foo").build());
		this.channel.receive();

		assertThat(this.spans).extracting(FinishedSpan::getKind).containsExactlyInAnyOrder(Span.Kind.CONSUMER,
				Span.Kind.PRODUCER);
	}

	@Test
	public void integrated_sendAndSubscriber() {
		ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
		channel.addInterceptor(this.interceptor);
		List<Message<?>> messages = new ArrayList<>();
		channel.subscribe(messages::add);

		channel.send(MessageBuilder.withPayload("foo").build());

		assertThat(this.spans).extracting(FinishedSpan::getKind).containsExactly(Span.Kind.CONSUMER, null,
				Span.Kind.PRODUCER);
	}

	@Test
	public void errorMessageHeadersRetained() {
		this.channel.addInterceptor(this.interceptor);
		QueueChannel deadReplyChannel = new QueueChannel();
		QueueChannel errorsReplyChannel = new QueueChannel();
		Map<String, Object> errorChannelHeaders = new HashMap<>();
		errorChannelHeaders.put(MessageHeaders.REPLY_CHANNEL, errorsReplyChannel);
		errorChannelHeaders.put(MessageHeaders.ERROR_CHANNEL, errorsReplyChannel);
		this.channel.send(new ErrorMessage(
				new MessagingException(
						MessageBuilder.withPayload("hi").setHeader("b3", "000000000000000a-000000000000000a")
								.setReplyChannel(deadReplyChannel).setErrorChannel(deadReplyChannel).build()),
				errorChannelHeaders));

		this.message = this.channel.receive();

		assertThat(this.message).isNotNull();

		// Parse fails if trace or span ID are missing
		String b3 = this.message.getHeaders().get("b3", String.class);
		// b3 can be traceid-spanid-sampled(-parentid)
		// the latter is not yet supported in otel
		B3Context b3Context = new B3Context(b3);
		assertThat(b3Context.traceId).endsWith("000000000000000a");
		assertThat(b3Context.spanId).doesNotEndWith("000000000000000a");
		assertThat(this.spans).hasSize(2);
		assertThat(this.message.getHeaders().getReplyChannel()).isSameAs(errorsReplyChannel);
		assertThat(this.message.getHeaders().getErrorChannel()).isSameAs(errorsReplyChannel);
	}

	@Test
	public void errorMessageOriginalMessageRetained() {
		this.channel.addInterceptor(this.interceptor);
		Message<?> originalMessage = MessageBuilder.withPayload("Hello").setHeader("header", "value").build();
		Message<?> failedMessage = MessageBuilder.fromMessage(originalMessage).removeHeader("header").build();
		this.channel.send(
				new ErrorMessage(new MessagingException(failedMessage), originalMessage.getHeaders(), originalMessage));

		this.message = this.channel.receive();

		assertThat(this.message).isNotNull();
		assertThat(this.message).isInstanceOfSatisfying(ErrorMessage.class, errorMessage -> {
			assertThat(errorMessage.getOriginalMessage()).isSameAs(originalMessage);
			assertThat(errorMessage.getHeaders().get("header")).isEqualTo("value");
		});
	}

	@Test
	public void errorMessageHeadersWithNullPayloadRetained() {
		this.channel.addInterceptor(this.interceptor);
		Map<String, Object> errorChannelHeaders = new HashMap<>();
		errorChannelHeaders.put("b3", "000000000000000a-000000000000000a");
		this.channel.send(new ErrorMessage(new MessagingException("exception"), errorChannelHeaders));

		this.message = this.channel.receive();

		String b3 = this.message.getHeaders().get("b3", String.class);
		// b3 can be traceid-spanid-sampled(-parentid)
		// the latter is not yet supported in otel
		B3Context b3Context = new B3Context(b3);
		assertThat(b3Context.traceId).endsWith("000000000000000a");
		assertThat(b3Context.spanId).doesNotEndWith("000000000000000a");
		assertThat(this.spans).hasSize(2);
	}

	@Test
	public void should_store_kafka_as_remote_service_name_when_kafka_header_is_present() {
		ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
		channel.addInterceptor(this.interceptor);
		List<Message<?>> messages = new ArrayList<>();
		channel.subscribe(messages::add);

		Map<String, Object> headers = new HashMap<>();
		headers.put("kafka_messageKey", "hello");
		channel.send(MessageBuilder.createMessage("foo", new MessageHeaders(headers)));

		assertThat(this.spans).extracting(FinishedSpan::getRemoteServiceName).contains("kafka");
	}

	@Test
	public void should_store_rabbitmq_as_remote_service_name_when_rabbit_header_is_present() {
		ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
		channel.addInterceptor(this.interceptor);
		List<Message<?>> messages = new ArrayList<>();
		channel.subscribe(messages::add);

		Map<String, Object> headers = new HashMap<>();
		headers.put("amqp_receivedRoutingKey", "hello");
		channel.send(MessageBuilder.createMessage("foo", new MessageHeaders(headers)));

		assertThat(this.spans).extracting(FinishedSpan::getRemoteServiceName).contains("rabbitmq");
	}

	@Test
	public void should_store_broker_as_remote_service_name_when_no_special_headers_were_found() {
		ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
		channel.addInterceptor(this.interceptor);
		List<Message<?>> messages = new ArrayList<>();
		channel.subscribe(messages::add);

		Map<String, Object> headers = new HashMap<>();
		channel.send(MessageBuilder.createMessage("foo", new MessageHeaders(headers)));

		assertThat(this.spans).extracting(FinishedSpan::getRemoteServiceName).containsOnly("broker", null);
	}

	public ChannelInterceptor producerSideOnly(ChannelInterceptor delegate) {
		return new ChannelInterceptorAdapter() {
			@Override
			public Message<?> preSend(Message<?> message, MessageChannel channel) {
				return delegate.preSend(message, channel);
			}

			@Override
			public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
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
			public void afterReceiveCompletion(Message<?> message, MessageChannel channel, Exception ex) {
				delegate.afterReceiveCompletion(message, channel, ex);
			}
		};
	}

	ExecutorChannelInterceptor executorSideOnly(ChannelInterceptor delegate) {
		class ExecutorSideOnly extends ChannelInterceptorAdapter implements ExecutorChannelInterceptor {

			@Override
			public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
				return ((ExecutorChannelInterceptor) delegate).beforeHandle(message, channel, handler);
			}

			@Override
			public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler,
					Exception ex) {
				((ExecutorChannelInterceptor) delegate).afterMessageHandled(message, channel, handler, ex);
			}

		}
		return new ExecutorSideOnly();
	}

}

class B3Context {

	public String traceId;

	public String spanId;

	public String sampled;

	public String parentSpanId;

	B3Context(String b3Header) {
		BDDAssertions.then(b3Header).isNotEmpty();
		String[] split = b3Header.split("-");
		if (split.length == 4) {
			this.traceId = split[0];
			this.spanId = split[1];
			this.sampled = split[2];
			this.parentSpanId = split[3];
		}
		else {
			this.traceId = split[0];
			this.spanId = split[1];
			this.sampled = split[2];
		}
	}

}
