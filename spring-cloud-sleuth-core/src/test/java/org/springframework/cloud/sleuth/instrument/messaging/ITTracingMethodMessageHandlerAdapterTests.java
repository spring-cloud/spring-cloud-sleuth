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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import brave.Span;
import brave.Tracing;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.aws.messaging.listener.QueueMessageHandler;
import org.springframework.cloud.aws.messaging.listener.annotation.SqsListener;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

@SpringBootTest(
		classes = ITTracingMethodMessageHandlerAdapterTests.TestingConfiguration.class,
		webEnvironment = NONE)
@RunWith(SpringRunner.class)
public class ITTracingMethodMessageHandlerAdapterTests {

	private static final String TRACE_ID = "12345678123456781234567812345678";

	private static final String SPAN_ID = "1234567812345678";

	@Autowired
	ApplicationContext applicationContext;

	@Autowired
	SqsQueueMessageHandlerFactory messageHandlerFactory;

	@Autowired
	TestingMessageHandler testingMessageHandler;

	@Autowired
	Tracing tracing;

	private QueueMessageHandler messageHandler;

	@Before
	public void setup() {
		messageHandler = messageHandlerFactory.createQueueMessageHandler();
		messageHandler.setApplicationContext(applicationContext);
		messageHandler.afterPropertiesSet();
	}

	@Test
	public void aSpanGetsPutIntoScopeWithoutHeadersOnTheMessage() {
		AtomicReference<Span> probedSpan = new AtomicReference<>();
		testingMessageHandler.withTestProbe(((headers, s) -> {
			probedSpan.set(tracing.tracer().currentSpan());
		}));

		messageHandler.handleMessage(new GenericMessage<>("message",
				Collections.singletonMap("LogicalResourceId", "test")));

		assertThat(probedSpan.get()).isNotNull();
	}

	@Test
	public void theSpanThatIsInTheHeadersIsUsedForTheTraceScope() {
		AtomicReference<Span> probedSpan = new AtomicReference<>();
		testingMessageHandler.withTestProbe(((headers, s) -> {
			probedSpan.set(tracing.tracer().currentSpan());
		}));

		Map<String, Object> headers = new HashMap<>();
		headers.put("LogicalResourceId", "test");
		headers.put("X-B3-TraceId", TRACE_ID);
		headers.put("X-B3-SpanId", SPAN_ID);
		headers.put("X-B3-Sampled", "1");
		messageHandler.handleMessage(new GenericMessage<>("message", headers));

		assertThat(probedSpan.get()).isNotNull();
		assertThat(probedSpan.get().context().traceIdString()).isEqualTo(TRACE_ID);
		assertThat(probedSpan.get().context().sampled()).isTrue();
	}

	@EnableAutoConfiguration
	@Configuration
	static class TestingConfiguration {

		@Bean
		TestingMessageHandler testingMessageHandler() {
			return new TestingMessageHandler();
		}

	}

	static class TestingMessageHandler {

		private BiConsumer<MessageHeaders, String> testProbe;

		void withTestProbe(BiConsumer<MessageHeaders, String> consumer) {
			this.testProbe = consumer;
		}

		@SqsListener("test")
		public void handle(MessageHeaders header, String payload) {
			testProbe.accept(header, payload);
		}

	}

}
