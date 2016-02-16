/*
 * Copyright 2013-2015 the original author or authors.
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
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.cloud.sleuth.instrument.messaging.TraceChannelInterceptorTests.App;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Dave Syer
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = App.class)
@IntegrationTest
@DirtiesContext
public class TraceChannelInterceptorTests implements MessageHandler {

	@Autowired
	@Qualifier("channel")
	private DirectChannel channel;

	@Autowired
	private Tracer tracer;

	@Autowired
	private MessagingTemplate messagingTemplate;

	@Autowired
	private App app;

	private Message<?> message;

	private Span span;

	@Override
	public void handleMessage(Message<?> message) throws MessagingException {
		this.message = message;
		this.span = TestSpanContextHolder.getCurrentSpan();
	}

	@Before
	public void init() {
		this.channel.subscribe(this);
	}

	@After
	public void close() {
		TestSpanContextHolder.removeCurrentSpan();
		this.channel.unsubscribe(this);
	}

	@Test
	public void nonExportableSpanCreation() {
		this.channel.send(MessageBuilder.withPayload("hi")
				.setHeader(Span.NOT_SAMPLED_NAME, "true").build());
		assertNotNull("message was null", this.message);

		String spanId = this.message.getHeaders().get(Span.SPAN_ID_NAME, String.class);
		assertNotNull("spanId was null", spanId);
		assertNull(TestSpanContextHolder.getCurrentSpan());
		assertFalse(this.span.isExportable());
	}

	@Test
	public void parentSpanIncluded() {
		this.channel.send(MessageBuilder.withPayload("hi")
				.setHeader(Span.TRACE_ID_NAME, Span.toHex(10L))
				.setHeader(Span.SPAN_ID_NAME, Span.toHex(20L)).build());
		assertNotNull("message was null", this.message);

		String spanId = this.message.getHeaders().get(Span.SPAN_ID_NAME, String.class);
		assertNotNull("spanId was null", spanId);
		long traceId = Span
				.fromHex(this.message.getHeaders().get(Span.TRACE_ID_NAME, String.class));
		then(traceId).isEqualTo(10L);
		then(spanId).isNotEqualTo(20L);
		assertEquals(1, this.app.events.size());
	}

	@Test
	public void spanCreation() {
		this.channel.send(MessageBuilder.withPayload("hi").build());
		assertNotNull("message was null", this.message);

		String spanId = this.message.getHeaders().get(Span.SPAN_ID_NAME, String.class);
		assertNotNull("spanId was null", spanId);

		String traceId = this.message.getHeaders().get(Span.TRACE_ID_NAME, String.class);
		assertNotNull("traceId was null", traceId);
		assertNull(TestSpanContextHolder.getCurrentSpan());
	}

	@Test
	public void headerCreation() {
		Span span = this.tracer.startTrace("http:testSendMessage", new AlwaysSampler());
		this.channel.send(MessageBuilder.withPayload("hi").build());
		this.tracer.close(span);
		assertNotNull("message was null", this.message);

		String spanId = this.message.getHeaders().get(Span.SPAN_ID_NAME, String.class);
		assertNotNull("spanId was null", spanId);

		String traceId = this.message.getHeaders().get(Span.TRACE_ID_NAME, String.class);
		assertNotNull("traceId was null", traceId);
		assertNull(TestSpanContextHolder.getCurrentSpan());
	}

	// TODO: Refactor to parametrized test together with sending messages via channel
	@Test
	public void headerCreationViaMessagingTemplate() {
		Span span = this.tracer.startTrace("http:testSendMessage", new AlwaysSampler());
		this.messagingTemplate.send(MessageBuilder.withPayload("hi").build());
		this.tracer.close(span);
		assertNotNull("message was null", this.message);

		String spanId = this.message.getHeaders().get(Span.SPAN_ID_NAME, String.class);
		assertNotNull("spanId was null", spanId);

		String traceId = this.message.getHeaders().get(Span.TRACE_ID_NAME, String.class);
		assertNotNull("traceId was null", traceId);
		assertNull(TestSpanContextHolder.getCurrentSpan());
	}

	@Configuration
	@EnableAutoConfiguration
	static class App {

		private List<SpanReleasedEvent> events = new ArrayList<>();

		@EventListener
		public void handle(SpanReleasedEvent event) {
			this.events.add(event);
		}

		@Bean
		public DirectChannel channel() {
			return new DirectChannel();
		}

		@Bean
		public MessagingTemplate messagingTemplate() {
			return new MessagingTemplate(channel());
		}

	}
}
