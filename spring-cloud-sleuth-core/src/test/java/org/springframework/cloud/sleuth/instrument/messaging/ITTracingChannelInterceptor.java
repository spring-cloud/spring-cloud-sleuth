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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PreDestroy;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ported from org.springframework.cloud.sleuth.instrument.messaging.TraceChannelInterceptorTest to
 * allow sleuth to decommission its implementation.
 */
@SpringBootTest(classes = ITTracingChannelInterceptor.App.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
@RunWith(SpringRunner.class)
@DirtiesContext
public class ITTracingChannelInterceptor implements MessageHandler {

	@Autowired @Qualifier("directChannel") DirectChannel directChannel;

	@Autowired @Qualifier("executorChannel") ExecutorChannel executorChannel;

	@Autowired Tracer tracer;

	@Autowired List<zipkin2.Span> spans;

	@Autowired MessagingTemplate messagingTemplate;

	Message<?> message;
	Span currentSpan;

	@Override public void handleMessage(Message<?> msg) {
		message = msg;
		currentSpan = tracer.currentSpan();
		if (message.getHeaders().containsKey("THROW_EXCEPTION")) {
			throw new RuntimeException("A terrible exception has occurred");
		}
	}

	@Before public void init() {
		directChannel.subscribe(this);
		executorChannel.subscribe(this);
	}

	@After public void close() {
		directChannel.unsubscribe(this);
		executorChannel.unsubscribe(this);
	}

	// formerly known as TraceChannelInterceptorTest.executableSpanCreation
	@Test public void propagatesNoopSpan() {
		directChannel.send(MessageBuilder.withPayload("hi").setHeader("X-B3-Sampled", "0")
				.build());

		assertThat(message.getHeaders()).containsEntry("X-B3-Sampled", "0");

		assertThat(currentSpan.isNoop()).isTrue();
	}

	@Test public void messageHeadersStillMutableForStomp() {
		directChannel.send(MessageBuilder.withPayload("hi").setHeader("stompCommand", "DISCONNECT")
				.build());

		assertThat(
				MessageHeaderAccessor.getAccessor(message, MessageHeaderAccessor.class))
				.isNotNull();

		message = null;
		directChannel.send(MessageBuilder.withPayload("hi").setHeader("simpMessageType", "sth")
				.build());

		assertThat(
				MessageHeaderAccessor.getAccessor(message, MessageHeaderAccessor.class))
				.isNotNull();
	}

	@Test public void messageHeadersImmutableForNonStomp() {
		directChannel.send(MessageBuilder.withPayload("hi").setHeader("foo", "bar")
				.build());

		assertThat(
				MessageHeaderAccessor.getAccessor(message, MessageHeaderAccessor.class))
				.isNull();
	}

	@Configuration @EnableAutoConfiguration static class App {

		@Bean List<zipkin2.Span> spans() {
			return new ArrayList<>();
		}

		@Bean Tracing tracing() {
			return Tracing.newBuilder()
					.currentTraceContext(new StrictCurrentTraceContext())
					.spanReporter(spans()::add).build();
		}

		@Bean Tracer tracer() {
			return tracing().tracer();
		}

		ExecutorService service = Executors.newSingleThreadExecutor();

		@Bean ExecutorChannel executorChannel() {
			return new ExecutorChannel(this.service);
		}

		@PreDestroy
		public void destroy() {
			this.service.shutdown();
		}

		@Bean DirectChannel directChannel() {
			return new DirectChannel();
		}

		@Bean public MessagingTemplate messagingTemplate() {
			return new MessagingTemplate(directChannel());
		}

		@Bean @GlobalChannelInterceptor
		public ChannelInterceptor tracingChannelInterceptor(Tracing tracing) {
			return TracingChannelInterceptor.create(tracing);
		}
	}
}