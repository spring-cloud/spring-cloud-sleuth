package org.springframework.cloud.sleuth.instrument.hystrix;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.jayway.awaitility.Awaitility;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {
		SpanPassingForHystrixViaAnnotationsITest.TestConfig.class })
public class SpanPassingForHystrixViaAnnotationsITest {

	@Autowired HystrixCommandInvocationSpanCatcher hystrixCommandInvocationSpanCatcher;
	@Autowired TraceManager traceManager;

	@Test
	public void should_set_span_on_an_hystrix_command_annotated_method() {
		Span span = givenASpanInCurrentThread();

		whenHystrixCommandAnnotatedMethodGetsExecuted();

		thenTraceIdIsPassedFromTheCurrentThreadToTheHystrixOne(span);
	}

	private Span givenASpanInCurrentThread() {
		Span span = traceManager.startSpan("existing").getSpan();
		traceManager.continueSpan(span);
		return span;
	}

	private void whenHystrixCommandAnnotatedMethodGetsExecuted() {
		hystrixCommandInvocationSpanCatcher.invokeLogicWrappedInHystrixCommand();
	}

	private void thenTraceIdIsPassedFromTheCurrentThreadToTheHystrixOne(final Span span) {
		Awaitility.await().until(new Runnable() {
			@Override
			public void run() {
				then(span)
						.hasTraceIdEqualTo(hystrixCommandInvocationSpanCatcher.getTraceId())
						.hasNameNotEqualTo(hystrixCommandInvocationSpanCatcher.getSpanName());
			}
		});
	}

	@After
	public void cleanTrace() {
		TraceContextHolder.removeCurrentTrace();
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
			spanCaughtFromHystrixThread = new AtomicReference<>(TraceContextHolder.getCurrentSpan());
		}

		public String getTraceId() {
			if (spanCaughtFromHystrixThread == null ||
					spanCaughtFromHystrixThread.get() == null ||
					(spanCaughtFromHystrixThread.get() != null &&
							spanCaughtFromHystrixThread.get().getTraceId() == null)) {
				return null;
			}
			return spanCaughtFromHystrixThread.get().getTraceId();
		}

		public String getSpanName() {
			if (spanCaughtFromHystrixThread == null ||
					(spanCaughtFromHystrixThread.get() != null &&
							spanCaughtFromHystrixThread.get().getName() == null)) {
				return null;
			}
			return spanCaughtFromHystrixThread.get().getName();
		}
	}
}
