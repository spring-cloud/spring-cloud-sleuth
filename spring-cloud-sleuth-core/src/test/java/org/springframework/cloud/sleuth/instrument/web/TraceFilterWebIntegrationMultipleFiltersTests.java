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

package org.springframework.cloud.sleuth.instrument.web;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.PreDestroy;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import brave.Span;
import brave.Tracing;
import brave.sampler.Sampler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.GenericFilterBean;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { TraceFilterWebIntegrationMultipleFiltersTests.Config.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.sleuth.http.legacy.enabled=true")
public class TraceFilterWebIntegrationMultipleFiltersTests {

	@Autowired Tracing tracer;
	@Autowired RestTemplate restTemplate;
	@Autowired Environment environment;
	@Autowired MyFilter myFilter;
	@Autowired ArrayListSpanReporter reporter;
	// issue #550
	@Autowired @Qualifier("myExecutor") Executor myExecutor;
	@Autowired @Qualifier("finalExecutor") Executor finalExecutor;
	@Autowired MyExecutor cglibExecutor;

	@Test
	public void should_register_trace_filter_before_the_custom_filter() {
		this.myExecutor.execute(() -> System.out.println("foo"));
		this.cglibExecutor.execute(() -> System.out.println("foo"));
		this.finalExecutor.execute(() -> System.out.println("foo"));

		this.restTemplate.getForObject("http://localhost:" + port() + "/", String.class);

		then(this.tracer.tracer().currentSpan()).isNull();
		then(this.myFilter.getSpan().get()).isNotNull();
		then(this.reporter.getSpans()).isNotEmpty();
	}

	private int port() {
		return this.environment.getProperty("local.server.port", Integer.class);
	}

	@EnableAutoConfiguration
	@Configuration
	public static class Config {

		// issue #550
		@Bean Executor myExecutor() {
			return new MyExecutorWithFinalMethod();
		}

		// issue #550
		@Bean MyExecutor cglibExecutor() {
			return new MyExecutor();
		}

		// issue #550
		@Bean MyFinalExecutor finalExecutor() {
			return new MyFinalExecutor();
		}

		@Bean Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean RestTemplate restTemplate() {
			RestTemplate restTemplate = new RestTemplate();
			restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
				@Override public void handleError(ClientHttpResponse response)
						throws IOException {
				}
			});
			return restTemplate;
		}

		@Bean MyFilter myFilter(Tracing tracer) {
			return new MyFilter(tracer);
		}

		@Bean FilterRegistrationBean registrationBean(MyFilter myFilter) {
			FilterRegistrationBean bean = new FilterRegistrationBean();
			bean.setFilter(myFilter);
			bean.setOrder(0);
			return bean;
		}

		@Bean ArrayListSpanReporter reporter() {
			return new ArrayListSpanReporter();
		}
	}

	static class MyFilter extends GenericFilterBean {

		AtomicReference<Span> span = new AtomicReference<>();

		private final Tracing tracer;

		MyFilter(Tracing tracer) {
			this.tracer = tracer;
		}

		@Override public void doFilter(ServletRequest request, ServletResponse response,
				FilterChain chain) throws IOException, ServletException {
			Span currentSpan = tracer.tracer().currentSpan();
			this.span.set(currentSpan);
		}

		public AtomicReference<Span> getSpan() {
			return span;
		}
	}

	static class MyExecutor implements Executor {

		private final Executor delegate = Executors.newSingleThreadExecutor();

		@Override public void execute(Runnable command) {
			this.delegate.execute(command);
		}

		@PreDestroy
		public void destroy() {
			((ExecutorService) this.delegate).shutdown();
		}
	}

	static class MyExecutorWithFinalMethod implements Executor {

		private final Executor delegate = Executors.newSingleThreadExecutor();

		@Override public final void execute(Runnable command) {
			this.delegate.execute(command);
		}

		@PreDestroy
		public void destroy() {
			((ExecutorService) this.delegate).shutdown();
		}
	}

	static final class MyFinalExecutor implements Executor {

		private final Executor delegate = Executors.newSingleThreadExecutor();

		@Override public void execute(Runnable command) {
			this.delegate.execute(command);
		}

		@PreDestroy
		public void destroy() {
			((ExecutorService) this.delegate).shutdown();
		}
	}
}
