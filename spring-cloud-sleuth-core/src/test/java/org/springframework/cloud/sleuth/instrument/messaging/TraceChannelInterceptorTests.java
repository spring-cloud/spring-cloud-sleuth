/*
 * Copyright 2013-2015 the original author or authors.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Log;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.assertions.ListOfSpans;
import org.springframework.cloud.sleuth.assertions.SleuthAssertions;
import org.springframework.cloud.sleuth.instrument.messaging.TraceChannelInterceptorTests.App;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.support.channel.BeanFactoryChannelResolver;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.core.DestinationResolver;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.SerializationUtils;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Dave Syer
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = App.class,
		properties = "spring.sleuth.integration.patterns=traced*",
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
public class TraceChannelInterceptorTests implements MessageHandler {

	@Autowired
	@Qualifier("tracedChannel")
	private DirectChannel tracedChannel;

	@Autowired
	@Qualifier("tracedExecutorChannel")
	private ExecutorChannel executorChannel;

	@Autowired
	@Qualifier("ignoredChannel")
	private DirectChannel ignoredChannel;

	@Autowired
	@Qualifier("tracedPollableChannel")
	private QueueChannel queueChannel;

	@Autowired
	private DestinationResolver<MessageChannel> resolver;

	@Autowired
	private Tracer tracer;

	@Autowired
	private MessagingTemplate messagingTemplate;

	@Autowired
	private ArrayListSpanAccumulator accumulator;

	@Autowired
	private TraceChannelInterceptor interceptor;

	private Message<?> message;

	private Span span;

	@Override
	public void handleMessage(Message<?> message) throws MessagingException {
		this.message = message;
		this.span = TestSpanContextHolder.getCurrentSpan();
		if (message.getHeaders().containsKey("THROW_EXCEPTION")) {
			throw new RuntimeException("A terrible exception has occurred");
		}
	}

	@Before
	public void init() {
		this.tracedChannel.subscribe(this);
		this.executorChannel.subscribe(this);
		this.ignoredChannel.subscribe(this);
		this.accumulator.getSpans().clear();
	}

	@After
	public void close() {
		then(ExceptionUtils.getLastException()).isNull();
		TestSpanContextHolder.removeCurrentSpan();
		this.tracedChannel.unsubscribe(this);
		this.executorChannel.unsubscribe(this);
		this.ignoredChannel.unsubscribe(this);
		this.accumulator.getSpans().clear();
	}

	@Test
	public void shouldWorkForEmptyQueue() {
		this.interceptor.beforeHandle(null, null, null);
		this.interceptor.afterMessageHandled(null, null, null, null);
		this.interceptor.afterSendCompletion(null, null, true, null);
		this.interceptor.preSend(null, null);

		thenNoSpanReported();
	}

	private void thenNoSpanReported() {
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(this.accumulator.getSpans()).isEmpty();
	}

	@Test
	public void shouldWorkForQueue() {
		this.queueChannel.send(MessageBuilder.withPayload("hi").build());
		this.message = this.queueChannel.receive();
		assertNotNull("message was null", this.message);

		String spanId = this.message.getHeaders().get(TraceMessageHeaders.SPAN_ID_NAME, String.class);
		then(spanId).isNotNull();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(this.accumulator.getSpans()).isNotEmpty();
	}

	@Test
	public void nonExportableSpanCreation() {
		this.tracedChannel.send(MessageBuilder.withPayload("hi")
				.setHeader(TraceMessageHeaders.SAMPLED_NAME, Span.SPAN_NOT_SAMPLED).build());
		assertNotNull("message was null", this.message);

		String spanId = this.message.getHeaders().get(TraceMessageHeaders.SPAN_ID_NAME, String.class);
		then(spanId).isNotNull();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(this.span.isExportable()).isFalse();
	}

	@Test
	public void executableSpanCreation() throws Exception {
		this.executorChannel.send(MessageBuilder.withPayload("hi")
				.setHeader(TraceMessageHeaders.SAMPLED_NAME, Span.SPAN_NOT_SAMPLED).build());
		Awaitility.await()
				.untilAsserted(() -> BDDAssertions.assertThat(this.message).isNotNull());

		String spanId = this.message.getHeaders().get(TraceMessageHeaders.SPAN_ID_NAME, String.class);
		then(spanId).isNotNull();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(this.span.isExportable()).isFalse();
	}

	@Test
	public void messageHeadersStillMutable() {
		this.tracedChannel.send(MessageBuilder.withPayload("hi")
				.setHeader(TraceMessageHeaders.SAMPLED_NAME, Span.SPAN_NOT_SAMPLED).build());
		assertNotNull("message was null", this.message);
		MessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(this.message, MessageHeaderAccessor.class);
		assertNotNull("Message header accessor should be still available", accessor);
	}

	@Test
	public void parentSpanIncluded() {
		this.tracedChannel.send(MessageBuilder.withPayload("hi")
				.setHeader(TraceMessageHeaders.TRACE_ID_NAME, Span.idToHex(10L))
				.setHeader(TraceMessageHeaders.SPAN_ID_NAME, Span.idToHex(20L)).build());
		then(this.message).isNotNull();

		thenMessageHasAllLogs();
	}

	@Test
	public void addsChannelInterceptorForDynamicDestinations() {
		this.resolver.resolveDestination("tracedChannel").send(MessageBuilder.withPayload("hi")
				.setHeader(TraceMessageHeaders.TRACE_ID_NAME, Span.idToHex(10L))
				.setHeader(TraceMessageHeaders.SPAN_ID_NAME, Span.idToHex(20L)).build());
		then(this.message).isNotNull();

		thenMessageHasAllLogs();
	}

	private void thenMessageHasAllLogs() {
		String spanId = this.message.getHeaders().get(TraceMessageHeaders.SPAN_ID_NAME, String.class);
		then(spanId).isNotNull();
		long traceId = Span
				.hexToId(this.message.getHeaders().get(TraceMessageHeaders.TRACE_ID_NAME, String.class));
		then(traceId).isEqualTo(10L);
		then(spanId).isNotEqualTo(20L);
		then(this.accumulator.getSpans()).hasSize(1);
		List<Log> logs = this.accumulator.getSpans().get(0).logs();
		then(logs).hasSize(4);
		then(logs.get(0).getEvent()).isEqualTo(Span.CLIENT_SEND);
		then(logs.get(1).getEvent()).isEqualTo(Span.SERVER_RECV);
		then(logs.get(2).getEvent()).isEqualTo(Span.SERVER_SEND);
		then(logs.get(3).getEvent()).isEqualTo(Span.CLIENT_RECV);
	}

	@Test
	public void spanCreation() {
		this.tracedChannel.send(MessageBuilder.withPayload("hi").build());
		then(this.message).isNotNull();

		String spanId = this.message.getHeaders().get(TraceMessageHeaders.SPAN_ID_NAME, String.class);
		then(spanId).isNotNull();

		String traceId = this.message.getHeaders().get(TraceMessageHeaders.TRACE_ID_NAME, String.class);
		then(traceId).isNotNull();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void shouldLogClientReceivedClientSentEventWhenTheMessageIsSentAndReceived() {
		this.tracedChannel.send(MessageBuilder.withPayload("hi").build());

		then(this.accumulator.getSpans()).hasSize(1);
		then(this.accumulator.getSpans().get(0).logs()).extracting("event").contains(Span.CLIENT_SEND,
				Span.CLIENT_RECV);
	}

	@Test
	public void shouldLogServerReceivedServerSentEventWhenTheMessageIsPropagatedToTheNextListener() {
		this.tracedChannel.send(MessageBuilder.withPayload("hi")
				.setHeader(TraceMessageHeaders.MESSAGE_SENT_FROM_CLIENT, true).build());

		then(this.accumulator.getSpans()).hasSize(1);
		then(this.accumulator.getSpans().get(0).logs()).extracting("event").contains(Span.SERVER_RECV,
				Span.SERVER_SEND);
	}

	@Test
	public void headerCreation() {
		Span span = this.tracer.createSpan("http:testSendMessage", new AlwaysSampler());
		this.tracedChannel.send(MessageBuilder.withPayload("hi").build());
		this.tracer.close(span);
		then(this.message).isNotNull();

		String spanId = this.message.getHeaders().get(TraceMessageHeaders.SPAN_ID_NAME, String.class);
		then(spanId).isNotNull();

		String traceId = this.message.getHeaders().get(TraceMessageHeaders.TRACE_ID_NAME, String.class);
		then(traceId).isNotNull();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	// TODO: Refactor to parametrized test together with sending messages via channel
	@Test
	public void headerCreationViaMessagingTemplate() {
		Span span = this.tracer.createSpan("http:testSendMessage", new AlwaysSampler());
		this.messagingTemplate.send(MessageBuilder.withPayload("hi").build());

		this.tracer.close(span);
		then(this.message).isNotNull();

		String spanId = this.message.getHeaders().get(TraceMessageHeaders.SPAN_ID_NAME, String.class);
		then(spanId).isNotNull();

		String traceId = this.message.getHeaders().get(TraceMessageHeaders.TRACE_ID_NAME, String.class);
		then(traceId).isNotNull();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void shouldCloseASpanWhenExceptionOccurred() {
		Span span = this.tracer.createSpan("http:testSendMessage", new AlwaysSampler());
		Map<String, String> errorHeaders = new HashMap<>();
		errorHeaders.put("THROW_EXCEPTION", "TRUE");

		try {
			this.messagingTemplate.send(
					MessageBuilder.withPayload("hi").copyHeaders(errorHeaders).build());
			SleuthAssertions.fail("Exception should occur");
		}
		catch (RuntimeException e) {
		}

		then(this.message).isNotNull();
		this.tracer.close(span);
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(new ListOfSpans(this.accumulator.getSpans()))
				.hasASpanWithTagEqualTo(Span.SPAN_ERROR_TAG_NAME,
						"A terrible exception has occurred");
	}

	@Test
	public void shouldNotTraceIgnoredChannel() {
		this.ignoredChannel.send(MessageBuilder.withPayload("hi").build());
		then(this.message).isNotNull();

		String spanId = this.message.getHeaders().get(TraceMessageHeaders.SPAN_ID_NAME, String.class);
		then(spanId).isNull();

		String traceId = this.message.getHeaders().get(TraceMessageHeaders.TRACE_ID_NAME, String.class);
		then(traceId).isNull();

		then(this.accumulator.getSpans()).isEmpty();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void downgrades128bitIdsByDroppingHighBits() {
		String hex128Bits = "463ac35c9f6413ad48485a3953bb6124";
		String lower64Bits = "48485a3953bb6124";
		this.tracedChannel.send(MessageBuilder.withPayload("hi")
				.setHeader(TraceMessageHeaders.TRACE_ID_NAME, hex128Bits)
				.setHeader(TraceMessageHeaders.SPAN_ID_NAME, Span.idToHex(20L)).build());
		then(this.message).isNotNull();

		long traceId = Span.hexToId(this.message.getHeaders()
				.get(TraceMessageHeaders.TRACE_ID_NAME, String.class));
		then(traceId).isEqualTo(Span.hexToId(lower64Bits));
	}

	@Test
	public void shouldNotBreakWhenInvalidHeadersAreSent() {
		this.tracedChannel.send(MessageBuilder.withPayload("hi")
				.setHeader(TraceMessageHeaders.PARENT_ID_NAME, "-")
				.setHeader(TraceMessageHeaders.TRACE_ID_NAME, Span.idToHex(10L))
				.setHeader(TraceMessageHeaders.SPAN_ID_NAME, Span.idToHex(20L)).build());

		then(this.message).isNotNull();
		then(this.accumulator.getSpans()).isNotEmpty();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void shouldShortenTheNameWhenItsTooLarge() {
		this.tracedChannel.send(MessageBuilder.withPayload("hi")
				.setHeader(TraceMessageHeaders.SPAN_NAME_NAME, bigName())
				.setHeader(TraceMessageHeaders.TRACE_ID_NAME, Span.idToHex(10L))
				.setHeader(TraceMessageHeaders.SPAN_ID_NAME, Span.idToHex(20L)).build());

		then(this.message).isNotNull();

		then(this.accumulator.getSpans()).isNotEmpty();
		this.accumulator.getSpans().forEach(span1 -> then(span1.getName().length()).isLessThanOrEqualTo(50));
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	private String bigName() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 60; i++) {
			sb.append("a");
		}
		return sb.toString();
	}

	@Test
	public void serializeMutableHeaders() throws Exception {
		Map<String, Object> headers = new HashMap<>();
		headers.put("foo", "bar");
		Message<?> message = new GenericMessage<>("test", headers);
		ChannelInterceptor immutableMessageInterceptor = new ChannelInterceptorAdapter() {
			@Override
			public Message<?> preSend(Message<?> message, MessageChannel channel) {
				MessageHeaderAccessor headers = MessageHeaderAccessor.getMutableAccessor(message);
				return new GenericMessage<Object>(message.getPayload(), headers.toMessageHeaders());
			}
		};
		this.tracedChannel.addInterceptor(immutableMessageInterceptor);

		this.tracedChannel.send(message);

		Message<?> output = (Message<?>) SerializationUtils.deserialize(SerializationUtils.serialize(this.message));
		then(output.getPayload()).isEqualTo("test");
		then(output.getHeaders().get("foo")).isEqualTo("bar");
		this.tracedChannel.removeInterceptor(immutableMessageInterceptor);
	}

	@Test
	public void workWithMessagingException() throws Exception {
		Message<?> message = new GenericMessage<>(new MessagingException(
				MessageBuilder.withPayload("hi")
						.setHeader(TraceMessageHeaders.TRACE_ID_NAME, Span.idToHex(10L))
						.setHeader(TraceMessageHeaders.SPAN_ID_NAME, Span.idToHex(20L)).build()
		));

		this.tracedChannel.send(message);

		String spanId = this.message.getHeaders().get(TraceMessageHeaders.SPAN_ID_NAME, String.class);
		then(message.getPayload()).isEqualTo(this.message.getPayload());
		then(spanId).isNotNull();
		long traceId = Span
				.hexToId(this.message.getHeaders().get(TraceMessageHeaders.TRACE_ID_NAME, String.class));
		then(traceId).isEqualTo(10L);
		then(spanId).isNotEqualTo(20L);
		then(this.accumulator.getSpans()).hasSize(1);
	}

	@Test
	public void errorMessageHeadersRetained() {
		QueueChannel deadReplyChannel = new QueueChannel();
		QueueChannel errorsReplyChannel = new QueueChannel();
		Map<String, Object> errorChannelHeaders = new HashMap<>();
		errorChannelHeaders.put(MessageHeaders.REPLY_CHANNEL, errorsReplyChannel);
		errorChannelHeaders.put(MessageHeaders.ERROR_CHANNEL, errorsReplyChannel);

		this.tracedChannel.send(new ErrorMessage(
				new MessagingException(MessageBuilder.withPayload("hi")
						.setHeader(TraceMessageHeaders.TRACE_ID_NAME, Span.idToHex(10L))
						.setHeader(TraceMessageHeaders.SPAN_ID_NAME, Span.idToHex(20L))
						.setReplyChannel(deadReplyChannel)
						.setErrorChannel(deadReplyChannel)
						.build()	),
				errorChannelHeaders));
		then(this.message).isNotNull();

		String spanId = this.message.getHeaders().get(TraceMessageHeaders.SPAN_ID_NAME, String.class);
		then(spanId).isNotNull();
		long traceId = Span
				.hexToId(this.message.getHeaders().get(TraceMessageHeaders.TRACE_ID_NAME, String.class));
		then(traceId).isEqualTo(10L);
		then(spanId).isNotEqualTo(20L);
		then(this.accumulator.getSpans()).hasSize(1);
		then(this.message.getHeaders().getReplyChannel()).isSameAs(errorsReplyChannel);
		then(this.message.getHeaders().getErrorChannel()).isSameAs(errorsReplyChannel);
	}

	@Configuration
	@EnableAutoConfiguration
	static class App {

		@Bean
		ArrayListSpanAccumulator arrayListSpanAccumulator() {
			return new ArrayListSpanAccumulator();
		}

		@Bean
		ExecutorChannel tracedExecutorChannel() {
			return new ExecutorChannel(Executors.newSingleThreadExecutor());
		}

		@Bean
		PollableChannel tracedPollableChannel() {
			return new QueueChannel(10);
		}

		@Bean
		DirectChannel tracedChannel() {
			return new DirectChannel();
		}

		@Bean
		DirectChannel ignoredChannel() {
			return new DirectChannel();
		}

		@Bean
		MessagingTemplate messagingTemplate() {
			return new MessagingTemplate(tracedChannel());
		}

		@Bean
		Sampler alwaysSampler() {
			return new AlwaysSampler();
		}

		@Bean
		BeanFactoryChannelResolver resolver(BeanFactory beanFactory) {
			return new BeanFactoryChannelResolver(beanFactory);
		}

	}
}
