/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin2.sender;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import brave.Span;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.sampler.Sampler;
import brave.spring.web.TracingClientHttpRequestInterceptor;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.client.SleuthWebClientInterceptorRemover;
import org.springframework.cloud.sleuth.instrument.web.client.TraceWebAsyncClientAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.client.TraceWebClientAutoConfiguration;
import org.springframework.cloud.sleuth.zipkin2.ZipkinAutoConfiguration;
import org.springframework.cloud.sleuth.zipkin2.ZipkinBackwardsCompatibilityAutoConfiguration;
import org.springframework.cloud.sleuth.zipkin2.ZipkinProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;


public class ZipkinRestTemplateSenderConfigurationTest {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Rule
	public MockWebServer server = new MockWebServer();

	MockEnvironment environment = new MockEnvironment();

	AnnotationConfigApplicationContext context;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	private MockEnvironment environment() {
		this.context.setEnvironment(this.environment);
		return this.environment;
	}

	@Test
	public void shouldNotTraceDefaultZipkinRestTemplate() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.sleuth.web.client.enabled", "true");
		this.context.register(PropertyPlaceholderAutoConfiguration.class,
				ZipkinAutoConfiguration.class, TraceAutoConfiguration.class,
				ZipkinBackwardsCompatibilityAutoConfiguration.class,
				TraceWebClientAutoConfiguration.class,
				TraceWebAsyncClientAutoConfiguration.class);
		this.context.register(Config.class);
		this.context.refresh();

		simulateTraceWebClientAutoConfiguration();

		RestTemplate zipkinRestTemplate = this.context.getBean(
				ZipkinAutoConfiguration.REST_TEMPLATE_BEAN_NAME, RestTemplate.class);
		then(zipkinRestTemplate.getClass().getName())
				.contains("ZipkinRestTemplateWrapper");
		then(zipkinRestTemplate.getInterceptors()).isEmpty();

