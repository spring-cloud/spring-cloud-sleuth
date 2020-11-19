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

package org.springframework.cloud.sleuth.autoconfig.zipkin2;

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
import org.springframework.cloud.sleuth.zipkin2.CachingZipkinUrlExtractor;
import org.springframework.cloud.sleuth.zipkin2.LoadBalancerClientZipkinLoadBalancer;
import org.springframework.cloud.sleuth.zipkin2.RestTemplateSender;
import org.springframework.cloud.sleuth.zipkin2.StaticInstanceZipkinLoadBalancer;
import org.springframework.cloud.sleuth.zipkin2.ZipkinLoadBalancer;
import org.springframework.cloud.sleuth.zipkin2.ZipkinProperties;
import org.springframework.cloud.sleuth.zipkin2.ZipkinRestTemplateCustomizer;
import org.springframework.cloud.sleuth.zipkin2.ZipkinRestTemplateWrapper;
import org.springframework.cloud.sleuth.zipkin2.ZipkinUrlExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(name = ZipkinAutoConfiguration.SENDER_BEAN_NAME)
@Conditional(ZipkinSenderCondition.class)
@EnableConfigurationProperties(ZipkinSenderProperties.class)
class ZipkinRestTemplateSenderConfiguration {

	private static final Log log = LogFactory.getLog(ZipkinRestTemplateSenderConfiguration.class);

	@Bean(ZipkinAutoConfiguration.SENDER_BEAN_NAME)
	Sender restTemplateSender(ZipkinProperties zipkin, ZipkinRestTemplateCustomizer zipkinRestTemplateCustomizer,
			ZipkinUrlExtractor extractor) {
		RestTemplate restTemplate = new ZipkinRestTemplateWrapper(zipkin, extractor);
		restTemplate = zipkinRestTemplateCustomizer.customizeTemplate(restTemplate);
		return new RestTemplateSender(restTemplate, zipkin.getBaseUrl(), zipkin.getEncoder());
	}

	@Bean
	ZipkinUrlExtractor defaultZipkinUrlExtractor(final ZipkinLoadBalancer zipkinLoadBalancer) {
		return new CachingZipkinUrlExtractor(zipkinLoadBalancer);
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingClass("org.springframework.cloud.client.loadbalancer.LoadBalancerClient")
	static class DefaultZipkinUrlExtractorConfiguration {

		@Autowired(required = false)
		LoadBalancerClient client;

		@Bean
		@ConditionalOnMissingBean
		ZipkinLoadBalancer staticInstanceLoadBalancer(final ZipkinProperties zipkinProperties) {
			return new StaticInstanceZipkinLoadBalancer(zipkinProperties);
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
			ZipkinLoadBalancer staticInstanceLoadBalancer(final ZipkinProperties zipkinProperties) {
				return new StaticInstanceZipkinLoadBalancer(zipkinProperties);
			}

		}

	}

}
