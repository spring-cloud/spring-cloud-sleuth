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

package org.springframework.cloud.sleuth.benchmarks.jmh;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import brave.Tracing;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.sleuth.benchmarks.app.stream.SleuthBenchmarkingStreamApplication;
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

@Disabled
public class SampleTests {

	@Test
	public void testStream() throws Exception {
		for (BenchmarkContext.Instrumentation value : BenchmarkContext.Instrumentation.values()) {
			run(value);
		}
		// run(BenchmarkContext.Instrumentation.sleuthReactiveSimpleManual);
	}

	private void run(BenchmarkContext.Instrumentation value) throws Exception {
		BenchmarkContext context = new BenchmarkContext();
		System.out.println("\n\n\n\n WILL WORK WITH [" + value + "]\n\n\n\n");
		context.instrumentation = value;
		context.setup();

		try {
			context.run(value);
		}
		finally {
			context.clean();
		}
		System.out.println("\n\n FINISHED WITH [" + value + "]\n\n\n\n");
	}

	public static class BenchmarkContext {

		volatile ConfigurableApplicationContext applicationContext;

		volatile InputDestination input;

		volatile OutputDestination output;

		@Param
		private Instrumentation instrumentation;

		@Setup
		public void setup() {
			this.applicationContext = initContext();
			this.input = this.applicationContext.getBean(InputDestination.class);
			this.output = this.applicationContext.getBean(OutputDestination.class);
		}

		protected ConfigurableApplicationContext initContext() {
			SpringApplication application = new SpringApplicationBuilder(SleuthBenchmarkingStreamApplication.class)
					.web(WebApplicationType.REACTIVE).application();
			return application.run(runArgs());
		}

		protected String[] runArgs() {
			List<String> strings = new ArrayList<>();
			strings.addAll(Arrays.asList("--spring.jmx.enabled=false",
					"--spring.autoconfigure.exclude=org.springframework.cloud.sleuth.otel.autoconfig.TraceOtelAutoConfiguration",
					"--spring.application.name=defaultTraceContextForStream" + instrumentation.name()));
			strings.addAll(instrumentation.entires.stream().map(s -> "--" + s).collect(Collectors.toList()));
			return strings.toArray(new String[0]);
		}

		void run(Instrumentation value) {
			System.out.println("Sending the message to input");
			input.send(MessageBuilder.withPayload("hello".getBytes())
					.setHeader("b3", "4883117762eb9420-4883117762eb9420-1").build());
			System.out.println("Retrieving the message for tests");
			Message<byte[]> message = output.receive(200L);
			System.out.println("Got the message from output");
			assertThat(message).isNotNull();
			System.out.println("Message is not null");
			assertThat(message.getPayload()).isEqualTo("HELLO".getBytes());
			System.out.println("Payload is HELLO");
			if (!value.toString().toLowerCase().contains("nosleuth")) {
				String b3 = message.getHeaders().get("b3", String.class);
				System.out.println("Checking the b3 header [" + b3 + "]");
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

			noSleuthSimple("spring.sleuth.enabled=false,spring.sleuth.function.type=simple");

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
