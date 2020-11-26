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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import brave.Tracing;
import brave.handler.SpanHandler;
import brave.http.HttpTracing;
import brave.httpclient.TracingHttpClientBuilder;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import jmh.mbr.junit5.Microbenchmark;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.sleuth.benchmarks.app.webflux.SleuthBenchmarkingSpringWebFluxApp;
import org.springframework.cloud.sleuth.benchmarks.jmh.TracerImplementation;
import org.springframework.context.ConfigurableApplicationContext;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 5, time = 1)
@Fork(2)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(2)
@State(Scope.Benchmark)
@Microbenchmark
public abstract class SpringWebFluxBenchmarksTests {

	static final SpanHandler FAKE_SPAN_HANDLER = new SpanHandler() {
		// intentionally anonymous to prevent logging fallback on NOOP
	};

	protected static TraceContext defaultTraceContext = TraceContext.newBuilder().traceIdHigh(333L).traceId(444L)
			.spanId(3).sampled(true).build();

	protected ConfigurableApplicationContext applicationContext;

	protected SleuthBenchmarkingSpringWebFluxApp springWebFluxApp;

	CloseableHttpClient client;

	CloseableHttpClient tracedClient;

	CloseableHttpClient unsampledClient;

	private String baseUrl;

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder().include(".*" + SpringWebFluxBenchmarksTests.class.getSimpleName() + ".*")
				.build();

		new Runner(opt).run();
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	protected CloseableHttpClient newClient(HttpTracing httpTracing) {
		return TracingHttpClientBuilder.create(httpTracing).disableAutomaticRetries().build();
	}

	protected CloseableHttpClient newClient() {
		return HttpClients.custom().disableAutomaticRetries().build();
	}

	protected void get(CloseableHttpClient client) throws Exception {
		EntityUtils.consume(client.execute(new HttpGet(getBaseUrl())).getEntity());
	}

	protected void close(CloseableHttpClient client) throws IOException {
		client.close();
	}

	@Setup
	public void setup() {
		ConfigurableApplicationContext context = initContext();
		this.applicationContext = context;
		this.springWebFluxApp = this.applicationContext.getBean(SleuthBenchmarkingSpringWebFluxApp.class);
		baseUrl = "http://127.0.0.1:" + springWebFluxApp.port + "/foo";
		client = newClient();
		tracedClient = newClient(HttpTracing.create(Tracing.newBuilder().addSpanHandler(FAKE_SPAN_HANDLER).build()));
		unsampledClient = newClient(HttpTracing
				.create(Tracing.newBuilder().sampler(Sampler.NEVER_SAMPLE).addSpanHandler(FAKE_SPAN_HANDLER).build()));
		postSetUp();
	}

	protected ConfigurableApplicationContext initContext() {
		SpringApplication application = new SpringApplicationBuilder(SleuthBenchmarkingSpringWebFluxApp.class)
				.web(WebApplicationType.REACTIVE).application();
		customSpringApplication(application);
		return application.run(runArgs());
	}

	protected void customSpringApplication(SpringApplication springApplication) {

	}

	protected void postSetUp() {
	}

	protected String[] runArgs() {
		return new String[] { "--spring.jmx.enabled=false", "--spring.application.name=defaultTraceContext",
				TracerImplementation.brave.toString(), "--spring.sleuth.enabled=true" };
	}

	@TearDown
	public void clean() throws Exception {
		close(client);
		close(unsampledClient);
		close(tracedClient);
		Tracing.current().close();
		try {

			this.applicationContext.close();
		}
		catch (Exception ig) {

		}
	}

	@Benchmark
	public void client_get() throws Exception {
		get(client);
	}

	@Benchmark
	public void unsampledClient_get() throws Exception {
		get(unsampledClient);
	}

	@Benchmark
	public void tracedClient_get() throws Exception {
		get(tracedClient);
	}

	@Benchmark
	public void tracedClient_get_resumeTrace() throws Exception {
		try (CurrentTraceContext.Scope scope = Tracing.current().currentTraceContext().newScope(defaultTraceContext)) {
			get(tracedClient);
		}
	}

}
