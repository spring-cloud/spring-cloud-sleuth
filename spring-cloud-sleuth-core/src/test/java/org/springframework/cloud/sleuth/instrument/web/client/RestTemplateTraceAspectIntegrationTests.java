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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import brave.Tracing;
import brave.sampler.Sampler;
import brave.spring.web.TracingAsyncClientHttpRequestInterceptor;
import zipkin2.Span;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.async.WebAsyncTask;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = RestTemplateTraceAspectIntegrationTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) @DirtiesContext
public class RestTemplateTraceAspectIntegrationTests {

	@Autowired WebApplicationContext context;
	@Autowired AspectTestingController controller;
	@Autowired Tracing tracer;
	@Autowired ArrayListSpanReporter reporter;

	private MockMvc mockMvc;

	@Before public void init() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
		this.controller.reset();
	}

	@Before @After public void verify() {
		then(this.tracer.tracer().currentSpan()).isNull();
	}

	@Test public void should_set_span_data_on_headers_via_aspect_in_synchronous_call()
			throws Exception {
		whenARequestIsSentToASyncEndpoint();

		thenTraceIdHasBeenSetOnARequestHeader();
		thenClientKindIsReported();
	}

	@Test
	public void should_set_span_data_on_headers_when_sending_a_request_via_async_rest_template()
			throws Exception {
		whenARequestIsSentToAnAsyncRestTemplateEndpoint();

		thenTraceIdHasBeenSetOnARequestHeader();
		thenClientKindIsReported();
	}

	@Test
	public void should_set_span_data_on_headers_via_aspect_in_asynchronous_callable()
			throws Exception {
		whenARequestIsSentToAnAsyncEndpoint("/callablePing");

		thenTraceIdHasBeenSetOnARequestHeader();
		thenClientKindIsReported();
	}

	@Test
	public void should_set_span_data_on_headers_via_aspect_in_asynchronous_web_async()
			throws Exception {
		whenARequestIsSentToAnAsyncEndpoint("/webAsyncTaskPing");

		thenTraceIdHasBeenSetOnARequestHeader();
		thenClientKindIsReported();
	}

	private void whenARequestIsSentToAnAsyncRestTemplateEndpoint() throws Exception {
		this.mockMvc.perform(MockMvcRequestBuilders.get("/asyncRestTemplate")
				.accept(MediaType.TEXT_PLAIN)).andReturn();
	}

	private void whenARequestIsSentToASyncEndpoint() throws Exception {
		this.mockMvc.perform(
				MockMvcRequestBuilders.get("/syncPing").accept(MediaType.TEXT_PLAIN))
				.andReturn();
	}

	private void thenTraceIdHasBeenSetOnARequestHeader() {
		assertThat(this.controller.getTraceId()).matches("^(?!\\s*$).+");
	}

	// Brave was never designed to run tests of server and client in one test
	// that's why we have to pick only CLIENT side
	private void thenClientKindIsReported() {
		assertThat(this.reporter.getSpans().stream().map(Span::kind)
				.collect(Collectors.toList()))
				.contains(Span.Kind.CLIENT);
	}

	private void whenARequestIsSentToAnAsyncEndpoint(String url) throws Exception {
		MvcResult mvcResult = this.mockMvc
				.perform(MockMvcRequestBuilders.get(url).accept(MediaType.TEXT_PLAIN))
				.andExpect(request().asyncStarted()).andReturn();
		mvcResult.getAsyncResult(SECONDS.toMillis(2));
		this.mockMvc.perform(asyncDispatch(mvcResult)).andDo(print())
				.andExpect(status().isOk());
	}

	@DefaultTestAutoConfiguration
	@Import(AspectTestingController.class)
	public static class Config {
		@Bean public RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean public AsyncRestTemplate asyncRestTemplate(Tracing tracing) {
			AsyncRestTemplate asyncRestTemplate = new AsyncRestTemplate();
			asyncRestTemplate.setInterceptors(Collections.singletonList(
					TracingAsyncClientHttpRequestInterceptor.create(tracing)));
			return asyncRestTemplate;
		}

		@Bean ArrayListSpanReporter reporter() {
			return new ArrayListSpanReporter();
		}
	}

	@RestController public static class AspectTestingController {

		@Autowired Tracing tracer;
		@Autowired RestTemplate restTemplate;
		@Autowired Environment environment;
		@Autowired AsyncRestTemplate asyncRestTemplate;
		private String traceId;

		public void reset() {
			this.traceId = null;
		}

		@RequestMapping(value = "/", method = RequestMethod.GET,
				produces = MediaType.TEXT_PLAIN_VALUE) public String home(
				@RequestHeader(value = "X-B3-SpanId", required = false) String traceId) {
			this.traceId = traceId == null ? "UNKNOWN" : traceId;
			return "trace=" + this.getTraceId();
		}

		@RequestMapping(value = "/customTag", method = RequestMethod.GET,
				produces = MediaType.TEXT_PLAIN_VALUE) public String customTag(
				@RequestHeader(value = "X-B3-TraceId", required = false) String traceId) {
			this.traceId = traceId == null ? "UNKNOWN" : traceId;
			return "trace=" + this.getTraceId();
		}

		@RequestMapping(value = "/asyncRestTemplate", method = RequestMethod.GET,
				produces = MediaType.TEXT_PLAIN_VALUE) public String asyncRestTemplate()
				throws ExecutionException, InterruptedException {
			return callViaAsyncRestTemplateAndReturnOk();
		}

		@RequestMapping(value = "/syncPing", method = RequestMethod.GET,
				produces = MediaType.TEXT_PLAIN_VALUE) public String syncPing() {
			return callAndReturnOk();
		}

		@RequestMapping(value = "/callablePing", method = RequestMethod.GET,
				produces = MediaType.TEXT_PLAIN_VALUE)
		public Callable<String> asyncPing() {
			return new Callable<String>() {
				@Override public String call() throws Exception {
					return callAndReturnOk();
				}
			};
		}

		@RequestMapping(value = "/webAsyncTaskPing", method = RequestMethod.GET,
				produces = MediaType.TEXT_PLAIN_VALUE)
		public WebAsyncTask<String> webAsyncTaskPing() {
			return new WebAsyncTask<>(new Callable<String>() {
				@Override public String call() throws Exception {
					return callAndReturnOk();
				}
			});
		}

		;

		private String callAndReturnOk() {
			this.restTemplate.getForObject("http://localhost:" + port(), String.class);
			return "OK";
		}

		private String callViaAsyncRestTemplateAndReturnOk()
				throws ExecutionException, InterruptedException {
			this.asyncRestTemplate
					.getForEntity("http://localhost:" + port(), String.class).get();
			return "OK";
		}

		private int port() {
			return this.environment.getProperty("local.server.port", Integer.class);
		}

		String getTraceId() {
			return this.traceId;
		}
	}
}
