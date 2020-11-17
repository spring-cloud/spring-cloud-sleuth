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

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author liaochuntao
 */
public class ZipkinRestTemplateSenderConfigurationTest {

	@Test
	public void disableZipkinDiscoveryClient() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withPropertyValues("spring.zipkin.discovery-client-enabled=false").withUserConfiguration(
						ZipkinRestTemplateSenderConfigurationTest.MyDiscoveryClientZipkinUrlExtractorConfiguration.class,
						ZipkinProperties.class);

		runner.run(context -> assertThat(context).hasSingleBean(NoOpZipkinLoadBalancer.class));

	}

	@Test
	public void enableZipkinDiscoveryClient() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withPropertyValues("spring.zipkin.discovery-client-enabled=true").withUserConfiguration(
						ZipkinRestTemplateSenderConfigurationTest.MyDiscoveryClientZipkinUrlExtractorConfiguration.class,
						ZipkinProperties.class);

		runner.run(context -> assertThat(context).hasSingleBean(LoadBalancerClientZipkinLoadBalancer.class));
	}

	@Test
	public void shouldReturnCachedPortValueIfPresent() {
		final AtomicBoolean portCalculated = new AtomicBoolean();
		ZipkinProperties zipkinProperties = new ZipkinProperties();
		ZipkinRestTemplateSenderConfiguration.CachingZipkinUrlExtractor extractor = new ZipkinRestTemplateSenderConfiguration.CachingZipkinUrlExtractor(
				new NoOpZipkinLoadBalancer(zipkinProperties)) {
			@Override
			int calculatePort(ZipkinProperties zipkinProperties) {
				portCalculated.set(true);
				return super.calculatePort(zipkinProperties);
			}
		};
		extractor.zipkinPort.set(9411);

		URI uri = extractor.zipkinUrl(zipkinProperties);

		assertThat(uri.toString()).isEqualTo(URI.create(zipkinProperties.getBaseUrl()).toString());
		assertThat(portCalculated).isFalse();
	}

	@Test
	public void shouldDelegateToLoadBalancingWhenNoPortPresent() {
		ZipkinProperties zipkinProperties = new ZipkinProperties();
		zipkinProperties.setBaseUrl("https://somehostnamewithnoport/endpoint");
		ZipkinRestTemplateSenderConfiguration.CachingZipkinUrlExtractor extractor = new ZipkinRestTemplateSenderConfiguration.CachingZipkinUrlExtractor(
				() -> URI.create("https://example.com"));

		URI uri = extractor.zipkinUrl(zipkinProperties);

		assertThat(uri.toString()).isEqualTo(URI.create("https://example.com").toString());
	}

	@Test
	public void shouldDelegateToNonLoadBalancingWhenPortPresent() {
		ZipkinProperties zipkinProperties = new ZipkinProperties();
		ZipkinRestTemplateSenderConfiguration.CachingZipkinUrlExtractor extractor = new ZipkinRestTemplateSenderConfiguration.CachingZipkinUrlExtractor(
				() -> URI.create("https://example.com"));

		URI uri = extractor.zipkinUrl(zipkinProperties);

		assertThat(uri.toString()).isEqualTo(URI.create(zipkinProperties.getBaseUrl()).toString());
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(LoadBalancerClient.class)
	static class MyDiscoveryClientZipkinUrlExtractorConfiguration {

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
