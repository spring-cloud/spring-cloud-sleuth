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

package org.springframework.cloud.sleuth.benchmarks.jmh.webflux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import brave.Tracer;
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
import org.springframework.cloud.sleuth.benchmarks.app.webflux.SleuthBenchmarkingSpringWebFluxApp;
import org.springframework.cloud.sleuth.benchmarks.jmh.Pair;
import org.springframework.cloud.sleuth.benchmarks.jmh.TracerImplementation;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@Measurement(iterations = 10, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(4)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Microbenchmark
public class MicroBenchmarkHttpTests {

	@Benchmark
	@Testable
	public void test(BenchmarkContext context) throws Exception {
		context.run();
	}

	@State(Scope.Benchmark)
	public static class BenchmarkContext {

		volatile ConfigurableApplicationContext applicationContext;

		volatile WebTestClient webTestClient;

		@Param
		private Instrumentation instrumentation;

		@Param
		private TracerImplementation tracerImplementation;

		@Setup
		public void setup() {
			this.applicationContext = initContext();
			this.webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
		}

		protected ConfigurableApplicationContext initContext() {
			SpringApplication application = new SpringApplicationBuilder(SleuthBenchmarkingSpringWebFluxApp.class)
					.web(WebApplicationType.REACTIVE).application();
			return application.run(runArgs());
		}

		protected String[] runArgs() {
			String[] defaultArgs = new String[] { "--spring.jmx.enabled=false",
					"--spring.application.name=defaultTraceContext" + instrumentation.name() + "_"
							+ tracerImplementation.name() };
			List<String> list = new ArrayList<>(Arrays.asList(defaultArgs));
			list.addAll(Arrays.asList(instrumentation.asParams()));
			return list.toArray(new String[0]);
		}

		void run() {
			this.webTestClient.get().uri(instrumentation.url).header("X-B3-TraceId", "4883117762eb9420")
					.header("X-B3-SpanId", "4883117762eb9420").exchange().expectStatus().isOk();
			assertThat(this.applicationContext.getBean(Tracer.class).currentSpan()).isNull();
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
			noSleuthSimple("/simple", Pair.noSleuth()),
			sleuthSimpleOnHooks("/simple", Pair.onHook()),
			sleuthSimpleOnEach("/simple", Pair.onEach()),
			sleuthSimpleOnLast("/simple", Pair.onLast()),
			noSleuthComplex("/complexNoSleuth", Pair.noSleuth()),
			onHooksComplex("/complex", Pair.onHook()),
			onEachComplex("/complex", Pair.onEach()),
			onLastComplex("/complex", Pair.onLast());
			// @formatter:on

			private String url;

			private List<Pair> pairs;

			Instrumentation(String url, Pair... pairs) {
				this.url = url;
				this.pairs = Arrays.asList(pairs);
			}

			String[] asParams() {
				return this.pairs.stream().map(p -> "--" + p.asProp()).collect(Collectors.toList()).toArray(new String[0]);
			}
		}

	}

}
