/*
 * Copyright 2013-2016 the original author or authors.
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
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.GenericFilterBean;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { TraceFilterWebIntegrationMultipleFiltersTests.Config.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TraceFilterWebIntegrationMultipleFiltersTests {

	@Autowired Tracer tracer;
	@Autowired RestTemplate restTemplate;
	@Autowired Environment environment;
	@Autowired MyFilter myFilter;

	@Before
	@After
	public void cleanup() {
		ExceptionUtils.setFail(true);
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_register_trace_filter_before_the_custom_filter() {
		this.restTemplate.getForObject("http://localhost:" + port() + "/", String.class);

		then(this.tracer.getCurrentSpan()).isNull();
		then(this.myFilter.getSpan().get()).isNotNull();
		then(ExceptionUtils.getLastException()).isNull();
	}

	private int port() {
		return this.environment.getProperty("local.server.port", Integer.class);
	}

	@EnableAutoConfiguration
	@Configuration
	public static class Config {

		@Bean Sampler alwaysSampler() {
			return new AlwaysSampler();
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

		@Bean MyFilter myFilter(Tracer tracer) {
			return new MyFilter(tracer);
		}

		@Bean FilterRegistrationBean registrationBean(MyFilter myFilter) {
			FilterRegistrationBean bean = new FilterRegistrationBean();
			bean.setFilter(myFilter);
			bean.setOrder(0);
			return bean;
		}
	}

	static class MyFilter extends GenericFilterBean {

		AtomicReference<Span> span = new AtomicReference<>();

		private final Tracer tracer;

		MyFilter(Tracer tracer) {
			this.tracer = tracer;
		}

		@Override public void doFilter(ServletRequest request, ServletResponse response,
				FilterChain chain) throws IOException, ServletException {
			Span currentSpan = tracer.getCurrentSpan();
			this.span.set(currentSpan);
		}

		public AtomicReference<Span> getSpan() {
			return span;
		}
	}
}
