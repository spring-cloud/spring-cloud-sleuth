package org.springframework.cloud.sleuth.stream;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.log.SpanLogger;
import org.springframework.cloud.sleuth.metric.TraceMetricsAutoConfiguration;
import org.springframework.cloud.stream.config.ChannelBindingAutoConfiguration;
import org.springframework.cloud.stream.test.binder.TestSupportBinderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import static org.assertj.core.api.Assertions.assertThat;

public class SleuthStreamAutoConfigurationTest {

	private AnnotationConfigApplicationContext ctx;
	private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final String TEST_SCHEDULED = "2016-01-01 12:00:00";
	private static final String TEST_COMPLETION = "2016-01-01 12:00:02";
	private static Date LAST_SCHEDULED_DATE;
	private static Date LAST_EXECUTED_DATE;
	private static Date LAST_COMPLETED_DATE;

	@BeforeClass
	public static void setupTests() throws ParseException {
		LAST_SCHEDULED_DATE = dateFormat.parse(TEST_SCHEDULED);
		LAST_EXECUTED_DATE = new Date(LAST_SCHEDULED_DATE.getTime());
		LAST_COMPLETED_DATE = dateFormat.parse(TEST_COMPLETION);
	}

	@After
	public void cleanup() {
		if (ctx != null) {
			ctx.close();
		}
	}

	@Test
	public void shouldUseDefaultPollerConfiguration() {
		ctx = createContext();
		ctx.refresh();

		PollerMetadata poller = ctx.getBean(StreamSpanReporter.POLLER,
				PollerMetadata.class);
		assertThat(poller).isNotNull();
		assertPollerConfigurationUsingConfigurationProperties(poller);
	}

	@Test
	public void shouldUseCustomPollerConfiguration() {
		ctx = createContext();
		EnvironmentTestUtils.addEnvironment(ctx,
				"spring.sleuth.stream.poller.fixed-delay=5000",
				"spring.sleuth.stream.poller.max-messages-per-poll=100");
		ctx.refresh();

		PollerMetadata poller = ctx.getBean(StreamSpanReporter.POLLER,
				PollerMetadata.class);
		assertThat(poller).isNotNull();
		assertPollerConfigurationUsingConfigurationProperties(poller);
	}

	@Test
	public void shouldUseCustomPollerBean() {
		ctx = createContext(CustomPollerConfiguration.class);
		ctx.refresh();

		PollerMetadata poller = ctx.getBean(StreamSpanReporter.POLLER,
				PollerMetadata.class);
		assertThat(poller).isNotNull();
		assertPollerConfiguration(poller, 500L, 5000L);
	}

	private void assertPollerConfigurationUsingConfigurationProperties(
			PollerMetadata poller) {
		SleuthStreamProperties sleuth = ctx.getBean(SleuthStreamProperties.class);
		assertPollerConfiguration(poller, sleuth.getPoller().getMaxMessagesPerPoll(),
				sleuth.getPoller().getFixedDelay());
	}

	private void assertPollerConfiguration(PollerMetadata poller,
			long expectedMaxMessages, long expectedFixedDelay) {
		assertThat(poller.getMaxMessagesPerPoll()).isEqualTo(expectedMaxMessages);
		Trigger trigger = poller.getTrigger();
		assertThat(trigger).isInstanceOf(PeriodicTrigger.class);
		TriggerContext triggerContext = new SimpleTriggerContext(LAST_SCHEDULED_DATE,
				LAST_EXECUTED_DATE, LAST_COMPLETED_DATE);
		Date nextExecution = trigger.nextExecutionTime(triggerContext);
		assertThat(nextExecution.getTime())
				.isEqualTo(LAST_COMPLETED_DATE.getTime() + expectedFixedDelay);
	}

	public AnnotationConfigApplicationContext createContext(Class<?>... classes) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		if (classes != null && classes.length > 0) {
			context.register(classes);
		}
		context.register(BaseConfiguration.class);
		return context;
	}

	@Configuration
	@Import({ SleuthStreamAutoConfiguration.class, TraceMetricsAutoConfiguration.class,
			TestSupportBinderAutoConfiguration.class,
			ChannelBindingAutoConfiguration.class, TraceAutoConfiguration.class,
			PropertyPlaceholderAutoConfiguration.class, UtilAutoConfiguration.class })
	public static class BaseConfiguration {
		@Bean
		SpanLogger spanLogger() {
			return new NoOpSpanLogger();
		}
	}

	@Configuration
	public static class CustomPollerConfiguration {

		@Bean(name = StreamSpanReporter.POLLER)
		PollerMetadata customPoller() {
			PollerMetadata poller = new PollerMetadata();
			poller.setMaxMessagesPerPoll(500);
			poller.setTrigger(new PeriodicTrigger(5000L));
			return poller;
		}
	}
}
