/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig.brave;

import brave.TracingCustomizer;
import brave.handler.SpanHandler;
import brave.sampler.Sampler;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Sleuth 1.x optimized for log-correlation only. Unless "spring-cloud-sleuth-zipkin" was
 * present, the sampler defaulted to {@link Sampler#NEVER_SAMPLE}. This was to ensure the
 * log only nodes never set the sampled flag.
 *
 * <p>
 * "spring-cloud-sleuth-zipkin" obviated the {@link Sampler#NEVER_SAMPLE} default by
 * importing {@link SamplerConfiguration}. Nothing else did, so sampling properties were
 * effectively ignored unless "spring-cloud-sleuth-zipkin" was in use, or something else
 * similarly imported {@link SamplerConfiguration}.
 *
 * <p>
 * During a review of Wavefront integration, it was considered not correct to have other
 * code import {@link SamplerConfiguration}. To avoid that, retain the old behaviour about
 * log only nodes, and also not pin configuration to Zipkin involves a more complex
 * condition.
 *
 * <p>
 * This condition looks for signs of non-default setup which likely requires sampling
 * configuration. It passes on one of the following beans exist:
 *
 * <p>
 * <ul>
 * <li>{@code zipkin2.reporter.Reporter} - what's used by Zipkin or others like
 * Stackdriver</li>
 * <li>{@link SpanHandler} - only accepts sampled data</li>
 * <li>{@link TracingCustomizer} - can configure one of the above</li>
 * </ul>
 *
 * <p>
 * An integrated test that shows {@link Sampler#NEVER_SAMPLE} is default on fail exists in
 * {@code TraceAutoConfigurationTests} intentionally, as users now needn't import
 * {@link SamplerConfiguration} directly.
 */
final class SamplerCondition extends AnyNestedCondition {

	SamplerCondition() {
		super(ConfigurationPhase.REGISTER_BEAN);
	}

	// zipkin2.reporter.Reporter is in the classpath here, but technically it is optional
	@ConditionalOnBean(type = "zipkin2.reporter.Reporter")
	static final class ReporterAvailable {

	}

	@Conditional(SpanHandlerOtherThanCompositePresent.class)
	static final class SpanHandlerAvailable {

	}

	@ConditionalOnBean(TracingCustomizer.class)
	static final class TracingCustomizerAvailable {

	}

	static class SpanHandlerOtherThanCompositePresent extends SpringBootCondition implements ConfigurationCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			String[] spanHandlers = ((ListableBeanFactory) context.getBeanFactory())
					.getBeanNamesForType(SpanHandler.class);
			boolean moreThanSingleHandler = spanHandlers.length > 1;
			if (moreThanSingleHandler) {
				return ConditionOutcome.match("More than one handler present");
			}
			if (spanHandlers.length == 0) {
				return ConditionOutcome.noMatch("No span handler is available");
			}
			// bean name is set in Brave bridge configuration
			boolean singleCompositeSpanHandler = spanHandlers.length == 1
					&& spanHandlers[0].equals("traceCompositeSpanHandler");
			return singleCompositeSpanHandler ? ConditionOutcome.noMatch("Composite handler found")
					: ConditionOutcome.match("Composite handler not found");
		}

		@Override
		public ConfigurationPhase getConfigurationPhase() {
			return ConfigurationPhase.REGISTER_BEAN;
		}

	}

}
