package org.springframework.cloud.sleuth.instrument.hystrix;

import com.jayway.awaitility.Awaitility;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.trace.SpanContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {
		SpanPassingForHystrixViaAnnotationsIntegrationTests.TestConfig.class })
public class SpanPassingForHystrixViaAnnotationsIntegrationTests {

	@Autowired HystrixCommandInvocationSpanCatcher hystrixCommandInvocationSpanCatcher;
	@Autowired
	Tracer tracer;

	@Test
	public void should_set_span_on_an_hystrix_command_annotated_method() {
		Span span = givenASpanInCurrentThread();

		whenHystrixCommandAnnotatedMethodGetsExecuted();

		thenTraceIdIsPassedFromTheCurrentThreadToTheHystrixOne(span);
	}

	private Span givenASpanInCurrentThread() {
		Span span = this.tracer.startTrace("existing");
		this.tracer.continueSpan(span);
		return span;
	}

	private void whenHystrixCommandAnnotatedMethodGetsExecuted() {
		this.hystrixCommandInvocationSpanCatcher.invokeLogicWrappedInHystrixCommand();
	}

	private void thenTraceIdIsPassedFromTheCurrentThreadToTheHystrixOne(final Span span) {
		Awaitility.await().until(new Runnable() {
			@Override
			public void run() {
				then(span)
						.hasTraceIdEqualTo(SpanPassingForHystrixViaAnnotationsIntegrationTests.this.hystrixCommandInvocationSpanCatcher.getTraceId())
						.hasNameNotEqualTo(SpanPassingForHystrixViaAnnotationsIntegrationTests.this.hystrixCommandInvocationSpanCatcher.getSpanName());
			}
		});
	}

	@After
	public void cleanTrace() {
		SpanContextHolder.removeCurrentSpan();
	}

	@DefaultTestAutoConfiguration
	@EnableHystrix
	@Configuration
	static class TestConfig {

		@Bean HystrixCommandInvocationSpanCatcher spanCatcher() {
			return new HystrixCommandInvocationSpanCatcher();
		}

	}

	static class HystrixCommandInvocationSpanCatcher {

		AtomicReference<Span> spanCaughtFromHystrixThread;

		@HystrixCommand
		public void invokeLogicWrappedInHystrixCommand() {
			this.spanCaughtFromHystrixThread = new AtomicReference<>(SpanContextHolder.getCurrentSpan());
		}

		public Long getTraceId() {
			if (this.spanCaughtFromHystrixThread == null ||
					this.spanCaughtFromHystrixThread.get() == null) {
				return null;
			}
			return this.spanCaughtFromHystrixThread.get().getTraceId();
		}

		public String getSpanName() {
			if (this.spanCaughtFromHystrixThread == null ||
					(this.spanCaughtFromHystrixThread.get() != null &&
							this.spanCaughtFromHystrixThread.get().getName() == null)) {
				return null;
			}
			return this.spanCaughtFromHystrixThread.get().getName();
		}
	}
}
