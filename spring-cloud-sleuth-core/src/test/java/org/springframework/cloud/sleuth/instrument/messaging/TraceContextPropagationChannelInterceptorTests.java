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

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.messaging.TraceContextPropagationChannelInterceptorTests.App;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.PollableChannel;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Spencer Gibb
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes=App.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
public class TraceContextPropagationChannelInterceptorTests {

	@Autowired
	@Qualifier("channel")
	private PollableChannel channel;

	@Autowired
	private Tracer tracer;

	@After
	public void close() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void testSpanPropagation() {

		Span span = this.tracer.createSpan("http:testSendMessage", new AlwaysSampler());
		this.channel.send(MessageBuilder.withPayload("hi").build());
		Long expectedSpanId = span.getSpanId();
		this.tracer.close(span);

		Message<?> message = this.channel.receive(0);

		assertNotNull("message was null", message);

		Long spanId = Span
				.hexToId(message.getHeaders().get(Span.SPAN_ID_NAME, String.class));
		assertNotEquals("spanId was equal to parent's id", expectedSpanId,  spanId);

		long traceId = Span
				.hexToId(message.getHeaders().get(Span.TRACE_ID_NAME, String.class));
		assertNotNull("traceId was null", traceId);

		Long parentId = Span
				.hexToId(message.getHeaders().get(Span.PARENT_ID_NAME, String.class));
		assertEquals("parentId was not equal to parent's id", expectedSpanId,  parentId);

	}

	@Configuration
	@EnableAutoConfiguration
	static class App {

		@Bean
		public QueueChannel channel() {
			return new QueueChannel();
		}

		@Bean
		Sampler testSampler() {
			return new AlwaysSampler();
		}
	}
}
