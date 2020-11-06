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

package org.springframework.cloud.sleuth.zipkin2;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import zipkin2.reporter.Sender;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables reporting to Zipkin via HTTP.
 *
 * The {@link ZipkinRestTemplateCustomizer} allows you to customize the
 * {@link RestTemplate} that is used to send Spans to Zipkin. Its default implementation -
 * {@link DefaultZipkinRestTemplateCustomizer} adds the GZip compression.
 *
 * @author Spencer Gibb
 * @author Tim Ysewyn
 * @since 1.0.0
 * @see ZipkinRestTemplateCustomizer
 * @see DefaultZipkinRestTemplateCustomizer
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = { "spring.sleuth.enabled", "spring.zipkin.enabled" }, matchIfMissing = true)
@AutoConfigureBefore(TraceAutoConfiguration.class)
@ConditionalOnClass(Tracer.class)
@AutoConfigureAfter(ZipkinAutoConfiguration.class)
public class ZipkinOtelAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(ZipkinSpanExporter.class)
	static class ZipkinConfiguration {

		@Bean
		@ConditionalOnMissingBean
		ZipkinSpanExporter otelZipkinSpanExporter(ZipkinProperties zipkinProperties,
				@Qualifier(ZipkinAutoConfiguration.SENDER_BEAN_NAME) Sender sender, Environment env) {
			return ZipkinSpanExporter.builder().setEndpoint(zipkinProperties.getBaseUrl() + "api/v2/spans")
					.setSender(sender).setEncoder(zipkinProperties.getEncoder())
					.setServiceName(
							StringUtils.hasText(zipkinProperties.getService().getName())
									? zipkinProperties.getService().getName()
									: env.getProperty("spring.application.name", env.getProperty(
											"spring.zipkin.service.name", ZipkinSpanExporter.DEFAULT_SERVICE_NAME)))
					.build();
		}

	}

}
