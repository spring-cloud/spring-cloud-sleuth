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

package org.springframework.cloud.sleuth.instrument.rxjava;

import brave.Span;
import brave.Tracer;
import brave.sampler.Sampler;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import rx.Observable;
import rx.functions.Action0;
import rx.plugins.RxJavaPlugins;
import rx.schedulers.Schedulers;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.BDDAssertions.then;
import static org.awaitility.Awaitility.await;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { SleuthRxJavaTests.TestConfig.class })
@DirtiesContext
public class SleuthRxJavaTests {

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
		RxJavaPlugins.getInstance().reset();
	}

	@Test
	public void should_create_new_span_when_rx_java_action_is_executed_and_there_was_no_span() {
		Observable
				.defer(() -> Observable.just(
						(Action0) () -> this.caller = new StringBuffer("actual_action")))
				.subscribeOn(Schedulers.newThread()).toBlocking()
				.subscribe(Action0::call);

		then(this.caller.toString()).isEqualTo("actual_action");
		then(this.tracer.currentSpan()).isNull();
		await().atMost(5, SECONDS)
				.untilAsserted(() -> then(this.reporter.getSpans()).hasSize(1));
		then(this.reporter.getSpans()).hasSize(1);
		zipkin2.Span span = this.reporter.getSpans().get(0);
		then(span.name()).isEqualTo("rxjava");
	}

	@Test
	public void should_continue_current_span_when_rx_java_action_is_executed() {
		Span spanInCurrentThread = this.tracer.nextSpan().name("current_span");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(spanInCurrentThread)) {
			Observable
					.defer(() -> Observable.just(
							(Action0) () -> this.caller = new StringBuffer("actual_action")))
					.subscribeOn(Schedulers.newThread()).toBlocking()
					.subscribe(Action0::call);
		} finally {
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
