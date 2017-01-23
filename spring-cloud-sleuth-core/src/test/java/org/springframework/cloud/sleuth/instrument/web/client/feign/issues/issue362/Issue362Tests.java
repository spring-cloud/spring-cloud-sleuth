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

package org.springframework.cloud.sleuth.instrument.web.client.feign.issues.issue362;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import feign.Logger;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class, webEnvironment =
		SpringBootTest.WebEnvironment.DEFINED_PORT,
		properties = {"ribbon.eureka.enabled=false", "feign.hystrix.enabled=false", "server.port=9998"})
public class Issue362Tests {

	RestTemplate template = new RestTemplate();
	@Autowired FeignComponentAsserter feignComponentAsserter;
	@Autowired Tracer tracer;

	@Before
	public void setup() {
		this.feignComponentAsserter.executedComponents.clear();
		ExceptionUtils.setFail(true);
	}

	@Test
	public void should_successfully_work_with_custom_error_decoder_when_sending_successful_request() {
		String securedURl = "http://localhost:9998/sleuth/test-ok";

		ResponseEntity<String> response = this.template.getForEntity(securedURl, String.class);

		then(response.getBody()).isEqualTo("I'm OK");
		then(ExceptionUtils.getLastException()).isNull();
		then(this.tracer.getCurrentSpan()).isNull();
	}

	@Test
	public void should_successfully_work_with_custom_error_decoder_when_sending_failing_request() {
		String securedURl = "http://localhost:9998/sleuth/test-not-ok";

		try {
			this.template.getForEntity(securedURl, String.class);
			fail("should propagate an exception");
		} catch (Exception e) { }

		then(ExceptionUtils.getLastException()).isNull();
		then(this.feignComponentAsserter.executedComponents)
				.containsEntry(ErrorDecoder.class, true);
		then(this.tracer.getCurrentSpan()).isNull();
	}
}

@Configuration
@EnableAutoConfiguration
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
		return feign.Logger.Level.FULL;
	}

	@Bean
	public AlwaysSampler defaultSampler() {
		return new AlwaysSampler();
	}

	@Bean
	public FeignComponentAsserter testHolder() { return new FeignComponentAsserter(); }

}

class FeignComponentAsserter {
	Map<Class, Boolean> executedComponents = new ConcurrentHashMap<>();
}

@Configuration
class CustomConfig {

	@Bean
	public ErrorDecoder errorDecoder(FeignComponentAsserter feignComponentAsserter) {
		return new CustomErrorDecoder(feignComponentAsserter);
	}

	@Bean
	public Retryer retryer() {
		return new Retryer.Default();
	}

	public static class CustomErrorDecoder extends ErrorDecoder.Default {

		private final FeignComponentAsserter feignComponentAsserter;

		public CustomErrorDecoder(FeignComponentAsserter feignComponentAsserter) {
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
}

@FeignClient(name="myFeignClient", url="http://localhost:9998",
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
		String result = "I'm OK";
		return result;
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
		String result = myFeignClient.ok();
		return result;
	}

	@RequestMapping("/test-not-ok")
	public String notOk() throws InterruptedException, ExecutionException {
		String result = myFeignClient.exp();
		return result;
	}
}