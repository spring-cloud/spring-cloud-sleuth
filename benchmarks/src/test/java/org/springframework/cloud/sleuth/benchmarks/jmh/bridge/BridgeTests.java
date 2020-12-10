/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.benchmarks.jmh.bridge;

import java.util.concurrent.TimeUnit;

import jmh.mbr.junit5.Microbenchmark;
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
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.benchmarks.jmh.TracerImplementation;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.BDDAssertions.then;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 5, time = 1)
@Fork(2)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Microbenchmark
public class BridgeTests {

	@Benchmark
	public void should_create_next_span(BenchmarkContext context) throws Exception {
		Tracer tracer = context.tracer;
		Span span = tracer.nextSpan().start();
		try {
			then(span).isNotNull();
		}
		finally {
			if (span != null) {
				span.end();
			}
		}
	}

	@Benchmark
	public void should_create_next_span_with_parent(BenchmarkContext context) throws Exception {
		Tracer tracer = context.tracer;
		Span span = tracer.nextSpan(context.parent).start();
		try {
			then(span).isNotNull();
		}
		finally {
			if (span != null) {
				span.end();
			}
		}
	}

	@Benchmark
	public void should_retrieve_current_span_from_scope(BenchmarkContext context) throws Exception {
		Tracer tracer = context.tracer;
		Span span = context.parent;
		try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
			then(tracer.currentSpan().context().spanId()).isEqualTo(span.context().spanId());
		}
	}

	@State(Scope.Benchmark)
	public static class BenchmarkContext {

		volatile ConfigurableApplicationContext withSleuth;

		volatile Tracer tracer;

		volatile Span parent;

		@Param
		private TracerImplementation tracerImplementation;

		@Setup
		public void setup() {
			SpringApplication application = new SpringApplication(TestConfiguration.class);
			application.setWebApplicationType(WebApplicationType.NONE);
			this.withSleuth = application.run("--spring.jmx.enabled=false",
					"--spring.application.name=withSleuth_" + this.tracerImplementation.name());
			this.tracer = this.withSleuth.getBean(Tracer.class);
			this.parent = this.tracer.nextSpan().name("name").start();
		}

		@TearDown
		public void clean() {
			this.withSleuth.close();
			this.parent.end();
		}

		@Configuration(proxyBeanMethods = false)
		@ImportAutoConfiguration(BraveAutoConfiguration.class)
		static class TestConfiguration {

		}

	}

}
