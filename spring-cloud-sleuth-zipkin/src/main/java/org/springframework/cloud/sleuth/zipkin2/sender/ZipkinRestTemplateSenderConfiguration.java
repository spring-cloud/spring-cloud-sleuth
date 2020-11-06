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

package org.springframework.cloud.sleuth.zipkin2.sender;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import zipkin2.reporter.Sender;

import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(name = ZipkinAutoConfiguration.SENDER_BEAN_NAME)
@Conditional(ZipkinSenderCondition.class)
@EnableConfigurationProperties(ZipkinSenderProperties.class)
class ZipkinRestTemplateSenderConfiguration {

	private static final Log log = LogFactory.getLog(ZipkinRestTemplateSenderConfiguration.class);

	@Autowired
	ZipkinUrlExtractor extractor;

	@Bean(ZipkinAutoConfiguration.SENDER_BEAN_NAME)
	public Sender restTemplateSender(ZipkinProperties zipkin,
			ZipkinRestTemplateCustomizer zipkinRestTemplateCustomizer) {
		RestTemplate restTemplate = new ZipkinRestTemplateWrapper(zipkin, this.extractor);
		restTemplate = zipkinRestTemplateCustomizer.customizeTemplate(restTemplate);
		return new RestTemplateSender(restTemplate, zipkin.getBaseUrl(), zipkin.getEncoder());
	}

	@Bean
	ZipkinUrlExtractor zipkinUrlExtractor(final ZipkinLoadBalancer zipkinLoadBalancer) {
		return new CachingZipkinUrlExtractor(zipkinLoadBalancer);
	}

	static class CachingZipkinUrlExtractor implements ZipkinUrlExtractor {

		final AtomicInteger zipkinPort = new AtomicInteger();

		private final ZipkinLoadBalancer zipkinLoadBalancer;

		CachingZipkinUrlExtractor(ZipkinLoadBalancer zipkinLoadBalancer) {
			this.zipkinLoadBalancer = zipkinLoadBalancer;
		}

		@Override
		public URI zipkinUrl(ZipkinProperties zipkinProperties) {
			int cachedZipkinPort = zipkinPort(zipkinProperties);
			if (cachedZipkinPort == -1) {
				if (log.isDebugEnabled()) {
					log.debug("The port in Zipkin's URL [" + zipkinProperties.getBaseUrl()
							+ "] wasn't provided - that means that load balancing might take place");
				}
				return this.zipkinLoadBalancer.instance();
			}
			if (log.isDebugEnabled()) {
				log.debug("The port in Zipkin's URL [" + zipkinProperties.getBaseUrl()
						+ "] is provided - that means that load balancing will not take place");
			}
			return noOpZipkinLoadBalancer(zipkinProperties).instance();
		}

		NoOpZipkinLoadBalancer noOpZipkinLoadBalancer(ZipkinProperties zipkinProperties) {
			return new NoOpZipkinLoadBalancer(zipkinProperties);
		}

		private int zipkinPort(ZipkinProperties zipkinProperties) {
			int cachedZipkinPort = this.zipkinPort.get();
			if (cachedZipkinPort != 0) {
				return cachedZipkinPort;
			}
			return calculatePort(zipkinProperties);
		}

		int calculatePort(ZipkinProperties zipkinProperties) {
			String baseUrl = zipkinProperties.getBaseUrl();
			URI uri = createUri(baseUrl);
			int zipkinPort = uri.getPort();
			this.zipkinPort.set(zipkinPort);
			return zipkinPort;
		}

		URI createUri(String baseUrl) {
			return URI.create(baseUrl);
		}

	}

	@Configuration(proxyBeanMethods = false)
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

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(LoadBalancerClient.class)
	static class DiscoveryClientZipkinUrlExtractorConfiguration {

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnProperty(value = "spring.zipkin.discovery-client-enabled", havingValue = "true",
				matchIfMissing = true)
		static class ZipkinClientLoadBalancedConfiguration {

			@Autowired(required = false)
			LoadBalancerClient client;

			@Bean
			@ConditionalOnMissingBean
			ZipkinLoadBalancer loadBalancerClientZipkinLoadBalancer(ZipkinProperties zipkinProperties) {
				return new LoadBalancerClientZipkinLoadBalancer(this.client, zipkinProperties);
			}

		}

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnProperty(value = "spring.zipkin.discovery-client-enabled", havingValue = "false")
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
 * Resolves at runtime where the Zipkin server is. If there's no discovery client then
 * {@link URI} from the properties is taken. Otherwise service discovery is pinged for
 * current Zipkin address.
 */
class ZipkinRestTemplateWrapper extends RestTemplate {

	private static final Log log = LogFactory.getLog(ZipkinRestTemplateWrapper.class);

	private static final int DEFAULT_TIMEOUT = 500;

	private final ZipkinProperties zipkinProperties;

	private final ZipkinUrlExtractor extractor;

	ZipkinRestTemplateWrapper(ZipkinProperties zipkinProperties, ZipkinUrlExtractor extractor) {
		this.zipkinProperties = zipkinProperties;
		this.extractor = extractor;
		setRequestFactory(clientHttpRequestFactory());
	}

	private ClientHttpRequestFactory clientHttpRequestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setReadTimeout(DEFAULT_TIMEOUT);
		factory.setConnectTimeout(DEFAULT_TIMEOUT);
		return factory;
	}

	@Override
	protected <T> T doExecute(URI originalUrl, HttpMethod method, RequestCallback requestCallback,
			ResponseExtractor<T> responseExtractor) throws RestClientException {
		URI uri = this.extractor.zipkinUrl(this.zipkinProperties);
		URI newUri = resolvedZipkinUri(originalUrl, uri);
		return super.doExecute(newUri, method, requestCallback, responseExtractor);
	}

	private URI resolvedZipkinUri(URI originalUrl, URI resolvedZipkinUri) {
		try {
			return new URI(resolvedZipkinUri.getScheme(), resolvedZipkinUri.getUserInfo(), resolvedZipkinUri.getHost(),
					resolvedZipkinUri.getPort(), originalUrl.getPath(), originalUrl.getQuery(),
					originalUrl.getFragment());
		}
		catch (URISyntaxException e) {
			if (log.isDebugEnabled()) {
				log.debug("Failed to create the new URI from original [" + originalUrl + "] and new one ["
						+ resolvedZipkinUri + "]");
			}
			return originalUrl;
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
