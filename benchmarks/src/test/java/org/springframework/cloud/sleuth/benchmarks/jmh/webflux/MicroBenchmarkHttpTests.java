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
import org.springframework.cloud.sleuth.benchmarks.app.webflux.SleuthBenchmarkingSpringWebFluxApp;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(2)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Microbenchmark
public class MicroBenchmarkHttpTests {

	static {
		System.setProperty("jmh.mbr.report.publishTo", "csv:http.csv");
	}

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
			return new String[] { "--spring.jmx.enabled=false",
					"--spring.application.name=defaultTraceContext" + instrumentation.name(),
					"--" + instrumentation.key + "=" + instrumentation.value };
		}

		void run() {
			this.webTestClient.get().uri(this.instrumentation.url).header("X-B3-TraceId", "4883117762eb9420")
					.header("X-B3-SpanId", "4883117762eb9420").exchange().expectStatus().isOk();
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

			noSleuthSimple("spring.sleuth.enabled", "false", "/simple"), sleuthSimpleManual(
					"spring.sleuth.reactor.instrumentation-type", "MANUAL",
					"/simple"), noSleuthComplex("spring.sleuth.enabled", "false", "/complexNoSleuth"), onEachComplex(
							"spring.sleuth.reactor.instrumentation-type", "DECORATE_ON_EACH",
							"/complex"), onLastComplex("spring.sleuth.reactor.instrumentation-type", "DECORATE_ON_LAST",
									"/complex"), onManualComplex("spring.sleuth.reactor.instrumentation-type", "MANUAL",
											"/complexManual");

			private String key;

			private String value;

			private String url;

			Instrumentation(String key, String value, String url) {
				this.key = key;
				this.value = value;
				this.url = url;
			}

		}

	}

}
