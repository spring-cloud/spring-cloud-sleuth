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

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.handler.SpanHandler;
import brave.propagation.B3SingleFormat;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.PollableChannel;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 */
@SpringBootTest(classes = TraceContextPropagationChannelInterceptorTests.App.class,
		properties = "spring.sleuth.integration.enabled=true")
@DirtiesContext
public class TraceContextPropagationChannelInterceptorTests {

	@Autowired
	@Qualifier("channel")
	private PollableChannel channel;

	@Autowired
	private Tracing tracing;

	@Autowired
	private TestSpanHandler spans;

	@AfterEach
	public void close() {
		this.spans.clear();
	}

	@Test
	public void testSpanPropagation() {
		Span span = this.tracing.tracer().nextSpan().name("http:testSendMessage").start();
		String expectedSpanId = span.context().spanIdString();

		try (Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span)) {
			this.channel.send(MessageBuilder.withPayload("hi").build());
		}
		finally {
			span.finish();
		}

		assertThatNewSpanIdWasSetOnMessage(expectedSpanId);
	}

	private void assertThatNewSpanIdWasSetOnMessage(String expectedSpanId) {
		Message<?> message = this.channel.receive(0);
		assertThat(message).as("message was null").isNotNull();

		String b3 = message.getHeaders().get("b3", String.class);
		// Trace and Span IDs are implicitly checked
		TraceContext extracted = B3SingleFormat.parseB3SingleFormat(b3).context();

		assertThat(extracted.spanIdString()).as("spanId was equal to parent's id").isNotEqualTo(expectedSpanId);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class App {

		@Bean
		public QueueChannel channel() {
			return new QueueChannel();
		}

		@Bean
		Sampler testSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

	}

}
