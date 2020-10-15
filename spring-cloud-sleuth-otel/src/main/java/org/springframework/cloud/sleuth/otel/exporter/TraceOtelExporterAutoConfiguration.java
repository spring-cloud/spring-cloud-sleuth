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

package org.springframework.cloud.sleuth.otel.exporter;

import java.util.List;

import io.opentelemetry.exporters.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.extensions.trace.export.DisruptorAsyncSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.api.exporter.SpanFilter;
import org.springframework.cloud.sleuth.otel.autoconfig.TraceOtelAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable OTel exporters.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
@AutoConfigureBefore(TraceOtelAutoConfiguration.class)
@EnableConfigurationProperties(OtelExporterProperties.class)
public class TraceOtelExporterAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	static class SleuthExporterConfiguration {

		@Bean
		@ConditionalOnProperty(value = "spring.sleuth.otel.exporter.sleuth-span-filter.enabled", matchIfMissing = true)
		SpanExporterConverter sleuthSpanFilterConverter(List<SpanFilter> spanFilters) {
			return new SpanExporterConverter() {
				@Override
				public SpanExporter get(SpanExporter spanExporter) {
					return new CompositeSpanExporter(spanExporter, spanFilters);
				}
			};
		}

	}

	// TODO: [OTEL] Move it to `spring-cloud-sleuth-zipkin` module
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(ZipkinSpanExporter.class)
	@EnableConfigurationProperties(OtelZipkinProperties.class)
	static class ZipkinConfiguration {

		@Value("${spring.application.name:" + ZipkinSpanExporter.DEFAULT_SERVICE_NAME + "}")
		String defaultServiceName;

		@Bean
		@ConditionalOnMissingBean
		ZipkinSpanExporter otelZipkinSpanExporter(OtelZipkinProperties otelZipkinProperties, Environment env) {
			return ZipkinSpanExporter.newBuilder().setEndpoint(otelZipkinProperties.getZipkinEndpoint())
					.setServiceName(StringUtils.hasText(otelZipkinProperties.getServiceName())
							? otelZipkinProperties.getServiceName()
							: env.getProperty("spring.application.name",
									env.getProperty("spring.zipkin.service.name",
											ZipkinSpanExporter.DEFAULT_SERVICE_NAME)))
					// TODO: add sender and encoder
					.build();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(DisruptorAsyncSpanProcessor.class)
	static class DisruptorAsyncSpanProcessorConfiguration {

		// TODO: [OTEL] how to use this? A BPP or what?
		// @Bean
		DisruptorAsyncSpanProcessor otelDisruptorAsyncSpanProcessor() {
			return DisruptorAsyncSpanProcessor.newBuilder(null).build();
		}

	}

}