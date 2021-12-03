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

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.JsonMessageConverter;
import org.springframework.cloud.function.json.JacksonMapper;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Span.Builder;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.cloud.sleuth.tracer.SimpleTracer;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;

class TraceFunctionAroundWrapperTests {

	CompositeMessageConverter messageConverter = new CompositeMessageConverter(
			Collections.singletonList(new JsonMessageConverter(new JacksonMapper(new ObjectMapper()))));

	SimpleFunctionRegistry catalog = new SimpleFunctionRegistry(new DefaultConversionService(), messageConverter,
			new JacksonMapper(new ObjectMapper()));

	SimpleTracer tracer = new SimpleTracer();

	MockEnvironment mockEnvironment = mockEnvironment();

	TraceFunctionAroundWrapper wrapper = new TraceFunctionAroundWrapper(mockEnvironment, tracer, testPropagator(),
			new MessageHeaderPropagatorSetter(), new MessageHeaderPropagatorGetter()) {
		@Override
		MessageAndSpan getMessageAndSpans(Message<?> resultMessage, String name, Span spanFromMessage) {
			return new MessageAndSpan(resultMessage, spanFromMessage);
		}
	};

	private Propagator testPropagator() {
		return new Propagator() {

			@Override
			public <C> void inject(TraceContext context, C carrier, Setter<C> setter) {
				setter.set(carrier, "superHeader", "test");
			}

			@Override
			public List<String> fields() {
				return Collections.singletonList("superHeader");
			}

			@Override
			public <C> Builder extract(C carrier, Getter<C> getter) {
				return tracer.spanBuilder();
			}
		};
	}

	@Test
	void test_tracing_with_supplier() {
		FunctionRegistration<Greeter> registration = new FunctionRegistration<>(new Greeter(), "greeter")
				.type(FunctionType.of(Greeter.class));
		catalog.register(registration);
		FunctionInvocationWrapper function = catalog.lookup("greeter");

		Message<?> result = (Message<?>) wrapper.apply(null, function);

		assertThat(result.getPayload()).isEqualTo("hello");
		assertThat(tracer.getOnlySpan().name).isEqualTo("greeter");
		assertThatAllSpansAreStartedAndStopped();
	}

	@Test
	void test_tracing_with_function() {
		FunctionRegistration<GreeterFunction> registration = new FunctionRegistration<>(new GreeterFunction(),
				"greeter").type(FunctionType.of(GreeterFunction.class));
		catalog.register(registration);
		FunctionInvocationWrapper function = catalog.lookup("greeter");

		Message<?> result = (Message<?>) wrapper
				.apply(MessageBuilder.withPayload("hello").setHeader("superHeader", "someValue").build(), function);

		assertThat(result.getPayload()).isEqualTo("HELLO");
		assertThat(tracer.spans).hasSize(3);
		assertThat(tracer.spans.get(0).name).isEqualTo("handle");
		assertThat(tracer.spans.get(1).name).isEqualTo("greeter");
		assertThat(tracer.spans.get(2).name).isEqualTo("send");
		assertThatAllSpansAreStartedAndStopped();
	}

	@Test
	void test_tracing_with_function_and_input_as_non_message() {
		FunctionRegistration<GreeterFunction> registration = new FunctionRegistration<>(new GreeterFunction(),
				"greeter").type(FunctionType.of(GreeterFunction.class));
		catalog.register(registration);
		FunctionInvocationWrapper function = catalog.lookup("greeter");

		String result = (String) wrapper.apply("hello", function);

		assertThat(result).isEqualTo("HELLO");
		assertThat(tracer.spans).isEmpty();
	}

	@Test
	void test_tracing_with_consumer() {
		GreeterConsumer consumer = new GreeterConsumer();
		FunctionRegistration<GreeterConsumer> registration = new FunctionRegistration<>(consumer, "greeter")
				.type(FunctionType.of(GreeterConsumer.class));
		catalog.register(registration);
		FunctionInvocationWrapper function = catalog.lookup("greeter");

		wrapper.apply(MessageBuilder.withPayload("hello").setHeader("superHeader", "someValue").build(), function);

		assertThat(consumer.result).isEqualTo("HELLO");
		assertThat(tracer.spans).hasSize(2);
		assertThat(tracer.spans.get(0).name).isEqualTo("handle");
		assertThat(tracer.spans.get(1).name).isEqualTo("greeter");
		assertThatAllSpansAreStartedAndStopped();
	}

	@Test
	void should_trace_when_reactive_mono_supplier() {
		FunctionRegistration<ReactiveMonoGreeter> registration = new FunctionRegistration<>(new ReactiveMonoGreeter(),
				"greeter").type(FunctionType.of(ReactiveMonoGreeter.class));
		catalog.register(registration);
		FunctionInvocationWrapper function = catalog.lookup("greeter");

		Message<?> result = ((Mono<Message<?>>) wrapper.apply(null, function)).block(Duration.ofSeconds(5));

		assertThat(result.getPayload()).isEqualTo("hello");
		assertThat(tracer.getOnlySpan().name).isEqualTo("greeter");
		assertThatAllSpansAreStartedAndStopped();
	}

	@Test
	void should_trace_when_reactive_mono_function() {
		FunctionRegistration<ReactiveMonoGreeterFunction> registration = new FunctionRegistration<>(
				new ReactiveMonoGreeterFunction(), "greeter").type(FunctionType.of(ReactiveMonoGreeterFunction.class));
		catalog.register(registration);
		FunctionInvocationWrapper function = catalog.lookup("greeter");

		Message<?> result = ((Mono<Message<?>>) wrapper.apply(
				Mono.just(MessageBuilder.withPayload("hello").setHeader("superHeader", "someValue").build()), function))
						.block(Duration.ofSeconds(5));

		assertThat(result.getPayload()).isEqualTo("HELLO");
		assertThat(tracer.spans).hasSize(3);
		assertThat(tracer.spans.get(0).name).isEqualTo("handle");
		assertThat(tracer.spans.get(1).name).isEqualTo("greeter");
		assertThat(tracer.spans.get(2).name).isEqualTo("send");
		assertThatAllSpansAreStartedAndStopped();
	}

