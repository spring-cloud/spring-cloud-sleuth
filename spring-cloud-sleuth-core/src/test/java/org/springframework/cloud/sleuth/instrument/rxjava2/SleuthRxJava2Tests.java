package org.springframework.cloud.sleuth.instrument.rxjava2;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.BDDAssertions.then;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.Executors;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import brave.Span;
import brave.Tracer;
import brave.sampler.Sampler;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { SleuthRxJava2Tests.TestConfig.class })
@DirtiesContext
public class SleuthRxJava2Tests {

	@Autowired
	ArrayListSpanReporter reporter;
	@Autowired
	Tracer tracer;
	StringBuffer caller = new StringBuffer();

	@Before
	public void clean() {
		this.reporter.clear();
	}

	@BeforeClass
	@AfterClass
	public static void cleanUp() {
		RxJavaPlugins.reset();
	}

	@Test
	public void should_create_new_span_when_rx_java_action_is_executed_and_there_was_no_span() {
		Observable.fromCallable(
				() -> (Runnable) () -> this.caller = new StringBuffer("actual_action"))
				.subscribeOn(Schedulers.io()).blockingSubscribe(Runnable::run);

		then(this.caller.toString()).isEqualTo("actual_action");
		then(this.tracer.currentSpan()).isNull();
		await().atMost(5, SECONDS)
				.untilAsserted(() -> then(this.reporter.getSpans()).hasSize(1));
		zipkin2.Span span = this.reporter.getSpans().get(0);
		then(span.name()).isEqualTo("rxjava2");
	}

	@Test
	public void should_create_new_span_when_rx_java_action_is_executed_and_there_was_no_span_and_on_thread_pool_changed() {
		Scheduler scheduler = Schedulers.from(Executors
				.newSingleThreadExecutor(new CustomizableThreadFactory("myPool-")));
		Scheduler scheduler2 = Schedulers.from(Executors
				.newSingleThreadExecutor(new CustomizableThreadFactory("myPool2-")));

		Observable.fromCallable(
				() -> (Runnable) () -> this.caller = new StringBuffer("actual_action"))
				.observeOn(scheduler).observeOn(scheduler2).subscribeOn(Schedulers.io())
				.blockingSubscribe(Runnable::run);

		then(this.caller.toString()).isEqualTo("actual_action");
		then(this.tracer.currentSpan()).isNull();
		await().atMost(5, SECONDS)
				.untilAsserted(() -> then(this.reporter.getSpans()).hasSize(1));
		zipkin2.Span span = this.reporter.getSpans().get(0);
		then(span.name()).isEqualTo("rxjava2");
	}

	@Test
	public void should_continue_current_span_when_rx_java_action_is_executed() {
		Span spanInCurrentThread = this.tracer.nextSpan().name("current_span");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(spanInCurrentThread)) {
			Observable
					.fromCallable(() -> (Runnable) () -> this.caller = new StringBuffer(
							"actual_action"))
					.subscribeOn(Schedulers.newThread()).blockingSubscribe(Runnable::run);
		}
		finally {
			spanInCurrentThread.finish();
		}
		then(this.caller.toString()).isEqualTo("actual_action");
		then(this.tracer.currentSpan()).isNull();
		// making sure here that no new spans were created or reported as closed
		then(this.reporter.getSpans()).hasSize(1);
		zipkin2.Span span = this.reporter.getSpans().get(0);
		then(span.name()).isEqualTo("current_span");
	}

	@Configuration
	@EnableAutoConfiguration
	public static class TestConfig {

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		ArrayListSpanReporter spanReporter() {
			return new ArrayListSpanReporter();
		}

	}
}
