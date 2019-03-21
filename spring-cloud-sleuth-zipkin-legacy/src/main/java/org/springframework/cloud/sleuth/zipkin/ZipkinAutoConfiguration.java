/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.SpanAdjuster;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.cloud.sleuth.sampler.PercentageBasedSampler;
import org.springframework.cloud.sleuth.sampler.SamplerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * enables reporting to Zipkin via HTTP. Has a default {@link Sampler} set as
 * {@link PercentageBasedSampler}.
 *
 * The {@link ZipkinRestTemplateCustomizer} allows you to customize the {@link RestTemplate}
 * that is used to send Spans to Zipkin. Its default implementation - {@link DefaultZipkinRestTemplateCustomizer}
 * adds the GZip compression.
 *
 * @author Spencer Gibb
 * @since 1.0.0
 *
 * @see PercentageBasedSampler
 * @see ZipkinRestTemplateCustomizer
 * @see DefaultZipkinRestTemplateCustomizer
 */
@Configuration
@EnableConfigurationProperties({ZipkinProperties.class, SamplerProperties.class})
@ConditionalOnProperty(value = "spring.zipkin.enabled", matchIfMissing = true)
@AutoConfigureBefore(TraceAutoConfiguration.class)
public class ZipkinAutoConfiguration {

	@Autowired(required = false) List<SpanAdjuster> spanAdjusters = new ArrayList<>();
	@Autowired ZipkinUrlExtractor extractor;

	@Bean
	@ConditionalOnMissingBean
	public ZipkinSpanReporter reporter(SpanMetricReporter spanMetricReporter, ZipkinProperties zipkin,
			ZipkinRestTemplateCustomizer zipkinRestTemplateCustomizer) {
		RestTemplate restTemplate = zipkinRestTemplate(zipkin);
		zipkinRestTemplateCustomizer.customize(restTemplate);
		return new HttpZipkinSpanReporter(restTemplate, zipkin.getBaseUrl(), zipkin.getFlushInterval(),
				spanMetricReporter, zipkin.getEncoding());
	}

	private RestTemplate zipkinRestTemplate(ZipkinProperties zipkinProperties) {
		return new ZipkinRestTemplateWrapper(zipkinProperties, this.extractor);
	}

	@Configuration
	@ConditionalOnClass(LoadBalancerClient.class)
	static class DiscoveryClientZipkinUrlExtractorConfiguration {

		@Autowired(required = false) LoadBalancerClient client;

		@Bean
		@ConditionalOnMissingBean
		ZipkinLoadBalancer loadBalancerClientZipkinLoadBalancer(ZipkinProperties zipkinProperties) {
			return new LoadBalancerClientZipkinLoadBalancer(this.client, zipkinProperties);
		}
	}

	@Configuration
	@ConditionalOnMissingClass("org.springframework.cloud.client.loadbalancer.LoadBalancerClient")
	static class DefaultZipkinUrlExtractorConfiguration {

		@Bean
		@ConditionalOnMissingBean
		ZipkinLoadBalancer loadBalancerClientZipkinLoadBalancer(final ZipkinProperties zipkinProperties) {
			return new ZipkinLoadBalancer() {
				@Override public URI instance() {
					return URI.create(zipkinProperties.getBaseUrl());
				}
			};
		}
	}

	@Bean
	ZipkinUrlExtractor zipkinUrlExtractor(final ZipkinLoadBalancer zipkinLoadBalancer) {
		return new ZipkinUrlExtractor() {
			@Override
			public URI zipkinUrl(ZipkinProperties zipkinProperties) {
				return zipkinLoadBalancer.instance();
			}
		};
	}

	@Bean
	@ConditionalOnMissingBean
	public ZipkinRestTemplateCustomizer zipkinRestTemplateCustomizer(ZipkinProperties zipkinProperties) {
		return new DefaultZipkinRestTemplateCustomizer(zipkinProperties);
	}

