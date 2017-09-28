/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.jms;

import org.assertj.core.api.BDDAssertions;
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
import org.springframework.jms.annotation.JmsListener;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Stefan Zeller
 * @since 1.2.5
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { JmsListenerTestConfiguration.class })
public class TracingOnJmsListenerTests {
	@Autowired
	TestBeanWithJmsListenerMethod beanWithJmsListenerMethod;
	@Autowired
	TestBeanWithJmsListenerMethodToBeIgnored beanWithJmsListenerMethodToBeIgnored;

	@Test
	public void should_have_span_set_after_jmslistener_method_has_been_executed() {
		beanWithJmsListenerMethod.jmsMethod();

		Span storedSpan = TracingOnJmsListenerTests.this.beanWithJmsListenerMethod.getSpan();
		then(storedSpan).isNotNull();
		BDDAssertions.then(storedSpan.getTraceId()).isNotNull();
		then(storedSpan).hasATag("class", "TestBeanWithJmsListenerMethod");
		then(storedSpan).hasATag("method", "jmsMethod");
	}

	@Test
	public void should_have_a_new_span_set_each_time_a_scheduled_method_has_been_executed() {
		beanWithJmsListenerMethod.jmsMethod();
		Span firstSpan = TracingOnJmsListenerTests.this.beanWithJmsListenerMethod.getSpan();

		beanWithJmsListenerMethod.jmsMethod();
		then(TracingOnJmsListenerTests.this.beanWithJmsListenerMethod.getSpan())
				.isNotEqualTo(firstSpan);
	}

	@Test
	public void should_not_span_in_the_scheduled_class_that_matches_skip_pattern() {
		beanWithJmsListenerMethodToBeIgnored.jmsMethodToIgnore();

		Span storedSpan = TracingOnJmsListenerTests.this.beanWithJmsListenerMethodToBeIgnored.getSpan();
		then(storedSpan).isNull();
	}

}

@Configuration
@DefaultTestAutoConfiguration
class JmsListenerTestConfiguration {

	@Bean
	TestBeanWithJmsListenerMethod testBeanWithJmsListenerMethod() {
		return new TestBeanWithJmsListenerMethod();
	}

	@Bean
	TestBeanWithJmsListenerMethodToBeIgnored testBeanWithJmsListenerMethodToBeIgnored() {
		return new TestBeanWithJmsListenerMethodToBeIgnored();
	}

	@Bean
	AlwaysSampler alwaysSampler() {
		return new AlwaysSampler();
	}

}

class TestBeanWithJmsListenerMethod {

	Span span;

	@org.springframework.jms.annotation.JmsListener(destination = "destination")
	public void jmsMethod() {
		this.span = TestSpanContextHolder.getCurrentSpan();
	}

	public Span getSpan() {
		return this.span;
	}
}

class TestBeanWithJmsListenerMethodToBeIgnored {

	Span span;
	AtomicBoolean executed = new AtomicBoolean(false);

	@JmsListener(destination = "destination")
	public void jmsMethodToIgnore() {
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
