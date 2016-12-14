/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.client;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.AsyncClientHttpRequest;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.AsyncRestTemplate;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(Enclosed.class)
public class TraceWebAsyncClientAutoConfigurationTests {

	@RunWith(SpringJUnit4ClassRunner.class)
	@SpringApplicationConfiguration(classes = { CustomSyncAndAsyncClientFactory.TestConfiguration.class })
	@WebIntegrationTest(randomPort = true)
	public static class CustomSyncAndAsyncClientFactory {
		@Autowired AsyncRestTemplate asyncRestTemplate;

		@Test
		public void should_inject_to_async_rest_template_custom_client_http_request_factory() {
			then(this.asyncRestTemplate.getAsyncRequestFactory()).isInstanceOf(TraceAsyncClientHttpRequestFactoryWrapper.class);
			TraceAsyncClientHttpRequestFactoryWrapper wrapper = (TraceAsyncClientHttpRequestFactoryWrapper) this.asyncRestTemplate.getAsyncRequestFactory();
			then(wrapper.syncDelegate).isInstanceOf(MySyncClientHttpRequestFactory.class);
			then(wrapper.asyncDelegate).isInstanceOf(MyAsyncClientHttpRequestFactory.class);
			then(this.asyncRestTemplate).isInstanceOf(TraceAsyncRestTemplate.class);
		}

		// tag::async_template_factories[]
		@EnableAutoConfiguration
		@Configuration
		public static class TestConfiguration {

			@Bean
			ClientHttpRequestFactory mySyncClientFactory() {
				return new MySyncClientHttpRequestFactory();
			}

			@Bean
			AsyncClientHttpRequestFactory myAsyncClientFactory() {
				return new MyAsyncClientHttpRequestFactory();
			}
		}
		// end::async_template_factories[]

	}

	@RunWith(SpringJUnit4ClassRunner.class)
	@SpringApplicationConfiguration(classes = { CustomSyncClientFactory.TestConfiguration.class })
	@WebIntegrationTest(randomPort = true)
	public static class CustomSyncClientFactory {
		@Autowired AsyncRestTemplate asyncRestTemplate;

		@Test
		public void should_inject_to_async_rest_template_custom_client_http_request_factory() {
			then(this.asyncRestTemplate.getAsyncRequestFactory()).isInstanceOf(TraceAsyncClientHttpRequestFactoryWrapper.class);
			TraceAsyncClientHttpRequestFactoryWrapper wrapper = (TraceAsyncClientHttpRequestFactoryWrapper) this.asyncRestTemplate.getAsyncRequestFactory();
			then(wrapper.syncDelegate).isInstanceOf(MySyncClientHttpRequestFactory.class);
			then(wrapper.asyncDelegate).isInstanceOf(SimpleClientHttpRequestFactory.class);
			then(this.asyncRestTemplate).isInstanceOf(TraceAsyncRestTemplate.class);
		}

		@EnableAutoConfiguration
		@Configuration
		public static class TestConfiguration {

			@Bean
			ClientHttpRequestFactory mySyncClientFactory() {
				return new MySyncClientHttpRequestFactory();
			}
		}

	}

	@RunWith(SpringJUnit4ClassRunner.class)
	@SpringApplicationConfiguration(classes = { CustomASyncClientFactory.TestConfiguration.class })
	@WebIntegrationTest(randomPort = true)
	public static class CustomASyncClientFactory {
		@Autowired AsyncRestTemplate asyncRestTemplate;

		@Test
		public void should_inject_to_async_rest_template_custom_client_http_request_factory() {
			then(this.asyncRestTemplate.getAsyncRequestFactory()).isInstanceOf(TraceAsyncClientHttpRequestFactoryWrapper.class);
			TraceAsyncClientHttpRequestFactoryWrapper wrapper = (TraceAsyncClientHttpRequestFactoryWrapper) this.asyncRestTemplate.getAsyncRequestFactory();
			then(wrapper.syncDelegate).isInstanceOf(SimpleClientHttpRequestFactory.class);
			then(wrapper.asyncDelegate).isInstanceOf(AsyncClientHttpRequestFactory.class);
			then(this.asyncRestTemplate).isInstanceOf(TraceAsyncRestTemplate.class);
		}

