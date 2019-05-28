/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin2.sender;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import zipkin2.reporter.Sender;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.sleuth.zipkin2.ZipkinAutoConfiguration;
import org.springframework.cloud.sleuth.zipkin2.ZipkinLoadBalancer;
import org.springframework.cloud.sleuth.zipkin2.ZipkinProperties;
import org.springframework.cloud.sleuth.zipkin2.ZipkinRestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConditionalOnMissingBean(name = ZipkinAutoConfiguration.SENDER_BEAN_NAME)
@Conditional(ZipkinSenderCondition.class)
@EnableConfigurationProperties(ZipkinSenderProperties.class)
class ZipkinRestTemplateSenderConfiguration {

	@Bean
	ZipkinUrlExtractor zipkinUrlExtractor(final ZipkinLoadBalancer zipkinLoadBalancer) {
		return new ZipkinUrlExtractor() {
			@Override
			public URI zipkinUrl(ZipkinProperties zipkinProperties) {
				return zipkinLoadBalancer.instance();
			}
		};
	}

	@Configuration
	@AutoConfigureAfter(OAuth2RestTemplateSenderConfiguration.class)
	static class DefaultRestTemplateSenderConfiguration {
		@Autowired
		ZipkinUrlExtractor extractor;

		@ConditionalOnMissingBean(name = ZipkinAutoConfiguration.SENDER_BEAN_NAME)
		@Bean(ZipkinAutoConfiguration.SENDER_BEAN_NAME)
		public Sender restTemplateSender(ZipkinProperties zipkin,
				ZipkinRestTemplateCustomizer zipkinRestTemplateCustomizer) {
			RestTemplate restTemplate = new ZipkinRestTemplateWrapper(zipkin, this.extractor, new ZipkinUriResolver());
			zipkinRestTemplateCustomizer.customize(restTemplate);
			return new RestTemplateSender(restTemplate, zipkin.getBaseUrl(),
					zipkin.getEncoder());
		}
	}

	@Configuration
	@ConditionalOnClass({ OAuth2ProtectedResourceDetails.class,
			OAuth2RestTemplate.class })
	@ConditionalOnBean(value = OAuth2ProtectedResourceDetails.class, name = ZipkinAutoConfiguration.OAUTH2_RESOURCE_BEAN_NAME)
	static class OAuth2RestTemplateSenderConfiguration {

		@Autowired
		ZipkinUrlExtractor extractor;

		@Autowired
		@Qualifier(ZipkinAutoConfiguration.OAUTH2_RESOURCE_BEAN_NAME)
		OAuth2ProtectedResourceDetails resource;

		@Bean(ZipkinAutoConfiguration.SENDER_BEAN_NAME)
		public Sender restTemplateSender(ZipkinProperties zipkin,
				ZipkinRestTemplateCustomizer zipkinRestTemplateCustomizer) {
			RestTemplate restTemplate = new ZipkinOAuth2RestTemplateWrapper(zipkin, this.extractor, new ZipkinUriResolver(), resource);
			zipkinRestTemplateCustomizer.customize(restTemplate);
			return new RestTemplateSender(restTemplate, zipkin.getBaseUrl(),
					zipkin.getEncoder());
		}
	}

	@Configuration
	@ConditionalOnMissingClass("org.springframework.cloud.client.loadbalancer.LoadBalancerClient")
	static class DefaultZipkinUrlExtractorConfiguration {

		@Autowired(required = false)
		LoadBalancerClient client;

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
		@ConditionalOnProperty(value = "spring.zipkin.discoveryClientEnabled",
				havingValue = "true", matchIfMissing = true)
		static class ZipkinClientLoadBalancedConfiguration {

			@Autowired(required = false)
			LoadBalancerClient client;

			@Bean
			@ConditionalOnMissingBean
			ZipkinLoadBalancer loadBalancerClientZipkinLoadBalancer(
					ZipkinProperties zipkinProperties) {
				return new LoadBalancerClientZipkinLoadBalancer(this.client,
						zipkinProperties);
			}

		}

		@Configuration
		@ConditionalOnProperty(value = "spring.zipkin.discoveryClientEnabled",
				havingValue = "false")
		static class ZipkinClientNoOpConfiguration {

