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

import java.util.function.Function;

import brave.Span;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StreamFunctionAdapterTests.Config.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class StreamFunctionAdapterTests {

	@Autowired(required = false)
	TracingChannelInterceptor tracingChannelInterceptor;

	@Autowired
	TestSpanHandler handler;

	@Autowired
	InputDestination inputDestination;

	@Autowired
	OutputDestination outputDestination;

	@Test
	void should_instrument_a_simple_message_to_message_function() {
		assertThat(tracingChannelInterceptor).as("Ensure that we're doing instrumentation via function wrapper")
				.isNull();

		this.inputDestination.send(MessageBuilder.withPayload("hello".getBytes())
				.setHeader("b3", "4883117762eb9420-4883117762eb9420-1").build());

		Message<byte[]> message = this.outputDestination.receive(200L);
		assertThat(message).isNotNull();
		assertThat(message.getPayload()).containsAnyOf("HELLO".getBytes());
		String b3 = message.getHeaders().get("b3", String.class);
		assertThat(b3).startsWith("4883117762eb9420");

		assertThat(this.handler.spans()).hasSize(3).extracting("kind").containsOnly(Span.Kind.CONSUMER, null,
				Span.Kind.PRODUCER);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@ImportAutoConfiguration(TestChannelBinderConfiguration.class)
	static class Config {

		@Bean
		TestSpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		@Bean
		SimpleFunction simpleFunction() {
			return new SimpleFunction();
		}

	}

}

class SimpleFunction implements Function<Message<String>, Message<String>> {

	private static final Logger log = LoggerFactory.getLogger(SimpleFunction.class);

	@Override
	public Message<String> apply(Message<String> input) {
		log.info("Hello from simple [{}]", input);
		return MessageBuilder.createMessage(input.getPayload().toUpperCase(), input.getHeaders());
	}

}
