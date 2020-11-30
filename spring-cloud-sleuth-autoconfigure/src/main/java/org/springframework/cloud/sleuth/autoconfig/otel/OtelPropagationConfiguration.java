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

package org.springframework.cloud.sleuth.autoconfig.otel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.DefaultContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.BaggageManager;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.otel.propagation.BaggageTextMapPropagator;
import org.springframework.cloud.sleuth.otel.propagation.CompositeTextMapPropagator;
import org.springframework.cloud.sleuth.otel.propagation.PropagationType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable propagation configuration via Spring Cloud Sleuth and
 * OpenTelemetry SDK.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ SleuthPropagationProperties.class, OtelPropagationProperties.class })
class OtelPropagationConfiguration {

	@Bean
	@ConditionalOnMissingBean
	ContextPropagators otelContextPropagators(ObjectProvider<List<TextMapPropagator>> propagators) {
		List<TextMapPropagator> mapPropagators = propagators.getIfAvailable(ArrayList::new);
		if (mapPropagators.isEmpty()) {
			return noOpContextPropagator();
		}
		DefaultContextPropagators.Builder builder = DefaultContextPropagators.builder();
		mapPropagators.forEach(builder::addTextMapPropagator);
		return builder.build();
	}

	private ContextPropagators noOpContextPropagator() {
		return () -> new TextMapPropagator() {
			@Override
			public List<String> fields() {
				return Collections.emptyList();
			}

			@Override
			public <C> void inject(Context context, @Nullable C carrier, Setter<C> setter) {

			}

			@Override
			public <C> Context extract(Context context, @Nullable C carrier, Getter<C> getter) {
				return context;
			}
		};
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(name = "spring.sleuth.otel.propagation.composite-text-map-propagator.enabled",
			matchIfMissing = true)
	static class PropagatorsConfiguration {

		@Bean
		TextMapPropagator compositeTextMapPropagator(BeanFactory beanFactory, SleuthPropagationProperties properties) {
			return new CompositeTextMapPropagator(beanFactory, properties.getType());
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Conditional(B3PresentOrPropertyEnabledCondition.class)
	static class BaggagePropagatorConfiguration {

		@Bean
		TextMapPropagator baggageTextMapPropagator(SleuthBaggageProperties properties, BaggageManager baggageManager) {
			return new BaggageTextMapPropagator(properties.getRemoteFields(), baggageManager);
		}

	}

	static class B3PresentOrPropertyEnabledCondition implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			String type = context.getEnvironment()
					.getProperty("spring.sleuth.propagation.type", PropagationType.B3.toString()).toLowerCase();
			boolean sleuthBaggageEnabled = context.getEnvironment()
					.getProperty("spring.sleuth.otel.propagation.sleuth-baggage.enabled", Boolean.class, false);
			return type.contains(PropagationType.B3.toString().toLowerCase()) || sleuthBaggageEnabled;
		}

	}

}
