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

package org.springframework.cloud.sleuth.instrument.hystrix;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import com.jayway.awaitility.Awaitility;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.strategy.HystrixPlugins;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
		HystrixAnnotationsIntegrationTests.TestConfig.class })
@DirtiesContext
public class HystrixAnnotationsIntegrationTests {

	@Autowired HystrixCommandInvocationSpanCatcher catcher;
	@Autowired Tracer tracer;

	@BeforeClass
	@AfterClass
	public static void reset() {
		HystrixPlugins.reset();
	}

	@After
	public void cleanTrace() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_continue_current_span_when_executed_a_hystrix_command_annotated_method() {
		Span span = givenASpanInCurrentThread();

		whenHystrixCommandAnnotatedMethodGetsExecuted();

		thenSpanInHystrixThreadIsContinued(span);
	}

	@Test
	public void should_create_new_span_with_thread_name_when_executed_a_hystrix_command_annotated_method() {
		whenHystrixCommandAnnotatedMethodGetsExecuted();

		thenSpanInHystrixThreadIsCreated();
	}

	private Span givenASpanInCurrentThread() {
		return this.tracer.createSpan("http:existing");
	}

	private void whenHystrixCommandAnnotatedMethodGetsExecuted() {
		this.catcher.invokeLogicWrappedInHystrixCommand();
	}

	private void thenSpanInHystrixThreadIsContinued(final Span span) {
		then(span).isNotNull();
		Awaitility.await().until(new Runnable() {
			@Override
			public void run() {
				then(HystrixAnnotationsIntegrationTests.this.catcher).isNotNull();
				then(span)
						.hasTraceIdEqualTo(HystrixAnnotationsIntegrationTests.this.catcher
								.getTraceId())
						.hasNameEqualTo(HystrixAnnotationsIntegrationTests.this.catcher
								.getSpanName());
			}
		});
	}

	private void thenSpanInHystrixThreadIsCreated() {
		Awaitility.await().until(new Runnable() {
			@Override
			public void run() {
				then(HystrixAnnotationsIntegrationTests.this.catcher.getSpan())
						.nameStartsWith("hystrix")
						.isALocalComponentSpan();
			}
		});
	}

	@DefaultTestAutoConfiguration
	@EnableHystrix
	@Configuration
	static class TestConfig {

		@Bean
		HystrixCommandInvocationSpanCatcher spanCatcher() {
			return new HystrixCommandInvocationSpanCatcher();
		}

		@Bean
		Sampler sampler() {
			return new AlwaysSampler();
		}

	}

	public static class HystrixCommandInvocationSpanCatcher {

		AtomicReference<Span> spanCaughtFromHystrixThread;

		@HystrixCommand
		public void invokeLogicWrappedInHystrixCommand() {
			this.spanCaughtFromHystrixThread = new AtomicReference<>(
					TestSpanContextHolder.getCurrentSpan());
		}

		public Long getTraceId() {
			if (this.spanCaughtFromHystrixThread == null
					|| this.spanCaughtFromHystrixThread.get() == null) {
				return null;
			}
			return this.spanCaughtFromHystrixThread.get().getTraceId();
		}

		public String getSpanName() {
			if (this.spanCaughtFromHystrixThread == null
					|| (this.spanCaughtFromHystrixThread.get() != null
					&& this.spanCaughtFromHystrixThread.get()
					.getName() == null)) {
				return null;
			}
			return this.spanCaughtFromHystrixThread.get().getName();
		}

		public Span getSpan() {
			return this.spanCaughtFromHystrixThread.get();
		}
	}
}
