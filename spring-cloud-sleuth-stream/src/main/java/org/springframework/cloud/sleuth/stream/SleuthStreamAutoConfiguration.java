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

package org.springframework.cloud.sleuth.stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.cloud.sleuth.metric.TraceMetricsAutoConfiguration;
import org.springframework.cloud.sleuth.sampler.PercentageBasedSampler;
import org.springframework.cloud.sleuth.sampler.SamplerProperties;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.config.ChannelBindingAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.messaging.support.ChannelInterceptor;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * for sending spans over Spring Cloud Stream. This is for the producer
 * (via {@link SleuthSource}). A consumer can enable binding to {@link SleuthSink} and
 * receive the messages coming from the source (they have the same channel name so there
 * is no additional configuration to do by default).
 *
 * @author Dave Syer
 *
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties({SleuthStreamProperties.class, SamplerProperties.class})
@AutoConfigureAfter(TraceMetricsAutoConfiguration.class)
@AutoConfigureBefore(ChannelBindingAutoConfiguration.class)
@EnableBinding(SleuthSource.class)
@ConditionalOnProperty(value = "spring.sleuth.stream.enabled", matchIfMissing = true)
public class SleuthStreamAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public Sampler defaultTraceSampler(SamplerProperties config) {
		return new PercentageBasedSampler(config);
	}

	@Bean
	@GlobalChannelInterceptor(patterns = SleuthSource.OUTPUT, order = Ordered.HIGHEST_PRECEDENCE)
	public ChannelInterceptor zipkinChannelInterceptor(SpanMetricReporter spanMetricReporter) {
		return new TracerIgnoringChannelInterceptor(spanMetricReporter);
	}

	@Bean
	public StreamSpanListener sleuthTracer(HostLocator endpointLocator,
			SpanMetricReporter spanMetricReporter) {
		return new StreamSpanListener(endpointLocator, spanMetricReporter);
	}

	@Configuration
	@ConditionalOnMissingClass("org.springframework.cloud.client.discovery.DiscoveryClient")
	protected static class DefaultEndpointLocatorConfiguration {

		@Autowired(required = false)
		private ServerProperties serverProperties;

		@Value("${spring.application.name:unknown}")
		private String appName;

		@Bean
		public HostLocator zipkinEndpointLocator() {
			return new ServerPropertiesHostLocator(this.serverProperties, this.appName);
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

		@Bean
		public HostLocator zipkinEndpointLocator() {
			if (this.client != null) {
				return new DiscoveryClientHostLocator(this.client);
			}
			return new ServerPropertiesHostLocator(this.serverProperties, this.appName);
		}

	}

}
