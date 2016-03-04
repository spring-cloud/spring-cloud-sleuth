package org.springframework.cloud.sleuth.instrument.hystrix;

import java.util.concurrent.atomic.AtomicReference;

import com.jayway.awaitility.Awaitility;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.strategy.HystrixPlugins;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
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
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {
		HystrixAnnotationsIntegrationTests.TestConfig.class })
@DirtiesContext
public class HystrixAnnotationsIntegrationTests {

	@Autowired
	HystrixCommandInvocationSpanCatcher catcher;
	@Autowired
	Tracer tracer;

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

	static class HystrixCommandInvocationSpanCatcher {

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
