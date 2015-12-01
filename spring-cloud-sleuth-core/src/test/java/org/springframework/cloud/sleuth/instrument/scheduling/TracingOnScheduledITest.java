package org.springframework.cloud.sleuth.instrument.scheduling;

import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.BDDAssertions.then;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {ScheduledTestConfiguration.class})
public class TracingOnScheduledITest {

	@Autowired TestBeanWithScheduledMethod beanWithScheduledMethod;

	@Test
	public void should_have_span_set_after_scheduled_method_has_been_executed() {
		await().until(spanIsSetOnAScheduledMethod());
	}

	@Test
	public void should_have_a_new_span_set_each_time_a_scheduled_method_has_been_executed() {
		Span firstSpan = beanWithScheduledMethod.getSpan();
		await().until(differentSpanHasBeenSetThan(firstSpan));
	}

	private Runnable spanIsSetOnAScheduledMethod() {
		return new Runnable() {
			@Override
			public void run() {
				Span storedSpan = beanWithScheduledMethod.getSpan();
				then(storedSpan).isNotNull();
				then(storedSpan.getTraceId()).isNotNull();
			}
		};
	}

	private Runnable differentSpanHasBeenSetThan(final Span spanToCompare) {
		return new Runnable() {
			@Override
			public void run() {
				then(beanWithScheduledMethod.getSpan()).isNotEqualTo(spanToCompare);
			}
		};
	}

}
