/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.brave.instrument.async.issues.issue410;

import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import brave.Span;
import brave.Tracer;
import brave.sampler.Sampler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.sleuth.instrument.async.LazyTraceExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@SpringBootTest(classes = { AppConfig.class, Application.class }, webEnvironment = WebEnvironment.RANDOM_PORT)
public class Issue410Tests {

	private static final Log log = LogFactory.getLog(Issue410Tests.class);

	@Autowired
	Environment environment;

	@Autowired
	Tracer tracer;

	@Autowired
	AsyncTask asyncTask;

	@Autowired
	RestTemplate restTemplate;

	/**
	 * Related to issue #445.
	 */
	@Autowired
	Application.MyService executorService;

	@Test
	public void should_pass_tracing_info_for_tasks_running_without_a_pool() {
		Span span = this.tracer.nextSpan().name("foo");
		log.info("Starting test");
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			String response = this.restTemplate.getForObject("http://localhost:" + port() + "/without_pool",
					String.class);

			then(response).isEqualTo(span.context().traceIdString());
			Awaitility.await().untilAsserted(() -> {
				then(this.asyncTask.getSpan().get()).isNotNull();
				then(this.asyncTask.getSpan().get().context().traceId()).isEqualTo(span.context().traceId());
			});
		}
		finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void should_pass_tracing_info_for_tasks_running_with_a_pool() {
		Span span = this.tracer.nextSpan().name("foo");
		log.info("Starting test");
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			String response = this.restTemplate.getForObject("http://localhost:" + port() + "/with_pool", String.class);

			then(response).isEqualTo(span.context().traceIdString());
			Awaitility.await().untilAsserted(() -> {
				then(this.asyncTask.getSpan().get()).isNotNull();
				then(this.asyncTask.getSpan().get().context().traceId()).isEqualTo(span.context().traceId());
			});
		}
		finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
	}

	/**
	 * Related to issue #423.
	 */
	@Test
	public void should_pass_tracing_info_for_completable_futures_with_executor() {
		Span span = this.tracer.nextSpan().name("foo");
		log.info("Starting test");
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			String response = this.restTemplate.getForObject("http://localhost:" + port() + "/completable",
					String.class);

			then(response).isEqualTo(span.context().traceIdString());
			Awaitility.await().untilAsserted(() -> {
				then(this.asyncTask.getSpan().get()).isNotNull();
				then(this.asyncTask.getSpan().get().context().traceId()).isEqualTo(span.context().traceId());
			});
		}
		finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
	}

	/**
	 * Related to issue #423.
	 */
	@Test
	public void should_pass_tracing_info_for_completable_futures_with_task_scheduler() {
		Span span = this.tracer.nextSpan().name("foo");
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			log.info("Starting test");
			String response = this.restTemplate.getForObject("http://localhost:" + port() + "/taskScheduler",
					String.class);

			then(response).isEqualTo(span.context().traceIdString());
			Awaitility.await().untilAsserted(() -> {
				then(this.asyncTask.getSpan().get()).isNotNull();
				then(this.asyncTask.getSpan().get().context().traceId()).isEqualTo(span.context().traceId());
			});
		}
		finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
	}

	/**
	 * Related to issue #1232
	 */
	@Test
	public void should_pass_tracing_info_for_submitted_tasks_with_threadPoolTaskScheduler() {
		Span span = this.tracer.nextSpan().name("foo");
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			log.info("Starting test");
			String response = this.restTemplate
					.getForObject("http://localhost:" + port() + "/threadPoolTaskScheduler_submit", String.class);

			then(response).isEqualTo(span.context().traceIdString());
			Awaitility.await().untilAsserted(() -> {
				then(this.asyncTask.getSpan().get()).isNotNull();
				then(this.asyncTask.getSpan().get().context().traceId()).isEqualTo(span.context().traceId());
			});
		}
		finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void should_pass_tracing_info_for_scheduled_tasks_with_threadPoolTaskScheduler() {
		Span span = this.tracer.nextSpan().name("foo");
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			log.info("Starting test");
			String response = this.restTemplate
					.getForObject("http://localhost:" + port() + "/threadPoolTaskScheduler_schedule", String.class);

			then(response).isEqualTo(span.context().traceIdString());
			Awaitility.await().untilAsserted(() -> {
				then(this.asyncTask.getSpan().get()).isNotNull();
				then(this.asyncTask.getSpan().get().context().traceId()).isEqualTo(span.context().traceId());
			});
		}
		finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
	}

	/**
	 * Related to issue #1232
	 */
	@Test
	public void should_pass_tracing_info_for_completable_futures_with_scheduledThreadPoolExecutor() {
		Span span = this.tracer.nextSpan().name("foo");
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			log.info("Starting test");
			String response = this.restTemplate
					.getForObject("http://localhost:" + port() + "/scheduledThreadPoolExecutor", String.class);

			then(response).isEqualTo(span.context().traceIdString());
			Awaitility.await().untilAsserted(() -> {
				then(this.asyncTask.getSpan().get()).isNotNull();
				then(this.asyncTask.getSpan().get().context().traceId()).isEqualTo(span.context().traceId());
			});
		}
		finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
	}

	private int port() {
		return this.environment.getProperty("local.server.port", Integer.class);
	}

}

