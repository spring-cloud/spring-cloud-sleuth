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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PreDestroy;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
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
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ported from
 * org.springframework.cloud.sleuth.instrument.messaging.TraceChannelInterceptorTest to
 * allow sleuth to decommission its implementation.
 *
 * @author Marcin Grzejszczak
 */
@SpringBootTest(classes = ITTracingChannelInterceptor.App.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@RunWith(SpringRunner.class)
@DirtiesContext
public class ITTracingChannelInterceptor implements MessageHandler {

	@Autowired
	@Qualifier("directChannel")
	DirectChannel directChannel;

	@Autowired
	@Qualifier("executorChannel")
	ExecutorChannel executorChannel;

	@Autowired
	Tracer tracer;

	@Autowired
	List<zipkin2.Span> spans;

	@Autowired
	MessagingTemplate messagingTemplate;

	Message<?> message;

	Span currentSpan;

	@Override
	public void handleMessage(Message<?> msg) {
		this.message = msg;
		this.currentSpan = this.tracer.currentSpan();
		if (this.message.getHeaders().containsKey("THROW_EXCEPTION")) {
			throw new RuntimeException("A terrible exception has occurred");
		}
	}

	@Before
	public void init() {
		this.directChannel.subscribe(this);
		this.executorChannel.subscribe(this);
	}

	@After
	public void close() {
		this.directChannel.unsubscribe(this);
		this.executorChannel.unsubscribe(this);
	}

	// formerly known as TraceChannelInterceptorTest.executableSpanCreation
	@Test
	public void propagatesNoopSpan() {
		this.directChannel.send(
				MessageBuilder.withPayload("hi").setHeader("X-B3-Sampled", "0").build());

		assertThat(this.message.getHeaders()).containsEntry("X-B3-Sampled", "0");

		assertThat(this.currentSpan.isNoop()).isTrue();
	}

	@Test
	public void messageHeadersStillMutableForStomp() {
		this.directChannel.send(MessageBuilder.withPayload("hi")
				.setHeader("stompCommand", "DISCONNECT").build());

		assertThat(MessageHeaderAccessor.getAccessor(this.message,
				MessageHeaderAccessor.class)).isNotNull();

		this.message = null;
		this.directChannel.send(MessageBuilder.withPayload("hi")
				.setHeader("simpMessageType", "sth").build());

		assertThat(MessageHeaderAccessor.getAccessor(this.message,
				MessageHeaderAccessor.class)).isNotNull();
	}

	@Test
	public void messageHeadersImmutableForNonStomp() {
		this.directChannel
				.send(MessageBuilder.withPayload("hi").setHeader("foo", "bar").build());

		assertThat(MessageHeaderAccessor.getAccessor(this.message,
				MessageHeaderAccessor.class)).isNull();
	}

	@Configuration
	@EnableAutoConfiguration
	static class App {

		ExecutorService service = Executors.newSingleThreadExecutor();

		@Bean
		List<zipkin2.Span> spans() {
			return new ArrayList<>();
		}

		@Bean
		Tracing tracing() {
			return Tracing.newBuilder()
					.currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
							.addScopeDecorator(StrictScopeDecorator.create()).build())
					.spanReporter(spans()::add).build();
		}

		@Bean
		Tracer tracer() {
			return tracing().tracer();
		}

		@Bean
		ExecutorChannel executorChannel() {
			return new ExecutorChannel(this.service);
		}

		@PreDestroy
		public void destroy() {
			this.service.shutdown();
		}

		@Bean
		DirectChannel directChannel() {
			return new DirectChannel();
		}

		@Bean
		public MessagingTemplate messagingTemplate() {
			return new MessagingTemplate(directChannel());
		}

	}

}
