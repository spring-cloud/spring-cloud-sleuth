/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.sleuth.benchmarks.jmh.benchmarks;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.sleuth.benchmarks.app.SleuthBenchmarkingSpringApp;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.BDDAssertions.then;

@Measurement(iterations = 5)
@Warmup(iterations = 10)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(Threads.MAX)
public class AsyncBenchmarks {

	@State(Scope.Benchmark)
	public static class BenchmarkContext {
		volatile ConfigurableApplicationContext withSleuth;
		volatile ConfigurableApplicationContext withoutSleuth;
		volatile SleuthBenchmarkingSpringApp tracedAsyncMethodHavingBean;
		volatile SleuthBenchmarkingSpringApp untracedAsyncMethodHavingBean;

		@Setup public void setup() {
			this.withSleuth = new SpringApplication(
					SleuthBenchmarkingSpringApp.class)
					.run("--spring.jmx.enabled=false",
							"--spring.application.name=withSleuth");
			this.withoutSleuth = new SpringApplication(
					SleuthBenchmarkingSpringApp.class)
					.run("--spring.jmx.enabled=false",
							"--spring.application.name=withoutSleuth",
							"--spring.sleuth.enabled=false",
							"--spring.sleuth.async.enabled=false");
			this.tracedAsyncMethodHavingBean = this.withSleuth.getBean(
					SleuthBenchmarkingSpringApp.class);
			this.untracedAsyncMethodHavingBean = this.withoutSleuth.getBean(
					SleuthBenchmarkingSpringApp.class);
		}

		@TearDown public void clean() {
			this.tracedAsyncMethodHavingBean.clean();
			this.untracedAsyncMethodHavingBean.clean();
			this.withSleuth.close();
			this.withoutSleuth.close();
		}
	}

	@Benchmark
	public void asyncMethodWithoutSleuth(BenchmarkContext context)
			throws Exception {
		then(context.untracedAsyncMethodHavingBean.async().get()).isEqualTo("async");
	}

	@Benchmark
	public void asyncMethodWithSleuth(BenchmarkContext context)
			throws Exception {
		then(context.tracedAsyncMethodHavingBean.async().get()).isEqualTo("async");
	}
}