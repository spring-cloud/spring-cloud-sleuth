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

package org.springframework.cloud.sleuth.instrument.web.client.exception;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
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

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import static junitparams.JUnitParamsRunner.$;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

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
	public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();
	@Rule
	public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

	@Autowired TestFeignInterfaceWithException testFeignInterfaceWithException;
	@Autowired @LoadBalanced RestTemplate template;
	@Autowired Tracer tracer;

	@Before
	public void open() {
		TestSpanContextHolder.removeCurrentSpan();
		ExceptionUtils.setFail(true);
	}

	@After
	public void close() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	// issue #198
	@Test
	@Parameters
	public void shouldCloseSpanUponException(ResponseEntityProvider provider)
			throws IOException {
		Span span = this.tracer.createSpan("new trace");
		log.info("Started new span " + span);

		try {
			provider.get(this);
			Assert.fail("should throw an exception");
		}
		catch (RuntimeException e) {
			// SleuthAssertions.then(e).hasRootCauseInstanceOf(IOException.class);
		}

		then(ExceptionUtils.getLastException()).isNull();
		then(this.tracer.getCurrentSpan()).isEqualTo(span);
		this.tracer.close(span);
		then(ExceptionUtils.getLastException()).isNull();
		then(this.systemErrRule.getLog()).doesNotContain("Tried to detach trace span but it is not the current span");
		then(this.systemOutRule.getLog()).doesNotContain("Tried to detach trace span but it is not the current span");
	}

	Object[] parametersForShouldCloseSpanUponException() {
		return $(
				(ResponseEntityProvider) (tests) -> tests.testFeignInterfaceWithException
						.shouldFailToConnect(),
				(ResponseEntityProvider) (tests) -> tests.template
						.getForEntity("http://exceptionservice/", Map.class));
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

		@Bean
		Sampler alwaysSampler() {
			return new AlwaysSampler();
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
		ResponseEntity<?> get(WebClientExceptionTests webClientTests);
	}
}
