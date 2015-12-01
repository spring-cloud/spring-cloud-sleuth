package org.springframework.cloud.sleuth.instrument.scheduling;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {ScheduledTestConfiguration.class})
public class TracingOnScheduledITest {

	@Autowired TestBeanWithScheduledMethod beanWithScheduledMethod;

	@Test
	public void should_have_correlationId_set_after_scheduled_method_has_been_called() {
		await().until(spanIsSetOnAScheduledMethod());
	}

	private Runnable spanIsSetOnAScheduledMethod() {
		return new Runnable() {
			@Override
			public void run() {
				Span storedSpan = beanWithScheduledMethod.getSpan();
				assertThat(storedSpan).isNotNull();
				assertThat(storedSpan.getTraceId()).isNotNull();
			}
		};
	}

}