	@Configuration
	@ConditionalOnClass(RefreshScope.class)
	protected static class RefreshScopedPercentageBasedSamplerConfiguration {
		@Bean
		@RefreshScope
		@ConditionalOnMissingBean
		public Sampler defaultTraceSampler(SamplerProperties config) {
			return new PercentageBasedSampler(config);
		}
	}

	@Configuration
	@ConditionalOnMissingClass("org.springframework.cloud.context.config.annotation.RefreshScope")
	protected static class NonRefreshScopePercentageBasedSamplerConfiguration {
		@Bean
		@ConditionalOnMissingBean
		public Sampler defaultTraceSampler(SamplerProperties config) {
			return new PercentageBasedSampler(config);
		}
	}

	@Bean
	public SpanReporter zipkinSpanListener(ZipkinSpanReporter reporter, EndpointLocator endpointLocator,
			Environment environment) {
		return new ZipkinSpanListener(reporter, endpointLocator, environment, this.spanAdjusters);
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
			return new ServerPropertiesEndpointLocator(this.serverProperties, this.environment,
					this.zipkinProperties, this.inetUtils);
		}

	}

	@Configuration
	@ConditionalOnClass(DiscoveryClient.class)
	@ConditionalOnMissingBean(EndpointLocator.class)
	@ConditionalOnProperty(value = "spring.zipkin.locator.discovery.enabled", havingValue = "true")
	protected static class DiscoveryClientEndpointLocatorConfiguration {

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
			return new FallbackHavingEndpointLocator(discoveryClientEndpointLocator(),
					new ServerPropertiesEndpointLocator(this.serverProperties, this.environment,
							this.zipkinProperties, this.inetUtils));
		}

		private DiscoveryClientEndpointLocator discoveryClientEndpointLocator() {
			if (this.registration != null) {
				return new DiscoveryClientEndpointLocator(this.registration, this.zipkinProperties);
			}
			return null;
		}

	}

}

/**
 * Internal interface to provide a way to retrieve Zipkin URI. If there's no discovery client
 * then this value will be taken from the properties. Otherwise host will be assumed to
 * be a service id.
 */
interface ZipkinUrlExtractor {
	URI zipkinUrl(ZipkinProperties zipkinProperties);
}

/**
 * Resolves at runtime where the Zipkin server is. If there's no discovery client then
 * {@link URI} from the properties is taken. Otherwise service discovery is pinged
 * for current Zipkin address.
 */
class ZipkinRestTemplateWrapper extends RestTemplate {

	private static final Log log = LogFactory.getLog(ZipkinRestTemplateWrapper.class);

	private final ZipkinProperties zipkinProperties;
	private final ZipkinUrlExtractor extractor;

	ZipkinRestTemplateWrapper(ZipkinProperties zipkinProperties,
			ZipkinUrlExtractor extractor) {
		this.zipkinProperties = zipkinProperties;
		this.extractor = extractor;
	}

	@Override protected <T> T doExecute(URI originalUrl, HttpMethod method,
			RequestCallback requestCallback,
			ResponseExtractor<T> responseExtractor) throws RestClientException {
		URI uri = this.extractor.zipkinUrl(this.zipkinProperties);
		URI newUri = resolvedZipkinUri(originalUrl, uri);
		return super.doExecute(newUri, method, requestCallback, responseExtractor);
	}

	private URI resolvedZipkinUri(URI originalUrl, URI resolvedZipkinUri) {
		try {
			return new URI(resolvedZipkinUri.getScheme(), resolvedZipkinUri.getUserInfo(),
					resolvedZipkinUri.getHost(), resolvedZipkinUri.getPort(), originalUrl.getPath(),
					originalUrl.getQuery(), originalUrl.getFragment());
		} catch (URISyntaxException e) {
			if (log.isDebugEnabled()) {
				log.debug("Failed to create the new URI from original [" + originalUrl + "] and new one [" + resolvedZipkinUri + "]");
			}
			return originalUrl;
		}
	}
}
