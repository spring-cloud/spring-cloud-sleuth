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

package org.springframework.cloud.sleuth.instrument.scheduling;

import java.util.AbstractMap;
import java.util.concurrent.atomic.AtomicBoolean;

import brave.Span;
import brave.Tracing;
import brave.sampler.Sampler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin2.reporter.Reporter;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.BDDAssertions.then;
import static org.awaitility.Awaitility.await;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { ScheduledTestConfiguration.class })
@DirtiesContext
public class TracingOnScheduledTests {

	@Autowired TestBeanWithScheduledMethod beanWithScheduledMethod;
	@Autowired TestBeanWithScheduledMethodToBeIgnored beanWithScheduledMethodToBeIgnored;
	@Autowired ArrayListSpanReporter reporter;

	@Before
	public void setup() {
		this.beanWithScheduledMethod.clear();
		this.beanWithScheduledMethodToBeIgnored.clear();
	}

	@Test
	public void should_have_span_set_after_scheduled_method_has_been_executed() {
		await().atMost(	10, SECONDS).untilAsserted(() -> {
			then(this.beanWithScheduledMethod.isExecuted()).isTrue();
			spanIsSetOnAScheduledMethod();
		});
	}

	@Test
	public void should_have_a_new_span_set_each_time_a_scheduled_method_has_been_executed() {
		final Span firstSpan = this.beanWithScheduledMethod.getSpan();
		await().atMost(5, SECONDS).untilAsserted(() -> {
			then(this.beanWithScheduledMethod.isExecuted()).isTrue();
			differentSpanHasBeenSetThan(firstSpan);
		});
	}

	@Test
	public void should_not_create_span_in_the_scheduled_class_that_matches_skip_pattern()
			throws Exception {
		await().atMost(5, SECONDS).untilAsserted(() -> {
			then(this.beanWithScheduledMethodToBeIgnored.isExecuted()).isTrue();
			then(this.beanWithScheduledMethodToBeIgnored.getSpan()).isNull();
		});
	}

	private void spanIsSetOnAScheduledMethod() {
		Span storedSpan = TracingOnScheduledTests.this.beanWithScheduledMethod
				.getSpan();
		then(storedSpan).isNotNull();
		then(storedSpan.context().traceId()).isNotNull();
		then(this.reporter.getSpans().get(0).tags())
				.contains(new AbstractMap.SimpleEntry<>("class", "TestBeanWithScheduledMethod"),
						new AbstractMap.SimpleEntry<>("method", "scheduledMethod"));
	}

	private void differentSpanHasBeenSetThan(final Span spanToCompare) {
		then(TracingOnScheduledTests.this.beanWithScheduledMethod.getSpan())
				.isNotEqualTo(spanToCompare);
	}

}

@Configuration
@DefaultTestAutoConfiguration
@EnableScheduling
class ScheduledTestConfiguration {

	@Bean Reporter<zipkin2.Span> testRepoter() {
		return new ArrayListSpanReporter();
	}

	@Bean TestBeanWithScheduledMethod testBeanWithScheduledMethod(Tracing tracing) {
		return new TestBeanWithScheduledMethod(tracing);
	}

	@Bean TestBeanWithScheduledMethodToBeIgnored testBeanWithScheduledMethodToBeIgnored(Tracing tracing) {
		return new TestBeanWithScheduledMethodToBeIgnored(tracing);
	}

	@Bean Sampler alwaysSampler() {
		return Sampler.ALWAYS_SAMPLE;
	}

}

class TestBeanWithScheduledMethod {

	private static final Log log = LogFactory.getLog(TestBeanWithScheduledMethod.class);

	private final Tracing tracing;

	Span span;

	AtomicBoolean executed = new AtomicBoolean(false);

	TestBeanWithScheduledMethod(Tracing tracing) {
		this.tracing = tracing;
	}

	@Scheduled(fixedDelay = 1L)
	public void scheduledMethod() {
		log.info("Running the scheduled method");
		this.span = this.tracing.tracer().currentSpan();
		log.info("Stored the span " + this.span + " as current span");
		this.executed.set(true);
	}

	public Span getSpan() {
		return this.span;
	}

	public AtomicBoolean isExecuted() {
		return this.executed;
	}

	public void clear() {
		this.span = null;
		this.executed.set(false);
	}
}

class TestBeanWithScheduledMethodToBeIgnored {

	private final Tracing tracing;

	Span span;
	AtomicBoolean executed = new AtomicBoolean(false);

	TestBeanWithScheduledMethodToBeIgnored(Tracing tracing) {
		this.tracing = tracing;
	}

	@Scheduled(fixedDelay = 1L)
	public void scheduledMethodToIgnore() {
		this.span = this.tracing.tracer().currentSpan();
		this.executed.set(true);
	}

	public Span getSpan() {
		return this.span;
	}

	public AtomicBoolean isExecuted() {
		return this.executed;
	}

	public void clear() {
		this.executed.set(false);
	}
}
