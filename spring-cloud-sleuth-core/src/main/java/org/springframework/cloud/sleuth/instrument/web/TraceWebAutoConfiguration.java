/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import brave.Tracing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ConditionalOnManagementPort;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.actuate.endpoint.EndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that sets up common building blocks for both reactive and servlet
 * based web application.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.web.enabled", matchIfMissing = true)
@ConditionalOnBean(Tracing.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
@EnableConfigurationProperties(SleuthWebProperties.class)
public class TraceWebAutoConfiguration {

	@Autowired(required = false)
	List<SingleSkipPattern> patterns = new ArrayList<>();

	@Bean
	@ConditionalOnMissingBean
	SkipPatternProvider sleuthSkipPatternProvider() {
		return () -> Pattern
				.compile(this.patterns.stream().map(SingleSkipPattern::skipPattern)
						.filter(Optional::isPresent).map(Optional::get)
						.map(Pattern::pattern).collect(Collectors.joining("|")));
	}

	@Configuration
	@ConditionalOnClass(ManagementServerProperties.class)
	@ConditionalOnProperty(value = "spring.sleuth.web.ignoreAutoConfiguredSkipPatterns", havingValue = "false", matchIfMissing = true)
	protected static class ManagementSkipPatternProviderConfig {

		/**
		 * Sets or appends {@link ManagementServerProperties#getServlet()} to the skip
		 * pattern. If neither is available then sets the default one
		 * @param managementServerProperties properties
		 * @return optional skip pattern
		 */
		static Optional<Pattern> getPatternForManagementServerProperties(
				ManagementServerProperties managementServerProperties) {
			String contextPath = managementServerProperties.getServlet().getContextPath();
			if (StringUtils.hasText(contextPath)) {
				return Optional.of(Pattern.compile(contextPath + ".*"));
			}
			return Optional.empty();
		}

		@Bean
		@ConditionalOnBean(ManagementServerProperties.class)
		public SingleSkipPattern skipPatternForManagementServerProperties(
				final ManagementServerProperties managementServerProperties) {
			return () -> getPatternForManagementServerProperties(
					managementServerProperties);
		}

	}

	@Configuration
	@ConditionalOnClass({ ServerProperties.class, EndpointsSupplier.class,
			ExposableWebEndpoint.class })
	@ConditionalOnBean(ServerProperties.class)
	@ConditionalOnProperty(value = "spring.sleuth.web.ignoreAutoConfiguredSkipPatterns", havingValue = "false", matchIfMissing = true)
	protected static class ActuatorSkipPatternProviderConfig {

		static Optional<Pattern> getEndpointsPatterns(String contextPath,
				WebEndpointProperties webEndpointProperties,
				EndpointsSupplier<ExposableWebEndpoint> endpointsSupplier) {
			Collection<ExposableWebEndpoint> endpoints = endpointsSupplier.getEndpoints();

			if (endpoints.isEmpty()) {
				return Optional.empty();
			}

			String pattern = endpoints.stream().map(PathMappedEndpoint::getRootPath)
					.map(path -> path + "|" + path + "/.*").collect(
							Collectors.joining("|",
									getPathPrefix(contextPath,
											webEndpointProperties.getBasePath()) + "/(",
									")"));
			if (StringUtils.hasText(pattern)) {
				return Optional.of(Pattern.compile(pattern));
			}
			return Optional.empty();
		}

		private static String getPathPrefix(String contextPath, String actuatorBasePath) {
			String result = "";
			if (StringUtils.hasText(contextPath)) {
				result += contextPath;
			}
			if (!actuatorBasePath.equals("/")) {
				result += actuatorBasePath;
			}
			return result;
		}

		@Bean
		@ConditionalOnManagementPort(ManagementPortType.SAME)
		public SingleSkipPattern skipPatternForActuatorEndpointsSamePort(
				final ServerProperties serverProperties,
				final WebEndpointProperties webEndpointProperties,
				final EndpointsSupplier<ExposableWebEndpoint> endpointsSupplier) {
			return () -> getEndpointsPatterns(
					serverProperties.getServlet().getContextPath(), webEndpointProperties,
					endpointsSupplier);
		}

		@Bean
		@ConditionalOnManagementPort(ManagementPortType.DIFFERENT)
		@ConditionalOnProperty(name = "management.server.servlet.context-path", havingValue = "/", matchIfMissing = true)
		public SingleSkipPattern skipPatternForActuatorEndpointsDifferentPort(
				final ServerProperties serverProperties,
				final WebEndpointProperties webEndpointProperties,
				final EndpointsSupplier<ExposableWebEndpoint> endpointsSupplier) {
			return () -> getEndpointsPatterns(null, webEndpointProperties,
					endpointsSupplier);
		}

	}

	@Configuration
	static class DefaultSkipPatternConfig {

		private static String combinedPattern(String skipPattern,
				String additionalSkipPattern) {
			String pattern = skipPattern;
			if (!StringUtils.hasText(skipPattern)) {
				pattern = SleuthWebProperties.DEFAULT_SKIP_PATTERN;
			}
			if (StringUtils.hasText(additionalSkipPattern)) {
				return pattern + "|" + additionalSkipPattern;
			}
			return pattern;
		}

		@Bean
		SingleSkipPattern defaultSkipPatternBean(
				SleuthWebProperties sleuthWebProperties) {
			return () -> Optional.of(
					Pattern.compile(combinedPattern(sleuthWebProperties.getSkipPattern(),
							sleuthWebProperties.getAdditionalSkipPattern())));
		}

	}

}
