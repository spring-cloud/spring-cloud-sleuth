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

package org.springframework.cloud.sleuth.instrument.scheduling;

import java.util.concurrent.atomic.AtomicBoolean;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.junit4.SpringRunner;

import static com.jayway.awaitility.Awaitility.await;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ScheduledTestConfiguration.class})
public class TracingOnScheduledTests {

	@Autowired TestBeanWithScheduledMethod beanWithScheduledMethod;
	@Autowired TestBeanWithScheduledMethodToBeIgnored beanWithScheduledMethodToBeIgnored;

	@Test
	public void should_have_span_set_after_scheduled_method_has_been_executed() {
		await().until(spanIsSetOnAScheduledMethod());
	}

	@Test
	public void should_have_a_new_span_set_each_time_a_scheduled_method_has_been_executed() {
		Span firstSpan = this.beanWithScheduledMethod.getSpan();
		await().until(differentSpanHasBeenSetThan(firstSpan));
	}

	@Test
	public void should_not_span_in_the_scheduled_class_that_matches_skip_pattern() throws Exception {
		await().untilAtomic(this.beanWithScheduledMethodToBeIgnored.isExecuted(), Matchers.is(true));
		then(this.beanWithScheduledMethodToBeIgnored.getSpan()).isNull();
	}

	private Runnable spanIsSetOnAScheduledMethod() {
		return new Runnable() {
			@Override
			public void run() {
				Span storedSpan = TracingOnScheduledTests.this.beanWithScheduledMethod.getSpan();
				then(storedSpan).isNotNull();
				then(storedSpan.getTraceId()).isNotNull();
				then(storedSpan).hasATag("class", "TestBeanWithScheduledMethod");
				then(storedSpan).hasATag("method", "scheduledMethod");
			}
		};
	}

	private Runnable differentSpanHasBeenSetThan(final Span spanToCompare) {
		return new Runnable() {
			@Override
			public void run() {
				then(TracingOnScheduledTests.this.beanWithScheduledMethod.getSpan()).isNotEqualTo(spanToCompare);
			}
		};
	}

}

@Configuration
@DefaultTestAutoConfiguration
class ScheduledTestConfiguration {

	@Bean TestBeanWithScheduledMethod testBeanWithScheduledMethod() {
		return new TestBeanWithScheduledMethod();
	}

	@Bean TestBeanWithScheduledMethodToBeIgnored testBeanWithScheduledMethodToBeIgnored() {
		return new TestBeanWithScheduledMethodToBeIgnored();
	}

	@Bean
	AlwaysSampler alwaysSampler() {
		return new AlwaysSampler();
	}

}

class TestBeanWithScheduledMethod {

	Span span;

	@Scheduled(fixedDelay = 1L)
	public void scheduledMethod() {
		this.span = TestSpanContextHolder.getCurrentSpan();
	}

	public Span getSpan() {
		return this.span;
	}
}

class TestBeanWithScheduledMethodToBeIgnored {

	Span span;
	AtomicBoolean executed = new AtomicBoolean(false);

	@Scheduled(fixedDelay = 1L)
	public void scheduledMethodToIgnore() {
		this.span = TestSpanContextHolder.getCurrentSpan();
		this.executed.set(true);
	}

	public Span getSpan() {
		return this.span;
	}

	public AtomicBoolean isExecuted() {
		return this.executed;
	}
}