	@Test
	void should_trace_when_reactive_flux_supplier() {
		FunctionRegistration<ReactiveFluxGreeter> registration = new FunctionRegistration<>(new ReactiveFluxGreeter(),
				"greeter").type(FunctionType.of(ReactiveFluxGreeter.class));
		catalog.register(registration);
		FunctionInvocationWrapper function = catalog.lookup("greeter");

		Message<?> result = ((Flux<Message<?>>) wrapper.apply(null, function)).blockFirst(Duration.ofSeconds(5));

		assertThat(result.getPayload()).isEqualTo("hello");
		assertThat(tracer.getOnlySpan().name).isEqualTo("greeter");
		assertThatAllSpansAreStartedAndStopped();
	}

	@Test
	void should_trace_when_reactive_flux_function() {
		FunctionRegistration<ReactiveFluxGreeterFunction> registration = new FunctionRegistration<>(
				new ReactiveFluxGreeterFunction(), "greeter").type(FunctionType.of(ReactiveFluxGreeterFunction.class));
		catalog.register(registration);
		FunctionInvocationWrapper function = catalog.lookup("greeter");

		Message<?> result = ((Flux<Message<?>>) wrapper.apply(
				Flux.just(MessageBuilder.withPayload("hello").setHeader("superHeader", "someValue").build()), function))
						.blockFirst(Duration.ofSeconds(5));

		assertThat(result.getPayload()).isEqualTo("HELLO");
		assertThat(tracer.spans).hasSize(3);
		assertThat(tracer.spans.get(0).name).isEqualTo("handle");
		assertThat(tracer.spans.get(1).name).isEqualTo("greeter");
		assertThat(tracer.spans.get(2).name).isEqualTo("send");
		assertThatAllSpansAreStartedAndStopped();
	}

	@Test
	void should_trace_when_reactive_flux_consumer() {
		ReactiveFluxGreeterConsumer consumer = new ReactiveFluxGreeterConsumer(this.tracer);
		FunctionRegistration<ReactiveFluxGreeterConsumer> registration = new FunctionRegistration<>(consumer, "greeter")
				.type(FunctionType.of(ReactiveFluxGreeterConsumer.class));
		catalog.register(registration);
		FunctionInvocationWrapper function = catalog.lookup("greeter");

		wrapper.apply(Flux.just(MessageBuilder.withPayload("hello").setHeader("superHeader", "someValue").build()),
				function);

		assertThat(consumer.result).isEqualTo("HELLO");
		assertThat(tracer.spans).hasSize(2);
		assertThat(tracer.spans.get(0).name).isEqualTo("handle");
		assertThat(tracer.spans.get(1).name).isEqualTo("greeter");
		assertThatAllSpansAreStartedAndStopped();
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

	private MockEnvironment mockEnvironment() {
		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty("spring.cloud.stream.bindings.greeter-in-0.destination", "oleg");
		mockEnvironment.setProperty("spring.cloud.stream.bindings.greeter-out-0.destination", "bob");
		return mockEnvironment;
	}

	private void assertThatAllSpansAreStartedAndStopped() {
		assertThat(tracer.spans.stream().allMatch(s -> s.started && s.ended))
				.as("All spans must be started and stopped").isTrue();
	}

	private static class Greeter implements Supplier<String> {

		@Override
		public String get() {
			return "hello";
		}

	}

	private static class GreeterFunction implements Function<String, String> {

		@Override
		public String apply(String in) {
			return in.toUpperCase();
		}

	}

	private static class GreeterConsumer implements Consumer<String> {

		String result;

		@Override
		public void accept(String in) {
			this.result = in.toUpperCase();
		}

	}

	private static class ReactiveMonoGreeter implements Supplier<Mono<Message<String>>> {

		@Override
		public Mono<Message<String>> get() {
			return Mono.just(MessageBuilder.withPayload("hello").build());
		}

	}

	private static class ReactiveMonoGreeterFunction implements Function<Mono<Message<String>>, Mono<Message<String>>> {

		@Override
		public Mono<Message<String>> apply(Mono<Message<String>> in) {
			return in.map(s -> MessageBuilder.fromMessage(s).withPayload(s.getPayload().toUpperCase()).build());
		}

	}

	private static class ReactiveFluxGreeter implements Supplier<Flux<Message<String>>> {

		@Override
		public Flux<Message<String>> get() {
			return Flux.just(MessageBuilder.withPayload("hello").build());
		}

	}

	private static class ReactiveFluxGreeterFunction implements Function<Flux<Message<String>>, Flux<Message<String>>> {

		@Override
		public Flux<Message<String>> apply(Flux<Message<String>> in) {
			return in.map(s -> MessageBuilder.fromMessage(s).withPayload(s.getPayload().toUpperCase()).build());
		}

	}

	private static class ReactiveFluxGreeterConsumer implements Consumer<Flux<Message<String>>> {

		String result;

		private final Tracer tracer;

		ReactiveFluxGreeterConsumer(Tracer tracer) {
			this.tracer = tracer;
		}

		@Override
		public void accept(Flux<Message<String>> in) {
			in.map(s -> s.getPayload().toUpperCase()).doOnNext(s -> {
				result = s;
			}).doOnNext(s -> {
				tracer.currentSpan().end();
				tracer.withSpan(null);
			}).subscribe();
		}

	}

}
