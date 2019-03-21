/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.stream;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.SpanAdjuster;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.cloud.sleuth.metric.TraceMetricsAutoConfiguration;
import org.springframework.cloud.sleuth.sampler.PercentageBasedSampler;
import org.springframework.cloud.sleuth.sampler.SamplerProperties;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.config.ChannelBindingAutoConfiguration;
import org.springframework.cloud.stream.config.ChannelsEndpointAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} for sending spans over Spring Cloud Stream. This is for
 * the producer (via {@link SleuthSource}). A consumer can enable binding to
 * {@link SleuthSink} and receive the messages coming from the source (they have
 * the same channel name so there is no additional configuration to do by
 * default).
 *
 * @author Dave Syer
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties({ SleuthStreamProperties.class, SamplerProperties.class, ZipkinProperties.class })
@AutoConfigureAfter({ TraceMetricsAutoConfiguration.class, IntegrationAutoConfiguration.class })
@AutoConfigureBefore({ ChannelBindingAutoConfiguration.class, TraceAutoConfiguration.class, ChannelsEndpointAutoConfiguration.class })
@EnableBinding(SleuthSource.class)
@ConditionalOnProperty(value = "spring.sleuth.stream.enabled", matchIfMissing = true)
public class SleuthStreamAutoConfiguration {

	@Autowired(required = false) List<SpanAdjuster> spanAdjusters = new ArrayList<>();

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
	@GlobalChannelInterceptor(patterns = SleuthSource.OUTPUT, order = Ordered.HIGHEST_PRECEDENCE)
	public ChannelInterceptor zipkinChannelInterceptor(SpanMetricReporter spanMetricReporter) {
		return new TracerIgnoringChannelInterceptor(spanMetricReporter);
	}

	@Bean
	@ConditionalOnMissingBean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public StreamSpanReporter sleuthStreamSpanReporter(HostLocator endpointLocator,
			SpanMetricReporter spanMetricReporter, Environment environment) {
		return new StreamSpanReporter(endpointLocator, spanMetricReporter, environment,
				this.spanAdjusters);
	}

	@Bean(name = StreamSpanReporter.POLLER)
	@ConditionalOnMissingBean(name = StreamSpanReporter.POLLER)
	public PollerMetadata defaultStreamSpanReporterPoller(SleuthStreamProperties sleuth) {
		PollerMetadata poller = new PollerMetadata();
		poller.setTrigger(new PeriodicTrigger(sleuth.getPoller().getFixedDelay()));
		poller.setMaxMessagesPerPoll(sleuth.getPoller().getMaxMessagesPerPoll());
		return poller;
	}

	@Configuration
	@ConditionalOnMissingBean(HostLocator.class)
	@ConditionalOnProperty(value = "spring.zipkin.locator.discovery.enabled", havingValue = "false", matchIfMissing = true)
	protected static class DefaultEndpointLocatorConfiguration {

		@Autowired(required = false)
		private ServerProperties serverProperties;

		@Autowired
		private ZipkinProperties zipkinProperties;

		@Autowired
		private InetUtils inetUtils;

		@Autowired
		private Environment environment;

		@Bean
		public HostLocator zipkinEndpointLocator() {
			return new ServerPropertiesHostLocator(this.serverProperties, this.environment, this.zipkinProperties,
					this.inetUtils);
		}

	}

	@Configuration
	@ConditionalOnClass(DiscoveryClient.class)
	@ConditionalOnMissingBean(HostLocator.class)
	@ConditionalOnProperty(value = "spring.zipkin.locator.discovery.enabled", havingValue = "true")
	protected static class DiscoveryClientEndpointLocatorConfiguration {

		@Autowired(required = false)
		private ServerProperties serverProperties;

		@Autowired
		private ZipkinProperties zipkinProperties;

		@Autowired(required = false)
		private InetUtils inetUtils;

		@Autowired
		private Environment environment;

		@Autowired(required = false)
		private Registration registration;

		@Bean
		public HostLocator zipkinEndpointLocator() {
			if (this.registration != null) {
				return new DiscoveryClientHostLocator(this.registration, this.zipkinProperties);
			}
			return new ServerPropertiesHostLocator(this.serverProperties, this.environment, this.zipkinProperties,
					this.inetUtils);
		}

	}

}
