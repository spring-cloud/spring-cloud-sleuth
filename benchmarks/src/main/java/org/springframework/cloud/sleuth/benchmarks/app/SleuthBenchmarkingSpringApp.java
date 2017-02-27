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

package org.springframework.cloud.sleuth.benchmarks.app;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.PreDestroy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.cloud.sleuth.annotation.SpanTag;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.util.SocketUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Marcin Grzejszczak
 */
@SpringBootApplication
@RestController
@EnableAsync
public class SleuthBenchmarkingSpringApp implements
		ApplicationListener<EmbeddedServletContainerInitializedEvent> {

	private static final Log log = LogFactory.getLog(SleuthBenchmarkingSpringApp.class);

	public final ExecutorService pool = Executors.newWorkStealingPool();

	public int port;

	@Autowired(required = false) Tracer tracer;

	@RequestMapping("/foo")
	public String foo() {
		return "foo";
	}

	@RequestMapping("/bar")
	public Callable<String> bar() {
		return () -> "bar";
	}

	@RequestMapping("/async")
	public String asyncHttp() throws ExecutionException, InterruptedException {
		return this.async().get();
	}

	public String manualSpan() {
		Span manual = this.tracer.createSpan("span-name");
		try {
			return continuedSpan();
		} finally {
			this.tracer.close(manual);
		}
	}

	private String continuedSpan() {
		Span continuedSpan = this.tracer.continueSpan(this.tracer.getCurrentSpan());
		this.tracer.addTag("foo", "bar");
		continuedSpan.logEvent("continuedspan.start");
		String response = "continued";
		continuedSpan.logEvent("continuedspan.end");
		return response;
	}

	@NewSpan
	public String newSpan() {
		return continuedAnnotation("bar");
	}

	@ContinueSpan(log = "continuedspan")
	public String continuedAnnotation(@SpanTag("foo") String tagValue) {
		return "continued";
	}

	@Async
	public Future<String> async() {
		return this.pool.submit(() -> "async");
	}

	@Override
	public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
		this.port = event.getEmbeddedServletContainer().getPort();
	}

	@Bean
	public EmbeddedServletContainerFactory servletContainer(@Value("${server.port:0}") int serverPort) {
		log.info("Starting container at port [" + serverPort + "]");
		return new TomcatEmbeddedServletContainerFactory(serverPort == 0 ? SocketUtils.findAvailableTcpPort() : serverPort);
	}

	@PreDestroy
	public void clean() {
		this.pool.shutdownNow();
	}

	@Bean
	public Sampler alwaysSampler() {
		return new AlwaysSampler();
	}

	public ExecutorService getPool() {
		return this.pool;
	}

	public static void main(String... args) {
		SpringApplication.run(SleuthBenchmarkingSpringApp.class, args);
	}
}
