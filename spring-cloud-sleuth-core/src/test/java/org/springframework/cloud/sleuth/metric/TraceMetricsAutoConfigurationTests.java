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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.cloud.sleuth.metric.TraceMetricsAutoConfiguration.PickMetricIfMetricsIsMissing;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.metric.TraceMetricsAutoConfiguration.PickMetricIfMetricsIsMissing.DEPRECATED_SPRING_SLEUTH_METRICS_ENABLED;
import static org.springframework.cloud.sleuth.metric.TraceMetricsAutoConfiguration.PickMetricIfMetricsIsMissing.SPRING_SLEUTH_METRIC_ENABLED;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceMetricsAutoConfigurationTests {

	@Mock ConditionContext conditionContext;
	@Mock AnnotatedTypeMetadata annotatedTypeMetadata;
	MockEnvironment mockEnvironment = new MockEnvironment();

	PickMetricIfMetricsIsMissing condition = new PickMetricIfMetricsIsMissing();

	@Before
	public void setup() {
		BDDMockito.given(this.conditionContext.getEnvironment()).willReturn(this.mockEnvironment);
	}

	@Test
	public void should_turn_on_the_feature_when_no_explicit_one_was_provided() {
		ConditionOutcome outcome = this.condition.getMatchOutcome(this.conditionContext, this.annotatedTypeMetadata);

		then(outcome.isMatch()).isTrue();
	}

	@Test
	public void should_turn_on_the_feature_when_deprecated_property_is_enabled() {
		this.mockEnvironment.setProperty(DEPRECATED_SPRING_SLEUTH_METRICS_ENABLED, "true");

		ConditionOutcome outcome = this.condition.getMatchOutcome(this.conditionContext, this.annotatedTypeMetadata);

		then(outcome.isMatch()).isTrue();
	}

	@Test
	public void should_turn_off_the_feature_when_deprecated_property_is_disabled() {
		this.mockEnvironment.setProperty(DEPRECATED_SPRING_SLEUTH_METRICS_ENABLED, "false");

		ConditionOutcome outcome = this.condition.getMatchOutcome(this.conditionContext, this.annotatedTypeMetadata);

		then(outcome.isMatch()).isFalse();
	}

	@Test
	public void should_turn_on_the_feature_when_property_is_enabled() {
		this.mockEnvironment.setProperty(SPRING_SLEUTH_METRIC_ENABLED, "true");

		ConditionOutcome outcome = this.condition.getMatchOutcome(this.conditionContext, this.annotatedTypeMetadata);

		then(outcome.isMatch()).isTrue();
	}

	@Test
	public void should_turn_off_the_feature_when_property_is_disabled() {
		this.mockEnvironment.setProperty(SPRING_SLEUTH_METRIC_ENABLED, "false");

		ConditionOutcome outcome = this.condition.getMatchOutcome(this.conditionContext, this.annotatedTypeMetadata);

		then(outcome.isMatch()).isFalse();
	}

	@Test
	public void should_turn_on_the_feature_when_new_property_is_disabled_and_old_is_enabled() {
		this.mockEnvironment.setProperty(DEPRECATED_SPRING_SLEUTH_METRICS_ENABLED, "true");
		this.mockEnvironment.setProperty(SPRING_SLEUTH_METRIC_ENABLED, "false");

		ConditionOutcome outcome = this.condition.getMatchOutcome(this.conditionContext, this.annotatedTypeMetadata);

		then(outcome.isMatch()).isTrue();
	}

	@Test
	public void should_turn_off_the_feature_when_new_property_is_disabled_and_old_is_enabled() {
		this.mockEnvironment.setProperty(DEPRECATED_SPRING_SLEUTH_METRICS_ENABLED, "false");
		this.mockEnvironment.setProperty(SPRING_SLEUTH_METRIC_ENABLED, "true");

		ConditionOutcome outcome = this.condition.getMatchOutcome(this.conditionContext, this.annotatedTypeMetadata);

		then(outcome.isMatch()).isFalse();
	}
}