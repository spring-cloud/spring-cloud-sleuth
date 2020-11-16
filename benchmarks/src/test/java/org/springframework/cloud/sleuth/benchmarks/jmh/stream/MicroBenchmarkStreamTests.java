/*
 * Copyright 2013-2020 the original author or authors.
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import org.springframework.cloud.sleuth.benchmarks.app.stream.SleuthBenchmarkingStreamApplication;
import org.springframework.cloud.sleuth.benchmarks.jmh.TracerImplementation;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(2)
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
			strings.addAll(Arrays.asList("--spring.jmx.enabled=false", this.tracerImplementation.property(),
					"--spring.application.name=defaultTraceContextForStream" + instrumentation.name() + "_"
							+ tracerImplementation.name()));
			strings.addAll(instrumentation.entires.stream().map(s -> "--" + s).collect(Collectors.toList()));
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
				assertThat(b3).startsWith("4883117762eb9420");
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

			noSleuthSimple("spring.sleuth.enabled=false,spring.sleuth.function.type=simple"), sleuthSimple(
					"spring.sleuth.function.type=simple"), sleuthSimpleWithAround(
							"spring.sleuth.function.type=simple_function_with_around"), noSleuthReactiveSimple(
									"spring.sleuth.enabled=false,spring.sleuth.function.type=reactive_simple"), sleuthReactiveSimpleManual(
											"spring.sleuth.function.type=reactive_simple_manual"), sleuthReactiveSimpleOnEach(
													"spring.sleuth.reactor.instrumentation-type=DECORATE_ON_EACH,spring.sleuth.integration.enabled=true,spring.sleuth.function.type=DECORATE_ON_EACH"),
			// This won't work with messaging
			// sleuthReactiveSimpleOnLast("spring.sleuth.reactor.instrumentation-type=DECORATE_ON_LAST,spring.sleuth.function.type=DECORATE_ON_LAST"),
			// NO FUNCTION, NO INTEGRATION, MANUAL OPERATORS
			sleuthSimpleManual(
					"spring.sleuth.function.enabled=false,spring.sleuth.integration.enabled=false,spring.sleuth.function.type=simple_manual"), sleuthSimpleNoFunctionInstrumentationManual(
							"spring.sleuth.function.type=simple_manual,spring.sleuth.function.enabled=false,spring.sleuth.integration.enabled=true,spring.sleuth.reactor.instrumentation-type=MANUAL"), sleuthReactiveSimpleNoFunctionInstrumentationManual(
									"spring.sleuth.function.type=reactive_simple_manual,spring.sleuth.function.enabled=false,spring.sleuth.integration.enabled=true,spring.sleuth.reactor.instrumentation-type=MANUAL");

			private Set<String> entires = new HashSet<>();

			Instrumentation(String key, String value) {
				this.entires.add(key + "=" + value);
			}

			Instrumentation(String commaSeparated) {
				this.entires.addAll(StringUtils.commaDelimitedListToSet(commaSeparated));
			}

		}

	}

	@Configuration(proxyBeanMethods = false)
	@Import(TestChannelBinderConfiguration.class)
	static class TestConfiguration {

	}

}
