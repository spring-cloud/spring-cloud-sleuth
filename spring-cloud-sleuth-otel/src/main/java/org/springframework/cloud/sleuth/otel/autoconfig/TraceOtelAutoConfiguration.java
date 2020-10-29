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
import java.util.stream.Collectors;

import io.opentelemetry.baggage.BaggageManager;
import io.opentelemetry.baggage.spi.BaggageManagerFactory;
import io.opentelemetry.sdk.baggage.spi.BaggageManagerFactorySdk;
import io.opentelemetry.sdk.trace.Sampler;
import io.opentelemetry.sdk.trace.Samplers;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.spi.TracerProviderFactorySdk;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.TracerProvider;
import io.opentelemetry.trace.spi.TracerProviderFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.otel.bridge.TraceOtelBridgeConfiguation;
import org.springframework.cloud.sleuth.otel.exporter.SpanExporterCustomizer;
import org.springframework.cloud.sleuth.otel.log.TraceOtelLogConfiguration;
import org.springframework.cloud.sleuth.otel.propagation.TraceOtelPropagationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable tracing via Spring Cloud Sleuth and OpenTelemetry SDK.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(TraceAutoConfiguration.class)
@EnableConfigurationProperties(OtelProperties.class)
@Import({ TraceAutoConfiguration.PropertiesConfiguration.class, TraceOtelBridgeConfiguation.class,
		TraceOtelPropagationConfiguration.class, TraceOtelLogConfiguration.class })
@OnOtelEnabled
public class TraceOtelAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	TracerProviderFactory otelTracerProviderFactory() {
		return new TracerProviderFactorySdk();
	}

	@Bean
	@ConditionalOnMissingBean
	TracerProvider otelTracerProvider(TracerProviderFactory tracerProviderFactory) {
		return tracerProviderFactory.create();
	}

	@Bean
	@ConditionalOnMissingBean
	BaggageManagerFactory otelBaggageManagerFactory() {
		return new BaggageManagerFactorySdk();
	}

	@Bean
	@ConditionalOnMissingBean
	BaggageManager otelBaggageManager(BaggageManagerFactory baggageManagerFactory) {
		return baggageManagerFactory.create();
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
	Tracer otelTracer(TracerProvider tracerProvider, ObjectProvider<TracerSdkProvider> tracerSdkObjectProvider,
			TraceConfig traceConfig, OtelProperties otelProperties, ObjectProvider<List<SpanProcessor>> spanProcessors,
			ObjectProvider<List<SpanExporter>> spanExporters, SpanExporterCustomizer spanExporterCustomizer) {
		tracerSdkObjectProvider.ifAvailable(tracerSdkProvider -> {
			List<SpanProcessor> processors = spanProcessors.getIfAvailable(ArrayList::new);
			processors.addAll(spanExporters.getIfAvailable(ArrayList::new).stream()
					.map(e -> SimpleSpanProcessor.newBuilder(spanExporterCustomizer.customize(e)).build())
					.collect(Collectors.toList()));
			processors.forEach(tracerSdkProvider::addSpanProcessor);
			tracerSdkProvider.updateActiveTraceConfig(traceConfig);
		});
		return tracerProvider.get(otelProperties.getInstrumentationName());
	}

	@Bean
	@ConditionalOnMissingBean
	Sampler otelSampler(OtelProperties otelProperties) {
		return Samplers.traceIdRatioBased(otelProperties.getTraceIdRatioBased());
	}

	@Bean
	@ConditionalOnMissingBean
	SpanExporterCustomizer noOpSleuthSpanFilterConverter() {
		return new SpanExporterCustomizer() {

		};
	}

}
