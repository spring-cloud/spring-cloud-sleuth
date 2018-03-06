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

package org.springframework.cloud.sleuth.instrument.web.client.exception;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Map;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.sampler.Sampler;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestTemplate;

import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(JUnitParamsRunner.class)
@SpringBootTest(classes = {
		WebClientExceptionTests.TestConfiguration.class },
		properties = {"ribbon.ConnectTimeout=30000", "spring.application.name=exceptionservice" },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebClientExceptionTests {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	@ClassRule
	public static final SpringClassRule SCR = new SpringClassRule();
	@Rule
	public final SpringMethodRule springMethodRule = new SpringMethodRule();
	@Rule
	public final OutputCapture capture = new OutputCapture();

	@Autowired TestFeignInterfaceWithException testFeignInterfaceWithException;
	@Autowired @LoadBalanced RestTemplate template;
	@Autowired Tracing tracer;
	@Autowired ArrayListSpanReporter reporter;

	@Before
	public void open() {
		this.reporter.clear();
	}

	// issue #198
	@Test
	@Parameters
	public void shouldCloseSpanUponException(ResponseEntityProvider provider)
			throws IOException {
		Span span = this.tracer.tracer().nextSpan().name("new trace").start();

		try (Tracer.SpanInScope ws = this.tracer.tracer().withSpanInScope(span)) {
			log.info("Started new span " + span);
			provider.get(this);
			Assert.fail("should throw an exception");
		}
		catch (RuntimeException e) {
			// SleuthAssertions.then(e).hasRootCauseInstanceOf(IOException.class);
		} finally {
			span.finish();
		}

		then(this.tracer.tracer().currentSpan()).isNull();
		then(this.reporter.getSpans()).isNotEmpty();
		then(this.reporter.getSpans().get(0).tags()).containsKey("error");
	}

	Object[] parametersForShouldCloseSpanUponException() {
		return new Object[] {
				(ResponseEntityProvider) (tests) -> tests.testFeignInterfaceWithException
						.shouldFailToConnect(),
				(ResponseEntityProvider) (tests) -> tests.template
						.getForEntity("http://exceptionservice/", Map.class) };
	}

	@FeignClient("exceptionservice")
	public interface TestFeignInterfaceWithException {
		@RequestMapping(method = RequestMethod.GET, value = "/")
		ResponseEntity<String> shouldFailToConnect();
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableFeignClients
	@RibbonClient(value = "exceptionservice", configuration = ExceptionServiceRibbonClientConfiguration.class)
	public static class TestConfiguration {

		@LoadBalanced
		@Bean
		public RestTemplate restTemplate() {
			SimpleClientHttpRequestFactory clientHttpRequestFactory = new SimpleClientHttpRequestFactory();
			clientHttpRequestFactory.setReadTimeout(1);
			clientHttpRequestFactory.setConnectTimeout(1);
			return new RestTemplate(clientHttpRequestFactory);
		}

		@Bean Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean ArrayListSpanReporter accumulator() {
			return new ArrayListSpanReporter();
		}
	}

	@Configuration
	public static class ExceptionServiceRibbonClientConfiguration {

		@Bean
		public ILoadBalancer exceptionServiceRibbonLoadBalancer() {
			BaseLoadBalancer balancer = new BaseLoadBalancer();
			balancer.setServersList(Collections
					.singletonList(new Server("invalid.host.to.break.tests", 1234)));
			return balancer;
		}

	}

	@FunctionalInterface
	interface ResponseEntityProvider {
		ResponseEntity<?> get(
				WebClientExceptionTests webClientTests);
	}
}
