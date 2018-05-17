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

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import brave.servlet.TracingFilter;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Warmup(iterations = 10)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(Threads.MAX)
public class HttpFilterBenchmarks {

	@State(Scope.Benchmark)
	public static class BenchmarkContext {
		volatile ConfigurableApplicationContext withSleuth;
		volatile DummyFilter dummyFilter = new DummyFilter();
		volatile TracingFilter tracingFilter;
		volatile MockMvc mockMvcForTracedController;
		volatile MockMvc mockMvcForUntracedController;

		@Setup public void setup() {
			this.withSleuth = new SpringApplication(
					SleuthBenchmarkingSpringApp.class)
					.run("--spring.jmx.enabled=false",
							"--spring.application.name=withSleuth");
			this.tracingFilter = this.withSleuth.getBean(TracingFilter.class);
			this.mockMvcForTracedController = MockMvcBuilders.standaloneSetup(
					this.withSleuth.getBean(SleuthBenchmarkingSpringApp.class))
					.build();
			this.mockMvcForUntracedController = MockMvcBuilders.standaloneSetup(
					new VanillaController())
					.build();
		}


		@TearDown public void clean() {
			this.withSleuth.getBean(SleuthBenchmarkingSpringApp.class).clean();
			this.withSleuth.close();
		}
	}

	@Benchmark
	@Measurement(iterations = 5, time = 1)
	@Fork(3)
	public void filterWithoutSleuth(BenchmarkContext context)
			throws IOException, ServletException {
		MockHttpServletRequest request = builder().buildRequest(new MockServletContext());
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);

		context.dummyFilter.doFilter(request, response, new MockFilterChain());
	}

	@Benchmark
	@Measurement(iterations = 5, time = 1)
	@Fork(3)
	public void filterWithSleuth(BenchmarkContext context)
			throws ServletException, IOException {
		MockHttpServletRequest request = builder().buildRequest(new MockServletContext());
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);

		context.tracingFilter.doFilter(request, response, new MockFilterChain());
	}

	@Benchmark
	@Measurement(iterations = 5, time = 10)
	@Fork(10)
	public void asyncWithoutSleuth(BenchmarkContext context) throws Exception {
		performRequest(context.mockMvcForUntracedController, "vanilla", "vanilla");
	}

	@Benchmark
	@Measurement(iterations = 5, time = 10)
	@Fork(10)
	public void asyncWithSleuth(BenchmarkContext context) throws Exception {
		performRequest(context.mockMvcForTracedController, "bar", "bar");
	}

	private MockHttpServletRequestBuilder builder() {
		return get("/").accept(MediaType.APPLICATION_JSON)
				.header("User-Agent", "MockMvc");
	}

	private void performRequest(MockMvc mockMvc, String url, String expectedResult) throws Exception {
		MvcResult mvcResult = mockMvc.perform(get("/" + url))
				.andExpect(status().isOk())
				.andExpect(request().asyncStarted())
				.andReturn();

		mockMvc.perform(asyncDispatch(mvcResult))
				.andExpect(status().isOk())
				.andExpect(content().string(expectedResult));
	}

	private static class DummyFilter implements Filter {

		@Override public void init(FilterConfig filterConfig) throws ServletException {}

		@Override public void doFilter(ServletRequest request, ServletResponse response,
				FilterChain chain) throws IOException, ServletException {
			chain.doFilter(request, response);
		}

		@Override public void destroy() { }
	}
	
	@RestController
	private static class VanillaController {
		@RequestMapping("/vanilla")
		public Callable<String> vanilla() {
			return () -> "vanilla";
		}
	}
}