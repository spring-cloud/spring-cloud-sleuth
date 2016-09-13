/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.cloud.sleuth.sampler.PercentageBasedSampler;
import org.springframework.cloud.sleuth.sampler.SamplerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import zipkin.Endpoint;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables reporting to Zipkin via HTTP. Has a default {@link Sampler}
 * set as {@link PercentageBasedSampler}.
 *
 * The {@link ZipkinRestTemplateCustomizer} allows you to customize the
 * {@link RestTemplate} that is used to send Spans to Zipkin. Its default implementation -
 * {@link DefaultZipkinRestTemplateCustomizer} adds the GZip compression.
 *
 * @author Spencer Gibb
 * @since 1.0.0
 *
 * @see PercentageBasedSampler
 * @see ZipkinRestTemplateCustomizer
 * @see DefaultZipkinRestTemplateCustomizer
 */
@Configuration
@EnableConfigurationProperties({ ZipkinProperties.class, SamplerProperties.class })
@ConditionalOnProperty(value = "spring.zipkin.enabled", matchIfMissing = true)
@AutoConfigureBefore(TraceAutoConfiguration.class)
public class ZipkinAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ZipkinSpanReporter reporter(SpanMetricReporter spanMetricReporter,
			ZipkinProperties zipkin,
			ZipkinRestTemplateCustomizer zipkinRestTemplateCustomizer) {
		RestTemplate restTemplate = new RestTemplate();
		zipkinRestTemplateCustomizer.customize(restTemplate);
		return new HttpZipkinSpanReporter(restTemplate, zipkin.getBaseUrl(),
				zipkin.getFlushInterval(), spanMetricReporter);
	}

	@Bean
	@ConditionalOnMissingBean
	public ZipkinRestTemplateCustomizer zipkinRestTemplateCustomizer(
			ZipkinProperties zipkinProperties) {
		return new DefaultZipkinRestTemplateCustomizer(zipkinProperties);
	}

	@Bean
	@ConditionalOnMissingBean
	public Sampler defaultTraceSampler(SamplerProperties config) {
		return new PercentageBasedSampler(config);
	}

	@Bean
	public SpanReporter zipkinSpanListener(ZipkinSpanReporter reporter,
			EndpointLocator endpointLocator) {
		return new ZipkinSpanListener(reporter, endpointLocator);
	}

	@Configuration
	@ConditionalOnMissingClass("org.springframework.cloud.client.discovery.DiscoveryClient")
	protected static class DefaultEndpointLocatorConfiguration {

		@Autowired(required = false)
		private ServerProperties serverProperties;

		@Value("${spring.application.name:unknown}")
		private String appName;

		@Bean
		public EndpointLocator zipkinEndpointLocator() {
			return new ServerPropertiesEndpointLocator(this.serverProperties,
					this.appName);
		}

	}

	@Configuration
	@ConditionalOnClass(DiscoveryClient.class)
	protected static class DiscoveryClientEndpointLocatorConfiguration {

		@Autowired(required = false)
		private ServerProperties serverProperties;

		@Value("${spring.application.name:unknown}")
		private String appName;

		@Autowired(required = false)
		private DiscoveryClient client;

		@Bean(name = "discoveryClientEndpointCache")
		@ConditionalOnMissingBean
		@ConditionalOnProperty(value = "spring.zipkin.localEndpointCachingEnabled", matchIfMissing = false, havingValue = "true")
		public EndpointCache discoveryClientEndpointEnabledCache() {
			return new EndpointCacheImpl();
		}

		@Bean(name = "discoveryClientEndpointCache")
		@ConditionalOnMissingBean
		@ConditionalOnProperty(value = "spring.zipkin.localEndpointCachingEnabled", matchIfMissing = true, havingValue = "false")
		public EndpointCache discoveryClientEndpointNoOpCache() {
			return new EndpointCache() {
				@Override
				public Endpoint getEndpoint(EndpointFactory factory,
						ServiceInstance instance) {
					return factory.create();
				}
			};
		}

		@Bean
		public EndpointLocator zipkinEndpointLocator(EndpointCache cache) {
			return new FallbackHavingEndpointLocator(
					discoveryClientEndpointLocator(cache),
					new ServerPropertiesEndpointLocator(this.serverProperties,
							this.appName));
		}

		private DiscoveryClientEndpointLocator discoveryClientEndpointLocator(
				EndpointCache cache) {
			if (this.client != null) {
				return new DiscoveryClientEndpointLocator(this.client, cache);
			}
			return null;
		}

	}

}
