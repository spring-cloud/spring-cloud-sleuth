package org.springframework.cloud.sleuth.instrument.rxjava;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import rx.Observable;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

import static com.jayway.awaitility.Awaitility.await;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;
import rx.plugins.RxJavaPlugins;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {SleuthRxJavaTests.TestConfig.class})
@DirtiesContext
public class SleuthRxJavaTests {

	@Autowired Listener listener;
	@Autowired Tracer tracer;
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
		RxJavaPlugins.getInstance().reset();
	}

	@Test
	public void should_create_new_span_when_rx_java_action_is_executed_and_there_was_no_span() {
		Observable.defer(() -> Observable.just(
			(Action0) () -> this.caller = new StringBuffer("actual_action")
		)).subscribeOn(Schedulers.newThread()).toBlocking()
			.subscribe(Action0::call);

		then(this.caller.toString()).isEqualTo("actual_action");
		then(this.tracer.getCurrentSpan()).isNull();
		await().until(() -> then(this.listener.getEvents()).hasSize(1));
		then(this.listener.getEvents().get(0)).hasNameEqualTo("rxjava");
		then(this.listener.getEvents().get(0)).hasATag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, "rxjava");
		then(this.listener.getEvents().get(0)).isALocalComponentSpan();
	}

	@Test
	public void should_continue_current_span_when_rx_java_action_is_executed() {
		Span spanInCurrentThread = this.tracer.createSpan("current_span");
		this.tracer.addTag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, "current_span");

		Observable.defer(() -> Observable.just(
			(Action0) () -> this.caller = new StringBuffer("actual_action")
		)).subscribeOn(Schedulers.newThread()).toBlocking()
			.subscribe(Action0::call);

		then(this.caller.toString()).isEqualTo("actual_action");
		then(this.tracer.getCurrentSpan()).isNotNull();
		//making sure here that no new spans were created or reported as closed
		then(this.listener.getEvents()).isEmpty();
		then(spanInCurrentThread).hasNameEqualTo(spanInCurrentThread.getName());
		then(spanInCurrentThread).hasATag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, "current_span");
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
