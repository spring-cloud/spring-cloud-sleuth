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

package org.springframework.cloud.sleuth.instrument.web.client.feign.issues.issue350;

import java.util.List;
import java.util.concurrent.ExecutionException;

import brave.Tracing;
import brave.sampler.Sampler;
import feign.Logger;
import zipkin2.Span;
import zipkin2.reporter.Reporter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = Application.class,
		webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = {"ribbon.eureka.enabled=false",
		"feign.hystrix.enabled=false", "server.port=9988"})
public class Issue350Tests {

	TestRestTemplate template = new TestRestTemplate();
	@Autowired Tracing tracer;
	@Autowired ArrayListSpanReporter reporter;

	@Test
	public void should_successfully_work_without_hystrix() {
		this.template.getForEntity("http://localhost:9988/sleuth/test-not-ok", String.class);

		List<Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		then(spans.get(0).tags()).containsEntry("http.status_code", "406");
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
	public Reporter<Span> spanReporter() {
		return new ArrayListSpanReporter();
	}
}

@RestController
@RequestMapping(path = "/service")
class ServiceTestController {

	@RequestMapping("/ok")
	public String ok() throws InterruptedException, ExecutionException {
		return "I'm OK";
	}

	@RequestMapping("/not-ok")
	@ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
	public String notOk() throws InterruptedException, ExecutionException {
		return "Not OK";
	}
}

@FeignClient(name="myFeignClient", url="localhost:9988")
interface MyFeignClient {

	@RequestMapping("/service/ok")
	String ok();

	@RequestMapping("/service/not-ok")
	String exp();
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

