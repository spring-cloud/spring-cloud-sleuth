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
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.JsonMessageConverter;
import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.tracer.SimpleTracer;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;

class TraceFunctionAroundWrapperTests {

	@Test
	void test_tracing_with_supplier() {
		CompositeMessageConverter messageConverter = new CompositeMessageConverter(
				Collections.singletonList(new JsonMessageConverter(new JacksonMapper(new ObjectMapper()))));

		SimpleTracer tracer = new SimpleTracer();
		TraceFunctionAroundWrapper wrapper = new TraceFunctionAroundWrapper(null, tracer, null, null, null) {
			@Override
			MessageAndSpan getMessageAndSpans(Message<?> resultMessage, String name, Span spanFromMessage) {
				return new MessageAndSpan(resultMessage, spanFromMessage);
			}
		};

		FunctionRegistration<Greeter> registration = new FunctionRegistration<>(new Greeter(), "greeter")
				.type(FunctionType.of(Greeter.class));
		SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(new DefaultConversionService(), messageConverter,
				new JacksonMapper(new ObjectMapper()));
		catalog.register(registration);
		FunctionInvocationWrapper function = catalog.lookup("greeter");
		Message<?> result = (Message<?>) wrapper.apply(null, function);
		assertThat(result.getPayload()).isEqualTo("hello");
		assertThat(tracer.getOnlySpan().name).isEqualTo("greeter");
	}

	@Test
	void should_clear_cache_on_refresh() {
		TraceFunctionAroundWrapper wrapper = new TraceFunctionAroundWrapper(null, null, null, null, null);
		wrapper.functionToDestinationCache.put("example", "entry");
		then(wrapper.functionToDestinationCache).isNotEmpty();

		wrapper.onApplicationEvent(null);

		then(wrapper.functionToDestinationCache).isEmpty();
	}

	@Test
	void should_point_to_proper_destination_when_working_with_function_definition() {
		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty("spring.cloud.stream.bindings.marcin-in-0.destination", "oleg");
		mockEnvironment.setProperty("spring.cloud.stream.bindings.marcin-out-0.destination", "bob");
		TraceFunctionAroundWrapper wrapper = new TraceFunctionAroundWrapper(mockEnvironment, null, null, null, null);

		assertThat(wrapper.inputDestination("marcin")).isEqualTo("oleg");

		wrapper.functionToDestinationCache.clear();

		assertThat(wrapper.outputDestination("marcin")).isEqualTo("bob");
	}

	@Test
	void should_point_to_proper_destination_when_working_with_remapped_functions() {
		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty("spring.cloud.stream.function.bindings.marcin-in-0", "input");
		mockEnvironment.setProperty("spring.cloud.stream.bindings.input.destination", "oleg");
		mockEnvironment.setProperty("spring.cloud.stream.function.bindings.marcin-out-0", "output");
		mockEnvironment.setProperty("spring.cloud.stream.bindings.output.destination", "bob");
		TraceFunctionAroundWrapper wrapper = new TraceFunctionAroundWrapper(mockEnvironment, null, null, null, null);

		assertThat(wrapper.inputDestination("marcin")).isEqualTo("oleg");

		wrapper.functionToDestinationCache.clear();

		assertThat(wrapper.outputDestination("marcin")).isEqualTo("bob");
	}

	private static class Greeter implements Supplier<String> {

		@Override
		public String get() {
			return "hello";
		}

	}

}
