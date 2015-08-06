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

package org.springframework.cloud.sleuth.instrument.integration;

import static org.junit.Assert.assertNotNull;
import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;

import org.junit.After;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.PollableChannel;
import org.springframework.test.context.ContextConfiguration;

/**
 * @author Spencer Gibb
 */
@ContextConfiguration(classes = TraceContextPropagationChannelInterceptorTests.App.class)
public class TraceContextPropagationChannelInterceptorTests {
	private ConfigurableApplicationContext context;

	@After
	public void close() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void testSpanPropagation() {
		context = SpringApplication.run(App.class);

		PollableChannel channel = context.getBean("channel", PollableChannel.class);

		Trace trace = context.getBean(Trace.class);

		TraceScope traceScope = trace.startSpan("testSendMessage", new AlwaysSampler(), null);
		channel.send(MessageBuilder.withPayload("hi").build());
		traceScope.close();

		Message<?> message = channel.receive(0);

		assertNotNull("message was null", message);

		String spanId = message.getHeaders().get(SPAN_ID_NAME, String.class);
		assertNotNull("spanId was null", spanId);

		String traceId = message.getHeaders().get(TRACE_ID_NAME, String.class);
		assertNotNull("traceId was null", traceId);
	}

	@Configuration
	@EnableAutoConfiguration
	@MessageEndpoint
	@EnableIntegration
	static class App {

		@Bean
		public QueueChannel channel() {
			return new QueueChannel();
		}

	}
}
