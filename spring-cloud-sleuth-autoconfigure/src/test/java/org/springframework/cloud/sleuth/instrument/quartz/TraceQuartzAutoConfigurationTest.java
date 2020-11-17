/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.quartz;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.quartz.ListenerManager;
import org.quartz.Scheduler;

import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * @author Branden Cash
 */
public class TraceQuartzAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.sleuth.noop.enabled=true").withConfiguration(
					AutoConfigurations.of(SchedulerConfig.class, TracingConfig.class, EnableAutoConfig.class));

	@Test
	public void should_create_job_listener_bean_when_all_conditions_are_met() {
		// when
		this.contextRunner.run(context -> {
			// expect
			Assertions.assertThat(context).hasSingleBean(TracingJobListener.class);
		});
	}

	@Test
	public void should_add_listener_to_trigger_listeners_when_conditions_are_met() {
		// when
		this.contextRunner.run(context -> {
			// expect
			Mockito.verify(context.getBean(Scheduler.class).getListenerManager())
					.addTriggerListener(context.getBean(TracingJobListener.class));
		});
	}

	@Test
	public void should_add_listener_job_listeners_when_conditions_are_met() {
		// when
		this.contextRunner.run(context -> {
			// expect
			Mockito.verify(context.getBean(Scheduler.class).getListenerManager())
					.addJobListener(context.getBean(TracingJobListener.class));
		});
	}

	@Test
	public void should_not_create_listener_bean_when_tracing_bean_is_not_present() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations
				// given
				.of(SchedulerConfig.class, TraceQuartzAutoConfiguration.class))

				// when
				.run(context -> {
					// expect
					Assertions.assertThat(context).doesNotHaveBean(TracingJobListener.class);
				});
	}

	@Test
	public void should_not_create_listener_when_scheduler_bean_is_not_present() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations
				// given
				.of(TracingConfig.class, TraceQuartzAutoConfiguration.class))

				// when
				.run(context -> {
					// expect
					Assertions.assertThat(context).doesNotHaveBean(TracingJobListener.class);
				});
	}

	@Test
	public void should_not_create_listener_when_sleuth_is_disabled() {
		// when
		this.contextRunner.withPropertyValues("spring.sleuth.quartz.enabled=false")
				// expect
				.run(context -> {
					Assertions.assertThat(context).doesNotHaveBean(TracingJobListener.class);
				});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class })
	public static class EnableAutoConfig {

	}

	@Configuration(proxyBeanMethods = false)
	@AutoConfigureBefore(TraceQuartzAutoConfiguration.class)
	public static class SchedulerConfig {

		@Bean
		@Primary
		public Scheduler scheduler() throws Exception {
			Scheduler scheduler = Mockito.mock(Scheduler.class);
			Mockito.when(scheduler.getListenerManager()).thenReturn(Mockito.mock(ListenerManager.class));
			return scheduler;
		}

	}

	@Configuration(proxyBeanMethods = false)
	@AutoConfigureBefore(TraceQuartzAutoConfiguration.class)
	@ConditionalOnProperty("spring.sleuth.noop.enabled")
	public static class TracingConfig {

		@Bean
		public Tracer testTracer() {
			return Mockito.mock(Tracer.class);
		}

	}

}