		@EnableAutoConfiguration
		@Configuration
		public static class TestConfiguration {

			@Bean
			AsyncClientHttpRequestFactory myAsyncClientFactory() {
				return new MyAsyncClientHttpRequestFactory();
			}
		}

	}

	private  static class MySyncClientHttpRequestFactory implements ClientHttpRequestFactory {
		@Override public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod)
				throws IOException {
			return null;
		}
	}
	private static class MyAsyncClientHttpRequestFactory implements AsyncClientHttpRequestFactory {
		@Override
		public AsyncClientHttpRequest createAsyncRequest(URI uri, HttpMethod httpMethod)
				throws IOException {
			return null;
		}
	}

	@RunWith(SpringJUnit4ClassRunner.class)
	@SpringApplicationConfiguration(classes = { DurationChecking.TestConfiguration.class })
	@WebIntegrationTest(randomPort = true)
	public static class DurationChecking {
		@Autowired AsyncRestTemplate asyncRestTemplate;
		@Autowired Environment environment;
		@Autowired ArrayListSpanAccumulator accumulator;
		@Autowired Tracer tracer;

		@Before
		public void setup() {
			ExceptionUtils.setFail(true);
		}

		@Test
		public void should_close_span_upon_success_callback()
				throws ExecutionException, InterruptedException {
			ListenableFuture<ResponseEntity<String>> future = this.asyncRestTemplate
					.getForEntity("http://localhost:" + port() + "/foo", String.class);
			String result = future.get().getBody();

			then(result).isEqualTo("foo");
			then(this.accumulator.getSpans().stream().filter(
					span -> span.logs().stream().filter(log -> Span.CLIENT_RECV.equals(log.getEvent())).findFirst().isPresent()
			).findFirst().get()).matches(span -> span.getAccumulatedMicros() >= TimeUnit.MILLISECONDS.toMicros(100));
			then(this.tracer.getCurrentSpan()).isNull();
			then(ExceptionUtils.getLastException()).isNull();
		}

		@Test
		public void should_close_span_upon_failure_callback()
				throws ExecutionException, InterruptedException {
			ListenableFuture<ResponseEntity<String>> future;
			try {
				future = this.asyncRestTemplate
						.getForEntity("http://localhost:" + port() + "/blowsup", String.class);
				future.get();
			} catch (Exception e) {
				then(e.getMessage()).contains("Internal Server Error");
			}

			then(this.accumulator.getSpans().stream().filter(
					span -> span.logs().stream().filter(log -> Span.CLIENT_RECV.equals(log.getEvent())).findFirst().isPresent()
			).findFirst().get()).matches(span -> span.getAccumulatedMicros() >= TimeUnit.MILLISECONDS.toMicros(100))
					.hasATag(Span.SPAN_ERROR_TAG_NAME, "500 Internal Server Error");
			then(this.tracer.getCurrentSpan()).isNull();
			then(ExceptionUtils.getLastException()).isNull();
		}

		int port() {
			return this.environment.getProperty("local.server.port", Integer.class);
		}

		@EnableAutoConfiguration
		@Configuration
		public static class TestConfiguration {

			@Bean ArrayListSpanAccumulator accumulator() {
				return new ArrayListSpanAccumulator();
			}

			@Bean
			MyController myController() {
				return new MyController();
			}

			@Bean AlwaysSampler sampler() {
				return new AlwaysSampler();
			}
		}

		@RestController
		public static class MyController {

			@RequestMapping("/foo")
			String foo() throws Exception {
				Thread.sleep(100);
				return "foo";
			}

			@RequestMapping("/blowsup")
			String blowsup() throws Exception {
				Thread.sleep(100);
				throw new RuntimeException("boom");
			}
		}

	}

}