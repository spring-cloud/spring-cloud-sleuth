
package org.springframework.cloud.sleuth.instrument.web;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.jayway.awaitility.Awaitility;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {
		TraceAsyncITest.TraceAsyncITestConfiguration.class })
public class TraceAsyncITest {

	@Autowired AsyncClass asyncClass;
	@Autowired AsyncDelegation asyncDelegation;
	@Autowired TraceManager traceManager;

	@Test
	public void should_set_span_on_an_async_annotated_method() {
		final Span span = givenASpanInCurrentThread();

		whenAsyncProcessingTakesPlace();

		thenSpanPutInTheAsyncThreadIsSameAs(span);
	}

	private Span givenASpanInCurrentThread() {
		Span span = this.traceManager.startSpan("existing").getSpan();
		this.traceManager.continueSpan(span);
		return span;
	}

	private void whenAsyncProcessingTakesPlace() {
		this.asyncDelegation.doSthThatDelegatesToAsync();
	}

	private void thenSpanPutInTheAsyncThreadIsSameAs(final Span span) {
		Awaitility.await().until(new Runnable() {
			@Override
			public void run() {
				then(span)
						.hasTraceId(asyncClass.getTraceId())
						.hasNameNotEqualTo(asyncClass.getSpanName());
			}
		});
	}

	@After
	public void cleanTrace() {
		TraceContextHolder.removeCurrentTrace();
	}

	@DefaultTestAutoConfiguration
	@EnableAsync
	@EnableAspectJAutoProxy(proxyTargetClass = true)
	@Configuration
	public static class TraceAsyncITestConfiguration {

		@Bean
		AsyncClass asyncClass() {
			return new AsyncClass();
		}

		@Bean
		AsyncDelegation asyncDelegation() {
			return new AsyncDelegation(asyncClass());
		}
	}

	public static class AsyncDelegation {

		private final AsyncClass asyncClass;

		public AsyncDelegation(AsyncClass asyncClass) {
			this.asyncClass = asyncClass;
		}

		public void doSthThatDelegatesToAsync() {
			this.asyncClass.doSth();
		}
	}

	public static class AsyncClass {

		AtomicReference<Span> span;

		@Async
		public void doSth() {
			this.span = new AtomicReference<>(TraceContextHolder.getCurrentSpan());
		}

		public String getTraceId() {
			if (this.span == null || (this.span.get() != null
					&& this.span.get().getTraceId() == null)) {
				return null;
			}
			return this.span.get().getTraceId();
		}

		public String getSpanName() {
			if (this.span == null
					|| (this.span.get() != null && this.span.get().getName() == null)) {
				return null;
			}
			return this.span.get().getName();
		}
	}
}
