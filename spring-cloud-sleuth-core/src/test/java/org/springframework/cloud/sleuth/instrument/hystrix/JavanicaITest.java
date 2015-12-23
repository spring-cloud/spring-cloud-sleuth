package org.springframework.cloud.sleuth.instrument.hystrix;

import static org.assertj.core.api.BDDAssertions.then;

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
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.jayway.awaitility.Awaitility;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {
		JavanicaITest.JavanicaITestConfiguration.class })
public class JavanicaITest {

	@Autowired JavanicaClass javanicaClass;
	@Autowired JavanicaDelegation javanicaDelegation;
	@Autowired TraceManager traceManager;

	@Test
	public void should_set_span_on_an_hystrix_command_annotated_method() {
		final Span span = givenASpanInCurrentThread();

		whenHystrixCommandGetsExecutedViaJavanica();

		thenSpanPutInTheAsyncThreadIsSameAs(span);
	}

	private Span givenASpanInCurrentThread() {
		Span span = this.traceManager.startSpan("existing").getSpan();
		this.traceManager.continueSpan(span);
		return span;
	}

	private void whenHystrixCommandGetsExecutedViaJavanica() {
		this.javanicaDelegation.doSthThatDelegatesToJavanica();
	}

	private void thenSpanPutInTheAsyncThreadIsSameAs(final Span span) {
		Awaitility.await().until(new Runnable() {
			@Override
			public void run() {
				then(span.getTraceId()).isNotNull()
						.isEqualTo(javanicaClass.getTraceId());
				then(span.getName())
						.isNotEqualTo(javanicaClass.getSpanName());
			}
		});
	}

	@After
	public void cleanTrace() {
		TraceContextHolder.removeCurrentTrace();
	}

	@DefaultTestAutoConfiguration
	@EnableHystrix
	@EnableAspectJAutoProxy(proxyTargetClass = true)
	@Configuration
	public static class JavanicaITestConfiguration {

		@Bean
		JavanicaClass javanicaClass() {
			return new JavanicaClass();
		}

		@Bean
		JavanicaDelegation javanicaDelegation() {
			return new JavanicaDelegation(javanicaClass());
		}
	}

	public static class JavanicaDelegation {

		private final JavanicaClass javanicaClass;

		public JavanicaDelegation(JavanicaClass javanicaClass) {
			this.javanicaClass = javanicaClass;
		}

		public void doSthThatDelegatesToJavanica() {
			this.javanicaClass.doSth();
		}
	}

	public static class JavanicaClass {

		AtomicReference<Span> span;

		@HystrixCommand
		public void doSth() {
			this.span = new AtomicReference<>(TraceContextHolder.getCurrentSpan());
		}

		public String getTraceId() {
			if (this.span == null || this.span.get() == null || (this.span.get() != null
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
