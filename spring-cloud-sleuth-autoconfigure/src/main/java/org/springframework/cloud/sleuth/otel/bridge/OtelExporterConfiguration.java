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

import java.util.List;

import io.opentelemetry.sdk.trace.export.SpanExporter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.api.exporter.SpanFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable OpenTelemetry exporters.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(SpanExporter.class)
@EnableConfigurationProperties(OtelExporterProperties.class)
class OtelExporterConfiguration {

	@Bean
	@ConditionalOnProperty(value = "spring.sleuth.otel.exporter.sleuth-span-filter.enabled", matchIfMissing = true)
	SpanExporterCustomizer sleuthSpanFilterConverter(List<SpanFilter> spanFilters) {
		return new SpanExporterCustomizer() {
			@Override
			public SpanExporter customize(SpanExporter spanExporter) {
				return new CompositeSpanExporter(spanExporter, spanFilters);
			}
		};
	}

}
