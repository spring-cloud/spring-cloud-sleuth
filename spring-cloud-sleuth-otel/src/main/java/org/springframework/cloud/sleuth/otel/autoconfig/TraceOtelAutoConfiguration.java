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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.spi.MeterProviderFactory;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.trace.spi.TracerProviderFactory;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.spi.TracerProviderFactorySdk;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.otel.bridge.OtelOpenTelemetry;
import org.springframework.cloud.sleuth.otel.exporter.SpanExporterCustomizer;
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
	OpenTelemetry otel(TracerProviderFactory tracerProviderFactory, MeterProviderFactory meterProviderFactory,
			TracerProvider tracerProvider, MeterProvider meterProvider, ContextPropagators contextPropagators) {
		return new OtelOpenTelemetry(tracerProviderFactory, meterProviderFactory, tracerProvider, meterProvider,
				contextPropagators);
	}

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
	MeterProviderFactory otelMeterProviderFactory() {
		return OpenTelemetry::getGlobalMeterProvider;
	}

	@Bean
	@ConditionalOnMissingBean
	MeterProvider otelMeterProvider(MeterProviderFactory meterProviderFactory) {
		return meterProviderFactory.create();
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
					.map(e -> SimpleSpanProcessor.builder(spanExporterCustomizer.customize(e)).build())
					.collect(Collectors.toList()));
			processors.forEach(tracerSdkProvider::addSpanProcessor);
			tracerSdkProvider.updateActiveTraceConfig(traceConfig);
		});
		return tracerProvider.get(otelProperties.getInstrumentationName());
	}

	@Bean
	@ConditionalOnMissingBean
	Sampler otelSampler(OtelProperties otelProperties) {
		return Sampler.traceIdRatioBased(otelProperties.getTraceIdRatioBased());
	}

	@Bean
	@ConditionalOnMissingBean
	SpanExporterCustomizer noOpSleuthSpanFilterConverter() {
		return new SpanExporterCustomizer() {

		};
	}

}