@Configuration(proxyBeanMethods = false)
@EnableAsync
class AppConfig {

	@Bean
	public Sampler testSampler() {
		return Sampler.ALWAYS_SAMPLE;
	}

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Bean("taskScheduler")
	public Executor myScheduler() {
		return Executors.newSingleThreadExecutor();
	}

	@Bean
	public Executor poolTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.initialize();
		return executor;
	}

	@Bean
	public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
		ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
		executor.initialize();
		return executor;
	}

	@Bean
	public ScheduledThreadPoolExecutor scheduledThreadPoolExecutor() {
		return new ScheduledThreadPoolExecutor(10);
	}

}

@Component
class AsyncTask {

	private static final Log log = LogFactory.getLog(AsyncTask.class);

	@Autowired
	Tracer tracer;

	@Autowired
	@Qualifier("poolTaskExecutor")
	Executor executor;

	@Autowired
	@Qualifier("taskScheduler")
	Executor taskScheduler;

	@Autowired
	BeanFactory beanFactory;

	@Autowired
	ThreadPoolTaskScheduler threadPoolTaskScheduler;

	@Autowired
	ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

	private AtomicReference<Span> span = new AtomicReference<>();

	@Async("poolTaskExecutor")
	public void runWithPool() {
		log.info("This task is running with a pool.");
		this.span.set(this.tracer.currentSpan());
	}

	@Async
	public void runWithoutPool() {
		log.info("This task is running without a pool.");
		this.span.set(this.tracer.currentSpan());
	}

	public Span completableFutures() throws ExecutionException, InterruptedException {
		log.info("This task is running with completable future");
		CompletableFuture<Span> span1 = CompletableFuture.supplyAsync(() -> {
			AsyncTask.log.info("First completable future");
			return AsyncTask.this.tracer.currentSpan();
		}, AsyncTask.this.executor);
		CompletableFuture<Span> span2 = CompletableFuture.supplyAsync(() -> {
			AsyncTask.log.info("Second completable future");
			return AsyncTask.this.tracer.currentSpan();
		}, AsyncTask.this.executor);
		CompletableFuture<Span> response = CompletableFuture.allOf(span1, span2).thenApply(ignoredVoid -> {
			AsyncTask.log.info("Third completable future");
			Span joinedSpan1 = span1.join();
			Span joinedSpan2 = span2.join();
			then(joinedSpan2).isNotNull();
			then(joinedSpan1.context().traceId()).isEqualTo(joinedSpan2.context().traceId());
			AsyncTask.log.info("TraceIds are correct");
			return joinedSpan2;
		});
		this.span.set(response.get());
		return this.span.get();
	}

