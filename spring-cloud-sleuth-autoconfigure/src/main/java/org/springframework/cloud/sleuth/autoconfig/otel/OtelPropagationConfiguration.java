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
import java.util.List;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.DefaultContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.api.BaggageManager;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.otel.propagation.BaggageTextMapPropagator;
import org.springframework.cloud.sleuth.otel.propagation.CompositeTextMapPropagator;
import org.springframework.cloud.sleuth.otel.propagation.SleuthPropagationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
			return OpenTelemetry.getGlobalPropagators();
		}
		DefaultContextPropagators.Builder builder = DefaultContextPropagators.builder();
		mapPropagators.forEach(builder::addTextMapPropagator);
		OpenTelemetry.setGlobalPropagators(builder.build());
		return OpenTelemetry.getGlobalPropagators();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(name = "spring.sleuth.otel.propagation.composite-text-map-propagator.enabled",
			matchIfMissing = true)
	static class PropagatorsConfiguration {

		@Bean
		TextMapPropagator compositeTextMapPropagator(BeanFactory beanFactory, SleuthPropagationProperties properties) {
			return new CompositeTextMapPropagator(beanFactory, properties);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(name = "spring.sleuth.otel.propagation.sleuth-baggage.enabled", matchIfMissing = true)
	static class BaggagePropagatorConfiguration {

		@Bean
		TextMapPropagator baggageTextMapPropagator(SleuthBaggageProperties properties, BaggageManager baggageManager) {
			return new BaggageTextMapPropagator(properties, baggageManager);
		}

	}

}
