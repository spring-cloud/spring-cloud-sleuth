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

package org.springframework.cloud.sleuth.otel.log;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import org.slf4j.MDC;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.otel.bridge.OtelBaggageManager;
import org.springframework.cloud.sleuth.otel.bridge.TraceOtelBridgeAutoConfiguation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable logging configuration via Spring Cloud Sleuth and
 * OpenTelemetry SDK.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(Tracer.class)
@AutoConfigureBefore(TraceAutoConfiguration.class)
@AutoConfigureAfter(TraceOtelBridgeAutoConfiguation.class)
@EnableConfigurationProperties(OtelLogProperties.class)
public class TraceOtelLogAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(MDC.class)
	@ConditionalOnProperty(value = "spring.sleuth.otel.log.slf4j.enabled", matchIfMissing = true)
	static class Slf4jConfiguration {

		@Bean
		Slf4jSpanProcessor otelSlf4jSpanProcessor(SleuthBaggageProperties sleuthBaggageProperties,
				OtelBaggageManager baggageManager) {
			return new Slf4jSpanProcessor(sleuthBaggageProperties, baggageManager);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(LoggingSpanExporter.class)
	@ConditionalOnProperty("spring.sleuth.otel.log.exporter.enabled")
	static class LoggingExporterConfiguration {

		@Bean
		LoggingSpanExporter otelLoggingSpanExporter() {
			return new LoggingSpanExporter();
		}

	}

}
