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

package org.springframework.cloud.sleuth.otel.bridge;

import java.util.regex.Pattern;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.spi.MeterProviderFactory;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.trace.spi.TracerProviderFactory;
import io.opentelemetry.context.propagation.ContextPropagators;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.SamplerFunction;
import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.api.http.HttpRequest;
import org.springframework.cloud.sleuth.api.http.HttpRequestParser;
import org.springframework.cloud.sleuth.api.http.HttpResponseParser;
import org.springframework.cloud.sleuth.api.http.HttpServerHandler;
import org.springframework.cloud.sleuth.api.propagation.Propagator;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.instrument.web.HttpClientRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientResponseParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientSampler;
import org.springframework.cloud.sleuth.instrument.web.HttpServerRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpServerResponseParser;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternProvider;
import org.springframework.cloud.sleuth.instrument.web.SleuthWebProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.lang.Nullable;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable the bridge between Sleuth API and OpenTelemetry.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(OpenTelemetry.class)
@Import({ OtelLogConfiguration.class, OtelExporterConfiguration.class })
public class OtelBridgeConfiguation {

	@Bean
	@ConditionalOnMissingBean
	OpenTelemetry otel(TracerProviderFactory tracerProviderFactory, MeterProviderFactory meterProviderFactory,
			TracerProvider tracerProvider, MeterProvider meterProvider, ContextPropagators contextPropagators) {
		return new OtelOpenTelemetry(tracerProviderFactory, meterProviderFactory, tracerProvider, meterProvider,
				contextPropagators);
	}

	@Bean
	Tracer otelTracerBridge(io.opentelemetry.api.trace.Tracer tracer, ApplicationEventPublisher publisher,
			OtelBaggageManager otelBaggageManager) {
		return new OtelTracer(tracer, publisher, otelBaggageManager);
	}

	// Both CurrentTraceContext & ContextStorageProvider
	@Bean
	@ConditionalOnMissingBean
	OtelCurrentTraceContext otelCurrentTraceContext(ApplicationEventPublisher publisher) {
		return new OtelCurrentTraceContext(publisher);
	}

	@Bean
	SpanCustomizer otelSpanCustomizer(io.opentelemetry.api.trace.Tracer tracer) {
		return new OtelSpanCustomizer(tracer);
	}

	@Bean
	Propagator otelPropagator(ContextPropagators contextPropagators, io.opentelemetry.api.trace.Tracer tracer) {
		return new OtelPropagator(contextPropagators, tracer);
	}

	@Bean
	OtelBaggageManager otelBaggageManager(CurrentTraceContext currentTraceContext,
			SleuthBaggageProperties sleuthBaggageProperties, ApplicationEventPublisher publisher) {
		return new OtelBaggageManager(currentTraceContext, sleuthBaggageProperties, publisher);
	}

	@Bean
	@ConditionalOnMissingBean
	SpanExporterCustomizer noOpSleuthSpanFilterConverter() {
		return new SpanExporterCustomizer() {

		};
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(value = "spring.sleuth.http.enabled", matchIfMissing = true)
	@EnableConfigurationProperties(SleuthWebProperties.class)
	static class TraceOtelHttpBridgeConfiguration {

		@Bean
		HttpClientHandler otelHttpClientHandler(io.opentelemetry.api.trace.Tracer tracer,
				@Nullable @HttpClientRequestParser HttpRequestParser httpClientRequestParser,
				@Nullable @HttpClientResponseParser HttpResponseParser httpClientResponseParser,
				SamplerFunction<HttpRequest> samplerFunction) {
			return new OtelHttpClientHandler(tracer, httpClientRequestParser, httpClientResponseParser,
					samplerFunction);
		}

		@Bean
		HttpServerHandler otelHttpServerHandler(io.opentelemetry.api.trace.Tracer tracer,
				@Nullable @HttpServerRequestParser HttpRequestParser httpServerRequestParser,
				@Nullable @HttpServerResponseParser HttpResponseParser httpServerResponseParser,
				SkipPatternProvider skipPatternProvider) {
			return new OtelHttpServerHandler(tracer, httpServerRequestParser, httpServerResponseParser,
					skipPatternProvider);
		}

		@Bean
		@ConditionalOnMissingBean(name = HttpClientSampler.NAME)
		SamplerFunction<HttpRequest> defaultHttpClientSampler(SleuthWebProperties sleuthWebProperties) {
			String skipPattern = sleuthWebProperties.getClient().getSkipPattern();
			if (skipPattern == null) {
				return SamplerFunction.deferDecision();
			}
			return new SkipPatternSampler(Pattern.compile(skipPattern));
		}

	}

}