			@Bean
			@ConditionalOnMissingBean
			ZipkinLoadBalancer noOpLoadBalancer(final ZipkinProperties zipkinProperties) {
				return new NoOpZipkinLoadBalancer(zipkinProperties);
			}

		}

	}

}

/**
 * Internal interface to provide a way to retrieve Zipkin URI. If there's no discovery
 * client then this value will be taken from the properties. Otherwise host will be
 * assumed to be a service id.
 */
interface ZipkinUrlExtractor {

	URI zipkinUrl(ZipkinProperties zipkinProperties);

}

/**
 * Resolves at runtime where the Zipkin server is. If there's no discovery client then
 * {@link URI} from the properties is taken. Otherwise service discovery is pinged for
 * current Zipkin address.
 */
class ZipkinRestTemplateWrapper extends RestTemplate {

	private final ZipkinProperties zipkinProperties;

	private final ZipkinUrlExtractor extractor;

	private final ZipkinUriResolver resolver;

	ZipkinRestTemplateWrapper(ZipkinProperties zipkinProperties,
			ZipkinUrlExtractor extractor, ZipkinUriResolver resolver) {
		this.zipkinProperties = zipkinProperties;
		this.extractor = extractor;
		this.resolver = resolver;
	}

	@Override
	protected <T> T doExecute(URI originalUrl, HttpMethod method,
			RequestCallback requestCallback, ResponseExtractor<T> responseExtractor)
			throws RestClientException {
		URI uri = this.extractor.zipkinUrl(this.zipkinProperties);
		URI newUri = resolver.resolvedZipkinUri(originalUrl, uri);
		return super.doExecute(newUri, method, requestCallback, responseExtractor);
	}

}

/**
 * Resolves at runtime where the Zipkin server is. If there's no discovery client then
 * {@link URI} from the properties is taken. Otherwise service discovery is pinged for
 * current Zipkin address.
 */
class ZipkinOAuth2RestTemplateWrapper extends OAuth2RestTemplate {

	private final ZipkinProperties zipkinProperties;

	private final ZipkinUrlExtractor extractor;

	private final ZipkinUriResolver resolver;

	ZipkinOAuth2RestTemplateWrapper(ZipkinProperties zipkinProperties,
			ZipkinUrlExtractor extractor, ZipkinUriResolver resolver, OAuth2ProtectedResourceDetails resource) {
		super(resource);
		this.zipkinProperties = zipkinProperties;
		this.extractor = extractor;
		this.resolver = resolver;
	}

	@Override
	protected <T> T doExecute(URI originalUrl, HttpMethod method,
			RequestCallback requestCallback, ResponseExtractor<T> responseExtractor)
			throws RestClientException {
		URI uri = this.extractor.zipkinUrl(this.zipkinProperties);
		URI newUri = resolver.resolvedZipkinUri(originalUrl, uri);
		return super.doExecute(newUri, method, requestCallback, responseExtractor);
	}

}

class ZipkinUriResolver {
	private static final Log log = LogFactory.getLog(ZipkinUriResolver.class);

	URI resolvedZipkinUri(URI originalUrl, URI resolvedZipkinUri) {
		try {
			return new URI(resolvedZipkinUri.getScheme(), resolvedZipkinUri.getUserInfo(),
					resolvedZipkinUri.getHost(), resolvedZipkinUri.getPort(),
					originalUrl.getPath(), originalUrl.getQuery(),
					originalUrl.getFragment());
		}
		catch (URISyntaxException e) {
			if (log.isDebugEnabled()) {
				log.debug("Failed to create the new URI from original [" + originalUrl
						+ "] and new one [" + resolvedZipkinUri + "]");
			}
			return originalUrl;
		}
	}
}

class NoOpZipkinLoadBalancer implements ZipkinLoadBalancer {

	private final ZipkinProperties zipkinProperties;

	NoOpZipkinLoadBalancer(ZipkinProperties zipkinProperties) {
		this.zipkinProperties = zipkinProperties;
	}

	@Override
	public URI instance() {
		return URI.create(this.zipkinProperties.getBaseUrl());
	}

}
