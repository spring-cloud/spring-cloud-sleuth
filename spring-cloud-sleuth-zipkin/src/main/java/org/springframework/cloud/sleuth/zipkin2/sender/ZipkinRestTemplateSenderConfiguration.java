package org.springframework.cloud.sleuth.zipkin2.sender;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
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
			return new ZipkinLoadBalancer() {
				@Override public URI instance() {
					return URI.create(zipkinProperties.getBaseUrl());
				}
			};
		}
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
