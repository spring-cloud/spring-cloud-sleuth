/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.zipkin2;

import java.util.concurrent.TimeUnit;

import brave.sampler.Sampler;
import zipkin2.Span;
import zipkin2.codec.BytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.ReporterMetrics;
import zipkin2.reporter.Sender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.sampler.ProbabilityBasedSampler;
import org.springframework.cloud.sleuth.sampler.SamplerProperties;
import org.springframework.cloud.sleuth.zipkin2.sender.ZipkinSenderConfigurationImportSelector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * enables reporting to Zipkin via HTTP. Has a default {@link Sampler} set as
 * {@link ProbabilityBasedSampler}.
 *
 * The {@link ZipkinRestTemplateCustomizer} allows you to customize the {@link RestTemplate}
 * that is used to send Spans to Zipkin. Its default implementation - {@link DefaultZipkinRestTemplateCustomizer}
 * adds the GZip compression.
 *
 * @author Spencer Gibb
 * @since 1.0.0
 *
 * @see ProbabilityBasedSampler
 * @see ZipkinRestTemplateCustomizer
 * @see DefaultZipkinRestTemplateCustomizer
 */
@Configuration
@EnableConfigurationProperties({ZipkinProperties.class, SamplerProperties.class})
@ConditionalOnProperty(value = "spring.zipkin.enabled", matchIfMissing = true)
@AutoConfigureBefore(TraceAutoConfiguration.class)
@Import(ZipkinSenderConfigurationImportSelector.class)
public class ZipkinAutoConfiguration {

	/**
	 * Accepts a sender so you can plug-in any standard one. Returns a Reporter so you can also
	 * replace with a standard one.
	 */
	@Bean
	@ConditionalOnMissingBean
	public Reporter<Span> reporter(
			ReporterMetrics reporterMetrics,
			ZipkinProperties zipkin,
			Sender sender,
			BytesEncoder<Span> spanBytesEncoder
	) {
		return AsyncReporter.builder(sender)
				.queuedMaxSpans(1000) // historical constraint. Note: AsyncReporter supports memory bounds
				.messageTimeout(zipkin.getMessageTimeout(), TimeUnit.SECONDS)
				.metrics(reporterMetrics)
				.build(spanBytesEncoder);
	}

	@Bean
	@ConditionalOnMissingBean
	public BytesEncoder<Span> spanBytesEncoder(ZipkinProperties zipkinProperties) {
		return zipkinProperties.getEncoder();
	}

	@Bean
	@ConditionalOnMissingBean
	public ZipkinRestTemplateCustomizer zipkinRestTemplateCustomizer(ZipkinProperties zipkinProperties) {
		return new DefaultZipkinRestTemplateCustomizer(zipkinProperties);
	}

	@Bean
	@ConditionalOnMissingBean
	ReporterMetrics sleuthReporterMetrics() {
		return new InMemoryReporterMetrics(); 
	}
	
	@Configuration
	@ConditionalOnClass(RefreshScope.class)
	protected static class RefreshScopedProbabilityBasedSamplerConfiguration {
		@Bean
		@RefreshScope
		@ConditionalOnMissingBean
		public Sampler defaultTraceSampler(SamplerProperties config) {
			return new ProbabilityBasedSampler(config);
		}
	}

	@Configuration
	@ConditionalOnMissingClass("org.springframework.cloud.context.config.annotation.RefreshScope")
	protected static class NonRefreshScopeProbabilityBasedSamplerConfiguration {
		@Bean
		@ConditionalOnMissingBean
		public Sampler defaultTraceSampler(SamplerProperties config) {
			return new ProbabilityBasedSampler(config);
		}
	}

	@Configuration
	@ConditionalOnMissingBean(EndpointLocator.class)
	@ConditionalOnProperty(value = "spring.zipkin.locator.discovery.enabled", havingValue = "false", matchIfMissing = true)
	protected static class DefaultEndpointLocatorConfiguration {

		@Autowired(required=false)
		private ServerProperties serverProperties;

		@Autowired
		private ZipkinProperties zipkinProperties;

		@Autowired(required=false)
		private InetUtils inetUtils;

		@Autowired
		private Environment environment;

		@Bean
		public EndpointLocator zipkinEndpointLocator() {
			return new DefaultEndpointLocator(null, this.serverProperties, this.environment,
					this.zipkinProperties, this.inetUtils);
		}

	}

	@Configuration
	@ConditionalOnClass(Registration.class)
	@ConditionalOnMissingBean(EndpointLocator.class)
	@ConditionalOnProperty(value = "spring.zipkin.locator.discovery.enabled", havingValue = "true")
	protected static class RegistrationEndpointLocatorConfiguration {

		@Autowired(required=false)
		private ServerProperties serverProperties;

		@Autowired
		private ZipkinProperties zipkinProperties;

		@Autowired(required=false)
		private InetUtils inetUtils;

		@Autowired
		private Environment environment;

		@Autowired(required=false)
		private Registration registration;

		@Bean
		public EndpointLocator zipkinEndpointLocator() {
			return new DefaultEndpointLocator(this.registration, this.serverProperties, this.environment,
							this.zipkinProperties, this.inetUtils);
		}
	}
}
