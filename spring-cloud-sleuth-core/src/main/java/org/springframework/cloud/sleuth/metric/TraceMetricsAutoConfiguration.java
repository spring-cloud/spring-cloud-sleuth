/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.metric;

import java.lang.invoke.MethodHandles;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * enables Sleuth related metrics reporting
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Configuration
@Conditional(TraceMetricsAutoConfiguration.PickMetricIfMetricsIsMissing.class)
@EnableConfigurationProperties
public class TraceMetricsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public SleuthMetricProperties sleuthMetricProperties() {
		return new SleuthMetricProperties();
	}

	@Configuration
	@ConditionalOnClass(CounterService.class)
	@ConditionalOnMissingBean(SpanMetricReporter.class)
	protected static class CounterServiceSpanReporterConfig {
		@Bean
		@ConditionalOnBean(CounterService.class)
		public SpanMetricReporter spanReporterCounterService(CounterService counterService,
				SleuthMetricProperties sleuthMetricProperties) {
			return new CounterServiceBasedSpanMetricReporter(sleuthMetricProperties.getSpan().getAcceptedName(),
					sleuthMetricProperties.getSpan().getDroppedName(), counterService);
		}

		@Bean
		@ConditionalOnMissingBean(CounterService.class)
		public SpanMetricReporter noOpSpanReporterCounterService() {
			return new NoOpSpanMetricReporter();
		}
	}

	@Bean
	@ConditionalOnMissingClass("org.springframework.boot.actuate.metrics.CounterService")
	@ConditionalOnMissingBean(SpanMetricReporter.class)
	public SpanMetricReporter noOpSpanReporterCounterService() {
		return new NoOpSpanMetricReporter();
	}

	static class PickMetricIfMetricsIsMissing extends SpringBootCondition {

		private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

		static final String DEPRECATED_SPRING_SLEUTH_METRICS_ENABLED = "spring.sleuth.metrics.enabled";
		static final String SPRING_SLEUTH_METRIC_ENABLED = "spring.sleuth.metric.enabled";

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			Boolean oldValue = context.getEnvironment().getProperty(DEPRECATED_SPRING_SLEUTH_METRICS_ENABLED, Boolean.class);
			Boolean newValue = context.getEnvironment().getProperty(SPRING_SLEUTH_METRIC_ENABLED, Boolean.class);
			if (oldValue != null) {
				log.warn("You're using an old version of the metrics property. Instead of using [" +
						DEPRECATED_SPRING_SLEUTH_METRICS_ENABLED + "] please use [" + SPRING_SLEUTH_METRIC_ENABLED + "]");
				return matchCondition(oldValue, DEPRECATED_SPRING_SLEUTH_METRICS_ENABLED);
			}
			if (newValue != null) {
				return matchCondition(newValue, SPRING_SLEUTH_METRIC_ENABLED);
			}
			return ConditionOutcome.match("No property was passed - assuming that metrics are enabled.");
		}

		private ConditionOutcome matchCondition(Boolean value, String property) {
			if (Boolean.TRUE.equals(value)) {
				return ConditionOutcome.match();
			}
			return ConditionOutcome.noMatch("Property [" + property + "] is set to false.");
		}
	}
}
