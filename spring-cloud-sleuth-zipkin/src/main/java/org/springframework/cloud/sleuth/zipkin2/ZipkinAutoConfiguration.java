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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import zipkin2.CheckResult;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.ReporterMetrics;
import zipkin2.reporter.Sender;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.sampler.SamplerAutoConfiguration;
import org.springframework.cloud.sleuth.zipkin2.sender.ZipkinSenderConfigurationImportSelector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables reporting to Zipkin via HTTP. Has a default sampler set
 * from the {@link SamplerAutoConfiguration}
 *
 * The {@link ZipkinRestTemplateCustomizer} allows you to customize the
 * {@link RestTemplate} that is used to send Spans to Zipkin. Its default implementation -
 * {@link DefaultZipkinRestTemplateCustomizer} adds the GZip compression.
 *
 * @author Spencer Gibb
 * @author Tim Ysewyn
 * @since 1.0.0
 * @see SamplerAutoConfiguration
 * @see ZipkinRestTemplateCustomizer
 * @see DefaultZipkinRestTemplateCustomizer
 */
@Configuration
@EnableConfigurationProperties(ZipkinProperties.class)
@ConditionalOnProperty(value = { "spring.sleuth.enabled",
		"spring.zipkin.enabled" }, matchIfMissing = true)
@AutoConfigureBefore(TraceAutoConfiguration.class)
@AutoConfigureAfter(name = "org.springframework.cloud.autoconfigure.RefreshAutoConfiguration")
@Import({ ZipkinSenderConfigurationImportSelector.class, SamplerAutoConfiguration.class })
public class ZipkinAutoConfiguration {

	private static final Log log = LogFactory.getLog(ZipkinAutoConfiguration.class);

	/**
	 * Zipkin reporter bean name. Name of the bean matters for supporting multiple tracing
	 * systems.
	 */
	public static final String REPORTER_BEAN_NAME = "zipkinReporter";

	/**
	 * Zipkin sender bean name. Name of the bean matters for supporting multiple tracing
	 * systems.
	 */
	public static final String SENDER_BEAN_NAME = "zipkinSender";

	@Bean(REPORTER_BEAN_NAME)
	@ConditionalOnMissingBean(name = REPORTER_BEAN_NAME)
	public Reporter<Span> reporter(ReporterMetrics reporterMetrics,
			ZipkinProperties zipkin, @Qualifier(SENDER_BEAN_NAME) Sender sender) {
		// historical constraint. Note: AsyncReporter supports memory bounds
		AsyncReporter<Span> asyncReporter = AsyncReporter.builder(sender)
				.queuedMaxSpans(1000)
				.messageTimeout(zipkin.getMessageTimeout(), TimeUnit.SECONDS)
				.metrics(reporterMetrics).build(zipkin.getEncoder());
		CheckResult checkResult = checkResult(asyncReporter);
		logCheckResult(asyncReporter, checkResult);
		return asyncReporter;
	}

	private void logCheckResult(AsyncReporter asyncReporter, CheckResult checkResult) {
		if (log.isDebugEnabled() && checkResult != null && checkResult.ok()) {
			log.debug("Check result of the [" + asyncReporter.toString() + "] is ["
					+ checkResult + "]");
		}
		else if (checkResult != null && !checkResult.ok()) {
			log.warn("Check result of the [" + asyncReporter.toString()
					+ "] contains an error [" + checkResult + "]");
		}
	}

	private CheckResult checkResult(AsyncReporter<Span> asyncReporter) {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Callable<CheckResult> task = asyncReporter::check;
		Future<CheckResult> future = executor.submit(task);
		try {
			return future.get(1, TimeUnit.SECONDS);
		}
		catch (Exception ex) {
			log.warn(
					"An exception took place when trying to retrieve the check result. Will return null.",
					ex);
			return null;
		}
		finally {
			future.cancel(true);
			executor.shutdown();
		}
	}

	@Bean
	@ConditionalOnMissingBean
	public ZipkinRestTemplateCustomizer zipkinRestTemplateCustomizer(
			ZipkinProperties zipkinProperties) {
		return new DefaultZipkinRestTemplateCustomizer(zipkinProperties);
	}

	@Configuration
	@ConditionalOnMissingBean(EndpointLocator.class)
	@ConditionalOnProperty(value = "spring.zipkin.locator.discovery.enabled", havingValue = "false", matchIfMissing = true)
	protected static class DefaultEndpointLocatorConfiguration {

		@Autowired(required = false)
		private ServerProperties serverProperties;

		@Autowired
		private ZipkinProperties zipkinProperties;

		@Autowired(required = false)
		private InetUtils inetUtils;

		@Autowired
		private Environment environment;

		@Bean
		public EndpointLocator zipkinEndpointLocator() {
			return new DefaultEndpointLocator(null, this.serverProperties,
					this.environment, this.zipkinProperties, this.inetUtils);
		}

	}

	@Configuration
	@ConditionalOnClass(Registration.class)
	@ConditionalOnMissingBean(EndpointLocator.class)
	@ConditionalOnProperty(value = "spring.zipkin.locator.discovery.enabled", havingValue = "true")
	protected static class RegistrationEndpointLocatorConfiguration {

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
		public EndpointLocator zipkinEndpointLocator() {
			return new DefaultEndpointLocator(this.registration, this.serverProperties,
					this.environment, this.zipkinProperties, this.inetUtils);
		}

	}

}
