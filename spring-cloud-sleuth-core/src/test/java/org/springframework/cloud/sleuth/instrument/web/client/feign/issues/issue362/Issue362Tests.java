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

package org.springframework.cloud.sleuth.instrument.web.client.feign.issues.issue362;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import brave.Tracing;
import brave.sampler.Sampler;
import feign.Client;
import feign.Logger;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import zipkin2.Span;
import zipkin2.reporter.Reporter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = Application.class,
		webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = {"ribbon.eureka.enabled=false",
		"feign.hystrix.enabled=false", "server.port=9998"})
public class Issue362Tests {

	RestTemplate template = new RestTemplate();
	@Autowired FeignComponentAsserter feignComponentAsserter;
	@Autowired Tracing tracer;
	@Autowired ArrayListSpanReporter reporter;

	@Before
	public void setup() {
		this.feignComponentAsserter.executedComponents.clear();
		this.reporter.clear();
	}

	@Test
	public void should_successfully_work_with_custom_error_decoder_when_sending_successful_request() {
		String securedURl = "http://localhost:9998/sleuth/test-ok";

		ResponseEntity<String> response = this.template.getForEntity(securedURl, String.class);

		then(response.getBody()).isEqualTo("I'm OK");
		then(this.feignComponentAsserter.executedComponents).containsEntry(Client.class, true);
		List<Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		then(spans.get(0).tags()).containsEntry("http.path", "/service/ok");
	}

	@Test
	public void should_successfully_work_with_custom_error_decoder_when_sending_failing_request() {
		String securedURl = "http://localhost:9998/sleuth/test-not-ok";

		try {
			this.template.getForEntity(securedURl, String.class);
			fail("should propagate an exception");
		} catch (Exception e) { }

		then(this.feignComponentAsserter.executedComponents)
				.containsEntry(ErrorDecoder.class, true)
				.containsEntry(Client.class, true);
		List<Span> spans = this.reporter.getSpans();
		// retries
		then(spans).hasSize(5);
		then(spans.stream().map(span -> span.tags().get("http.status_code")).collect(
				Collectors.toList())).containsOnly("409");
	}
}

@Configuration
@EnableAutoConfiguration(exclude = TraceWebServletAutoConfiguration.class)
@EnableFeignClients(basePackageClasses = {
		SleuthTestController.class})
class Application {

	@Bean
	public ServiceTestController serviceTestController() {
		return new ServiceTestController();
	}

	@Bean
	public SleuthTestController sleuthTestController() {
		return new SleuthTestController();
	}

	@Bean
	public Logger.Level feignLoggerLevel() {
		return Logger.Level.FULL;
	}

	@Bean
	public Sampler defaultSampler() {
		return Sampler.ALWAYS_SAMPLE;
	}

	@Bean
	public FeignComponentAsserter testHolder() { return new FeignComponentAsserter(); }

	@Bean
	public Reporter<Span> spanReporter() {
		return new ArrayListSpanReporter();
	}

}

class FeignComponentAsserter {
	Map<Class, Boolean> executedComponents = new ConcurrentHashMap<>();
}

@Configuration
class CustomConfig {

	@Bean
	public ErrorDecoder errorDecoder(
			FeignComponentAsserter feignComponentAsserter) {
		return new CustomErrorDecoder(feignComponentAsserter);
	}

	@Bean
	public Retryer retryer() {
		return new Retryer.Default();
	}

	public static class CustomErrorDecoder extends ErrorDecoder.Default {

		private final FeignComponentAsserter feignComponentAsserter;

		public CustomErrorDecoder(
				FeignComponentAsserter feignComponentAsserter) {
			this.feignComponentAsserter = feignComponentAsserter;
		}

		@Override
		public Exception decode(String methodKey, Response response) {
			this.feignComponentAsserter.executedComponents.put(ErrorDecoder.class, true);
			if (response.status() == 409) {
				return new RetryableException("Article not Ready", new Date());
			} else {
				return super.decode(methodKey, response);
			}
		}
	}

	@Bean
	public Client client(
			FeignComponentAsserter feignComponentAsserter) {
		return new CustomClient(feignComponentAsserter);
	}

	public static class CustomClient extends Client.Default {

		private final FeignComponentAsserter feignComponentAsserter;

		public CustomClient(
				FeignComponentAsserter feignComponentAsserter) {
			super(null, null);
			this.feignComponentAsserter = feignComponentAsserter;
		}

		@Override public Response execute(Request request, Request.Options options)
				throws IOException {
			this.feignComponentAsserter.executedComponents.put(Client.class, true);
			return super.execute(request, options);
		}
	}
}

@FeignClient(value="myFeignClient", url="http://localhost:9998",
		configuration = CustomConfig.class)
interface MyFeignClient {

	@RequestMapping("/service/ok")
	String ok();

	@RequestMapping("/service/not-ok")
	String exp();
}

@RestController
@RequestMapping(path = "/service")
class ServiceTestController {

	@RequestMapping("/ok")
	public String ok() throws InterruptedException, ExecutionException {
		return "I'm OK";
	}

	@RequestMapping("/not-ok")
	@ResponseStatus(HttpStatus.CONFLICT)
	public String notOk() throws InterruptedException, ExecutionException {
		return "Not OK";
	}
}

@RestController
@RequestMapping(path = "/sleuth")
class SleuthTestController {

	@Autowired
	private MyFeignClient myFeignClient;

	@RequestMapping("/test-ok")
	public String ok() throws InterruptedException, ExecutionException {
		return myFeignClient.ok();
	}

	@RequestMapping("/test-not-ok")
	public String notOk() throws InterruptedException, ExecutionException {
		return myFeignClient.exp();
	}
}