		this.context.close();
	}

	/**
	 * Simulates the behavior of TraceWebClientAytoConfiguration because I was not able to
	 * inject it in context during auto configuration phase.
	 */
	private void simulateTraceWebClientAutoConfiguration() {
		HttpTracing httpTracing = this.context.getBean(HttpTracing.class);
		Map<String, RestTemplate> restTemplates = this.context
				.getBeansOfType(RestTemplate.class);
		ClientHttpRequestInterceptor interceptor = TracingClientHttpRequestInterceptor
				.create(httpTracing);
		for (RestTemplate restTemplate : restTemplates.values()) {

			List<ClientHttpRequestInterceptor> interceptors = new ArrayList<ClientHttpRequestInterceptor>(
					restTemplate.getInterceptors());
			interceptors.add(0, interceptor);
			restTemplate.setInterceptors(interceptors);
		}
	}

	@Test
	public void createSpanInfiniteLoopWhenOverridingTemplateWithoutCare()
			throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.base-url",
				this.server.url("/test").toString());
		this.context.register(ZipkinAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class, TraceAutoConfiguration.class,
				Config.class, ZipkinBackwardsCompatibilityAutoConfiguration.class,
				TraceWebClientAutoConfiguration.class,
				TraceWebAsyncClientAutoConfiguration.class,
				InvalidCustomRestTemplateConfig.class);
		this.context.refresh();
		simulateTraceWebClientAutoConfiguration();

		Span span = this.context.getBean(Tracing.class).tracer().nextSpan().name("foo")
				.tag("foo", "bar").start();

		this.server.enqueue(new MockResponse().setResponseCode(200));
		span.finish();

		Awaitility.await().untilAsserted(
				() -> then(this.server.getRequestCount()).isGreaterThan(0));
		RecordedRequest request = this.server.takeRequest();
		then(request.getPath()).isEqualTo("/test");
		then(request.getBody().readUtf8()).contains("localEndpoint");

		this.server.enqueue(new MockResponse().setResponseCode(200));
		Awaitility.await().atMost(Duration.FIVE_SECONDS).untilAsserted(
				() -> then(this.server.getRequestCount()).isGreaterThan(1));
		System.out.println("after 2nd request");
		RecordedRequest request2 = this.server.takeRequest();
		String span2Body = request2.getBody().readUtf8();
		assertThat(span2Body).contains("\"kind\":\"CLIENT\"");
		assertThat(span2Body)
				.contains("\"tags\":{\"http.method\":\"POST\",\"http.path\":\"/test\"}");
	}

	@Test
	public void canOverrideRestTemplate() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		environment().setProperty("spring.zipkin.base-url",
				this.server.url("/test").toString());
		this.context.register(ZipkinAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class, TraceAutoConfiguration.class,
				Config.class, ZipkinBackwardsCompatibilityAutoConfiguration.class,
				TraceWebClientAutoConfiguration.class,
				TraceWebAsyncClientAutoConfiguration.class,
				ValidCustomRestTemplateConfig.class);
		this.context.refresh();
		simulateTraceWebClientAutoConfiguration();

		Span span = this.context.getBean(Tracing.class).tracer().nextSpan().name("foo")
				.tag("foo", "bar").start();

		this.server.enqueue(new MockResponse().setResponseCode(200));
		span.finish();

		Awaitility.await().untilAsserted(
				() -> then(this.server.getRequestCount()).isGreaterThan(0));
		RecordedRequest request = this.server.takeRequest();
		then(request.getPath()).isEqualTo("/test");
		then(request.getBody().readUtf8()).contains("localEndpoint");

		// not really pretty but same duration as in the invalid test so if we should
		// avoid false positives
		// could be avoiding by flushing the AsyncReporter
		Thread.sleep(Duration.FIVE_SECONDS.getValueInMS());
		assertThat(this.server.getRequestCount()).isEqualTo(1);
	}

	@Configuration
	@Import(TraceWebClientAutoConfiguration.class)
	protected static class Config {

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		HttpTracing httpTracing(Tracing tracing) {
			return HttpTracing.create(tracing);
		}

	}

	@Configuration
	protected static class InvalidCustomRestTemplateConfig {

		@Autowired
		private ZipkinProperties properties;

		@Bean(ZipkinAutoConfiguration.REST_TEMPLATE_BEAN_NAME)
		RestTemplate myTemplate() throws MalformedURLException, URISyntaxException {
			URI uri = new URL(properties.getBaseUrl()).toURI();
			return new InvalidTemplate(uri);
		}

		static class InvalidTemplate extends RestTemplate {
			URI zipkinUri;

			InvalidTemplate(URI uri) throws MalformedURLException, URISyntaxException {
				zipkinUri = uri;
			}

			@Override
			protected <T> T doExecute(URI arg0, HttpMethod method, RequestCallback arg2,
					ResponseExtractor<T> arg3) throws RestClientException {
				return super.doExecute(zipkinUri, method, arg2, arg3);
			}
		}
	}

	@Configuration
	protected static class ValidCustomRestTemplateConfig {

		@Autowired
		private ZipkinProperties properties;

		@Bean(ZipkinAutoConfiguration.REST_TEMPLATE_BEAN_NAME)
		RestTemplate myTemplate() throws MalformedURLException, URISyntaxException {
			URI uri = new URL(properties.getBaseUrl()).toURI();
			return new ValidTemplate(uri);
		}

		static class ValidTemplate extends RestTemplate {
			URI zipkinUri;

			ValidTemplate(URI uri) throws MalformedURLException, URISyntaxException {
				zipkinUri = uri;
			}

			@Override
			public void setInterceptors(List<ClientHttpRequestInterceptor> interceptors) {
				super.setInterceptors(
						new SleuthWebClientInterceptorRemover().filter(interceptors));
			}

			@Override
			protected <T> T doExecute(URI arg0, HttpMethod method, RequestCallback arg2,
					ResponseExtractor<T> arg3) throws RestClientException {
				return super.doExecute(zipkinUri, method, arg2, arg3);
			}
		}
	}

}
