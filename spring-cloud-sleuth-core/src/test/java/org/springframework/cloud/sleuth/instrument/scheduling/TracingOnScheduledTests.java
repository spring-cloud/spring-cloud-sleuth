package org.springframework.cloud.sleuth.instrument.scheduling;

import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.BDDAssertions.then;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.trace.SpanContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {ScheduledTestConfiguration.class})
public class TracingOnScheduledTests {

	@Autowired TestBeanWithScheduledMethod beanWithScheduledMethod;

	@Test
	public void should_have_span_set_after_scheduled_method_has_been_executed() {
		await().until(spanIsSetOnAScheduledMethod());
	}

	@Test
	public void should_have_a_new_span_set_each_time_a_scheduled_method_has_been_executed() {
		Span firstSpan = this.beanWithScheduledMethod.getSpan();
		await().until(differentSpanHasBeenSetThan(firstSpan));
	}

	private Runnable spanIsSetOnAScheduledMethod() {
		return new Runnable() {
			@Override
			public void run() {
				Span storedSpan = TracingOnScheduledTests.this.beanWithScheduledMethod.getSpan();
				then(storedSpan).isNotNull();
				then(storedSpan.getTraceId()).isNotNull();
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

}

class TestBeanWithScheduledMethod {

	Span span;

	@Scheduled(fixedDelay = 1L)
	public void scheduledMethod() {
		this.span = SpanContextHolder.getCurrentSpan();
	}

	public Span getSpan() {
		return this.span;
	}
}