	public Span taskScheduler() throws ExecutionException, InterruptedException {
		log.info("This task is running with completable future");
		CompletableFuture<Span> span1 = CompletableFuture.supplyAsync(() -> {
			AsyncTask.log.info("First completable future");
			return AsyncTask.this.tracer.currentSpan();
		}, new LazyTraceExecutor(AsyncTask.this.beanFactory, AsyncTask.this.taskScheduler));
		CompletableFuture<Span> span2 = CompletableFuture.supplyAsync(() -> {
			AsyncTask.log.info("Second completable future");
			return AsyncTask.this.tracer.currentSpan();
		}, new LazyTraceExecutor(AsyncTask.this.beanFactory, AsyncTask.this.taskScheduler));
		CompletableFuture<Span> response = CompletableFuture.allOf(span1, span2).thenApply(ignoredVoid -> {
			AsyncTask.log.info("Third completable future");
			Span joinedSpan1 = span1.join();
			Span joinedSpan2 = span2.join();
			then(joinedSpan2).isNotNull();
			then(joinedSpan1.context().traceId()).isEqualTo(joinedSpan2.context().traceId());
			AsyncTask.log.info("TraceIds are correct");
			return joinedSpan2;
		});
		this.span.set(response.get());
		return this.span.get();
	}

	public Span scheduledThreadPoolExecutor() throws ExecutionException, InterruptedException {
		log.info("This task is running with ScheduledThreadPoolExecutor");
		this.scheduledThreadPoolExecutor.submit(() -> {
			log.info("Hello from runnable");
			AsyncTask.this.span.set(AsyncTask.this.tracer.currentSpan());
		}).get();
		return this.span.get();
	}

	public Span threadPoolTaskSchedulerSubmit() throws ExecutionException, InterruptedException {
		log.info("This task is running with ThreadPoolTaskScheduler");
		this.threadPoolTaskScheduler.submit(() -> {
			log.info("Hello from runnable");
			AsyncTask.this.span.set(AsyncTask.this.tracer.currentSpan());
		}).get();
		return this.span.get();
	}

	public Span threadPoolTaskSchedulerSchedule() throws ExecutionException, InterruptedException {
		log.info("This task is running with ThreadPoolTaskScheduler");
		this.threadPoolTaskScheduler.schedule(() -> {
			log.info("Hello from runnable");
			AsyncTask.this.span.set(AsyncTask.this.tracer.currentSpan());
		}, new Date()).get();
		return this.span.get();
	}

	public AtomicReference<Span> getSpan() {
		return this.span;
	}

}

@SpringBootApplication(exclude = SpringDataWebAutoConfiguration.class)
@RestController
class Application {

	private static final Log log = LogFactory.getLog(Application.class);

	@Autowired
	AsyncTask asyncTask;

	@Autowired
	Tracer tracer;

	@RequestMapping("/with_pool")
	public String withPool() {
		log.info("Executing with pool.");
		this.asyncTask.runWithPool();
		return this.tracer.currentSpan().context().traceIdString();

	}

	@RequestMapping("/without_pool")
	public String withoutPool() {
		log.info("Executing without pool.");
		this.asyncTask.runWithoutPool();
		return this.tracer.currentSpan().context().traceIdString();
	}

	@RequestMapping("/completable")
	public String completable() throws ExecutionException, InterruptedException {
		log.info("Executing completable");
		return this.asyncTask.completableFutures().context().traceIdString();
	}

	@RequestMapping("/taskScheduler")
	public String taskScheduler() throws ExecutionException, InterruptedException {
		log.info("Executing completable via task scheduler");
		return this.asyncTask.taskScheduler().context().traceIdString();
	}

	@RequestMapping("/threadPoolTaskScheduler_submit")
	public String threadPoolTaskSchedulerSubmit() throws ExecutionException, InterruptedException {
		log.info("Executing completable via ThreadPoolTaskScheduler");
		return this.asyncTask.threadPoolTaskSchedulerSubmit().context().traceIdString();
	}

	@RequestMapping("/threadPoolTaskScheduler_schedule")
	public String threadPoolTaskSchedulerSchedule() throws ExecutionException, InterruptedException {
		log.info("Executing completable via ThreadPoolTaskScheduler");
		return this.asyncTask.threadPoolTaskSchedulerSchedule().context().traceIdString();
	}

	@RequestMapping("/scheduledThreadPoolExecutor")
	public String scheduledThreadPoolExecutor() throws ExecutionException, InterruptedException {
		log.info("Executing completable via ScheduledThreadPoolExecutor");
		return this.asyncTask.scheduledThreadPoolExecutor().context().traceIdString();
	}

	/**
	 * Related to issue #445.
	 * @return service bean
	 */
	@Bean
	public MyService executorService() {
		return new MyService() {
			@Override
			public void execute(Runnable command) {

			}
		};
	}

	interface MyService extends Executor {

	}

}
