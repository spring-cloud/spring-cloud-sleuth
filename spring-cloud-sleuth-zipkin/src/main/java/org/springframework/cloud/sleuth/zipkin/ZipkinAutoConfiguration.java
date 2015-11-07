/*
 * Copyright 2013-2015 the original author or authors.
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.scribe.ScribeSpanCollector;

/**
 * @author Spencer Gibb
 */
@Configuration
@EnableConfigurationProperties
@ConditionalOnClass(ScribeSpanCollector.class)
@ConditionalOnProperty(value = "spring.zipkin.enabled", matchIfMissing = true)
public class ZipkinAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(SpanCollector.class)
	public ScribeSpanCollector spanCollector() {
		ZipkinProperties zipkin = zipkinProperties();
		ScribeSpanCollector collector = new ScribeSpanCollector(zipkin.getHost(),
				zipkin.getPort(), zipkin.getCollector());
		return collector;
	}

	@Bean
	public ZipkinProperties zipkinProperties() {
		return new ZipkinProperties();
	}

	@Bean
	public ZipkinSpanListener sleuthTracer(SpanCollector spanCollector, EndpointLocator endpointLocator) {
		return new ZipkinSpanListener(spanCollector, endpointLocator.local());
	}

	@Configuration
	@ConditionalOnMissingClass("org.springframework.cloud.client.discovery.DiscoveryClient")
	protected static class DefaultEndpointLocatorConfiguration {

		@Autowired(required=false)
		private ServerProperties serverProperties;

		@Bean
		public EndpointLocator zipkinEndpointLocator() {
			return new ServerPropertiesEndpointLocator(this.serverProperties);
		}

	}

	@Configuration
	@ConditionalOnClass(DiscoveryClient.class)
	protected static class DiscoveryClientEndpointLocatorConfiguration {

		@Autowired(required=false)
		private ServerProperties serverProperties;

		@Autowired(required=false)
		private DiscoveryClient client;

		@Bean
		public EndpointLocator zipkinEndpointLocator() {
			if (this.client!=null) {
				return new DiscoveryClientEndpointLocator(this.client);
			}
			return new ServerPropertiesEndpointLocator(this.serverProperties);
		}

	}

}
