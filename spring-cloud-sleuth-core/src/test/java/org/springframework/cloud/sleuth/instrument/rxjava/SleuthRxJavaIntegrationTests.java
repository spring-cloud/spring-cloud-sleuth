package org.springframework.cloud.sleuth.instrument.rxjava;

import java.util.ArrayList;
import java.util.List;
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
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import rx.Observable;
import rx.functions.Action0;
import rx.plugins.SleuthRxJavaPlugins;
import rx.schedulers.Schedulers;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {
	SleuthRxJavaIntegrationTests.TestConfig.class})
public class SleuthRxJavaIntegrationTests {

	@Autowired
	Tracer tracer;
	@Autowired
	TraceKeys traceKeys;
	@Autowired
	Listener listener;
	@Autowired
	SleuthRxJavaSchedulersHook sleuthRxJavaSchedulersHook;
	StringBuilder caller = new StringBuilder();

	@Before
	public void cleanTrace() {
		this.listener.getEvents().clear();
	}

	@BeforeClass
	@AfterClass
	public static void cleanUp() {
		SleuthRxJavaPlugins.resetPlugins();
	}

	@Test
	public void should_create_new_span_when_no_current_span_when_rx_java_action_is_executed() {
		Observable.defer(() -> Observable.just(
			(Action0) () -> this.caller = new StringBuilder("actual_action")
		)).subscribeOn(Schedulers.newThread()).toBlocking()
			.subscribe(Action0::call);

		then(this.caller.toString()).isEqualTo("actual_action");
		then(this.tracer.getCurrentSpan()).isNull();
		then(this.listener.getEvents().size()).isEqualTo(1);
		then(this.listener.getEvents().get(0)).hasNameEqualTo("rxjava");
		then(this.listener.getEvents().get(0)).hasATag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, "rxjava");
		then(this.listener.getEvents().get(0)).isALocalComponentSpan();
	}

	@Test
	public void should_continue_current_span_when_rx_java_action_is_executed() {
		Span spanInCurrentThread = this.tracer.createSpan("current_span");
		this.tracer.addTag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, "current_span");

		Observable.defer(() -> Observable.just(
			(Action0) () -> this.caller = new StringBuilder("actual_action")
		)).subscribeOn(Schedulers.newThread()).toBlocking()
			.subscribe(Action0::call);

		then(this.caller.toString()).isEqualTo("actual_action");
		then(this.tracer.getCurrentSpan()).isNotNull();
		//making sure here that no new spans were created or reported as closed
		then(this.listener.getEvents().size()).isEqualTo(0);
		then(spanInCurrentThread).hasNameEqualTo(spanInCurrentThread.getName());
		then(spanInCurrentThread).hasATag(Span.SPAN_LOCAL_COMPONENT_TAG_NAME, "current_span");
		then(spanInCurrentThread).isALocalComponentSpan();
	}

	@Component
	public static class Listener implements SpanReporter {

		List<Span> events = new ArrayList<>();

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
		Listener listener() {
			return new Listener();
		}

		@Bean
		Sampler alwaysSampler() {
			return new AlwaysSampler();
		}
	}

}
