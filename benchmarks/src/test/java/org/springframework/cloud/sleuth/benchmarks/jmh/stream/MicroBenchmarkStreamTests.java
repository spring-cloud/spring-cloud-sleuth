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

package org.springframework.cloud.sleuth.benchmarks.jmh.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import brave.Tracing;
import jmh.mbr.junit5.Microbenchmark;
import org.junit.platform.commons.annotation.Testable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.benchmarks.app.stream.SleuthBenchmarkingStreamApplication;
import org.springframework.cloud.sleuth.benchmarks.jmh.Pair;
import org.springframework.cloud.sleuth.benchmarks.jmh.TracerImplementation;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@Measurement(iterations = 10, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(4)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Microbenchmark
public class MicroBenchmarkStreamTests {

	@Benchmark
	@Testable
	public void testStream(BenchmarkContext context) throws Exception {
		context.run();
	}

	@State(Scope.Benchmark)
	public static class BenchmarkContext {

		volatile ConfigurableApplicationContext applicationContext;

		volatile InputDestination input;

		volatile OutputDestination output;

		@Param
		private Instrumentation instrumentation;

		@Param
		private TracerImplementation tracerImplementation;

		@Setup
		public void setup() {
			this.applicationContext = initContext();
			this.input = this.applicationContext.getBean(InputDestination.class);
			this.output = this.applicationContext.getBean(OutputDestination.class);
		}

		private void sendInputMessage() {
			// System.out.println("Sending the message to input");
			input.send(MessageBuilder.withPayload("hello".getBytes())
					.setHeader("b3", "4883117762eb9420-4883117762eb9420-1").build());
		}

		protected ConfigurableApplicationContext initContext() {
			SpringApplication application = new SpringApplicationBuilder(SleuthBenchmarkingStreamApplication.class)
					.web(WebApplicationType.NONE).application();
			return application.run(runArgs());
		}

		protected String[] runArgs() {
			List<String> strings = new ArrayList<>();
			strings.addAll(Arrays.asList("--spring.jmx.enabled=false",
					"--spring.application.name=defaultTraceContextForStream" + instrumentation.name() + "_"
							+ tracerImplementation.name()));
			strings.addAll(Arrays.asList(instrumentation.asParams()));
			return strings.toArray(new String[0]);
		}

		void run() {
			sendInputMessage();
			assertThatOutputMessageGotReceived();
		}

		private void assertThatOutputMessageGotReceived() {
			// System.out.println("Retrieving the message for tests");
			Message<byte[]> message = output.receive(200L);
			// System.out.println("Got the message from output");
			assertThat(message).isNotNull();
			// System.out.println("Message is not null");
			assertThat(message.getPayload()).isEqualTo("HELLO".getBytes());
			// System.out.println("Payload is HELLO");
			if (!instrumentation.toString().toLowerCase().contains("nosleuth")) {
				String b3 = message.getHeaders().get("b3", String.class);
				// System.out.println("Checking the b3 header [" + b3 + "]");
				assertThat(b3).isNotEmpty();
				if (b3.startsWith("0000000000000000")) {
					assertThat(b3).startsWith("00000000000000004883117762eb9420");
				} else {
					assertThat(b3).startsWith("4883117762eb9420");
				}
				assertThat(this.applicationContext.getBean(Tracer.class).currentSpan()).isNull();
			}
		}

		@TearDown
		public void clean() throws Exception {
			Tracing current = Tracing.current();
			if (current != null) {
				current.close();
			}
			try {
				this.applicationContext.close();
			}
			catch (Exception ig) {

			}
		}

		public enum Instrumentation {

			// @formatter:off
			noSleuthSimple(Pair.noSleuth(), function("simple")),
			sleuthSimpleOnQueues(function("simple"), Pair.onHook()),
			sleuthSimpleManual(function("simple_manual"), Pair.manual(), functionDisabled(), integrationDisabled()),
			sleuthSimpleNoFunctionInstrumentationManual(function("simple_manual"), Pair.manual(), functionDisabled(), integrationEnabled()),
			sleuthSimpleOnEach(function("simple"), Pair.onEach()),
			sleuthSimpleOnLast(function("simple"), Pair.onLast()),
			sleuthSimpleWithAroundOnQueues(function("simple_function_with_around")),
			noSleuthReactiveSimple(function("reactive_simple"), Pair.noSleuth()),
			sleuthReactiveSimpleOnQueues(function("DECORATE_QUEUES"), Pair.decorateQueues(), integrationEnabled()),
			sleuthReactiveSimpleOnEach(function("DECORATE_ON_EACH"), Pair.onEach(), integrationEnabled()),
			sleuthReactiveSimpleManual(function("reactive_simple_manual"), Pair.manual()),
			sleuthReactiveSimpleNoFunctionInstrumentationManual(function("reactive_simple_manual"), Pair.manual(), integrationEnabled(), functionDisabled());
			// @formatter:on

			private final List<Pair> pairs;

			Instrumentation(Pair... pairs) {
				this.pairs = Arrays.asList(pairs);
			}

			String[] asParams() {
				return this.pairs.stream().map(p -> "--" + p.asProp()).toArray(String[]::new);
			}

			static Pair function(String type) {
				return Pair.of("spring.sleuth.function.type", type);
			}

			static Pair integrationEnabled() {
				return Pair.of("spring.sleuth.integration.enabled", "true");
			}

			static Pair integrationDisabled() {
				return Pair.of("spring.sleuth.integration.enabled", "false");
			}

			static Pair functionDisabled() {
				return Pair.of("spring.sleuth.function.enabled", "false");
			}
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Import(TestChannelBinderConfiguration.class)
	static class TestConfiguration {

	}

}
