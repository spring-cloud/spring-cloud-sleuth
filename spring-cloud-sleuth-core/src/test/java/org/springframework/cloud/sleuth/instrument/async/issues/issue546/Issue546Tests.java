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

package org.springframework.cloud.sleuth.instrument.async.issues.issue546;

import java.lang.invoke.MethodHandles;

import brave.Tracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Issue546TestsApp.class,
		properties = {"ribbon.eureka.enabled=false", "feign.hystrix.enabled=false", "server.port=0"},
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class Issue546Tests {

	@Autowired Environment environment;

	@Test
	public void should_pass_tracing_info_when_using_callbacks() {
		new RestTemplate()
				.getForObject("http://localhost:" + port() + "/trace-async-rest-template",
						String.class);
	}

	private int port() {
		return this.environment.getProperty("local.server.port", Integer.class);
	}
}

@SpringBootApplication
class Issue546TestsApp {

	@Bean
	AsyncRestTemplate asyncRestTemplate() {
		return new AsyncRestTemplate();
	}

}

@RestController
class Controller {
	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final AsyncRestTemplate traceAsyncRestTemplate;
	private final Tracing tracer;

	public Controller(AsyncRestTemplate traceAsyncRestTemplate, Tracing tracer) {
		this.traceAsyncRestTemplate = traceAsyncRestTemplate;
		this.tracer = tracer;
	}

	@Value("${server.port}") private String port;

	@RequestMapping(value = "/bean") public HogeBean bean() {
		log.info("(/bean) I got a request!");
		return new HogeBean("test", 18);
	}

	@RequestMapping(value = "/trace-async-rest-template")
	public void asyncTest(@RequestParam(required = false) boolean isSleep)
			throws InterruptedException {
		log.info("(/trace-async-rest-template) I got a request!");
		final long traceId = tracer.tracer().currentSpan().context().traceId();
		ListenableFuture<ResponseEntity<HogeBean>> res = traceAsyncRestTemplate
				.getForEntity("http://localhost:" + port + "/bean", HogeBean.class);
		if (isSleep) {
			Thread.sleep(1000);
		}
		res.addCallback(success -> {
			then(Controller.this.tracer.tracer().currentSpan().context().traceId())
					.isEqualTo(traceId);
			log.info("(/trace-async-rest-template) success");
			then(Controller.this.tracer.tracer().currentSpan().context().traceId())
					.isEqualTo(traceId);
		}, failure -> {
			then(Controller.this.tracer.tracer().currentSpan().context().traceId())
					.isEqualTo(traceId);
			log.error("(/trace-async-rest-template) failure", failure);
			then(Controller.this.tracer.tracer().currentSpan().context().traceId())
					.isEqualTo(traceId);
		});
	}

}

class HogeBean {
	private String name;
	private int age;

	public HogeBean(String name, int age) {
		this.name = name;
		this.age = age;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getAge() {
		return this.age;
	}

	public void setAge(int age) {
		this.age = age;
	}
}