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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.OutputCapture;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.RibbonClients;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

import feign.codec.Decoder;
import feign.codec.ErrorDecoder;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Related to https://github.com/spring-cloud/spring-cloud-sleuth/issues/257
 *
 * @author ryarabori
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = FeignClientServerErrorTests.TestConfiguration.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "spring.application.name=fooservice" })
public class FeignClientServerErrorTests {

	@Autowired TestFeignInterface feignInterface;
	@Autowired TestFeignWithCustomConfInterface customConfFeignInterface;
	@Rule public OutputCapture capture = new OutputCapture();

	@Before
	public void setup() {
		ExceptionUtils.setFail(true);
	}

	@Test
	public void shouldCloseSpanOnInternalServerError() throws InterruptedException {
		try {
			this.feignInterface.internalError();
		} catch (HystrixRuntimeException e) {
		}

		// ugly :/ waiting for rx thread to complete
		Thread.sleep(100);
		then(this.capture.toString())
				.doesNotContain("Tried to close span but it is not the current span");
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void shouldCloseSpanOnNotFound() throws InterruptedException {
		try {
			this.feignInterface.notFound();
		} catch (HystrixRuntimeException e) {
		}

		// ugly :/ waiting for rx thread to complete
		Thread.sleep(100);
		then(this.capture.toString())
				.doesNotContain("Tried to close span but it is not the current span");
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void shouldCloseSpanOnOk() throws InterruptedException {
		try {
			this.feignInterface.ok();
		} catch (HystrixRuntimeException e) {
		}

		// ugly :/ waiting for rx thread to complete
		Thread.sleep(100);
		then(this.capture.toString())
				.doesNotContain("Tried to close span but it is not the current span");
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void shouldCloseSpanOnOkWithCustomFeignConfiguration() throws InterruptedException {
		try {
			this.customConfFeignInterface.ok();
		} catch (HystrixRuntimeException e) {
		}

		// ugly :/ waiting for rx thread to complete
		Thread.sleep(100);
		then(this.capture.toString())
				.doesNotContain("Tried to close span but it is not the current span");
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void shouldCloseSpanOnNotFoundWithCustomFeignConfiguration() throws InterruptedException {
		try {
			this.customConfFeignInterface.notFound();
		} catch (HystrixRuntimeException e) {
		}

		// ugly :/ waiting for rx thread to complete
		Thread.sleep(100);
		then(this.capture.toString())
				.doesNotContain("Tried to close span but it is not the current span");
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableFeignClients
	@RibbonClients({@RibbonClient(value = "fooservice",
			configuration = SimpleRibbonClientConfiguration.class),
			@RibbonClient(value = "customConfFooService",
			configuration = SimpleRibbonClientConfiguration.class)})
	public static class TestConfiguration {

		@Bean
		FooController fooController() {
			return new FooController();
		}

		@Bean
		Listener listener() {
			return new Listener();
		}

		@LoadBalanced
		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean Sampler testSampler() {
			return new AlwaysSampler();
		}

	}

	@FeignClient(value = "fooservice")
	public interface TestFeignInterface {

		@RequestMapping(method = RequestMethod.GET, value = "/internalerror")
		ResponseEntity<String> internalError();

		@RequestMapping(method = RequestMethod.GET, value = "/notfound")
		ResponseEntity<String> notFound();

		@RequestMapping(method = RequestMethod.GET, value = "/ok")
		ResponseEntity<String> ok();
	}

	@FeignClient(value = "customConfFooService", configuration = CustomFeignClientConfiguration.class)
	public interface TestFeignWithCustomConfInterface {

		@RequestMapping(method = RequestMethod.GET, value = "/notfound")
		ResponseEntity<String> notFound();

		@RequestMapping(method = RequestMethod.GET, value = "/ok")
		ResponseEntity<String> ok();
	}


	@Configuration
	public static class CustomFeignClientConfiguration {
		@Bean
		Decoder decoder() {
			return new Decoder.Default();
		}

		@Bean
		ErrorDecoder errorDecoder() {
			return new ErrorDecoder.Default();
		}
	}

	@Component
	public static class Listener implements SpanReporter {
		private List<Span> events = new ArrayList<>();

		public List<Span> getEvents() {
			return this.events;
		}

		@Override
		public void report(Span span) {
			this.events.add(span);
		}
	}

	@RestController
	public static class FooController {

		@Autowired
		Tracer tracer;

		@RequestMapping("/internalerror")
		public ResponseEntity<String> internalError(
				@RequestHeader(Span.TRACE_ID_NAME) String traceId,
				@RequestHeader(Span.SPAN_ID_NAME) String spanId,
				@RequestHeader(Span.PARENT_ID_NAME) String parentId) {
			return new ResponseEntity<>("internal error",
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		@RequestMapping("/notfound")
		public ResponseEntity<String> notFound(
				@RequestHeader(Span.TRACE_ID_NAME) String traceId,
				@RequestHeader(Span.SPAN_ID_NAME) String spanId,
				@RequestHeader(Span.PARENT_ID_NAME) String parentId) {
			return new ResponseEntity<>("not found", HttpStatus.NOT_FOUND);
		}

		@RequestMapping("/ok")
		public ResponseEntity<String> ok(
				@RequestHeader(Span.TRACE_ID_NAME) String traceId,
				@RequestHeader(Span.SPAN_ID_NAME) String spanId,
				@RequestHeader(Span.PARENT_ID_NAME) String parentId) {
			return new ResponseEntity<>("ok", HttpStatus.OK);
		}
	}

	@Configuration
	public static class SimpleRibbonClientConfiguration {

		@Value("${local.server.port}")
		private int port = 0;

		@Bean
		public ILoadBalancer ribbonLoadBalancer() {
			BaseLoadBalancer balancer = new BaseLoadBalancer();
			balancer.setServersList(
					Collections.singletonList(new Server("localhost", this.port)));
			return balancer;
		}
	}

}
