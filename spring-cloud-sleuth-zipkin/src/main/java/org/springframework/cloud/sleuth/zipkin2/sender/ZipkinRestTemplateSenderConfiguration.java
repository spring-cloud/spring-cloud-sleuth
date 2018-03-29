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

package org.springframework.cloud.sleuth.zipkin2.sender;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.sleuth.zipkin2.ZipkinLoadBalancer;
import org.springframework.cloud.sleuth.zipkin2.ZipkinProperties;
import org.springframework.cloud.sleuth.zipkin2.ZipkinRestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zipkin2.reporter.Sender;

@Configuration
@ConditionalOnMissingBean(Sender.class)
@Conditional(ZipkinSenderCondition.class)
@EnableConfigurationProperties(ZipkinSenderProperties.class)
class ZipkinRestTemplateSenderConfiguration {
	@Autowired ZipkinUrlExtractor extractor;

	@Bean
	@ConditionalOnMissingBean
	public Sender restTemplateSender(ZipkinProperties zipkin,
			ZipkinRestTemplateCustomizer zipkinRestTemplateCustomizer) {
		RestTemplate restTemplate = new ZipkinRestTemplateWrapper(zipkin, this.extractor);
		zipkinRestTemplateCustomizer.customize(restTemplate);
		return new RestTemplateSender(restTemplate, zipkin.getBaseUrl(), zipkin.getEncoder());
	}

	@Configuration
	@ConditionalOnMissingClass("org.springframework.cloud.client.loadbalancer.LoadBalancerClient")
	static class DefaultZipkinUrlExtractorConfiguration {
		@Autowired(required = false) LoadBalancerClient client;

		@Bean
		@ConditionalOnMissingBean
		ZipkinLoadBalancer noOpLoadBalancer(final ZipkinProperties zipkinProperties) {
			return new NoOpZipkinLoadBalancer(zipkinProperties);
		}
	}

	@Configuration
	@ConditionalOnClass(LoadBalancerClient.class)
	static class DiscoveryClientZipkinUrlExtractorConfiguration {

		@Configuration
		@ConditionalOnProperty(value = "spring.zipkin.discoveryClientEnabled", havingValue = "true", matchIfMissing = true)
		static class ZipkinClientLoadBalancedConfiguration {
			@Autowired(required = false) LoadBalancerClient client;

			@Bean
			@ConditionalOnMissingBean
			ZipkinLoadBalancer loadBalancerClientZipkinLoadBalancer(ZipkinProperties zipkinProperties) {
				return new LoadBalancerClientZipkinLoadBalancer(this.client, zipkinProperties);
			}
		}

		@Configuration
		@ConditionalOnProperty(value = "spring.zipkin.discoveryClientEnabled", havingValue = "false")
		static class ZipkinClientNoOpConfiguration {
			@Bean
			@ConditionalOnMissingBean
			ZipkinLoadBalancer noOpLoadBalancer(final ZipkinProperties zipkinProperties) {
				return new NoOpZipkinLoadBalancer(zipkinProperties);
			}
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
}

/**
 * Resolves at runtime where the Zipkin server is. If there's no discovery client then {@link URI}
 * from the properties is taken. Otherwise service discovery is pinged for current Zipkin address.
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
				log.debug("Failed to create the new URI from original ["
						+ originalUrl
						+ "] and new one ["
						+ resolvedZipkinUri
						+ "]");
			}
			return originalUrl;
		}
	}
}

/**
 * Internal interface to provide a way to retrieve Zipkin URI. If there's no discovery client then
 * this value will be taken from the properties. Otherwise host will be assumed to be a service id.
 */
interface ZipkinUrlExtractor {
	URI zipkinUrl(ZipkinProperties zipkinProperties);
}

class NoOpZipkinLoadBalancer implements ZipkinLoadBalancer {

	private final ZipkinProperties zipkinProperties;

	NoOpZipkinLoadBalancer(ZipkinProperties zipkinProperties) {
		this.zipkinProperties = zipkinProperties;
	}

	@Override public URI instance() {
		return URI.create(this.zipkinProperties.getBaseUrl());
	}
}