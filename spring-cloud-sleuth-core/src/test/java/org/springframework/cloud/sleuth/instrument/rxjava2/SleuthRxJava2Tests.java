package org.springframework.cloud.sleuth.instrument.rxjava2;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { SleuthRxJava2Tests.TestConfig.class })
@DirtiesContext
public class SleuthRxJava2Tests {

	@Autowired
	Listener listener;
	@Autowired
	Tracer tracer;
	StringBuffer caller = new StringBuffer();

	@Before
	public void clean() {
		this.listener.getEvents().clear();
	}

	@After
	public void clearTrace() {
		TestSpanContextHolder.removeCurrentSpan();
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
		then(this.tracer.getCurrentSpan()).isNull();
		await().atMost(5, SECONDS)
				.untilAsserted(() -> then(this.listener.getEvents()).hasSize(1));
		then(this.listener.getEvents().get(0)).hasNameEqualTo("rxjava2");
		then(this.listener.getEvents().get(0)).isExportable();
		then(this.listener.getEvents().get(0)).hasATag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME,
				"rxjava2");
		then(this.listener.getEvents().get(0)).isALocalComponentSpan();
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
		then(this.tracer.getCurrentSpan()).isNull();
		await().atMost(5, SECONDS)
				.untilAsserted(() -> then(this.listener.getEvents()).hasSize(1));
		then(this.listener.getEvents().get(0)).hasNameEqualTo("rxjava2");
		then(this.listener.getEvents().get(0)).isExportable();
		then(this.listener.getEvents().get(0)).hasATag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME,
				"rxjava2");
		then(this.listener.getEvents().get(0)).isALocalComponentSpan();
	}

	@Test
	public void should_continue_current_span_when_rx_java_action_is_executed() {
		Span spanInCurrentThread = this.tracer.createSpan("current_span");
		this.tracer.addTag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, "current_span");

		Observable.fromCallable(
				() -> (Runnable) () -> this.caller = new StringBuffer("actual_action"))
				.subscribeOn(Schedulers.newThread()).blockingSubscribe(Runnable::run);

		then(this.caller.toString()).isEqualTo("actual_action");
		then(this.tracer.getCurrentSpan()).isNotNull();
		// making sure here that no new spans were created or reported as closed
		await().atMost(2, SECONDS)
				.untilAsserted(() -> then(this.listener.getEvents()).isEmpty());
		then(spanInCurrentThread).hasNameEqualTo(spanInCurrentThread.getName());
		then(spanInCurrentThread).isExportable();
		then(spanInCurrentThread).hasATag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME,
				"current_span");
		then(spanInCurrentThread).isALocalComponentSpan();
	}

	static class Listener implements SpanReporter {

		private List<Span> events = new ArrayList<>();

		public List<Span> getEvents() {
			return this.events;
		}

		@Override
		public void report(Span span) {
			this.events.add(span);
		}
	}

	@Configuration
	@EnableAutoConfiguration
	public static class TestConfig {

		@Bean
		Sampler alwaysSampler() {
			return new AlwaysSampler();
		}

		@Bean
		SpanReporter spanReporter() {
			return new Listener();
		}

	}
}
