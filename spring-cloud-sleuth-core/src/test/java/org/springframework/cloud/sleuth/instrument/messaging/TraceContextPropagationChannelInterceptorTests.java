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

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.sampler.Sampler;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.cloud.sleuth.util.SpanUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.PollableChannel;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Spencer Gibb
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = TraceContextPropagationChannelInterceptorTests.App.class)
@DirtiesContext
public class TraceContextPropagationChannelInterceptorTests {

	@Autowired @Qualifier("channel") private PollableChannel channel;

	@Autowired private Tracing tracing;
	@Autowired private ArrayListSpanReporter reporter;

	@After public void close() {
		this.reporter.clear();
	}

	@Test public void testSpanPropagation() {
		Span span = this.tracing.tracer().nextSpan().name("http:testSendMessage").start();
		String expectedSpanId = SpanUtil.idToHex(span.context().spanId());
		try (Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span)) {
			this.channel.send(MessageBuilder.withPayload("hi").build());
		} finally {
			span.finish();
		}

		Message<?> message = this.channel.receive(0);
		assertNotNull("message was null", message);

		String spanId =
				message.getHeaders().get(TraceMessageHeaders.SPAN_ID_NAME, String.class);
		assertNotEquals("spanId was equal to parent's id", expectedSpanId, spanId);

		String traceId = message.getHeaders()
				.get(TraceMessageHeaders.TRACE_ID_NAME, String.class);
		assertNotNull("traceId was null", traceId);

		String parentId = message.getHeaders()
				.get(TraceMessageHeaders.PARENT_ID_NAME, String.class);
		assertEquals("parentId was not equal to parent's id",
				this.reporter.getSpans().get(0).id(), parentId);

	}

	@Configuration
	@EnableAutoConfiguration
	static class App {

		@Bean public QueueChannel channel() {
			return new QueueChannel();
		}

		@Bean Sampler testSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean ArrayListSpanReporter reporter() {
			return new ArrayListSpanReporter();
		}
	}
}
