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

package org.springframework.cloud.sleuth.otel.autoconfig;

import java.util.ArrayList;
import java.util.List;

import io.opentelemetry.sdk.trace.Sampler;
import io.opentelemetry.sdk.trace.Samplers;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.trace.Tracer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable tracing via Spring Cloud Sleuth and OpenTelemetry SDK.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(TraceAutoConfiguration.class)
@EnableConfigurationProperties(OtelProperties.class)
public class TraceOtelAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	TracerSdkProvider otelTracerSdkProvider() {
		return TracerSdkProvider.builder().build();
	}

	@Bean
	@ConditionalOnMissingBean
	TraceConfig otelTracerConfig(OtelProperties otelProperties, Sampler sampler) {
		return TraceConfig.getDefault().toBuilder().setMaxLengthOfAttributeValues(otelProperties.getMaxAttrLength())
				.setMaxNumberOfAttributes(otelProperties.getMaxAttrs())
				.setMaxNumberOfAttributesPerEvent(otelProperties.getMaxEventAttrs())
				.setMaxNumberOfAttributesPerLink(otelProperties.getMaxLinkAttrs())
				.setMaxNumberOfEvents(otelProperties.getMaxEvents()).setMaxNumberOfLinks(otelProperties.getMaxLinks())
				.setSampler(sampler).build();
	}

	@Bean
	@ConditionalOnMissingBean
	Tracer otelTracer(TracerSdkProvider tracerSdkProvider, TraceConfig traceConfig, OtelProperties otelProperties,
			ObjectProvider<List<SpanProcessor>> spanProcessors) {
		List<SpanProcessor> processors = spanProcessors.getIfAvailable(ArrayList::new);
		processors.forEach(tracerSdkProvider::addSpanProcessor);
		tracerSdkProvider.updateActiveTraceConfig(traceConfig);
		return tracerSdkProvider.get(otelProperties.getInstrumentationName());
	}

	@Bean
	@ConditionalOnMissingBean
	Sampler otelSampler(OtelProperties otelProperties) {
		return Samplers.traceIdRatioBased(otelProperties.getTraceIdRatioBased());
	}

}