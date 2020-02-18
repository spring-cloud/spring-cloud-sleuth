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

package org.springframework.cloud.sleuth.zipkin2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import zipkin2.Span;
import zipkin2.codec.BytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.ReporterMetrics;
import zipkin2.reporter.Sender;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.Assert;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that will provide backwards compatibility to be able to support
 * multiple tracing systems on the classpath.
 *
 * Needs to be auto-configured before {@link ZipkinAutoConfiguration} in order to create a
 * {@link Reporter span reporter} if needed.
 *
 * @author Tim Ysewyn
 * @since 2.1.0
 * @see ZipkinAutoConfiguration
 * @deprecated left for backward compatibility
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = { "spring.sleuth.enabled", "spring.zipkin.enabled" },
		matchIfMissing = true)
@AutoConfigureBefore(ZipkinAutoConfiguration.class)
@Deprecated
public class ZipkinBackwardsCompatibilityAutoConfiguration {

	/**
	 * Only used for creating a reporter bean with the method below.
	 * @param zipkinProperties zipkin properties
	 * @return bytes encoder
	 * @deprecated left for backwards compatibility
	 */
	@Bean
	@ConditionalOnMissingBean
	@Deprecated
	BytesEncoder<Span> spanBytesEncoder(ZipkinProperties zipkinProperties) {
		return zipkinProperties.getEncoder();
	}

	@Configuration(proxyBeanMethods = false)
	@Conditional(BackwardsCompatibilityCondition.class)
	static class BackwardsCompatibilityConfiguration {

		/**
		 * Reporter that is depending on a {@link Sender} bean which is created in another
		 * auto-configuration than {@link ZipkinAutoConfiguration}.
		 * @param reporterMetrics metrics
		 * @param zipkin zipkin properties
		 * @param spanBytesEncoder encoder
		 * @param beanFactory Spring's Bean Factory
		 * @return span reporter
		 * @deprecated left for backwards compatibility
		 */
		@Bean
		@Deprecated
		Reporter<Span> reporter(ReporterMetrics reporterMetrics, ZipkinProperties zipkin,
				BytesEncoder<Span> spanBytesEncoder,
				DefaultListableBeanFactory beanFactory) {
			List<String> beanNames = new ArrayList<>(
					Arrays.asList(beanFactory.getBeanNamesForType(Sender.class)));
			beanNames.remove(ZipkinAutoConfiguration.SENDER_BEAN_NAME);
			Sender sender = (Sender) beanFactory.getBean(beanNames.get(0));
			// historical constraint. Note: AsyncReporter supports memory bounds
			return AsyncReporter.builder(sender).queuedMaxSpans(1000)
					.messageTimeout(zipkin.getMessageTimeout(), TimeUnit.SECONDS)
					.metrics(reporterMetrics).build(spanBytesEncoder);
		}

		/**
		 * Deprecated because this is moved to {@link TraceAutoConfiguration}. Left for
		 * backwards compatibility reasons.
		 * @return reporter metrics
		 * @deprecated left for backwards compatibility
		 */
		@Bean
		@ConditionalOnMissingBean
		@Deprecated
		ReporterMetrics zipkinReporterMetrics() {
			return new InMemoryReporterMetrics();
		}

	}

	/**
	 * Old approach: - one sender - one reporter
	 *
	 * This auto configuration verifies if we have the old approach. In which case we
	 * define the missing beans.
	 *
	 * In case of having 0 or more than 1 sender and there is a reporter, we don't need to
	 * use the backward compatibility bean setup.
	 */
	static class BackwardsCompatibilityCondition extends SpringBootCondition
			implements ConfigurationCondition {

		@Override
		public ConfigurationPhase getConfigurationPhase() {
			return ConfigurationPhase.REGISTER_BEAN;
		}

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context,
				AnnotatedTypeMetadata metadata) {
			Assert.isInstanceOf(DefaultListableBeanFactory.class,
					context.getBeanFactory());
			DefaultListableBeanFactory listableBeanFactory = (DefaultListableBeanFactory) context
					.getBeanFactory();
			int foundSenders = listableBeanFactory
					.getBeanNamesForType(Sender.class).length;

			// Previously we supported 1 Sender bean at a time
			// which could be overridden by another auto-configuration.
			// Now we support both the overridden bean and our default zipkinSender bean.
			// Since this config is adapting the old config we're searching for exactly 1
			// `Sender` bean before `ZipkinAutoConfiguration` kicks in.
			if (foundSenders != 1) {
				return ConditionOutcome.noMatch(
						"None or multiple Sender beans found - no reason to apply backwards compatibility");
			}
			int foundReporters = listableBeanFactory
					.getBeanNamesForType(Reporter.class).length;
			// Check if we need to provide a Reporter bean for the overridden Sender bean
			if (foundReporters > 0) {
				return ConditionOutcome.noMatch(
						"The old config setup already defines its own Reporter bean");
			}
			return ConditionOutcome.match();
		}

	}

}
