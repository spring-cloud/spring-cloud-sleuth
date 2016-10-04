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

package org.springframework.cloud.sleuth.instrument.async.issues.issue410;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
@WebIntegrationTest
@TestPropertySource(properties = {"ribbon.eureka.enabled=false", "feign.hystrix.enabled=false", "server.port=0"})
public class Issue410Tests {

	@Autowired Environment environment;
	@Autowired Tracer tracer;
	@Autowired AsyncTask asyncTask;
	@Autowired RestTemplate restTemplate;

	@Test
	public void should_pass_tracing_info_for_tasks_running_without_a_pool() {
		Span span = this.tracer.createSpan("foo");

		String response = this.restTemplate.getForObject("http://localhost:" + port() + "/without_pool", String.class);

		then(response).isEqualTo(Span.idToHex(span.getTraceId()));
		then(this.asyncTask.getSpan().get()).isNotNull();
		then(this.asyncTask.getSpan().get().getTraceId()).isEqualTo(span.getTraceId());
	}

	@Test
	public void should_pass_tracing_info_for_tasks_running_with_a_pool() {
		Span span = this.tracer.createSpan("foo");

		String response = this.restTemplate.getForObject("http://localhost:" + port() + "/with_pool", String.class);

		then(response).isEqualTo(Span.idToHex(span.getTraceId()));
		then(this.asyncTask.getSpan().get()).isNotNull();
		then(this.asyncTask.getSpan().get().getTraceId()).isEqualTo(span.getTraceId());
	}

	private int port() {
		return this.environment.getProperty("local.server.port", Integer.class);
	}
}


@Configuration
@EnableAsync
class AppConfig {

	@Bean Sampler testSampler() {
		return new AlwaysSampler();
	}

	@Bean RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Bean public Executor poolTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.initialize();
		return executor;
	}

}

@Component
class AsyncTask {

	private static final Log log = LogFactory.getLog(AsyncTask.class);

	private AtomicReference<Span> span = new AtomicReference<>();

	@Autowired Tracer tracer;

	@Async("poolTaskExecutor")
	public void runWithPool() {
		log.info("This task is running with a pool.");
		this.span.set(this.tracer.getCurrentSpan());
	}

	@Async
	public void runWithoutPool() {
		log.info("This task is running without a pool.");
		this.span.set(this.tracer.getCurrentSpan());
	}

	public AtomicReference<Span> getSpan() {
		return span;
	}
}

@SpringBootApplication(exclude = SpringDataWebAutoConfiguration.class)
@RestController
class Application {

	private static final Log log = LogFactory.getLog(Application.class);

	@Autowired AsyncTask asyncTask;
	@Autowired Tracer tracer;

	@RequestMapping("/with_pool")
	public String withPool() {
		log.info("Executing with pool.");
		this.asyncTask.runWithPool();
		return Span.idToHex(this.tracer.getCurrentSpan().getTraceId());

	}

	@RequestMapping("/without_pool")
	public String withoutPool() {
		log.info("Executing without pool.");
		this.asyncTask.runWithoutPool();
		return Span.idToHex(this.tracer.getCurrentSpan().getTraceId());
	}

}
